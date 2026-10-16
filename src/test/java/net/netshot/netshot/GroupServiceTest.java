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
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.GroupDefinition;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Tests of the device group use cases, against a real database.
 */
@TestInstance(Lifecycle.PER_CLASS)
class GroupServiceTest extends WithDatabaseTest {

	private static final String STATIC = "StaticDeviceGroup";
	private static final String DYNAMIC = "DynamicDeviceGroup";

	private Transactions transactions;
	private GroupService service;
	private long routerA;
	private long routerB;
	private long switchC;
	private int counter;

	@BeforeAll
	void initDb() throws Exception {
		Properties config = getFreshDatabaseConfig("groupservicetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		Netshot.initConfig(config);
		Netshot.loadModuleConfigs();
		DeviceDriver.refreshDrivers();
		Database.update();
		Wiring.init();
		this.transactions = new Transactions();
		this.service = new GroupService(this.transactions, new GroupRepository());
		Domain domain = new Domain("Lab", "Test domain", null, null);
		this.transactions.run(session -> session.persist(domain));
		this.routerA = this.createDevice(domain, "router-a", "10.30.0.1");
		this.routerB = this.createDevice(domain, "router-b", "10.30.0.2");
		this.switchC = this.createDevice(domain, "switch-c", "10.30.0.3");
	}

	private long createDevice(Domain domain, String name, String address) {
		return this.transactions.write(session -> {
			Device device = new Device("CiscoIOS12", address, domain, "tester");
			device.setName(name);
			session.persist(device);
			return device.getId();
		});
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private String nextName() {
		return "Group " + (this.counter++);
	}

	private static GroupDefinition staticGroup(String name, Long... devices) {
		return new GroupDefinition(STATIC, name, List.of(devices), null, "", false);
	}

	private static GroupDefinition dynamicGroup(String name, String query) {
		return new GroupDefinition(DYNAMIC, name, List.of(), query, "", false);
	}

	private List<Long> members(long groupId) {
		return this.transactions.read(session -> session.get(DeviceGroup.class, groupId)
			.getCachedDevices().stream().map(Device::getId).toList());
	}

	// ------------------------------------------------------------------
	// create
	// ------------------------------------------------------------------

	@Test
	@DisplayName("create() stores a static group with its devices in the given order")
	void createStatic() {
		String name = this.nextName();
		DeviceGroup created = this.service.create(new GroupDefinition(STATIC, " " + name + " ", List.of(this.routerB, this.routerA),
			"ignored", "Production/Core", true));
		StaticDeviceGroup group = Assertions.assertInstanceOf(StaticDeviceGroup.class, created);
		Assertions.assertEquals(name, group.getName());
		Assertions.assertEquals("Production/Core", group.getFolder());
		Assertions.assertTrue(group.isHiddenFromReports());
		Assertions.assertEquals(List.of(this.routerB, this.routerA), this.members(group.getId()));
	}

	@Test
	@DisplayName("create() stores a dynamic group, normalizes its query and computes its members")
	void createDynamic() {
		DeviceGroup created = this.service.create(dynamicGroup(this.nextName(), "[Name] contains   \"router\""));
		DynamicDeviceGroup group = Assertions.assertInstanceOf(DynamicDeviceGroup.class, created);
		Assertions.assertEquals("[Name] contains \"router\"", group.getQuery());
		Assertions.assertEquals(2, this.members(group.getId()).size());
		Assertions.assertTrue(this.members(group.getId()).containsAll(List.of(this.routerA, this.routerB)));
	}

	@Test
	@DisplayName("create() gives a dynamic group without query the default one")
	void createDynamicWithoutQuery() {
		DeviceGroup created = this.service.create(dynamicGroup(this.nextName(), null));
		DynamicDeviceGroup group = Assertions.assertInstanceOf(DynamicDeviceGroup.class, created);
		Assertions.assertEquals("[Name] is \"example\"", group.getQuery());
		Assertions.assertEquals(List.of(), this.members(group.getId()));
	}

	@Test
	@DisplayName("create() checks the name first, then the type")
	void createValidation() {
		assertError(ErrorCode.NETSHOT_INVALID_GROUP_NAME, () -> this.service.create(new GroupDefinition("Nope", "  ", List.of(), null, "", false)));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP, () -> this.service.create(new GroupDefinition("Nope", this.nextName(), List.of(), null, "", false)));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP, () -> this.service.create(new GroupDefinition(null, this.nextName(), List.of(), null, "", false)));
	}

