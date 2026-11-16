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

import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestWiring;
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
import net.netshot.netshot.platform.query.ListQuery;
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
@DisplayName("Vault instance service")
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
		this.service = new VaultInstanceService(this.transactions, TestWiring.get().repositories().vaultInstance(),
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

	private static SecretChange local(String value) {
		return value == null ? SecretChange.UNCHANGED : new SecretChange.Local(value);
	}

	private static VaultInstanceDefinition definition(String name, String baseUrl, AuthMethod authMethod, String appRoleSecretId,
		String jwtClientSecret) {
		return new VaultInstanceDefinition(name, baseUrl, null, null, null, null, authMethod, null, "role", local(appRoleSecretId),
			null, null, null, local(jwtClientSecret), null, null);
	}

	private static VaultInstanceDefinition definition(String name) {
		return definition(name, "https://vault.example.net:8200", AuthMethod.APPROLE, "secret", "jwt-secret");
	}

	private HashicorpVaultKv2Instance stored(long id) {
		return (HashicorpVaultKv2Instance) this.transactions.read(dsl -> TestWiring.get().repositories().vaultInstance().find(dsl, id));
	}

	private HashicorpVaultKv2Instance createInstance() {
		return (HashicorpVaultKv2Instance) this.service.create(definition(this.nextName()));
	}

	private static void assertParameter(String target, Executable executable) {
		Assertions.assertEquals(target, assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, executable).getTarget());
	}

	@Test
	@DisplayName("create() stores the Vault instance with its secrets and the defaults, trims the name, and does not notify anyone")
	void create() {
		int notifications = this.notified.size();
		HashicorpVaultKv2Instance created = (HashicorpVaultKv2Instance) this.service.create(definition("  Padded name "));
		Assertions.assertTrue(created.id() > 0);
		HashicorpVaultKv2Instance reloaded = this.stored(created.id());
		Assertions.assertEquals("Padded name", reloaded.name());
		Assertions.assertEquals(Secret.local("secret"), reloaded.appRoleSecretId());
		Assertions.assertEquals(Secret.local("jwt-secret"), reloaded.jwtClientSecret());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, reloaded.httpsCaTrustMode());
		Assertions.assertEquals("secret", reloaded.kvMountPath());
		Assertions.assertEquals("approle", reloaded.appRoleMountPath());
		Assertions.assertEquals(notifications, this.notified.size());
	}

	@Test
	@DisplayName("create() refuses a blank name, a duplicate name, an invalid base URL, a missing authentication method or property")
	void createErrors() {
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, () -> this.service.create(definition(" ")));
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, () -> this.service.create(definition(null)));
		HashicorpVaultKv2Instance first = this.createInstance();
		int before = this.service.list(ListQuery.ALL).items().size();
		assertError(ErrorCode.NETSHOT_DUPLICATE_VAULT_INSTANCE, () -> this.service.create(definition(first.name())));
		assertParameter("baseUrl", () -> this.service.create(definition(this.nextName(), null, AuthMethod.APPROLE, "s", null)));
		assertParameter("baseUrl", () -> this.service.create(definition(this.nextName(), "vault:8200", AuthMethod.APPROLE, "s", null)));
		assertParameter("authMethod", () -> this.service.create(definition(this.nextName(), "https://v.example.net", null, "s", null)));
		assertParameter("appRoleSecretId",
			() -> this.service.create(definition(this.nextName(), "https://v.example.net", AuthMethod.APPROLE, null, null)));
		assertParameter("jwtIdpTokenEndpoint",
			() -> this.service.create(definition(this.nextName(), "https://v.example.net", AuthMethod.JWT, null, "j")));
		Assertions.assertEquals(before, this.service.list(ListQuery.ALL).items().size());
	}

	@Test
	@DisplayName("list() honours the page")
	void list() {
		this.createInstance();
		this.createInstance();
		List<VaultInstance> all = this.service.list(ListQuery.ALL).items();
		Assertions.assertTrue(all.size() >= 2);
		Assertions.assertEquals(1, this.service.list(new ListQuery(List.of(), null, List.of(), new Page(0, 1), false)).items().size());
		Assertions.assertEquals(all.size() - 1, this.service.list(new ListQuery(List.of(), null, List.of(), new Page(1, null), false)).items().size());
	}

	@Test
	@DisplayName("update() changes the fields, keeps the secrets which are unchanged, and notifies the change")
	void update() {
		HashicorpVaultKv2Instance created = this.createInstance();
		VaultInstanceDefinition incoming = new VaultInstanceDefinition("Renamed " + created.name(), "https://other.example.net", "ns",
			HttpsCaTrustMode.TRUST_ANY, null, "kv", AuthMethod.JWT, "changed-approle", "role2", SecretChange.UNCHANGED, "changed-jwt",
			"https://idp.example.net", "client", SecretChange.UNCHANGED, "vrole", "scope");
		int notifications = this.notified.size();
		HashicorpVaultKv2Instance updated = (HashicorpVaultKv2Instance) this.service.update(created.id(), _ -> incoming, null);
		Assertions.assertEquals(created.id(), updated.id());
		HashicorpVaultKv2Instance reloaded = this.stored(created.id());
		Assertions.assertEquals(incoming.name(), reloaded.name());
		Assertions.assertEquals("https://other.example.net", reloaded.baseUrl());
		Assertions.assertEquals("ns", reloaded.namespace());
		Assertions.assertEquals(HttpsCaTrustMode.TRUST_ANY, reloaded.httpsCaTrustMode());
		Assertions.assertEquals("kv", reloaded.kvMountPath());
		Assertions.assertEquals(AuthMethod.JWT, reloaded.authMethod());
		Assertions.assertEquals("role2", reloaded.appRoleId());
		Assertions.assertEquals(Secret.local("secret"), reloaded.appRoleSecretId(), "the unchanged secret is kept");
		Assertions.assertEquals(Secret.local("jwt-secret"), reloaded.jwtClientSecret(), "the unchanged secret is kept");
		Assertions.assertEquals("client", reloaded.jwtClientId());
		Assertions.assertEquals("changed-approle", reloaded.appRoleMountPath());
		Assertions.assertEquals("changed-jwt", reloaded.jwtMountPath());
		Assertions.assertEquals(List.of(created.id()), this.notified.subList(notifications, this.notified.size()));

		this.service.update(created.id(), _ -> definition(incoming.name(), "https://vault.example.net:8200", AuthMethod.APPROLE,
			"new-secret", "new-jwt-secret"), null);
		reloaded = this.stored(created.id());
		Assertions.assertEquals(Secret.local("new-secret"), reloaded.appRoleSecretId());
		Assertions.assertEquals(Secret.local("new-jwt-secret"), reloaded.jwtClientSecret());
	}

	@Test
	@DisplayName("update() reports an unknown Vault instance, a blank or used name, an invalid definition, and notifies nothing then")
	void updateErrors() {
		HashicorpVaultKv2Instance first = this.createInstance();
		HashicorpVaultKv2Instance second = this.createInstance();
		int notifications = this.notified.size();
		assertError(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, () -> this.service.update(99999999L, _ -> definition("x"), null));
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, () -> this.service.update(second.id(), _ -> definition(" "), null));
		assertError(ErrorCode.NETSHOT_DUPLICATE_VAULT_INSTANCE, () -> this.service.update(second.id(), _ -> definition(first.name()), null));
		assertParameter("baseUrl", () -> this.service.update(second.id(), _ -> definition(second.name(), null, AuthMethod.APPROLE, null, null), null));
		Assertions.assertEquals(second.name(), this.stored(second.id()).name());
		Assertions.assertEquals("https://vault.example.net:8200", this.stored(second.id()).baseUrl());
		Assertions.assertEquals(notifications, this.notified.size());
	}

	@Test
	@DisplayName("delete() removes the Vault instance, notifies, and reports an unknown one")
	void delete() {
		HashicorpVaultKv2Instance created = this.createInstance();
		int notifications = this.notified.size();
		this.service.delete(created.id(), null);
		Assertions.assertNull(this.stored(created.id()));
		Assertions.assertEquals(List.of(created.id()), this.notified.subList(notifications, this.notified.size()));
		assertError(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, () -> this.service.delete(created.id(), null));
		Assertions.assertEquals(notifications + 1, this.notified.size(), "nothing to notify for an unknown Vault instance");
	}

	@Test
	@DisplayName("delete() refuses a Vault instance which credential fields reference, and keeps it")
	void deleteInUse() {
		HashicorpVaultKv2Instance created = this.createInstance();
		TestDevices.addCredentialSet(DeviceSshAccount.builder().name("Credentials of " + created.name())
			.password(Secret.vault(created.id(), "a/b")).build());
		int notifications = this.notified.size();
		assertError(ErrorCode.NETSHOT_VAULT_INSTANCE_IN_USE, () -> this.service.delete(created.id(), null));
		Assertions.assertNotNull(this.stored(created.id()));
		Assertions.assertEquals(notifications, this.notified.size());
	}

	@Test
	@DisplayName("test() tries the login with the definition it is given")
	void testDefinition() {
		this.tested.clear();
		this.testFailure = null;
		VaultConnectionTestResult result = this.service.test(null, definition("Candidate"));
		Assertions.assertTrue(result.success());
		Assertions.assertEquals(1, this.tested.size());
		Assertions.assertEquals("Candidate", this.tested.get(0).name());
		Assertions.assertEquals(Secret.local("secret"), this.tested.get(0).appRoleSecretId());
	}

	@Test
	@DisplayName("test() of an existing instance takes the stored secrets which the definition leaves unchanged")
	void testOverlay() {
		this.tested.clear();
		this.testFailure = null;
		HashicorpVaultKv2Instance created = this.createInstance();
		this.service.test(created.id(), definition("Candidate", "https://elsewhere.example.net", AuthMethod.APPROLE, null, null));
		HashicorpVaultKv2Instance testedInstance = this.tested.get(0);
		Assertions.assertEquals("https://elsewhere.example.net", testedInstance.baseUrl());
		Assertions.assertEquals(Secret.local("secret"), testedInstance.appRoleSecretId());
		Assertions.assertEquals(Secret.local("jwt-secret"), testedInstance.jwtClientSecret());

		this.service.test(created.id(), definition("Candidate", "https://elsewhere.example.net", AuthMethod.APPROLE, "given", null));
		Assertions.assertEquals(Secret.local("given"), this.tested.get(1).appRoleSecretId());
	}

	@Test
	@DisplayName("test() reports an unknown instance, an invalid definition, and a login failure with the message of the Vault client")
	void testErrors() {
		this.tested.clear();
		this.testFailure = null;
		assertError(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, () -> this.service.test(99999999L, definition("x")));
		assertParameter("appRoleSecretId",
			() -> this.service.test(null, definition("x", "https://v.example.net", AuthMethod.APPROLE, null, null)));
		Assertions.assertTrue(this.tested.isEmpty());
		this.testFailure = new VaultException("No route to Vault");
		VaultConnectionTestResult failure = this.service.test(null, definition("x"));
		Assertions.assertFalse(failure.success());
		Assertions.assertEquals("No route to Vault", failure.message());
		this.testFailure = null;
	}

	@Test
	@DisplayName("test() does not persist or notify anything")
	void testHasNoEffect() {
		this.tested.clear();
		this.testFailure = null;
		HashicorpVaultKv2Instance created = this.createInstance();
		int before = this.service.list(ListQuery.ALL).items().size();
		int notifications = this.notified.size();
		this.service.test(null, definition("Not saved"));
		this.service.test(created.id(), definition("Not saved either"));
		Assertions.assertEquals(before, this.service.list(ListQuery.ALL).items().size());
		Assertions.assertEquals(created.name(), this.stored(created.id()).name());
		Assertions.assertEquals(notifications, this.notified.size());
	}
}
