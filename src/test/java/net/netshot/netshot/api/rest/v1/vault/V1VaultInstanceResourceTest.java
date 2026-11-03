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
package net.netshot.netshot.api.rest.v1.vault;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;
import net.netshot.netshot.vault.VaultInstanceRepository;

/**
 * The Vault instances of the v1 API, over HTTP: the secrets are write-only (never in a response, kept when left
 * out, cleared with null), and the connection tests answer 200 with their outcome.
 */
@ApiTest
@DisplayName("v1 Vault instances")
@ResourceLock("DB")
@V1Endpoints({"GET /vault-instances", "GET /vault-instances/{id}", "POST /vault-instances", "PUT /vault-instances/{id}",
	"PATCH /vault-instances/{id}", "DELETE /vault-instances/{id}", "POST /vault-instances/{id}/test", "POST /vault-instances/test"})
public class V1VaultInstanceResourceTest {

	private static final String SECRET_ID = "app-role-SECRET-id";
	private static final String CLIENT_SECRET = "jwt-client-SECRET";

	private TestServer server;
	private V1Client admin;

	@BeforeEach
	void setUp(TestServer testServer) {
		this.server = testServer;
		this.admin = new V1Client(testServer, UiUser.LEVEL_ADMIN);
		TestWiring.get().transactions().run(dsl -> {
			dsl.deleteFrom(Tables.DEVICE_CREDENTIAL_SET).execute();
			dsl.deleteFrom(Tables.VAULT_INSTANCE).execute();
		});
	}

	private static String appRole(String name, String extra) {
		return ("{\"name\": \"%s\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"http://127.0.0.1:1\", \"authMethod\": \"APPROLE\", "
			+ "\"appRoleId\": \"role\", \"appRoleSecretId\": \"%s\"%s}").formatted(name, SECRET_ID, extra == null ? "" : ", " + extra);
	}

	private long create(String body) throws Exception {
		Answer answer = this.admin.post("/vault-instances", body);
		Assertions.assertEquals(201, answer.status(), answer.toString());
		this.assertNoSecret(answer);
		return answer.json().get("id").asLong();
	}

	private void assertNoSecret(Answer answer) {
		Assertions.assertFalse(answer.body().contains(SECRET_ID), "Secret in the response: " + answer.body());
		Assertions.assertFalse(answer.body().contains(CLIENT_SECRET), "Secret in the response: " + answer.body());
	}

	private static HashicorpVaultKv2Instance stored(long id) {
		return (HashicorpVaultKv2Instance) TestWiring.get().transactions().read(dsl -> new VaultInstanceRepository().find(dsl, id));
	}

	private static Set<String> names(JsonNode object) {
		Set<String> names = new TreeSet<>();
		object.fieldNames().forEachRemaining(names::add);
		return names;
	}

	private static List<String> values(JsonNode list, String property) {
		List<String> values = new ArrayList<>();
		list.get("results").forEach(item -> values.add(item.get(property).asText()));
		return values;
	}

	@Test
	@DisplayName("Create: 201, defaults, flags, no secret in the response")
	void create() throws Exception {
		Answer answer = this.admin.post("/vault-instances", appRole("Vault 1", "\"namespace\": \"team\""));
		Assertions.assertEquals(201, answer.status(), answer.toString());
		this.assertNoSecret(answer);
		JsonNode vault = answer.json();
		long id = vault.get("id").asLong();
		Assertions.assertEquals("/api/v1/vault-instances/" + id, answer.header("Location"));
		Assertions.assertEquals("\"0\"", answer.header("ETag"));
		Assertions.assertEquals("HASHICORP_KV2", vault.get("type").asText());
		Assertions.assertEquals("SYSTEM_TRUSTSTORE", vault.get("httpsCaTrustMode").asText());
		Assertions.assertEquals("secret", vault.get("kvMountPath").asText());
		Assertions.assertEquals("approle", vault.get("appRoleMountPath").asText());
		Assertions.assertEquals("team", vault.get("namespace").asText());
		Assertions.assertTrue(vault.get("hasAppRoleSecretId").asBoolean());
		Assertions.assertFalse(vault.get("hasJwtClientSecret").asBoolean());
		Assertions.assertFalse(vault.has("appRoleSecretId"));
		Assertions.assertFalse(vault.has("jwtClientSecret"));
		Assertions.assertEquals(SECRET_ID, stored(id).appRoleSecretId());

		Answer duplicate = this.admin.post("/vault-instances", appRole("Vault 1", null));
		Assertions.assertEquals(409, duplicate.status(), duplicate.toString());
		Assertions.assertEquals("NETSHOT_DUPLICATE_VAULT_INSTANCE", duplicate.json().get("code").asText());
	}

