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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.boot.Application;
import net.netshot.netshot.boot.Bootstrap;
import net.netshot.netshot.boot.ConfigOption;
import net.netshot.netshot.boot.FatalError;
import net.netshot.netshot.boot.Signals;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.runtime.AgentInstrumentation;
import net.netshot.netshot.platform.runtime.BuildInfo;

/**
 * The entry points of Netshot: {@link #main} starts the server (the configuration store, with the configuration files
 * as source, is loaded and the process is initialized by {@code Bootstrap}, the application runs through
 * {@code Application}), {@link #agentmain} receives the instrumentation of the JVM.
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
		options.addOption(ConfigOption.option());
		options.addOption("v", "version", false, "print the current version and exit");
		CommandLineParser parser = new DefaultParser();
		CommandLine commandLine;
		try {
			commandLine = parser.parse(options, args);
		}
		catch (ParseException e) {
			FatalError.report(ConfigOption.invalidArguments(e));
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

		try {
			ConfigStore config = ConfigStore.ofFiles(ConfigOption.files(commandLine));
			// Sets the logging up: nothing is logged before
			Bootstrap.initialize(config);
			// The lifecycle milestones are warnings, logged at the default level
			log.warn("Starting Netshot version {}.", BuildInfo.VERSION);
			System.out.println(String.format("Configuration file %s read.", config.current().fileName()));
			log.info("Configuration file {} read.", config.current().fileName());

			Application application = Application.create(config);
			// Stops the services on SIGTERM/SIGINT (or if the startup fails), reloads the configuration on HUP
			Signals.register(application);
			application.migrate();
			application.start();
			if (!application.isClosed()) {
				// Not when stopped while it started: the stop is logged
				log.warn("Netshot is started.");
			}
		}
		catch (Exception e) {
			FatalError.report(e);
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
		AgentInstrumentation.set(inst);
	}

}

