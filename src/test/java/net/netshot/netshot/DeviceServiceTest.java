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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;
import org.quartz.SchedulerException;

import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceCreation;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.device.DeviceUpdate;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Tests of the device use cases, against a real database and a recording task submitter.
 */
@TestInstance(Lifecycle.PER_CLASS)
class DeviceServiceTest extends WithDatabaseTest {

	private static final String AUTHOR = "tester";

	private final List<Task> submittedTasks = new ArrayList<>();
	private final AtomicInteger addressCounter = new AtomicInteger(10);
	private SchedulerException schedulerFailure;
	private DeviceService service;
	private Transactions transactions;
	private Path binaryFolder;
	private long domainId;
	private long otherDomainId;
	private long sshAccountId;

	@BeforeAll
	void initDb() throws Exception {
		Properties config = getFreshDatabaseConfig("deviceservicetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		this.binaryFolder = Files.createTempDirectory("netshot-device-service-");
		config.setProperty("netshot.snapshots.binary.path", this.binaryFolder.toString());
		Netshot.initConfig(config);
		Netshot.loadModuleConfigs();
		TestDrivers.reload();
		Database.update();
		Wiring.init(TestDrivers.registry());
		this.transactions = new Transactions();
		TaskSubmitter submitter = task -> {
			if (this.schedulerFailure != null) {
				throw this.schedulerFailure;
			}
			this.submittedTasks.add(task);
		};
		this.service = new DeviceService(this.transactions, new DeviceRepository(), submitter, TestDrivers.registry());
		this.domainId = this.transactions.write(session -> {
			Domain domain = new Domain("Lab", "Test domain", null, null);
			session.persist(domain);
			return domain.getId();
		});
		this.otherDomainId = this.transactions.write(session -> {
			Domain domain = new Domain("Other lab", "Other test domain", null, null);
			session.persist(domain);
			return domain.getId();
		});
		this.sshAccountId = this.transactions.write(session -> {
			DeviceSshAccount account = new DeviceSshAccount("admin", "secret", "enable", "Lab SSH account");
			session.persist(account);
			return account.getId();
		});
	}

	@BeforeEach
	void resetSubmitter() {
		this.submittedTasks.clear();
		this.schedulerFailure = null;
	}

	private String nextAddress() {
		return "10.20.0." + this.addressCounter.getAndIncrement();
	}

	private DeviceCreation creation(String address) {
		return new DeviceCreation(false, address, this.domainId, "CiscoIOS12", null, null, null, null, AUTHOR);
	}

	private Device createDevice(String address) {
		Task task = this.service.create(this.creation(address));
		return ((TakeSnapshotTask) task).getDevice();
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private static DeviceAccess access(String name) {
		return new DeviceAccess(null, name);
	}

	/** A reference to a global credential set, as a request holds it (only the ID counts). */
	private static DeviceCredentialSet credentialRef(long id) {
		DeviceSshAccount reference = new DeviceSshAccount(null, null, null, "ref");
		reference.setId(id);
		return reference;
	}

	// ------------------------------------------------------------------
	// create
	// ------------------------------------------------------------------

	@Test
	@DisplayName("create() stores the device and submits an initial snapshot task")
	void createImmediate() {
		String address = this.nextAddress();
		DeviceAccess ssh = access("ssh");
		ssh.setPort(2222);
		DeviceCreation creation = new DeviceCreation(false, address, this.domainId, "CiscoIOS12",
			List.of(ssh), Map.of(), "Some comments", List.of(" core ", "lab"), AUTHOR);
		Task task = this.service.create(creation);
		TakeSnapshotTask snapshot = Assertions.assertInstanceOf(TakeSnapshotTask.class, task);
		Assertions.assertEquals(List.of(task), this.submittedTasks);
		Assertions.assertEquals(AUTHOR, snapshot.getAuthor());

		Device device = this.service.get(snapshot.getDevice().getId());
		Assertions.assertEquals(address, device.getMgmtAddress());
		Assertions.assertEquals("CiscoIOS12", device.getDriver());
		Assertions.assertEquals(AUTHOR, device.getCreator());
		Assertions.assertEquals("Some comments", device.getComments());
		Assertions.assertEquals(java.util.Set.of("core", "lab"), device.getTags());
		Assertions.assertEquals(this.domainId, device.getMgmtDomain().getId());
		Assertions.assertEquals(1, device.getAccesses().size());
		DeviceAccess stored = device.getDeviceAccess("ssh");
		Assertions.assertEquals(2222, stored.getPort());
		Assertions.assertEquals(DeviceAccess.SshHostKeyVerification.TRUST_ANY, stored.getSshHostKeyVerification());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, stored.getHttpsCaTrustMode());
	}

	@Test
	@DisplayName("create() applies the strict SSH host key verification to an access which has none")
	void createDefaultHostKeyVerification() {
		DeviceAccess ssh = access("ssh");
		ssh.setSshHostKeyVerification(null);
		ssh.setHttpsCaTrustMode(null);
		Device created = ((TakeSnapshotTask) this.service.create(new DeviceCreation(false, this.nextAddress(), this.domainId,
			"CiscoIOS12", List.of(ssh), null, null, null, AUTHOR))).getDevice();
		DeviceAccess stored = this.service.get(created.getId()).getDeviceAccess("ssh");
		Assertions.assertEquals(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN, stored.getSshHostKeyVerification());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, stored.getHttpsCaTrustMode());
	}