	@Test
	@DisplayName("Create: the properties the authentication method needs are required")
	void createValidation() throws Exception {
		String[] invalid = {
			"{\"name\": \"A\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"http://v\", \"authMethod\": \"APPROLE\", \"appRoleId\": \"r\"}|appRoleSecretId",
			"{\"name\": \"A\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"http://v\", \"authMethod\": \"APPROLE\", \"appRoleSecretId\": \"s\"}|appRoleId",
			"{\"name\": \"A\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"http://v\", \"authMethod\": \"JWT\", \"jwtIdpTokenEndpoint\": \"http://i\", "
				+ "\"jwtClientId\": \"c\", \"jwtVaultRole\": \"r\"}|jwtClientSecret",
			"{\"name\": \"A\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"http://v\", \"authMethod\": \"JWT\", \"jwtClientId\": \"c\", "
				+ "\"jwtClientSecret\": \"s\", \"jwtVaultRole\": \"r\"}|jwtIdpTokenEndpoint",
			"{\"name\": \"A\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"ftp://v\", \"authMethod\": \"APPROLE\", \"appRoleId\": \"r\", "
				+ "\"appRoleSecretId\": \"s\"}|baseUrl",
			"{\"name\": \"A\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"http://v\", \"authMethod\": \"APPROLE\", \"appRoleId\": \"r\", "
				+ "\"appRoleSecretId\": \"s\", \"httpsCaTrustMode\": \"CUSTOM_CA\"}|httpsCustomCaCertificate",
			"{\"name\": \"A\", \"type\": \"OTHER\", \"baseUrl\": \"http://v\", \"authMethod\": \"APPROLE\"}|type",
			"{\"name\": \"A\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"http://v\"}|authMethod",
			"{\"name\": \"A\", \"type\": \"HASHICORP_KV2\", \"authMethod\": \"APPROLE\"}|baseUrl",
			"{\"type\": \"HASHICORP_KV2\", \"baseUrl\": \"http://v\", \"authMethod\": \"APPROLE\"}|name",
			"{\"name\": \"A\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"http://v\", \"authMethod\": \"APPROLE\", \"color\": 1}|color",
			"{\"name\": \"A\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"http://v\", \"authMethod\": \"NONE\"}|authMethod",
		};
		for (String test : invalid) {
			String[] parts = test.split("\\|");
			Answer answer = this.admin.post("/vault-instances", parts[0]);
			Assertions.assertEquals(400, answer.status(), test + " -> " + answer);
			Assertions.assertEquals(parts[1], answer.json().get("errors").get(0).get("field").asText(), test + " -> " + answer);
		}
		Assertions.assertEquals(0, this.admin.get("/vault-instances").json().get("results").size());

		Answer jwt = this.admin.post("/vault-instances", ("{\"name\": \"JWT\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"https://vault:8200\", "
			+ "\"authMethod\": \"JWT\", \"jwtIdpTokenEndpoint\": \"https://idp/token\", \"jwtClientId\": \"c\", "
			+ "\"jwtClientSecret\": \"%s\", \"jwtVaultRole\": \"r\", \"jwtScope\": \"vault\"}").formatted(CLIENT_SECRET));
		Assertions.assertEquals(201, jwt.status(), jwt.toString());
		this.assertNoSecret(jwt);
		Assertions.assertTrue(jwt.json().get("hasJwtClientSecret").asBoolean());
		Assertions.assertFalse(jwt.json().get("hasAppRoleSecretId").asBoolean());
	}

