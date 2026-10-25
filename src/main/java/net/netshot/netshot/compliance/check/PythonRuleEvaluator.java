/**
 * Copyright 2013-2025 Netshot
 * 
 * This file is part of Netshot project.
 * 
 * Netshot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Netshot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Netshot.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.netshot.netshot.compliance.check;

import static net.netshot.netshot.compliance.check.RuleDeadline.runBeforeDeadline;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.PythonRule;
import net.netshot.netshot.devicescript.helper.PyDeviceHelper;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.compliance.CheckResult.ResultOption;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.concurrent.NamedThreadFactory;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.platform.script.ScriptToolbox;

/**
 * Evaluates the Python rules: a Python-coded script checks the device attributes,
 * including config, and returns whether the device is compliant or not.
 */
@Slf4j
public class PythonRuleEvaluator implements RuleEvaluator<PythonRule> {

	/** The allowed results. */
	private static final CheckResult.ResultOption[] ALLOWED_RESULTS = new CheckResult.ResultOption[] {
		CheckResult.ResultOption.CONFORMING,
		CheckResult.ResultOption.NONCONFORMING,
		CheckResult.ResultOption.NOTAPPLICABLE,
	};

	private final PyRuleRuntime runtime;
	private final DriverRegistry driverRegistry;

	/** Is the script of the rule a Python-valid rule (once prepared, for the lifetime of the rule instance)? */
	private final RuleStateCache<PythonRule, Boolean> validity = new RuleStateCache<>();

	/**
	 * Builds the evaluator.
	 *
	 * @param runtime the Python runtime of the rules
	 * @param driverRegistry the loaded device drivers
	 */
	public PythonRuleEvaluator(PyRuleRuntime runtime, DriverRegistry driverRegistry) {
		this.runtime = runtime;
		this.driverRegistry = driverRegistry;
	}

	@Override
	public Class<PythonRule> ruleType() {
		return PythonRule.class;
	}

	/**
	 * Prepare the polyglot source.
	 * @param rule the rule
	 * @return the source
	 */
	private static Source getSource(PythonRule rule) {
		return Source
			.newBuilder("python", rule.script(), "_rule%d.py".formatted(rule.id()))
			.cached(false).buildLiteral();
	}

	/**
	 * Prepare the rule (try to evaluate the script).
	 * @param rule = the rule
	 * @param context = the context
	 * @param taskContext = the task context
	 * @return true if the script is a valid rule
	 */
	private boolean prepare(PythonRule rule, Context context, TaskContext taskContext) {
		return this.validity.get(rule, _ -> {
			boolean pyValid = false;
			try {
				Value checkFunction = context.getBindings("python").getMember("check");
				if (checkFunction == null || !checkFunction.canExecute()) {
					log.warn("The check sub wasn't found in the script");
					taskContext.error("The 'check' sub couldn't be found in the script.");
				}
				else {
					pyValid = true;
				}
			}
			catch (PolyglotException e) {
				taskContext.error("Error while evaluating the Python script.");
				log.warn("Error while evaluating the Python script.", e);
				pyValid = false;
			}
			return pyValid;
		});
	}

