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
package net.netshot.netshot.boot;

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
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.Setting;

/**
 * Sets up the logging (main log, audit log, remote Syslog) from the configuration, at startup
 * and again on each configuration change.
 */
@Slf4j
public final class LoggingSetup {

	/** Location of the main log file ({@code CONSOLE} to log to the console). */
	static final Setting<String> FILE = Setting.text("netshot.log.file").defaultValue("netshot.log").live();

	/** Global log level. */
	static final Setting<String> LEVEL = Setting.text("netshot.log.level").live();

	/** Number of rotated log files to keep. */
	static final Setting<Integer> COUNT = Setting.integer("netshot.log.count").defaultValue(5).range(1, 65535).live();

	/** Max size (MB) of the log file before rotation. */
	static final Setting<Integer> MAX_SIZE = Setting.integer("netshot.log.maxsize").defaultValue(2).range(1, Integer.MAX_VALUE).live();

	/** Log level of a class or package logger (from the configuration file only). */
	static final Setting<String> CLASS_LEVEL = Setting.text("netshot.log.class.<ClassName>").live();

	/** Location of the audit log file. */
	static final Setting<String> AUDIT_FILE = Setting.text("netshot.log.audit.file").live();

	/** Level of the audit messages. */
	static final Setting<String> AUDIT_LEVEL = Setting.text("netshot.log.audit.level").live();

	/** Number of rotated audit log files to keep. */
	static final Setting<Integer> AUDIT_COUNT = Setting.integer("netshot.log.audit.count").defaultValue(5).range(1, 65535).live();

	/** Max size (MB) of the audit log file before rotation. */
	static final Setting<Integer> AUDIT_MAX_SIZE = Setting.integer("netshot.log.audit.maxsize")
		.defaultValue(2).range(1, Integer.MAX_VALUE).live();

	/** Host of a remote Syslog destination. */
	static final Setting<String> SYSLOG_HOST = Setting.text("netshot.log.syslogN.host").live();

	/** Port of a remote Syslog destination. */
	static final Setting<Integer> SYSLOG_PORT = Setting.integer("netshot.log.syslogN.port").defaultValue(514).range(1, 65535).live();

	/** Facility of a remote Syslog destination. */
	static final Setting<String> SYSLOG_FACILITY = Setting.text("netshot.log.syslogN.facility").defaultValue("LOCAL7").live();

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private LoggingSetup() {
	}

	/**
	 * Sets up all the logging from the configuration.
	 *
	 * @param config the configuration
	 * @return true if the main and audit logging could be set up
	 */
	public static boolean apply(Config config) {
		return initMainLogging(config) && initAuditLogging(config) && initSyslogLogging(config);
	}

	/**
	 * Initializes the logging.
	 *
	 * @param config the configuration
	 * @return true, if successful
	 */
	static boolean initMainLogging(Config config) {
		// Redirect JUL to SLF4J
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();

		ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)
			LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
		LoggerContext loggerContext = rootLogger.getLoggerContext();

		System.setProperty("org.jboss.logging.provider", "slf4j");

		String logFile = config.get(FILE);
		String logLevelCfg = config.get(LEVEL);

		int logCount = config.get(COUNT);
		int logMaxSize = config.get(MAX_SIZE);

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

		// jOOQ (open source edition) warns at each start that the PostgreSQL version is older than the latest one,
		// the only one it officially supports: the supported versions are tested (see the backend architecture docs)
		((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.jooq.impl.DefaultExecuteContext.logVersionSupport"))
			.setLevel(Level.ERROR);

		// Only from the configuration file (the environment variable names can't carry the class names)
		final String classPrefix = CLASS_LEVEL.key().substring(0, CLASS_LEVEL.key().indexOf('<'));
		for (String propertyName : config.fileKeys()) {
			if (CLASS_LEVEL.matches(propertyName)) {
				String className = propertyName.substring(classPrefix.length());
				String propertyValue = config.get(CLASS_LEVEL.at(className));
				className = className.trim();
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
	 * @param config the configuration
	 * @return true if everything went well
	 */
	static boolean initAuditLogging(Config config) {
		String auditFile = config.get(AUDIT_FILE);
		String auditLevelCfg = config.get(AUDIT_LEVEL);
		int auditCount = config.get(AUDIT_COUNT);
		int auditMaxSize = config.get(AUDIT_MAX_SIZE);

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
		return true;
	}

	/**
	 * Initialize remote Syslog logging.
	 *
	 * @param config the configuration
	 * @return true if everything went fine
	 */
	static boolean initSyslogLogging(Config config) {

		ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)
			LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
		LoggerContext loggerContext = rootLogger.getLoggerContext();

		int syslogIndex = 1;
		while (true) {
			String host = config.get(SYSLOG_HOST.at(syslogIndex));
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
			int port = config.get(SYSLOG_PORT.at(syslogIndex));
			appender.setPort(port);
			appender.setFacility(config.get(SYSLOG_FACILITY.at(syslogIndex)));
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
}
