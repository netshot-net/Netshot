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
package net.netshot.netshot.access;

import java.time.Duration;
import java.util.Properties;

import org.apache.sshd.core.CoreModuleProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("SSH client service")
class SshClientServiceTest {

	@AfterEach
	void resetConfig() {
		TestConfig.init(new Properties());
	}

	private static void configure(int receiveTimeout) {
		Properties config = new Properties();
		config.setProperty("netshot.cli.ssh.receivetimeout", Integer.toString(receiveTimeout));
		TestConfig.init(config);
	}

	@Test
	@DisplayName("Applies the SSH settings to the client, and their changes while it runs")
	void appliesSettings() {
		configure(12000);
		SshClientService service = new SshClientService(TestConfig.store(), new AccessSettings(TestConfig.store()));
		Assertions.assertEquals(Duration.ofMillis(12000), CoreModuleProperties.NIO2_READ_TIMEOUT.getRequired(service.client()));
		service.start();
		try {
			configure(34000);
			Assertions.assertEquals(Duration.ofMillis(34000), CoreModuleProperties.NIO2_READ_TIMEOUT.getRequired(service.client()));
		}
		finally {
			service.stop();
		}
		configure(56000);
		Assertions.assertEquals(Duration.ofMillis(34000), CoreModuleProperties.NIO2_READ_TIMEOUT.getRequired(service.client()),
			"Not applied once stopped");
	}
}
