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
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.MarkerFactory;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.devicescript.helper.PythonFileSystem;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;

/**
 * The Python runtime of the compliance rules: the rule loader source, the settings
 * and the way the contexts are created.
 */
@Slf4j
public class PyRuleRuntime {

	/**
	 * The settings of the Python runtime of the rules.
	 *
	 * @param maxExecutionTime the max time (ms) to wait for a script to execute
	 * @param fileSystemFilter whether the file system access is restricted to the Python libraries
	 * @param allowAllAccess whether the scripts get all the accesses (not secure)
	 * @param allowCreateThread whether the scripts can create threads (not secure)
	 * @param allowCreateProcess whether the scripts can create processes (not secure)
	 * @param allowNativeAccess whether the scripts get native access (not secure)
	 * @param allowHostFileAccess whether the scripts can access the host files (not secure)
	 * @param allowHostSocketAccess whether the scripts can access the host sockets (not secure)
	 */
	record Settings(int maxExecutionTime, boolean fileSystemFilter, boolean allowAllAccess, boolean allowCreateThread,
		boolean allowCreateProcess, boolean allowNativeAccess, boolean allowHostFileAccess, boolean allowHostSocketAccess) {

		/** Max time (ms) to wait for a script to execute. */
		static final Setting<Integer> MAX_EXECUTION_TIME = Setting.integer("netshot.python.maxexecutiontime")
			.defaultValue(60000).range(1, 60 * 60 * 1000).live();

		/** Whether the file system access is restricted to the Python libraries. */
		static final Setting<Boolean> FILE_SYSTEM_FILTER = Setting.bool("netshot.python.filesystemfilter").defaultValue(true).live();

		/** Whether the scripts get all the accesses. */
		static final Setting<Boolean> ALLOW_ALL_ACCESS = Setting.bool("netshot.python.allowallaccess").defaultValue(false).live();

		/** Whether the scripts can create threads. */
		static final Setting<Boolean> ALLOW_CREATE_THREAD = Setting.bool("netshot.python.allowcreatethread").defaultValue(false).live();

		/** Whether the scripts can create processes. */
		static final Setting<Boolean> ALLOW_CREATE_PROCESS = Setting.bool("netshot.python.allowcreateprocess").defaultValue(false).live();

		/** Whether the scripts get native access. */
		static final Setting<Boolean> ALLOW_NATIVE_ACCESS = Setting.bool("netshot.python.allownativeaccess").defaultValue(false).live();

		/** Whether the scripts can access the host files. */
		static final Setting<Boolean> ALLOW_HOST_FILE_ACCESS = Setting.bool("netshot.python.allowhostfileaccess")
			.defaultValue(false).live();

		/** Whether the scripts can access the host sockets. */
		static final Setting<Boolean> ALLOW_HOST_SOCKET_ACCESS = Setting.bool("netshot.python.allowhostsocketaccess")
			.defaultValue(false).live();

		/**
		 * Reads the settings.
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			return new Settings(config.get(MAX_EXECUTION_TIME), config.get(FILE_SYSTEM_FILTER), config.get(ALLOW_ALL_ACCESS),
				config.get(ALLOW_CREATE_THREAD), config.get(ALLOW_CREATE_PROCESS), config.get(ALLOW_NATIVE_ACCESS),
				config.get(ALLOW_HOST_FILE_ACCESS), config.get(ALLOW_HOST_SOCKET_ACCESS));
		}
	}

	/** The settings, from the configuration. */
	private final ConfigStore.Binding<Settings> settings;

	/** Rule loader Python source. */
	@Getter
	private final Source loaderSource = readLoaderSource();

	/**
	 * Builds the runtime.
	 *
	 * @param config the configuration store, to read the settings from
	 */
	public PyRuleRuntime(ConfigStore config) {
		this.settings = config.bind(Settings::from);
	}

	/**
	 * Gets the max time to wait for a script to execute.
	 *
	 * @return the max execution time (ms)
	 */
	public int getMaxExecutionTime() {
		return this.settings.get().maxExecutionTime();
	}

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
	 * Create a GraalVM context to run a rule script.
	 * @return the context
	 * @throws IOException if the context can't be created
	 */
	public Context newContext() throws IOException {
		Settings current = this.settings.get();
		IOAccess.Builder accessBuilder = IOAccess.newBuilder();
		if (current.fileSystemFilter()) {
			accessBuilder.fileSystem(PythonFileSystem.newRestrictedFileSystem());
		}
		else {
			log.info("Python VM, file system filter is disabled (this is not secure)");
		}

		Context.Builder builder = Context.newBuilder("python");

		if (current.allowAllAccess()) {
			log.info("Python VM, allowing all access (this is not secure)");
			builder.allowAllAccess(true);
			accessBuilder.allowHostFileAccess(true);
			accessBuilder.allowHostSocketAccess(true);
		}
		else {
			// Don't expose the Netshot process environment (which may contain secrets) to the scripts
			builder.allowEnvironmentAccess(EnvironmentAccess.NONE);
			if (current.allowCreateThread()) {
				log.info("Python VM, allowing thread creation (this is not secure)");
				builder.allowCreateThread(true);
			}
			if (current.allowCreateProcess()) {
				log.info("Python VM, allowing process creation (this is not secure)");
				builder.allowCreateProcess(true);
			}
			if (current.allowNativeAccess()) {
				log.info("Python VM, allowing native access (this is not secure)");
				builder.allowNativeAccess(true);
			}
			if (current.allowHostFileAccess()) {
				log.info("Python VM, allowing host file access (this is not secure)");
				accessBuilder.allowHostFileAccess(true);
			}
			if (current.allowHostSocketAccess()) {
				log.info("Python VM, allowing host socket access (this is not secure)");
				accessBuilder.allowHostSocketAccess(true);
			}
		}
		builder.allowIO(accessBuilder.build());

		String venvFolder = PythonFileSystem.settings().venvFolder();
		if (venvFolder != null) {
			builder
				.option("python.Executable", venvFolder + "/bin/graalpy")
				.option("python.ForceImportSite", "true");
		}
		Context context = builder.build();
		return context;
	}

}
