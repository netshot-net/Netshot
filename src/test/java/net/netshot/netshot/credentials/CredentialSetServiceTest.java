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
package net.netshot.netshot.credentials;

import java.sql.SQLException;
import java.util.HashMap;
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

import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.Network6Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;
import net.netshot.netshot.vault.VaultInstanceRepository;

/**
 * Tests of the credential set use cases, against a real database: error codes and their order,
 * the semantics of the secrets left out (kept), the Vault references, the deletion rules.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
class CredentialSetServiceTest {

	private Transactions transactions;
	private CredentialSetService service;
	private int counter;
	private long domainId;
	private long vaultId;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = new Transactions();
		this.service = new CredentialSetService(this.transactions, new CredentialSetRepository());
		this.domainId = TestDevices.addDomain(Domain.builder().name("Credentials domain").description("d")
			.server4Address(new Network4Address("10.255.0.1")).server6Address(new Network6Address("::")).build()).id();
		this.vaultId = this.transactions.write(dsl -> new VaultInstanceRepository().add(dsl,
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

	private Domain domainRef() {
		return Domain.reference(this.domainId);
	}

	private static DeviceSnmpv1Community snmpv1(String community, String name) {
		return DeviceSnmpv1Community.builder().community(community).name(name).build();
	}

	private static DeviceSnmpv3Community snmpv3(String community, String name, String username, AuthProtocol authType, String authKey,
		PrivProtocol privType, String privKey) {
		return DeviceSnmpv3Community.builder().community(community).name(name).username(username)
			.authType(authType).authKey(authKey).privType(privType).privKey(privKey).build();
	}

	private static DeviceSshKeyAccount sshKey(String username, String privateKey, String password, String superPassword, String name) {
		return DeviceSshKeyAccount.builder().username(username).privateKey(privateKey).password(password)
			.superPassword(superPassword).name(name).build();
	}

	private DeviceSshAccount createSsh(String username, String password, String superPassword) {
		return (DeviceSshAccount) this.service.create(DeviceSshAccount.builder().username(username).password(password).superPassword(superPassword).name(this.nextName()).build());
	}

	@Test
	@DisplayName("create() stores every kind of credential set, as a global one")
	void createEveryKind() {
		DeviceSnmpv1Community v1 = DeviceSnmpv1Community.builder().community("c1").name(this.nextName()).deviceSpecific(true).build();
		DeviceSnmpv2cCommunity v2 = DeviceSnmpv2cCommunity.builder().community("c2").name(this.nextName()).deviceSpecific(true).build();
		DeviceSnmpv3Community v3 = snmpv3("ctx", this.nextName(), "u", AuthProtocol.MD5, "ak", PrivProtocol.DES, "pk").toBuilder().deviceSpecific(true).build();
		DeviceSshAccount ssh = DeviceSshAccount.builder().username("u").password("p").superPassword("s").name(this.nextName()).deviceSpecific(true).build();
		DeviceSshKeyAccount key = sshKey("u", "key", "p", "s", this.nextName()).toBuilder().deviceSpecific(true).build();
		DeviceTelnetAccount telnet = DeviceTelnetAccount.builder().username("u").password("p").superPassword("s").name(this.nextName()).deviceSpecific(true).build();
		DeviceHttpAccount http = DeviceHttpAccount.builder().username("u").password("p").name(this.nextName()).deviceSpecific(true).build();
		Map<String, Long> ids = new HashMap<>();
		for (DeviceCredentialSet credentialSet : List.of(v1, v2, v3, ssh, key, telnet, http)) {
			DeviceCredentialSet created = this.service.create(credentialSet);
			ids.put(credentialSet.name(), created.id());
			Assertions.assertTrue(created.id() > 0);
			DeviceCredentialSet reloaded = this.stored(created.id());
			Assertions.assertEquals(credentialSet.getClass(), reloaded.getClass());
			Assertions.assertEquals(credentialSet.name(), reloaded.name());
			Assertions.assertFalse(reloaded.deviceSpecific(), "a credential set created through the use case is a global one");
		}
		Assertions.assertEquals("pk", ((DeviceSnmpv3Community) this.stored(ids.get(v3.name()))).privKey());
		Assertions.assertEquals("key", ((DeviceSshKeyAccount) this.stored(ids.get(key.name()))).privateKey());
	}

	@Test
	@DisplayName("create() checks the name first, then the domain")
	void createValidation() {
		DeviceSshAccount blank = DeviceSshAccount.builder().username("u").password("p").superPassword(null).name("  ").mgmtDomain(this.domainRef()).build();
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, () -> this.service.create(blank));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, () -> this.service.create(DeviceSshAccount.builder().username("u").password("p").superPassword(null).name(null).build()));

		DeviceSshAccount badDomain = DeviceSshAccount.builder().username("u").password("p").superPassword(null).name(this.nextName())
			.mgmtDomain(Domain.reference(99999999L)).build();
		assertError(ErrorCode.NETSHOT_INVALID_DOMAIN, () -> this.service.create(badDomain));
	}

	@Test
	@DisplayName("create() resolves the domain")
	void createWithDomain() {
		DeviceSshAccount ssh = DeviceSshAccount.builder().username("u").password("p").superPassword(null).name(this.nextName()).mgmtDomain(this.domainRef()).build();
		DeviceCredentialSet created = this.service.create(ssh);
		Assertions.assertEquals("Credentials domain", created.mgmtDomain().name());
		Assertions.assertEquals(this.domainId, this.stored(created.id()).mgmtDomain().id());
	}

	@Test
	@DisplayName("create() refuses an unknown Vault instance and stores nothing")
	void createInvalidVault() {
		DeviceSshAccount ssh = DeviceSshAccount.builder().username(null).password(null).superPassword(null).name(this.nextName())
			.passwordVaultInstanceId(99999999L).passwordVaultPath("a/b").build();
		int before = this.service.list(Page.ALL).size();
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE, () -> this.service.create(ssh));
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
	}

	@Test
	@DisplayName("create() holds a secret in a Vault instance when asked to, and drops the local value")
	void createWithVault() {
		DeviceSshAccount ssh = DeviceSshAccount.builder().username("local-user").password("local-pass").superPassword(null).name(this.nextName())
			.passwordVaultInstanceId(this.vaultId).passwordVaultPath("netshot/ssh").build();
		DeviceSshAccount created = (DeviceSshAccount) this.service.create(ssh);
		DeviceSshAccount reloaded = (DeviceSshAccount) this.stored(created.id());
		Assertions.assertNull(reloaded.password());
		Assertions.assertEquals(this.vaultId, reloaded.passwordVaultInstanceId());
		Assertions.assertEquals("netshot/ssh", reloaded.passwordVaultPath());
		Assertions.assertEquals("local-user", reloaded.username());
	}

	@Test
	@DisplayName("create() refuses a name which is already used, whatever the kind, and stores nothing")
	void createDuplicate() {
		DeviceSshAccount first = this.createSsh("u", "p", null);
		int before = this.service.list(Page.ALL).size();
		assertError(ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS, () -> this.service.create(DeviceSshAccount.builder().username("o").password("o").superPassword(null).name(first.name()).build()));
		assertError(ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS, () -> this.service.create(DeviceTelnetAccount.builder().username("o").password("o").superPassword(null).name(first.name())
			.build()));
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
	}

	@Test
	@DisplayName("create() ignores the ID of the given credential set: the database assigns one")
	void createWithId() {
		DeviceSshAccount ssh = DeviceSshAccount.builder().id(77_777_777L).username("u").password("p").superPassword(null).name(this.nextName()).build();
		DeviceCredentialSet created = this.service.create(ssh);
		Assertions.assertNotEquals(77_777_777L, created.id());
		Assertions.assertEquals(ssh.name(), this.stored(created.id()).name());
	}

	@Test
	@DisplayName("list() returns the global credential sets only, and honours the page")
	void list() {
		DeviceSshAccount global = this.createSsh("u", "p", null);
		DeviceCredentialSet specific = TestDevices.addCredentialSet(DeviceSshAccount.builder().username("u").password("p").superPassword(null).name(DeviceCredentialSet.generateSpecificName())
			.deviceSpecific(true).build());
		List<DeviceCredentialSet> all = this.service.list(Page.ALL);
		Assertions.assertTrue(all.stream().anyMatch(cs -> cs.id() == global.id()));
		Assertions.assertTrue(all.stream().noneMatch(cs -> cs.id() == specific.id()));
		Assertions.assertTrue(all.stream().noneMatch(DeviceCredentialSet::deviceSpecific));
		Assertions.assertEquals(1, this.service.list(new Page(0, 1)).size());
		Assertions.assertEquals(all.size() - 1, this.service.list(new Page(1, null)).size());
	}

	@Test
	@DisplayName("list() returns credential sets whose domain and Vault references can be read without a session")
	void listIsReadable() {
		DeviceSshAccount ssh = DeviceSshAccount.builder().username("u").password(null).superPassword(null).name(this.nextName())
			.mgmtDomain(this.domainRef()).passwordVaultInstanceId(this.vaultId).passwordVaultPath("p").build();
		DeviceCredentialSet created = this.service.create(ssh);
		DeviceSshAccount listed = (DeviceSshAccount) this.service.list(Page.ALL).stream()
			.filter(cs -> cs.id() == created.id()).findFirst().orElseThrow();
		Assertions.assertEquals("Credentials domain", listed.mgmtDomain().name());
		Assertions.assertEquals(this.vaultId, listed.passwordVaultInstanceId());
	}

	@Test
	@DisplayName("update() keeps the secrets which are not given, and changes the other fields")
	void updateKeepsSecrets() {
		DeviceSshAccount created = this.createSsh("user", "pass", "enable");
		DeviceSshAccount incoming = DeviceSshAccount.builder().username("user2").password(null).superPassword(null).name(this.nextName()).build();
		DeviceSshAccount updated = (DeviceSshAccount) this.service.update(created.id(), incoming);
		Assertions.assertEquals(created.id(), updated.id());
		DeviceSshAccount reloaded = (DeviceSshAccount) this.stored(created.id());
		Assertions.assertEquals("user2", reloaded.username());
		Assertions.assertEquals("pass", reloaded.password());
		Assertions.assertEquals("enable", reloaded.superPassword());
		Assertions.assertEquals(incoming.name(), reloaded.name());

		this.service.update(created.id(), DeviceSshAccount.builder().username("user3").password("newpass").superPassword("newenable").name(incoming.name()).build());
		reloaded = (DeviceSshAccount) this.stored(created.id());
		Assertions.assertEquals("newpass", reloaded.password());
		Assertions.assertEquals("newenable", reloaded.superPassword());

		// The username is not a secret: not giving it clears it
		this.service.update(created.id(), DeviceSshAccount.builder().username(null).password(null).superPassword(null).name(incoming.name()).build());
		Assertions.assertNull(((DeviceSshAccount) this.stored(created.id())).username());
	}

	@Test
	@DisplayName("update() of a SSH key account keeps the private key when it is not given, and changes it when it is")
	void updateSshKey() {
		DeviceSshKeyAccount created = (DeviceSshKeyAccount) this.service.create(
			sshKey("u", "the-key", "p", null, this.nextName()));
		this.service.update(created.id(), sshKey("u", null, null, null, created.name()));
		DeviceSshKeyAccount reloaded = (DeviceSshKeyAccount) this.stored(created.id());
		Assertions.assertEquals("p", reloaded.password(), "the password is kept");
		Assertions.assertEquals("the-key", reloaded.privateKey(), "the private key is kept when it is not given");
		this.service.update(created.id(), sshKey("u", "new-key", null, null, created.name()));
		Assertions.assertEquals("new-key", ((DeviceSshKeyAccount) this.stored(created.id())).privateKey());
	}

	@Test
	@DisplayName("update() keeps the SNMP and HTTP secrets which are not given")
	void updateOtherKinds() {
		DeviceSnmpv1Community v1 = (DeviceSnmpv1Community) this.service.create(snmpv1("c1", this.nextName()));
		this.service.update(v1.id(), snmpv1(null, v1.name()));
		Assertions.assertEquals("c1", ((DeviceSnmpv1Community) this.stored(v1.id())).community());
		this.service.update(v1.id(), snmpv1("c2", v1.name()));
		Assertions.assertEquals("c2", ((DeviceSnmpv1Community) this.stored(v1.id())).community());

		DeviceSnmpv3Community v3 = (DeviceSnmpv3Community) this.service.create(
			snmpv3("ctx", this.nextName(), "u", AuthProtocol.MD5, "ak", PrivProtocol.DES, "pk"));
		this.service.update(v3.id(), snmpv3(null, v3.name(), "u2", AuthProtocol.SHA, null, PrivProtocol.AES128, null));
		DeviceSnmpv3Community reloaded = (DeviceSnmpv3Community) this.stored(v3.id());
		Assertions.assertEquals("u2", reloaded.username());
		Assertions.assertEquals(AuthProtocol.SHA, reloaded.authType());
		Assertions.assertEquals(PrivProtocol.AES128, reloaded.privType());
		Assertions.assertEquals("ak", reloaded.authKey());
		Assertions.assertEquals("pk", reloaded.privKey());
		Assertions.assertEquals("ctx", reloaded.community(), "the context community is kept");

		DeviceHttpAccount http = (DeviceHttpAccount) this.service.create(DeviceHttpAccount.builder().username("u").password("p").name(this.nextName()).build());
		this.service.update(http.id(), DeviceHttpAccount.builder().username("u2").password(null).name(http.name()).build());
		DeviceHttpAccount reloadedHttp = (DeviceHttpAccount) this.stored(http.id());
		Assertions.assertEquals("u2", reloadedHttp.username());
		Assertions.assertEquals("p", reloadedHttp.password());
	}

	@Test
	@DisplayName("update() reports an unknown credential set, or one of another kind, the same way")
	void updateNotFound() {
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS, () -> this.service.update(99999999L, DeviceSshAccount.builder().username("u").password("p").superPassword(null).name("x").build()));
		DeviceSshAccount created = this.createSsh("u", "p", null);
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS, () -> this.service.update(created.id(), DeviceTelnetAccount.builder().username("u").password("p").superPassword(null)
			.name(created.name()).build()));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS, () -> this.service.update(created.id(), sshKey("u", "k", "p", null, created.name())));
	}

	@Test
	@DisplayName("update() refuses a blank name, and a name used by another credential set, and changes nothing")
	void updateNameChecks() {
		DeviceSshAccount first = this.createSsh("first", "p", null);
		DeviceSshAccount second = this.createSsh("second", "p", null);
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, () -> this.service.update(second.id(), DeviceSshAccount.builder().username("changed").password("p").superPassword(null)
			.name(" ")
			.build()));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, () -> this.service.update(second.id(), DeviceSshAccount.builder().username("changed").password("p").superPassword(null)
			.name(null)
			.build()));
		assertError(ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS, () -> this.service.update(second.id(), DeviceSshAccount.builder().username("changed").password("p").superPassword(null)
			.name(first.name()).build()));
		DeviceSshAccount reloaded = (DeviceSshAccount) this.stored(second.id());
		Assertions.assertEquals("second", reloaded.username());
		Assertions.assertEquals(second.name(), reloaded.name());
	}

	@Test
	@DisplayName("update() sets and clears the domain, and refuses an unknown domain without changing anything")
	void updateDomain() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		DeviceSshAccount withDomain = DeviceSshAccount.builder().username("u").password(null).superPassword(null).name(created.name()).mgmtDomain(this.domainRef()).build();
		this.service.update(created.id(), withDomain);
		Assertions.assertEquals(this.domainId, this.stored(created.id()).mgmtDomain().id());

		this.service.update(created.id(), DeviceSshAccount.builder().username("u").password(null).superPassword(null).name(created.name()).build());
		Assertions.assertNull(this.stored(created.id()).mgmtDomain());

		this.service.update(created.id(), withDomain);
		DeviceSshAccount unknownDomain = DeviceSshAccount.builder().username("u").password(null).superPassword(null).name(created.name())
			.mgmtDomain(Domain.reference(99999999L)).build();
		assertError(ErrorCode.NETSHOT_INVALID_DOMAIN, () -> this.service.update(created.id(), unknownDomain));
		Assertions.assertEquals(this.domainId, this.stored(created.id()).mgmtDomain().id(), "the domain is unchanged");
	}

	@Test
	@DisplayName("update() switches a field to a Vault instance and back, and refuses an unknown Vault instance")
	void updateVault() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		DeviceSshAccount toVault = DeviceSshAccount.builder().username("u").password(null).superPassword(null).name(created.name())
			.passwordVaultInstanceId(this.vaultId).passwordVaultPath("a/b").build();
		this.service.update(created.id(), toVault);
		DeviceSshAccount reloaded = (DeviceSshAccount) this.stored(created.id());
		Assertions.assertNull(reloaded.password());
		Assertions.assertEquals(this.vaultId, reloaded.passwordVaultInstanceId());

		this.service.update(created.id(), DeviceSshAccount.builder().username("u").password("again").superPassword(null).name(created.name()).build());
		reloaded = (DeviceSshAccount) this.stored(created.id());
		Assertions.assertEquals("again", reloaded.password());
		Assertions.assertNull(reloaded.passwordVaultInstanceId());
		Assertions.assertNull(reloaded.passwordVaultPath());

		DeviceSshAccount badVault = DeviceSshAccount.builder().username("changed").password(null).superPassword(null).name(created.name())
			.passwordVaultInstanceId(99999999L).build();
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE, () -> this.service.update(created.id(), badVault));
		Assertions.assertEquals("u", ((DeviceSshAccount) this.stored(created.id())).username());
	}

	@Test
	@DisplayName("update() also edits a credential set specific to a device access (no check on that)")
	void updateDeviceSpecific() {
		DeviceCredentialSet specific = TestDevices.addCredentialSet(DeviceSshAccount.builder().username("u").password("p").superPassword(null).name(DeviceCredentialSet.generateSpecificName())
			.deviceSpecific(true).build());
		DeviceCredentialSet updated = this.service.update(specific.id(), DeviceSshAccount.builder().username("changed").password(null).superPassword(null).name("Now named").build());
		Assertions.assertEquals("Now named", updated.name());
		Assertions.assertTrue(this.stored(specific.id()).deviceSpecific());
	}

	@Test
	@DisplayName("delete() removes a credential set, and says when there was none")
	void delete() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		Assertions.assertTrue(this.service.delete(created.id()));
		Assertions.assertNull(this.stored(created.id()));
		Assertions.assertFalse(this.service.delete(created.id()));
		Assertions.assertFalse(this.service.delete(99999999L));
	}

	@Test
	@DisplayName("delete() refuses a credential set specific to a device access")
	void deleteDeviceSpecific() {
		DeviceCredentialSet specific = TestDevices.addCredentialSet(DeviceSshAccount.builder().username("u").password("p").superPassword(null).name(DeviceCredentialSet.generateSpecificName())
			.deviceSpecific(true).build());
		assertError(ErrorCode.NETSHOT_CREDENTIALS_IN_USE, () -> this.service.delete(specific.id()));
		Assertions.assertNotNull(this.stored(specific.id()));
	}

	@Test
	@DisplayName("delete() removes a credential set used by a task, and the link is dropped with it")
	void deleteUsedByTask() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		TestTasks.add(DiscoverDeviceTypeTask.of("10.99.9.1", this.domainId, Set.of(created.id()), "c", "tester"));
		Assertions.assertTrue(this.service.delete(created.id()));
		Assertions.assertNull(this.stored(created.id()));
	}

	@Test
	@DisplayName("delete() reports a constraint violation as 'in use', and any other failure as a database error")
	void deleteFailures() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		CredentialSetService violating = new CredentialSetService(this.transactions, new CredentialSetRepository() {
			@Override
			public boolean remove(DSLContext dsl, long id) {
				throw new DataAccessException("in use", new SQLException("fk", "23503"));
			}
		});
		assertError(ErrorCode.NETSHOT_CREDENTIALS_IN_USE, () -> violating.delete(created.id()));
		CredentialSetService failing = new CredentialSetService(this.transactions, new CredentialSetRepository() {
			@Override
			public boolean remove(DSLContext dsl, long id) {
				throw new IllegalStateException("boom");
			}
		});
		assertError(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, () -> failing.delete(created.id()));
		Assertions.assertNotNull(this.stored(created.id()));
	}

	@Test
	@DisplayName("delete() removes a credential set pinned by a device access, and the pin is cleared")
	void deletePinned() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		Device device = TestDevices.add(Device.create("CiscoIOS12", "10.99.9.2", Domain.reference(this.domainId), "tester").toBuilder()
			.accesses(List.of(DeviceAccess.of("ssh").toBuilder().globalCredentialSet(created).build())).build());
		Assertions.assertEquals(created.id(), TestDevices.find(device.id()).getDeviceAccess("ssh").globalCredentialSet().id());
		Assertions.assertTrue(this.service.delete(created.id()));
		Assertions.assertNull(TestDevices.find(device.id()).getDeviceAccess("ssh").globalCredentialSet());
	}
}
