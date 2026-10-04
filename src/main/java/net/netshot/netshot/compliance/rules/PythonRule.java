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
package net.netshot.netshot.compliance.rules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.hibernate.Session;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.slf4j.MarkerFactory;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.script.helper.PyDeviceHelper;
import net.netshot.netshot.device.script.helper.PythonFileSystem;
import net.netshot.netshot.rest.RestViews.DefaultView;
import net.netshot.netshot.work.TaskContext;

/**
 * A PythonRule is a Python-coded script that will check the device attributes,
 * including config, and return whether the device is compliant or not.
 */
@Entity
@OnDelete(action = OnDeleteAction.CASCADE)
@Slf4j
public class PythonRule extends Rule {

	/** The allowed results. */
	private static final CheckResult.ResultOption[] ALLOWED_RESULTS = new CheckResult.ResultOption[] {
		CheckResult.ResultOption.CONFORMING,
		CheckResult.ResultOption.NONCONFORMING,
		CheckResult.ResultOption.NOTAPPLICABLE,
	};

	/**
	 * Settings/config for the current class.
	 */
	public static final class Settings {
		/** Max time (ms) to wait for script to execute. */
		@Getter
		private int maxExecutionTime;

		/**
		 * Load settings from config.
		 */
		private void load() {
			this.maxExecutionTime = Netshot.getConfig("netshot.python.maxexecutiontime", 
				60000, 1, 60 * 60 * 1000);
		}
	}

	/** Settings for this class. */
	public static final Settings SETTINGS = new Settings();

	/** Rule loader Python source. */
	private static final Source PYLOADER_SOURCE = readLoaderSource();

