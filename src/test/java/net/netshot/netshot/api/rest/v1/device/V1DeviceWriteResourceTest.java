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
package net.netshot.netshot.api.rest.v1.device;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The writes on the devices of the v1 API, over HTTP: adding a device, replacing and patching it, deleting it.
 */
@ApiTest
@V1Endpoints({
	"POST /devices",
	"PUT /devices/{id}",
	"PATCH /devices/{id}",
	"GET /devices/{id}",
	"DELETE /devices/{id}"
})
@DisplayName("v1 device writes")
@ResourceLock("DB")
public class V1DeviceWriteResourceTest {

	private V1Client admin;
	private Domain paris;
	private Domain lyon;
	private Device router1;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
		TestDevices.deleteAll();
		TestWiring.get().transactions().run(dsl -> dsl.execute("truncate table task cascade"));
		this.paris = TestDevices.addDomain("Paris-" + System.nanoTime(), "Paris DC");
		this.lyon = TestDevices.addDomain("Lyon-" + System.nanoTime(), "Lyon DC");
		this.router1 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.paris, null, 1));
	}

	private String creation(String extra) {
		return """
			{"mgmtAddress": "10.99.0.1", "mgmtDomain": %d, "driver": "CiscoIOS12"%s}""".formatted(this.paris.id(), extra);
	}

	private Device storedDevice(Answer accepted) {
		return TestDevices.find(accepted.json().get("device").get("id").asLong());
	}

	@Test
	@DisplayName("Add with a driver: 202, Location, task, device stored with its accesses and specific credentials")
	void addWithDriver() throws Exception {
		Answer answer = this.admin.post("/devices", this.creation("""
			, "comments": "New device", "tags": ["lab", "edge"],
			"accesses": [{"accessName": "ssh", "port": 2222, "specificCredentialSet": {"type": "SSH", "username": "u", "password": "p"}}]"""));
		Assertions.assertEquals(202, answer.status(), answer.toString());
		JsonNode task = answer.json();
		Assertions.assertEquals("/api/v1/tasks/" + task.get("id").asLong(), answer.header("Location"));
		Assertions.assertEquals("TakeSnapshotTask", task.get("type").asText());
		Device device = this.storedDevice(answer);
		Assertions.assertEquals("10.99.0.1", device.mgmtAddress());
		Assertions.assertEquals("New device", device.comments());
		Assertions.assertEquals(Set.of("edge", "lab"), device.tags());
		Assertions.assertEquals(this.paris.id(), device.mgmtDomain().id());
		DeviceAccess ssh = device.getDeviceAccess("ssh");
		Assertions.assertEquals(2222, ssh.port());
		DeviceSshAccount specific = (DeviceSshAccount) ssh.specificCredentialSet();
		Assertions.assertTrue(specific.deviceSpecific());
		Assertions.assertEquals("u", specific.username());
		Assertions.assertEquals("p", specific.password());
		// The task is stored, and the secrets are never returned
		Assertions.assertNotNull(TestTasks.find(task.get("id").asLong()));
		Answer got = this.admin.get("/devices/" + device.id());
		Assertions.assertFalse(got.body().contains("\"p\""), got.body());
		Assertions.assertTrue(got.json().get("accesses").get(0).get("specificCredentialSet").get("hasPassword").asBoolean());
	}

	@Test
	@DisplayName("Add with driver options and a global credential set")
	void addWithOptionsAndGlobalCredentials() throws Exception {
		DeviceSshAccount global = (DeviceSshAccount) TestDevices.addCredentialSet(DeviceSshAccount.builder().name("Global-" + System.nanoTime())
			.username("g").password("gp").build());
		Answer answer = this.admin.post("/devices", """
			{"mgmtAddress": "10.99.0.2", "mgmtDomain": %d, "driver": "CheckpointGaia", "options": {"fullBackup": false},
			"accesses": [{"accessName": "ssh", "globalCredentialSet": %d}]}""".formatted(this.paris.id(), global.id()));
		Assertions.assertEquals(202, answer.status(), answer.toString());
		Device device = this.storedDevice(answer);
		Assertions.assertEquals(Boolean.FALSE, device.options().get("fullBackup"));
		Assertions.assertEquals(global.id(), device.getDeviceAccess("ssh").globalCredentialSet().id());
		Answer badOption = this.admin.post("/devices", """
			{"mgmtAddress": "10.99.0.3", "mgmtDomain": %d, "driver": "CheckpointGaia", "options": {"nothing": 1}}""".formatted(this.paris.id()));
		Assertions.assertEquals(400, badOption.status(), badOption.toString());
		Assertions.assertEquals(400, this.admin.post("/devices", """
			{"mgmtAddress": "10.99.0.3", "mgmtDomain": %d, "driver": "CiscoIOS12",
			"accesses": [{"accessName": "ssh", "globalCredentialSet": %d}]}""".formatted(this.paris.id(), TestServer.UNKNOWN_ID)).status());
	}

	@Test
	@DisplayName("Add without a driver: a discovery task, which needs an SNMP community")
	void addWithAutoDiscovery() throws Exception {
		String body = """
			{"mgmtAddress": "10.99.0.4", "mgmtDomain": %d, "comments": "Found", "tags": ["auto"]}""".formatted(this.paris.id());
		Answer noCommunity = this.admin.post("/devices", body);
		Assertions.assertEquals(400, noCommunity.status(), noCommunity.toString());
		Assertions.assertEquals("NETSHOT_INVALID_CREDENTIALS", noCommunity.json().get("code").asText());
		TestDevices.addCredentialSet(DeviceSnmpv2cCommunity.builder().name("Public-" + System.nanoTime()).community("public").build());
		Answer answer = this.admin.post("/devices", body);
		Assertions.assertEquals(202, answer.status(), answer.toString());
		JsonNode task = answer.json();
		Assertions.assertEquals("DiscoverDeviceTypeTask", task.get("type").asText());
		Assertions.assertEquals("/api/v1/tasks/" + task.get("id").asLong(), answer.header("Location"));
		Assertions.assertEquals("10.99.0.4", task.get("parameters").get("deviceAddress").asText());
		Task stored = TestTasks.find(task.get("id").asLong());
		DiscoverDeviceTypeTask spec = (DiscoverDeviceTypeTask) stored.spec();
		Assertions.assertEquals(this.paris.id(), spec.domainId());
		Assertions.assertEquals(List.of("auto"), spec.deviceTags());
		// Neither accesses nor options without a driver
		Assertions.assertEquals(400, this.admin.post("/devices", """
			{"mgmtAddress": "10.99.0.5", "mgmtDomain": %d, "accesses": [{"accessName": "ssh"}]}""".formatted(this.paris.id())).status());
		Assertions.assertEquals(400, this.admin.post("/devices", """
			{"mgmtAddress": "10.99.0.5", "mgmtDomain": %d, "options": {"fullBackup": true}}""".formatted(this.paris.id())).status());
	}

	@Test
	@DisplayName("Add: validation errors")
	void addValidation() throws Exception {
		Answer unknownDomain = this.admin.post("/devices", """
			{"mgmtAddress": "10.99.0.1", "mgmtDomain": %d, "driver": "CiscoIOS12"}""".formatted(TestServer.UNKNOWN_ID));
		Assertions.assertEquals(400, unknownDomain.status(), unknownDomain.toString());
		Assertions.assertEquals("NETSHOT_INVALID_DOMAIN", unknownDomain.json().get("code").asText());
		Answer badAddress = this.admin.post("/devices", """
			{"mgmtAddress": "not an address!", "mgmtDomain": %d, "driver": "CiscoIOS12"}""".formatted(this.paris.id()));
		Assertions.assertEquals(400, badAddress.status(), badAddress.toString());
		Assertions.assertEquals("NETSHOT_MALFORMED_IP_ADDRESS", badAddress.json().get("code").asText());
		Assertions.assertEquals(400, this.admin.post("/devices", """
			{"mgmtAddress": "224.0.0.1", "mgmtDomain": %d, "driver": "CiscoIOS12"}""".formatted(this.paris.id())).status());
		Assertions.assertEquals(400, this.admin.post("/devices", """
			{"mgmtDomain": %d, "driver": "CiscoIOS12"}""".formatted(this.paris.id())).status());
		Assertions.assertEquals(400, this.admin.post("/devices", """
			{"mgmtAddress": "10.99.0.1", "driver": "CiscoIOS12"}""").status());
		Assertions.assertEquals(400, this.admin.post("/devices", """
			{"mgmtAddress": "10.99.0.1", "mgmtDomain": null, "driver": "CiscoIOS12"}""").status());
		Answer unknownDriver = this.admin.post("/devices", """
			{"mgmtAddress": "10.99.0.1", "mgmtDomain": %d, "driver": "Nothing"}""".formatted(this.paris.id()));
		Assertions.assertEquals(400, unknownDriver.status(), unknownDriver.toString());
		Assertions.assertEquals(400, this.admin.post("/devices", this.creation(", \"tags\": [\"\"]")).status());
		Assertions.assertEquals(400, this.admin.post("/devices", this.creation(", \"colour\": \"red\"")).status());
		// Credentials which are missing, unknown, incompatible with the access, or twice
		Answer incomplete = this.admin.post("/devices", this.creation("""
			, "accesses": [{"accessName": "ssh", "specificCredentialSet": {"type": "SSH", "username": "u"}}]"""));
		Assertions.assertEquals(400, incomplete.status(), incomplete.toString());
		Assertions.assertEquals("password", incomplete.json().get("errors").get(0).get("field").asText());
		Assertions.assertEquals(400, this.admin.post("/devices", this.creation("""
			, "accesses": [{"accessName": "ssh", "specificCredentialSet": {"type": "SNMP_V2C", "community": "c"}}]""")).status());
		Assertions.assertEquals(400, this.admin.post("/devices", this.creation("""
			, "accesses": [{"accessName": "nothing", "specificCredentialSet": {"type": "SSH", "username": "u", "password": "p"}}]""")).status());
		Assertions.assertEquals(400, this.admin.post("/devices", this.creation("""
			, "accesses": [{"accessName": "ssh"}, {"accessName": "ssh"}]""")).status());
		Assertions.assertEquals(400, this.admin.post("/devices", this.creation("""
			, "accesses": [{"accessName": "ssh", "port": 70000}]""")).status());
		Assertions.assertEquals(0, TestTasks.all().size());
		Assertions.assertEquals(1, TestDevices.count());
	}

	@Test
	@DisplayName("Add: a duplicate address is a conflict")
	void addDuplicate() throws Exception {
		Answer duplicate = this.admin.post("/devices", """
			{"mgmtAddress": "%s", "mgmtDomain": %d, "driver": "CiscoIOS12"}""".formatted(this.router1.mgmtAddress(), this.paris.id()));
		Assertions.assertEquals(409, duplicate.status(), duplicate.toString());
		Assertions.assertEquals("NETSHOT_DUPLICATE_DEVICE", duplicate.json().get("code").asText());
	}

	@Test
	@DisplayName("Permissions: adding, replacing, patching and deleting need the read-write level")
	void permissions(TestServer server) throws Exception {
		V1Client operator = new V1Client(server, UiUser.LEVEL_OPERATOR);
		V1Client readWrite = new V1Client(server, UiUser.LEVEL_READWRITE);
		String path = "/devices/" + this.router1.id();
		Assertions.assertEquals(403, operator.post("/devices", this.creation("")).status());
		Assertions.assertEquals(403, operator.put(path, "{}").status());
		Assertions.assertEquals(403, operator.patch(path, "{}").status());
		Assertions.assertEquals(403, operator.delete(path).status());
		Assertions.assertEquals(202, readWrite.post("/devices", this.creation("")).status());
		Assertions.assertEquals(200, readWrite.patch(path, "{\"comments\": \"rw\"}").status());
		Assertions.assertEquals(401, new V1Client(server, null).post("/devices", this.creation("")).status());
	}

	@Test
	@DisplayName("PUT: a device read, modified and sent back is replaced, keeping its secrets")
	void putRoundTrip() throws Exception {
		String path = "/devices/" + this.router1.id();
		Answer got = this.admin.get(path);
		JsonNode device = got.json();
		((ObjectNode) device).put("comments", "Edited").put("status", "DISABLED");
		((ObjectNode) device).putArray("tags").add("one").add("two");
		((ObjectNode) device.get("accesses").get(0)).put("port", 2200);
		Answer put = this.admin.put(path, device.toString(), "If-Match", got.header("ETag"));
		Assertions.assertEquals(200, put.status(), put.toString());
		Assertions.assertEquals("Edited", put.json().get("comments").asText());
		Assertions.assertEquals("DISABLED", put.json().get("status").asText());
		Assertions.assertEquals(2200, put.json().get("accesses").get(0).get("port").asInt());
		Assertions.assertNotEquals(got.header("ETag"), put.header("ETag"));
		Device stored = TestDevices.find(this.router1.id());
		Assertions.assertEquals(Set.of("one", "two"), stored.tags());
		DeviceSshAccount specific = (DeviceSshAccount) stored.getDeviceAccess("ssh").specificCredentialSet();
		Assertions.assertEquals("user", specific.username());
		Assertions.assertEquals("pass", specific.password());
		Assertions.assertEquals("pass", specific.superPassword());
		Assertions.assertEquals(((DeviceSshAccount) this.router1.getDeviceAccess("ssh").specificCredentialSet()).id(), specific.id());
		// Read only data is kept
		Assertions.assertEquals("router00001", stored.name());
		Assertions.assertEquals(3, stored.attributes().size());
		Assertions.assertEquals(2, stored.networkInterfaces().size());
	}

	@Test
	@DisplayName("PUT: secrets are replaced or cleared, the access can change credentials")
	void putCredentials() throws Exception {
		String path = "/devices/" + this.router1.id();
		String base = """
			{"mgmtAddress": "172.16.0.1", "mgmtDomain": %d, "accesses": [%%s]}""".formatted(this.paris.id());
		Answer changed = this.admin.put(path, base.formatted("""
			{"accessName": "ssh", "specificCredentialSet": {"type": "SSH", "username": "other", "password": "newpass", "superPassword": null}}"""));
		Assertions.assertEquals(200, changed.status(), changed.toString());
		DeviceSshAccount specific = (DeviceSshAccount) TestDevices.find(this.router1.id()).getDeviceAccess("ssh").specificCredentialSet();
		Assertions.assertEquals("other", specific.username());
		Assertions.assertEquals("newpass", specific.password());
		Assertions.assertNull(specific.superPassword());
		// Another type of credentials gives another credential set, the old one is removed
		Answer telnet = this.admin.put(path, base.formatted("""
			{"accessName": "telnet", "specificCredentialSet": {"type": "TELNET", "username": "t", "password": "tp"}}"""));
		Assertions.assertEquals(200, telnet.status(), telnet.toString());
		Device stored = TestDevices.find(this.router1.id());
		Assertions.assertNull(stored.getDeviceAccess("ssh"));
		Assertions.assertEquals("tp", ((DeviceTelnetAccount) stored.getDeviceAccess("telnet")
			.specificCredentialSet()).password());
		Assertions.assertNull(TestDevices.findCredentialSet(specific.id()));
		// A PUT without accesses removes them
		Assertions.assertEquals(200, this.admin.put(path, base.formatted("")).status());
		Assertions.assertEquals(0, TestDevices.find(this.router1.id()).accesses().size());
	}

	@Test
	@DisplayName("PUT: validation errors, 404")
	void putValidation() throws Exception {
		String path = "/devices/" + this.router1.id();
		Assertions.assertEquals(400, this.admin.put(path, "{\"mgmtDomain\": " + this.paris.id() + "}").status());
		Assertions.assertEquals(400, this.admin.put(path, "{\"mgmtAddress\": \"10.0.0.1\", \"mgmtDomain\": " + TestServer.UNKNOWN_ID + "}").status());
		Assertions.assertEquals(400, this.admin.put(path, "{\"mgmtAddress\": \"bad address\", \"mgmtDomain\": " + this.paris.id() + "}").status());
		Assertions.assertEquals(400, this.admin.put(path, "{\"mgmtAddress\": \"10.0.0.1\", \"mgmtDomain\": " + this.paris.id()
			+ ", \"options\": {\"x\": 1}}").status());
		Assertions.assertEquals(400, this.admin.put(path, "{\"mgmtAddress\": \"10.0.0.1\", \"mgmtDomain\": " + this.paris.id()
			+ ", \"status\": \"BROKEN\"}").status());
		Answer missing = this.admin.put("/devices/" + TestServer.UNKNOWN_ID, "{\"mgmtAddress\": \"10.0.0.1\", \"mgmtDomain\": "
			+ this.paris.id() + "}");
		Assertions.assertEquals(404, missing.status(), missing.toString());
		Assertions.assertEquals("NETSHOT_DEVICE_NOT_FOUND", missing.json().get("code").asText());
		Device other = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(this.paris, null, 2));
		Answer duplicate = this.admin.put(path, "{\"mgmtAddress\": \"" + other.mgmtAddress() + "\", \"mgmtDomain\": " + this.paris.id() + "}");
		Assertions.assertEquals(409, duplicate.status(), duplicate.toString());
	}

	@Test
	@DisplayName("PATCH merges, keeps the secrets and the other properties")
	void patch() throws Exception {
		String path = "/devices/" + this.router1.id();
		Answer patched = this.admin.patch(path, "{\"comments\": \"Patched\", \"mgmtDomain\": " + this.lyon.id() + "}");
		Assertions.assertEquals(200, patched.status(), patched.toString());
		Assertions.assertEquals("Patched", patched.json().get("comments").asText());
		Assertions.assertEquals(this.lyon.id(), patched.json().get("mgmtDomain").get("id").asLong());
		Assertions.assertEquals("172.16.0.1", patched.json().get("mgmtAddress").asText());
		Device stored = TestDevices.find(this.router1.id());
		Assertions.assertEquals(this.lyon.id(), stored.mgmtDomain().id());
		Assertions.assertEquals("pass", ((DeviceSshAccount) stored.getDeviceAccess("ssh").specificCredentialSet()).password());
		Assertions.assertEquals(Set.of(), stored.tags());
		// A list is replaced as a whole, a secret not given is kept
		Answer accesses = this.admin.patch(path, """
			{"accesses": [{"accessName": "ssh", "port": 2022, "specificCredentialSet": {"type": "SSH", "username": "patched"}}]}""");
		Assertions.assertEquals(200, accesses.status(), accesses.toString());
		DeviceAccess ssh = TestDevices.find(this.router1.id()).getDeviceAccess("ssh");
		Assertions.assertEquals(2022, ssh.port());
		Assertions.assertEquals("patched", ((DeviceSshAccount) ssh.specificCredentialSet()).username());
		Assertions.assertEquals("pass", ((DeviceSshAccount) ssh.specificCredentialSet()).password());
		Assertions.assertEquals(400, this.admin.patch(path, "{\"mgmtAddress\": null}").status());
		Assertions.assertEquals(400, this.admin.patch(path, "[]").status());
		Assertions.assertEquals(404, this.admin.patch("/devices/" + TestServer.UNKNOWN_ID, "{}").status());
	}

	@Test
	@DisplayName("If-Match is honoured by PUT, PATCH and DELETE (412)")
	void ifMatch() throws Exception {
		String path = "/devices/" + this.router1.id();
		Answer got = this.admin.get(path);
		String etag = got.header("ETag");
		String body = "{\"mgmtAddress\": \"172.16.0.1\", \"mgmtDomain\": " + this.paris.id() + ", \"comments\": \"%s\"}";
		Assertions.assertEquals(200, this.admin.put(path, body.formatted("first"), "If-Match", etag).status());
		Answer stalePut = this.admin.put(path, body.formatted("second"), "If-Match", etag);
		Assertions.assertEquals(412, stalePut.status(), stalePut.toString());
		Assertions.assertEquals("NETSHOT_VERSION_MISMATCH", stalePut.json().get("code").asText());
		Assertions.assertEquals(412, this.admin.patch(path, "{\"comments\": \"second\"}", "If-Match", etag).status());
		Assertions.assertEquals(412, this.admin.delete(path, "If-Match", etag).status());
		Assertions.assertEquals("first", TestDevices.find(this.router1.id()).comments());
		Assertions.assertEquals(400, this.admin.put(path, body.formatted("third"), "If-Match", "W/\"1\"").status());
		String current = this.admin.get(path).header("ETag");
		Assertions.assertEquals(200, this.admin.patch(path, "{\"comments\": \"third\"}", "If-Match", current).status());
		String latest = this.admin.get(path).header("ETag");
		Assertions.assertEquals(204, this.admin.delete(path, "If-Match", latest).status());
		Assertions.assertEquals(404, this.admin.delete(path, "If-Match", latest).status());
	}
}
