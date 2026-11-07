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
package net.netshot.netshot.api.rest.v0.vault;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
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

import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.contract.DtoContract;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;
import net.netshot.netshot.vault.VaultInstance;

/**
 * Checks that the Vault instance DTOs and their mapper are a faithful replacement of the entities
 * the REST API used to read and write: same representation in JSON and XML, no secret ever written,
 * requests read like they used to be (defaults for absent properties, {@code =} for unchanged secrets).
 */
@UnitTest
class VaultInstanceDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();
	private static final YAMLMapper YAML = YAMLMapper.builder().build();

	private static final String APPROLE_SECRET = "S3CRET-approle-secret-id";
	private static final String JWT_SECRET = "S3CRET-jwt-client-secret";

	@RegisterExtension
	private final DtoContract contract = new DtoContract(VaultInstanceDtoTest.class);

	private static HashicorpVaultKv2Instance full() {
		HashicorpVaultKv2Instance instance = HashicorpVaultKv2Instance.builder("Main vault")
			.id(3)
			.changeDate(Instant.ofEpochMilli(1_600_000_000_000L))
			.baseUrl("https://vault.example.net:8200")
			.namespace("netshot")
			.httpsCaTrustMode(HttpsCaTrustMode.CUSTOM_CA)
			.httpsCustomCaCertificate("-----BEGIN CERTIFICATE-----")
			.kvMountPath("kv")
			.authMethod(AuthMethod.JWT)
			.appRoleMountPath("approle-2")
			.appRoleId("role-1234")
			.appRoleSecretId(Secret.local(APPROLE_SECRET))
			.jwtMountPath("jwt-2")
			.jwtIdpTokenEndpoint("https://idp.example.net/token")
			.jwtClientId("netshot")
			.jwtClientSecret(Secret.local(JWT_SECRET))
			.jwtVaultRole("netshot-role")
			.jwtScope("vault")
			.build();
		return instance;
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

	@Test
	@DisplayName("A Vault instance has the same JSON and XML representation as the entity, alone and in a list")
	void representation() throws Exception {
		HashicorpVaultKv2Instance full = full();
		this.assertRepresentation(VaultInstanceMapper.toDto(full));
		HashicorpVaultKv2Instance bare = HashicorpVaultKv2Instance.builder("Bare").build();
		this.assertRepresentation(VaultInstanceMapper.toDto(bare));

		List<VaultInstance> entities = new ArrayList<>(List.of(full, bare));
		TypeReference<List<VaultInstanceDto>> dtoType = new TypeReference<>() { };
		byte[] json = JSON.writerFor(dtoType).writeValueAsBytes(VaultInstanceMapper.toDtos(entities));
		byte[] xml = XML.writerFor(dtoType).writeValueAsBytes(VaultInstanceMapper.toDtos(entities));
		this.contract.checkBoth(json, xml);
		Assertions.assertTrue(new String(json).contains("Vault KV v2"));
		Assertions.assertTrue(new String(xml(VaultInstanceMapper.toDto(full))).startsWith("<HashicorpVaultKv2Instance type=\"Vault KV v2\">"));
	}

	@Test
	@DisplayName("No secret value ever appears in the JSON, XML or YAML representation")
	void secretsAreNeverWritten() throws Exception {
		VaultInstanceDto dto = VaultInstanceMapper.toDto(full());
		for (byte[] bytes : List.of(json(dto), xml(dto), yaml(dto), json(List.of(dto)), xml(List.of(dto)), yaml(List.of(dto)))) {
			String text = new String(bytes);
			Assertions.assertFalse(text.contains(APPROLE_SECRET), text);
			Assertions.assertFalse(text.contains(JWT_SECRET), text);
		}
		String text = new String(json(dto));
		Assertions.assertTrue(text.contains("\"appRoleSecretId\":\"=\""), text);
		Assertions.assertTrue(text.contains("\"jwtClientSecret\":\"=\""), text);
		Assertions.assertTrue(text.contains("\"appRoleId\":\"role-1234\""), "the role ID is not a secret: " + text);
		String bare = new String(json(VaultInstanceMapper.toDto(HashicorpVaultKv2Instance.builder("Bare").build())));
		Assertions.assertTrue(bare.contains("\"appRoleSecretId\":null"), "a missing secret stays null: " + bare);
	}

	// ------------------------------------------------------------------
	// Requests
	// ------------------------------------------------------------------

	private static Map<String, Object> describe(Object bean) throws IntrospectionException, ReflectiveOperationException {
		Map<String, Object> description = new TreeMap<>();
		if (bean instanceof Record record) {
			// The components of the record are the properties of the former entity
			for (RecordComponent component : record.getClass().getRecordComponents()) {
				Object value = component.getAccessor().invoke(record);
				description.put(component.getName(), value instanceof LocalSecret secret ? secret.reveal() : value);
			}
			return description;
		}
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
		}
		return description;
	}

	private static final String BASE = "\"type\": \"Vault KV v2\", \"name\": \"n\", \"baseUrl\": \"https://x\", \"authMethod\": \"APPROLE\"";

	private static final List<String> BODIES = List.of(
		"{\"type\": \"Vault KV v2\", \"id\": 9, \"name\": \"full\", \"baseUrl\": \"https://v\", \"namespace\": \"ns\","
			+ " \"httpsCaTrustMode\": \"CUSTOM_CA\", \"httpsCustomCaCertificate\": \"PEM\", \"kvMountPath\": \"kv2\", \"authMethod\": \"JWT\","
			+ " \"appRoleMountPath\": \"ar\", \"appRoleId\": \"rid\", \"appRoleSecretId\": \"rsec\", \"jwtMountPath\": \"jw\","
			+ " \"jwtIdpTokenEndpoint\": \"https://idp\", \"jwtClientId\": \"cid\", \"jwtClientSecret\": \"csec\","
			+ " \"jwtVaultRole\": \"role\", \"jwtScope\": \"scp\"}",
		"{\"type\": \"Vault KV v2\", \"name\": \"min\"}",
		"{" + BASE + "}",
		// Default values when absent, but not when explicitly null
		"{" + BASE + ", \"httpsCaTrustMode\": null}",
		"{" + BASE + ", \"kvMountPath\": null, \"appRoleMountPath\": null, \"jwtMountPath\": null}",
		"{" + BASE + ", \"kvMountPath\": \"\"}",
		// Unchanged secrets
		"{" + BASE + ", \"appRoleSecretId\": \"=\", \"jwtClientSecret\": \"=\"}",
		"{" + BASE + ", \"appRoleSecretId\": \"new\", \"jwtClientSecret\": null}",
		"{" + BASE + ", \"appRoleSecretId\": \"==\"}",
		// Known properties of the entity which were never part of the representation: silently ignored
		"{" + BASE + ", \"version\": 3, \"changeDate\": 5}",
		// Refused
		"{" + BASE + ", \"unknown\": 1}",
		"{" + BASE + ", \"authMethod\": \"XXX\"}",
		"{" + BASE + ", \"httpsCaTrustMode\": \"XXX\"}",
		"{" + BASE + ", \"id\": \"abc\"}",
		"{\"type\": \"Other\", \"name\": \"n\"}",
		"{\"name\": \"n\"}",
		"[]",
		"\"text\"");

	@Test
	@DisplayName("The requests are read like the entities used to be (values, defaults, unchanged secrets, ignored and refused properties)")
	void requestsAreReadLikeTheEntity() throws Exception {
		int refused = 0;
		int index = 0;
		for (String body : BODIES) {
			index++;
			VaultInstance actual = null;
			try {
				actual = VaultInstanceMapper.toEntity(JSON.readerFor(VaultInstanceRequest.class).readValue(body));
			}
			catch (JsonProcessingException e) {
				// Refused
			}
			this.contract.checkText("request-" + index, DtoContract.render(actual, actual == null ? null : describe(actual)));
			if (actual == null) {
				refused++;
			}
		}
		Assertions.assertTrue(refused >= 7, "the bodies which must be refused are refused");
	}

	@Test
	@DisplayName("A request has the defaults of the entity, and = is read as unchanged (null)")
	void requestDefaults() throws Exception {
		HashicorpVaultKv2Instance minimal = (HashicorpVaultKv2Instance) VaultInstanceMapper.toEntity(
			JSON.readerFor(VaultInstanceRequest.class).readValue("{\"type\": \"Vault KV v2\", \"name\": \"n\"}"));
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, minimal.httpsCaTrustMode());
		Assertions.assertEquals("secret", minimal.kvMountPath());
		Assertions.assertEquals("approle", minimal.appRoleMountPath());
		Assertions.assertEquals("jwt", minimal.jwtMountPath());
		HashicorpVaultKv2Instance masked = (HashicorpVaultKv2Instance) VaultInstanceMapper.toEntity(JSON.readerFor(VaultInstanceRequest.class)
			.readValue("{" + BASE + ", \"appRoleSecretId\": \"=\", \"jwtClientSecret\": \"=\"}"));
		Assertions.assertNull(masked.appRoleSecretId());
		Assertions.assertNull(masked.jwtClientSecret());
		HashicorpVaultKv2Instance given = (HashicorpVaultKv2Instance) VaultInstanceMapper.toEntity(JSON.readerFor(VaultInstanceRequest.class)
			.readValue("{" + BASE + ", \"appRoleSecretId\": \"new\"}"));
		Assertions.assertEquals(Secret.local("new"), given.appRoleSecretId());
	}
}
