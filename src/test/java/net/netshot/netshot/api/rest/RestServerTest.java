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
package net.netshot.netshot.api.rest;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestNetwork;
import net.netshot.netshot.testing.TestWiring;

@DatabaseTest
@DisplayName("Web/REST server service")
public class RestServerTest {

	@Test
	@DisplayName("Serves the API once started, counts the requests by status class, and releases the port when stopped")
	void startCountStop() throws Exception {
		int port = TestNetwork.freePort();
		Properties config = TestConfig.properties();
		config.setProperty("netshot.http.ssl.enabled", "false");
		config.setProperty("netshot.http.baseurl", "http://127.0.0.1:%d".formatted(port));
		TestConfig.init(config);
		RestServer server = new RestServer(TestWiring.get().binder());
		Assertions.assertFalse(server.isHealthy());
		server.start();
		try {
			Assertions.assertTrue(server.isHealthy());
			HttpClient client = HttpClient.newHttpClient();
			HttpResponse<String> response = client.send(
				HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d/api/user".formatted(port))).build(),
				HttpResponse.BodyHandlers.ofString());
			Assertions.assertEquals(401, response.statusCode());
			client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:%d/api/nothing".formatted(port))).build(),
				HttpResponse.BodyHandlers.ofString());
			// Counted once the exchange is complete, possibly just after the client got the response
			long deadline = System.currentTimeMillis() + 5000;
			while ((Long) server.stats().get("requests") < 2L && System.currentTimeMillis() < deadline) {
				Thread.sleep(20);
			}
			Assertions.assertEquals(2L, server.stats().get("requests"));
			Assertions.assertEquals(2L, server.stats().get("4xx"));
			Assertions.assertEquals(0L, server.stats().get("2xx"));
			Assertions.assertEquals(0, server.stats().get("sessions"));
		}
		finally {
			server.stop();
		}
		Assertions.assertFalse(server.isHealthy());
		try (ServerSocket socket = new ServerSocket(port)) {
			Assertions.assertTrue(socket.isBound(), "The port wasn't released");
		}
	}
}