	@Test
	@DisplayName("create() stores a device specific credential set and pins a global one")
	void createWithCredentials() {
		DeviceAccess ssh = access("ssh");
		ssh.setGlobalCredentialSet(credentialRef(this.sshAccountId));
		DeviceAccess telnet = access("telnet");
		telnet.setSpecificCredentialSet(new DeviceTelnetAccount("user", "pass", "enable", "ignored"));
		DeviceCreation creation = new DeviceCreation(false, this.nextAddress(), this.domainId, "CiscoIOS12",
			List.of(ssh, telnet), null, null, null, AUTHOR);
		Device created = ((TakeSnapshotTask) this.service.create(creation)).getDevice();

		Device device = this.service.get(created.getId());
		Assertions.assertEquals(this.sshAccountId, device.getDeviceAccess("ssh").getGlobalCredentialSet().getId());
		DeviceCredentialSet specific = device.getDeviceAccess("telnet").getSpecificCredentialSet();
		Assertions.assertTrue(specific.isDeviceSpecific());
		Assertions.assertTrue(specific.getName().startsWith("DEVICESPECIFIC-"), "synthetic name");
	}

	@Test
	@DisplayName("create() with auto discovery submits a discovery task and creates no device")
	void createAutoDiscover() {
		this.transactions.run(session -> {
			DeviceSnmpv2cCommunity community = new DeviceSnmpv2cCommunity("public", "Lab SNMP");
			community.setMgmtDomain(session.get(Domain.class, this.otherDomainId));
			session.persist(community);
		});
		String address = this.nextAddress();
		Task task = this.service.create(new DeviceCreation(true, address, this.otherDomainId, "",
			null, null, "Discovered", List.of("auto"), AUTHOR));
		DiscoverDeviceTypeTask discovery = Assertions.assertInstanceOf(DiscoverDeviceTypeTask.class, task);
		Assertions.assertEquals(List.of(task), this.submittedTasks);
		Assertions.assertEquals(address, discovery.getDeviceAddress());
		Assertions.assertEquals("Discovered", discovery.getDeviceComments());
		Assertions.assertEquals(List.of("auto"), discovery.getDeviceTags());
		Assertions.assertEquals(1, discovery.getCredentialSets().size());
		Assertions.assertEquals(0, this.countDevices(address), "the device is created by the task, if the discovery succeeds");
	}

