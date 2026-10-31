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
package net.netshot.netshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;
import org.slf4j.MarkerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.boot.LoggingSetup;
import net.netshot.netshot.boot.NetshotSettings;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.lifecycle.ServiceManager;
import net.netshot.netshot.platform.tls.BouncyCastleLoader;
//CHECKSTYLE:OFF: IllegalImport
import sun.misc.Signal;
import sun.misc.SignalHandler;
//CHECKSTYLE:ON: IllegalImport

/**
 * The Class Netshot. Starting point of Netshot
 */
@Slf4j
public class Netshot {

	/** Netshot version. */
	public static final String VERSION = "0.0.0-dev";

	/** The list of configuration files to look at, in sequence. */
	private static final String[] CONFIG_FILENAMES = new String[] {
		"netshot.conf", "/etc/netshot.conf"};

	/** A custom truststore (CA certificates to trust). */
	static final Setting<String> TRUSTSTORE_FILE = Setting.text("netshot.ssl.truststore.file").restart();

	/** The application configuration (the current snapshot, replaced as a whole on a reload). */
	private static final ConfigStore CONFIG = new ConfigStore(Config.empty());

	/**
	 * The instrumentation handed by the JVM to the agent entry point (used to load the crypto libraries):
	 * the only mutable static field, see the STATIC_FIELDS_ARE_FINAL rule of ArchitectureTest.
	 */
	private static volatile Instrumentation instrumentation;

	protected Netshot() {
	}

	/**
	 * Retrieve the local machine hostname.
	 *
	 * @return the local machine hostname
	 */
	public static String getHostname() {
		return Hostname.VALUE;
	}

	/** Holder of the machine hostname, read once, on first use (lazy, thread-safe class initialization). */
	private static final class Hostname {
		static final String VALUE = read();

		private static String read() {
			String hostname = null;
			try {
				Process process = Runtime.getRuntime().exec(new String[] { "hostname" });
				BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
				hostname = stdInput.readLine();
			}
			catch (IOException e) {
				log.error("Error while getting local machine hostname", e);
			}
			return hostname == null ? "unknown" : hostname;
		}
	}

	/**
	 * Gets the configuration store, which holds the current configuration snapshot.
	 *
	 * @return the configuration store
	 */
	public static ConfigStore config() {
		return CONFIG;
	}

	/**
	 * Read the application configuration from default files.
	 *
	 * @return true, if successful
	 */
	public static boolean readConfig() {
		return readConfig(Netshot.CONFIG_FILENAMES);
	}

	/**
	 * Read the application configuration from the files (at startup). If no file can be read,
	 * the current configuration is kept.
	 *
	 * @param filenames the names of the files to read from
	 * @return true, if successful
	 */
	protected static boolean readConfig(String[] filenames) {
		return CONFIG.load(() -> Config.load(List.of(filenames)));
	}

	/**
	 * Reload the application configuration from the files (HUP signal). If no file can be read,
	 * the current configuration is kept.
	 *
	 * @param filenames the names of the files to read from
	 * @return true, if successful
	 */
	protected static boolean reloadConfig(String[] filenames) {
		return CONFIG.reload(() -> Config.load(List.of(filenames)));
	}

	/**
	 * Initialize the application configuration from Properties object.
	 *
	 * @param newConfig the configuration
	 * @return true
	 */
	protected static boolean initConfig(Properties newConfig) {
		CONFIG.replace(Config.of(newConfig));
		return true;
	}

	/**
	 * Check the JVM name and print a warning message if it's not GraalVM.
	 */
	protected static void checkJvm() {
		final String vendorVersion = System.getProperty("java.vendor.version"); // e.g. Oracle GraalVM 25.0.4+7.1
		final String vendor = System.getProperty("java.vendor"); // e.g. Oracle Corporation
		final String version = System.getProperty("java.vm.version"); // e.g. 25.0.4+7-LTS-jvmci-b01
		log.warn("Running on JVM: {}", vendorVersion);
		if (!vendorVersion.matches(".*GraalVM.*")) {
			log.error("The current JVM vendor version '{}' by '{}' doesn't look like GraalVM, Netshot might not work properly.", vendorVersion, vendor);
		}
		if (!version.matches("^25\\..*")) {
			log.error("The JVM version '{}' doesn't look like version 25, Netshot might not work properly.", version);
		}
	}

