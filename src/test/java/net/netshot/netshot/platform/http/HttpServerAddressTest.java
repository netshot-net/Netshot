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
package net.netshot.netshot.platform.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("HTTP server address")
class HttpServerAddressTest {

	@Test
	@DisplayName("Port comes from the base URL")
	void portFromBaseUrl() {
		HttpServerAddress address = HttpServerAddress.of("http://localhost:8443", 0, true);
		assertEquals(8443, address.port());
		assertEquals("localhost", address.baseUri().getHost());
	}

	@Test
	@DisplayName("Base port overrides the port of the base URL")
	void basePortOverride() {
		HttpServerAddress address = HttpServerAddress.of("http://0.0.0.0:8443/", 9443, true);
		assertEquals(9443, address.port());
		assertEquals("0.0.0.0", address.baseUri().getHost());
		assertEquals("/", address.baseUri().getPath());
	}

	@Test
	@DisplayName("Port defaults to the standard port of the protocol")
	void defaultPort() {
		assertEquals(443, HttpServerAddress.of("http://netshot.example.com", 0, true).port());
		assertEquals(80, HttpServerAddress.of("http://netshot.example.com", 0, false).port());
	}
}
