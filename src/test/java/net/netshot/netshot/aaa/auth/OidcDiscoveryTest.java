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
import net.netshot.netshot.aaa.user.UiUser;
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

	@Test
	@DisplayName("Without client, the IdP is used for the JWT access tokens only (no OIDC login)")
	void jwtWithoutClient() throws Exception {
		Properties config = new Properties();
		config.setProperty("netshot.aaa.oidc.idp.url", this.idpServer.getBaseUri().toString());
		TestConfig.init(config);
		this.discovery.start();
		Assertions.assertEquals(false, this.discovery.stats().get("configured"), "Fetched without client nor JWT");

		config.setProperty("netshot.aaa.oidc.jwt.enabled", "true");
		config.setProperty("netshot.aaa.oidc.jwt.audience", "netshot-api");
		TestConfig.init(config);
		await(this.oidc::hasIdpMetadata, "The IdP metadata wasn't fetched for the JWT access tokens");
		Assertions.assertFalse(this.oidc.isAvailable(), "The OIDC login is enabled without client");
		Assertions.assertNull(this.oidc.getClientId());
		Assertions.assertEquals(false, this.discovery.stats().get("login"));
		Assertions.assertTrue(this.discovery.isHealthy());
		UiUser user = this.oidc.authenticateWithBearerToken(
			this.idpServer.issueAccessToken("jwtreader", UiUser.Role.READONLY.getName(), "netshot-api", null, Duration.ofHours(1)));
		Assertions.assertNotNull(user, "Valid JWT refused");
		Assertions.assertEquals("jwtreader", user.username());
		Assertions.assertEquals(UiUser.LEVEL_READONLY, user.level());
		Assertions.assertNull(this.oidc.authenticateWithBearerToken(
			this.idpServer.issueAccessToken("jwtreader", "read-only", "netshot", null, Duration.ofHours(1))), "Wrong audience accepted");

		// Without audience (nor client ID to default to), the tokens are refused
		config.remove("netshot.aaa.oidc.jwt.audience");
		TestConfig.init(config);
		await(this.oidc::hasIdpMetadata, "The IdP metadata wasn't fetched again");
		Assertions.assertNull(this.oidc.authenticateWithBearerToken(
			this.idpServer.issueAccessToken("jwtreader", "read-only", "netshot-api", null, Duration.ofHours(1))));

		// With the client, the OIDC login is enabled too
		Properties full = this.oidcConfig();
		full.setProperty("netshot.aaa.oidc.jwt.enabled", "true");
		TestConfig.init(full);
		await(this.oidc::isAvailable, "The OIDC login wasn't enabled with the client");
		Assertions.assertNotNull(this.oidc.authenticateWithBearerToken(
			this.idpServer.issueAccessToken("jwtreader", "read-only", "netshot", null, Duration.ofHours(1))));
	}
}