	@Test
	@DisplayName("create() refuses an unknown device and an invalid query, and stores nothing")
	void createInvalidContent() {
		int before = this.service.list(Page.ALL).size();
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE, () -> this.service.create(staticGroup(this.nextName(), this.routerA, 99999999L)));
		assertError(ErrorCode.NETSHOT_INVALID_DYNAMICGROUP_QUERY, () -> this.service.create(dynamicGroup(this.nextName(), "[Nope] ???")));
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
	}

	@Test
	@DisplayName("create() refuses a name which is already used")
	void createDuplicate() {
		String name = this.nextName();
		this.service.create(staticGroup(name));
		assertError(ErrorCode.NETSHOT_DUPLICATE_GROUP, () -> this.service.create(dynamicGroup(name, null)));
	}

	// ------------------------------------------------------------------
	// read
	// ------------------------------------------------------------------

	@Test
	@DisplayName("get() returns the group, whatever its kind, and reports an unknown one")
	void get() {
		DeviceGroup fixed = this.service.create(staticGroup(this.nextName()));
		DeviceGroup dynamic = this.service.create(dynamicGroup(this.nextName(), "[Name] contains \"a\""));
		Assertions.assertInstanceOf(StaticDeviceGroup.class, this.service.get(fixed.getId()));
		Assertions.assertInstanceOf(DynamicDeviceGroup.class, this.service.get(dynamic.getId()));
		assertError(ErrorCode.NETSHOT_GROUP_NOT_FOUND, () -> this.service.get(99999999L));
	}

	@Test
	@DisplayName("list() returns the groups, and honours the page")
	void list() {
		this.service.create(staticGroup(this.nextName()));
		this.service.create(dynamicGroup(this.nextName(), null));
		List<DeviceGroup> all = this.service.list(Page.ALL);
		Assertions.assertTrue(all.size() >= 2);
		Assertions.assertTrue(all.stream().anyMatch(g -> g instanceof StaticDeviceGroup));
		Assertions.assertTrue(all.stream().anyMatch(g -> g instanceof DynamicDeviceGroup));
		Assertions.assertEquals(1, this.service.list(new Page(0, 1)).size());
	}

	// ------------------------------------------------------------------
	// update
	// ------------------------------------------------------------------

	@Test
	@DisplayName("update() replaces the devices of a static group, in the given order")
	void updateStatic() {
		DeviceGroup created = this.service.create(staticGroup(this.nextName(), this.routerA, this.routerB));
		String name = this.nextName();
		DeviceGroup updated = this.service.update(created.getId(),
			new GroupDefinition("ignored", " " + name + " ", List.of(this.switchC, this.routerB), "ignored", "Lab", true));
		Assertions.assertEquals(name, updated.getName());
		Assertions.assertEquals("Lab", updated.getFolder());
		Assertions.assertTrue(updated.isHiddenFromReports());
		Assertions.assertEquals(List.of(this.switchC, this.routerB), this.members(created.getId()));
	}

	@Test
	@DisplayName("update() changes the query of a dynamic group and refreshes its members")
	void updateDynamic() {
		DeviceGroup created = this.service.create(dynamicGroup(this.nextName(), "[Name] contains \"router\""));
		DeviceGroup updated = this.service.update(created.getId(), dynamicGroup(created.getName(), "[Name] contains  \"switch\""));
		Assertions.assertEquals("[Name] contains \"switch\"", ((DynamicDeviceGroup) updated).getQuery());
		Assertions.assertEquals(List.of(this.switchC), this.members(created.getId()));
	}

	@Test
	@DisplayName("update() ignores the type of the request: a group keeps its kind")
	void updateKeepsKind() {
		DeviceGroup created = this.service.create(staticGroup(this.nextName(), this.routerA));
		DeviceGroup updated = this.service.update(created.getId(), dynamicGroup(created.getName(), "[Name] contains \"switch\""));
		Assertions.assertInstanceOf(StaticDeviceGroup.class, updated);
		Assertions.assertEquals(List.of(), this.members(created.getId()), "the devices of the request, none");
	}

	@Test
	@DisplayName("update() reports an unknown group, then checks the content, then the name")
	void updateValidation() {
		assertError(ErrorCode.NETSHOT_GROUP_NOT_FOUND, () -> this.service.update(99999999L, staticGroup("x")));
		DeviceGroup fixed = this.service.create(staticGroup(this.nextName()));
		// The devices are checked before the name
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE, () -> this.service.update(fixed.getId(), staticGroup(" ", 99999999L)));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP_NAME, () -> this.service.update(fixed.getId(), staticGroup(" ")));
		DeviceGroup dynamic = this.service.create(dynamicGroup(this.nextName(), "[Name] contains \"router\""));
		// The query is checked before the name, and a failure leaves the group unchanged
		assertError(ErrorCode.NETSHOT_INVALID_DYNAMICGROUP_QUERY, () -> this.service.update(dynamic.getId(), dynamicGroup(" ", "[Nope] ???")));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP_NAME, () -> this.service.update(dynamic.getId(), dynamicGroup(" ", "[Name] contains \"switch\"")));
		Assertions.assertEquals("[Name] contains \"router\"", ((DynamicDeviceGroup) this.service.get(dynamic.getId())).getQuery());
		Assertions.assertEquals(2, this.members(dynamic.getId()).size());
	}

	@Test
	@DisplayName("update() refuses a name which is used by another group")
	void updateDuplicate() {
		String name = this.nextName();
		this.service.create(staticGroup(name));
		DeviceGroup other = this.service.create(staticGroup(this.nextName()));
		assertError(ErrorCode.NETSHOT_DUPLICATE_GROUP, () -> this.service.update(other.getId(), staticGroup(name)));
		Assertions.assertNotEquals(name, this.service.get(other.getId()).getName());
	}

	// ------------------------------------------------------------------
	// delete
	// ------------------------------------------------------------------

	@Test
	@DisplayName("delete() removes the group and its memberships, but not the devices; an unknown group is reported by the result")
	void delete() {
		DeviceGroup fixed = this.service.create(staticGroup(this.nextName(), this.routerA, this.routerB));
		DeviceGroup dynamic = this.service.create(dynamicGroup(this.nextName(), "[Name] contains \"router\""));
		Assertions.assertTrue(this.service.delete(fixed.getId()));
		Assertions.assertTrue(this.service.delete(dynamic.getId()));
		Assertions.assertFalse(this.service.delete(fixed.getId()));
		Assertions.assertFalse(this.service.delete(99999999L));
		assertError(ErrorCode.NETSHOT_GROUP_NOT_FOUND, () -> this.service.get(fixed.getId()));
		long memberships = this.transactions.read(session -> session
			.createQuery("select count(m) from DeviceGroupMembership m where m.key.group.id in (:ids)", Long.class)
			.setParameter("ids", List.of(fixed.getId(), dynamic.getId())).uniqueResult());
		Assertions.assertEquals(0L, memberships);
		Assertions.assertNotNull(this.transactions.read(session -> session.get(Device.class, this.routerA)));
		Assertions.assertNotNull(this.transactions.read(session -> session.get(Device.class, this.routerB)));
	}
}
