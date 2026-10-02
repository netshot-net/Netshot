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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.util.Enumeration;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.net.SyslogAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.Oidc;
import net.netshot.netshot.aaa.PasswordPolicy;
import net.netshot.netshot.aaa.Radius;
import net.netshot.netshot.aaa.Tacacs;
import net.netshot.netshot.aaa.UiUser;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.compliance.rules.JavaScriptRule;
import net.netshot.netshot.compliance.rules.PythonRule;
import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.access.Ssh;
import net.netshot.netshot.device.access.Telnet;
import net.netshot.netshot.device.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.device.collector.Collector;
import net.netshot.netshot.device.collector.SnmpTrapReceiver;
import net.netshot.netshot.device.collector.SshServer;
import net.netshot.netshot.device.collector.SyslogServer;
import net.netshot.netshot.device.script.helper.PythonFileSystem;
import net.netshot.netshot.rest.LoggerFilter;
import net.netshot.netshot.rest.RestService;
import net.netshot.netshot.utils.BouncyCastleLoader;
import net.netshot.netshot.vault.VaultManager;
import net.netshot.netshot.work.TaskLockManager;
import net.netshot.netshot.work.tasks.TakeSnapshotTask;
//CHECKSTYLE:OFF: IllegalImport
import sun.misc.Signal;
import sun.misc.SignalHandler;
//CHECKSTYLE:ON: IllegalImport

/**
 * The Class Netshot. Starting point of Netshot
 */
@Slf4j
public class Netshot extends Thread {

	/** Netshot version. */
	public static final String VERSION = "0.0.0-dev";

	/** The list of configuration files to look at, in sequence. */
	private static final String[] CONFIG_FILENAMES = new String[] {
		"netshot.conf", "/etc/netshot.conf"};

	/** The application configuration as retrieved from the configuration file. */
	private static Properties config;

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/** The machine hostname. */
	private static String hostname;

	/** Java instrumentation. */
	private static volatile Instrumentation instrumentation;

	/**
	 * Retrieve the local machine hostname.
	 *
	 * @return the local machine hostname
	 */
	public static String getHostname() {
		if (Netshot.hostname == null) {
			try {
				Process process = Runtime.getRuntime().exec(new String[] { "hostname" });
				BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
				Netshot.hostname = stdInput.readLine();
			}
			catch (IOException e) {
				log.error("Error while getting local machine hostname", e);
			}
			if (Netshot.hostname == null) {
				Netshot.hostname = "unknown";
			}
		}
		return Netshot.hostname;
	}

	/**
	 * Gets the config.
	 *
	 * @return the config
	 */
	public static Properties getConfig() {
		return config;
	}

	/**
	 * Gets the config item as boolean value.
	 *
	 * @param key the key
	 * @param defaultValue the default value
	 * @return the config
	 */
	public static boolean getConfig(String key, boolean defaultValue) {
		String value = getConfig(key);
		if ("false".equals(value)) {
			return false;
		}
		if ("true".equals(value)) {
			return true;
		}
		if (value != null) {
			log.error("Unable to parse the boolean value of configuration item '{}', using default value {}.",
				key, defaultValue);
		}
		return defaultValue;
	}

	/**
	 * Gets the config.
	 *
	 * @param key the key
	 * @param defaultValue the default value
	 * @return the config
	 */
	public static String getConfig(String key, String defaultValue) {
		String value = getConfig(key);
		return value == null ? defaultValue : value;
	}

	/**
	 * Gets a config item as an Integer.
	 *
	 * @param key the config key
	 * @param defaultValue the default value
	 * @return the config
	 */
	public static int getConfig(String key, int defaultValue) {
		String textValue = getConfig(key);
		if (textValue != null) {
			try {
				return Integer.parseInt(textValue);
			}
			catch (Exception e) {
				log.error("Unable to parse the integer value of configuration item '{}', using default value {}.",
					key, defaultValue);
			}
		}
		return defaultValue;
	}

	/**
	 * Gets a config item as an Integer.
	 *
	 * @param key the config key
	 * @param defaultValue the default value
	 * @param min the minimum acceptable value
	 * @param max the maximum acceptable value
	 * @return the config
	 */
	public static int getConfig(String key, int defaultValue, int min, int max) {
		int value = Netshot.getConfig(key, defaultValue);
		if (value < min || value > max) {
			log.error("Unacceptable integer value for configuration item '{}' (not in the range {} to {}), using default value {}",
					key, min, max, defaultValue);
			return defaultValue;
		}
		return value;
	}

