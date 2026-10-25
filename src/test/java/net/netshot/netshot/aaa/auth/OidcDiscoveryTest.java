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
package net.netshot.netshot.aaa.auth;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.testing.TestNetwork;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.fake.FakeOidcIdpServer;

@UnitTest
@DisplayName("OIDC IdP discovery service")
public class OidcDiscoveryTest {

	private FakeOidcIdpServer idpServer;

	private Oidc oidc;

	private OidcDiscovery discovery;

	@BeforeEach
	void startIdp() throws Exception {
		this.idpServer = new FakeOidcIdpServer(URI.create("http://127.0.0.1:%d/".formatted(TestNetwork.freePort())));
		this.idpServer.registerClient("netshot", "secret", URI.create("http://localhost:8080/"));
		this.idpServer.start();
		this.oidc = new Oidc(Netshot.config());
		this.discovery = new OidcDiscovery(this.oidc, Netshot.config());
	}

	@AfterEach
	void stopIdp() throws Exception {
		this.discovery.stop();
		this.idpServer.shutdown();
		TestConfig.init(new Properties());
	}

	private Properties oidcConfig() {
		Properties config = new Properties();
		config.setProperty("netshot.aaa.oidc.idp.url", this.idpServer.getBaseUri().toString());
		config.setProperty("netshot.aaa.oidc.clientid", "netshot");
		config.setProperty("netshot.aaa.oidc.clientsecret", "secret");
		return config;
	}

	private static void await(BooleanSupplier condition, String message) throws InterruptedException {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
		while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
			Thread.sleep(50);
		}
		Assertions.assertTrue(condition.getAsBoolean(), message);
	}

	@Test
	@DisplayName("OIDC is enabled once the IdP metadata is fetched, disabled when removed from the configuration")
	void enableThenDisableOnReload() throws Exception {
		TestConfig.init(this.oidcConfig());
		this.discovery.start();
		await(this.oidc::isAvailable, "OIDC wasn't enabled");
		Assertions.assertEquals("netshot", this.oidc.getClientId());
		Assertions.assertTrue(this.discovery.isHealthy());
		Assertions.assertEquals(1L, this.discovery.stats().get("fetches"));

		// Reload without OIDC
		TestConfig.init(new Properties());
		Assertions.assertFalse(this.oidc.isAvailable(), "OIDC is still enabled after its removal from the configuration");
		Assertions.assertEquals(false, this.discovery.stats().get("configured"));
		Assertions.assertTrue(this.discovery.isHealthy(), "Not configured is healthy");

		// Reload with OIDC again
		TestConfig.init(this.oidcConfig());
		await(this.oidc::isAvailable, "OIDC wasn't enabled again");
	}

	@Test
	@DisplayName("An unreachable IdP makes the service unhealthy, stopping the service disables OIDC")
	void unreachableIdp() throws Exception {
		Properties config = this.oidcConfig();
		config.setProperty("netshot.aaa.oidc.idp.url", "http://127.0.0.1:%d/".formatted(TestNetwork.freePort()));
		TestConfig.init(config);
		this.discovery.start();
		await(() -> (Long) this.discovery.stats().get("failures") > 0, "No failure reported");
		Assertions.assertFalse(this.oidc.isAvailable());
		Assertions.assertFalse(this.discovery.isHealthy());

		TestConfig.init(this.oidcConfig());
		await(this.oidc::isAvailable, "OIDC wasn't enabled with the reachable IdP");
		this.discovery.stop();
		Assertions.assertFalse(this.oidc.isAvailable(), "OIDC still enabled after the stop of the service");
	}
}
