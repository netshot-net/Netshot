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
package net.netshot.netshot.access.collector;

import java.net.UnknownHostException;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Collector address of a domain")
class CollectorTest {

	@AfterEach
	void resetConfig() {
		TestConfig.init(new Properties());
	}

	private static void overrides(String value) {
		Properties config = new Properties();
		config.setProperty("netshot.cluster.domainipoverride", value);
		TestConfig.init(config);
	}

	private static Domain domain(Network4Address address) {
		return Domain.builder().id(1).name("Domain").server4Address(address).build();
	}

	@Test
	@DisplayName("Without override, the domain server address is used")
	void noOverride() throws UnknownHostException {
		Assertions.assertEquals("10.0.0.1", Collector.getCollectorAddress(domain(new Network4Address("10.0.0.1"))).getIp());
	}

	@Test
	@DisplayName("The override of the domain server address is used")
	void override() throws UnknownHostException {
		overrides("10.0.0.9|192.168.0.9 10.0.0.1|192.168.0.1");
		Assertions.assertEquals("192.168.0.1", Collector.getCollectorAddress(domain(new Network4Address("10.0.0.1"))).getIp());
		Assertions.assertEquals("10.0.0.2", Collector.getCollectorAddress(domain(new Network4Address("10.0.0.2"))).getIp());
	}

	@Test
	@DisplayName("The override applies whatever the prefix length of the domain address")
	void overrideIgnoresPrefixLength() throws UnknownHostException {
		overrides("10.0.0.1|192.168.0.1");
		// As a domain created before 0.24 was stored (prefix length 0)
		Assertions.assertEquals("192.168.0.1", Collector.getCollectorAddress(domain(new Network4Address("10.0.0.1", 0))).getIp());
	}

	@Test
	@DisplayName("A domain without server address is refused")
	void noServerAddress() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> Collector.getCollectorAddress(domain(null)));
	}
}
