/**
 * Copyright 2013-present Netshot
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
package net.netshot.netshot;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.boot.Application;
import net.netshot.netshot.boot.Bootstrap;
import net.netshot.netshot.boot.BootstrapException;
import net.netshot.netshot.boot.Signals;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.runtime.AgentInstrumentation;
import net.netshot.netshot.platform.runtime.BuildInfo;

/**
 * The entry points of Netshot: {@link #main} starts the server (the process is prepared by {@code Bootstrap}, the
 * application runs through {@code Application}), {@link #agentmain} receives the instrumentation of the JVM.
 */
@Slf4j
public final class Netshot {

	private Netshot() {
	}

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
		Options options = new Options();
		options.addOption("h", "help", false, "print this help and exit");
		options.addOption("c", "config", true, "path to configuration file");
		options.addOption("v", "version", false, "print the current version and exit");
		CommandLineParser parser = new DefaultParser();
		CommandLine commandLine;
		try {
			commandLine = parser.parse(options, args);
		}
		catch (ParseException e) {
			System.err.println("Error while parsing arguments. " + e.getMessage());
			System.exit(1);
			return;
		}

		if (commandLine.hasOption("h")) {
			HelpFormatter formatter = HelpFormatter.builder()
				.setShowSince(false)
				.get();
			try {
				formatter.printHelp("netshot", null, options, null, true);
			}
			catch (IOException e) {
				// Ignore
			}
			return;
		}

		if (commandLine.hasOption("v")) {
			System.out.println(String.format("Netshot version %s", BuildInfo.VERSION));
			return;
		}

		System.out.println(String.format("Starting Netshot version %s.", BuildInfo.VERSION));
		log.info("Starting Netshot");

		final List<String> configFiles;
		String configFilename = commandLine.getOptionValue("c");
		if (configFilename == null) {
			configFiles = Bootstrap.DEFAULT_FILES;
		}
		else {
			configFiles = List.of(configFilename);
		}

		try {
			ConfigStore config = Bootstrap.load(configFiles);

			Application application = Application.create(config);
			// Stops the services (in the reverse order) on SIGTERM/SIGINT, or if the startup fails
			Signals.stopOnShutdown(application);
			application.migrate();
			application.start();

			log.info("Starting signal listener.");
			Signals.reloadOnHangup(application, configFiles);
			log.warn("Netshot is started");

		}
		catch (BootstrapException e) {
			System.err.println("NETSHOT FATAL ERROR: " + e.getMessage());
			if (e.getCause() != null) {
				e.printStackTrace();
			}
			System.exit(1);
		}
		catch (Exception e) {
			System.err.println("NETSHOT FATAL ERROR: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * The agentmain entry point.
	 *
	 * @param agentArgs the agent arguments
	 * @param inst the instrumentation
	 */
	public static void agentmain(String agentArgs, Instrumentation inst) {
		log.info("Netshot agentmain entrypoint");
		AgentInstrumentation.set(inst);
	}

}

