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
package net.netshot.netshot;

import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.credentials.DeviceSshAccount;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;
import net.netshot.netshot.vault.VaultException;
import net.netshot.netshot.vault.VaultKeyPath;
import net.netshot.netshot.vault.VaultManager;
import net.netshot.netshot.vault.VaultableSecret;

@Testcontainers
public class VaultTest extends WithDatabaseTest {

	@Nested
	@DisplayName("Vault key path test")
	class KeyPathTest {

		private final ObjectMapper json = new ObjectMapper();

		@Test
		void parsesSimplePathAndKey() throws VaultException {
			VaultKeyPath keyPath = VaultKeyPath.parse("secret/app/creds/username");
			Assertions.assertEquals("secret/app/creds", keyPath.getKvPath());
			Assertions.assertEquals("username", keyPath.toString());
		}

		@Test
		void resolvesNestedKeyViaUnescapedDots() throws VaultException, com.fasterxml.jackson.core.JsonProcessingException {
			VaultKeyPath keyPath = VaultKeyPath.parse("secret/app/creds/parent.child");
			JsonNode data = this.json.readTree("{\"parent\": {\"child\": \"value\"}}");
			Assertions.assertEquals("value", keyPath.resolve(data).asText());
		}

		@Test
		void treatsEscapedDotAsLiteralKeyCharacter() throws VaultException, com.fasterxml.jackson.core.JsonProcessingException {
			VaultKeyPath keyPath = VaultKeyPath.parse("secret/app/creds/a\\.b");
			JsonNode data = this.json.readTree("{\"a.b\": \"value\"}");
			Assertions.assertEquals("value", keyPath.resolve(data).asText());
		}

		@Test
		void treatsEscapedBackslashAsLiteralCharacter() throws VaultException, com.fasterxml.jackson.core.JsonProcessingException {
			VaultKeyPath keyPath = VaultKeyPath.parse("secret/app/creds/a\\\\b");
			JsonNode data = this.json.readTree("{\"a\\\\b\": \"value\"}");
			Assertions.assertEquals("value", keyPath.resolve(data).asText());
		}

		@Test
		void missingNestedKeyResolvesToMissingNode() throws VaultException, com.fasterxml.jackson.core.JsonProcessingException {
			VaultKeyPath keyPath = VaultKeyPath.parse("secret/app/creds/parent.missing");
			JsonNode data = this.json.readTree("{\"parent\": {\"child\": \"value\"}}");
			Assertions.assertTrue(keyPath.resolve(data).isMissingNode());
		}

		@Test
		void rejectsValueWithoutSlash() {
			Assertions.assertThrows(VaultException.class, () -> VaultKeyPath.parse("username"));
		}

		@Test
		void rejectsValueEndingWithSlash() {
			Assertions.assertThrows(VaultException.class, () -> VaultKeyPath.parse("secret/app/creds/"));
		}

	}

	/**
	 * End-to-end resolution test against a real HashiCorp Vault instance
	 * (Testcontainers-managed): a secret is seeded in Vault, a
	 * {@link HashicorpVaultKv2Instance} and a Vault-backed
	 * {@link DeviceSshAccount} are persisted to the DB, then reloaded in a
	 * fresh session to prove the FK/path columns round-trip correctly, and
	 * finally resolved through {@link VaultManager#resolve} - the same call
	 * every device connection (SSH/HTTP/SNMP) makes to obtain a Vault-backed
	 * credential field.
	 */
	@Nested
	@DisplayName("Vault-backed secret resolution (real Vault container)")
	class SecretResolutionTest {

		private static final String ROOT_TOKEN = "netshot-test-root-token";
		private static final String APPROLE_NAME = "netshot-test";
		private static final String ROLE_ID = "11111111-1111-1111-1111-111111111111";
		private static final String SECRET_ID = "22222222-2222-2222-2222-222222222222";
		private static final String SECRET_PATH = "netshot-test/device1";
		private static final String LOCAL_SUPER_PASSWORD = "locally-stored-enable-secret";

		/** Grants the AppRole role read access to the seeded secret - the minimum a device connection needs. */
		private static final String POLICY_HCL = """
			path "secret/data/*" {
			  capabilities = ["read"]
			}
			""";

		@Container
		private static final VaultContainer<?> vaultContainer = buildVaultContainer();

		/** Builds (but does not start) the Vault container; {@code @Container} starts/stops it around this class. */
		@SuppressWarnings("resource")
		private static VaultContainer<?> buildVaultContainer() {
			return new VaultContainer<>(DockerImageName.parse("hashicorp/vault:2.0.4"))
				.withVaultToken(ROOT_TOKEN)
				.withCopyToContainer(Transferable.of(POLICY_HCL), "/tmp/netshot-test-policy.hcl")
				.withInitCommand(
					"kv put secret/%s username=vaultadmin password=Sup3rS3cr3tPassword!".formatted(SECRET_PATH),
					"policy write %s /tmp/netshot-test-policy.hcl".formatted(APPROLE_NAME),
					"auth enable approle",
					"write auth/approle/role/%s token_policies=%s token_ttl=1h token_max_ttl=4h"
						.formatted(APPROLE_NAME, APPROLE_NAME),
					"write auth/approle/role/%s/role-id role_id=%s".formatted(APPROLE_NAME, ROLE_ID),
					"write auth/approle/role/%s/custom-secret-id secret_id=%s".formatted(APPROLE_NAME, SECRET_ID)
				);
		}

