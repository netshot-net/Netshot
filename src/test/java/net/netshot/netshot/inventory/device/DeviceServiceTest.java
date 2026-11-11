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
package net.netshot.netshot.inventory.device;

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_ACCESS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
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
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.task.DeviceCreationService;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;

/**
 * Tests of the device use cases (the devices are added by the {@link DeviceCreationService}), against a
 * real database and a recording task submitter.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Device service")
class DeviceServiceTest {

	private static final String AUTHOR = "tester";

	private final List<Task> submittedTasks = new ArrayList<>();
	private final AtomicInteger addressCounter = new AtomicInteger(10);
	private SchedulerException schedulerFailure;
	private DeviceService service;
	private DeviceCreationService creationService;
	private Transactions transactions;
	private long domainId;
	private long otherDomainId;

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
		TestDevices.addCredentialSet(DeviceSshAccount.builder().username(Secret.local("admin")).password(Secret.local("secret")).superPassword(Secret.local("enable"))
			.name("Lab SSH account").build());
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

	// ------------------------------------------------------------------
	// update
	// ------------------------------------------------------------------

	@Test
	@DisplayName("A boolean option given as text (as XML gives it) is stored as a boolean, on creation and update")
	void booleanOptionAsText() {
		Device created = this.deviceOf(this.creationService.create(new DeviceCreation(false, this.nextAddress(), this.domainId,
			"SkyhighSWG", null, Map.of("fullBackup", "true"), null, null, AUTHOR)));
		Assertions.assertEquals(Map.of("fullBackup", true), this.service.get(created.id()).options());
		Device updated = this.service.update(created.id(), new DeviceUpdate(null, null, null, null, null, null, Map.of("fullBackup", "false"), null));
		Assertions.assertEquals(Map.of("fullBackup", false), updated.options());
	}

	@Test
	@DisplayName("update() sets, replaces and clears the trusted SSH host keys of an access, and refuses an invalid one")
	void updateTrustedHostKeys() throws Exception {
		Device created = this.createDevice(this.nextAddress());
		String key = PublicKeyEntry.toString(KeyUtils.generateKeyPair(KeyPairProvider.ECDSA_SHA2_NISTP256, 256).getPublic());
		String otherKey = PublicKeyEntry.toString(KeyUtils.generateKeyPair(KeyPairProvider.SSH_ED25519, 256).getPublic());
		DeviceAccess known = access("ssh").toBuilder().sshHostKeyVerification(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN).build();

		DeviceAccess set = this.updateAccess(created, known.toBuilder().sshTrustedHostKeys(key + " comment").build());
		Assertions.assertEquals(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN, set.sshHostKeyVerification());
		Assertions.assertEquals(key, set.sshTrustedHostKeys());
		Assertions.assertNotNull(set.sshTrustedHostKeysSince());

		DeviceAccess replaced = this.updateAccess(created, known.toBuilder().sshTrustedHostKeys(key + "\n" + otherKey).build());
		Assertions.assertEquals(key + "\n" + otherKey, replaced.sshTrustedHostKeys());
		Assertions.assertNotNull(replaced.sshTrustedHostKeysSince());

		DeviceAccess cleared = this.updateAccess(created, known.toBuilder().sshTrustedHostKeys("").build());
		Assertions.assertEquals(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN, cleared.sshHostKeyVerification());
		Assertions.assertNull(cleared.sshTrustedHostKeys());
		Assertions.assertNull(cleared.sshTrustedHostKeysSince());

		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
			() -> this.updateAccess(created, known.toBuilder().sshTrustedHostKeys("ssh-rsa AAAA").build()));
		Assertions.assertNull(this.service.get(created.id()).getDeviceAccess("ssh").sshTrustedHostKeys(), "Changed by a refused update");
	}

	/**
	 * Replaces the accesses of a device by one access.
	 *
	 * @param device the device
	 * @param access the access
	 * @return the access as stored
	 */
	private DeviceAccess updateAccess(Device device, DeviceAccess access) {
		this.service.update(device.id(), new DeviceUpdate(null, null, null, null, List.of(access), null, null, null));
		return this.service.get(device.id()).getDeviceAccess(access.accessName());
	}

	@Test
	@DisplayName("update() accepts comments of up to 4096 characters, and refuses longer ones")
	void updateCommentsLength() {
		Device created = this.createDevice(this.nextAddress());
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.update(created.id(),
			new DeviceUpdate(null, "x".repeat(Device.MAX_COMMENTS_LENGTH + 1), null, null, null, null, null, null)));
		Device updated = this.service.update(created.id(),
			new DeviceUpdate(null, "y".repeat(Device.MAX_COMMENTS_LENGTH), null, null, null, null, null, null));
		Assertions.assertEquals(Device.MAX_COMMENTS_LENGTH, updated.comments().length());
	}

	@Test
	@DisplayName("update() leaves unchanged what the command leaves null")
	void updateNullsAreUnchanged() {
		DeviceAccess ssh = access("ssh").toBuilder().port(2200).build();
		Device created = this.deviceOf(this.creationService.create(new DeviceCreation(false, this.nextAddress(), this.domainId,
			"SkyhighSWG", List.of(ssh), Map.of("fullBackup", true), "Initial comments", List.of("a", "b"), AUTHOR)));

		Device updated = this.service.update(created.id(), new DeviceUpdate(null, null, null, null, null, null, null, null));
		Assertions.assertEquals(created.mgmtAddress(), updated.mgmtAddress());
		Assertions.assertEquals(Device.Status.INPRODUCTION, updated.status());
		Assertions.assertEquals("Initial comments", updated.comments());
		Assertions.assertEquals(java.util.Set.of("a", "b"), updated.tags());
		Assertions.assertEquals(Map.of("fullBackup", true), updated.options());
		Assertions.assertEquals(this.domainId, updated.mgmtDomain().id());
		Assertions.assertEquals(2200, updated.getDeviceAccess("ssh").port());
	}

	@Test
	@DisplayName("update() changes what the command provides")
	void updateChanges() {
		Device created = this.createDevice(this.nextAddress());
		String newAddress = this.nextAddress();
		DeviceAccess telnet = access("telnet").toBuilder().address("10.99.0.1").build();
		Device updated = this.service.update(created.id(), new DeviceUpdate(false, "New comments", newAddress,
			this.otherDomainId, List.of(telnet), null, Map.of(), List.of("x")));
		Assertions.assertEquals(Device.Status.DISABLED, updated.status());
		Assertions.assertEquals("New comments", updated.comments());
		Assertions.assertEquals(newAddress, updated.mgmtAddress());
		Assertions.assertEquals(newAddress, updated.cachedIpAddress().getHostAddress());
		Assertions.assertEquals(this.otherDomainId, updated.mgmtDomain().id());
		Assertions.assertEquals(java.util.Set.of("x"), updated.tags());
		Assertions.assertEquals(1, updated.accesses().size(), "the accesses are replaced as a whole");
		Assertions.assertEquals("10.99.0.1", updated.getDeviceAccess("telnet").address());

		Device reenabled = this.service.update(created.id(), new DeviceUpdate(true, null, null, null, null, null, null, List.of()));
		Assertions.assertEquals(Device.Status.INPRODUCTION, reenabled.status());
		Assertions.assertTrue(reenabled.tags().isEmpty(), "an empty list of tags clears the tags");
	}

	@Test
	@DisplayName("update() merges a legacy specific credential set into the current one, and refuses one carried by an access")
	void updateLegacySpecificCredentialSet() {
		AccessSpecificCredential initial = new AccessSpecificCredential.Legacy("ssh", DeviceSshAccount.builder()
			.username(Secret.local("u")).password(Secret.local("p")).superPassword(Secret.local("e")).name("n").build());
		Device created = this.deviceOf(this.creationService.create(new DeviceCreation(false, this.nextAddress(), this.domainId,
			"CiscoIOS12", List.of(access("ssh")), null, null, null, AUTHOR, List.of(initial))));
		long specificId = created.getDeviceAccess("ssh").specificCredentialSet().id();

		// The legacy rules: a secret left out is unchanged (the v0 mapping turns "=" into one), the username is replaced
		AccessSpecificCredential change = new AccessSpecificCredential.Legacy("ssh", DeviceSshAccount.builder()
			.username(Secret.local("u2")).superPassword(Secret.local("e2")).name("ignored").build());
		this.service.update(created.id(), new DeviceUpdate(null, null, null, null, List.of(access("ssh")), List.of(change), null, null));
		DeviceSshAccount stored = (DeviceSshAccount) this.service.get(created.id()).getDeviceAccess("ssh").specificCredentialSet();
		Assertions.assertEquals(specificId, stored.id(), "merged into the current credential set");
		Assertions.assertEquals(Secret.local("u2"), stored.username());
		Assertions.assertEquals(Secret.local("p"), stored.password());
		Assertions.assertEquals(Secret.local("e2"), stored.superPassword());

		DeviceAccess inline = access("ssh").toBuilder().specificCredentialSet(stored).build();
		Assertions.assertThrows(IllegalArgumentException.class, () -> this.service.update(created.id(),
			new DeviceUpdate(null, null, null, null, List.of(inline), null, null, null)));

		// An access given without specific credential set loses its current one
		this.service.update(created.id(), new DeviceUpdate(null, null, null, null, List.of(access("ssh")), null, null, null));
		Assertions.assertNull(this.service.get(created.id()).getDeviceAccess("ssh").specificCredentialSet());
	}

	@Test
	@DisplayName("update() reports the invalid changes and applies none of them")
	void updateInvalid() {
		Device created = this.createDevice(this.nextAddress());
		long id = created.id();
		assertError(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, () -> this.service.update(99999999L, new DeviceUpdate(null, "x", null, null, null, null, null, null)));
		assertError(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, () -> this.service.update(id, new DeviceUpdate(null, "changed", "bad address", null, null, null, null, null)));
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.update(id, new DeviceUpdate(null, "changed", null, null, null, null, Map.of("nope", 1), null)));
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.update(id, new DeviceUpdate(null, "changed", null, null, null, null, null, List.of(" "))));
		assertError(ErrorCode.NETSHOT_INVALID_DOMAIN, () -> this.service.update(id, new DeviceUpdate(null, "changed", null, 99999999L, null, null, null, null)));
		DeviceAccess badPort = access("ssh").toBuilder().port(0).build();
		assertError(ErrorCode.NETSHOT_INVALID_PORT, () -> this.service.update(id, new DeviceUpdate(null, "changed", null, null, List.of(badPort), null, null, null)));
		Assertions.assertEquals("", this.service.get(id).comments(), "failed updates are rolled back");
	}

	@Test
	@DisplayName("update() refuses an address used by another active device")
	void updateDuplicate() {
		Device first = this.createDevice(this.nextAddress());
		Device second = this.createDevice(this.nextAddress());
		assertError(ErrorCode.NETSHOT_DUPLICATE_DEVICE, () -> this.service.update(second.id(),
			new DeviceUpdate(null, null, first.mgmtAddress(), null, null, null, null, null)));
		// Same address as itself is fine
		this.service.update(second.id(), new DeviceUpdate(null, null, second.mgmtAddress(), null, null, null, null, null));
		// A disabled device doesn't conflict, and is not checked
		this.service.update(first.id(), new DeviceUpdate(false, null, null, null, null, null, null, null));
		this.service.update(second.id(), new DeviceUpdate(null, null, first.mgmtAddress(), null, null, null, null, null));
	}

	// ------------------------------------------------------------------
	// get, list
	// ------------------------------------------------------------------

	@Test
	@DisplayName("The repository stores the snapshot dates of a new device (copy or import)")
	void addKeepsSnapshotDates() {
		Instant success = Instant.parse("2026-03-10T10:00:00Z");
		Instant failure = Instant.parse("2026-04-10T10:00:00Z");
		Device copy = this.createDevice(this.nextAddress()).toBuilder().mgmtAddress(this.nextAddress())
			.lastSnapshotSuccessDate(success).lastSnapshotFailureDate(failure).build();
		Device added = this.service.get(TestDevices.add(copy).id());
		Assertions.assertEquals(success, added.lastSnapshotSuccessDate());
		Assertions.assertEquals(failure, added.lastSnapshotFailureDate());
	}

	@Test
	@DisplayName("get() reports an unknown device, and returns the complete device")
	void get() throws Exception {
		assertError(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, () -> this.service.get(99999999L));
		AccessSpecificCredential specific = new AccessSpecificCredential.Legacy("ssh", DeviceSshAccount.builder()
			.username(Secret.local("u")).password(Secret.local("p")).superPassword(Secret.local("e")).name("n").build());
		Device created = this.deviceOf(this.creationService.create(new DeviceCreation(false, this.nextAddress(), this.domainId,
			"CiscoIOS12", List.of(access("ssh")), null, null, List.of("t"), AUTHOR, List.of(specific))));
		Device device = this.service.get(created.id());
		// Everything the full representation reads
		Assertions.assertEquals("Lab", device.mgmtDomain().name());
		Assertions.assertNull(device.eolModule());
		Assertions.assertTrue(device.attributes().isEmpty());
		Assertions.assertTrue(device.ownerGroups().isEmpty());
		Assertions.assertTrue(device.compliant());
		Assertions.assertEquals(Secret.local("u"), ((DeviceSshAccount) device.getDeviceAccess("ssh").specificCredentialSet()).username());
		Assertions.assertEquals(java.util.Set.of("t"), device.tags());
		Assertions.assertEquals("Cisco IOS and IOS-XE", TestDrivers.registry().forDevice(device).getDescription());
	}

	@Test
	@DisplayName("list() returns the summaries, in pages")
	void list() {
		Device created = this.createDevice(this.nextAddress());
		List<DeviceSummary> all = this.service.list(null, Page.ALL);
		DeviceSummary summary = all.stream().filter(s -> s.id() == created.id()).findFirst().orElseThrow();
		Assertions.assertEquals(created.mgmtAddress(), summary.mgmtAddress());
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
		Assertions.assertTrue(this.service.delete(created.id()));
		assertError(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, () -> this.service.get(created.id()));
		Assertions.assertFalse(this.service.delete(created.id()), "already deleted");
		Assertions.assertFalse(this.service.delete(99999999L));
	}

	@Test
	@DisplayName("delete() removes the accesses of the device and their specific credential sets, not the global ones they pin")
	void deleteRemovesAccesses() {
		Domain domain = TestDevices.addDomain("Deletion lab", "Domain of the deleted device");
		DeviceCredentialSet global = TestDevices.addCredentialSet(DeviceSshAccount.builder().username(Secret.local("admin"))
			.password(Secret.local("admin")).name("Global SSH account").build());
		DeviceCredentialSet specific = DeviceSshAccount.builder().username(Secret.local("admin")).password(Secret.local("admin"))
			.name(DeviceCredentialSet.generateSpecificName()).deviceSpecific(true).build();
		Device device = TestDevices.add(Device.create("CiscoIOS12", this.nextAddress(), domain, AUTHOR).toBuilder().name("deleted")
			.accesses(List.of(
				DeviceAccess.of("ssh").toBuilder().globalCredentialSet(global).build(),
				DeviceAccess.of("telnet").toBuilder().specificCredentialSet(specific).build()))
			.build());
		long specificId = device.getDeviceAccess("telnet").specificCredentialSet().id();

		Assertions.assertTrue(this.service.delete(device.id()));
		int accesses = this.transactions.read(dsl -> dsl.fetchCount(DEVICE_ACCESS, DEVICE_ACCESS.DEVICE.eq(device.id())));
		Assertions.assertEquals(0, accesses, "The accesses of the device are kept");
		Assertions.assertNull(TestDevices.findCredentialSet(specificId), "The specific credential set of an access is kept");
		Assertions.assertNotNull(TestDevices.findCredentialSet(global.id()), "A global credential set pinned by an access is deleted");
	}

	@Test
	@DisplayName("delete() removes the binary files of the configurations of the device")
	void deleteRemovesBinaryFiles() throws Exception {
		Device created = this.createDevice(this.nextAddress());
		ConfigBinaryFileAttribute attribute = ConfigBinaryFileAttribute.create("binary", "original.bin");
		long configId = TestDevices.addOldConfig(created.id(), Config.builder().author("").attributes(List.of(attribute)).build());
		Path file = attribute.getFilePath(created.id(), configId);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "binary content");
		Assertions.assertTrue(this.service.delete(created.id()));
		Assertions.assertFalse(Files.exists(file), "the binary file is removed with the device");
	}
}
