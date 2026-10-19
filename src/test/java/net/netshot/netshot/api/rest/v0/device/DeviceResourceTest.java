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

import java.io.IOException;
import java.net.InetAddress;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.hibernate.Session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.FakeDeviceFactory;
import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.WithRestServerTest;
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.DeviceDriver.DriverProtocol;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device.MissingDeviceDriverException;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@DisplayName("Device tests")
@ResourceLock("DB")
public class DeviceResourceTest extends WithRestServerTest {

	private String testDomainName = "Domain 1";
	private Domain testDomain;

	private enum DeviceField {
		CONTACT(Device::getContact),
		CREATED_DATE(Device::getCreatedDate),
		CREATOR(Device::getCreator),
		DRIVER(Device::getDriver),
		EOL_DATE(Device::getEolDate),
		EOS_DATE(Device::getEosDate),
		FAMILY(Device::getFamily),
		LOCATION(Device::getLocation),
		MGMT_ADDRESS(Device::getMgmtAddress),
		NAME(Device::getName),
		NETWORK_CLASS(Device::getNetworkClass),
		SERIAL_NUMBER(Device::getSerialNumber),
		SOFTWARE_LEVEL(Device::getSoftwareLevel),
		SOFTWARE_VERSION(Device::getSoftwareVersion),
		STATUS(Device::getStatus),
		COMMENTS(Device::getComments);

		private Function<Device, ?> getter;

		DeviceField(Function<Device, ?> getter) {
			this.getter = getter;
		}
	}

