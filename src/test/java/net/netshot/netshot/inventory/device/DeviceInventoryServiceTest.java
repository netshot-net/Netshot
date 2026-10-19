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

import java.util.List;

import org.hibernate.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DeviceDriverService;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.search.SearchRepository;
import net.netshot.netshot.inventory.search.SearchResult;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.testing.DatabaseTest;
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
		TaskSubmitter noTask = task -> { };
		this.deviceService = new DeviceService(this.transactions, repository, noTask, TestDrivers.registry());
		this.catalogService = new DeviceCatalogService(this.transactions, repository);
		this.searchService = new SearchService(this.transactions, new SearchRepository());
		this.transactions.run(session -> {
			Domain domain = new Domain("Lab", "Test domain", null, null);
			session.persist(domain);
			Device routerDevice = device(session, "router-a", "10.50.0.1", "Cisco 2900", "16.1", domain);
			routerDevice.replaceTags(List.of("core", "lab"));
			this.router = routerDevice.getId();
			Device otherDevice = device(session, "switch-b", "10.50.0.2", "Cisco 1900", "", domain);
			otherDevice.replaceTags(List.of("lab"));
			this.other = otherDevice.getId();
			device(session, "router-c", "10.50.0.3", "Cisco 2900", "16.1", domain);

			session.persist(new Module("chassis", "PN-1", "SN-1", routerDevice));
			session.persist(new Module("slot 1", "PN-2", "SN-2", routerDevice));
			Module removed = new Module("slot 2", "PN-3", "SN-3", routerDevice);
			removed.setRemoved(true);
			session.persist(removed);
			session.persist(new Module("chassis", "PN-1", "SN-4", otherDevice));

			try {
				NetworkInterface uplink = new NetworkInterface(routerDevice, "Gi0/0", "", "VRF1", true, true, "Uplink");
				uplink.addIpAddress((Network4Address) Network4Address.getNetworkAddress("10.0.0.1", 24));
				uplink.addIpAddress((Network6Address) Network6Address.getNetworkAddress("2001:db8::1", 64));
				session.persist(uplink);
				session.persist(new NetworkInterface(routerDevice, "Lo0", "", "", true, false, ""));
			}
			catch (java.net.UnknownHostException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	private static Device device(Session session, String name, String address, String family, String version, Domain domain) {
		Device device = new Device("CiscoIOS12", address, domain, "seed");
		device.setName(name);
		device.setFamily(family);
		device.setSoftwareVersion(version);
		session.persist(device);
		return device;
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
		NetworkInterface uplink = interfaces.stream().filter(i -> "Gi0/0".equals(i.getInterfaceName())).findFirst().orElseThrow();
		Assertions.assertEquals(1, uplink.getIp4Addresses().size());
		Assertions.assertEquals(1, uplink.getIp6Addresses().size());
		Assertions.assertEquals("10.0.0.1", uplink.getIp4Addresses().iterator().next().getIp());
		Assertions.assertEquals(List.of(), this.deviceService.getInterfaces(this.other, Page.ALL));
		Assertions.assertEquals(List.of(), this.deviceService.getInterfaces(99999999L, Page.ALL));
		Assertions.assertEquals(1, this.deviceService.getInterfaces(this.router, new Page(0, 1)).size());
	}

	@Test
	@DisplayName("getModules() returns the current modules, and the removed ones on demand")
	void modules() {
		List<Module> current = this.deviceService.getModules(this.router, false, Page.ALL);
		Assertions.assertEquals(List.of("PN-1", "PN-2"), current.stream().map(Module::getPartNumber).sorted().toList());
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
		DeviceDriverService service = new DeviceDriverService(TestDrivers.registry());
		List<DeviceDriver> drivers = service.list(false);
		Assertions.assertEquals(TestDrivers.registry().getAllDrivers().size(), drivers.size());
		Assertions.assertTrue(drivers.stream().anyMatch(d -> "CiscoIOS12".equals(d.getName())));
	}
}