	/**
	 * Read the loader source code from resource file.
	 * @return the loader source
	 */
	private static Source readLoaderSource() {
		Source source = null;
		try {
			log.info("Reading the Python rule loader code from the resource Python file.");
			// Read the JavaScript loader code from the resource file.
			String path = "interfaces/rule-loader.py";
			InputStream in = DeviceDriver.class.getResourceAsStream("/" + path);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			StringBuilder buffer = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				buffer.append(line);
				buffer.append("\n");
			}
			source = Source.newBuilder("python", buffer.toString(), "rule-loader.py").buildLiteral();
			reader.close();
			in.close();
			log.debug("The Python rule loader code has been read from the resource Python file.");
		}
		catch (Exception e) {
			log.error(MarkerFactory.getMarker("FATAL"), "Unable to read the Python rule loader.", e);
			System.err.println("NETSHOT FATAL ERROR");
			e.printStackTrace();
			System.exit(1);
		}
		return source;
	}

	/**
	 * Initialize some additional static variables from global configuration.
	 */
	public static void loadConfig() {
		PythonRule.SETTINGS.load();
	}

	/** Was the execution prepared? */
	private boolean prepared;

	/** Is it a Python-valid rule? */
	private boolean pyValid;

	/** The default example script. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@Column(length = 10000000)
	}))
	@Setter
	private String script = ""
		+ "# Script template - to be customized\n"
		+ "def check(device):\n"
		+ "  ## Grab some data:\n"
		+ "  #  config = device.get('running_config')\n"
		+ "  #  name = device.get('name')\n"
		+ "  ## Some additional checks here...\n"
		+ "  ## debug('device name = %s' % name)\n"
		+ "  return result_option.CONFORMING\n"
		+ "  # return {'result': result_option.NONCONFORMING, 'comment': 'Why it is not fine'}\n"
		+ "  # return result_option.NOTAPPLICABLE\n";

	/**
	 * Instantiates a new Python rule.
	 */
	protected PythonRule() {

	}

	/**
	 * Instantiates a new Python rule.
	 *
	 * @param name
	 *                   the name
	 * @param policy
	 *                   the policy
	 */
	public PythonRule(String name, Policy policy) {
		super(name, policy);
	}

	/**
	 * Prepare the polyglot source.
	 * @return the source
	 */
	@Transient
	protected Source getSource() {
		return Source
			.newBuilder("python", this.script, "_rule%d.py".formatted(this.getId()))
			.cached(false).buildLiteral();
	}

	/**
	 * Create a GraalVM context and load the rule script.
	 * @return the context
	 * @throws IOException
	 */
	@Transient
	protected Context getContext() throws IOException {
		IOAccess.Builder accessBuilder = IOAccess.newBuilder();
		if (Netshot.getConfig("netshot.python.filesystemfilter", true)) {
			accessBuilder.fileSystem(new PythonFileSystem());
		}
		else {
			log.info("Python VM, file system filter is disabled (this is not secure)");
		}

		Context.Builder builder = Context.newBuilder("python");

		if (Netshot.getConfig("netshot.python.allowallaccess", false)) {
			log.info("Python VM, allowing all access (this is not secure)");
			builder.allowAllAccess(true);
			accessBuilder.allowHostFileAccess(true);
			accessBuilder.allowHostSocketAccess(true);
		}
		else {
			if (Netshot.getConfig("netshot.python.allowcreatethread", false)) {
				log.info("Python VM, allowing thread creation (this is not secure)");
				builder.allowCreateThread(true);
			}
			if (Netshot.getConfig("netshot.python.allowcreateprocess", false)) {
				log.info("Python VM, allowing process creation (this is not secure)");
				builder.allowCreateProcess(true);
			}
			if (Netshot.getConfig("netshot.python.allownativeaccess", false)) {
				log.info("Python VM, allowing native access (this is not secure)");
				builder.allowNativeAccess(true);
			}
			if (Netshot.getConfig("netshot.python.allowhostfileaccess", false)) {
				log.info("Python VM, allowing host file access (this is not secure)");
				accessBuilder.allowHostFileAccess(true);
			}
			if (Netshot.getConfig("netshot.python.allowhostsocketaccess", false)) {
				log.info("Python VM, allowing host socket access (this is not secure)");
				accessBuilder.allowHostSocketAccess(true);
			}
		}
		builder.allowIO(accessBuilder.build());

		if (PythonFileSystem.SETTINGS.getVenvFolder() != null) {
			builder
				.option("python.Executable", PythonFileSystem.SETTINGS.getVenvFolder() + "/bin/graalpy")
				.option("python.ForceImportSite", "true");
		}
		Context context = builder.build();
		return context;
	}

	/**
	 * Prepare the rule (try to evaluate the script).
	 * @param context = the context
	 * @param taskContext = the task context
	 */
	private void prepare(Context context, TaskContext taskContext) {
		if (prepared) {
			return;
		}
		prepared = true;
		pyValid = false;

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
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.compliance.Rule#check(net.netshot.netshot.device.Device, org.hibernate.Session)
	 */
	@Override
	public CheckResult check(Device device, Session session, TaskContext taskContext) {
		if (!this.isEnabled()) {
			return new CheckResult(this, device, ResultOption.DISABLED);
		}

		// Both the script top-level code and the check function run on the executor, within the
		// same deadline, so that an endless loop can't block the calling thread
		ExecutorService executor = Executors.newSingleThreadExecutor();
		long deadline = System.currentTimeMillis() + PythonRule.SETTINGS.getMaxExecutionTime();
		try (Context context = this.getContext()) {
			Value result;
			try {
				runBeforeDeadline(executor, () -> {
					context.eval(this.getSource());
					context.eval(PYLOADER_SOURCE);
					return null;
				}, deadline);
				prepare(context, taskContext);
				if (!this.pyValid) {
					return new CheckResult(this, device, ResultOption.INVALIDRULE);
				}
				if (device.isExempted(this)) {
					return new CheckResult(this, device, ResultOption.EXEMPTED);
				}
				PyDeviceHelper deviceHelper = new PyDeviceHelper(device, session, taskContext, true);
				result = runBeforeDeadline(executor,
					() -> context.getBindings("python").getMember("_check").execute(deviceHelper), deadline);
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
				return new CheckResult(this, device, CheckResult.ResultOption.INVALIDRULE,
					"The rule took too long to execute");
			}
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
					return new CheckResult(this, device, allowedResult, comment);
				}
			}
		}
		catch (IOException e) {
			taskContext.error("Error while evaluating the Python script.");
			log.warn("Error while evaluating the Python script.", e);
			pyValid = false;
		}
		catch (Exception e) {
			taskContext.error("Error while running the script: " + e.getMessage());
			log.error("Error while running the script on device {}.", device.getId(), e);
		}
		finally {
			executor.shutdownNow();
			taskContext.debug("End of check");
		}
		return new CheckResult(this, device, ResultOption.INVALIDRULE);
	}

	@Override
	public String toString() {
		return "Compliance Python-based rule " + id + " (name '" + name + "')";
	}

}
