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
package net.netshot.netshot.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;
import org.quartz.SchedulerException;

import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceCreation;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.inventory.device.DeviceUpdate;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;

/**
 * Tests of the use case which adds a device, against a real database and a recording task submitter.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
class DeviceCreationServiceTest {

	private static final String AUTHOR = "tester";

	private final List<Task> submittedTasks = new ArrayList<>();
	private final AtomicInteger addressCounter = new AtomicInteger(10);
	private SchedulerException schedulerFailure;
	private DeviceService service;
	private DeviceCreationService creationService;
	private Transactions transactions;
	private long domainId;
	private long otherDomainId;
	private long sshAccountId;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = TestWiring.get().transactions();
		TaskSubmitter submitter = task -> {
			if (this.schedulerFailure != null) {
				throw this.schedulerFailure;
			}
			this.submittedTasks.add(task);
			return task;
		};
		DynamicGroups dynamicGroups = new DynamicGroups(TestWiring.get().transactions(), TestDrivers.registry());
		this.service = new DeviceService(this.transactions, new DeviceRepository(), TestDrivers.registry(), dynamicGroups);
		this.creationService = new DeviceCreationService(this.transactions, new DeviceRepository(), submitter, TestDrivers.registry(), dynamicGroups);
		this.domainId = TestDevices.addDomain("Lab", "Test domain").id();
		this.otherDomainId = TestDevices.addDomain("Other lab", "Other test domain").id();
		this.sshAccountId = TestDevices.addCredentialSet(DeviceSshAccount.builder().username("admin").password("secret").superPassword("enable")
			.name("Lab SSH account").build()).id();
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

	// The device a snapshot task targets
	private Device deviceOf(Task task) {
		return this.service.get(((TakeSnapshotTask) task.spec()).deviceId());
	}

	private Device createDevice(String address) {
		Task task = this.creationService.create(this.creation(address));
		return this.deviceOf(task);
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private static DeviceAccess access(String name) {
		return DeviceAccess.of(name);
	}

	/**
	 * A reference to a global credential set, as a request holds it (only the ID counts).
	 * @param id the ID of the credential set
	 * @return the reference
	 */
	private static DeviceCredentialSet credentialRef(long id) {
		return DeviceSshAccount.builder().id(id).name("ref").build();
	}

	@Test
	@DisplayName("create() stores the device and submits an initial snapshot task")
	void createImmediate() {
		String address = this.nextAddress();
		DeviceAccess ssh = access("ssh").toBuilder().port(2222).build();
		DeviceCreation creation = new DeviceCreation(false, address, this.domainId, "CiscoIOS12",
			List.of(ssh), Map.of(), "Some comments", List.of(" core ", "lab"), AUTHOR);
		Task task = this.creationService.create(creation);
		TakeSnapshotTask snapshot = Assertions.assertInstanceOf(TakeSnapshotTask.class, task.spec());
		Assertions.assertEquals(List.of(task), this.submittedTasks);
		Assertions.assertEquals(AUTHOR, task.author());
		Assertions.assertTrue(snapshot.automatic());

		Device device = this.service.get(snapshot.deviceId());
		Assertions.assertEquals(address, device.mgmtAddress());
		Assertions.assertEquals("CiscoIOS12", device.driver());
		Assertions.assertEquals(AUTHOR, device.creator());
		Assertions.assertEquals("Some comments", device.comments());
		Assertions.assertEquals(java.util.Set.of("core", "lab"), device.tags());
		Assertions.assertEquals(this.domainId, device.mgmtDomain().id());
		Assertions.assertEquals(1, device.accesses().size());
		DeviceAccess stored = device.getDeviceAccess("ssh");
		Assertions.assertEquals(2222, stored.port());
		Assertions.assertEquals(DeviceAccess.SshHostKeyVerification.TRUST_ANY, stored.sshHostKeyVerification());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, stored.httpsCaTrustMode());
	}

	@Test
	@DisplayName("create() applies the strict SSH host key verification to an access which has none")
	void createDefaultHostKeyVerification() {
		DeviceAccess ssh = access("ssh").toBuilder().sshHostKeyVerification(null).httpsCaTrustMode(null).build();
		Device created = this.deviceOf(this.creationService.create(new DeviceCreation(false, this.nextAddress(), this.domainId,
			"CiscoIOS12", List.of(ssh), null, null, null, AUTHOR)));
		DeviceAccess stored = this.service.get(created.id()).getDeviceAccess("ssh");
		Assertions.assertEquals(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN, stored.sshHostKeyVerification());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, stored.httpsCaTrustMode());
	}

	@Test
	@DisplayName("create() stores a device specific credential set and pins a global one")
	void createWithCredentials() {
		DeviceAccess ssh = access("ssh").toBuilder().globalCredentialSet(credentialRef(this.sshAccountId)).build();
		DeviceAccess telnet = access("telnet").toBuilder().specificCredentialSet(DeviceTelnetAccount.builder().username("user").password("pass").superPassword("enable").name("ignored")
			.build())
			.build();
		DeviceCreation creation = new DeviceCreation(false, this.nextAddress(), this.domainId, "CiscoIOS12",
			List.of(ssh, telnet), null, null, null, AUTHOR);
		Device created = this.deviceOf(this.creationService.create(creation));

		Device device = this.service.get(created.id());
		Assertions.assertEquals(this.sshAccountId, device.getDeviceAccess("ssh").globalCredentialSet().id());
		DeviceCredentialSet specific = device.getDeviceAccess("telnet").specificCredentialSet();
		Assertions.assertTrue(specific.deviceSpecific());
		Assertions.assertTrue(specific.name().startsWith("DEVICESPECIFIC-"), "synthetic name");
	}

	@Test
	@DisplayName("create() with auto discovery submits a discovery task and creates no device")
	void createAutoDiscover() {
		TestDevices.addCredentialSet(DeviceSnmpv2cCommunity.builder().community("public").name("Lab SNMP")
			.mgmtDomain(Domain.reference(this.otherDomainId)).build());
		String address = this.nextAddress();
		Task task = this.creationService.create(new DeviceCreation(true, address, this.otherDomainId, "",
			null, null, "Discovered", List.of("auto"), AUTHOR));
		DiscoverDeviceTypeTask discovery = Assertions.assertInstanceOf(DiscoverDeviceTypeTask.class, task.spec());
		Assertions.assertEquals(List.of(task), this.submittedTasks);
		Assertions.assertEquals(address, discovery.deviceAddress());
		Assertions.assertEquals("Discovered", discovery.deviceComments());
		Assertions.assertEquals(List.of("auto"), discovery.deviceTags());
		Assertions.assertEquals(1, discovery.credentialSetIds().size());
		Assertions.assertEquals(0, this.countDevices(address), "the device is created by the task, if the discovery succeeds");
	}

	@Test
	@DisplayName("create() with auto discovery needs a SNMP community")
	void createAutoDiscoverWithoutCommunity() {
		long emptyDomainId = TestDevices.addDomain("No community", "").id();
		DeviceCreation creation = new DeviceCreation(true, this.nextAddress(), emptyDomainId, "", null, null, null, null, AUTHOR);
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS, () -> this.creationService.create(creation));
		Assertions.assertTrue(this.submittedTasks.isEmpty());
	}

	@Test
	@DisplayName("create() reports the invalid addresses")
	void createInvalidAddress() {
		assertError(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, () -> this.creationService.create(this.creation(null)));
		assertError(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, () -> this.creationService.create(this.creation("")));
		assertError(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, () -> this.creationService.create(this.creation("not a host name")));
		assertError(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, () -> this.creationService.create(this.creation("224.0.0.1")));
		assertError(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, () -> this.creationService.create(this.creation("127.0.0.1")));
		assertError(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, () -> this.creationService.create(this.creation("ff02::1")));
		assertError(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, () -> this.creationService.create(this.creation("2001:db8::zz")));
	}

	@Test
	@DisplayName("create() accepts host names and IPv6 addresses")
	void createHostNameAndIpv6() {
		Device byName = this.createDevice("localhost");
		Assertions.assertEquals("localhost", byName.mgmtAddress());
		Device byIpv6 = this.createDevice("2001:db8::" + Integer.toHexString(this.addressCounter.getAndIncrement()));
		Assertions.assertTrue(byIpv6.mgmtAddress().startsWith("2001:db8::"));
	}

	@Test
	@DisplayName("create() rejects invalid tags, unknown domain and unknown driver, in this order of checks")
	void createInvalidRequest() {
		String address = this.nextAddress();
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.creationService.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", null, null, null, List.of("ok", "  "), AUTHOR)));
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.creationService.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", null, null, null, List.of("x".repeat(256)), AUTHOR)));
		assertError(ErrorCode.NETSHOT_INVALID_DOMAIN, () -> this.creationService.create(
			new DeviceCreation(false, address, 99999999L, "CiscoIOS12", null, null, null, null, AUTHOR)));
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE_DRIVER, () -> this.creationService.create(
			new DeviceCreation(false, address, this.domainId, "NoSuchDriver", null, null, null, null, AUTHOR)));
		Assertions.assertEquals(0, this.countDevices(address));
		Assertions.assertTrue(this.submittedTasks.isEmpty());
	}

	@Test
	@DisplayName("create() validates the options and the accesses, and stores nothing then")
	void createInvalidOptionsAndAccesses() {
		String address = this.nextAddress();
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.creationService.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", null, Map.of("unknown", true), null, null, AUTHOR)));
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.creationService.create(
			new DeviceCreation(false, address, this.domainId, "SkyhighSWG", null, Map.of("fullBackup", "yes"), null, null, AUTHOR)));
		DeviceAccess noName = access("");
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.creationService.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(noName), null, null, null, AUTHOR)));
		DeviceAccess badPort = access("ssh").toBuilder().port(70000).build();
		assertError(ErrorCode.NETSHOT_INVALID_PORT, () -> this.creationService.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(badPort), null, null, null, AUTHOR)));
		DeviceAccess badAddress = access("ssh").toBuilder().address("not an address").build();
		assertError(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, () -> this.creationService.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(badAddress), null, null, null, AUTHOR)));
		DeviceAccess unknownAccess = access("carrier-pigeon").toBuilder().specificCredentialSet(DeviceTelnetAccount.builder().username("u").password("p").superPassword("e").name("n")
			.build()).build();
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.creationService.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(unknownAccess), null, null, null, AUTHOR)));
		DeviceAccess wrongKind = access("ssh").toBuilder().specificCredentialSet(DeviceSnmpv2cCommunity.builder().community("public").name("n").build()).build();
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE, () -> this.creationService.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(wrongKind), null, null, null, AUTHOR)));
		DeviceAccess both = access("ssh").toBuilder().specificCredentialSet(DeviceSshAccount.builder().username("u").password("p").superPassword("e").name("n").build())
			.globalCredentialSet(credentialRef(this.sshAccountId)).build();
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS, () -> this.creationService.create(
			new DeviceCreation(false, address, this.domainId, "CiscoIOS12", List.of(both), null, null, null, AUTHOR)));
		DeviceAccess unknownGlobal = access("ssh").toBuilder().globalCredentialSet(credentialRef(99999999L)).build();
		assertError(ErrorCode.NETSHOT_INVALID_CREDENTIALS, () -> this.creationService.create(
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
			() -> this.creationService.create(this.creation(address.toUpperCase())));
		Assertions.assertTrue(error.getMessage().contains("(" + first.id() + ")"), error.getMessage());
		this.service.update(first.id(), new DeviceUpdate(false, null, null, null, null, null, null));
		Device second = this.createDevice(address);
		Assertions.assertNotEquals(first.id(), second.id());
	}

	@Test
	@DisplayName("create() reports a task which can't be scheduled, once the device is stored")
	void createSchedulerFailure() {
		String address = this.nextAddress();
		this.schedulerFailure = new SchedulerException("no scheduler");
		assertError(ErrorCode.NETSHOT_SCHEDULE_ERROR, () -> this.creationService.create(this.creation(address)));
		Assertions.assertEquals(1, this.countDevices(address));
	}

	private long countDevices(String address) {
		return this.transactions.read(dsl -> (long) dsl.fetchCount(Tables.DEVICE, Tables.DEVICE.MGMT_ADDRESS.eq(address)));
	}
}