	/**
	 * Set up a custom truststore (CA certificates to trust).
	 */
	protected static void setTruststore() {
		String path = CONFIG.current().get(TRUSTSTORE_FILE);
		if (path != null) {
			System.setProperty("javax.net.ssl.trustStore", path);
		}
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
			System.out.println(String.format("Netshot version %s", Netshot.VERSION));
			return;
		}

		System.out.println(String.format("Starting Netshot version %s.", Netshot.VERSION));
		log.info("Starting Netshot");

		final String[] configFileNames;
		String configFilename = commandLine.getOptionValue("c");
		if (configFilename == null) {
			configFileNames = Netshot.CONFIG_FILENAMES;
		}
		else {
			configFileNames = new String[] { configFilename };
		}

		// The crypto libraries are registered before anything initializes the SSH library, which checks the
		// available crypto providers (EdDSA, X25519...) only once. Building the catalog of the settings initializes
		// the classes which declare them, the SSH ones included: the settings of the crypto libraries are read from
		// a first load of the configuration, without catalog (without the secrets), then the configuration is loaded
		// again with the catalog.
		if (!Netshot.readConfig(configFileNames)) {
			log.error(MarkerFactory.getMarker("FATAL"), "Unable to read the configuration. Exiting.");
			System.exit(1);
		}
		if (!LoggingSetup.apply(CONFIG.current())) {
			System.exit(1);
		}
		try {
			log.info("Registering crypto libraries");
			BouncyCastleLoader.registerBouncyCastle();
		}
		catch (Exception e) {
			System.err.println("NETSHOT FATAL ERROR: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
		CONFIG.setCatalog(NetshotSettings.catalog());
		if (!Netshot.readConfig(configFileNames)) {
			log.error(MarkerFactory.getMarker("FATAL"), "Unable to read the configuration. Exiting.");
			System.exit(1);
		}
		if (!LoggingSetup.apply(CONFIG.current())) {
			System.exit(1);
		}
		// Applied again on each configuration change (first, so that the other changes are logged accordingly)
		CONFIG.addListener(LoggingSetup::apply);
		CONFIG.catalog().warnUnknownItems(CONFIG.current());

		try {
			log.info("Checking current JVM.");
			Netshot.checkJvm();
			log.info("Selecting truststore.");
			Netshot.setTruststore();

			log.info("Updating the database schema, if necessary.");
			Database.update();

			Wiring wiring = Wiring.create(CONFIG);
			ServiceManager services = new ServiceManager(wiring.services());
			wiring.serviceHealthService().attach(services);
			// Stops the services (in the reverse order) on SIGTERM/SIGINT, or if the startup fails
			Runtime.getRuntime().addShutdownHook(services.shutdownHook());
			services.start();

			log.info("Starting signal listener.");
			Signal.handle(new Signal("HUP"), new SignalHandler() {
				@Override
				public void handle(Signal sig) {
					// The components are notified of the change by the configuration store
					Netshot.reloadConfig(configFileNames);
				}
			});
			log.warn("Netshot is started");

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
		instrumentation = inst;
	}

	/**
	 * Append a JAR to the system class loader search.
	 *
	 * @param jar the JAR file to append
	 * @throws Exception if instrumentation is not initialized
	 */
	public static void appendJar(JarFile jar) throws Exception {
		if (instrumentation == null) {
			throw new IllegalStateException("Instrumentation not initialized");
		}
		instrumentation.appendToSystemClassLoaderSearch(jar);
	}

}

