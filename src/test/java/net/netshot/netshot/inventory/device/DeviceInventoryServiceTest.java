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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DeviceDriverService;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;

/**
 * Tests of the device sub-resources (interfaces, modules), the device search, the reference
 * lists and the device types, against a real database.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Device inventory service")
class DeviceInventoryServiceTest {

	private Transactions transactions;
	private DeviceService deviceService;
	private DeviceCatalogService catalogService;
	private SearchService searchService;
	private long router;
	private long other;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = TestWiring.get().transactions();
		DeviceRepository repository = new DeviceRepository();
		this.deviceService = new DeviceService(this.transactions, repository, TestDrivers.registry(), new DynamicGroups(TestWiring.get().transactions(), TestDrivers.registry()));
		this.catalogService = new DeviceCatalogService(this.transactions, new DeviceInventoryRepository());
		this.searchService = new SearchService(this.transactions, new DeviceRepository(), TestDrivers.registry());
		Domain domain = TestDevices.addDomain("Lab", "Test domain");
		Set<Network4Address> ip4 = new LinkedHashSet<>();
		Set<Network6Address> ip6 = new LinkedHashSet<>();
		try {
			NetworkInterface.addIpAddress(ip4, ip6, Network4Address.getNetworkAddress("10.0.0.1", 24));
			NetworkInterface.addIpAddress(ip4, ip6, Network6Address.getNetworkAddress("2001:db8::1", 64));
		}
		catch (java.net.UnknownHostException e) {
			throw new IllegalStateException(e);
		}
		Device routerDevice = TestDevices.add(device("router-a", "10.50.0.1", "Cisco 2900", "16.1", domain)
			.tags(Device.normalizeTags(List.of("core", "lab")))
			.virtualDevices(Set.of("vdc1"))
			.vrfInstances(Set.of("VRF1"))
			.modules(List.of(module("chassis", "PN-1", "SN-1"), module("slot 1", "PN-2", "SN-2"),
				module("slot 2", "PN-3", "SN-3").toBuilder().removed(true).build()))
			.networkInterfaces(List.of(
				NetworkInterface.builder().interfaceName("Gi0/0").virtualDevice("").vrfInstance("VRF1").enabled(true).level3(true)
					.description("Uplink").ip4Addresses(ip4).ip6Addresses(ip6).build(),
				NetworkInterface.builder().interfaceName("Lo0").virtualDevice("").vrfInstance("").enabled(true).level3(false)
					.description("").build()))
			.build());
		this.router = routerDevice.id();
		Device otherDevice = TestDevices.add(device("switch-b", "10.50.0.2", "Cisco 1900", "", domain)
			.tags(Device.normalizeTags(List.of("lab")))
			.modules(List.of(module("chassis", "PN-1", "SN-4")))
			.build());
		this.other = otherDevice.id();
		TestDevices.add(device("router-c", "10.50.0.3", "Cisco 2900", "16.1", domain).build());
	}

	private static Module module(String slot, String partNumber, String serialNumber) {
		return Module.builder().slot(slot).partNumber(partNumber).serialNumber(serialNumber).build();
	}

	private static Device.DeviceBuilder device(String name, String address, String family, String version, Domain domain) {
		return Device.create("CiscoIOS12", address, domain, "seed").toBuilder()
			.name(name)
			.family(family)
			.softwareVersion(version);
	}

	private static ListQuery page(Integer offset, Integer limit) {
		return new ListQuery(List.of(), null, List.of(), new Page(offset, limit), false);
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	// ------------------------------------------------------------------
	// interfaces and modules
	// ------------------------------------------------------------------

	@Test
	@DisplayName("listInterfaces() returns the interfaces of the device with their addresses, and reports an unknown device")
	void interfaces() {
		List<NetworkInterface> interfaces = this.catalogService.listInterfaces(this.router, ListQuery.ALL).items();
		Assertions.assertEquals(2, interfaces.size());
		NetworkInterface uplink = interfaces.stream().filter(i -> "Gi0/0".equals(i.interfaceName())).findFirst().orElseThrow();
		Assertions.assertEquals(1, uplink.ip4Addresses().size());
		Assertions.assertEquals(1, uplink.ip6Addresses().size());
		Assertions.assertEquals("10.0.0.1", uplink.ip4Addresses().iterator().next().getIp());
		Assertions.assertEquals(List.of(), this.catalogService.listInterfaces(this.other, ListQuery.ALL).items());
		assertError(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, () -> this.catalogService.listInterfaces(99999999L, ListQuery.ALL));
		Assertions.assertEquals(1, this.catalogService.listInterfaces(this.router, page(0, 1)).items().size());
	}

	@Test
	@DisplayName("listModules() returns the modules, the removed ones unless filtered out, and reports an unknown device")
	void modules() {
		List<Module> current = this.catalogService.listModules(this.router,
			new ListQuery(List.of(new Criterion("removed", Lookup.EXACT, List.of(false))), null, List.of(), Page.ALL, false)).items();
		Assertions.assertEquals(List.of("PN-1", "PN-2"), current.stream().map(Module::partNumber).sorted().toList());
		Assertions.assertEquals(3, this.catalogService.listModules(this.router, ListQuery.ALL).items().size());
		Assertions.assertEquals(1, this.catalogService.listModules(this.router, page(2, null)).items().size());
		assertError(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, () -> this.catalogService.listModules(99999999L, ListQuery.ALL));
	}

	// ------------------------------------------------------------------
	// search
	// ------------------------------------------------------------------

	@Test
	@DisplayName("listDevices() gives the details of the devices but their components, which a single device read has")
	void listWithoutComponents() {
		ListQuery query = ListQuery.byIds(List.of(this.router));
		Device listed = this.searchService.listDevices(query, null).items().get(0);
		Assertions.assertEquals(Set.of("core", "lab"), listed.tags());
		Assertions.assertEquals(List.of(), listed.modules());
		Assertions.assertEquals(List.of(), listed.networkInterfaces());
		Device read = this.deviceService.get(this.router);
		Assertions.assertEquals(2, read.modules().size());
		Assertions.assertEquals(2, read.networkInterfaces().size());
	}

	@Test
	@DisplayName("listDevices() loads only the collections asked for, the others are empty")
	void listParts() {
		ListQuery query = ListQuery.byIds(List.of(this.router));
		Device all = this.searchService.listDevices(query, null, Device.Part.ALL).items().get(0);
		Assertions.assertEquals(Set.of("core", "lab"), all.tags());
		Assertions.assertEquals(Set.of("vdc1"), all.virtualDevices());
		Assertions.assertEquals(Set.of("VRF1"), all.vrfInstances());
		Device none = this.searchService.listDevices(query, null, Set.of()).items().get(0);
		Assertions.assertEquals("router-a", none.name());
		Assertions.assertEquals("Lab", none.mgmtDomain().name());
		Assertions.assertEquals(Set.of(), none.tags());
		Assertions.assertEquals(Set.of(), none.virtualDevices());
		Assertions.assertEquals(Set.of(), none.vrfInstances());
		Device vrfs = this.searchService.listDevices(query, null, Set.of(Device.Part.VRF_INSTANCES)).items().get(0);
		Assertions.assertEquals(Set.of(), vrfs.tags());
		Assertions.assertEquals(Set.of(), vrfs.virtualDevices());
		Assertions.assertEquals(Set.of("VRF1"), vrfs.vrfInstances());
	}

	@Test
	@DisplayName("listDeviceSummaries() returns the devices matching a search query, which formatQuery() normalizes")
	void search() {
		String query = "[Name]   contains \"router\"";
		Assertions.assertEquals("[Name] contains \"router\"", this.searchService.formatQuery(query));
		Assertions.assertNull(this.searchService.formatQuery(null));
		List<DeviceSummary> devices = this.searchService.listDeviceSummaries(ListQuery.ALL, query).items();
		Assertions.assertEquals(2, devices.size());
		DeviceSummary summary = devices.stream().filter(d -> d.id() == this.router).findFirst().orElseThrow();
		Assertions.assertEquals("router-a", summary.name());
		Assertions.assertEquals("Cisco 2900", summary.family());
		Assertions.assertEquals("CiscoIOS12", summary.driver());
		Assertions.assertEquals("10.50.0.1", summary.mgmtAddress());
		Assertions.assertEquals(Boolean.TRUE, summary.configCompliant(), "no non-conforming result");
		Assertions.assertEquals(1, this.searchService.listDeviceSummaries(page(1, 1), "[Name] contains \"\"").items().size());
	}

	@Test
	@DisplayName("formatQuery() and listDeviceSummaries() report an invalid query")
	void searchInvalid() {
		NetshotException exception = assertError(ErrorCode.NETSHOT_INVALID_SEARCH_STRING, () -> this.searchService.formatQuery("[Nope] ???"));
		Assertions.assertTrue(exception.getMessage().startsWith("Invalid search string. "), exception.getMessage());
		Assertions.assertEquals("query", exception.getTarget());
		assertError(ErrorCode.NETSHOT_INVALID_SEARCH_STRING, () -> this.searchService.listDeviceSummaries(ListQuery.ALL, "[Nope] ???"));
	}

	// ------------------------------------------------------------------
	// reference lists
	// ------------------------------------------------------------------

	@Test
	@DisplayName("The reference lists have one item per value, with its count, in alphabetical order")
	void catalog() {
		Assertions.assertEquals(List.of(new DeviceFamilyCount("CiscoIOS12", "Cisco 1900", 1), new DeviceFamilyCount("CiscoIOS12", "Cisco 2900", 2)),
			this.catalogService.listFamilies(ListQuery.ALL).items());
		Assertions.assertEquals(2, this.catalogService.listPartNumbers(ListQuery.ALL).items().stream()
			.filter(p -> "PN-1".equals(p.partNumber())).findFirst().orElseThrow().moduleCount());
		Assertions.assertEquals(List.of(new SoftwareVersionCount("16.1", 2)), this.catalogService.listSoftwareVersions(ListQuery.ALL).items(),
			"the empty version is left out");
		Assertions.assertEquals(1, this.catalogService.listFamilies(page(0, 1)).items().size());
	}

	@Test
	@DisplayName("listTags() counts the devices of each tag, sorted by tag")
	void tags() {
		Assertions.assertEquals(List.of(new DeviceTagCount("core", 1), new DeviceTagCount("lab", 2)),
			this.catalogService.listTags(ListQuery.ALL).items());
	}

	// ------------------------------------------------------------------
	// device types
	// ------------------------------------------------------------------

	@Test
	@DisplayName("The device types are the loaded drivers")
	void deviceTypes() {
		DeviceDriverService service = new DeviceDriverService(TestDrivers.registry(), () -> { });
		List<DeviceDriver> drivers = service.list(ListQuery.ALL).items();
		Assertions.assertEquals(TestDrivers.registry().getAllDrivers().size(), drivers.size());
		Assertions.assertTrue(drivers.stream().anyMatch(d -> "CiscoIOS12".equals(d.getName())));
	}
}