	@Override
	public CheckResult evaluate(PythonRule rule, DeviceDraft device, boolean exempted, TaskContext taskContext) {
		if (!rule.enabled()) {
			return CheckResult.of(rule.id(), device.getId(), ResultOption.DISABLED);
		}

		// The script top-level code, the check function and the context closing (which runs the
		// Python exit handlers) all run on the executor, within the same deadline, so that an
		// endless loop can't block the calling thread. Closing the context on the same thread
		// also matters: once the 'threading' module is loaded, Python considers this thread
		// as the main thread, and would wait forever for it to end if closing from another one
		ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("NetshotPyRule"));
		long deadline = System.currentTimeMillis() + this.runtime.getMaxExecutionTime();
		Context context = null;
		try {
			context = this.runtime.newContext();
			final Context ctx = context;
			CheckResult checkResult;
			try {
				runBeforeDeadline(executor, () -> {
					ScriptToolbox.loadPython(ctx);
					ctx.eval(getSource(rule));
					ctx.eval(this.runtime.getLoaderSource());
					return null;
				}, deadline);
				boolean pyValid = this.prepare(rule, context, taskContext);
				if (!pyValid) {
					checkResult = CheckResult.of(rule.id(), device.getId(), ResultOption.INVALIDRULE);
				}
				else if (exempted) {
					checkResult = CheckResult.of(rule.id(), device.getId(), ResultOption.EXEMPTED);
				}
				else {
					PyDeviceHelper deviceHelper = new PyDeviceHelper(this.driverRegistry, device, taskContext, true);
					Value result = runBeforeDeadline(executor,
						() -> ctx.getBindings("python").getMember("_check").execute(deviceHelper), deadline);
					checkResult = this.toCheckResult(rule, result, device, taskContext);
				}
				runBeforeDeadline(executor, () -> {
					ctx.close();
					return null;
				}, deadline);
			}
			catch (TimeoutException e1) {
				try {
					context.close(true);
				}
				catch (Exception e2) {
					log.warn("Error while closing abnormally long Python context", e2);
				}
				taskContext.error(
					"The rule took too long to execute (check for endless loop in the script or adjust netshot.python.maxexecutiontime value)");
				return CheckResult.of(rule.id(), device.getId(), CheckResult.ResultOption.INVALIDRULE,
					"The rule took too long to execute");
			}
			return checkResult;
		}
		catch (IOException e) {
			taskContext.error("Error while evaluating the Python script.");
			log.warn("Error while evaluating the Python script.", e);
			this.validity.replaceIfPresent(rule, false);
		}
		catch (Exception e) {
			taskContext.error("Error while running the script: " + e.getMessage());
			log.error("Error while running the script on device {}.", device.getId(), e);
		}
		finally {
			if (context != null) {
				// Error cases: the context might still be open (no-op otherwise)
				closeContext(executor, context, deadline);
			}
			executor.shutdownNow();
			taskContext.debug("End of check");
		}
		return CheckResult.of(rule.id(), device.getId(), ResultOption.INVALIDRULE);
	}

	/**
	 * Convert the value returned by the check function to a check result.
	 * @param rule the rule
	 * @param result the value returned by the Python check function
	 * @param device the device
	 * @param taskContext the task context
	 * @return the check result
	 */
	private CheckResult toCheckResult(PythonRule rule, Value result, DeviceDraft device, TaskContext taskContext) {
		String txtResult = null;
		String comment = "";
		if (result.isString()) {
			txtResult = result.asString();
		}
		else if (result.hasHashEntries()) {
			Value jsComment = result.getHashValue("comment");
			if (jsComment != null && jsComment.isString()) {
				comment = jsComment.asString();
			}
			Value jsResult = result.getHashValue("result");
			if (jsResult != null && jsResult.isString()) {
				txtResult = jsResult.asString();
			}
		}
		for (CheckResult.ResultOption allowedResult : ALLOWED_RESULTS) {
			if (allowedResult.toString().equals(txtResult)) {
				taskContext.info("The script returned {} ({}), comment '{}'.",
					allowedResult.toString(), allowedResult.getValue(), comment);
				return CheckResult.of(rule.id(), device.getId(), allowedResult, comment);
			}
		}
		return CheckResult.of(rule.id(), device.getId(), ResultOption.INVALIDRULE);
	}

	/**
	 * Close the Python context on the executor thread (see evaluate), before the deadline,
	 * or cancel it if that doesn't work.
	 * @param executor the executor which ran the script
	 * @param context the context to close
	 * @param deadline the deadline
	 */
	private static void closeContext(ExecutorService executor, Context context, long deadline) {
		try {
			runBeforeDeadline(executor, () -> {
				context.close();
				return null;
			}, deadline);
		}
		catch (Exception e) {
			try {
				context.close(true);
			}
			catch (Exception e2) {
				log.warn("Error while closing the Python context", e2);
			}
		}
	}

}
