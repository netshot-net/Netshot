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

import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.CredentialSetService;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.Network6Address;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;

/**
 * Tests of the credential set use cases, against a real database: error codes and their order,
 * the semantics of the secrets left out (kept), the Vault references, the deletion rules.
 */
@TestInstance(Lifecycle.PER_CLASS)
class CredentialSetServiceTest extends WithDatabaseTest {

	private Transactions transactions;
	private CredentialSetService service;
	private int counter;
	private long domainId;
	private long vaultId;

	@BeforeAll
	void initDb() throws Exception {
		Properties config = getFreshDatabaseConfig("credentialsetservicetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		Netshot.initConfig(config);
		Netshot.loadModuleConfigs();
		DeviceDriver.refreshDrivers();
		Database.update();
		Wiring.init();
		this.transactions = new Transactions();
		this.service = new CredentialSetService(this.transactions, new CredentialSetRepository());
		Domain domain = new Domain("Credentials domain", "d", new Network4Address("10.255.0.1"), new Network6Address("::"));
		this.domainId = this.transactions.write(session -> {
			session.persist(domain);
			return domain.getId();
		});
		this.vaultId = this.transactions.write(session -> {
			HashicorpVaultKv2Instance vault = new HashicorpVaultKv2Instance("Credentials vault");
			vault.setBaseUrl("https://vault.example.net");
			vault.setAuthMethod(HashicorpVaultKv2Instance.AuthMethod.APPROLE);
			session.persist(vault);
			return vault.getId();
		});
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
		return this.transactions.read(session -> session.get(DeviceCredentialSet.class, id));
	}

	private Domain domainRef() {
		Domain domain = new Domain("ignored", "", null, null);
		domain.setId(this.domainId);
		return domain;
	}

	private DeviceSshAccount createSsh(String username, String password, String superPassword) {
		return (DeviceSshAccount) this.service.create(new DeviceSshAccount(username, password, superPassword, this.nextName()));
	}

	@Test
	@DisplayName("create() stores every kind of credential set, as a global one")
	void createEveryKind() {
		DeviceSnmpv1Community v1 = new DeviceSnmpv1Community("c1", this.nextName());
		DeviceSnmpv2cCommunity v2 = new DeviceSnmpv2cCommunity("c2", this.nextName());
		DeviceSnmpv3Community v3 = new DeviceSnmpv3Community("ctx", this.nextName(), "u", AuthProtocol.MD5, "ak", PrivProtocol.DES, "pk");
		DeviceSshAccount ssh = new DeviceSshAccount("u", "p", "s", this.nextName());
		DeviceSshKeyAccount key = new DeviceSshKeyAccount("u", "key", "p", "s", this.nextName());
		DeviceTelnetAccount telnet = new DeviceTelnetAccount("u", "p", "s", this.nextName());
		DeviceHttpAccount http = new DeviceHttpAccount("u", "p", this.nextName());
		for (DeviceCredentialSet credentialSet : List.of(v1, v2, v3, ssh, key, telnet, http)) {
			credentialSet.setDeviceSpecific(true);
			DeviceCredentialSet created = this.service.create(credentialSet);
			Assertions.assertTrue(created.getId() > 0);
			DeviceCredentialSet reloaded = this.stored(created.getId());
			Assertions.assertEquals(credentialSet.getClass(), reloaded.getClass());
			Assertions.assertEquals(credentialSet.getName(), reloaded.getName());
			Assertions.assertFalse(reloaded.isDeviceSpecific(), "a credential set created through the use case is a global one");
		}
		Assertions.assertEquals("pk", ((DeviceSnmpv3Community) this.stored(v3.getId())).getPrivKey());
		Assertions.assertEquals("key", ((DeviceSshKeyAccount) this.stored(key.getId())).getPrivateKey());
	}

	@Test
	@DisplayName("create() checks the name first, then the domain")
	void createValidation() {
		DeviceSshAccount blank = new DeviceSshAccount("u", "p", null, "  ");
		blank.setMgmtDomain(this.domainRef());
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, () -> this.service.create(blank));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, () -> this.service.create(new DeviceSshAccount("u", "p", null, null)));

