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
import net.netshot.netshot.Netshot;
import net.netshot.netshot.devicescript.helper.PythonFileSystem;
import net.netshot.netshot.driver.DeviceDriver;

/**
 * The Python runtime of the compliance rules: the rule loader source, the settings
 * and the way the contexts are created.
 */
@Slf4j
public class PyRuleRuntime {

	/** Max time (ms) to wait for a script to execute. */
	@Getter
	private volatile int maxExecutionTime;

	/** Rule loader Python source. */
	@Getter
	private final Source loaderSource = readLoaderSource();

	/**
	 * Builds the runtime, with the settings from the current configuration.
	 */
	public PyRuleRuntime() {
		this.loadConfig();
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
	 * Load the settings from the global configuration.
	 */
	public void loadConfig() {
		this.maxExecutionTime = Netshot.getConfig("netshot.python.maxexecutiontime",
			60000, 1, 60 * 60 * 1000);
	}

	/**
	 * Create a GraalVM context to run a rule script.
	 * @return the context
	 * @throws IOException if the context can't be created
	 */
	public Context newContext() throws IOException {
		IOAccess.Builder accessBuilder = IOAccess.newBuilder();
		if (Netshot.getConfig("netshot.python.filesystemfilter", true)) {
			accessBuilder.fileSystem(PythonFileSystem.newRestrictedFileSystem());
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
			// Don't expose the Netshot process environment (which may contain secrets) to the scripts
			builder.allowEnvironmentAccess(EnvironmentAccess.NONE);
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

}
