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
package net.netshot.netshot.api.rest.v0;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

import org.glassfish.jersey.server.ApplicationHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.api.rest.RestServer;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestNetwork;
import net.netshot.netshot.testing.TestWiring;

@DatabaseTest
@DisplayName("v0 web application")
public class V0ApplicationTest {

	@Test
	@DisplayName("Makes the wiring injectable into the resources")
	void injection() {
		ApplicationHandler handler = new ApplicationHandler(new V0Application(TestWiring.get().binder()));
		Transactions injected = handler.getInjectionManager().getInstance(Transactions.class);
		Assertions.assertSame(TestWiring.get().transactions(), injected);
	}

	@Test
	@DisplayName("Served by the REST server when enabled")
	void servedWhenEnabled() throws Exception {
		this.checkServed(true);
	}

	@Test
	@DisplayName("Not served by the REST server when disabled")
	void notServedWhenDisabled() throws Exception {
		this.checkServed(false);
	}

	/**
	 * Starts a REST server with the v0 API enabled or not, and checks that it serves the v0 API accordingly.
	 *
	 * @param enabled the value of netshot.http.api.v0.enabled
	 */
	private void checkServed(boolean enabled) throws Exception {
		int port = TestNetwork.freePort();
		Properties original = TestConfig.properties();
		Properties config = TestConfig.properties();
		config.setProperty("netshot.http.ssl.enabled", "false");
		config.setProperty("netshot.http.baseurl", "http://127.0.0.1:%d".formatted(port));
		config.setProperty("netshot.http.api.v0.enabled", Boolean.toString(enabled));
		TestConfig.init(config);
		RestServer server = new RestServer(TestWiring.get().binder());
		server.start();
		try {
			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> user = client.send(
				HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d/api/user".formatted(port))).build(),
				HttpResponse.BodyHandlers.ofString());
			Assertions.assertEquals(enabled ? 401 : 404, user.statusCode(), "GET /api/user");
			HttpResponse<String> document = client.send(
				HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d/api/openapi.json".formatted(port))).build(),
				HttpResponse.BodyHandlers.ofString());
			Assertions.assertEquals(enabled ? 200 : 404, document.statusCode(), "GET /api/openapi.json");
		}
		finally {
			server.stop();
			TestConfig.init(original);
		}
	}
}
