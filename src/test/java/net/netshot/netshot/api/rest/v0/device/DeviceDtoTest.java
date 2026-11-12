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
package net.netshot.netshot.api.rest.v0.device;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceAccess.SshHostKeyVerification;
import net.netshot.netshot.inventory.device.DeviceCreation;
import net.netshot.netshot.inventory.device.DeviceUpdate;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.contract.DtoContract;

/**
 * Checks that the device DTOs and their mapper are a faithful replacement of the entities
 * the REST API used to read and write: the representation of an in-memory device (with all
 * the kinds of attributes, credential sets and groups) is the same, in JSON and XML, as the
 * one of the entity with the same view; the requests are read like they used to be.
 * (The API contract tests only cover the kinds the seed data holds.)
 */
@ScriptEngineTest
@DisplayName("v0 device DTOs")
class DeviceDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(DeviceDtoTest.class, System.currentTimeMillis());

	private static Domain domain(long id, String name) throws Exception {
		return Domain.builder().id(id).changeDate(Instant.ofEpochMilli(1_600_000_000_000L)).name(name).description("Description of " + name)
			.server4Address(new Network4Address("10.255.0.1")).server6Address(new Network6Address("2001:db8::1")).build();
	}

	private static Device fullDevice() throws Exception {
		Domain domain = domain(1, "Paris DC");
		Module eolModule = Module.builder().id(10).slot("1").partNumber("PN-EOL").serialNumber("SN-EOL")
			.firstSeenDate(Instant.ofEpochMilli(1_400_000_000_000L)).lastSeenDate(Instant.ofEpochMilli(1_410_000_000_000L)).build();
		Module eosModule = Module.builder().id(11).slot("2").partNumber("PN-EOS").serialNumber("SN-EOS").removed(true).build();
		DynamicDeviceGroup dynamic = DynamicDeviceGroup.builder().id(100).name("Dynamic").folder("Production").hiddenFromReports(true)
			.query("[Name] contains \"r\"").changeDate(Instant.ofEpochMilli(1_600_000_200_000L)).build();
		StaticDeviceGroup fixed = StaticDeviceGroup.builder().id(101).name("Static").folder("")
			.changeDate(Instant.ofEpochMilli(1_600_000_300_000L)).build();

		DeviceSnmpv1Community snmp1 = DeviceSnmpv1Community.builder().id(1).community(Secret.local("public1")).name("SNMP v1")
			.community(Secret.vault(5L, "path/community1")).build();
		DeviceSnmpv2cCommunity snmp2 = DeviceSnmpv2cCommunity.builder().id(2).community(Secret.local("public2")).name("SNMP v2").mgmtDomain(domain).build();
		DeviceSnmpv3Community snmp3 = DeviceSnmpv3Community.builder().id(3).name("SNMP v3").mgmtDomain(domain)
			.username(Secret.local("user")).username(Secret.vault(6L, "path/user"))
			.authType(AuthProtocol.HMAC192SHA256).authKey(Secret.local("authkey")).authKey(Secret.vault(7L, "path/auth"))
			.privType(PrivProtocol.AES256).privKey(Secret.local("privkey")).privKey(Secret.vault(8L, "path/priv")).build();
		DeviceSshAccount ssh = DeviceSshAccount.builder().id(4).username(Secret.local("sshuser")).password(Secret.local("sshpass")).superPassword(Secret.local("sshenable")).name("SSH")
			.password(Secret.vault(9L, "path/pass")).build();
		DeviceSshKeyAccount sshKey = DeviceSshKeyAccount.builder().id(5).username(Secret.local("keyuser")).privateKey(Secret.local("PRIVATE KEY")).password(Secret.local("passphrase"))
			.superPassword(Secret.local("keyenable")).name("SSH Key").mgmtDomain(domain).privateKey(Secret.vault(10L, "path/key")).build();
		DeviceTelnetAccount telnet = DeviceTelnetAccount.builder().id(6).username(Secret.local("telnetuser")).password(Secret.local("telnetpass")).superPassword(Secret.local("telnetenable"))
			.name("Telnet").build();
		DeviceHttpAccount http = DeviceHttpAccount.builder().id(7).username(Secret.local("httpuser")).password(Secret.local("httppass")).name("HTTP").deviceSpecific(true)
			.username(Secret.vault(11L, "path/http")).build();

		List<DeviceAccess> accesses = new ArrayList<>(List.of(
			access("a1", snmp1, true),
			access("a2", snmp2, false),
			access("a3", snmp3, true),
			access("a4", ssh, false),
			access("a5", sshKey, true),
			access("a6", telnet, false),
			access("a7", http, true)));
		accesses.add(DeviceAccess.of("plain").toBuilder()
			.address("192.0.2.1")
			.port(8443)
			.sshHostKeyVerification(SshHostKeyVerification.TRUST_KNOWN)
			.sshTrustedHostKeys("ssh-ed25519 AAAA")
			.sshTrustedHostKeysSince(Instant.ofEpochMilli(1_600_000_400_000L))
			.httpsCaTrustMode(HttpsCaTrustMode.CUSTOM_CA)
			.httpsCustomCaCertificate("-----BEGIN CERTIFICATE-----")
			.build());

		return Device.create("CiscoIOS12", "10.1.1.1", domain, "seed").toBuilder()
			.id(42)
			.name("router-1")
			.family("Cisco 2900")
			.location("Paris")
			.contact("noc@example.net")
			.serialNumber("SN1")
			.softwareVersion("16.1")
			.softwareLevel(ConformanceLevel.GOLD)
			.networkClass(Device.NetworkClass.ROUTER)
			.status(Device.Status.PREPRODUCTION)
			.comments("Some comments")
			.changeDate(Instant.ofEpochMilli(1_600_000_100_000L))
			.createdDate(Instant.ofEpochMilli(1_500_000_000_000L))
			.eolDate(Instant.ofEpochMilli(1_000_000_000_000L))
			.eosDate(Instant.ofEpochMilli(4_000_000_000_000L))
			.eolModule(eolModule)
			.eosModule(eosModule)
			.options(new HashMap<>(Map.of("fullBackup", true, "mode", "fast")))
			.tags(Device.normalizeTags(List.of("core", "production")))
			.attributes(List.of(
				new DeviceTextAttribute("text", "some text"),
				new DeviceNumericAttribute("number", 12.5),
				new DeviceLongTextAttribute("long", "some very long text"),
				new DeviceBinaryAttribute("binary", true)))
			.ownerGroups(List.of(dynamic, fixed))
			.accesses(accesses)
			.build();
	}

	private static DeviceAccess access(String name, DeviceCredentialSet credentialSet, boolean global) {
		DeviceAccess.DeviceAccessBuilder access = DeviceAccess.of(name).toBuilder();
		if (global) {
			access.globalCredentialSet(credentialSet);
		}
		else {
			access.specificCredentialSet(credentialSet);
		}
		return access.build();
	}

	private static byte[] json(Object value) throws Exception {
		return JSON.writer().writeValueAsBytes(value);
	}

	private static byte[] xml(Object value) throws Exception {
		return XML.writer().writeValueAsBytes(value);
	}

	private void assertRepresentation(Device device) throws Exception {
		DeviceDto dto = DeviceMapper.toDto(TestDrivers.registry(), device);
		this.contract.checkBoth(json(dto), xml(dto));
	}

	@Test
	@DisplayName("A full device has the same JSON and XML representation as the entity (all kinds of credentials, attributes, groups)")
	void fullDeviceRepresentation() throws Exception {
		Device device = fullDevice();
		this.assertRepresentation(device);
		// Make sure the check is meaningful
		String json = new String(json(DeviceMapper.toDto(TestDrivers.registry(), device)));
		Assertions.assertTrue(json.contains("\"type\":\"SNMP v3\""), json);
		Assertions.assertTrue(json.contains("\"type\":\"SSH Key\""), json);
		Assertions.assertTrue(json.contains("\"type\":\"LONGTEXT\""), json);
		Assertions.assertTrue(json.contains("\"type\":\"DynamicDeviceGroup\""), json);
		Assertions.assertTrue(json.contains("\"endOfLife\":true"), json);
		Assertions.assertTrue(json.contains("\"password\":\"=\""), json);
		Assertions.assertTrue(new String(xml(DeviceMapper.toDto(TestDrivers.registry(), device))).startsWith("<Device>"));
	}

	@Test
	@DisplayName("The tags and the attributes of a device (sets) are listed in a stable order, by name")
	void collectionsInStableOrder() {
		List<String> tags = List.of("zulu", "alpha", "mike", "bravo", "yankee", "charlie", "x-ray", "delta");
		Device device = Device.create("CiscoIOS12", "10.1.1.3", null, "seed").toBuilder()
			.tags(new HashSet<>(tags))
			.attributes(tags.stream().<DeviceAttribute>map(name -> new DeviceTextAttribute(name, "v")).toList())
			.build();
		DeviceDto dto = DeviceMapper.toDto(TestDrivers.registry(), device);
		List<String> sorted = tags.stream().sorted().toList();
		Assertions.assertEquals(sorted, dto.tags());
		Assertions.assertEquals(sorted, dto.attributes().stream().map(DeviceAttributeDto::name).toList());
	}

	@Test
	@DisplayName("A bare device has the same representation as the entity")
	void bareDeviceRepresentation() throws Exception {
		Device device = Device.create("CiscoIOS12", "10.1.1.2", null, "seed").toBuilder().id(43).build();
		this.assertRepresentation(device);
	}

	// ------------------------------------------------------------------
	// Requests
	// ------------------------------------------------------------------

	private static final String ACCESSES = """
		[
		 {"accessName": "plain", "address": "192.0.2.1", "port": 8443, "sshHostKeyVerification": "TRUST_KNOWN",
		  "sshTrustedHostKeys": "ssh-ed25519 AAAA", "httpsCaTrustMode": "CUSTOM_CA", "httpsCustomCaCertificate": "PEM",
		  "globalCredentialSet": {"type": "SSH", "id": 4}},
		 {"accessName": "a1", "specificCredentialSet": {"type": "SNMP v1", "id": 1, "name": "n1", "community": "c1",
		  "communityVaultInstanceId": 5, "communityVaultPath": "p1", "deviceSpecific": true}},
		 {"accessName": "a2", "specificCredentialSet": {"type": "SNMP v2", "community": "=", "name": "n2",
		  "mgmtDomain": {"id": 3, "name": "d3", "description": "dd3"}}},
		 {"accessName": "a3", "specificCredentialSet": {"type": "SNMP v3", "name": "n3", "community": "c3", "username": "u3",
		  "usernameVaultInstanceId": 6, "usernameVaultPath": "pu3", "authType": "SHA", "authKey": "ak", "authKeyVaultInstanceId": 7,
		  "authKeyVaultPath": "pa3", "privType": "AES128", "privKey": "=", "privKeyVaultInstanceId": 8, "privKeyVaultPath": "pp3"}},
		 {"accessName": "a4", "specificCredentialSet": {"type": "SSH", "name": "n4", "username": "u4", "password": "p4",
		  "passwordVaultInstanceId": 9, "passwordVaultPath": "pp4", "superPassword": "s4", "superPasswordVaultInstanceId": 10,
		  "superPasswordVaultPath": "ps4", "usernameVaultInstanceId": 11, "usernameVaultPath": "pu4"}},
		 {"accessName": "a5", "specificCredentialSet": {"type": "SSH Key", "name": "n5", "username": "u5", "password": "=",
		  "superPassword": "s5", "privateKey": "key5", "privateKeyVaultInstanceId": 12, "privateKeyVaultPath": "pk5"}},
		 {"accessName": "a6", "specificCredentialSet": {"type": "Telnet", "name": "n6", "username": "u6", "password": "p6", "superPassword": "s6"}},
		 {"accessName": "a7", "specificCredentialSet": {"type": "HTTP", "name": "n7", "username": "u7", "password": "p7",
		  "usernameVaultInstanceId": 13, "usernameVaultPath": "pu7", "passwordVaultInstanceId": 14, "passwordVaultPath": "pp7"}}
		]""";

	/**
	 * Describes what a request was read as, the way the former entities exposed it (bean properties):
	 * the access had a back reference to its device (unset), the Vault instance given by a request
	 * was a pending one ({@code resolvedXxxVaultInstanceId}), the stored one ({@code xxxVaultInstanceId})
	 * being unset, and only the identity of the domains is read.
	 * @param value the access or credential set
	 * @return the property values, by property name
	 */
	private static Map<String, Object> describe(Record value) throws ReflectiveOperationException {
		Map<String, Object> description = new TreeMap<>();
		if (value instanceof DeviceAccess) {
			description.put("device", null);
		}
		for (RecordComponent component : value.getClass().getRecordComponents()) {
			String name = component.getName();
			Object item = component.getAccessor().invoke(value);
			if (Secret.class.isAssignableFrom(component.getType())) {
				DtoContract.describeSecret(description, name, (Secret) item);
			}
			else if (item == null || item instanceof String || item instanceof Number || item instanceof Boolean
					|| item instanceof Enum) {
				description.put(name, item);
			}
			else if (item instanceof Instant instant) {
				description.put(name, Date.from(instant));
			}
			else if (item instanceof DeviceCredentialSet credentialSet) {
				description.put(name, describe((Record) credentialSet));
			}
			else if (item instanceof Domain domain) {
				Map<String, Object> reference = new TreeMap<>();
				reference.put("id", domain.id());
				reference.put("name", domain.name());
				reference.put("description", domain.description());
				description.put(name, reference);
			}
		}
		return description;
	}

	@Test
	@DisplayName("The accesses of a request are read like the entities used to be")
	void accessesRequestIsReadLikeTheEntity() throws Exception {
		List<DeviceAccessDto> dtos = JSON.readerFor(new TypeReference<List<DeviceAccessDto>>() { })
			.readValue(ACCESSES);
		for (int i = 0; i < dtos.size(); i++) {
			DeviceAccess actual = DeviceAccessMapper.toEntity(dtos.get(i));
			this.contract.checkText("access-" + i, DtoContract.render(actual, describe(actual)));
		}
		// Make sure the check is meaningful (a Vault reference wins over a local value given with it)
		Assertions.assertEquals(Secret.vault(9L, "pp4"), ((DeviceSshAccount) dtos.stream().map(DeviceAccessMapper::toEntity)
			.filter(a -> "a4".equals(a.accessName())).findFirst().orElseThrow().specificCredentialSet()).password());
		DeviceSshKeyAccount key = (DeviceSshKeyAccount) DeviceAccessMapper.toEntity(dtos.get(5)).specificCredentialSet();
		Assertions.assertNull(key.password(), "the = placeholder means unchanged, thus null");
		Assertions.assertEquals(12L, Secret.vaultInstanceId(key.privateKey()));
	}

	@Test
	@DisplayName("A new device request has the default values of the former request")
	void newDeviceRequestDefaults() throws Exception {
		NewDeviceRequest empty = JSON.readerFor(NewDeviceRequest.class).readValue("{}");
		Assertions.assertTrue(empty.autoDiscover());
		Assertions.assertEquals("", empty.ipAddress());
		Assertions.assertEquals(-1L, empty.domainId());
		Assertions.assertEquals("", empty.name());
		Assertions.assertEquals("", empty.deviceType());
		Assertions.assertNull(empty.accesses());
		Assertions.assertNull(empty.options());
		Assertions.assertNull(empty.comments());
		Assertions.assertNull(empty.tags());
		NewDeviceRequest explicitNull = JSON.readerFor(NewDeviceRequest.class)
			.readValue("{\"autoDiscover\": null}");
		Assertions.assertFalse(explicitNull.autoDiscover(), "null is false, like it was for the primitive");

		NewDeviceRequest full = JSON.readerFor(NewDeviceRequest.class).readValue("""
			{"autoDiscover": false, "autoDiscoveryTask": 3, "ipAddress": "10.0.0.1", "domainId": 4, "name": "n",
			 "deviceType": "CiscoIOS12", "accesses": %s, "options": {"a": true}, "comments": "c", "tags": ["t"]}""".formatted(ACCESSES));
		DeviceCreation creation = DeviceMapper.toCreation(full, "author");
		Assertions.assertFalse(creation.autoDiscover());
		Assertions.assertEquals("10.0.0.1", creation.ipAddress());
		Assertions.assertEquals(4L, creation.domainId());
		Assertions.assertEquals("CiscoIOS12", creation.deviceType());
		Assertions.assertEquals(8, creation.accesses().size());
		Assertions.assertEquals(Map.of("a", true), creation.options());
		Assertions.assertEquals("c", creation.comments());
		Assertions.assertEquals(List.of("t"), creation.tags());
		Assertions.assertEquals("author", creation.author());
	}

	@Test
	@DisplayName("An update request leaves unchanged what it doesn't mention")
	void updateRequestMapping() throws Exception {
		UpdateDeviceRequest empty = JSON.readerFor(UpdateDeviceRequest.class).readValue("{}");
		DeviceUpdate update = DeviceMapper.toUpdate(empty);
		Assertions.assertEquals(new DeviceUpdate(null, null, null, null, null, null, null, null), update);

		UpdateDeviceRequest request = JSON.readerFor(UpdateDeviceRequest.class).readValue("""
			{"id": 5, "enabled": false, "comments": "c", "ipAddress": "10.0.0.2", "mgmtDomain": 6,
			 "accesses": [], "options": {}, "tags": []}""");
		DeviceUpdate full = DeviceMapper.toUpdate(request);
		Assertions.assertEquals(Boolean.FALSE, full.enabled());
		Assertions.assertEquals("c", full.comments());
		Assertions.assertEquals("10.0.0.2", full.ipAddress());
		Assertions.assertEquals(6L, full.mgmtDomainId());
		Assertions.assertEquals(new ArrayList<DeviceAccess>(), full.accesses());
		Assertions.assertEquals(Map.of(), full.options());
		Assertions.assertEquals(List.of(), full.tags());
	}

	@Test
	@DisplayName("An access without SSH host key verification is TRUST_ANY, but an explicit null isn't set")
	void sshHostKeyVerificationDefault() throws Exception {
		DeviceAccessDto absent = JSON.readerFor(DeviceAccessDto.class)
			.readValue("{\"accessName\": \"ssh\"}");
		Assertions.assertEquals(SshHostKeyVerification.TRUST_ANY, absent.sshHostKeyVerification());
		DeviceAccessDto explicitNull = JSON.readerFor(DeviceAccessDto.class)
			.readValue("{\"accessName\": \"ssh\", \"sshHostKeyVerification\": null}");
		Assertions.assertNull(explicitNull.sshHostKeyVerification());
		DeviceAccessDto known = JSON.readerFor(DeviceAccessDto.class)
			.readValue("{\"accessName\": \"ssh\", \"sshHostKeyVerification\": \"TRUST_KNOWN\"}");
		Assertions.assertEquals(SshHostKeyVerification.TRUST_KNOWN, known.sshHostKeyVerification());
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(DeviceAccessDto.class)
			.readValue("{\"accessName\": \"ssh\", \"sshHostKeyVerification\": \"NOPE\"}"));
	}

	@Test
	@DisplayName("A request with an unknown property is refused, like it used to be")
	void unknownPropertiesAreRefused() {
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(NewDeviceRequest.class)
			.readValue("{\"ipAddress\": \"10.0.0.1\", \"unknown\": 1}"));
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(UpdateDeviceRequest.class)
			.readValue("{\"unknown\": 1}"));
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(DeviceAccessDto.class)
			.readValue("{\"accessName\": \"ssh\", \"unknown\": 1}"));
		Assertions.assertThrows(JsonMappingException.class, () -> JSON.readerFor(DeviceAccessDto.class)
			.readValue("{\"accessName\": \"ssh\", \"globalCredentialSet\": {\"id\": 1}}"),
			"a credential set needs its type");
	}
}
