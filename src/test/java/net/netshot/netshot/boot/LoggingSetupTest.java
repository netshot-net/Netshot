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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.classic.net.SyslogAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Logging setup")
class LoggingSetupTest {

	@Test
	@DisplayName("The log levels are read whatever their case; an invalid or missing one gives the default")
	void level() {
		Assertions.assertEquals(Level.INFO, LoggingSetup.level("INFO", Level.WARN, "netshot.log.level"));
		Assertions.assertEquals(Level.INFO, LoggingSetup.level("info", Level.WARN, "netshot.log.level"));
		Assertions.assertEquals(Level.DEBUG, LoggingSetup.level("Debug", Level.WARN, "netshot.log.level"));
		Assertions.assertEquals(Level.OFF, LoggingSetup.level("off", Level.WARN, "netshot.log.level"));
		Assertions.assertEquals(Level.WARN, LoggingSetup.level("verbose", Level.WARN, "netshot.log.level"));
		Assertions.assertEquals(Level.OFF, LoggingSetup.level(null, Level.OFF, "netshot.log.audit.level"));
	}

	@Test
	@DisplayName("A remote Syslog which can't be started isn't attached to the loggers")
	void syslogNotStarted() {
		Properties properties = new Properties();
		properties.setProperty("netshot.log.syslog1.host", "unknown-host.invalid");
		Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		try {
			LoggingSetup.initSyslogLogging(Config.of(properties));
			Assertions.assertFalse(hasSyslog(root), "A Syslog appender is attached");
		}
		finally {
			root.iteratorForAppenders().forEachRemaining(appender -> {
				if (appender instanceof SyslogAppender) {
					root.detachAppender(appender);
				}
			});
		}
	}

	private static boolean hasSyslog(Logger logger) {
		Iterator<Appender<ILoggingEvent>> appenders = logger.iteratorForAppenders();
		while (appenders.hasNext()) {
			if (appenders.next() instanceof SyslogAppender) {
				return true;
			}
		}
		return false;
	}

	@TempDir
	Path tempDir;

	private static Config config(String... items) {
		Properties properties = new Properties();
		for (int i = 0; i < items.length; i += 2) {
			properties.setProperty(items[i], items[i + 1]);
		}
		return Config.of(properties, Map.of());
	}

	/** Gives the tests their logging back (logback-test.xml), after a test replaced it. */
	private static void restoreTestLogging() throws Exception {
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		context.reset();
		new ContextInitializer(context).autoConfig();
	}

	private static String read(Path file) throws Exception {
		return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
	}

	@Test
	@DisplayName("The audit messages go to the audit file only, from the configured level")
	void auditFile() throws Exception {
		Path auditFile = this.tempDir.resolve("audit.log");
		Logger aaa = (Logger) LoggerFactory.getLogger("AAA");
		try {
			LoggingSetup.initAuditLogging(config("netshot.log.audit.file", auditFile.toString(), "netshot.log.audit.level", "info"));
			aaa.info("User admin logged in.");
			aaa.debug("Debug detail.");

			String audit = read(auditFile);
			Assertions.assertTrue(audit.contains(" AAA: Audit starting.\n"), audit);
			Assertions.assertTrue(audit.contains(" AAA: User admin logged in.\n"), audit);
			Assertions.assertFalse(audit.contains("Debug detail."), audit);
			Assertions.assertFalse(aaa.isAdditive(), "The audit messages also go to the main log");
		}
		finally {
			restoreTestLogging();
		}
	}

	@Test
	@DisplayName("Without audit level, nothing is audited")
	void auditOff() throws Exception {
		Path auditFile = this.tempDir.resolve("audit.log");
		Logger aaa = (Logger) LoggerFactory.getLogger("AAA");
		try {
			LoggingSetup.initAuditLogging(config("netshot.log.audit.file", auditFile.toString()));
			aaa.error("User admin logged in.");
			Assertions.assertEquals("", read(auditFile));
		}
		finally {
			restoreTestLogging();
		}
	}

	@Test
	@DisplayName("The main log goes to the configured file, at the configured level, with the valid class levels of the file")
	void mainFile() throws Exception {
		Path logFile = this.tempDir.resolve("netshot.log");
		try {
			LoggingSetup.initMainLogging(config("netshot.log.file", logFile.toString(), "netshot.log.level", "info",
				"netshot.log.class.net.netshot.lab.Verbose", "DEBUG", "netshot.log.class.net.netshot.lab.Loud", "LOUD"));
			LoggerFactory.getLogger("net.netshot.lab.Normal").info("Normal info.");
			LoggerFactory.getLogger("net.netshot.lab.Normal").debug("Normal debug.");
			LoggerFactory.getLogger("net.netshot.lab.Verbose").debug("Verbose debug.");

			String log = read(logFile);
			Assertions.assertTrue(log.contains(" INFO  [main] Normal: Normal info.\n"), log);
			Assertions.assertFalse(log.contains("Normal debug."), log);
			Assertions.assertTrue(log.contains("Verbose: Verbose debug.\n"), log);
			Assertions.assertEquals(Level.DEBUG, ((Logger) LoggerFactory.getLogger("net.netshot.lab.Verbose")).getLevel());
			Assertions.assertTrue(log.contains("Invalid log level 'LOUD' for class net.netshot.lab.Loud, its level is not changed.\n"), log);
			Assertions.assertNull(((Logger) LoggerFactory.getLogger("net.netshot.lab.Loud")).getLevel());
		}
		finally {
			restoreTestLogging();
		}
	}

	@Test
	@DisplayName("A log file which can't be written fails the setup, and the current logging is kept")
	void mainFileNotWritable() throws Exception {
		Path notADirectory = Files.writeString(this.tempDir.resolve("file"), "x");
		Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		List<Appender<ILoggingEvent>> before = new ArrayList<>();
		root.iteratorForAppenders().forEachRemaining(before::add);
		try {
			IllegalStateException error = Assertions.assertThrows(IllegalStateException.class,
				() -> LoggingSetup.initMainLogging(config("netshot.log.file", notADirectory.resolve("netshot.log").toString())));
			Assertions.assertTrue(error.getMessage().startsWith("Unable to log into file " + notADirectory.resolve("netshot.log")), error.getMessage());
			List<Appender<ILoggingEvent>> after = new ArrayList<>();
			root.iteratorForAppenders().forEachRemaining(after::add);
			Assertions.assertEquals(before, after, "The current appenders were replaced");
		}
		finally {
			restoreTestLogging();
		}
	}
}