	/**
	 * Gets the config.
	 *
	 * @param key the key
	 * @return the config
	 */
	public static String getConfig(String key) {
		String envKey = key.replace(".", "_").toUpperCase();
		try {
			String value = System.getenv(envKey);
			if (value != null) {
				return value;
			}
		}
		catch (SecurityException e) {
			log.warn("Security exception raised while getting environment variable {}", envKey, e);
		}
		return config.getProperty(key);
	}

	/**
	 * Read the application configuration from default files.
	 *
	 * @return true, if successful
	 */
	protected static boolean readConfig() {
		return readConfig(Netshot.CONFIG_FILENAMES);
	}

	/**
	 * Read the application configuration from the files.
	 *
	 * @param filenames the names of the files to read from
	 * @return true, if successful
	 */
	protected static boolean readConfig(String[] filenames) {
		Netshot.config = new Properties();
		for (String fileName : filenames) {
			try (InputStream fileStream = new FileInputStream(fileName)) {
				log.trace("Trying to load the configuration file {}.", fileName);
				Netshot.config.load(fileStream);
				log.warn("Configuration file {} successfully read.", fileName);
				break;
			}
			catch (Exception e) {
				log.warn("Unable to read the configuration file {}.", fileName);
			}
		}
		if (Netshot.config.isEmpty()) {
			log.error(MarkerFactory.getMarker("FATAL"), "No configuration file was found. Exiting.");
			return false;
		}
		return true;
	}

	/**
	 * Initialize the application configuration from Properties object.
	 *
	 * @param newConfig the configuration
	 * @return true
	 */
	protected static boolean initConfig(Properties newConfig) {
		Netshot.config = newConfig;
		return true;
	}

	/**
	 * Initializes the logging.
	 *
	 * @return true, if successful
	 */
	protected static boolean initMainLogging() {
		// Redirect JUL to SLF4J
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();

		ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)
			LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
		LoggerContext loggerContext = rootLogger.getLoggerContext();

		System.setProperty("org.jboss.logging.provider", "slf4j");

		String logFile = Netshot.getConfig("netshot.log.file", "netshot.log");
		String logLevelCfg = Netshot.getConfig("netshot.log.level");

		int logCount = Netshot.getConfig("netshot.log.count", 5, 1, 65535);
		int logMaxSize = Netshot.getConfig("netshot.log.maxsize", 2, 1, Integer.MAX_VALUE);

		Level logLevel = Level.toLevel(logLevelCfg, Level.WARN);
		if (logLevelCfg != null && !logLevel.toString().equals(logLevelCfg)) {
			log.error("Invalid log level (netshot.log.level) '{}'. Using {}.", logLevelCfg, logLevel);
		}

		OutputStreamAppender<ILoggingEvent> appender;

		if ("CONSOLE".equals(logFile)) {
			ConsoleAppender<ILoggingEvent> cAppender = new ConsoleAppender<>();
			log.info("Will go on logging to the console.");
			appender = cAppender;
		}
		else {
			log.info("Switching to file logging, into {}, level {}, rotation using {} files of max {}MB.",
				logFile, logLevel, logCount, logMaxSize);

			try {
				RollingFileAppender<ILoggingEvent> rfAppender = new RollingFileAppender<>();
				rfAppender.setContext(loggerContext);
				rfAppender.setFile(logFile);

				FixedWindowRollingPolicy fwRollingPolicy = new FixedWindowRollingPolicy();
				fwRollingPolicy.setContext(loggerContext);
				fwRollingPolicy.setFileNamePattern(logFile + ".%i.gz");
				fwRollingPolicy.setMinIndex(1);
				fwRollingPolicy.setMaxIndex(logCount);
				fwRollingPolicy.setParent(rfAppender);
				fwRollingPolicy.start();

				SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new
					SizeBasedTriggeringPolicy<>();
				triggeringPolicy.setMaxFileSize(new FileSize(logMaxSize * FileSize.MB_COEFFICIENT));
				triggeringPolicy.start();

				rfAppender.setRollingPolicy(fwRollingPolicy);
				rfAppender.setTriggeringPolicy(triggeringPolicy);
				appender = rfAppender;

			}
			catch (Exception e) {
				log.error(MarkerFactory.getMarker("FATAL"), "Unable to log into file {}. Exiting.", logFile, e);
				return false;
			}
		}

		loggerContext.reset();
		rootLogger.setLevel(logLevel);
		PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setContext(loggerContext);
		encoder.setPattern("%d %-5level [%thread] %logger{0}: %msg%n");
		encoder.start();
		appender.setEncoder(encoder);
		appender.setContext(loggerContext);
		appender.start();
		final String appenderName = "Netshot appender";
		appender.setName(appenderName);
		rootLogger.detachAppender(appenderName);
		rootLogger.addAppender(appender);

