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
package net.netshot.netshot.vault;

import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.TestDevices;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;

/**
 * Tests of the Vault instance use cases, against a real database: error codes and their order,
 * the semantics of the secrets left out (kept), the deletion rule, the notification of the changes
 * and the connection test (with a fake Vault client).
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
class VaultInstanceServiceTest {

	private Transactions transactions;
	private VaultInstanceService service;
	private final List<Long> notified = new ArrayList<>();
	private final List<HashicorpVaultKv2Instance> tested = new ArrayList<>();
	private VaultException testFailure;
	private int counter;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = TestWiring.get().transactions();
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
		HashicorpVaultKv2Instance instance = HashicorpVaultKv2Instance.builder(name)
			.baseUrl("https://vault.example.net:8200")
			.authMethod(AuthMethod.APPROLE)
			.appRoleId("role")
			.appRoleSecretId(Secret.local("secret"))
			.jwtClientSecret(Secret.local("jwt-secret"))
			.build();
		return instance;
	}

	private HashicorpVaultKv2Instance stored(long id) {
		return (HashicorpVaultKv2Instance) this.transactions.read(dsl -> new VaultInstanceRepository().find(dsl, id));
	}

	private HashicorpVaultKv2Instance createInstance() {
		return (HashicorpVaultKv2Instance) this.service.create(this.definition(this.nextName()));
	}

	@Test
	@DisplayName("create() stores the Vault instance, with its secrets, and does not notify anyone")
	void create() {
		int notifications = this.notified.size();
		HashicorpVaultKv2Instance created = (HashicorpVaultKv2Instance) this.service.create(this.definition("  Padded name "));
		Assertions.assertTrue(created.id() > 0);
		HashicorpVaultKv2Instance reloaded = this.stored(created.id());
		Assertions.assertEquals("  Padded name ", reloaded.name(), "the name is not trimmed");
		Assertions.assertEquals(Secret.local("secret"), reloaded.appRoleSecretId());
		Assertions.assertEquals(Secret.local("jwt-secret"), reloaded.jwtClientSecret());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, reloaded.httpsCaTrustMode());
		Assertions.assertEquals(notifications, this.notified.size());
	}

	@Test
	@DisplayName("create() refuses a blank name, a duplicate name, and a missing base URL or authentication method")
	void createErrors() {
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, () -> this.service.create(this.definition(" ")));
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, () -> this.service.create(this.definition(null)));
		HashicorpVaultKv2Instance first = this.createInstance();
		int before = this.service.list(Page.ALL).size();
		assertError(ErrorCode.NETSHOT_DUPLICATE_VAULT_INSTANCE, () -> this.service.create(this.definition(first.name())));
		HashicorpVaultKv2Instance noUrl = this.definition(this.nextName()).toBuilder()
			.baseUrl(null)
			.build();
		assertError(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, () -> this.service.create(noUrl));
		HashicorpVaultKv2Instance noAuth = this.definition(this.nextName()).toBuilder()
			.authMethod(null)
			.build();
		assertError(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, () -> this.service.create(noAuth));
		HashicorpVaultKv2Instance noTrustMode = this.definition(this.nextName()).toBuilder()
			.httpsCaTrustMode(null)
			.build();
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
		HashicorpVaultKv2Instance incoming = this.definition("Renamed " + created.name()).toBuilder()
			.baseUrl("https://other.example.net")
			.namespace("ns")
			.httpsCaTrustMode(HttpsCaTrustMode.TRUST_ANY)
			.kvMountPath("kv")
			.authMethod(AuthMethod.JWT)
			.appRoleId("role2")
			.appRoleSecretId(null)
			.jwtClientSecret(null)
			.jwtIdpTokenEndpoint("https://idp.example.net")
			.jwtClientId("client")
			.jwtVaultRole("vrole")
			.jwtScope("scope")
			.appRoleMountPath("changed-approle")
			.jwtMountPath("changed-jwt")
			.build();
		int notifications = this.notified.size();
		HashicorpVaultKv2Instance updated = (HashicorpVaultKv2Instance) this.service.update(created.id(), incoming);
		Assertions.assertEquals(created.id(), updated.id());
		HashicorpVaultKv2Instance reloaded = this.stored(created.id());
		Assertions.assertEquals(incoming.name(), reloaded.name());
		Assertions.assertEquals("https://other.example.net", reloaded.baseUrl());
		Assertions.assertEquals("ns", reloaded.namespace());
		Assertions.assertEquals(HttpsCaTrustMode.TRUST_ANY, reloaded.httpsCaTrustMode());
		Assertions.assertEquals("kv", reloaded.kvMountPath());
		Assertions.assertEquals(AuthMethod.JWT, reloaded.authMethod());
		Assertions.assertEquals("role2", reloaded.appRoleId());
		Assertions.assertEquals(Secret.local("secret"), reloaded.appRoleSecretId(), "the secret which is not given is kept");
		Assertions.assertEquals(Secret.local("jwt-secret"), reloaded.jwtClientSecret(), "the secret which is not given is kept");
		Assertions.assertEquals("client", reloaded.jwtClientId());
		Assertions.assertEquals("changed-approle", reloaded.appRoleMountPath());
		Assertions.assertEquals("changed-jwt", reloaded.jwtMountPath());
		Assertions.assertEquals(List.of(created.id()), this.notified.subList(notifications, this.notified.size()));

		HashicorpVaultKv2Instance withSecrets = this.definition(incoming.name()).toBuilder()
			.appRoleSecretId(Secret.local("new-secret"))
			.jwtClientSecret(Secret.local("new-jwt-secret"))
			.build();
		this.service.update(created.id(), withSecrets);
		reloaded = this.stored(created.id());
		Assertions.assertEquals(Secret.local("new-secret"), reloaded.appRoleSecretId());
		Assertions.assertEquals(Secret.local("new-jwt-secret"), reloaded.jwtClientSecret());
	}

	@Test
	@DisplayName("update() reports an unknown Vault instance, a blank or used name, and notifies nothing then")
	void updateErrors() {
		HashicorpVaultKv2Instance first = this.createInstance();
		HashicorpVaultKv2Instance second = this.createInstance();
		int notifications = this.notified.size();
		assertError(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, () -> this.service.update(99999999L, this.definition("x")));
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, () -> this.service.update(second.id(), this.definition(" ")));
		assertError(ErrorCode.NETSHOT_DUPLICATE_VAULT_INSTANCE, () -> this.service.update(second.id(), this.definition(first.name())));
		HashicorpVaultKv2Instance noUrl = this.definition(second.name()).toBuilder()
			.baseUrl(null)
			.build();
		assertError(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, () -> this.service.update(second.id(), noUrl));
		Assertions.assertEquals(second.name(), this.stored(second.id()).name());
		Assertions.assertEquals("https://vault.example.net:8200", this.stored(second.id()).baseUrl());
		Assertions.assertEquals(notifications, this.notified.size());
	}

	@Test
	@DisplayName("delete() removes the Vault instance, notifies, and says when there was none")
	void delete() {
		HashicorpVaultKv2Instance created = this.createInstance();
		int notifications = this.notified.size();
		Assertions.assertTrue(this.service.delete(created.id()));
		Assertions.assertNull(this.stored(created.id()));
		Assertions.assertEquals(List.of(created.id()), this.notified.subList(notifications, this.notified.size()));
		Assertions.assertFalse(this.service.delete(created.id()));
		Assertions.assertEquals(notifications + 1, this.notified.size(), "nothing to notify for an unknown Vault instance");
	}

	@Test
	@DisplayName("delete() refuses a Vault instance which credential fields reference, and keeps it")
	void deleteInUse() {
		HashicorpVaultKv2Instance created = this.createInstance();
		TestDevices.addCredentialSet(DeviceSshAccount.builder().name("Credentials of " + created.name())
			.password(Secret.vault(created.id(), "a/b")).build());
		int notifications = this.notified.size();
		assertError(ErrorCode.NETSHOT_VAULT_INSTANCE_IN_USE, () -> this.service.delete(created.id()));
		Assertions.assertNotNull(this.stored(created.id()));
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
		HashicorpVaultKv2Instance candidate = this.definition("Candidate").toBuilder()
			.baseUrl("https://elsewhere.example.net")
			.appRoleSecretId(null)
			.jwtClientSecret(null)
			.build();
		this.service.testConnection(created.id(), candidate);
		HashicorpVaultKv2Instance testedInstance = this.tested.get(0);
		Assertions.assertNotSame(candidate, testedInstance);
		Assertions.assertEquals("https://elsewhere.example.net", testedInstance.baseUrl());
		Assertions.assertEquals(Secret.local("secret"), testedInstance.appRoleSecretId());
		Assertions.assertEquals(Secret.local("jwt-secret"), testedInstance.jwtClientSecret());

		HashicorpVaultKv2Instance withSecret = this.definition("Candidate").toBuilder()
			.appRoleSecretId(Secret.local("given"))
			.build();
		this.service.testConnection(created.id(), withSecret);
		Assertions.assertEquals(Secret.local("given"), this.tested.get(1).appRoleSecretId());
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