	private void createTestDomain() throws IOException {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			this.testDomain = new Domain(
				this.testDomainName, "Test Domain for devices",
				new Network4Address("10.1.1.1"),
				null
			);
			session.persist(this.testDomain);
			session.getTransaction().commit();
		}
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

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@AfterEach
	void cleanUpData() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session
				.createMutationQuery("delete from Device")
				.executeUpdate();
			session
				.createMutationQuery("delete from Domain")
				.executeUpdate();
			session.getTransaction().commit();
		}
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
		Device device1 = new Device(this.getTestDriver().getName(),
			"10.1.1.1", this.testDomain, "test");
		device1.setName("device1");
		Device device2 = new Device(this.getTestDriver().getName(),
			"10.1.1.2", this.testDomain, "test");
		device2.setName("device2");
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(device1);
			session.persist(device2);
			session.getTransaction().commit();
		}
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
					.put("id", device1.getId())
					.put("name", device1.getName())
					.put("family", device1.getFamily())
					.put("mgmtAddress", device1.getMgmtAddress())
					.put("status", device1.getStatus().toString())
					.put("driver", device1.getDriver())
					.put("eol", device1.isEndOfLife())
					.put("eos", device1.isEndOfSale())
					.put("configCompliant", device1.isCompliant())
					.put("softwareLevel", device1.getSoftwareLevel().toString())
					.put("networkClass", device1.getNetworkClass().toString()),
				deviceNode1,
				"Retrieved device doesn't match expected object");
		}
	}

	@Test
	@DisplayName("Get device")
	@ResourceLock("DB")
	void getDevice() throws IOException, InterruptedException, MissingDeviceDriverException {
		this.createTestDomain();
		Device device1 = new Device(this.getTestDriver().getName(),
			"10.1.1.1", this.testDomain, "test");
		device1.setName("device1");
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(device1);
			session.getTransaction().commit();
		}
		{
			HttpResponse<JsonNode> response = apiClient.get("/devices/%d".formatted(UNKNOWN_ID));
			Assertions.assertEquals(
				Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
				"Not getting 404 response for nonexistent device");
		}
		{
			HttpResponse<JsonNode> response = apiClient.get("/devices/%d".formatted(device1.getId()));
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for device");
			JsonNode deviceNode1 = response.body();
			ObjectNode expectedNode1 = JsonNodeFactory.instance.objectNode()
				.put("id", device1.getId())
				.put("name", device1.getName())
				.put("networkClass", device1.getNetworkClass().toString())
				.put("family", device1.getFamily())
				.put("mgmtAddress", device1.getMgmtAddress())
				.put("cachedIpAddress", device1.getMgmtAddress())
				.put("status", device1.getStatus().toString())
				.put("driver", device1.getDriver())
				.put("realDeviceType", TestDrivers.registry().forDevice(device1).getDescription())
				.put("serialNumber", device1.getSerialNumber())
				.put("softwareVersion", device1.getSoftwareVersion())
				.put("softwareLevel", device1.getSoftwareLevel().toString())
				.put("contact", device1.getContact())
				.put("location", device1.getLocation())
				.put("creator", device1.getCreator())
				.put("changeDate", device1.getChangeDate().getTime())
				.put("createdDate", device1.getCreatedDate().getTime())
				.putNull("eolDate")
				.putNull("eolModule")
				.putNull("eosDate")
				.putNull("eosModule")
				.put("endOfLife", device1.isEndOfLife())
				.put("endOfSale", device1.isEndOfSale())
				.put("compliant", device1.isCompliant())
				.put("comments", device1.getComments());
			expectedNode1.putArray("ownerGroups");
			expectedNode1.putArray("attributes");
			expectedNode1.putArray("accesses");
			expectedNode1.putArray("tags");
			expectedNode1.putObject("options");
			expectedNode1.set("mgmtDomain",
				JsonNodeFactory.instance.objectNode()
					.put("id", device1.getMgmtDomain().getId())
					.put("name", device1.getMgmtDomain().getName())
					.put("changeDate", device1.getMgmtDomain().getChangeDate().getTime())
					.put("description", device1.getMgmtDomain().getDescription())
					.put("server4Address", device1.getMgmtDomain().getServer4Address().getIp())
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
		Device device1 = new Device(this.getTestDriver().getName(),
			"10.1.1.1", this.testDomain, "test");
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", device1.getMgmtAddress())
			.put("domainId", this.testDomain.getId())
			.put("name", device1.getName())
			.put("deviceType", device1.getDriver());
		{
			HttpResponse<JsonNode> response = apiClient.post("/devices",
				data.deepCopy().put("domainId", UNKNOWN_ID));
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

		try (Session session = Database.getSession()) {
			Device newDevice = session
				.createQuery("from Device d", Device.class)
				.uniqueResult();
			device1.setId(newDevice.getId());
			this.assertDevicesEqual(device1, newDevice,
				DeviceField.CREATED_DATE, DeviceField.CREATOR);
		}
	}

	@Test
	@DisplayName("Create device with IPv6 management address")
	@ResourceLock("DB")
	void createDeviceWithIpv6Address() throws IOException, InterruptedException {
		this.createTestDomain();
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", "2001:db8::1")
			.put("domainId", this.testDomain.getId())
			.put("name", "device1")
			.put("deviceType", this.getTestDriver().getName());
		HttpResponse<JsonNode> response = apiClient.post("/devices", data);
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for created device with an IPv6 management address");
		try (Session session = Database.getSession()) {
			Device newDevice = session
				.createQuery("from Device d", Device.class)
				.uniqueResult();
			Assertions.assertEquals("2001:db8::1", newDevice.getMgmtAddress(),
				"Management address not stored as entered");
			Assertions.assertEquals(InetAddress.getByName("2001:db8::1"), newDevice.getCachedIpAddress(),
				"Cached IP address not resolved from management address at creation");
		}
	}

	@Test
	@DisplayName("Create device with FQDN management address")
	@ResourceLock("DB")
	void createDeviceWithFqdnAddress() throws IOException, InterruptedException {
		this.createTestDomain();
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", "router1.example.com")
			.put("domainId", this.testDomain.getId())
			.put("name", "device1")
			.put("deviceType", this.getTestDriver().getName());
		HttpResponse<JsonNode> response = apiClient.post("/devices", data);
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for created device with a hostname management address");
		try (Session session = Database.getSession()) {
			Device newDevice = session
				.createQuery("from Device d", Device.class)
				.uniqueResult();
			Assertions.assertEquals("router1.example.com", newDevice.getMgmtAddress(),
				"Management address not stored as entered");
		}
	}

	@Test
	@DisplayName("Reject device with malformed management address")
	@ResourceLock("DB")
	void createDeviceWithMalformedAddress() throws IOException, InterruptedException {
		this.createTestDomain();
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", "not an address!")
			.put("domainId", this.testDomain.getId())
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
			.put("ipAddress", device1.getMgmtAddress())
			.put("domainId", this.testDomain.getId())
			.put("name", device1.getName())
			.put("deviceType", device1.getDriver());
		{
			HttpResponse<JsonNode> response = apiClient.post("/devices", data);
			Assertions.assertEquals(
				Response.Status.CREATED.getStatusCode(), response.statusCode(),
				"Not getting 201 response for created device");
		}
		try (Session session = Database.getSession()) {
			Device newDevice = session
				.createQuery("from Device d where d.mgmtAddress = :ip", Device.class)
				.setParameter("ip", device1.getMgmtAddress())
				.uniqueResult();
			device1.setId(newDevice.getId());
		}
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
				"/devices/%d".formatted(device1.getId()), editData);
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
				"/devices/%d".formatted(device1.getId()), editData);
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
		Device device1 = new Device(this.getTestDriver().getName(),
			"10.1.1.1", this.testDomain, "test");
		device1.setName("device1");
		Device device2 = new Device(this.getTestDriver().getName(),
			"10.1.1.2", this.testDomain, "test");
		device2.setName("device2");
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(device1);
			session.persist(device2);
			session.getTransaction().commit();
		}
		{
			HttpResponse<JsonNode> response = apiClient.delete(
				"/devices/%d".formatted(UNKNOWN_ID));
			Assertions.assertEquals(
				Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
				"Not getting 404 response for unknown device deletion");
		}
		{
			HttpResponse<JsonNode> response = apiClient.delete(
				"/devices/%d".formatted(device1.getId()));
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
				.put("id", device2.getId())
				.put("name", device2.getName())
				.put("family", device2.getFamily())
				.put("mgmtAddress", device2.getMgmtAddress())
				.put("status", device2.getStatus().toString())
				.put("driver", device2.getDriver())
				.put("eol", device2.isEndOfLife())
				.put("eos", device2.isEndOfSale())
				.put("configCompliant", device2.isCompliant())
				.put("softwareLevel", device2.getSoftwareLevel().toString())
				.put("networkClass", device2.getNetworkClass().toString()),
			deviceNode,
			"Retrieved device doesn't match expected object");
	}

	@Test
	@DisplayName("Deleting a device cascades to its DeviceAccess rows and their owned specific credential, but not a referenced global one")
	@ResourceLock("DB")
	void deleteDeviceCascadesAccessesAndOwnedCredentialOnly() throws IOException, InterruptedException {
		this.createTestDomain();
		Device device1 = new Device(this.getTestDriver().getName(),
			"10.1.1.1", this.testDomain, "test");
		device1.setName("device1");
		DeviceCredentialSet globalCred = new DeviceSshAccount("admin", "admin", null, "globalCredForCascadeTest");
		DeviceCredentialSet specificCred = new DeviceSshAccount("admin", "admin", null,
			DeviceCredentialSet.generateSpecificName());
		specificCred.setDeviceSpecific(true);
		long globalCredId;
		long specificCredId;
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(globalCred);
			DeviceAccess sshAccess = new DeviceAccess(device1, "ssh");
			sshAccess.setGlobalCredentialSet(globalCred);
			device1.getAccesses().add(sshAccess);
			DeviceAccess telnetAccess = new DeviceAccess(device1, "telnet");
			telnetAccess.setSpecificCredentialSet(specificCred);
			device1.getAccesses().add(telnetAccess);
			session.persist(device1);
			session.getTransaction().commit();
			globalCredId = globalCred.getId();
			specificCredId = specificCred.getId();
		}

		HttpResponse<JsonNode> response = apiClient.delete("/devices/%d".formatted(device1.getId()));
		Assertions.assertEquals(
			Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
			"Not getting 204 response for device deletion");

		try (Session session = Database.getSession()) {
			Long deviceAccessCount = session
				.createQuery("select count(a) from DeviceAccess a where a.key.device.id = :id", Long.class)
				.setParameter("id", device1.getId())
				.uniqueResult();
			Assertions.assertEquals(0L, deviceAccessCount,
				"DeviceAccess rows should be cascade-deleted along with the device");
			Assertions.assertNull(session.get(DeviceCredentialSet.class, specificCredId),
				"The owned specific credential set should be cascade-deleted along with its DeviceAccess");
			Assertions.assertNotNull(session.get(DeviceCredentialSet.class, globalCredId),
				"A referenced global credential set must survive device deletion");
		}
	}

	@Test
	@DisplayName("Reject a device access that sets both a global and a specific credential set")
	@ResourceLock("DB")
	void deviceAccessRejectsBothCredentialRelations() throws IOException, InterruptedException {
		this.createTestDomain();
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", "10.1.1.1")
			.put("domainId", this.testDomain.getId())
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
		DeviceCredentialSet snmpCred = new DeviceSnmpv2cCommunity("public", "snmpCredForMismatchTest");
		long snmpCredId;
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(snmpCred);
			session.getTransaction().commit();
			snmpCredId = snmpCred.getId();
		}
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("autoDiscover", false)
			.put("ipAddress", "10.1.1.1")
			.put("domainId", this.testDomain.getId())
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
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(device1);
			session.persist(device2);
			session.getTransaction().commit();
		}
		{
			ObjectNode editData = JsonNodeFactory.instance.objectNode()
				.put("enabled", false);
			HttpResponse<JsonNode> response = apiClient.put(
				"/devices/%d".formatted(UNKNOWN_ID), editData);
			Assertions.assertEquals(
				Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
				"Not getting 404 response for unknown edited device");
		}
		{
			ObjectNode editData = JsonNodeFactory.instance.objectNode()
				.put("enabled", false);
			HttpResponse<JsonNode> response = apiClient.put(
				"/devices/%d".formatted(device1.getId()), editData);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for edited device");

			try (Session session = Database.getSession()) {
				Device editedDevice = session
					.createQuery("from Device d where d.id = :id", Device.class)
					.setParameter("id", device1.getId())
					.uniqueResult();
				Assertions.assertEquals(Device.Status.DISABLED, editedDevice.getStatus(),
					"The edited device is not disabled");
				this.assertDevicesEqual(device1, editedDevice, DeviceField.STATUS);
			}
		}
		{
			ObjectNode editData = JsonNodeFactory.instance.objectNode()
				.put("enabled", true);
			HttpResponse<JsonNode> response = apiClient.put(
				"/devices/%d".formatted(device1.getId()), editData);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for edited device");

			try (Session session = Database.getSession()) {
				Device editedDevice = session
					.createQuery("from Device d where d.id = :id", Device.class)
					.setParameter("id", device1.getId())
					.uniqueResult();
				Assertions.assertEquals(Device.Status.INPRODUCTION, editedDevice.getStatus(),
					"The edited device is not enabled");
				this.assertDevicesEqual(device1, editedDevice);
			}
		}
		{
			final String comments = "TEST EDIT COMMENT";
			ObjectNode editData = JsonNodeFactory.instance.objectNode()
				.put("comments", comments);
			HttpResponse<JsonNode> response = apiClient.put(
				"/devices/%d".formatted(device1.getId()), editData);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for edited device");

			try (Session session = Database.getSession()) {
				Device editedDevice = session
					.createQuery("from Device d where d.id = :id", Device.class)
					.setParameter("id", device1.getId())
					.uniqueResult();
				Assertions.assertEquals(comments, editedDevice.getComments(),
					"The edited device comments are not correct");
				this.assertDevicesEqual(device1, editedDevice, DeviceField.COMMENTS);
			}
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
			.put("domainId", this.testDomain.getId())
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
		try (Session session = Database.getSession()) {
			Device newDevice = session
				.createQuery("from Device d left join fetch d.tags", Device.class)
				.uniqueResult();
			deviceId = newDevice.getId();
			Assertions.assertEquals(Set.of("core", "Paris"), newDevice.getTags(),
				"The tags of the created device are not correct");
			Assertions.assertEquals("Created with tags", newDevice.getComments(),
				"The comments of the created device are not correct");
		}
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
			try (Session session = Database.getSession()) {
				Device editedDevice = session
					.createQuery("from Device d left join fetch d.tags where d.id = :id", Device.class)
					.setParameter("id", deviceId)
					.uniqueResult();
				Assertions.assertEquals(Set.of("core", "lab"), editedDevice.getTags(),
					"The tags of the edited device are not correct");
				Assertions.assertEquals("Created with tags", editedDevice.getComments(),
					"The comments should be untouched when not passed");
			}
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
