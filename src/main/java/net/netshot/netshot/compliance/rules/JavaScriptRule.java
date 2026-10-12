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
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
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
import net.netshot.netshot.device.script.helper.JsDeviceHelper;
import net.netshot.netshot.device.script.helper.JsUtils;
import net.netshot.netshot.device.script.helper.ScriptToolbox;
import net.netshot.netshot.rest.RestViews.DefaultView;
import net.netshot.netshot.work.TaskContext;

/**
 * A JavaScriptRule is a Javascript-coded script that will check the device
 * attributes, including config, and return whether the device is compliant
 * or not.
 */
@Entity
@OnDelete(action = OnDeleteAction.CASCADE)
@Slf4j
public class JavaScriptRule extends Rule {

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
			this.maxExecutionTime = Netshot.getConfig("netshot.javascript.maxexecutiontime", 
				60000, 1, 60 * 60 * 1000);
		}
	}

	/** Settings for this class. */
	public static final Settings SETTINGS = new Settings();

	/** Rule loader JavaScript source. */
	private static final Source JSLOADER_SOURCE = readLoaderSource();

	/**
	 * Read the loader source code from resource file.
	 * @return the loader source
	 */
	private static Source readLoaderSource() {
		Source source = null;
		try {
			log.info("Reading the JavaScript rule loader code from the resource JS file.");
			// Read the JavaScript loader code from the resource file.
			String path = "interfaces/rule-loader.js";
			InputStream in = DeviceDriver.class.getResourceAsStream("/" + path);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			StringBuffer buffer = new StringBuffer();
			String line;
			while ((line = reader.readLine()) != null) {
				buffer.append(line);
				buffer.append("\n");
			}
			source = Source.create("js", buffer.toString());
			reader.close();
			in.close();
			log.debug("The JavaScript rule loader code has been read from the resource JS file.");
		}
		catch (Exception e) {
			log.error(MarkerFactory.getMarker("FATAL"),
				"Unable to read the Javascript rule loader.", e);
			System.err.println("NETSHOT FATAL ERROR");
			e.printStackTrace();
			System.exit(1);
		}
		return source;
	}

	/** The Python execution engine (for eval caching). */
	private static Engine engine = Engine.create();

	/**
	 * Initialize some additional static variables from global configuration.
	 */
	public static void loadConfig() {
		JavaScriptRule.SETTINGS.load();
	}

	/** The prepared. */
	private boolean prepared;

	/** The js valid. */
	private boolean jsValid;

	/** The script. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@Column(length = 10000000)
	}))
	@Setter
	private String script = ""
		+ "/*\n"
		+ " * Script template - to be customized.\n"
		+ " */\n"
		+ "function check(device) {\n"
		+ "    //const config = device.get(\"runningConfig\");\n"
		+ "    //const name = device.get(\"name\");\n"
		+ "    return CONFORMING;\n"
		+ "    //return NONCONFORMING;\n"
		+ "    //return NOTAPPLICABLE;\n"
		+ "}\n";

	/**
	 * Instantiates a new JavaScript rule.
	 */
	protected JavaScriptRule() {

	}

	/**
	 * Instantiates a new JavaScript rule.
	 *
	 * @param name the name
	 * @param policy the policy
	 */
	public JavaScriptRule(String name, Policy policy) {
		super(name, policy);
	}

	/**
	 * Prepare the polyglot source.
	 * @return the source
	 */
	@Transient
	protected Source getSource() {
		return Source
			.newBuilder("js", this.script, "_rule%d.js".formatted(this.getId()))
			.cached(false).buildLiteral();
	}

	@Transient
	public Context getContext() throws IOException {
		Context.Builder builder = Context
			.newBuilder("js");
		builder.allowExperimentalOptions(true)
			.option("js.console", "false")
			.option("js.load", "false")
			.option("js.polyglot-builtin", "false");
		Context context;
		synchronized (engine) {
			context = builder.engine(engine).build();
		}
		return context;
	}

	/**
	 * Prepare the rule.
	 * @param context = the context
	 * @param taskContext = the task context
	 */
	private void prepare(Context context, TaskContext taskContext) {
		if (prepared) {
			return;
		}
		prepared = true;
		jsValid = false;

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
		long deadline = System.currentTimeMillis() + JavaScriptRule.SETTINGS.getMaxExecutionTime();
		try (Context context = this.getContext()) {
			Value result;
			try {
				runBeforeDeadline(executor, () -> {
					ScriptToolbox.loadJs(context);
					context.eval(this.getSource());
					context.eval(JSLOADER_SOURCE);
					return null;
				}, deadline);
				prepare(context, taskContext);
				if (!this.jsValid) {
					return new CheckResult(this, device, ResultOption.INVALIDRULE);
				}
				if (device.isExempted(this)) {
					return new CheckResult(this, device, ResultOption.EXEMPTED);
				}
				JsDeviceHelper deviceHelper = new JsDeviceHelper(device, null, session, taskContext, true);
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
				return new CheckResult(this, device, CheckResult.ResultOption.INVALIDRULE,
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
					return new CheckResult(this, device, allowedResult, comment);
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
		return new CheckResult(this, device, ResultOption.INVALIDRULE);
	}

	@Override
	public String toString() {
		return "Compliance JavaScript-based rule " + id + " (name '" + name + "')";
	}

}
