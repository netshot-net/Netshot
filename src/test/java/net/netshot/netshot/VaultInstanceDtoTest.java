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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import net.netshot.netshot.api.rest.v0.vault.VaultInstanceDto;
import net.netshot.netshot.api.rest.v0.vault.VaultInstanceMapper;
import net.netshot.netshot.api.rest.v0.vault.VaultInstanceRequest;
import net.netshot.netshot.platform.rest.RestViews.RestApiView;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;
import net.netshot.netshot.vault.VaultInstance;

/**
 * Checks that the Vault instance DTOs and their mapper are a faithful replacement of the entities
 * the REST API used to read and write: same representation in JSON and XML, no secret ever written,
 * requests read like they used to be (defaults for absent properties, {@code =} for unchanged secrets).
 */
class VaultInstanceDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder()
		.disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
	private static final XmlMapper XML = XmlMapper.builder()
		.disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();
	private static final YAMLMapper YAML = YAMLMapper.builder()
		.disable(MapperFeature.DEFAULT_VIEW_INCLUSION).build();

	private static final String APPROLE_SECRET = "S3CRET-approle-secret-id";
	private static final String JWT_SECRET = "S3CRET-jwt-client-secret";

	private final ApiContractGolden golden = new ApiContractGolden(0);

	private static HashicorpVaultKv2Instance full() {
		HashicorpVaultKv2Instance instance = new HashicorpVaultKv2Instance("Main vault");
		instance.setId(3);
		instance.setChangeDate(new Date(1_600_000_000_000L));
		instance.setBaseUrl("https://vault.example.net:8200");
		instance.setNamespace("netshot");
		instance.setHttpsCaTrustMode(HttpsCaTrustMode.CUSTOM_CA);
		instance.setHttpsCustomCaCertificate("-----BEGIN CERTIFICATE-----");
		instance.setKvMountPath("kv");
		instance.setAuthMethod(AuthMethod.JWT);
		instance.setAppRoleMountPath("approle-2");
		instance.setAppRoleId("role-1234");
		instance.setAppRoleSecretId(APPROLE_SECRET);
		instance.setJwtMountPath("jwt-2");
		instance.setJwtIdpTokenEndpoint("https://idp.example.net/token");
		instance.setJwtClientId("netshot");
		instance.setJwtClientSecret(JWT_SECRET);
		instance.setJwtVaultRole("netshot-role");
		instance.setJwtScope("vault");
		return instance;
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

	private void assertSameRepresentation(Object entity, Object dto) throws Exception {
		String expectedJson = this.golden.canonicalize(ApiContractGolden.Format.JSON, json(entity), ApiContractGolden.Transform.NONE, false);
		String actualJson = this.golden.canonicalize(ApiContractGolden.Format.JSON, json(dto), ApiContractGolden.Transform.NONE, false);
		Assertions.assertEquals(expectedJson, actualJson, "JSON");
		String expectedXml = this.golden.canonicalize(ApiContractGolden.Format.XML, xml(entity), ApiContractGolden.Transform.NONE, false);
		String actualXml = this.golden.canonicalize(ApiContractGolden.Format.XML, xml(dto), ApiContractGolden.Transform.NONE, false);
		Assertions.assertEquals(expectedXml, actualXml, "XML");
	}

	@Test
	@DisplayName("A Vault instance has the same JSON and XML representation as the entity, alone and in a list")
	void representation() throws Exception {
		HashicorpVaultKv2Instance full = full();
		this.assertSameRepresentation(full, VaultInstanceMapper.toDto(full));
		HashicorpVaultKv2Instance bare = new HashicorpVaultKv2Instance("Bare");
		this.assertSameRepresentation(bare, VaultInstanceMapper.toDto(bare));

		List<VaultInstance> entities = new ArrayList<>(List.of(full, bare));
		TypeReference<List<VaultInstance>> entityType = new TypeReference<>() { };
		TypeReference<List<VaultInstanceDto>> dtoType = new TypeReference<>() { };
		String expectedJson = this.golden.canonicalize(ApiContractGolden.Format.JSON,
			JSON.writerFor(entityType).withView(RestApiView.class).writeValueAsBytes(entities), ApiContractGolden.Transform.NONE, false);
		String actualJson = this.golden.canonicalize(ApiContractGolden.Format.JSON,
			JSON.writerFor(dtoType).withView(RestApiView.class).writeValueAsBytes(VaultInstanceMapper.toDtos(entities)),
			ApiContractGolden.Transform.NONE, false);
		Assertions.assertEquals(expectedJson, actualJson, "JSON list");
		Assertions.assertTrue(actualJson.contains("Vault KV v2"));
		String expectedXml = this.golden.canonicalize(ApiContractGolden.Format.XML,
			XML.writerFor(entityType).withView(RestApiView.class).writeValueAsBytes(entities), ApiContractGolden.Transform.NONE, false);
		String actualXml = this.golden.canonicalize(ApiContractGolden.Format.XML,
			XML.writerFor(dtoType).withView(RestApiView.class).writeValueAsBytes(VaultInstanceMapper.toDtos(entities)),
			ApiContractGolden.Transform.NONE, false);
		Assertions.assertEquals(expectedXml, actualXml, "XML list");
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
		String bare = new String(json(VaultInstanceMapper.toDto(new HashicorpVaultKv2Instance("Bare"))));
		Assertions.assertTrue(bare.contains("\"appRoleSecretId\":null"), "a missing secret stays null: " + bare);
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
		for (String body : BODIES) {
			VaultInstance expected = null;
			try {
				expected = JSON.readerFor(VaultInstance.class).withView(RestApiView.class).readValue(body);
			}
			catch (JsonProcessingException e) {
				// Refused
			}
			VaultInstance actual = null;
			try {
				actual = VaultInstanceMapper.toEntity(JSON.readerFor(VaultInstanceRequest.class).withView(RestApiView.class).readValue(body));
			}
			catch (JsonProcessingException e) {
				// Refused
			}
			Assertions.assertEquals(expected == null, actual == null, "refused or accepted: " + body);
			if (expected == null) {
				refused++;
				continue;
			}
			Assertions.assertEquals(expected.getClass(), actual.getClass(), body);
			Assertions.assertEquals(describe(expected), describe(actual), body);
		}
		Assertions.assertTrue(refused >= 7, "the bodies which must be refused are refused");
	}

	@Test
	@DisplayName("A request has the defaults of the entity, and = is read as unchanged (null)")
	void requestDefaults() throws Exception {
		HashicorpVaultKv2Instance minimal = (HashicorpVaultKv2Instance) VaultInstanceMapper.toEntity(
			JSON.readerFor(VaultInstanceRequest.class).withView(RestApiView.class).readValue("{\"type\": \"Vault KV v2\", \"name\": \"n\"}"));
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, minimal.getHttpsCaTrustMode());
		Assertions.assertEquals("secret", minimal.getKvMountPath());
		Assertions.assertEquals("approle", minimal.getAppRoleMountPath());
		Assertions.assertEquals("jwt", minimal.getJwtMountPath());
		HashicorpVaultKv2Instance masked = (HashicorpVaultKv2Instance) VaultInstanceMapper.toEntity(JSON.readerFor(VaultInstanceRequest.class)
			.withView(RestApiView.class).readValue("{" + BASE + ", \"appRoleSecretId\": \"=\", \"jwtClientSecret\": \"=\"}"));
		Assertions.assertNull(masked.getAppRoleSecretId());
		Assertions.assertNull(masked.getJwtClientSecret());
		HashicorpVaultKv2Instance given = (HashicorpVaultKv2Instance) VaultInstanceMapper.toEntity(JSON.readerFor(VaultInstanceRequest.class)
			.withView(RestApiView.class).readValue("{" + BASE + ", \"appRoleSecretId\": \"new\"}"));
		Assertions.assertEquals("new", given.getAppRoleSecretId());
	}
}
