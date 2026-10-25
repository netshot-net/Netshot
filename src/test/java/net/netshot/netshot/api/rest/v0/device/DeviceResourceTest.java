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
package net.netshot.netshot.api.rest.v0.device;

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_ACCESS;

import java.io.IOException;
import java.net.InetAddress;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DeviceDriver.DriverProtocol;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.MissingDeviceDriverException;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.NetshotApiClient;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

@ApiTest
@DisplayName("Device tests")
@ResourceLock("DB")
public class DeviceResourceTest {

	/** API client of the current test, authenticated with the admin token. */
	private NetshotApiClient apiClient;

	@BeforeEach
	void createApiClient(NetshotApiClient client) {
		this.apiClient = client;
	}

	private String testDomainName = "Domain 1";
	private Domain testDomain;

	private enum DeviceField {
		CONTACT(Device::contact),
		CREATED_DATE(Device::createdDate),
		CREATOR(Device::creator),
		DRIVER(Device::driver),
		EOL_DATE(Device::eolDate),
		EOS_DATE(Device::eosDate),
		FAMILY(Device::family),
		LOCATION(Device::location),
		MGMT_ADDRESS(Device::mgmtAddress),
		NAME(Device::name),
		NETWORK_CLASS(Device::networkClass),
		SERIAL_NUMBER(Device::serialNumber),
		SOFTWARE_LEVEL(Device::softwareLevel),
		SOFTWARE_VERSION(Device::softwareVersion),
		STATUS(Device::status),
		COMMENTS(Device::comments);

		private Function<Device, ?> getter;

		DeviceField(Function<Device, ?> getter) {
			this.getter = getter;
		}
	}

	private void createTestDomain() throws IOException {
		this.testDomain = TestDevices.addDomain(Domain.builder()
			.name(this.testDomainName).description("Test Domain for devices")
			.server4Address(new Network4Address("10.1.1.1")).build());
	}

	private DeviceDriver getTestDriver() {
		return TestDrivers.registry().getDriverByName("CiscoIOS12");
	}

	private void assertDevicesEqual(Device d1, Device d2, DeviceField... ignoredFields) {
		List<DeviceField> iFields = Arrays.asList(ignoredFields);
		for (DeviceField field : DeviceField.values()) {
			if (!iFields.contains(field)) {
				Object v1 = field.getter.apply(d1);
				Object v2 = field.getter.apply(d2);
				if ((v1 == null && v2 != null) || (v1 != null && !v1.equals(v2))) {
					Assertions.fail("Passed devices are not equal, check field %s".formatted(field.name()));
				}
			}
		}
	}

	/**
	 * Finds the only stored device, if any.
	 *
	 * @return the device
	 */
	private static Device findOnlyDevice() {
		long id = new Transactions().read(dsl -> dsl.select(DEVICE.ID).from(DEVICE).fetchSingle(DEVICE.ID));
		return TestDevices.find(id);
	}

	/**
	 * Finds the only stored device having a given management address.
	 *
	 * @param address the management address
	 * @return the device
	 */
	private static Device findDeviceByAddress(String address) {
		long id = new Transactions().read(dsl -> dsl.select(DEVICE.ID).from(DEVICE)
			.where(DEVICE.MGMT_ADDRESS.eq(address)).fetchSingle(DEVICE.ID));
		return TestDevices.find(id);
	}

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@AfterEach
	void cleanUpData() {
		TestDevices.deleteAll();
	}

