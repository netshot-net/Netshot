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

import java.util.Iterator;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
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
}