	@Test
	@DisplayName("List: brief view, filters, search, sort; get and ETag")
	void list() throws Exception {
		long first = this.create(appRole("Alpha", null));
		this.create(appRole("Beta", "\"namespace\": \"ns\""));
		this.create(("{\"name\": \"Gamma\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"https://other:8200\", \"authMethod\": \"JWT\", "
			+ "\"jwtIdpTokenEndpoint\": \"https://idp\", \"jwtClientId\": \"c\", \"jwtClientSecret\": \"%s\", \"jwtVaultRole\": \"r\"}")
			.formatted(CLIENT_SECRET));

		Answer all = this.admin.get("/vault-instances");
		Assertions.assertEquals(200, all.status(), all.toString());
		this.assertNoSecret(all);
		Assertions.assertEquals(List.of("Alpha", "Beta", "Gamma"), values(all.json(), "name"));
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "type", "baseUrl", "authMethod", "httpsCaTrustMode"), names(all.json().get("results").get(0)));
		Assertions.assertEquals(3, all.json().get("results").size());
		Assertions.assertEquals(List.of("Gamma"), values(this.admin.get("/vault-instances?authMethod=JWT").json(), "name"));
		Assertions.assertEquals(List.of("Alpha", "Beta", "Gamma"), values(this.admin.get("/vault-instances?type=HASHICORP_KV2").json(), "name"));
		Assertions.assertEquals(List.of("Beta"), values(this.admin.get("/vault-instances?namespace=ns").json(), "name"));
		Assertions.assertEquals(List.of("Gamma"), values(this.admin.get("/vault-instances?baseUrl__ic=other").json(), "name"));
		Assertions.assertEquals(List.of("Beta"), values(this.admin.get("/vault-instances?q=bet").json(), "name"));
		Assertions.assertEquals(List.of("Gamma", "Beta", "Alpha"), values(this.admin.get("/vault-instances?sort=-name").json(), "name"));
		for (String query : List.of("type=FOO", "authMethod=NONE", "appRoleSecretId=x", "sort=appRoleId")) {
			Assertions.assertEquals(400, this.admin.get("/vault-instances?" + query).status(), query);
		}

		Answer get = this.admin.get("/vault-instances/" + first);
		Assertions.assertEquals(200, get.status());
		this.assertNoSecret(get);
		Assertions.assertEquals("\"0\"", get.header("ETag"));
		Assertions.assertEquals(304, this.admin.get("/vault-instances/" + first, "If-None-Match", "\"0\"").status());
		Assertions.assertEquals(Set.of("id", "url", "display", "version", "changeDate", "name", "type", "baseUrl", "namespace",
			"httpsCaTrustMode", "httpsCustomCaCertificate", "kvMountPath", "authMethod", "appRoleMountPath", "appRoleId",
			"hasAppRoleSecretId", "jwtMountPath", "jwtIdpTokenEndpoint", "jwtClientId", "hasJwtClientSecret", "jwtVaultRole",
			"jwtScope"), names(get.json()));
		Answer missing = this.admin.get("/vault-instances/" + TestServer.UNKNOWN_ID);
		Assertions.assertEquals(404, missing.status());
		Assertions.assertEquals("NETSHOT_VAULT_INSTANCE_NOT_FOUND", missing.json().get("code").asText());
	}

	@Test
	@DisplayName("PUT without the secrets keeps them, null clears them (refused if needed)")
	void replace() throws Exception {
		long id = this.create(appRole("Main", null));
		String withoutSecret = "{\"name\": \"Renamed\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"http://127.0.0.1:1\", "
			+ "\"authMethod\": \"APPROLE\", \"appRoleId\": \"role2\"}";
		Answer replaced = this.admin.put("/vault-instances/" + id, withoutSecret, "If-Match", "\"0\"");
		Assertions.assertEquals(200, replaced.status(), replaced.toString());
		this.assertNoSecret(replaced);
		Assertions.assertEquals("Renamed", replaced.json().get("name").asText());
		Assertions.assertEquals(1, replaced.json().get("version").asInt());
		Assertions.assertEquals("\"1\"", replaced.header("ETag"));
		Assertions.assertEquals("role2", stored(id).appRoleId());
		Assertions.assertEquals(SECRET_ID, stored(id).appRoleSecretId());

		// A new value replaces the stored one
		Assertions.assertEquals(200, this.admin.put("/vault-instances/" + id, withoutSecret.replace("\"appRoleId\"",
			"\"appRoleSecretId\": \"new-id\", \"appRoleId\"")).status());
		Assertions.assertEquals("new-id", stored(id).appRoleSecretId());

		// null clears, which APPROLE refuses
		Answer cleared = this.admin.put("/vault-instances/" + id, withoutSecret.replace("\"appRoleId\"", "\"appRoleSecretId\": null, \"appRoleId\""));
		Assertions.assertEquals(400, cleared.status(), cleared.toString());
		Assertions.assertEquals("appRoleSecretId", cleared.json().get("errors").get(0).get("field").asText());
		Assertions.assertEquals("new-id", stored(id).appRoleSecretId());

		// Switching to JWT: the AppRole secret is cleared with null, the JWT ones are required
		String jwt = "{\"name\": \"Renamed\", \"type\": \"HASHICORP_KV2\", \"baseUrl\": \"http://127.0.0.1:1\", \"authMethod\": \"JWT\", "
			+ "\"appRoleSecretId\": null, \"jwtIdpTokenEndpoint\": \"http://idp\", \"jwtClientId\": \"c\", \"jwtVaultRole\": \"r\"%s}";
		Assertions.assertEquals(400, this.admin.put("/vault-instances/" + id, jwt.formatted("")).status());
		Answer switched = this.admin.put("/vault-instances/" + id, jwt.formatted(", \"jwtClientSecret\": \"" + CLIENT_SECRET + "\""));
		Assertions.assertEquals(200, switched.status(), switched.toString());
		this.assertNoSecret(switched);
		Assertions.assertTrue(switched.json().get("hasJwtClientSecret").asBoolean());
		Assertions.assertFalse(switched.json().get("hasAppRoleSecretId").asBoolean());
		Assertions.assertNull(stored(id).appRoleSecretId());
		Assertions.assertEquals(CLIENT_SECRET, stored(id).jwtClientSecret());

		Answer stale = this.admin.put("/vault-instances/" + id, withoutSecret, "If-Match", "\"0\"");
		Assertions.assertEquals(412, stale.status(), stale.toString());
		Assertions.assertEquals("NETSHOT_VERSION_MISMATCH", stale.json().get("code").asText());
		Assertions.assertEquals(404, this.admin.put("/vault-instances/" + TestServer.UNKNOWN_ID, withoutSecret).status());
	}

	@Test
	@DisplayName("PATCH merges the given properties and keeps the secrets")
	void patch() throws Exception {
		long id = this.create(appRole("Main", null));
		Answer patched = this.admin.patch("/vault-instances/" + id, "{\"namespace\": \"ns\", \"kvMountPath\": \"kv\"}");
		Assertions.assertEquals(200, patched.status(), patched.toString());
		this.assertNoSecret(patched);
		Assertions.assertEquals("ns", patched.json().get("namespace").asText());
		Assertions.assertEquals("kv", patched.json().get("kvMountPath").asText());
		Assertions.assertEquals("role", patched.json().get("appRoleId").asText());
		Assertions.assertEquals(SECRET_ID, stored(id).appRoleSecretId());

		Answer secret = this.admin.patch("/vault-instances/" + id, "{\"appRoleSecretId\": \"patched-id\"}");
		Assertions.assertEquals(200, secret.status(), secret.toString());
		Assertions.assertEquals("patched-id", stored(id).appRoleSecretId());
		Assertions.assertEquals(400, this.admin.patch("/vault-instances/" + id, "{\"appRoleSecretId\": null}").status());
		Assertions.assertEquals("patched-id", stored(id).appRoleSecretId());
		Assertions.assertEquals(200, this.admin.patch("/vault-instances/" + id, "{\"namespace\": null}").status());
		Assertions.assertNull(stored(id).namespace());

		Assertions.assertEquals(412, this.admin.patch("/vault-instances/" + id, "{\"name\": \"X\"}", "If-Match", "\"0\"").status());
		Assertions.assertEquals(400, this.admin.patch("/vault-instances/" + id, "{\"color\": \"red\"}").status());
		Assertions.assertEquals(400, this.admin.patch("/vault-instances/" + id, "{\"type\": \"OTHER\"}").status());
		Assertions.assertEquals(404, this.admin.patch("/vault-instances/" + TestServer.UNKNOWN_ID, "{\"name\": \"X\"}").status());
	}

	@Test
	@DisplayName("DELETE: 204, If-Match, 404")
	void delete() throws Exception {
		long id = this.create(appRole("Main", null));
		Assertions.assertEquals(412, this.admin.delete("/vault-instances/" + id, "If-Match", "\"4\"").status());
		Assertions.assertEquals(204, this.admin.delete("/vault-instances/" + id, "If-Match", "\"0\"").status());
		Answer again = this.admin.delete("/vault-instances/" + id);
		Assertions.assertEquals(404, again.status());
		Assertions.assertEquals("NETSHOT_VAULT_INSTANCE_NOT_FOUND", again.json().get("code").asText());
	}

	@Test
	@DisplayName("Connection tests answer 200 with the outcome")
	void tests() throws Exception {
		long id = this.create(appRole("Main", null));
		// Nothing listens on the port: the login fails, which is an outcome
		Answer saved = this.admin.post("/vault-instances/" + id + "/test", null);
		Assertions.assertEquals(200, saved.status(), saved.toString());
		Assertions.assertFalse(saved.json().get("success").asBoolean());
		Assertions.assertFalse(saved.json().get("message").asText().isBlank());
		this.assertNoSecret(saved);

		Answer unsaved = this.admin.post("/vault-instances/test", appRole("Not saved", null));
		Assertions.assertEquals(200, unsaved.status(), unsaved.toString());
		Assertions.assertFalse(unsaved.json().get("success").asBoolean());
		this.assertNoSecret(unsaved);
		Assertions.assertEquals(1, this.admin.get("/vault-instances").json().get("results").size());

		// An invalid request is an error, a missing instance is a 404
		Assertions.assertEquals(400, this.admin.post("/vault-instances/test", "{\"name\": \"x\"}").status());
		Assertions.assertEquals(400, this.admin.post("/vault-instances/test", "{\"name\": \"x\", \"type\": \"HASHICORP_KV2\", "
			+ "\"baseUrl\": \"http://v\", \"authMethod\": \"APPROLE\", \"appRoleId\": \"r\"}").status());
		Assertions.assertEquals(404, this.admin.post("/vault-instances/" + TestServer.UNKNOWN_ID + "/test", null).status());
	}

	@Test
	@DisplayName("Authentication and permissions")
	void permissions() throws Exception {
		long id = this.create(appRole("Main", null));
		Assertions.assertEquals(401, new V1Client(this.server, null).get("/vault-instances").status());
		V1Client readOnly = new V1Client(this.server, UiUser.LEVEL_READONLY);
		Assertions.assertEquals(200, readOnly.get("/vault-instances").status());
		Assertions.assertEquals(200, readOnly.get("/vault-instances/" + id).status());
		Assertions.assertEquals(403, readOnly.post("/vault-instances", appRole("Other", null)).status());
		Assertions.assertEquals(403, readOnly.put("/vault-instances/" + id, appRole("Other", null)).status());
		Assertions.assertEquals(403, readOnly.patch("/vault-instances/" + id, "{\"name\": \"Other\"}").status());
		Assertions.assertEquals(403, readOnly.delete("/vault-instances/" + id).status());
		Assertions.assertEquals(403, readOnly.post("/vault-instances/" + id + "/test", null).status());
		Assertions.assertEquals(403, readOnly.post("/vault-instances/test", appRole("Other", null)).status());
	}
}