	@Test
	@DisplayName("List device types")
	void listDeviceTypes() throws Exception {
		List<DeviceDriver> drivers = TestDrivers.registry().getAllDrivers();
		HttpResponse<JsonNode> response = apiClient.get("/devicetypes");
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), response.statusCode(),
			"Not getting 200 response for device type list");
		Assertions.assertEquals(
			drivers.size(), response.body().size(),
			"Not getting the right number of device drivers");
		DeviceDriver testDriver = this.getTestDriver();
		JsonNode testDriverNode = null;
		Iterator<JsonNode> driverIt = response.body().iterator();
		while (driverIt.hasNext()) {
			JsonNode driverNode = driverIt.next();
			if (driverNode.get("name").asText().equals(testDriver.getName())) {
				testDriverNode = driverNode;
			}
		}
		Assertions.assertNotNull(testDriverNode, "API didn't return test driver");

		ObjectNode targetData = JsonNodeFactory.instance.objectNode()
			.put("name", testDriver.getName())
			.put("author", testDriver.getAuthor())
			.put("description", testDriver.getDescription())
			.put("version", testDriver.getVersion())
			.put("priority", Long.valueOf(testDriver.getPriority()));
		for (AttributeDefinition attribute : testDriver.getAttributes()) {
			targetData.withArray("attributes").add(
				JsonNodeFactory.instance.objectNode()
					.put("type", attribute.getType().toString())
					.put("level", attribute.getLevel().toString())
					.put("name", attribute.getName())
					.put("title", attribute.getTitle())
					.put("comparable", attribute.isComparable())
					.put("searchable", attribute.isSearchable())
					.put("checkable", attribute.isCheckable())
			);
		}
		for (DriverProtocol protocol : testDriver.getProtocols()) {
			targetData.withArray("protocols")
				.add(protocol.toString());
		}
		for (String mode : testDriver.getCliMainModes()) {
			targetData.withArray("cliMainModes").add(mode);
		}
		targetData
			.put("sourceHash", testDriver.getSourceHash())
			.set("location",
				JsonNodeFactory.instance.objectNode()
					.put("type", testDriver.getLocation().getType().toString())
					.put("fileName", testDriver.getLocation().getFileName()));
		ObjectNode accessDefinitionsNode = targetData.putObject("accessDefinitions");
		for (var entry : testDriver.getAccessDefinitions().entrySet()) {
			DeviceDriver.AccessDefinition accessDef = entry.getValue();
			ObjectNode accessDefNode = accessDefinitionsNode.putObject(entry.getKey())
				.put("name", accessDef.getName())
				.put("protocol", accessDef.getProtocol().toString());
			if (accessDef.getDescription() == null) {
				accessDefNode.putNull("description");
			}
			else {
				accessDefNode.put("description", accessDef.getDescription());
			}
			accessDefNode.put("defaultPort", Long.valueOf(accessDef.getDefaultPort()));
			accessDefNode.put("group", accessDef.getGroup());
			accessDefNode.put("priority", Long.valueOf(accessDef.getPriority()));
		}
		targetData.putObject("options");
		Assertions.assertEquals(targetData, testDriverNode,
			"Retrieved device type doesn't match expected object");
	}

	@Test
	@DisplayName("List devices")
	@ResourceLock("DB")
	void listDevices() throws IOException, InterruptedException {
		{
			HttpResponse<JsonNode> response = apiClient.get("/devices");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for initial list");
			Assertions.assertEquals(0, response.body().size(),
				"Device list is not empty");
		}
		this.createTestDomain();
		Device device1 = Device.create(this.getTestDriver().getName(),
			"10.1.1.1", this.testDomain, "test").toBuilder().name("device1").build();
		Device device2 = Device.create(this.getTestDriver().getName(),
			"10.1.1.2", this.testDomain, "test").toBuilder().name("device2").build();
		device1 = TestDevices.add(device1);
		device2 = TestDevices.add(device2);
		{
			HttpResponse<JsonNode> response = apiClient.get("/devices");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for device list");
			Assertions.assertEquals(2, response.body().size(),
				"Device list doesn't have 2 elements");
			Iterator<JsonNode> deviceNodeIt = response.body().iterator();
			JsonNode deviceNode1 = deviceNodeIt.next();
			Assertions.assertEquals(
				JsonNodeFactory.instance.objectNode()
					.put("id", device1.id())
					.put("name", device1.name())
					.put("family", device1.family())
					.put("mgmtAddress", device1.mgmtAddress())
					.put("status", device1.status().toString())
					.put("driver", device1.driver())
					.put("eol", device1.isEndOfLife())
					.put("eos", device1.isEndOfSale())
					.put("configCompliant", device1.compliant())
					.put("softwareLevel", device1.softwareLevel().toString())
					.put("networkClass", device1.networkClass().toString()),
				deviceNode1,
				"Retrieved device doesn't match expected object");
		}
	}

	@Test
	@DisplayName("Get device")
	@ResourceLock("DB")
	void getDevice() throws IOException, InterruptedException, MissingDeviceDriverException {
		this.createTestDomain();
		Device device1 = Device.create(this.getTestDriver().getName(),
			"10.1.1.1", this.testDomain, "test").toBuilder().name("device1").build();
		device1 = TestDevices.add(device1);
		{
			HttpResponse<JsonNode> response = apiClient.get("/devices/%d".formatted(TestServer.UNKNOWN_ID));
			Assertions.assertEquals(
				Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
				"Not getting 404 response for nonexistent device");
		}
		{
			HttpResponse<JsonNode> response = apiClient.get("/devices/%d".formatted(device1.id()));
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for device");
			JsonNode deviceNode1 = response.body();
			ObjectNode expectedNode1 = JsonNodeFactory.instance.objectNode()
				.put("id", device1.id())
				.put("name", device1.name())
				.put("networkClass", device1.networkClass().toString())
				.put("family", device1.family())
				.put("mgmtAddress", device1.mgmtAddress())
				.put("cachedIpAddress", device1.mgmtAddress())
				.put("status", device1.status().toString())
				.put("driver", device1.driver())
				.put("realDeviceType", TestDrivers.registry().forDevice(device1).getDescription())
				.put("serialNumber", device1.serialNumber())
				.put("softwareVersion", device1.softwareVersion())
				.put("softwareLevel", device1.softwareLevel().toString())
				.put("contact", device1.contact())
				.put("location", device1.location())
				.put("creator", device1.creator())
				.put("changeDate", device1.changeDate().toEpochMilli())
				.put("createdDate", device1.createdDate().toEpochMilli())
				.putNull("eolDate")
				.putNull("eolModule")
				.putNull("eosDate")
				.putNull("eosModule")
				.put("endOfLife", device1.isEndOfLife())
				.put("endOfSale", device1.isEndOfSale())
				.put("compliant", device1.compliant())
				.put("comments", device1.comments());
			expectedNode1.putArray("ownerGroups");
			expectedNode1.putArray("attributes");
			expectedNode1.putArray("accesses");
			expectedNode1.putArray("tags");
			expectedNode1.putObject("options");
			expectedNode1.set("mgmtDomain",
				JsonNodeFactory.instance.objectNode()
					.put("id", device1.mgmtDomain().id())
					.put("name", device1.mgmtDomain().name())
					.put("changeDate", device1.mgmtDomain().changeDate().toEpochMilli())
					.put("description", device1.mgmtDomain().description())
					.put("server4Address", device1.mgmtDomain().server4Address().getIp())
					.putNull("server6Address"));
			Assertions.assertEquals(expectedNode1, deviceNode1,
				"Retrieved device doesn't match expected object");
		}
	}

	@Test
	@DisplayName("Create device")
	@ResourceLock("DB")
	void createDevice() throws IOException, InterruptedException {
		this.createTestDomain();
		Device device1 = Device.create(this.getTestDriver().getName(),
			"10.1.1.1", this.testDomain, "test");
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", device1.mgmtAddress())
			.put("domainId", this.testDomain.id())
			.put("name", device1.name())
			.put("deviceType", device1.driver());
		{
			HttpResponse<JsonNode> response = apiClient.post("/devices",
				data.deepCopy().put("domainId", TestServer.UNKNOWN_ID));
			Assertions.assertEquals(
				Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
				"Not getting 400 response for invalid domain");
		}
		{
			HttpResponse<JsonNode> response = apiClient.post("/devices",
				data.deepCopy().put("deviceType", "NonExistingDriver"));
			Assertions.assertEquals(
				Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
				"Not getting 400 response for invalid domain");
		}
		{
			HttpResponse<JsonNode> response = apiClient.post("/devices", data);
			Assertions.assertEquals(
				Response.Status.CREATED.getStatusCode(), response.statusCode(),
				"Not getting 201 response for created device");
		}

		Device newDevice = findOnlyDevice();
		this.assertDevicesEqual(device1, newDevice,
			DeviceField.CREATED_DATE, DeviceField.CREATOR);
	}

	@Test
	@DisplayName("Create device with IPv6 management address")
	@ResourceLock("DB")
	void createDeviceWithIpv6Address() throws IOException, InterruptedException {
		this.createTestDomain();
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", "2001:db8::1")
			.put("domainId", this.testDomain.id())
			.put("name", "device1")
			.put("deviceType", this.getTestDriver().getName());
		HttpResponse<JsonNode> response = apiClient.post("/devices", data);
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for created device with an IPv6 management address");
		Device newDevice = findOnlyDevice();
		Assertions.assertEquals("2001:db8::1", newDevice.mgmtAddress(),
			"Management address not stored as entered");
		Assertions.assertEquals(InetAddress.getByName("2001:db8::1"), newDevice.cachedIpAddress(),
			"Cached IP address not resolved from management address at creation");
	}

	@Test
	@DisplayName("Create device with FQDN management address")
	@ResourceLock("DB")
	void createDeviceWithFqdnAddress() throws IOException, InterruptedException {
		this.createTestDomain();
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", "router1.example.com")
			.put("domainId", this.testDomain.id())
			.put("name", "device1")
			.put("deviceType", this.getTestDriver().getName());
		HttpResponse<JsonNode> response = apiClient.post("/devices", data);
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for created device with a hostname management address");
		Device newDevice = findOnlyDevice();
		Assertions.assertEquals("router1.example.com", newDevice.mgmtAddress(),
			"Management address not stored as entered");
	}

	@Test
	@DisplayName("Reject device with malformed management address")
	@ResourceLock("DB")
	void createDeviceWithMalformedAddress() throws IOException, InterruptedException {
		this.createTestDomain();
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", "not an address!")
			.put("domainId", this.testDomain.id())
			.put("name", "device1")
			.put("deviceType", this.getTestDriver().getName());
		HttpResponse<JsonNode> response = apiClient.post("/devices", data);
		Assertions.assertEquals(
			Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
			"Not getting 400 response for a malformed management address");
	}

	@Test
	@DisplayName("Create devices with same management address")
	@ResourceLock("DB")
	void createDevicesWithSameMgmtAddress() throws IOException, InterruptedException {
		this.createTestDomain();
		Device device1 = FakeDeviceFactory.getFakeCiscoIosDevice(this.testDomain, null, 0);
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", device1.mgmtAddress())
			.put("domainId", this.testDomain.id())
			.put("name", device1.name())
			.put("deviceType", device1.driver());
		{
			HttpResponse<JsonNode> response = apiClient.post("/devices", data);
			Assertions.assertEquals(
				Response.Status.CREATED.getStatusCode(), response.statusCode(),
				"Not getting 201 response for created device");
		}
		device1 = findDeviceByAddress(device1.mgmtAddress());
		{
			HttpResponse<JsonNode> response = apiClient.post("/devices", data);
			Assertions.assertEquals(
				Response.Status.CONFLICT.getStatusCode(), response.statusCode(),
				"Not getting 409 response for duplicated device");
		}
		{
			// Disable first device
			ObjectNode editData = JsonNodeFactory.instance.objectNode()
				.put("enabled", false);
			HttpResponse<JsonNode> response = apiClient.put(
				"/devices/%d".formatted(device1.id()), editData);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for edited device");
		}
		{
			// New creation attempt
			HttpResponse<JsonNode> response = apiClient.post("/devices", data);
			Assertions.assertEquals(
				Response.Status.CREATED.getStatusCode(), response.statusCode(),
				"Not getting 201 response for second created device");
		}
		{
			// Try to re-enable first device
			ObjectNode editData = JsonNodeFactory.instance.objectNode()
				.put("enabled", true);
			HttpResponse<JsonNode> response = apiClient.put(
				"/devices/%d".formatted(device1.id()), editData);
			Assertions.assertEquals(
				Response.Status.CONFLICT.getStatusCode(), response.statusCode(),
				"Not getting 409 response for edited device");
		}
	}

	@Test
	@DisplayName("Delete device")
	@ResourceLock("DB")
	void deleteDevice() throws IOException, InterruptedException {
		this.createTestDomain();
		Device device1 = Device.create(this.getTestDriver().getName(),
			"10.1.1.1", this.testDomain, "test").toBuilder().name("device1").build();
		Device device2 = Device.create(this.getTestDriver().getName(),
			"10.1.1.2", this.testDomain, "test").toBuilder().name("device2").build();
		device1 = TestDevices.add(device1);
		device2 = TestDevices.add(device2);
		{
			HttpResponse<JsonNode> response = apiClient.delete(
				"/devices/%d".formatted(TestServer.UNKNOWN_ID));
			Assertions.assertEquals(
				Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
				"Not getting 404 response for unknown device deletion");
		}
		{
			HttpResponse<JsonNode> response = apiClient.delete(
				"/devices/%d".formatted(device1.id()));
			Assertions.assertEquals(
				Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
				"Not getting 204 response for device deletion");
		}
		HttpResponse<JsonNode> listResponse = apiClient.get("/devices");
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), listResponse.statusCode(),
			"Not getting 200 response for device listing");
		Assertions.assertEquals(1, listResponse.body().size(),
			"Device list doesn't have 1 element");
		JsonNode deviceNode = listResponse.body().iterator().next();
		Assertions.assertEquals(
			JsonNodeFactory.instance.objectNode()
				.put("id", device2.id())
				.put("name", device2.name())
				.put("family", device2.family())
				.put("mgmtAddress", device2.mgmtAddress())
				.put("status", device2.status().toString())
				.put("driver", device2.driver())
				.put("eol", device2.isEndOfLife())
				.put("eos", device2.isEndOfSale())
				.put("configCompliant", device2.compliant())
				.put("softwareLevel", device2.softwareLevel().toString())
				.put("networkClass", device2.networkClass().toString()),
			deviceNode,
			"Retrieved device doesn't match expected object");
	}

	@Test
	@DisplayName("Deleting a device cascades to its DeviceAccess rows and their owned specific credential, but not a referenced global one")
	@ResourceLock("DB")
	void deleteDeviceCascadesAccessesAndOwnedCredentialOnly() throws IOException, InterruptedException {
		this.createTestDomain();
		Device device1 = Device.create(this.getTestDriver().getName(),
			"10.1.1.1", this.testDomain, "test").toBuilder().name("device1").build();
		DeviceCredentialSet globalCred = DeviceSshAccount.builder().username("admin").password("admin").superPassword(null).name("globalCredForCascadeTest").build();
		DeviceCredentialSet specificCred = DeviceSshAccount.builder().username("admin").password("admin").superPassword(null)
			.name(DeviceCredentialSet.generateSpecificName()).deviceSpecific(true).build();
		globalCred = TestDevices.addCredentialSet(globalCred);
		device1 = TestDevices.add(device1.toBuilder().accesses(List.of(
			DeviceAccess.of("ssh").toBuilder().globalCredentialSet(globalCred).build(),
			DeviceAccess.of("telnet").toBuilder().specificCredentialSet(specificCred).build())).build());
		long globalCredId = globalCred.id();
		long specificCredId = device1.getDeviceAccess("telnet").specificCredentialSet().id();

		HttpResponse<JsonNode> response = apiClient.delete("/devices/%d".formatted(device1.id()));
		Assertions.assertEquals(
			Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
			"Not getting 204 response for device deletion");

		long deviceId = device1.id();
		int deviceAccessCount = new Transactions().read(dsl -> dsl.fetchCount(DEVICE_ACCESS, DEVICE_ACCESS.DEVICE.eq(deviceId)));
		Assertions.assertEquals(0, deviceAccessCount,
			"DeviceAccess rows should be cascade-deleted along with the device");
		Assertions.assertNull(TestDevices.findCredentialSet(specificCredId),
			"The owned specific credential set should be cascade-deleted along with its DeviceAccess");
		Assertions.assertNotNull(TestDevices.findCredentialSet(globalCredId),
			"A referenced global credential set must survive device deletion");
	}

	@Test
	@DisplayName("Reject a device access that sets both a global and a specific credential set")
	@ResourceLock("DB")
	void deviceAccessRejectsBothCredentialRelations() throws IOException, InterruptedException {
		this.createTestDomain();
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", "10.1.1.1")
			.put("domainId", this.testDomain.id())
			.put("name", "device1")
			.put("deviceType", this.getTestDriver().getName());
		ObjectNode accessNode = JsonNodeFactory.instance.objectNode()
			.put("accessName", "ssh");
		accessNode.putObject("globalCredentialSet").put("type", "SSH").put("id", 1);
		accessNode.putObject("specificCredentialSet").put("type", "SSH")
			.put("username", "admin").put("password", "admin");
		data.withArray("accesses").add(accessNode);

		HttpResponse<JsonNode> response = apiClient.post("/devices", data);
		Assertions.assertEquals(
			Response.Status.PRECONDITION_FAILED.getStatusCode(), response.statusCode(),
			"Not getting the expected error status for a device access with both credential relations set");
	}

	@Test
	@DisplayName("Reject a device access pinned to a global credential set of the wrong family")
	@ResourceLock("DB")
	void deviceAccessRejectsMismatchedCredentialClass() throws IOException, InterruptedException {
		this.createTestDomain();
		DeviceCredentialSet snmpCred = DeviceSnmpv2cCommunity.builder().community("public").name("snmpCredForMismatchTest").build();
		long snmpCredId = TestDevices.addCredentialSet(snmpCred).id();
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", "10.1.1.1")
			.put("domainId", this.testDomain.id())
			.put("name", "device1")
			.put("deviceType", this.getTestDriver().getName());
		ObjectNode accessNode = JsonNodeFactory.instance.objectNode()
			.put("accessName", "ssh");
		accessNode.putObject("globalCredentialSet").put("type", "SNMP v2").put("id", snmpCredId);
		data.withArray("accesses").add(accessNode);

		HttpResponse<JsonNode> response = apiClient.post("/devices", data);
		Assertions.assertEquals(
			Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
			"Not getting 400 for a credential class mismatched with the access's declared family");
	}

	@Test
	@DisplayName("Edit device")
	@ResourceLock("DB")
	void editDevice() throws IOException, InterruptedException {
		this.createTestDomain();
		Device device1 = FakeDeviceFactory.getFakeCiscoIosDevice(this.testDomain, null, 1);
		Device device2 = FakeDeviceFactory.getFakeCiscoIosDevice(this.testDomain, null, 2);
		device1 = TestDevices.add(device1);
		device2 = TestDevices.add(device2);
		{
			ObjectNode editData = JsonNodeFactory.instance.objectNode()
				.put("enabled", false);
			HttpResponse<JsonNode> response = apiClient.put(
				"/devices/%d".formatted(TestServer.UNKNOWN_ID), editData);
			Assertions.assertEquals(
				Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
				"Not getting 404 response for unknown edited device");
		}
		{
			ObjectNode editData = JsonNodeFactory.instance.objectNode()
				.put("enabled", false);
			HttpResponse<JsonNode> response = apiClient.put(
				"/devices/%d".formatted(device1.id()), editData);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for edited device");

			Device editedDevice = TestDevices.find(device1.id());
			Assertions.assertEquals(Device.Status.DISABLED, editedDevice.status(),
				"The edited device is not disabled");
			this.assertDevicesEqual(device1, editedDevice, DeviceField.STATUS);
		}
		{
			ObjectNode editData = JsonNodeFactory.instance.objectNode()
				.put("enabled", true);
			HttpResponse<JsonNode> response = apiClient.put(
				"/devices/%d".formatted(device1.id()), editData);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for edited device");

			Device editedDevice = TestDevices.find(device1.id());
			Assertions.assertEquals(Device.Status.INPRODUCTION, editedDevice.status(),
				"The edited device is not enabled");
			this.assertDevicesEqual(device1, editedDevice);
		}
		{
			final String comments = "TEST EDIT COMMENT";
			ObjectNode editData = JsonNodeFactory.instance.objectNode()
				.put("comments", comments);
			HttpResponse<JsonNode> response = apiClient.put(
				"/devices/%d".formatted(device1.id()), editData);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for edited device");

			Device editedDevice = TestDevices.find(device1.id());
			Assertions.assertEquals(comments, editedDevice.comments(),
				"The edited device comments are not correct");
			this.assertDevicesEqual(device1, editedDevice, DeviceField.COMMENTS);
		}
	}

	@Test
	@DisplayName("Device tags: create, edit, list and search")
	@ResourceLock("DB")
	void deviceTags() throws IOException, InterruptedException {
		this.createTestDomain();
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", "10.1.1.1")
			.put("domainId", this.testDomain.id())
			.put("deviceType", this.getTestDriver().getName())
			.put("comments", "Created with tags");
		data.putArray("tags").add("core").add(" Paris ").add("core");
		{
			ObjectNode invalidData = data.deepCopy();
			invalidData.putArray("tags").add("x".repeat(Device.MAX_TAG_LENGTH + 1));
			HttpResponse<JsonNode> response = apiClient.post("/devices", invalidData);
			Assertions.assertEquals(
				Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
				"Not getting 400 response for a too long tag");
		}
		{
			HttpResponse<JsonNode> response = apiClient.post("/devices", data);
			Assertions.assertEquals(
				Response.Status.CREATED.getStatusCode(), response.statusCode(),
				"Not getting 201 response for created device");
		}
		long deviceId;
		Device newDevice = findOnlyDevice();
		deviceId = newDevice.id();
		Assertions.assertEquals(Set.of("core", "Paris"), newDevice.tags(),
			"The tags of the created device are not correct");
		Assertions.assertEquals("Created with tags", newDevice.comments(),
			"The comments of the created device are not correct");
		{
			HttpResponse<JsonNode> response = apiClient.get("/devices/%d".formatted(deviceId));
			Assertions.assertEquals(Response.Status.OK.getStatusCode(), response.statusCode());
			Set<String> tags = new HashSet<>();
			response.body().get("tags").forEach(t -> tags.add(t.asText()));
			Assertions.assertEquals(Set.of("core", "Paris"), tags, "The returned device tags are not correct");
		}
		{
			ObjectNode editData = JsonNodeFactory.instance.objectNode();
			editData.putArray("tags").add("core").add("lab");
			HttpResponse<JsonNode> response = apiClient.put("/devices/%d".formatted(deviceId), editData);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for edited device");
			Device editedDevice = TestDevices.find(deviceId);
			Assertions.assertEquals(Set.of("core", "lab"), editedDevice.tags(),
				"The tags of the edited device are not correct");
			Assertions.assertEquals("Created with tags", editedDevice.comments(),
				"The comments should be untouched when not passed");
		}
		{
			ObjectNode editData = JsonNodeFactory.instance.objectNode()
				.put("comments", "Only comments");
			HttpResponse<JsonNode> response = apiClient.put("/devices/%d".formatted(deviceId), editData);
			Assertions.assertEquals(Response.Status.OK.getStatusCode(), response.statusCode());
			Set<String> tags = new HashSet<>();
			response.body().get("tags").forEach(t -> tags.add(t.asText()));
			Assertions.assertEquals(Set.of("core", "lab"), tags, "The tags should be untouched when not passed");
		}
		{
			HttpResponse<JsonNode> response = apiClient.get("/devicetags");
			Assertions.assertEquals(Response.Status.OK.getStatusCode(), response.statusCode());
			JsonNode tagList = response.body();
			Assertions.assertEquals(2, tagList.size(), "Unexpected number of device tags");
			Assertions.assertEquals("core", tagList.get(0).get("name").asText());
			Assertions.assertEquals(1, tagList.get(0).get("deviceCount").asLong());
			Assertions.assertEquals("lab", tagList.get(1).get("name").asText());
			Assertions.assertEquals(1, tagList.get(1).get("deviceCount").asLong());
		}
		{
			ObjectNode searchData = JsonNodeFactory.instance.objectNode()
				.put("query", "[Tag] is \"lab\"");
			HttpResponse<JsonNode> response = apiClient.post("/devices/search", searchData);
			Assertions.assertEquals(Response.Status.OK.getStatusCode(), response.statusCode());
			Assertions.assertEquals(1, response.body().get("devices").size(), "The device should be found by tag");
			Assertions.assertEquals(deviceId, response.body().get("devices").get(0).get("id").asLong());
		}
		{
			ObjectNode searchData = JsonNodeFactory.instance.objectNode()
				.put("query", "[Tag] is \"Paris\"");
			HttpResponse<JsonNode> response = apiClient.post("/devices/search", searchData);
			Assertions.assertEquals(Response.Status.OK.getStatusCode(), response.statusCode());
			Assertions.assertEquals(0, response.body().get("devices").size(), "No device should be found by removed tag");
		}
	}

}
