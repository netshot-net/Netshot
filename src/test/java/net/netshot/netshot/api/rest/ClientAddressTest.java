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
package net.netshot.netshot.api.rest;

import java.lang.reflect.Proxy;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import net.netshot.netshot.testing.TestConfig;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Client address of a request")
class ClientAddressTest {

	/**
	 * A request from an address, with an X-Forwarded-For header.
	 *
	 * @param remoteAddress the address the request comes from
	 * @param forwardedFor the X-Forwarded-For header, or null
	 * @return the request
	 */
	private static HttpServletRequest request(String remoteAddress, String forwardedFor) {
		return (HttpServletRequest) Proxy.newProxyInstance(ClientAddressTest.class.getClassLoader(), new Class<?>[] {HttpServletRequest.class},
			(_, method, args) -> switch (method.getName()) {
				case "getRemoteAddr" -> remoteAddress;
				case "getHeader" -> "X-Forwarded-For".equals(args[0]) ? forwardedFor : null;
				default -> throw new UnsupportedOperationException(method.getName());
			});
	}

	@AfterEach
	void resetConfig() {
		TestConfig.init(new Properties());
	}

	@Test
	@DisplayName("The X-Forwarded-For header is ignored unless trusted, and the trust follows the configuration")
	void forwardedFor() {
		ClientAddress clientAddress = new ClientAddress(TestConfig.store());
		Assertions.assertFalse(clientAddress.trustsForwardedFor());
		Assertions.assertEquals("10.0.0.1", clientAddress.of(request("10.0.0.1", "192.0.2.7, 10.0.0.1")));
		Properties config = new Properties();
		config.setProperty("netshot.http.trustxforwardedfor", "true");
		TestConfig.init(config);
		Assertions.assertTrue(clientAddress.trustsForwardedFor());
		Assertions.assertEquals("192.0.2.7", clientAddress.of(request("10.0.0.1", "192.0.2.7, 10.0.0.1")));
		Assertions.assertEquals("10.0.0.1", clientAddress.of(request("10.0.0.1", null)));
	}
}