	@Test
	@DisplayName("create() with auto discovery needs a SNMP community")
	void createAutoDiscoverWithoutCommunity() {
		long emptyDomainId = this.transactions.write(session -> {
			Domain domain = new Domain("No community", "", null, null);
			session.persist(domain);
			return domain.getId();
		});
		DeviceCreation creation = new DeviceCreation(true, this.nextAddress(), emptyDomainId, "", null, null, null, null, AUTHOR);
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS, () -> this.service.create(creation));
		Assertions.assertTrue(this.submittedTasks.isEmpty());
	}

	@Test
	@DisplayName("create() reports the invalid addresses")
	void createInvalidAddress() {
		assertError(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, () -> this.service.create(this.creation(null)));
		assertError(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, () -> this.service.create(this.creation("")));
		assertError(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, () -> this.service.create(this.creation("not a host name")));
		assertError(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, () -> this.service.create(this.creation("224.0.0.1")));
		assertError(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, () -> this.service.create(this.creation("127.0.0.1")));
		assertError(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, () -> this.service.create(this.creation("ff02::1")));
		assertError(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, () -> this.service.create(this.creation("2001:db8::zz")));
	}

	@Test
	@DisplayName("create() accepts host names and IPv6 addresses")
	void createHostNameAndIpv6() {
		Device byName = this.createDevice("localhost");
		Assertions.assertEquals("localhost", byName.getMgmtAddress());
		Device byIpv6 = this.createDevice("2001:db8::" + Integer.toHexString(this.addressCounter.getAndIncrement()));
		Assertions.assertTrue(byIpv6.getMgmtAddress().startsWith("2001:db8::"));
	}

	@Test
	@DisplayName("create() rejects invalid tags, unknown domain and unknown driver, in this order of checks")
	void createInvalidRequest() {
		String address = this.nextAddress();
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", null, null, null, List.of("ok", "  "), AUTHOR)));
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", null, null, null, List.of("x".repeat(256)), AUTHOR)));
		assertError(ErrorCode.NETSHOT_INVALID_DOMAIN, () -> this.service.create(
			new DeviceCreation(false, address, 99999999L, "CiscoIOS12", null, null, null, null, AUTHOR)));
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE_DRIVER, () -> this.service.create(
			new DeviceCreation(false, address, this.domainId, "NoSuchDriver", null, null, null, null, AUTHOR)));
		Assertions.assertEquals(0, this.countDevices(address));
		Assertions.assertTrue(this.submittedTasks.isEmpty());
	}

	@Test
	@DisplayName("create() validates the options and the accesses, and stores nothing then")
	void createInvalidOptionsAndAccesses() {
		String address = this.nextAddress();
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", null, Map.of("unknown", true), null, null, AUTHOR)));
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.create(
			new DeviceCreation(false, address, this.domainId, "SkyhighSWG", null, Map.of("fullBackup", "yes"), null, null, AUTHOR)));
		DeviceAccess noName = access("");
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(noName), null, null, null, AUTHOR)));
		DeviceAccess badPort = access("ssh");
		badPort.setPort(70000);
		assertError(ErrorCode.NETSHOT_INVALID_PORT, () -> this.service.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(badPort), null, null, null, AUTHOR)));
		DeviceAccess badAddress = access("ssh");
		badAddress.setAddress("not an address");
		assertError(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, () -> this.service.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(badAddress), null, null, null, AUTHOR)));
		DeviceAccess unknownAccess = access("carrier-pigeon");
		unknownAccess.setSpecificCredentialSet(new DeviceTelnetAccount("u", "p", "e", "n"));
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(unknownAccess), null, null, null, AUTHOR)));
		DeviceAccess wrongKind = access("ssh");
		wrongKind.setSpecificCredentialSet(new DeviceSnmpv2cCommunity("public", "n"));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE, () -> this.service.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(wrongKind), null, null, null, AUTHOR)));
		DeviceAccess both = access("ssh");
		both.setSpecificCredentialSet(new DeviceSshAccount("u", "p", "e", "n"));
		both.setGlobalCredentialSet(credentialRef(this.sshAccountId));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS, () -> this.service.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(both), null, null, null, AUTHOR)));
		DeviceAccess unknownGlobal = access("ssh");
		unknownGlobal.setGlobalCredentialSet(credentialRef(99999999L));
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS, () -> this.service.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(unknownGlobal), null, null, null, AUTHOR)));
		Assertions.assertEquals(0, this.countDevices(address));
		Assertions.assertTrue(this.submittedTasks.isEmpty());
	}

	@Test
	@DisplayName("create() refuses the address of an active device, whatever its case, but not of a disabled one")
	void createDuplicate() {
		String address = "2001:db8:ab::" + Integer.toHexString(this.addressCounter.getAndIncrement());
		Device first = this.createDevice(address);
		NetshotException error = assertError(ErrorCode.NETSHOT_DUPLICATE_DEVICE,
			() -> this.service.create(this.creation(address.toUpperCase())));
		Assertions.assertTrue(error.getMessage().contains("(" + first.getId() + ")"), error.getMessage());
		this.service.update(first.getId(), new DeviceUpdate(false, null, null, null, null, null, null));
		Device second = this.createDevice(address);
		Assertions.assertNotEquals(first.getId(), second.getId());
	}

	@Test
	@DisplayName("create() reports a task which can't be scheduled, once the device is stored")
	void createSchedulerFailure() {
		String address = this.nextAddress();
		this.schedulerFailure = new SchedulerException("no scheduler");
		assertError(ErrorCode.NETSHOT_SCHEDULE_ERROR, () -> this.service.create(this.creation(address)));
		Assertions.assertEquals(1, this.countDevices(address));
	}

	// ------------------------------------------------------------------
	// update
	// ------------------------------------------------------------------

	@Test
	@DisplayName("A boolean option given as text (as XML gives it) is stored as a boolean, on creation and update")
	void booleanOptionAsText() {
		Device created = ((TakeSnapshotTask) this.service.create(new DeviceCreation(false, this.nextAddress(), this.domainId,
			"SkyhighSWG", null, Map.of("fullBackup", "true"), null, null, AUTHOR))).getDevice();
		Assertions.assertEquals(Map.of("fullBackup", true), this.service.get(created.getId()).getOptions());
		Device updated = this.service.update(created.getId(), new DeviceUpdate(null, null, null, null, null, Map.of("fullBackup", "false"), null));
		Assertions.assertEquals(Map.of("fullBackup", false), updated.getOptions());
	}

	@Test
	@DisplayName("update() leaves unchanged what the command leaves null")
	void updateNullsAreUnchanged() {
		DeviceAccess ssh = access("ssh");
		ssh.setPort(2200);
		Device created = ((TakeSnapshotTask) this.service.create(new DeviceCreation(false, this.nextAddress(), this.domainId,
			"SkyhighSWG", List.of(ssh), Map.of("fullBackup", true), "Initial comments", List.of("a", "b"), AUTHOR))).getDevice();

		Device updated = this.service.update(created.getId(), new DeviceUpdate(null, null, null, null, null, null, null));
		Assertions.assertEquals(created.getMgmtAddress(), updated.getMgmtAddress());
		Assertions.assertEquals(Device.Status.INPRODUCTION, updated.getStatus());
		Assertions.assertEquals("Initial comments", updated.getComments());
		Assertions.assertEquals(java.util.Set.of("a", "b"), updated.getTags());
		Assertions.assertEquals(Map.of("fullBackup", true), updated.getOptions());
		Assertions.assertEquals(this.domainId, updated.getMgmtDomain().getId());
		Assertions.assertEquals(2200, updated.getDeviceAccess("ssh").getPort());
	}

	@Test
	@DisplayName("update() changes what the command provides")
	void updateChanges() {
		Device created = this.createDevice(this.nextAddress());
		String newAddress = this.nextAddress();
		DeviceAccess telnet = access("telnet");
		telnet.setAddress("10.99.0.1");
		Device updated = this.service.update(created.getId(), new DeviceUpdate(false, "New comments", newAddress,
			this.otherDomainId, List.of(telnet), Map.of(), List.of("x")));
		Assertions.assertEquals(Device.Status.DISABLED, updated.getStatus());
		Assertions.assertEquals("New comments", updated.getComments());
		Assertions.assertEquals(newAddress, updated.getMgmtAddress());
		Assertions.assertEquals(newAddress, updated.getCachedIpAddress().getHostAddress());
		Assertions.assertEquals(this.otherDomainId, updated.getMgmtDomain().getId());
		Assertions.assertEquals(java.util.Set.of("x"), updated.getTags());
		Assertions.assertEquals(1, updated.getAccesses().size(), "the accesses are replaced as a whole");
		Assertions.assertEquals("10.99.0.1", updated.getDeviceAccess("telnet").getAddress());

		Device reenabled = this.service.update(created.getId(), new DeviceUpdate(true, null, null, null, null, null, List.of()));
		Assertions.assertEquals(Device.Status.INPRODUCTION, reenabled.getStatus());
		Assertions.assertTrue(reenabled.getTags().isEmpty(), "an empty list of tags clears the tags");
	}

	@Test
	@DisplayName("update() reports the invalid changes and applies none of them")
	void updateInvalid() {
		Device created = this.createDevice(this.nextAddress());
		long id = created.getId();
		assertError(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, () -> this.service.update(99999999L, new DeviceUpdate(null, "x", null, null, null, null, null)));
		assertError(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, () -> this.service.update(id, new DeviceUpdate(null, "changed", "bad address", null, null, null, null)));
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.update(id, new DeviceUpdate(null, "changed", null, null, null, Map.of("nope", 1), null)));
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.update(id, new DeviceUpdate(null, "changed", null, null, null, null, List.of(" "))));
		assertError(ErrorCode.NETSHOT_INVALID_DOMAIN, () -> this.service.update(id, new DeviceUpdate(null, "changed", null, 99999999L, null, null, null)));
		DeviceAccess badPort = access("ssh");
		badPort.setPort(0);
		assertError(ErrorCode.NETSHOT_INVALID_PORT, () -> this.service.update(id, new DeviceUpdate(null, "changed", null, null, List.of(badPort), null, null)));
		Assertions.assertEquals("", this.service.get(id).getComments(), "failed updates are rolled back");
	}

	@Test
	@DisplayName("update() refuses an address used by another active device")
	void updateDuplicate() {
		Device first = this.createDevice(this.nextAddress());
		Device second = this.createDevice(this.nextAddress());
		assertError(ErrorCode.NETSHOT_DUPLICATE_DEVICE, () -> this.service.update(second.getId(),
			new DeviceUpdate(null, null, first.getMgmtAddress(), null, null, null, null)));
		// Same address as itself is fine
		this.service.update(second.getId(), new DeviceUpdate(null, null, second.getMgmtAddress(), null, null, null, null));
		// A disabled device doesn't conflict, and is not checked
		this.service.update(first.getId(), new DeviceUpdate(false, null, null, null, null, null, null));
		this.service.update(second.getId(), new DeviceUpdate(null, null, first.getMgmtAddress(), null, null, null, null));
	}

	// ------------------------------------------------------------------
	// get, list
	// ------------------------------------------------------------------

	@Test
	@DisplayName("get() reports an unknown device, and returns a device usable once the session is closed")
	void get() throws Exception {
		assertError(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, () -> this.service.get(99999999L));
		DeviceAccess ssh = access("ssh");
		ssh.setSpecificCredentialSet(new DeviceSshAccount("u", "p", "e", "n"));
		Device created = ((TakeSnapshotTask) this.service.create(new DeviceCreation(false, this.nextAddress(), this.domainId,
			"CiscoIOS12", List.of(ssh), null, null, List.of("t"), AUTHOR))).getDevice();
		Device device = this.service.get(created.getId());
		// Everything the full representation reads (a LazyInitializationException would show here)
		Assertions.assertEquals("Lab", device.getMgmtDomain().getName());
		Assertions.assertNull(device.getEolModule());
		Assertions.assertTrue(device.getAttributes().isEmpty());
		Assertions.assertTrue(device.getOwnerGroups().isEmpty());
		Assertions.assertTrue(device.isCompliant());
		Assertions.assertEquals("u", ((DeviceSshAccount) device.getDeviceAccess("ssh").getSpecificCredentialSet()).getUsername());
		Assertions.assertEquals(java.util.Set.of("t"), device.getTags());
		Assertions.assertEquals("Cisco IOS and IOS-XE", TestDrivers.registry().forDevice(device).getDescription());
	}

	@Test
	@DisplayName("list() returns the summaries, in pages")
	void list() {
		Device created = this.createDevice(this.nextAddress());
		List<DeviceSummary> all = this.service.list(null, Page.ALL);
		DeviceSummary summary = all.stream().filter(s -> s.id() == created.getId()).findFirst().orElseThrow();
		Assertions.assertEquals(created.getMgmtAddress(), summary.mgmtAddress());
		Assertions.assertEquals("CiscoIOS12", summary.driver());
		Assertions.assertEquals(Device.Status.INPRODUCTION, summary.status());
		Assertions.assertEquals(Boolean.TRUE, summary.configCompliant());
		Assertions.assertEquals(Boolean.FALSE, summary.eol());
		Assertions.assertEquals(Boolean.FALSE, summary.eos());

		Assertions.assertEquals(1, this.service.list(null, new Page(null, 1)).size());
		Assertions.assertEquals(all.size() - 1, this.service.list(null, new Page(1, null)).size());
		Assertions.assertTrue(this.service.list(999999999L, Page.ALL).isEmpty(), "no member in an unknown group");
	}

	@Test
	@DisplayName("A page with invalid parameters is refused")
	void invalidPage() {
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> new Page(-1, null));
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> new Page(null, 0));
	}

	// ------------------------------------------------------------------
	// delete
	// ------------------------------------------------------------------

	@Test
	@DisplayName("delete() removes the device, and tells whether there was one")
	void delete() {
		Device created = this.createDevice(this.nextAddress());
		Assertions.assertTrue(this.service.delete(created.getId()));
		assertError(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, () -> this.service.get(created.getId()));
		Assertions.assertFalse(this.service.delete(created.getId()), "already deleted");
		Assertions.assertFalse(this.service.delete(99999999L));
	}

	@Test
	@DisplayName("delete() removes the binary files of the configurations of the device")
	void deleteRemovesBinaryFiles() throws Exception {
		Device created = this.createDevice(this.nextAddress());
		Path file = this.transactions.write(session -> {
			Device device = session.get(Device.class, created.getId());
			Config config = new Config(device);
			session.persist(config);
			ConfigBinaryFileAttribute attribute = new ConfigBinaryFileAttribute(config, "binary", "original.bin");
			session.persist(attribute);
			session.flush();
			return attribute.getFilePath();
		});
		Files.createDirectories(file.getParent());
		Files.writeString(file, "binary content");
		Assertions.assertTrue(this.service.delete(created.getId()));
		Assertions.assertFalse(Files.exists(file), "the binary file is removed with the device");
	}

	private long countDevices(String address) {
		try (Session session = Database.getSession()) {
			return session.createQuery("select count(d) from Device d where d.mgmtAddress = :address", Long.class)
				.setParameter("address", address).uniqueResult();
		}
	}
}
