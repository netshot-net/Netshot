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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.slf4j.MarkerFactory;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;

/**
 * The JavaScript runtime of the compliance rules: the shared polyglot engine
 * (for eval caching), the rule loader source and the settings.
 */
@Slf4j
public class JsRuleRuntime {

	/** Max time (ms) to wait for a script to execute. */
	static final Setting<Integer> MAX_EXECUTION_TIME = Setting.integer("netshot.javascript.maxexecutiontime")
		.defaultValue(60000).range(1, 60 * 60 * 1000).live();

	/** Max time (ms) to wait for a script to execute, from the configuration. */
	private final ConfigStore.Binding<Integer> maxExecutionTime;

	/** Rule loader JavaScript source. */
	@Getter
	private final Source loaderSource = readLoaderSource();

	/** The JavaScript execution engine (for eval caching), created on first use. */
	private Engine engine;

	/**
	 * Builds the runtime.
	 *
	 * @param config the configuration store, to read the settings from
	 */
	public JsRuleRuntime(ConfigStore config) {
		this.maxExecutionTime = config.bind(c -> c.get(MAX_EXECUTION_TIME));
	}

	/**
	 * Gets the max time to wait for a script to execute.
	 *
	 * @return the max execution time (ms)
	 */
	public int getMaxExecutionTime() {
		return this.maxExecutionTime.get();
	}

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

	private synchronized Engine engine() {
		if (this.engine == null) {
			this.engine = Engine.create();
		}
		return this.engine;
	}

	/**
	 * Creates a context to run a rule script.
	 *
	 * @return the context
	 * @throws IOException if the context can't be created
	 */
	public Context newContext() throws IOException {
		Context.Builder builder = Context
			.newBuilder("js");
		builder.allowExperimentalOptions(true)
			.option("js.console", "false")
			.option("js.load", "false")
			.option("js.polyglot-builtin", "false");
		Engine sharedEngine = this.engine();
		Context context;
		synchronized (sharedEngine) {
			context = builder.engine(sharedEngine).build();
		}
		return context;
	}

}