		DeviceSshAccount badDomain = new DeviceSshAccount("u", "p", null, this.nextName());
		Domain missing = new Domain("x", "", null, null);
		missing.setId(99999999L);
		badDomain.setMgmtDomain(missing);
		assertError(ErrorCode.NETSHOT_INVALID_DOMAIN, () -> this.service.create(badDomain));
	}

	@Test
	@DisplayName("create() resolves the domain")
	void createWithDomain() {
		DeviceSshAccount ssh = new DeviceSshAccount("u", "p", null, this.nextName());
		ssh.setMgmtDomain(this.domainRef());
		DeviceCredentialSet created = this.service.create(ssh);
		Assertions.assertEquals("Credentials domain", created.getMgmtDomain().getName());
		Assertions.assertEquals(this.domainId, this.stored(created.getId()).getMgmtDomain().getId());
	}

	@Test
	@DisplayName("create() refuses an unknown Vault instance and stores nothing")
	void createInvalidVault() {
		DeviceSshAccount ssh = new DeviceSshAccount(null, null, null, this.nextName());
		ssh.setResolvedPasswordVaultInstanceId(99999999L);
		ssh.setPasswordVaultPath("a/b");
		int before = this.service.list(Page.ALL).size();
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE, () -> this.service.create(ssh));
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
	}

	@Test
	@DisplayName("create() holds a secret in a Vault instance when asked to, and drops the local value")
	void createWithVault() {
		DeviceSshAccount ssh = new DeviceSshAccount("local-user", "local-pass", null, this.nextName());
		ssh.setResolvedPasswordVaultInstanceId(this.vaultId);
		ssh.setPasswordVaultPath("netshot/ssh");
		DeviceSshAccount created = (DeviceSshAccount) this.service.create(ssh);
		DeviceSshAccount reloaded = (DeviceSshAccount) this.stored(created.getId());
		Assertions.assertNull(reloaded.getPassword());
		Assertions.assertEquals(this.vaultId, reloaded.getResolvedPasswordVaultInstanceId());
		Assertions.assertEquals("netshot/ssh", reloaded.getPasswordVaultPath());
		Assertions.assertEquals("local-user", reloaded.getUsername());
	}

	@Test
	@DisplayName("create() refuses a name which is already used, whatever the kind, and stores nothing")
	void createDuplicate() {
		DeviceSshAccount first = this.createSsh("u", "p", null);
		int before = this.service.list(Page.ALL).size();
		assertError(ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS, () -> this.service.create(new DeviceSshAccount("o", "o", null, first.getName())));
		assertError(ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS, () -> this.service.create(new DeviceTelnetAccount("o", "o", null, first.getName())));
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
	}

	@Test
	@DisplayName("create() refuses a credential set which already has an ID (an unexpected failure, not a Netshot error)")
	void createWithId() {
		DeviceSshAccount ssh = new DeviceSshAccount("u", "p", null, this.nextName());
		ssh.setId(77);
		int before = this.service.list(Page.ALL).size();
		Assertions.assertThrows(RuntimeException.class, () -> this.service.create(ssh));
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
	}

	@Test
	@DisplayName("list() returns the global credential sets only, and honours the page")
	void list() {
		DeviceSshAccount global = this.createSsh("u", "p", null);
		DeviceSshAccount specific = new DeviceSshAccount("u", "p", null, DeviceCredentialSet.generateSpecificName());
		specific.setDeviceSpecific(true);
		this.transactions.run(session -> session.persist(specific));
		List<DeviceCredentialSet> all = this.service.list(Page.ALL);
		Assertions.assertTrue(all.stream().anyMatch(cs -> cs.getId() == global.getId()));
		Assertions.assertTrue(all.stream().noneMatch(cs -> cs.getId() == specific.getId()));
		Assertions.assertTrue(all.stream().noneMatch(DeviceCredentialSet::isDeviceSpecific));
		Assertions.assertEquals(1, this.service.list(new Page(0, 1)).size());
		Assertions.assertEquals(all.size() - 1, this.service.list(new Page(1, null)).size());
	}

	@Test
	@DisplayName("list() returns credential sets whose domain and Vault references can be read without a session")
	void listIsReadable() {
		DeviceSshAccount ssh = new DeviceSshAccount("u", null, null, this.nextName());
		ssh.setMgmtDomain(this.domainRef());
		ssh.setResolvedPasswordVaultInstanceId(this.vaultId);
		ssh.setPasswordVaultPath("p");
		DeviceCredentialSet created = this.service.create(ssh);
		DeviceSshAccount listed = (DeviceSshAccount) this.service.list(Page.ALL).stream()
			.filter(cs -> cs.getId() == created.getId()).findFirst().orElseThrow();
		Assertions.assertEquals("Credentials domain", listed.getMgmtDomain().getName());
		Assertions.assertEquals(this.vaultId, listed.getResolvedPasswordVaultInstanceId());
	}

	@Test
	@DisplayName("update() keeps the secrets which are not given, and changes the other fields")
	void updateKeepsSecrets() {
		DeviceSshAccount created = this.createSsh("user", "pass", "enable");
		DeviceSshAccount incoming = new DeviceSshAccount("user2", null, null, this.nextName());
		DeviceSshAccount updated = (DeviceSshAccount) this.service.update(created.getId(), incoming);
		Assertions.assertEquals(created.getId(), updated.getId());
		DeviceSshAccount reloaded = (DeviceSshAccount) this.stored(created.getId());
		Assertions.assertEquals("user2", reloaded.getUsername());
		Assertions.assertEquals("pass", reloaded.getPassword());
		Assertions.assertEquals("enable", reloaded.getSuperPassword());
		Assertions.assertEquals(incoming.getName(), reloaded.getName());

		this.service.update(created.getId(), new DeviceSshAccount("user3", "newpass", "newenable", incoming.getName()));
		reloaded = (DeviceSshAccount) this.stored(created.getId());
		Assertions.assertEquals("newpass", reloaded.getPassword());
		Assertions.assertEquals("newenable", reloaded.getSuperPassword());

		// The username is not a secret: not giving it clears it
		this.service.update(created.getId(), new DeviceSshAccount(null, null, null, incoming.getName()));
		Assertions.assertNull(((DeviceSshAccount) this.stored(created.getId())).getUsername());
	}

	@Test
	@DisplayName("update() of a SSH key account clears the private key when it is not given (a known quirk)")
	void updateSshKey() {
		DeviceSshKeyAccount created = (DeviceSshKeyAccount) this.service.create(
			new DeviceSshKeyAccount("u", "the-key", "p", null, this.nextName()));
		this.service.update(created.getId(), new DeviceSshKeyAccount("u", null, null, null, created.getName()));
		DeviceSshKeyAccount reloaded = (DeviceSshKeyAccount) this.stored(created.getId());
		Assertions.assertEquals("p", reloaded.getPassword(), "the password is kept");
		Assertions.assertNull(reloaded.getPrivateKey(), "the private key is not kept when it is not given");
	}

	@Test
	@DisplayName("update() keeps the SNMP and HTTP secrets which are not given")
	void updateOtherKinds() {
		DeviceSnmpv1Community v1 = (DeviceSnmpv1Community) this.service.create(new DeviceSnmpv1Community("c1", this.nextName()));
		this.service.update(v1.getId(), new DeviceSnmpv1Community(null, v1.getName()));
		Assertions.assertEquals("c1", ((DeviceSnmpv1Community) this.stored(v1.getId())).getCommunity());
		this.service.update(v1.getId(), new DeviceSnmpv1Community("c2", v1.getName()));
		Assertions.assertEquals("c2", ((DeviceSnmpv1Community) this.stored(v1.getId())).getCommunity());

		DeviceSnmpv3Community v3 = (DeviceSnmpv3Community) this.service.create(
			new DeviceSnmpv3Community("ctx", this.nextName(), "u", AuthProtocol.MD5, "ak", PrivProtocol.DES, "pk"));
		this.service.update(v3.getId(), new DeviceSnmpv3Community(null, v3.getName(), "u2", AuthProtocol.SHA, null, PrivProtocol.AES128, null));
		DeviceSnmpv3Community reloaded = (DeviceSnmpv3Community) this.stored(v3.getId());
		Assertions.assertEquals("u2", reloaded.getUsername());
		Assertions.assertEquals(AuthProtocol.SHA, reloaded.getAuthType());
		Assertions.assertEquals(PrivProtocol.AES128, reloaded.getPrivType());
		Assertions.assertEquals("ak", reloaded.getAuthKey());
		Assertions.assertEquals("pk", reloaded.getPrivKey());
		Assertions.assertEquals("ctx", reloaded.getCommunity(), "the context community is kept");

		DeviceHttpAccount http = (DeviceHttpAccount) this.service.create(new DeviceHttpAccount("u", "p", this.nextName()));
		this.service.update(http.getId(), new DeviceHttpAccount("u2", null, http.getName()));
		DeviceHttpAccount reloadedHttp = (DeviceHttpAccount) this.stored(http.getId());
		Assertions.assertEquals("u2", reloadedHttp.getUsername());
		Assertions.assertEquals("p", reloadedHttp.getPassword());
	}

	@Test
	@DisplayName("update() reports an unknown credential set, or one of another kind, the same way")
	void updateNotFound() {
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS, () -> this.service.update(99999999L, new DeviceSshAccount("u", "p", null, "x")));
		DeviceSshAccount created = this.createSsh("u", "p", null);
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS, () -> this.service.update(created.getId(), new DeviceTelnetAccount("u", "p", null, created.getName())));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS, () -> this.service.update(created.getId(), new DeviceSshKeyAccount("u", "k", "p", null, created.getName())));
	}

	@Test
	@DisplayName("update() refuses a blank name, and a name used by another credential set, and changes nothing")
	void updateNameChecks() {
		DeviceSshAccount first = this.createSsh("first", "p", null);
		DeviceSshAccount second = this.createSsh("second", "p", null);
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, () -> this.service.update(second.getId(), new DeviceSshAccount("changed", "p", null, " ")));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, () -> this.service.update(second.getId(), new DeviceSshAccount("changed", "p", null, null)));
		assertError(ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS, () -> this.service.update(second.getId(), new DeviceSshAccount("changed", "p", null, first.getName())));
		DeviceSshAccount reloaded = (DeviceSshAccount) this.stored(second.getId());
		Assertions.assertEquals("second", reloaded.getUsername());
		Assertions.assertEquals(second.getName(), reloaded.getName());
	}

	@Test
	@DisplayName("update() sets, clears and silently drops the domain")
	void updateDomain() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		DeviceSshAccount withDomain = new DeviceSshAccount("u", null, null, created.getName());
		withDomain.setMgmtDomain(this.domainRef());
		this.service.update(created.getId(), withDomain);
		Assertions.assertEquals(this.domainId, this.stored(created.getId()).getMgmtDomain().getId());

		this.service.update(created.getId(), new DeviceSshAccount("u", null, null, created.getName()));
		Assertions.assertNull(this.stored(created.getId()).getMgmtDomain());

		this.service.update(created.getId(), withDomain);
		DeviceSshAccount unknownDomain = new DeviceSshAccount("u", null, null, created.getName());
		Domain missing = new Domain("x", "", null, null);
		missing.setId(99999999L);
		unknownDomain.setMgmtDomain(missing);
		this.service.update(created.getId(), unknownDomain);
		Assertions.assertNull(this.stored(created.getId()).getMgmtDomain(), "an unknown domain is not an error: the restriction is dropped");
	}

	@Test
	@DisplayName("update() switches a field to a Vault instance and back, and refuses an unknown Vault instance")
	void updateVault() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		DeviceSshAccount toVault = new DeviceSshAccount("u", null, null, created.getName());
		toVault.setResolvedPasswordVaultInstanceId(this.vaultId);
		toVault.setPasswordVaultPath("a/b");
		this.service.update(created.getId(), toVault);
		DeviceSshAccount reloaded = (DeviceSshAccount) this.stored(created.getId());
		Assertions.assertNull(reloaded.getPassword());
		Assertions.assertEquals(this.vaultId, reloaded.getResolvedPasswordVaultInstanceId());

		this.service.update(created.getId(), new DeviceSshAccount("u", "again", null, created.getName()));
		reloaded = (DeviceSshAccount) this.stored(created.getId());
		Assertions.assertEquals("again", reloaded.getPassword());
		Assertions.assertNull(reloaded.getResolvedPasswordVaultInstanceId());
		Assertions.assertNull(reloaded.getPasswordVaultPath());

		DeviceSshAccount badVault = new DeviceSshAccount("changed", null, null, created.getName());
		badVault.setResolvedPasswordVaultInstanceId(99999999L);
		assertError(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE, () -> this.service.update(created.getId(), badVault));
		Assertions.assertEquals("u", ((DeviceSshAccount) this.stored(created.getId())).getUsername());
	}

	@Test
	@DisplayName("update() also edits a credential set specific to a device access (no check on that)")
	void updateDeviceSpecific() {
		DeviceSshAccount specific = new DeviceSshAccount("u", "p", null, DeviceCredentialSet.generateSpecificName());
		specific.setDeviceSpecific(true);
		this.transactions.run(session -> session.persist(specific));
		DeviceCredentialSet updated = this.service.update(specific.getId(), new DeviceSshAccount("changed", null, null, "Now named"));
		Assertions.assertEquals("Now named", updated.getName());
		Assertions.assertTrue(this.stored(specific.getId()).isDeviceSpecific());
	}

	@Test
	@DisplayName("delete() removes a credential set, and says when there was none")
	void delete() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		Assertions.assertTrue(this.service.delete(created.getId()));
		Assertions.assertNull(this.stored(created.getId()));
		Assertions.assertFalse(this.service.delete(created.getId()));
		Assertions.assertFalse(this.service.delete(99999999L));
	}

	@Test
	@DisplayName("delete() refuses a credential set specific to a device access")
	void deleteDeviceSpecific() {
		DeviceSshAccount specific = new DeviceSshAccount("u", "p", null, DeviceCredentialSet.generateSpecificName());
		specific.setDeviceSpecific(true);
		this.transactions.run(session -> session.persist(specific));
		assertError(ErrorCode.NETSHOT_CREDENTIALS_IN_USE, () -> this.service.delete(specific.getId()));
		Assertions.assertNotNull(this.stored(specific.getId()));
	}

	@Test
	@DisplayName("delete() removes a credential set used by a task, and the link is dropped with it")
	void deleteUsedByTask() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		this.transactions.run(session -> {
			DiscoverDeviceTypeTask task = new DiscoverDeviceTypeTask("10.99.9.1", session.get(Domain.class, this.domainId), "c", "tester");
			task.addCredentialSet(session.get(DeviceCredentialSet.class, created.getId()));
			session.persist(task);
		});
		Assertions.assertTrue(this.service.delete(created.getId()));
		Assertions.assertNull(this.stored(created.getId()));
	}

	@Test
	@DisplayName("delete() reports a constraint violation as 'in use', and any other failure as a database error")
	void deleteFailures() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		CredentialSetService violating = new CredentialSetService(this.transactions, new CredentialSetRepository() {
			@Override
			public void remove(Session session, DeviceCredentialSet credentialSet) {
				throw new ConstraintViolationException("in use", new SQLException("fk"), "fk_name");
			}
		});
		assertError(ErrorCode.NETSHOT_CREDENTIALS_IN_USE, () -> violating.delete(created.getId()));
		CredentialSetService failing = new CredentialSetService(this.transactions, new CredentialSetRepository() {
			@Override
			public void remove(Session session, DeviceCredentialSet credentialSet) {
				throw new IllegalStateException("boom");
			}
		});
		assertError(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, () -> failing.delete(created.getId()));
		Assertions.assertNotNull(this.stored(created.getId()));
	}

	@Test
	@DisplayName("delete() removes a credential set pinned by a device access, and the pin is cleared")
	void deletePinned() {
		DeviceSshAccount created = this.createSsh("u", "p", null);
		long deviceId = this.transactions.write(session -> {
			Device device = new Device("CiscoIOS12", "10.99.9.2", session.get(Domain.class, this.domainId), "tester");
			device.pinGlobalCredentialSet("ssh", session.get(DeviceCredentialSet.class, created.getId()));
			session.persist(device);
			return device.getId();
		});
		Assertions.assertTrue(this.service.delete(created.getId()));
		Assertions.assertNull(this.transactions.read(session -> session.get(Device.class, deviceId).getDeviceAccess("ssh").getGlobalCredentialSet()));
	}
}