		Pattern logSetting = Pattern.compile("^netshot\\.log\\.class\\.(?<class>.*)");
		Enumeration<?> propertyNames = Netshot.config.propertyNames();
		while (propertyNames.hasMoreElements()) {
			String propertyName = (String) propertyNames.nextElement();
			Matcher matcher = logSetting.matcher(propertyName);
			if (matcher.find()) {
				String propertyValue = Netshot.getConfig(propertyName);
				String className = matcher.group("class").trim();
				ch.qos.logback.classic.Logger classLogger = (ch.qos.logback.classic.Logger)
					LoggerFactory.getLogger(className);
				try {
					Level classLevel = Level.valueOf(propertyValue);
					classLogger.setLevel(classLevel);
					log.info("Assigning level {} to class {}.", classLevel, className);
				}
				catch (Exception e) {
					log.error("Invalid log level for class {}.", className);
				}
			}
		}

		return true;
	}

	/**
	 * Initialize the audit logging.
	 * @return true if everything went well
	 */
	protected static boolean initAuditLogging() {
		String auditFile = Netshot.getConfig("netshot.log.audit.file");
		String auditLevelCfg = Netshot.getConfig("netshot.log.audit.level");
		int auditCount = Netshot.getConfig("netshot.log.audit.count", 5, 1, 65535);
		int auditMaxSize = Netshot.getConfig("netshot.log.audit.maxsize", 2, 1, Integer.MAX_VALUE);

		((ch.qos.logback.classic.Logger) AAA_LOG).setAdditive(false);
		Level logLevel = Level.toLevel(auditLevelCfg, Level.OFF);
		if (auditLevelCfg != null && !logLevel.toString().equals(auditLevelCfg)) {
			log.error("Invalid log level (netshot.log.audit.level) '{}'. Using {}.", auditLevelCfg, logLevel);
		}
		((ch.qos.logback.classic.Logger) AAA_LOG).setLevel(logLevel);

		if (auditFile != null) {
			try {
				LoggerContext loggerContext = ((ch.qos.logback.classic.Logger) AAA_LOG).getLoggerContext();
				RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
				appender.setContext(loggerContext);
				appender.setFile(auditFile);

				FixedWindowRollingPolicy fwRollingPolicy = new FixedWindowRollingPolicy();
				fwRollingPolicy.setContext(loggerContext);
				fwRollingPolicy.setFileNamePattern(auditFile + ".%i.gz");
				fwRollingPolicy.setMinIndex(1);
				fwRollingPolicy.setMaxIndex(auditCount);
				fwRollingPolicy.setParent(appender);
				fwRollingPolicy.start();

				SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new
					SizeBasedTriggeringPolicy<>();
				triggeringPolicy.setMaxFileSize(new FileSize(auditMaxSize * FileSize.MB_COEFFICIENT));
				triggeringPolicy.start();

				appender.setRollingPolicy(fwRollingPolicy);
				appender.setTriggeringPolicy(triggeringPolicy);
				PatternLayoutEncoder encoder = new PatternLayoutEncoder();
				encoder.setContext(loggerContext);
				encoder.setPattern("%d %logger{0}: %msg%n");
				encoder.start();
				appender.setEncoder(encoder);
				appender.setContext(loggerContext);

				appender.start();
				((ch.qos.logback.classic.Logger) AAA_LOG).addAppender(appender);
				log.warn("Audit information will be logged to {}.", auditFile);
				AAA_LOG.error("Audit starting.");
			}
			catch (Exception e) {
				log.error("Unable to log AAA data into file {}. Exiting.", auditFile, e);
			}
		}
		LoggerFilter.init();
		return true;
	}

	/** The exception handler. To be used by other threads. */
	public static Thread.UncaughtExceptionHandler exceptionHandler = new Thread.UncaughtExceptionHandler() {
		@Override
		public void uncaughtException(Thread th, Throwable ex) {
			System.err.println("NETSHOT FATAL ERROR");
			ex.printStackTrace();
			System.exit(1);
		}
	};

	/**
	 * Initialize remote Syslog logging.
	 *
	 * @return true if everything went fine
	 */
	protected static boolean initSyslogLogging() {

		ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)
			LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
		LoggerContext loggerContext = rootLogger.getLoggerContext();

		int syslogIndex = 1;
		while (true) {
			String host = Netshot.getConfig(String.format("netshot.log.syslog%d.host", syslogIndex));
			if (host == null) {
				break;
			}
			host = host.trim();
			if ("".equals(host)) {
				break;
			}
			SyslogAppender appender = new SyslogAppender();
			appender.setSyslogHost(host);
			appender.setContext(loggerContext);
			appender.setSuffixPattern("[Netshot] %logger{0}: %msg");
			int port = Netshot.getConfig(String.format("netshot.log.syslog%d.port", syslogIndex), 514, 1, 65535);
			appender.setPort(port);
			String facility = Netshot.getConfig(String.format("netshot.log.syslog%d.facility", syslogIndex));
			appender.setFacility(facility == null ? "LOCAL7" : facility);
			rootLogger.addAppender(appender);
			((ch.qos.logback.classic.Logger) AAA_LOG).addAppender(appender);
			try {
				appender.start();
			}
			catch (Exception e) {
				log.error("Unable to start syslog instance {}: {}.", syslogIndex, e.getMessage());
			}
			log.warn("Logging to syslog {}:{} has started", appender.getSyslogHost(), appender.getPort());
			syslogIndex++;
		}

		return true;
	}

	/**
	 * Check the JVM name and print a warning message if it's not GraalVM.
	 */
	protected static void checkJvm() {
		final String vendorVersion = System.getProperty("java.vendor.version"); // e.g. Oracle GraalVM 17.0.8+9.1
		final String vendor = System.getProperty("java.vendor"); // e.g. Oracle Corporation
		final String version = System.getProperty("java.vm.version"); // e.g. 11.0.10+8-jvmci-21.0-b06
		log.warn("Running on JVM: {}", vendorVersion);
		if (!vendorVersion.matches(".*GraalVM.*")) {
			log.error("The current JVM vendor version '{}' by '{}' doesn't look like GraalVM, Netshot might not work properly.", vendorVersion, vendor);
		}
		if (!version.matches("^21\\..*")) {
			log.error("The JVM version '{}' doesn't look like version 21, Netshot might not work properly.", version);
		}
	}

	/**
	 * Set up a custom truststore (CA certificates to trust).
	 */
	protected static void setTruststore() {
		String path = Netshot.getConfig("netshot.ssl.truststore.file");
		if (path != null) {
			System.setProperty("javax.net.ssl.trustStore", path);
		}
	}

	/**
	 * Load (or reload) the configuration of various modules.
	 */
	public static void loadModuleConfigs() {
		log.info("Loading module configurations.");
		PasswordPolicy.loadConfig();
		UiUser.loadConfig();
		Radius.loadConfig();
		Tacacs.loadConfig();
		Oidc.loadConfig();
		TakeSnapshotTask.loadConfig();
		JavaScriptRule.loadConfig();
		PythonRule.loadConfig();
		PythonFileSystem.loadConfig();
		Ssh.loadConfig();
		Telnet.loadConfig();
		Collector.loadConfig();
		ConfigBinaryFileAttribute.loadConfig();
		VaultManager.loadConfig();
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

		if (!Netshot.readConfig(configFileNames)) {
			System.exit(1);
		}
		if (!Netshot.initMainLogging()
				|| !Netshot.initAuditLogging()
				|| !Netshot.initSyslogLogging()) {
			System.exit(1);
		}

		try {
			log.info("Checking current JVM.");
			Netshot.checkJvm();
			log.info("Selecting truststore.");
			Netshot.setTruststore();

			log.info("Registering crypto libraries");
			BouncyCastleLoader.registerBouncyCastle();

			log.info("Updating the database schema, if necessary.");
			Database.update();
			log.info("Initializing access to the database.");
			Database.init();

			Netshot.loadModuleConfigs();

			log.info("Loading the device drivers.");
			DeviceDriver.refreshDrivers();
			log.info("Starting the Syslog server.");
			SyslogServer.init();
			log.info("Starting the SNMP v1/v2c/v3 trap receiver.");
			SnmpTrapReceiver.init();
			log.info("Starting the SSH/SCP/SFTP server.");
			SshServer.init();
			//log.info("Starting the TFTP server.");
			//TftpServer.init();

			// Before the clustering manager, which can start running tasks
			TaskLockManager.init(Netshot.getConfig("netshot.cluster.enabled", false));

			log.info("Starting the clustering manager.");
			ClusterManager.init();

			log.info("Starting the Vault token refresh daemon.");
			VaultManager.init();

			log.info("Initializing the task manager.");
			TaskManager.init();
			log.info("Scheduling the existing tasks.");
			TaskManager.rescheduleAll();
			log.info("Cancelling orphaned delayed tasks.");
			TaskManager.cancelOrphanedDelayedTasks();
			log.info("Starting the REST service.");
			RestService.init();

			log.info("Starting signal listener.");
			Signal.handle(new Signal("HUP"), new SignalHandler() {
				@Override
				public void handle(Signal sig) {
					Netshot.readConfig(configFileNames);
					Netshot.initMainLogging();
					Netshot.initAuditLogging();
					Netshot.initSyslogLogging();
					Netshot.loadModuleConfigs();
					SnmpTrapReceiver.reload();
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

