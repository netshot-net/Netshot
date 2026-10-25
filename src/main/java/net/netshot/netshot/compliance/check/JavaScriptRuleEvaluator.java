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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.JavaScriptRule;
import net.netshot.netshot.devicescript.helper.JsDeviceHelper;
import net.netshot.netshot.devicescript.helper.JsUtils;
import net.netshot.netshot.devicescript.helper.ScriptToolbox;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.concurrent.NamedThreadFactory;
import net.netshot.netshot.platform.log.TaskContext;

/**
 * Evaluates the JavaScript rules: a JavaScript-coded script checks the device
 * attributes, including config, and returns whether the device is compliant or not.
 */
@Slf4j
public class JavaScriptRuleEvaluator implements RuleEvaluator<JavaScriptRule> {

	/** The allowed results. */
	private static final CheckResult.ResultOption[] ALLOWED_RESULTS = new CheckResult.ResultOption[] {
		CheckResult.ResultOption.CONFORMING,
		CheckResult.ResultOption.NONCONFORMING,
		CheckResult.ResultOption.NOTAPPLICABLE,
	};

	private final JsRuleRuntime runtime;
	private final DriverRegistry driverRegistry;

	/** Is the script of the rule a JS-valid rule (once prepared, for the lifetime of the rule instance)? */
	private final RuleStateCache<JavaScriptRule, Boolean> validity = new RuleStateCache<>();

	/**
	 * Builds the evaluator.
	 *
	 * @param runtime the JavaScript runtime of the rules
	 * @param driverRegistry the loaded device drivers
	 */
	public JavaScriptRuleEvaluator(JsRuleRuntime runtime, DriverRegistry driverRegistry) {
		this.runtime = runtime;
		this.driverRegistry = driverRegistry;
	}

	@Override
	public Class<JavaScriptRule> ruleType() {
		return JavaScriptRule.class;
	}

	/**
	 * Prepare the polyglot source.
	 * @param rule the rule
	 * @return the source
	 */
	private static Source getSource(JavaScriptRule rule) {
		return Source
			.newBuilder("js", rule.script(), "_rule%d.js".formatted(rule.id()))
			.cached(false).buildLiteral();
	}

	/**
	 * Prepare the rule.
	 * @param rule = the rule
	 * @param context = the context
	 * @param taskContext = the task context
	 * @return true if the script is a valid rule
	 */
	private boolean prepare(JavaScriptRule rule, Context context, TaskContext taskContext) {
		return this.validity.get(rule, _ -> {
			boolean jsValid = false;
			try {
				Value checkFunction = context.getBindings("js").getMember("check");
				if (checkFunction == null || !checkFunction.canExecute()) {
					log.warn("The check function wasn't found in the script");
					taskContext.error("The 'check' function couldn't be found in the script.");
				}
				else {
					jsValid = true;
				}
			}
			catch (PolyglotException e) {
				taskContext.error("Error while evaluating the Javascript script.");
				log.warn("Error while evaluating the Javascript script.", e);
				jsValid = false;
			}
			return jsValid;
		});
	}

	@Override
	public CheckResult evaluate(JavaScriptRule rule, DeviceDraft device, boolean exempted, TaskContext taskContext) {
		if (!rule.enabled()) {
			return CheckResult.of(rule.id(), device.getId(), ResultOption.DISABLED);
		}

		// Both the script top-level code and the check function run on the executor, within the
		// same deadline, so that an endless loop can't block the calling thread
		ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("NetshotJsRule"));
		long deadline = System.currentTimeMillis() + this.runtime.getMaxExecutionTime();
		try (Context context = this.runtime.newContext()) {
			Value result;
			try {
				runBeforeDeadline(executor, () -> {
					ScriptToolbox.loadJs(context);
					context.eval(getSource(rule));
					context.eval(this.runtime.getLoaderSource());
					return null;
				}, deadline);
				boolean jsValid = this.prepare(rule, context, taskContext);
				if (!jsValid) {
					return CheckResult.of(rule.id(), device.getId(), ResultOption.INVALIDRULE);
				}
				if (exempted) {
					return CheckResult.of(rule.id(), device.getId(), ResultOption.EXEMPTED);
				}
				JsDeviceHelper deviceHelper = new JsDeviceHelper(this.driverRegistry, device, null, taskContext, true);
				result = runBeforeDeadline(executor,
					() -> context.getBindings("js").getMember("_check").execute(deviceHelper), deadline);
			}
			catch (TimeoutException e1) {
				try {
					context.close(true);
				}
				catch (Exception e2) {
					log.warn("Error while closing abnormally long JavaScript context", e2);
				}
				taskContext.error(
					"The rule took too long to execute (check for endless loop in the script or adjust netshot.javascript.maxexecutiontime value)");
				return CheckResult.of(rule.id(), device.getId(), CheckResult.ResultOption.INVALIDRULE,
					"The rule took too long to execute");
			}
			String txtResult = null;
			String comment = "";
			if (result.isString()) {
				txtResult = result.asString();
			}
			else if (result.hasMembers()) {
				Value jsComment = result.getMember("comment");
				if (jsComment != null && jsComment.isString()) {
					comment = jsComment.asString();
				}
				Value jsResult = result.getMember("result");
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
		}
		catch (Exception e) {
			taskContext.error("Error while running the script: {}", JsUtils.jsErrorToMessage(e));
			log.error("Error while running the script on device {}.", device.getId(), e);
		}
		finally {
			executor.shutdownNow();
			taskContext.debug("End of check");
		}
		return CheckResult.of(rule.id(), device.getId(), ResultOption.INVALIDRULE);
	}

}
