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

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import net.netshot.netshot.api.rest.v0.credentials.CredentialSetDto;
import net.netshot.netshot.api.rest.v0.credentials.CredentialSetMapper;
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
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.Network6Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.rest.RestViews.RestApiView;

/**
 * Checks that the credential set DTOs and their mapper are a faithful replacement of the entities
 * the REST API used to read and write: the representation of every kind of credential set is
 * the same, in JSON and XML, as the one of the entity with the same view; no secret ever appears in
 * it; the requests are read like they used to be.
 */
class CredentialSetDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder()
		.disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
	private static final XmlMapper XML = XmlMapper.builder()
		.disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
	private static final YAMLMapper YAML = YAMLMapper.builder()
		.disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();

	@RegisterExtension
	private final DtoContract contract = new DtoContract(CredentialSetDtoTest.class);

	/** Every secret value used by the credential sets of this test. */
	private static final List<String> SECRETS = new ArrayList<>();

	private static String secret(String name) {
		String value = "S3CRET-" + name;
		SECRETS.add(value);
		return value;
	}

	private static Domain domain() throws Exception {
		Domain domain = new Domain("Paris DC", "Paris data center", new Network4Address("10.255.0.1"), new Network6Address("2001:db8::1"));
		domain.setId(1);
		domain.setChangeDate(new Date(1_600_000_000_000L));
		return domain;
	}

	/** One credential set of each kind, with every field filled, secrets included. */
	private static List<DeviceCredentialSet> localCredentialSets() throws Exception {
		SECRETS.clear();
		DeviceSnmpv1Community v1 = new DeviceSnmpv1Community(secret("v1-community"), "SNMP v1");
		DeviceSnmpv2cCommunity v2 = new DeviceSnmpv2cCommunity(secret("v2-community"), "SNMP v2");
		v2.setMgmtDomain(domain());
		DeviceSnmpv3Community v3 = new DeviceSnmpv3Community(secret("v3-context"), "SNMP v3", "snmpuser", AuthProtocol.SHA,
			secret("v3-auth"), PrivProtocol.AES128, secret("v3-priv"));
		DeviceSshAccount ssh = new DeviceSshAccount("sshuser", secret("ssh-password"), secret("ssh-super"), "SSH");
		DeviceSshKeyAccount key = new DeviceSshKeyAccount("keyuser", secret("key-private"), secret("key-password"), secret("key-super"), "SSH Key");
		key.setMgmtDomain(domain());
		DeviceTelnetAccount telnet = new DeviceTelnetAccount("telnetuser", secret("telnet-password"), secret("telnet-super"), "Telnet");
		DeviceHttpAccount http = new DeviceHttpAccount("httpuser", secret("http-password"), "HTTP");
		List<DeviceCredentialSet> all = List.of(v1, v2, v3, ssh, key, telnet, http);
		long id = 1;
		for (DeviceCredentialSet credentialSet : all) {
			credentialSet.setId(id++);
		}
		return all;
	}

	/** The same kinds, with the fields held by a Vault instance. */
	private static List<DeviceCredentialSet> vaultCredentialSets() throws Exception {
		List<DeviceCredentialSet> all = localCredentialSets();
		DeviceSnmpv1Community v1 = (DeviceSnmpv1Community) all.get(0);
		v1.setResolvedCommunityVaultInstanceId(11L);
		v1.setCommunityVaultPath("netshot/v1");
		DeviceSnmpv3Community v3 = (DeviceSnmpv3Community) all.get(2);
		v3.setResolvedCommunityVaultInstanceId(12L);
		v3.setCommunityVaultPath("netshot/v3/community");
		v3.setResolvedUsernameVaultInstanceId(12L);
		v3.setUsernameVaultPath("netshot/v3/username");
		v3.setResolvedAuthKeyVaultInstanceId(12L);
		v3.setAuthKeyVaultPath("netshot/v3/auth");
		v3.setResolvedPrivKeyVaultInstanceId(12L);
		v3.setPrivKeyVaultPath("netshot/v3/priv");
		DeviceSshKeyAccount key = (DeviceSshKeyAccount) all.get(4);
		key.setResolvedUsernameVaultInstanceId(13L);
		key.setUsernameVaultPath("netshot/key/username");
		key.setResolvedPasswordVaultInstanceId(13L);
		key.setPasswordVaultPath("netshot/key/password");
		key.setResolvedSuperPasswordVaultInstanceId(13L);
		key.setSuperPasswordVaultPath("netshot/key/super");
		key.setResolvedPrivateKeyVaultInstanceId(13L);
		key.setPrivateKeyVaultPath("netshot/key/private");
		DeviceHttpAccount http = (DeviceHttpAccount) all.get(6);
		http.setResolvedUsernameVaultInstanceId(14L);
		http.setUsernameVaultPath("netshot/http/username");
		all.get(1).setDeviceSpecific(true);
		return all;
	}

	private static byte[] json(Object value) throws Exception {
		return JSON.writerWithView(RestApiView.class).writeValueAsBytes(value);
	}

	private static byte[] xml(Object value) throws Exception {
		return XML.writerWithView(RestApiView.class).writeValueAsBytes(value);
	}

	private static byte[] yaml(Object value) throws Exception {
		return YAML.writerWithView(RestApiView.class).writeValueAsBytes(value);
	}

	private void assertRepresentation(Object dto) throws Exception {
		this.contract.checkBoth(json(dto), xml(dto));
	}

	private void assertListRepresentation(List<CredentialSetDto> dtos) throws Exception {
		TypeReference<List<CredentialSetDto>> dtoType = new TypeReference<>() { };
		byte[] json = JSON.writerFor(dtoType).withView(RestApiView.class).writeValueAsBytes(dtos);
		byte[] xml = XML.writerFor(dtoType).withView(RestApiView.class).writeValueAsBytes(dtos);
		this.contract.checkBoth(json, xml);
		Assertions.assertTrue(new String(json).contains("SNMP v3"), "the items have their type");
		Assertions.assertTrue(new String(xml).contains("type=\"SNMP v3\""), "the items have their type");
	}

	@Test
	@DisplayName("Every kind of credential set has the same JSON and XML representation as the entity (single and in a list)")
	void representation() throws Exception {
		for (List<DeviceCredentialSet> credentialSets : List.of(localCredentialSets(), vaultCredentialSets())) {
			for (DeviceCredentialSet credentialSet : credentialSets) {
				this.assertRepresentation(CredentialSetMapper.toDto(credentialSet));
			}
			// The list endpoint answers an ArrayList, which XML publishes as such, with the type of its items
			this.assertListRepresentation(new ArrayList<>(CredentialSetMapper.toDtos(credentialSets)));
		}
		// Make sure the check is meaningful
		StringBuilder all = new StringBuilder();
		for (CredentialSetDto dto : CredentialSetMapper.toDtos(vaultCredentialSets())) {
			all.append(new String(json(dto)));
		}
		String text = all.toString();
		Assertions.assertTrue(text.contains("\"type\":\"SNMP v3\""), text);
		Assertions.assertTrue(text.contains("\"authKeyVaultPath\":\"netshot/v3/auth\""), text);
		Assertions.assertTrue(text.contains("\"privateKeyVaultInstanceId\":13"), text);
		Assertions.assertTrue(text.contains("\"deviceSpecific\":true"), text);
		Assertions.assertTrue(new String(xml(CredentialSetMapper.toDto(localCredentialSets().get(3)))).startsWith("<DeviceSshAccount type=\"SSH\">"));
	}

	@Test
	@DisplayName("A credential set without any value has the same representation as the entity")
	void emptyRepresentation() throws Exception {
		for (DeviceCredentialSet credentialSet : List.of(new DeviceSnmpv1Community(null, null), new DeviceSnmpv3Community(null, "n", null, null, null, null, null),
			new DeviceSshAccount(null, null, null, "n"), new DeviceSshKeyAccount(null, null, null, null, "n"), new DeviceHttpAccount(null, null, "n"))) {
			this.assertRepresentation(CredentialSetMapper.toDto(credentialSet));
		}
	}

	@Test
	@DisplayName("No secret value ever appears in the JSON, XML or YAML representation, whatever the kind of credential set")
	void secretsAreNeverWritten() throws Exception {
		for (List<DeviceCredentialSet> credentialSets : List.of(localCredentialSets(), vaultCredentialSets())) {
			Assertions.assertFalse(SECRETS.isEmpty());
			List<CredentialSetDto> dtos = CredentialSetMapper.toDtos(credentialSets);
			List<Object> outputs = new ArrayList<>(dtos);
			outputs.add(dtos);
			for (Object output : outputs) {
				for (byte[] bytes : List.of(json(output), xml(output), yaml(output))) {
					String text = new String(bytes);
					for (String secret : SECRETS) {
						Assertions.assertFalse(text.contains(secret), "secret " + secret + " written in " + text);
					}
				}
			}
			// What is a secret is written as the mask, and only when there is a value
			String text = new String(json(CredentialSetMapper.toDto(credentialSets.get(3))));
			Assertions.assertTrue(text.contains("\"password\":\"=\""), text);
			Assertions.assertTrue(text.contains("\"superPassword\":\"=\""), text);
			Assertions.assertTrue(text.contains("\"username\":\"sshuser\""), "the username is not a secret: " + text);
		}
		String empty = new String(json(CredentialSetMapper.toDto(new DeviceSshAccount(null, null, null, "n"))));
		Assertions.assertTrue(empty.contains("\"password\":null"), "a missing secret stays null: " + empty);
	}

	@Test
	@DisplayName("A secret read as = means unchanged (null), for every secret of every kind")
	void maskedSecretsAreRead() throws Exception {
		String[][] bodies = {
			{"SNMP v1", "community"}, {"SNMP v2", "community"}, {"SNMP v3", "community"}, {"SNMP v3", "authKey"}, {"SNMP v3", "privKey"},
			{"SSH", "password"}, {"SSH", "superPassword"}, {"SSH Key", "password"}, {"SSH Key", "superPassword"}, {"SSH Key", "privateKey"},
			{"Telnet", "password"}, {"Telnet", "superPassword"}, {"HTTP", "password"},
		};
		for (String[] body : bodies) {
			String text = "{\"type\": \"%s\", \"name\": \"n\", \"%s\": \"=\"}".formatted(body[0], body[1]);
			DeviceCredentialSet actual = CredentialSetMapper.toEntity(JSON.readerFor(CredentialSetDto.class).withView(RestApiView.class).readValue(text));
			Assertions.assertNull(describe(actual).get(body[1]), text);
			String set = "{\"type\": \"%s\", \"name\": \"n\", \"%s\": \"value\"}".formatted(body[0], body[1]);
			DeviceCredentialSet actualSet = CredentialSetMapper.toEntity(JSON.readerFor(CredentialSetDto.class).withView(RestApiView.class).readValue(set));
			Assertions.assertEquals("value", describe(actualSet).get(body[1]), set);
		}
	}

	// ------------------------------------------------------------------
	// Requests
	// ------------------------------------------------------------------

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
			else if (value instanceof Domain domain) {
				// Only the identity of the domain is read
				Map<String, Object> reference = new TreeMap<>();
				reference.put("id", domain.getId());
				reference.put("name", domain.getName());
				reference.put("description", domain.getDescription());
				description.put(property.getName(), reference);
			}
		}
		return description;
	}

	private static final List<String> BODIES = List.of(
		"{\"type\": \"SNMP v1\", \"name\": \"a\", \"community\": \"c\", \"communityVaultInstanceId\": 3, \"communityVaultPath\": \"p\"}",
		"{\"type\": \"SNMP v2\", \"id\": 7, \"name\": \"a\", \"community\": \"c\", \"deviceSpecific\": true, \"mgmtDomain\": {\"id\": 2}}",
		"{\"type\": \"SNMP v3\", \"name\": \"a\", \"community\": \"c\", \"username\": \"u\", \"authType\": \"MD5\", \"authKey\": \"ak\","
			+ " \"privType\": \"DES\", \"privKey\": \"pk\", \"usernameVaultInstanceId\": 1, \"usernameVaultPath\": \"u\","
			+ " \"authKeyVaultInstanceId\": 2, \"authKeyVaultPath\": \"a\", \"privKeyVaultInstanceId\": 3, \"privKeyVaultPath\": \"p\"}",
		"{\"type\": \"SNMP v3\", \"name\": \"a\"}",
		"{\"type\": \"SNMP v3\", \"name\": \"a\", \"authType\": null, \"privType\": null}",
		"{\"type\": \"SSH\", \"name\": \"a\", \"username\": \"u\", \"password\": \"p\", \"superPassword\": \"s\","
			+ " \"usernameVaultInstanceId\": 1, \"usernameVaultPath\": \"u\", \"passwordVaultInstanceId\": 2, \"passwordVaultPath\": \"p\","
			+ " \"superPasswordVaultInstanceId\": 3, \"superPasswordVaultPath\": \"s\"}",
		"{\"type\": \"SSH Key\", \"name\": \"a\", \"username\": \"u\", \"privateKey\": \"k\", \"password\": \"p\", \"superPassword\": \"=\","
			+ " \"privateKeyVaultInstanceId\": 4, \"privateKeyVaultPath\": \"k\"}",
		"{\"type\": \"Telnet\", \"name\": \"a\", \"username\": \"u\", \"password\": \"p\", \"superPassword\": \"s\"}",
		"{\"type\": \"HTTP\", \"name\": \"a\", \"username\": \"u\", \"password\": \"p\", \"usernameVaultInstanceId\": 5, \"usernameVaultPath\": \"u\"}",
		"{\"type\": \"SSH\"}",
		"{\"type\": \"SSH\", \"name\": null, \"username\": null, \"mgmtDomain\": null, \"deviceSpecific\": null}",
		// Known properties of the entities which were never part of the representation: silently ignored
		"{\"type\": \"SSH\", \"name\": \"a\", \"version\": 4, \"changeDate\": 1600000000000, \"usernameSecret\": {\"localValue\": \"x\"},"
			+ " \"passwordSecret\": {}, \"superPasswordSecret\": null}",
		"{\"type\": \"SSH Key\", \"name\": \"a\", \"privateKeySecret\": {}}",
		"{\"type\": \"SNMP v3\", \"name\": \"a\", \"communitySecret\": {}, \"authKeySecret\": {}, \"privKeySecret\": {}}",
		"{\"type\": \"SNMP v1\", \"name\": \"a\", \"communitySecret\": {}}",
		"{\"type\": \"SSH\", \"name\": \"a\", \"mgmtDomain\": {\"id\": 2, \"name\": \"n\", \"description\": \"d\", \"changeDate\": 5}}",
		// Refused
		"{\"type\": \"SSH\", \"name\": \"a\", \"unknown\": 1}",
		"{\"type\": \"SSH\", \"name\": \"a\", \"mgmtDomain\": {\"id\": 2, \"unknown\": 1}}",
		"{\"type\": \"SSH\", \"name\": \"a\", \"deviceSpecific\": \"yes\"}",
		"{\"type\": \"SNMP v3\", \"name\": \"a\", \"authType\": \"NOPE\"}",
		"{\"type\": \"SSH\", \"name\": \"a\", \"id\": \"abc\"}",
		"{\"type\": \"Unknown\", \"name\": \"a\"}",
		"{\"name\": \"a\"}",
		"[]",
		"\"text\"");

	@Test
	@DisplayName("The requests are read like the entities used to be (values, defaults, ignored and refused properties)")
	void requestsAreReadLikeTheEntity() throws Exception {
		int refused = 0;
		int index = 0;
		for (String body : BODIES) {
			index++;
			DeviceCredentialSet actual = null;
			try {
				actual = CredentialSetMapper.toEntity(JSON.readerFor(CredentialSetDto.class).withView(RestApiView.class).readValue(body));
			}
			catch (JsonProcessingException e) {
				// Refused
			}
			this.contract.checkText("request-" + index, DtoContract.render(actual, actual == null ? null : describe(actual)));
			if (actual == null) {
				refused++;
			}
		}
		Assertions.assertTrue(refused >= 9, "the bodies which must be refused are refused");
	}

	@Test
	@DisplayName("A credential set read from a list is read like the entities used to be")
	void listsAreRead() throws Exception {
		String body = "[" + String.join(",", BODIES.subList(0, 9)) + "]";
		List<CredentialSetDto> dtos = JSON.readerFor(new TypeReference<List<CredentialSetDto>>() { })
			.withView(RestApiView.class).readValue(body);
		Assertions.assertEquals(9, dtos.size());
		for (int i = 0; i < dtos.size(); i++) {
			DeviceCredentialSet actual = CredentialSetMapper.toEntity(dtos.get(i));
			this.contract.checkText("request-" + (i + 1), DtoContract.render(actual, describe(actual)));
		}
	}
}