		private static long vaultInstanceId;
		private static long accountId;

		@BeforeAll
		static void initDatabaseAndVaultInstance() throws Exception {
			Properties config = getFreshDatabaseConfig("vaultresolutiontest");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "INFO");
			Netshot.initConfig(config);
			VaultManager.loadConfig();
			Database.update();
			Database.init();

			HashicorpVaultKv2Instance vaultInstance = new HashicorpVaultKv2Instance("test-vault");
			vaultInstance.setBaseUrl(vaultContainer.getHttpHostAddress());
			vaultInstance.setKvMountPath("secret");
			vaultInstance.setAuthMethod(AuthMethod.APPROLE);
			vaultInstance.setAppRoleId(ROLE_ID);
			vaultInstance.setAppRoleSecretId(SECRET_ID);
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(vaultInstance);
				session.getTransaction().commit();
			}
			vaultInstanceId = vaultInstance.getId();

			DeviceSshAccount account = new DeviceSshAccount(null, null, LOCAL_SUPER_PASSWORD, "vault-test-account");
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				HashicorpVaultKv2Instance reloadedInstance = session.get(HashicorpVaultKv2Instance.class, vaultInstanceId);

				VaultableSecret usernameSecret = new VaultableSecret();
				usernameSecret.setVaultInstance(reloadedInstance);
				usernameSecret.setVaultPath(SECRET_PATH + "/username");
				account.setUsernameSecret(usernameSecret);

				VaultableSecret passwordSecret = new VaultableSecret();
				passwordSecret.setVaultInstance(reloadedInstance);
				passwordSecret.setVaultPath(SECRET_PATH + "/password");
				account.setPasswordSecret(passwordSecret);

				session.persist(account);
				session.getTransaction().commit();
			}
			accountId = account.getId();
		}

		/** Reloads the test account in a fresh session, so its Vault references come from the DB, not from the object still held by {@code @BeforeAll}. */
		private DeviceSshAccount reloadAccount() {
			try (Session session = Database.getSession(true)) {
				return session.get(DeviceSshAccount.class, accountId);
			}
		}

		@Test
		@DisplayName("A Vault-backed username/password field resolves to the value seeded in Vault")
		void resolvesVaultBackedFields() throws VaultException {
			DeviceSshAccount account = this.reloadAccount();

			Assertions.assertEquals("vaultadmin", VaultManager.resolve(account.getUsernameSecret()));
			Assertions.assertEquals("Sup3rS3cr3tPassword!", VaultManager.resolve(account.getPasswordSecret()));
		}

		@Test
		@DisplayName("A local (non-Vault) field on the same entity still resolves to its stored local value")
		void resolvesLocalFieldUnaffected() throws VaultException {
			DeviceSshAccount account = this.reloadAccount();

			Assertions.assertFalse(account.getSuperPasswordSecret().isVaultBacked());
			Assertions.assertEquals(LOCAL_SUPER_PASSWORD, VaultManager.resolve(account.getSuperPasswordSecret()));
		}

		@Test
		@DisplayName("Resolution fails fast when the key doesn't exist at the Vault path")
		void resolutionFailsForMissingKey() {
			try (Session session = Database.getSession(true)) {
				HashicorpVaultKv2Instance reloadedInstance = session.get(HashicorpVaultKv2Instance.class, vaultInstanceId);
				VaultableSecret missingKeySecret = new VaultableSecret();
				missingKeySecret.setVaultInstance(reloadedInstance);
				missingKeySecret.setVaultPath(SECRET_PATH + "/no-such-key");

				Assertions.assertThrows(VaultException.class, () -> VaultManager.resolve(missingKeySecret));
			}
		}

		@Test
		@DisplayName("Resolution fails fast with wrong AppRole credentials")
		void resolutionFailsForWrongCredentials() {
			HashicorpVaultKv2Instance wrongInstance = new HashicorpVaultKv2Instance("test-vault-bad-creds");
			wrongInstance.setBaseUrl(vaultContainer.getHttpHostAddress());
			wrongInstance.setKvMountPath("secret");
			wrongInstance.setAuthMethod(AuthMethod.APPROLE);
			wrongInstance.setAppRoleId(ROLE_ID);
			wrongInstance.setAppRoleSecretId("not-the-right-secret-id");
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				session.persist(wrongInstance);
				session.getTransaction().commit();
			}

			VaultableSecret secret = new VaultableSecret();
			secret.setVaultInstance(wrongInstance);
			secret.setVaultPath(SECRET_PATH + "/username");

			Assertions.assertThrows(VaultException.class, () -> VaultManager.resolve(secret));
		}

	}

}
