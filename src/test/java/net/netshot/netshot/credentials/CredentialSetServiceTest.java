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
package net.netshot.netshot.credentials;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.credentials.CredentialSetDefinition.Kind;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.platform.secret.VaultSecret;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;
import net.netshot.netshot.vault.SecretChange;
import net.netshot.netshot.boot.Repositories;

/**
 * Tests of the credential set use cases, against a real database: error codes and their order,
 * the semantics of the secrets left out (kept), the Vault references, the deletion rules.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Credential set service")
class CredentialSetServiceTest {

	private Transactions transactions;
	private CredentialSetService service;
	private int counter;
	private long domainId;
	private long vaultId;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = TestWiring.get().transactions();
		Repositories repositories = TestWiring.get().repositories();
		this.service = new CredentialSetService(this.transactions, repositories.credentialSet(), repositories.domain(),
			repositories.credentialSetAssembler());
		this.domainId = TestDevices.addDomain(Domain.builder().name("Credentials domain").description("d")
			.server4Address(new Network4Address("10.255.0.1")).server6Address(new Network6Address("::")).build()).id();
		this.vaultId = this.transactions.write(dsl -> TestWiring.get().repositories().vaultInstance().add(dsl,
			HashicorpVaultKv2Instance.builder("Credentials vault")
				.baseUrl("https://vault.example.net")
				.authMethod(HashicorpVaultKv2Instance.AuthMethod.APPROLE)
				.build()).id());
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private String nextName() {
		return "Credential set " + (this.counter++);
	}

	private DeviceCredentialSet stored(long id) {
		return TestDevices.findCredentialSet(id);
	}

	private static SecretChange local(String value) {
		return value == null ? SecretChange.UNCHANGED : new SecretChange.Local(value);
	}

	private static CredentialSetDefinition definition(Kind kind, String name, Long domainId, String username, String password,
		String superPassword) {
		return new CredentialSetDefinition(kind, name, domainId, local(username), local(password), local(superPassword), null, null,
			null, null, null, null);
	}

	private static CredentialSetDefinition ssh(String name, String username, String password, String superPassword) {
		return definition(Kind.SSH, name, null, username, password, superPassword);
	}

	private static CredentialSetDefinition sshKey(String name, String username, String privateKey, String password) {
		return new CredentialSetDefinition(Kind.SSH_KEY, name, null, local(username), local(password), null, local(privateKey), null,
			null, null, null, null);
	}

	private static CredentialSetDefinition snmpv1(String name, String community) {
		return new CredentialSetDefinition(Kind.SNMP_V1, name, null, null, null, null, null, local(community), null, null, null, null);
	}

	private static CredentialSetDefinition snmpv3(String name, String username, AuthProtocol authType, String authKey,
		PrivProtocol privType, String privKey) {
		return new CredentialSetDefinition(Kind.SNMP_V3, name, null, local(username), null, null, null, null, authType, local(authKey),
			privType, local(privKey));
	}

	private DeviceSshAccount createSsh(String username, String password, String superPassword) {
		return (DeviceSshAccount) this.service.create(ssh(this.nextName(), username, password, superPassword));
	}

	private DeviceCredentialSet update(long id, CredentialSetDefinition definition) {
		return this.service.update(id, _ -> definition, null);
	}

	private static void assertParameter(String target, Executable executable) {
		Assertions.assertEquals(target, assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, executable).getTarget());
	}

	@Test
	@DisplayName("create() stores every kind of credential set, as a global one")
	void createEveryKind() {
		Map<CredentialSetDefinition, Class<?>> kinds = new LinkedHashMap<>();
		kinds.put(snmpv1(this.nextName(), "c1"), DeviceSnmpv1Community.class);
		kinds.put(new CredentialSetDefinition(Kind.SNMP_V2C, this.nextName(), null, null, null, null, null, local("c2"), null, null,
			null, null), DeviceSnmpv2cCommunity.class);
		kinds.put(snmpv3(this.nextName(), "u", AuthProtocol.MD5, "ak", PrivProtocol.DES, "pk"), DeviceSnmpv3Community.class);
		kinds.put(ssh(this.nextName(), "u", "p", "s"), DeviceSshAccount.class);
		kinds.put(sshKey(this.nextName(), "u", "key", "p"), DeviceSshKeyAccount.class);
		kinds.put(definition(Kind.TELNET, this.nextName(), null, "u", "p", "s"), DeviceTelnetAccount.class);
		kinds.put(definition(Kind.HTTP, this.nextName(), null, "u", "p", null), DeviceHttpAccount.class);
		Map<Kind, Long> ids = new HashMap<>();
		kinds.forEach((definition, type) -> {
			DeviceCredentialSet created = this.service.create(definition);
			ids.put(definition.kind(), created.id());
			Assertions.assertTrue(created.id() > 0);
			DeviceCredentialSet reloaded = this.stored(created.id());
			Assertions.assertEquals(type, reloaded.getClass());
			Assertions.assertEquals(definition.name(), reloaded.name());
			Assertions.assertFalse(reloaded.deviceSpecific(), "a credential set created through the use case is a global one");
		});
		Assertions.assertEquals(Secret.local("pk"), ((DeviceSnmpv3Community) this.stored(ids.get(Kind.SNMP_V3))).privKey());
		Assertions.assertEquals(Secret.local("key"), ((DeviceSshKeyAccount) this.stored(ids.get(Kind.SSH_KEY))).privateKey());
	}

	@Test
	@DisplayName("create() trims the name, and refuses a blank name and an unknown domain")
	void createValidation() {
		DeviceCredentialSet padded = this.service.create(ssh("  " + this.nextName() + " ", "u", "p", null));
		Assertions.assertFalse(padded.name().startsWith(" ") || padded.name().endsWith(" "));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, () -> this.service.create(ssh("  ", "u", "p", null)));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, () -> this.service.create(ssh(null, "u", "p", null)));
		assertParameter("mgmtDomain", () -> this.service.create(definition(Kind.SSH, this.nextName(), 99999999L, "u", "p", null)));
	}

	@Test
	@DisplayName("create() refuses a credential set without the properties its type requires, or SNMPv3 privacy without authentication")
	void createRequiredProperties() {
		assertParameter("password", () -> this.service.create(ssh(this.nextName(), "u", null, null)));
		assertParameter("username", () -> this.service.create(ssh(this.nextName(), null, "p", null)));
		assertParameter("privateKey", () -> this.service.create(sshKey(this.nextName(), "u", null, null)));
		assertParameter("community", () -> this.service.create(snmpv1(this.nextName(), null)));
		assertParameter("authKey", () -> this.service.create(snmpv3(this.nextName(), "u", AuthProtocol.SHA, null, PrivProtocol.NONE, null)));
		assertParameter("privType", () -> this.service.create(snmpv3(this.nextName(), "u", AuthProtocol.NONE, null, PrivProtocol.AES128, "pk")));
		// The keys of a protocol which is not used are dropped
		DeviceSnmpv3Community noPriv = (DeviceSnmpv3Community) this.service.create(
			snmpv3(this.nextName(), "u", AuthProtocol.SHA, "ak", PrivProtocol.NONE, "pk"));
		Assertions.assertNull(noPriv.privKey());
	}

	@Test
	@DisplayName("create() resolves the domain")
	void createWithDomain() {
		DeviceCredentialSet created = this.service.create(definition(Kind.SSH, this.nextName(), this.domainId, "u", "p", null));
		Assertions.assertEquals("Credentials domain", created.mgmtDomain().name());
		Assertions.assertEquals(this.domainId, this.stored(created.id()).mgmtDomain().id());
	}

	@Test
	@DisplayName("create() refuses an unknown Vault instance and stores nothing")
	void createInvalidVault() {
		CredentialSetDefinition definition = new CredentialSetDefinition(Kind.SSH, this.nextName(), null, local("u"),
			new SecretChange.Referenced(99999999L, "a/b"), null, null, null, null, null, null, null);
		int before = this.service.list(ListQuery.ALL).items().size();
		assertParameter("passwordVault", () -> this.service.create(definition));
		Assertions.assertEquals(before, this.service.list(ListQuery.ALL).items().size());
	}

	@Test
	@DisplayName("create() holds a secret in a Vault instance when asked to")
	void createWithVault() {
		DeviceSshAccount created = (DeviceSshAccount) this.service.create(new CredentialSetDefinition(Kind.SSH, this.nextName(), null,
			local("local-user"), new SecretChange.Referenced(this.vaultId, "netshot/ssh"), null, null, null, null, null, null, null));
		DeviceSshAccount reloaded = (DeviceSshAccount) this.stored(created.id());
		Assertions.assertInstanceOf(VaultSecret.class, reloaded.password());
		Assertions.assertEquals(this.vaultId, Secret.vaultInstanceId(reloaded.password()));
		Assertions.assertEquals("netshot/ssh", Secret.vaultPath(reloaded.password()));
		Assertions.assertEquals(Secret.local("local-user"), reloaded.username());
	}

	@Test
	@DisplayName("create() refuses a name which is already used, whatever the kind, and stores nothing")
	void createDuplicate() {
		DeviceSshAccount first = this.createSsh("u", "p", null);
		int before = this.service.list(ListQuery.ALL).items().size();
		assertError(ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS, () -> this.service.create(ssh(first.name(), "o", "o", null)));
		assertError(ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS,
			() -> this.service.create(definition(Kind.TELNET, first.name(), null, "o", "o", null)));
		Assertions.assertEquals(before, this.service.list(ListQuery.ALL).items().size());
	}

	@Test
	@DisplayName("list() returns the global credential sets only, and honours the page")
	void list() {
		DeviceSshAccount global = this.createSsh("u", "p", null);
		DeviceCredentialSet specific = this.addSpecific();
		List<DeviceCredentialSet> all = this.service.list(ListQuery.ALL).items();
		Assertions.assertTrue(all.stream().anyMatch(cs -> cs.id() == global.id()));
		Assertions.assertTrue(all.stream().noneMatch(cs -> cs.id() == specific.id()));
		Assertions.assertTrue(all.stream().noneMatch(DeviceCredentialSet::deviceSpecific));
		Assertions.assertEquals(1, this.service.list(new ListQuery(List.of(), null, List.of(), new Page(0, 1), false)).items().size());
		Assertions.assertEquals(all.size() - 1, this.service.list(new ListQuery(List.of(), null, List.of(), new Page(1, null), false)).items().size());
	}

	private DeviceCredentialSet addSpecific() {
		return TestDevices.addCredentialSet(DeviceSshAccount.builder().username(Secret.local("u")).password(Secret.local("p")).superPassword(null)
			.name(DeviceCredentialSet.generateSpecificName())
			.deviceSpecific(true).build());
	}

	@Test
	@DisplayName("Reading the credential sets doesn't decrypt their secrets: they are decrypted when they are used")
	void readsWithoutDecrypting() {
		DeviceSshAccount ssh = this.createSsh("u", "p", "s");
		// A stored value which can't be decrypted: reading and listing still work
		this.transactions.write(dsl -> dsl.execute("update device_credential_set set password = 'not-encrypted' where id = ?", ssh.id()));
		DeviceSshAccount listed = (DeviceSshAccount) this.service.list(ListQuery.ALL).items().stream()
			.filter(cs -> cs.id() == ssh.id()).findFirst().orElseThrow();
		LocalSecret password = Assertions.assertInstanceOf(LocalSecret.class, listed.password());
		Assertions.assertThrows(IllegalArgumentException.class, password::reveal, "the value is only decrypted when revealed");
		Assertions.assertEquals("s", ((LocalSecret) listed.superPassword()).reveal());
		// A value which was read is written back as it is stored, without being decrypted
		this.update(ssh.id(), ssh(ssh.name(), "u2", null, null));
		Assertions.assertEquals("not-encrypted", this.transactions.read(dsl -> dsl.fetchValue(
			"select password from device_credential_set where id = ?", ssh.id())));
	}

	@Test
	@DisplayName("list() returns credential sets whose domain and Vault references can be read without a session")
	void listIsReadable() {
		DeviceCredentialSet created = this.service.create(new CredentialSetDefinition(Kind.SSH, this.nextName(), this.domainId, local("u"),
			new SecretChange.Referenced(this.vaultId, "p"), null, null, null, null, null, null, null));
		DeviceSshAccount listed = (DeviceSshAccount) this.service.list(ListQuery.ALL).items().stream()
			.filter(cs -> cs.id() == created.id()).findFirst().orElseThrow();
		Assertions.assertEquals("Credentials domain", listed.mgmtDomain().name());
		Assertions.assertEquals(this.vaultId, Secret.vaultInstanceId(listed.password()));
	}

	@Test
	@DisplayName("update() keeps the secrets which are unchanged, changes or clears the others")
	void updateKeepsSecrets() {
		DeviceSshAccount created = this.createSsh("user", "pass", "enable");
		String name = this.nextName();
		DeviceSshAccount updated = (DeviceSshAccount) this.update(created.id(), ssh(name, "user2", null, null));
		Assertions.assertEquals(created.id(), updated.id());
		DeviceSshAccount reloaded = (DeviceSshAccount) this.stored(created.id());
		Assertions.assertEquals(Secret.local("user2"), reloaded.username());
		Assertions.assertEquals(Secret.local("pass"), reloaded.password());
		Assertions.assertEquals(Secret.local("enable"), reloaded.superPassword());
		Assertions.assertEquals(name, reloaded.name());

		this.update(created.id(), ssh(name, "user3", "newpass", "newenable"));
		reloaded = (DeviceSshAccount) this.stored(created.id());
		Assertions.assertEquals(Secret.local("newpass"), reloaded.password());
		Assertions.assertEquals(Secret.local("newenable"), reloaded.superPassword());

		// An optional secret can be cleared, a required one can't
		this.update(created.id(), new CredentialSetDefinition(Kind.SSH, name, null, null, null, SecretChange.CLEARED, null, null, null,
			null, null, null));
		Assertions.assertNull(((DeviceSshAccount) this.stored(created.id())).superPassword());
		assertParameter("username", () -> this.update(created.id(), new CredentialSetDefinition(Kind.SSH, name, null,
			SecretChange.CLEARED, null, null, null, null, null, null, null, null)));
		Assertions.assertEquals(Secret.local("user3"), ((DeviceSshAccount) this.stored(created.id())).username());
	}

	@Test
	@DisplayName("update() of a SSH key account keeps the private key when it is unchanged, and changes it when it is given")
	void updateSshKey() {
		DeviceSshKeyAccount created = (DeviceSshKeyAccount) this.service.create(sshKey(this.nextName(), "u", "the-key", "p"));
		this.update(created.id(), sshKey(created.name(), "u", null, null));
		DeviceSshKeyAccount reloaded = (DeviceSshKeyAccount) this.stored(created.id());
		Assertions.assertEquals(Secret.local("p"), reloaded.password(), "the password is kept");
		Assertions.assertEquals(Secret.local("the-key"), reloaded.privateKey(), "the private key is kept");
		this.update(created.id(), sshKey(created.name(), "u", "new-key", null));
		Assertions.assertEquals(Secret.local("new-key"), ((DeviceSshKeyAccount) this.stored(created.id())).privateKey());
	}

	@Test
	@DisplayName("update() keeps the SNMP and HTTP secrets which are unchanged")
	void updateOtherKinds() {
		DeviceSnmpv1Community v1 = (DeviceSnmpv1Community) this.service.create(snmpv1(this.nextName(), "c1"));
		this.update(v1.id(), snmpv1(v1.name(), null));
		Assertions.assertEquals(Secret.local("c1"), ((DeviceSnmpv1Community) this.stored(v1.id())).community());
		this.update(v1.id(), snmpv1(v1.name(), "c2"));
		Assertions.assertEquals(Secret.local("c2"), ((DeviceSnmpv1Community) this.stored(v1.id())).community());

		DeviceSnmpv3Community v3 = (DeviceSnmpv3Community) this.service.create(
			snmpv3(this.nextName(), "u", AuthProtocol.MD5, "ak", PrivProtocol.DES, "pk"));
		this.update(v3.id(), snmpv3(v3.name(), "u2", AuthProtocol.SHA, null, PrivProtocol.AES128, null));
		DeviceSnmpv3Community reloaded = (DeviceSnmpv3Community) this.stored(v3.id());
		Assertions.assertEquals(Secret.local("u2"), reloaded.username());
		Assertions.assertEquals(AuthProtocol.SHA, reloaded.authType());
		Assertions.assertEquals(PrivProtocol.AES128, reloaded.privType());
		Assertions.assertEquals(Secret.local("ak"), reloaded.authKey());
		Assertions.assertEquals(Secret.local("pk"), reloaded.privKey());

		DeviceHttpAccount http = (DeviceHttpAccount) this.service.create(definition(Kind.HTTP, this.nextName(), null, "u", "p", null));
		this.update(http.id(), definition(Kind.HTTP, http.name(), null, "u2", null, null));
		DeviceHttpAccount reloadedHttp = (DeviceHttpAccount) this.stored(http.id());
		Assertions.assertEquals(Secret.local("u2"), reloadedHttp.username());
		Assertions.assertEquals(Secret.local("p"), reloadedHttp.password());
	}

	@Test
	@DisplayName("update() reports an unknown credential set, and refuses a change of type")
	void updateNotFound() {
		assertError(ErrorCode.NETSHOT_CREDENTIALS_NOT_FOUND, () -> this.update(99999999L, ssh("x", "u", "p", null)));
		DeviceSshAccount created = this.createSsh("u", "p", null);
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE,
			() -> this.update(created.id(), definition(Kind.TELNET, created.name(), null, "u", "p", null)));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE, () -> this.update(created.id(), sshKey(created.name(), "u", "k", "p")));
	}

	@Test
	@DisplayName("update() refuses a blank name, and a name used by another credential set, and changes nothing")
	void updateNameChecks() {
		DeviceSshAccount first = this.createSsh("first", "p", null);
		DeviceSshAccount second = this.createSsh("second", "p", null);
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, () -> this.update(second.id(), ssh(" ", "changed", "p", null)));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, () -> this.update(second.id(), ssh(null, "changed", "p", null)));
		assertError(ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS, () -> this.update(second.id(), ssh(first.name(), "changed", "p", null)));
		DeviceSshAccount reloaded = (DeviceSshAccount) this.stored(second.id());
		Assertions.assertEquals(Secret.local("second"), reloaded.username());
		Assertions.assertEquals(second.name(), reloaded.name());
	}

	@Test
	@DisplayName("update() sets and clears the domain, and refuses an unknown domain without changing anything")
	void updateDomain() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		CredentialSetDefinition withDomain = definition(Kind.SSH, created.name(), this.domainId, "u", null, null);
		this.update(created.id(), withDomain);
		Assertions.assertEquals(this.domainId, this.stored(created.id()).mgmtDomain().id());

		this.update(created.id(), ssh(created.name(), "u", null, null));
		Assertions.assertNull(this.stored(created.id()).mgmtDomain());

		this.update(created.id(), withDomain);
		assertParameter("mgmtDomain", () -> this.update(created.id(), definition(Kind.SSH, created.name(), 99999999L, "u", null, null)));
		Assertions.assertEquals(this.domainId, this.stored(created.id()).mgmtDomain().id(), "the domain is unchanged");
	}

	@Test
	@DisplayName("update() switches a field to a Vault instance and back, and refuses an unknown Vault instance")
	void updateVault() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		this.update(created.id(), new CredentialSetDefinition(Kind.SSH, created.name(), null, local("u"),
			new SecretChange.Referenced(this.vaultId, "a/b"), null, null, null, null, null, null, null));
		DeviceSshAccount reloaded = (DeviceSshAccount) this.stored(created.id());
		Assertions.assertInstanceOf(VaultSecret.class, reloaded.password(), "the local value is dropped");
		Assertions.assertEquals(this.vaultId, Secret.vaultInstanceId(reloaded.password()));

		this.update(created.id(), ssh(created.name(), "u", "again", null));
		reloaded = (DeviceSshAccount) this.stored(created.id());
		Assertions.assertEquals(Secret.local("again"), reloaded.password());
		Assertions.assertNull(Secret.vaultInstanceId(reloaded.password()));
		Assertions.assertNull(Secret.vaultPath(reloaded.password()));

		assertParameter("passwordVault", () -> this.update(created.id(), new CredentialSetDefinition(Kind.SSH, created.name(), null,
			local("changed"), new SecretChange.Referenced(99999999L, null), null, null, null, null, null, null, null)));
		Assertions.assertEquals(Secret.local("u"), ((DeviceSshAccount) this.stored(created.id())).username());
	}

	@Test
	@DisplayName("update() refuses a credential set specific to a device access: it is changed with its access")
	void updateDeviceSpecific() {
		DeviceCredentialSet specific = this.addSpecific();
		assertError(ErrorCode.NETSHOT_CREDENTIALS_IN_USE, () -> this.update(specific.id(), ssh("Now named", "changed", null, null)));
		DeviceCredentialSet reloaded = this.stored(specific.id());
		Assertions.assertEquals(specific.name(), reloaded.name());
		Assertions.assertTrue(reloaded.deviceSpecific());
	}

	@Test
	@DisplayName("delete() removes a credential set, and reports an unknown one")
	void delete() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		this.service.delete(created.id(), null);
		Assertions.assertNull(this.stored(created.id()));
		assertError(ErrorCode.NETSHOT_CREDENTIALS_NOT_FOUND, () -> this.service.delete(created.id(), null));
		assertError(ErrorCode.NETSHOT_CREDENTIALS_NOT_FOUND, () -> this.service.delete(99999999L, null));
	}

	@Test
	@DisplayName("delete() refuses a credential set specific to a device access")
	void deleteDeviceSpecific() {
		DeviceCredentialSet specific = this.addSpecific();
		assertError(ErrorCode.NETSHOT_CREDENTIALS_IN_USE, () -> this.service.delete(specific.id(), null));
		Assertions.assertNotNull(this.stored(specific.id()));
	}

	@Test
	@DisplayName("delete() removes a credential set used by a task, and the link is dropped with it")
	void deleteUsedByTask() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		TestTasks.add(DiscoverDeviceTypeTask.of("10.99.9.1", this.domainId, Set.of(created.id()), "c", "tester"));
		this.service.delete(created.id(), null);
		Assertions.assertNull(this.stored(created.id()));
	}

	@Test
	@DisplayName("delete() reports a constraint violation as 'in use', and any other failure as a database error")
	void deleteFailures() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		CredentialSetService violating = new CredentialSetService(this.transactions, new CredentialSetRepository(TestWiring.get().secretConverter()) {
			@Override
			public boolean remove(DSLContext dsl, long id) {
				throw new DataAccessException("in use", new SQLException("fk", "23503"));
			}
		}, TestWiring.get().repositories().domain(), TestWiring.get().repositories().credentialSetAssembler());
		assertError(ErrorCode.NETSHOT_CREDENTIALS_IN_USE, () -> violating.delete(created.id(), null));
		CredentialSetService failing = new CredentialSetService(this.transactions, new CredentialSetRepository(TestWiring.get().secretConverter()) {
			@Override
			public boolean remove(DSLContext dsl, long id) {
				throw new DataAccessException("boom", new SQLException("down", "08006"));
			}
		}, TestWiring.get().repositories().domain(), TestWiring.get().repositories().credentialSetAssembler());
		assertError(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, () -> failing.delete(created.id(), null));
		Assertions.assertNotNull(this.stored(created.id()));
	}

	@Test
	@DisplayName("delete() removes a credential set pinned by a device access, and the pin is cleared")
	void deletePinned() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		Device device = TestDevices.add(Device.create("CiscoIOS12", "10.99.9.2", Domain.reference(this.domainId), "tester").toBuilder()
			.accesses(List.of(DeviceAccess.of("ssh").toBuilder().globalCredentialSet(created).build())).build());
		Assertions.assertEquals(created.id(), TestDevices.find(device.id()).getDeviceAccess("ssh").globalCredentialSet().id());
		this.service.delete(created.id(), null);
		Assertions.assertNull(TestDevices.find(device.id()).getDeviceAccess("ssh").globalCredentialSet());
	}
}
