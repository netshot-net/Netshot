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
package net.netshot.netshot.vault;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.testing.TestTls;
import net.netshot.netshot.testing.TestTls.Identity;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;

/**
 * The Vault client on an HTTPS Vault: the CA trust policy of the instance applies to the connection (the
 * policies themselves are tested by {@code platform.tls.HttpsTrustPolicyTest}).
 */
@UnitTest
@DisplayName("Vault client over HTTPS")
class VaultClientTest {

	/** The answer of Vault to an AppRole login. */
	private static final String LOGIN_ANSWER = "{\"auth\": {\"client_token\": \"token-1\", \"lease_duration\": 3600}}";

	private static final VaultManager.Settings SETTINGS = new VaultManager.Settings(30000, 60000, 5000, 5000);

	private static HashicorpVaultKv2Instance instance(TestTls.Server server, HttpsCaTrustMode mode, String customCa) {
		return HashicorpVaultKv2Instance.builder("TLS vault")
			.baseUrl(server.url("localhost").toString())
			.httpsCaTrustMode(mode)
			.httpsCustomCaCertificate(customCa)
			.authMethod(AuthMethod.APPROLE)
			.appRoleMountPath("approle")
			.appRoleId("role-1")
			.appRoleSecretId(Secret.local("secret-1"))
			.build();
	}

	@Test
	@DisplayName("The trust policy of the instance applies: system trust store refuses the unknown CA, the custom CA is accepted")
	void trustPolicy() throws Exception {
		Identity authority = TestTls.authority("Vault CA");
		try (TestTls.Server server = new TestTls.Server(TestTls.server(authority, "localhost"), LOGIN_ANSWER)) {
			VaultException e = Assertions.assertThrows(VaultException.class,
				() -> VaultClient.login(instance(server, HttpsCaTrustMode.SYSTEM_TRUSTSTORE, null), SETTINGS));
			Assertions.assertTrue(hasSslCause(e), "Not refused by TLS: " + e);

			VaultToken token = VaultClient.login(instance(server, HttpsCaTrustMode.CUSTOM_CA, authority.pem()), SETTINGS);
			Assertions.assertEquals("token-1", token.clientToken());
		}
	}

	private static boolean hasSslCause(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SSLException) {
				return true;
			}
		}
		return false;
	}
}
