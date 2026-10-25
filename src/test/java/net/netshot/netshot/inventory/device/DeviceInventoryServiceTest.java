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
import net.netshot.netshot.inventory.search.SearchRepository;
import net.netshot.netshot.inventory.search.SearchResult;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;

/**
 * Tests of the device sub-resources (interfaces, modules), the device search, the reference
 * lists and the device types, against a real database.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
class DeviceInventoryServiceTest {

	private Transactions transactions;
	private DeviceService deviceService;
	private DeviceCatalogService catalogService;
	private SearchService searchService;
	private long router;
	private long other;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = new Transactions();
		DeviceRepository repository = new DeviceRepository();
		this.deviceService = new DeviceService(this.transactions, repository, TestDrivers.registry(), new DynamicGroups(TestDrivers.registry()));
		this.catalogService = new DeviceCatalogService(this.transactions, repository);
		this.searchService = new SearchService(this.transactions, new SearchRepository(), TestDrivers.registry());
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

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	// ------------------------------------------------------------------
	// interfaces and modules
	// ------------------------------------------------------------------

	@Test
	@DisplayName("getInterfaces() returns the interfaces of the device with their addresses, readable without a session")
	void interfaces() {
		List<NetworkInterface> interfaces = this.deviceService.getInterfaces(this.router, Page.ALL);
		Assertions.assertEquals(2, interfaces.size());
		NetworkInterface uplink = interfaces.stream().filter(i -> "Gi0/0".equals(i.interfaceName())).findFirst().orElseThrow();
		Assertions.assertEquals(1, uplink.ip4Addresses().size());
		Assertions.assertEquals(1, uplink.ip6Addresses().size());
		Assertions.assertEquals("10.0.0.1", uplink.ip4Addresses().iterator().next().getIp());
		Assertions.assertEquals(List.of(), this.deviceService.getInterfaces(this.other, Page.ALL));
		Assertions.assertEquals(List.of(), this.deviceService.getInterfaces(99999999L, Page.ALL));
		Assertions.assertEquals(1, this.deviceService.getInterfaces(this.router, new Page(0, 1)).size());
	}

	@Test
	@DisplayName("getModules() returns the current modules, and the removed ones on demand")
	void modules() {
		List<Module> current = this.deviceService.getModules(this.router, false, Page.ALL);
		Assertions.assertEquals(List.of("PN-1", "PN-2"), current.stream().map(Module::partNumber).sorted().toList());
		List<Module> history = this.deviceService.getModules(this.router, true, Page.ALL);
		Assertions.assertEquals(3, history.size());
		Assertions.assertEquals(1, this.deviceService.getModules(this.router, true, new Page(2, null)).size());
		Assertions.assertEquals(List.of(), this.deviceService.getModules(99999999L, true, Page.ALL));
	}

	// ------------------------------------------------------------------
	// search
	// ------------------------------------------------------------------

	@Test
	@DisplayName("searchDevices() returns the matching devices and the normalized query")
	void search() {
		SearchResult result = this.searchService.searchDevices("[Name]   contains \"router\"", Page.ALL);
		Assertions.assertEquals("[Name] contains \"router\"", result.query());
		Assertions.assertEquals(2, result.devices().size());
		DeviceSummary summary = result.devices().stream().filter(d -> d.id() == this.router).findFirst().orElseThrow();
		Assertions.assertEquals("router-a", summary.name());
		Assertions.assertEquals("Cisco 2900", summary.family());
		Assertions.assertEquals("CiscoIOS12", summary.driver());
		Assertions.assertEquals("10.50.0.1", summary.mgmtAddress());
		Assertions.assertEquals(Boolean.TRUE, summary.configCompliant(), "no non-conforming result");
		Assertions.assertEquals(1, this.searchService.searchDevices("[Name] contains \"\"", new Page(1, 1)).devices().size());
	}

	@Test
	@DisplayName("searchDevices() reports an invalid query")
	void searchInvalid() {
		NetshotException exception = assertError(ErrorCode.NETSHOT_INVALID_SEARCH_STRING, () -> this.searchService.searchDevices("[Nope] ???", Page.ALL));
		Assertions.assertTrue(exception.getMessage().startsWith("Invalid search string. "), exception.getMessage());
	}

	// ------------------------------------------------------------------
	// reference lists
	// ------------------------------------------------------------------

	@Test
	@DisplayName("The reference lists have an item per device or module (they are not distinct)")
	void catalog() {
		List<DeviceFamily> families = this.catalogService.getFamilies(Page.ALL);
		Assertions.assertEquals(3, families.size());
		Assertions.assertEquals(2, families.stream().filter(f -> "Cisco 2900".equals(f.family())).count());
		Assertions.assertTrue(families.stream().allMatch(f -> "CiscoIOS12".equals(f.driver())));

		List<String> partNumbers = this.catalogService.getPartNumbers(Page.ALL);
		Assertions.assertEquals(4, partNumbers.size());
		Assertions.assertEquals(2, partNumbers.stream().filter("PN-1"::equals).count());

		List<String> versions = this.catalogService.getSoftwareVersions(Page.ALL);
		Assertions.assertEquals(List.of("16.1", "16.1"), versions, "the empty version is left out");
		Assertions.assertEquals(1, this.catalogService.getSoftwareVersions(new Page(0, 1)).size());
	}

	@Test
	@DisplayName("getTags() counts the devices of each tag, sorted by tag")
	void tags() {
		Assertions.assertEquals(List.of(new DeviceTagCount("core", 1), new DeviceTagCount("lab", 2)),
			this.catalogService.getTags(Page.ALL));
	}

	// ------------------------------------------------------------------
	// device types
	// ------------------------------------------------------------------

	@Test
	@DisplayName("The device types are the loaded drivers")
	void deviceTypes() {
		DeviceDriverService service = new DeviceDriverService(TestDrivers.registry(), () -> { });
		List<DeviceDriver> drivers = service.list(false);
		Assertions.assertEquals(TestDrivers.registry().getAllDrivers().size(), drivers.size());
		Assertions.assertTrue(drivers.stream().anyMatch(d -> "CiscoIOS12".equals(d.getName())));
	}
}
