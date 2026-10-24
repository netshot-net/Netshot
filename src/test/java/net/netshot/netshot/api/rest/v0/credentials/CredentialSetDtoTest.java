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
package net.netshot.netshot.api.rest.v0.credentials;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
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
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

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
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.contract.DtoContract;

/**
 * Checks that the credential set DTOs and their mapper are a faithful replacement of the entities
 * the REST API used to read and write: the representation of every kind of credential set is
 * the same, in JSON and XML, as the one of the entity with the same view; no secret ever appears in
 * it; the requests are read like they used to be.
 */
@UnitTest
class CredentialSetDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();
	private static final YAMLMapper YAML = YAMLMapper.builder().build();

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
		return Domain.builder().id(1).name("Paris DC").description("Paris data center").changeDate(Instant.ofEpochMilli(1_600_000_000_000L))
			.server4Address(new Network4Address("10.255.0.1")).server6Address(new Network6Address("2001:db8::1")).build();
	}

	/**
	 * One credential set of each kind, with every field filled, secrets included.
	 * @return the credential sets
	 */
	private static List<DeviceCredentialSet> localCredentialSets() throws Exception {
		SECRETS.clear();
		List<DeviceCredentialSet> all = List.of(
			DeviceSnmpv1Community.builder().id(1).community(secret("v1-community")).name("SNMP v1").build(),
			DeviceSnmpv2cCommunity.builder().id(2).community(secret("v2-community")).name("SNMP v2").mgmtDomain(domain()).build(),
			DeviceSnmpv3Community.builder().id(3).community(secret("v3-context")).name("SNMP v3").username("snmpuser").authType(AuthProtocol.SHA)
				.authKey(secret("v3-auth")).privType(PrivProtocol.AES128).privKey(secret("v3-priv")).build(),
			DeviceSshAccount.builder().id(4).username("sshuser").password(secret("ssh-password")).superPassword(secret("ssh-super")).name("SSH").build(),
			DeviceSshKeyAccount.builder().id(5).username("keyuser").privateKey(secret("key-private")).password(secret("key-password"))
				.superPassword(secret("key-super")).name("SSH Key").mgmtDomain(domain()).build(),
			DeviceTelnetAccount.builder().id(6).username("telnetuser").password(secret("telnet-password")).superPassword(secret("telnet-super"))
				.name("Telnet").build(),
			DeviceHttpAccount.builder().id(7).username("httpuser").password(secret("http-password")).name("HTTP").build());
		return all;
	}

	/**
	 * The same kinds, with the fields held by a Vault instance.
	 * @return the credential sets
	 */
	private static List<DeviceCredentialSet> vaultCredentialSets() throws Exception {
		List<DeviceCredentialSet> local = localCredentialSets();
		DeviceSnmpv1Community v1 = (DeviceSnmpv1Community) local.get(0);
		DeviceSnmpv3Community v3 = (DeviceSnmpv3Community) local.get(2);
		DeviceSshKeyAccount key = (DeviceSshKeyAccount) local.get(4);
		DeviceHttpAccount http = (DeviceHttpAccount) local.get(6);
		List<DeviceCredentialSet> all = new ArrayList<>(local);
		all.set(0, v1.toBuilder().communityVaultInstanceId(11L).communityVaultPath("netshot/v1").build());
		all.set(1, local.get(1).withIdentity(local.get(1).id(), local.get(1).name(), true));
		all.set(2, v3.toBuilder()
			.communityVaultInstanceId(12L).communityVaultPath("netshot/v3/community")
			.usernameVaultInstanceId(12L).usernameVaultPath("netshot/v3/username")
			.authKeyVaultInstanceId(12L).authKeyVaultPath("netshot/v3/auth")
			.privKeyVaultInstanceId(12L).privKeyVaultPath("netshot/v3/priv")
			.build());
		all.set(4, key.toBuilder()
			.usernameVaultInstanceId(13L).usernameVaultPath("netshot/key/username")
			.passwordVaultInstanceId(13L).passwordVaultPath("netshot/key/password")
			.superPasswordVaultInstanceId(13L).superPasswordVaultPath("netshot/key/super")
			.privateKeyVaultInstanceId(13L).privateKeyVaultPath("netshot/key/private")
			.build());
		all.set(6, http.toBuilder().usernameVaultInstanceId(14L).usernameVaultPath("netshot/http/username").build());
		return all;
	}

	private static byte[] json(Object value) throws Exception {
		return JSON.writer().writeValueAsBytes(value);
	}

	private static byte[] xml(Object value) throws Exception {
		return XML.writer().writeValueAsBytes(value);
	}

	private static byte[] yaml(Object value) throws Exception {
		return YAML.writer().writeValueAsBytes(value);
	}

	private void assertRepresentation(Object dto) throws Exception {
		this.contract.checkBoth(json(dto), xml(dto));
	}

	private void assertListRepresentation(List<CredentialSetDto> dtos) throws Exception {
		TypeReference<List<CredentialSetDto>> dtoType = new TypeReference<>() { };
		byte[] json = JSON.writerFor(dtoType).writeValueAsBytes(dtos);
		byte[] xml = XML.writerFor(dtoType).writeValueAsBytes(dtos);
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
		for (DeviceCredentialSet credentialSet : List.of(DeviceSnmpv1Community.builder().community(null).name(null).build(), DeviceSnmpv3Community.builder().name("n").build(),
			DeviceSshAccount.builder().username(null).password(null).superPassword(null).name("n").build(), DeviceSshKeyAccount.builder().username(null).privateKey(null).password(null)
				.superPassword(null).name("n").build(), DeviceHttpAccount.builder().username(null).password(null).name("n").build())) {
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
		String empty = new String(json(CredentialSetMapper.toDto(DeviceSshAccount.builder().username(null).password(null).superPassword(null).name("n").build())));
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
			DeviceCredentialSet actual = CredentialSetMapper.toEntity(JSON.readerFor(CredentialSetDto.class).readValue(text));
			Assertions.assertNull(describe(actual).get(body[1]), text);
			String set = "{\"type\": \"%s\", \"name\": \"n\", \"%s\": \"value\"}".formatted(body[0], body[1]);
			DeviceCredentialSet actualSet = CredentialSetMapper.toEntity(JSON.readerFor(CredentialSetDto.class).readValue(set));
			Assertions.assertEquals("value", describe(actualSet).get(body[1]), set);
		}
	}

	// ------------------------------------------------------------------
	// Requests
	// ------------------------------------------------------------------

	/**
	 * Describes what a request was read as, the way the former entities exposed it (bean properties):
	 * the Vault instance given by a request was a pending one ({@code resolvedXxxVaultInstanceId}),
	 * the stored one ({@code xxxVaultInstanceId}) being unset.
	 *
	 * @param bean the credential set
	 * @return the property values, by property name
	 */
	private static Map<String, Object> describe(Object bean) throws ReflectiveOperationException {
		Map<String, Object> description = new TreeMap<>();
		for (RecordComponent component : bean.getClass().getRecordComponents()) {
			String name = component.getName();
			Object value = component.getAccessor().invoke(bean);
			if (name.endsWith("VaultInstanceId")) {
				description.put("resolved" + Character.toUpperCase(name.charAt(0)) + name.substring(1), value);
				description.put(name, null);
			}
			else if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
					|| value instanceof Enum || value instanceof Date) {
				description.put(name, value);
			}
			else if (value instanceof Instant instant) {
				description.put(name, Date.from(instant));
			}
			else if (value instanceof Domain domain) {
				// Only the identity of the domain is read
				Map<String, Object> reference = new TreeMap<>();
				reference.put("id", domain.id());
				reference.put("name", domain.name());
				reference.put("description", domain.description());
				description.put(name, reference);
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
				actual = CredentialSetMapper.toEntity(JSON.readerFor(CredentialSetDto.class).readValue(body));
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
			.readValue(body);
		Assertions.assertEquals(9, dtos.size());
		for (int i = 0; i < dtos.size(); i++) {
			DeviceCredentialSet actual = CredentialSetMapper.toEntity(dtos.get(i));
			this.contract.checkText("request-" + (i + 1), DtoContract.render(actual, describe(actual)));
		}
	}
}
