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

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.DtoContract;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.access.DeviceAccess.SshHostKeyVerification;
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceCreation;
import net.netshot.netshot.inventory.device.DeviceUpdate;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.Network6Address;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroupMembership;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * Checks that the device DTOs and their mapper are a faithful replacement of the entities
 * the REST API used to read and write: the representation of an in-memory device (with all
 * the kinds of attributes, credential sets and groups) is the same, in JSON and XML, as the
 * one of the entity with the same view; the requests are read like they used to be.
 * (The API contract tests only cover the kinds the seed data holds.)
 */
class DeviceDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(DeviceDtoTest.class, System.currentTimeMillis());

	private static Domain domain(long id, String name) throws Exception {
		Domain domain = new Domain(name, "Description of " + name, new Network4Address("10.255.0.1"), new Network6Address("2001:db8::1"));
		domain.setId(id);
		domain.setChangeDate(new Date(1_600_000_000_000L));
		return domain;
	}

	private static <T extends DeviceCredentialSet> T credential(T credentialSet, long id, Domain domain) {
		credentialSet.setId(id);
		credentialSet.setMgmtDomain(domain);
		return credentialSet;
	}

	private static Device fullDevice() throws Exception {
		Domain domain = domain(1, "Paris DC");
		Device device = new Device("CiscoIOS12", "10.1.1.1", domain, "seed");
		device.setId(42);
		device.setName("router-1");
		device.setFamily("Cisco 2900");
		device.setLocation("Paris");
		device.setContact("noc@example.net");
		device.setSerialNumber("SN1");
		device.setSoftwareVersion("16.1");
		device.setSoftwareLevel(ConformanceLevel.GOLD);
		device.setNetworkClass(Device.NetworkClass.ROUTER);
		device.setStatus(Device.Status.PREPRODUCTION);
		device.setComments("Some comments");
		device.setChangeDate(new Date(1_600_000_100_000L));
		device.setCreatedDate(new Date(1_500_000_000_000L));
		device.setEolDate(new Date(1_000_000_000_000L));
		device.setEosDate(new Date(4_000_000_000_000L));
		Module eolModule = new Module("1", "PN-EOL", "SN-EOL", device);
		eolModule.setId(10);
		eolModule.setFirstSeenDate(new Date(1_400_000_000_000L));
		eolModule.setLastSeenDate(new Date(1_410_000_000_000L));
		device.setEolModule(eolModule);
		Module eosModule = new Module("2", "PN-EOS", "SN-EOS", device);
		eosModule.setId(11);
		eosModule.setRemoved(true);
		device.setEosModule(eosModule);
		device.setOptions(new java.util.HashMap<>(Map.of("fullBackup", true, "mode", "fast")));
		device.replaceTags(List.of("core", "production"));
		device.addAttribute(new DeviceTextAttribute(device, "text", "some text"));
		device.addAttribute(new DeviceNumericAttribute(device, "number", 12.5));
		device.addAttribute(new DeviceLongTextAttribute(device, "long", "some very long text"));
		device.addAttribute(new DeviceBinaryAttribute(device, "binary", true));
		DynamicDeviceGroup dynamic = new DynamicDeviceGroup("Dynamic", "CiscoIOS12", "[Name] contains \"r\"");
		dynamic.setId(100);
		dynamic.setFolder("Production");
		dynamic.setHiddenFromReports(true);
		dynamic.setChangeDate(new Date(1_600_000_200_000L));
		StaticDeviceGroup fixed = new StaticDeviceGroup("Static");
		fixed.setId(101);
		fixed.setChangeDate(new Date(1_600_000_300_000L));
		device.getGroupMemberships().add(new DeviceGroupMembership(device, dynamic, 0));
		device.getGroupMemberships().add(new DeviceGroupMembership(device, fixed, 1));

		DeviceSnmpv1Community snmp1 = credential(new DeviceSnmpv1Community("public1", "SNMP v1"), 1, null);
		snmp1.setResolvedCommunityVaultInstanceId(5L);
		snmp1.setCommunityVaultPath("path/community1");
		DeviceSnmpv2cCommunity snmp2 = credential(new DeviceSnmpv2cCommunity("public2", "SNMP v2"), 2, domain);
		DeviceSnmpv3Community snmp3 = credential(new DeviceSnmpv3Community(null, "SNMP v3", "user", AuthProtocol.HMAC192SHA256,
			"authkey", PrivProtocol.AES256, "privkey"), 3, domain);
		snmp3.setResolvedUsernameVaultInstanceId(6L);
		snmp3.setUsernameVaultPath("path/user");
		snmp3.setResolvedAuthKeyVaultInstanceId(7L);
		snmp3.setAuthKeyVaultPath("path/auth");
		snmp3.setResolvedPrivKeyVaultInstanceId(8L);
		snmp3.setPrivKeyVaultPath("path/priv");
		DeviceSshAccount ssh = credential(new DeviceSshAccount("sshuser", "sshpass", "sshenable", "SSH"), 4, null);
		ssh.setResolvedPasswordVaultInstanceId(9L);
		ssh.setPasswordVaultPath("path/pass");
		DeviceSshKeyAccount sshKey = credential(new DeviceSshKeyAccount("keyuser", "PRIVATE KEY", "passphrase", "keyenable", "SSH Key"), 5, domain);
		sshKey.setResolvedPrivateKeyVaultInstanceId(10L);
		sshKey.setPrivateKeyVaultPath("path/key");
		DeviceTelnetAccount telnet = credential(new DeviceTelnetAccount("telnetuser", "telnetpass", "telnetenable", "Telnet"), 6, null);
		DeviceHttpAccount http = credential(new DeviceHttpAccount("httpuser", "httppass", "HTTP"), 7, null);
		http.setDeviceSpecific(true);
		http.setResolvedUsernameVaultInstanceId(11L);
		http.setUsernameVaultPath("path/http");

		addAccess(device, "a1", snmp1, true);
		addAccess(device, "a2", snmp2, false);
		addAccess(device, "a3", snmp3, true);
		addAccess(device, "a4", ssh, false);
		addAccess(device, "a5", sshKey, true);
		addAccess(device, "a6", telnet, false);
		addAccess(device, "a7", http, true);
		DeviceAccess plain = new DeviceAccess(device, "plain");
		plain.setAddress("192.0.2.1");
		plain.setPort(8443);
		plain.setSshHostKeyVerification(SshHostKeyVerification.TRUST_KNOWN);
		plain.setSshTrustedHostKeys("ssh-ed25519 AAAA");
		plain.setSshTrustedHostKeysSince(new Date(1_600_000_400_000L));
		plain.setHttpsCaTrustMode(HttpsCaTrustMode.CUSTOM_CA);
		plain.setHttpsCustomCaCertificate("-----BEGIN CERTIFICATE-----");
		device.getAccesses().add(plain);
		return device;
	}

	private static void addAccess(Device device, String name, DeviceCredentialSet credentialSet, boolean global) {
		DeviceAccess access = new DeviceAccess(device, name);
		if (global) {
			access.setGlobalCredentialSet(credentialSet);
		}
		else {
			access.setSpecificCredentialSet(credentialSet);
		}
		device.getAccesses().add(access);
	}

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestConfig.init(new Properties());
		TestDrivers.reload();
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
		Device device = new Device("CiscoIOS12", "10.1.1.3", null, "seed");
		List<String> tags = List.of("zulu", "alpha", "mike", "bravo", "yankee", "charlie", "x-ray", "delta");
		device.replaceTags(tags);
		for (String name : tags) {
			device.addAttribute(new DeviceTextAttribute(device, name, "v"));
		}
		DeviceDto dto = DeviceMapper.toDto(TestDrivers.registry(), device);
		List<String> sorted = tags.stream().sorted().toList();
		Assertions.assertEquals(sorted, dto.tags());
		Assertions.assertEquals(sorted, dto.attributes().stream().map(DeviceAttributeDto::name).toList());
	}

	@Test
	@DisplayName("A bare device has the same representation as the entity")
	void bareDeviceRepresentation() throws Exception {
		Device device = new Device("CiscoIOS12", "10.1.1.2", null, "seed");
		device.setId(43);
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

	/** The simple properties of an object, and the ones of the credential sets and domains it refers to. */
	private static Map<String, Object> describe(Object bean) throws IntrospectionException, ReflectiveOperationException {
		Map<String, Object> description = new TreeMap<>();
		for (PropertyDescriptor property : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {
			if (property.getReadMethod() == null || "class".equals(property.getName())) {
				continue;
			}
			Object value;
			try {
				value = property.getReadMethod().invoke(bean);
			}
			catch (InvocationTargetException e) {
				continue;
			}
			if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
					|| value instanceof Enum || value instanceof Date) {
				description.put(property.getName(), value);
			}
			else if (value instanceof DeviceCredentialSet || value instanceof Domain) {
				description.put(property.getName(), describe(value));
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
			Map<String, Object> actualDescription = describe(actual);
			// Only the id and the name of the domain are kept
			actualDescription.values().forEach(DeviceDtoTest::keepIdentityOfDomains);
			this.contract.checkText("access-" + i, DtoContract.render(actual, actualDescription));
		}
		// Make sure the check is meaningful
		Assertions.assertEquals("p4", ((DeviceSshAccount) dtos.stream().map(DeviceAccessMapper::toEntity)
			.filter(a -> "a4".equals(a.getAccessName())).findFirst().orElseThrow().getSpecificCredentialSet()).getPassword());
		DeviceSshKeyAccount key = (DeviceSshKeyAccount) DeviceAccessMapper.toEntity(dtos.get(5)).getSpecificCredentialSet();
		Assertions.assertNull(key.getPassword(), "the = placeholder means unchanged, thus null");
		Assertions.assertEquals(12L, key.getResolvedPrivateKeyVaultInstanceId());
	}

	@SuppressWarnings("unchecked")
	private static void keepIdentityOfDomains(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> nested = (Map<String, Object>) map;
			if (nested.containsKey("mgmtDomain") && nested.get("mgmtDomain") instanceof Map<?, ?> domain) {
				((Map<String, Object>) domain).keySet().retainAll(Set.of("id", "name", "description"));
			}
			nested.values().forEach(DeviceDtoTest::keepIdentityOfDomains);
		}
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
		Assertions.assertEquals(new DeviceUpdate(null, null, null, null, null, null, null), update);

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
