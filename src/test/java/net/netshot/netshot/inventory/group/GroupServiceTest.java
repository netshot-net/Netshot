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
package net.netshot.netshot.inventory.group;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;

/**
 * Tests of the device group use cases, against a real database.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
class GroupServiceTest {

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
		this.transactions = TestWiring.get().transactions();
		this.service = new GroupService(this.transactions, new GroupRepository(), new DynamicGroups(TestWiring.get().transactions(), TestDrivers.registry()));
		Domain domain = TestDevices.addDomain("Lab", "Test domain");
		this.routerA = this.createDevice(domain, "router-a", "10.30.0.1");
		this.routerB = this.createDevice(domain, "router-b", "10.30.0.2");
		this.switchC = this.createDevice(domain, "switch-c", "10.30.0.3");
	}

	private long createDevice(Domain domain, String name, String address) {
		return TestDevices.add(Device.create("CiscoIOS12", address, domain, "tester").toBuilder().name(name).build()).id();
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
		return TestDevices.memberIds(groupId);
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
		Assertions.assertEquals(name, group.name());
		Assertions.assertEquals("Production/Core", group.folder());
		Assertions.assertTrue(group.hiddenFromReports());
		Assertions.assertEquals(List.of(this.routerB, this.routerA), this.members(group.id()));
	}

	@Test
	@DisplayName("create() stores a dynamic group, normalizes its query and computes its members")
	void createDynamic() {
		DeviceGroup created = this.service.create(dynamicGroup(this.nextName(), "[Name] contains   \"router\""));
		DynamicDeviceGroup group = Assertions.assertInstanceOf(DynamicDeviceGroup.class, created);
		Assertions.assertEquals("[Name] contains \"router\"", group.query());
		Assertions.assertEquals(2, this.members(group.id()).size());
		Assertions.assertTrue(this.members(group.id()).containsAll(List.of(this.routerA, this.routerB)));
	}

	@Test
	@DisplayName("create() gives a dynamic group without query the default one")
	void createDynamicWithoutQuery() {
		DeviceGroup created = this.service.create(dynamicGroup(this.nextName(), null));
		DynamicDeviceGroup group = Assertions.assertInstanceOf(DynamicDeviceGroup.class, created);
		Assertions.assertEquals("[Name] is \"example\"", group.query());
		Assertions.assertEquals(List.of(), this.members(group.id()));
	}

	@Test
	@DisplayName("create() checks the name first, then the type")
	void createValidation() {
		assertError(ErrorCode.NETSHOT_INVALID_GROUP_NAME, () -> this.service.create(new GroupDefinition("Nope", "  ", List.of(), null, "", false)));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP_NAME, () -> this.service.create(new GroupDefinition("Nope", null, List.of(), null, "", false)));
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
		Assertions.assertInstanceOf(StaticDeviceGroup.class, this.service.get(fixed.id()));
		Assertions.assertInstanceOf(DynamicDeviceGroup.class, this.service.get(dynamic.id()));
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
		DeviceGroup updated = this.service.update(created.id(),
			new GroupDefinition("ignored", " " + name + " ", List.of(this.switchC, this.routerB), "ignored", "Lab", true));
		Assertions.assertEquals(name, updated.name());
		Assertions.assertEquals("Lab", updated.folder());
		Assertions.assertTrue(updated.hiddenFromReports());
		Assertions.assertEquals(List.of(this.switchC, this.routerB), this.members(created.id()));
	}

	@Test
	@DisplayName("update() changes the query of a dynamic group and refreshes its members")
	void updateDynamic() {
		DeviceGroup created = this.service.create(dynamicGroup(this.nextName(), "[Name] contains \"router\""));
		DeviceGroup updated = this.service.update(created.id(), dynamicGroup(created.name(), "[Name] contains  \"switch\""));
		Assertions.assertEquals("[Name] contains \"switch\"", ((DynamicDeviceGroup) updated).query());
		Assertions.assertEquals(List.of(this.switchC), this.members(created.id()));
	}

	@Test
	@DisplayName("update() ignores the type of the request: a group keeps its kind")
	void updateKeepsKind() {
		DeviceGroup created = this.service.create(staticGroup(this.nextName(), this.routerA));
		DeviceGroup updated = this.service.update(created.id(), dynamicGroup(created.name(), "[Name] contains \"switch\""));
		Assertions.assertInstanceOf(StaticDeviceGroup.class, updated);
		Assertions.assertEquals(List.of(), this.members(created.id()), "the devices of the request, none");
	}

	@Test
	@DisplayName("update() reports an unknown group, then checks the content, then the name")
	void updateValidation() {
		assertError(ErrorCode.NETSHOT_GROUP_NOT_FOUND, () -> this.service.update(99999999L, staticGroup("x")));
		DeviceGroup fixed = this.service.create(staticGroup(this.nextName()));
		// The devices are checked before the name
		assertError(ErrorCode.NETSHOT_INVALID_DEVICE, () -> this.service.update(fixed.id(), staticGroup(" ", 99999999L)));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP_NAME, () -> this.service.update(fixed.id(), staticGroup(" ")));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP_NAME, () -> this.service.update(fixed.id(), staticGroup(null)));
		DeviceGroup dynamic = this.service.create(dynamicGroup(this.nextName(), "[Name] contains \"router\""));
		// The query is checked before the name, and a failure leaves the group unchanged
		assertError(ErrorCode.NETSHOT_INVALID_DYNAMICGROUP_QUERY, () -> this.service.update(dynamic.id(), dynamicGroup(" ", "[Nope] ???")));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP_NAME, () -> this.service.update(dynamic.id(), dynamicGroup(" ", "[Name] contains \"switch\"")));
		Assertions.assertEquals("[Name] contains \"router\"", ((DynamicDeviceGroup) this.service.get(dynamic.id())).query());
		Assertions.assertEquals(2, this.members(dynamic.id()).size());
	}

	@Test
	@DisplayName("update() refuses a name which is used by another group")
	void updateDuplicate() {
		String name = this.nextName();
		this.service.create(staticGroup(name));
		DeviceGroup other = this.service.create(staticGroup(this.nextName()));
		assertError(ErrorCode.NETSHOT_DUPLICATE_GROUP, () -> this.service.update(other.id(), staticGroup(name)));
		Assertions.assertNotEquals(name, this.service.get(other.id()).name());
	}

	// ------------------------------------------------------------------
	// delete
	// ------------------------------------------------------------------

	@Test
	@DisplayName("delete() removes the group and its memberships, but not the devices; an unknown group is reported by the result")
	void delete() {
		DeviceGroup fixed = this.service.create(staticGroup(this.nextName(), this.routerA, this.routerB));
		DeviceGroup dynamic = this.service.create(dynamicGroup(this.nextName(), "[Name] contains \"router\""));
		Assertions.assertTrue(this.service.delete(fixed.id()));
		Assertions.assertTrue(this.service.delete(dynamic.id()));
		Assertions.assertFalse(this.service.delete(fixed.id()));
		Assertions.assertFalse(this.service.delete(99999999L));
		assertError(ErrorCode.NETSHOT_GROUP_NOT_FOUND, () -> this.service.get(fixed.id()));
		long memberships = TestDevices.memberIds(fixed.id()).size() + TestDevices.memberIds(dynamic.id()).size();
		Assertions.assertEquals(0L, memberships);
		Assertions.assertNotNull(TestDevices.find(this.routerA));
		Assertions.assertNotNull(TestDevices.find(this.routerB));
	}

	// ------------------------------------------------------------------
	// refresh of the groups of one device
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Refreshing the groups of one device keeps its static memberships and recomputes the dynamic ones (#413)")
	void refreshGroupsOfOneDeviceKeepsStaticMemberships() {
		DeviceGroup fixed = this.service.create(staticGroup(this.nextName(), this.routerA, this.routerB));
		DeviceGroup matching = this.service.create(dynamicGroup(this.nextName(), "[Name] is \"router-a\""));
		DeviceGroup notMatching = this.service.create(dynamicGroup(this.nextName(), "[Name] is \"router-b\""));
		// Stale membership, which the refresh must remove
		TestDevices.addMembers(notMatching, TestDevices.find(this.routerA));
		new DynamicGroups(TestWiring.get().transactions(), TestDrivers.registry()).refreshAllGroupsOfOneDevice(this.routerA);
		Assertions.assertEquals(List.of(this.routerA, this.routerB), this.members(fixed.id()));
		Assertions.assertEquals(List.of(this.routerA), this.members(matching.id()));
		Assertions.assertEquals(List.of(this.routerB), this.members(notMatching.id()));
	}
}
