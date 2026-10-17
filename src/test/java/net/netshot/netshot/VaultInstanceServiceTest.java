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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;
import net.netshot.netshot.vault.VaultException;
import net.netshot.netshot.vault.VaultInstance;
import net.netshot.netshot.vault.VaultInstanceRepository;
import net.netshot.netshot.vault.VaultInstanceService;

/**
 * Tests of the Vault instance use cases, against a real database: error codes and their order,
 * the semantics of the secrets left out (kept), the deletion rule, the notification of the changes
 * and the connection test (with a fake Vault client).
 */
@TestInstance(Lifecycle.PER_CLASS)
class VaultInstanceServiceTest extends WithDatabaseTest {

	private Transactions transactions;
	private VaultInstanceService service;
	private final List<Long> notified = new ArrayList<>();
	private final List<HashicorpVaultKv2Instance> tested = new ArrayList<>();
	private VaultException testFailure;
	private int counter;

	@BeforeAll
	void initDb() throws Exception {
		Properties config = getFreshDatabaseConfig("vaultinstanceservicetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		Netshot.initConfig(config);
		Netshot.loadModuleConfigs();
		TestDrivers.reload();
		Database.update();
		Wiring.init(TestDrivers.registry());
		this.transactions = new Transactions();
		this.service = new VaultInstanceService(this.transactions, new VaultInstanceRepository(),
			this.notified::add,
			instance -> {
				this.tested.add(instance);
				if (this.testFailure != null) {
					throw this.testFailure;
				}
			});
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private String nextName() {
		return "Vault " + (this.counter++);
	}

	private HashicorpVaultKv2Instance definition(String name) {
		HashicorpVaultKv2Instance instance = new HashicorpVaultKv2Instance(name);
		instance.setBaseUrl("https://vault.example.net:8200");
		instance.setAuthMethod(AuthMethod.APPROLE);
		instance.setAppRoleId("role");
		instance.setAppRoleSecretId("secret");
		instance.setJwtClientSecret("jwt-secret");
		return instance;
	}

	private HashicorpVaultKv2Instance stored(long id) {
		return (HashicorpVaultKv2Instance) this.transactions.read(session -> session.get(VaultInstance.class, id));
	}

	private HashicorpVaultKv2Instance createInstance() {
		return (HashicorpVaultKv2Instance) this.service.create(this.definition(this.nextName()));
	}

	@Test
	@DisplayName("create() stores the Vault instance, with its secrets, and does not notify anyone")
	void create() {
		int notifications = this.notified.size();
		HashicorpVaultKv2Instance created = (HashicorpVaultKv2Instance) this.service.create(this.definition("  Padded name "));
		Assertions.assertTrue(created.getId() > 0);
		HashicorpVaultKv2Instance reloaded = this.stored(created.getId());
		Assertions.assertEquals("  Padded name ", reloaded.getName(), "the name is not trimmed");
		Assertions.assertEquals("secret", reloaded.getAppRoleSecretId());
		Assertions.assertEquals("jwt-secret", reloaded.getJwtClientSecret());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, reloaded.getHttpsCaTrustMode());
		Assertions.assertEquals(notifications, this.notified.size());
	}

	@Test
	@DisplayName("create() refuses a blank name, a duplicate name, and a missing base URL or authentication method")
	void createErrors() {
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, () -> this.service.create(this.definition(" ")));
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, () -> this.service.create(this.definition(null)));
		HashicorpVaultKv2Instance first = this.createInstance();
		int before = this.service.list(Page.ALL).size();
		assertError(ErrorCode.NETSHOT_DUPLICATE_VAULT_INSTANCE, () -> this.service.create(this.definition(first.getName())));
		HashicorpVaultKv2Instance noUrl = this.definition(this.nextName());
		noUrl.setBaseUrl(null);
		assertError(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, () -> this.service.create(noUrl));
		HashicorpVaultKv2Instance noAuth = this.definition(this.nextName());
		noAuth.setAuthMethod(null);
		assertError(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, () -> this.service.create(noAuth));
		HashicorpVaultKv2Instance noTrustMode = this.definition(this.nextName());
		noTrustMode.setHttpsCaTrustMode(null);
		assertError(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, () -> this.service.create(noTrustMode));
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
	}

	@Test
	@DisplayName("list() honours the page")
	void list() {
		this.createInstance();
		this.createInstance();
		List<VaultInstance> all = this.service.list(Page.ALL);
		Assertions.assertTrue(all.size() >= 2);
		Assertions.assertEquals(1, this.service.list(new Page(0, 1)).size());
		Assertions.assertEquals(all.size() - 1, this.service.list(new Page(1, null)).size());
	}

	@Test
	@DisplayName("update() changes the fields, keeps the secrets which are not given, and notifies the change")
	void update() {
		HashicorpVaultKv2Instance created = this.createInstance();
		HashicorpVaultKv2Instance incoming = this.definition("Renamed " + created.getName());
		incoming.setBaseUrl("https://other.example.net");
		incoming.setNamespace("ns");
		incoming.setHttpsCaTrustMode(HttpsCaTrustMode.TRUST_ANY);
		incoming.setKvMountPath("kv");
		incoming.setAuthMethod(AuthMethod.JWT);
		incoming.setAppRoleId("role2");
		incoming.setAppRoleSecretId(null);
		incoming.setJwtClientSecret(null);
		incoming.setJwtIdpTokenEndpoint("https://idp.example.net");
		incoming.setJwtClientId("client");
		incoming.setJwtVaultRole("vrole");
		incoming.setJwtScope("scope");
		incoming.setAppRoleMountPath("changed-approle");
		incoming.setJwtMountPath("changed-jwt");
		int notifications = this.notified.size();
		HashicorpVaultKv2Instance updated = (HashicorpVaultKv2Instance) this.service.update(created.getId(), incoming);
		Assertions.assertEquals(created.getId(), updated.getId());
		HashicorpVaultKv2Instance reloaded = this.stored(created.getId());
		Assertions.assertEquals(incoming.getName(), reloaded.getName());
		Assertions.assertEquals("https://other.example.net", reloaded.getBaseUrl());
		Assertions.assertEquals("ns", reloaded.getNamespace());
		Assertions.assertEquals(HttpsCaTrustMode.TRUST_ANY, reloaded.getHttpsCaTrustMode());
		Assertions.assertEquals("kv", reloaded.getKvMountPath());
		Assertions.assertEquals(AuthMethod.JWT, reloaded.getAuthMethod());
		Assertions.assertEquals("role2", reloaded.getAppRoleId());
		Assertions.assertEquals("secret", reloaded.getAppRoleSecretId(), "the secret which is not given is kept");
		Assertions.assertEquals("jwt-secret", reloaded.getJwtClientSecret(), "the secret which is not given is kept");
		Assertions.assertEquals("client", reloaded.getJwtClientId());
		Assertions.assertEquals("changed-approle", reloaded.getAppRoleMountPath());
		Assertions.assertEquals("changed-jwt", reloaded.getJwtMountPath());
		Assertions.assertEquals(List.of(created.getId()), this.notified.subList(notifications, this.notified.size()));

		HashicorpVaultKv2Instance withSecrets = this.definition(incoming.getName());
		withSecrets.setAppRoleSecretId("new-secret");
		withSecrets.setJwtClientSecret("new-jwt-secret");
		this.service.update(created.getId(), withSecrets);
		reloaded = this.stored(created.getId());
		Assertions.assertEquals("new-secret", reloaded.getAppRoleSecretId());
		Assertions.assertEquals("new-jwt-secret", reloaded.getJwtClientSecret());
	}

	@Test
	@DisplayName("update() reports an unknown Vault instance, a blank or used name, and notifies nothing then")
	void updateErrors() {
		HashicorpVaultKv2Instance first = this.createInstance();
		HashicorpVaultKv2Instance second = this.createInstance();
		int notifications = this.notified.size();
		assertError(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, () -> this.service.update(99999999L, this.definition("x")));
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, () -> this.service.update(second.getId(), this.definition(" ")));
		assertError(ErrorCode.NETSHOT_DUPLICATE_VAULT_INSTANCE, () -> this.service.update(second.getId(), this.definition(first.getName())));
		HashicorpVaultKv2Instance noUrl = this.definition(second.getName());
		noUrl.setBaseUrl(null);
		assertError(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, () -> this.service.update(second.getId(), noUrl));
		Assertions.assertEquals(second.getName(), this.stored(second.getId()).getName());
		Assertions.assertEquals("https://vault.example.net:8200", this.stored(second.getId()).getBaseUrl());
		Assertions.assertEquals(notifications, this.notified.size());
	}

	@Test
	@DisplayName("delete() removes the Vault instance, notifies, and says when there was none")
	void delete() {
		HashicorpVaultKv2Instance created = this.createInstance();
		int notifications = this.notified.size();
		Assertions.assertTrue(this.service.delete(created.getId()));
		Assertions.assertNull(this.stored(created.getId()));
		Assertions.assertEquals(List.of(created.getId()), this.notified.subList(notifications, this.notified.size()));
		Assertions.assertFalse(this.service.delete(created.getId()));
		Assertions.assertEquals(notifications + 1, this.notified.size(), "nothing to notify for an unknown Vault instance");
	}

	@Test
	@DisplayName("delete() refuses a Vault instance which credential fields reference, and keeps it")
	void deleteInUse() {
		HashicorpVaultKv2Instance created = this.createInstance();
		this.transactions.run(session -> {
			DeviceSshAccount ssh = new DeviceSshAccount(null, null, null, "Credentials of " + created.getName());
			ssh.setPasswordVaultInstanceId(session.get(VaultInstance.class, created.getId()));
			ssh.setPasswordVaultPath("a/b");
			session.persist(ssh);
		});
		int notifications = this.notified.size();
		assertError(ErrorCode.NETSHOT_VAULT_INSTANCE_IN_USE, () -> this.service.delete(created.getId()));
		Assertions.assertNotNull(this.stored(created.getId()));
		Assertions.assertEquals(notifications, this.notified.size());
	}

	@Test
	@DisplayName("testConnection() tries the login with the configuration it is given")
	void testConnection() {
		this.tested.clear();
		this.testFailure = null;
		HashicorpVaultKv2Instance candidate = this.definition("Candidate");
		this.service.testConnection(null, candidate);
		Assertions.assertEquals(1, this.tested.size());
		Assertions.assertSame(candidate, this.tested.get(0));
	}

	@Test
	@DisplayName("testConnection() overlays the stored secrets on the ones which are not given")
	void testConnectionOverlay() {
		this.tested.clear();
		this.testFailure = null;
		HashicorpVaultKv2Instance created = this.createInstance();
		HashicorpVaultKv2Instance candidate = this.definition("Candidate");
		candidate.setBaseUrl("https://elsewhere.example.net");
		candidate.setAppRoleSecretId(null);
		candidate.setJwtClientSecret(null);
		this.service.testConnection(created.getId(), candidate);
		HashicorpVaultKv2Instance tested = this.tested.get(0);
		Assertions.assertNotSame(candidate, tested);
		Assertions.assertEquals("https://elsewhere.example.net", tested.getBaseUrl());
		Assertions.assertEquals("secret", tested.getAppRoleSecretId());
		Assertions.assertEquals("jwt-secret", tested.getJwtClientSecret());

		HashicorpVaultKv2Instance withSecret = this.definition("Candidate");
		withSecret.setAppRoleSecretId("given");
		this.service.testConnection(created.getId(), withSecret);
		Assertions.assertEquals("given", this.tested.get(1).getAppRoleSecretId());
	}

	@Test
	@DisplayName("testConnection() reports an unknown instance, and a login failure with the message of the Vault client")
	void testConnectionErrors() {
		this.tested.clear();
		this.testFailure = null;
		assertError(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, () -> this.service.testConnection(99999999L, this.definition("x")));
		Assertions.assertTrue(this.tested.isEmpty());
		this.testFailure = new VaultException("No route to Vault");
		NetshotException failure = assertError(ErrorCode.NETSHOT_VAULT_CONNECTION_FAILED,
			() -> this.service.testConnection(null, this.definition("x")));
		Assertions.assertEquals("Vault connection test failed: No route to Vault", failure.getMessage());
		this.testFailure = null;
	}

	@Test
	@DisplayName("testConnection() does not persist or notify anything")
	void testConnectionHasNoEffect() {
		this.tested.clear();
		this.testFailure = null;
		int before = this.service.list(Page.ALL).size();
		int notifications = this.notified.size();
		this.service.testConnection(null, this.definition("Not saved"));
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
		Assertions.assertEquals(notifications, this.notified.size());
	}
}
