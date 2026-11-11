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
package net.netshot.netshot.api.rest.v1.credentials;

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
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.platform.secret.VaultSecret;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.v1.V1Client;
import net.netshot.netshot.testing.api.v1.V1Client.Answer;
import net.netshot.netshot.testing.api.v1.V1Endpoints;

/**
 * The credential sets of the v1 API, over HTTP: the secrets are write-only (never in a response, kept when left
 * out, cleared with null).
 */
@ApiTest
@DisplayName("v1 credential sets")
@ResourceLock("DB")
@V1Endpoints({"GET /credential-sets", "GET /credential-sets/{id}", "POST /credential-sets", "PUT /credential-sets/{id}",
	"PATCH /credential-sets/{id}", "DELETE /credential-sets/{id}"})
public class V1CredentialSetResourceTest {

	private static final String PASSWORD = "Pa55-w0rd-SECRET";
	private static final String ENABLE = "3nable-SECRET";
	private static final String KEY = "-----BEGIN KEY-----PRIVATE-SECRET";
	private static final String COMMUNITY = "c0mmunity-SECRET";
	private static final String AUTH_KEY = "auth-key-SECRET";
	private static final String PRIV_KEY = "priv-key-SECRET";

	private static final List<String> SECRETS = List.of(PASSWORD, ENABLE, KEY, COMMUNITY, AUTH_KEY, PRIV_KEY, "vaulted-SECRET");

	private TestServer server;
	private V1Client admin;

	@BeforeEach
	void setUp(TestServer testServer) {
		this.server = testServer;
		this.admin = new V1Client(testServer, UiUser.LEVEL_ADMIN);
		TestWiring.get().transactions().run(dsl -> {
			dsl.deleteFrom(Tables.DEVICE).execute();
			dsl.deleteFrom(Tables.DEVICE_CREDENTIAL_SET).execute();
			dsl.deleteFrom(Tables.VAULT_INSTANCE).execute();
			dsl.deleteFrom(Tables.DOMAIN).execute();
		});
	}

	private static String ssh(String name, String extra) {
		return "{\"name\": \"%s\", \"type\": \"SSH\", \"username\": \"admin\", \"password\": \"%s\"%s}".formatted(name, PASSWORD,
			extra == null ? "" : ", " + extra);
	}

	private long create(String body) throws Exception {
		Answer answer = this.admin.post("/credential-sets", body);
		Assertions.assertEquals(201, answer.status(), answer.toString());
		this.assertNoSecret(answer);
		return answer.json().get("id").asLong();
	}

	private void assertNoSecret(Answer answer) {
		for (String secret : SECRETS) {
			Assertions.assertFalse(answer.body().contains(secret), "Secret in the response: " + answer.body());
		}
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

	private long createVault(String name) throws Exception {
		Answer answer = this.admin.post("/vault-instances", ("{\"name\": \"%s\", \"type\": \"HASHICORP_KV2\", "
			+ "\"baseUrl\": \"http://127.0.0.1:1\", \"authMethod\": \"APPROLE\", \"appRoleId\": \"role\", "
			+ "\"appRoleSecretId\": \"vaulted-SECRET\"}").formatted(name));
		Assertions.assertEquals(201, answer.status(), answer.toString());
		return answer.json().get("id").asLong();
	}

	@Test
	@DisplayName("Create an SSH credential set: 201, flags, no secret in the response")
	void createSsh() throws Exception {
		long domainId = TestDevices.addDomain("Paris", "Paris DC").id();
		Answer answer = this.admin.post("/credential-sets", ssh("Main", "\"superPassword\": \"" + ENABLE + "\", \"mgmtDomain\": " + domainId));
		Assertions.assertEquals(201, answer.status(), answer.toString());
		this.assertNoSecret(answer);
		JsonNode cs = answer.json();
		long id = cs.get("id").asLong();
		Assertions.assertEquals("/api/v1/credential-sets/" + id, answer.header("Location"));
		Assertions.assertEquals("\"0\"", answer.header("ETag"));
		Assertions.assertEquals("SSH", cs.get("type").asText());
		Assertions.assertEquals("admin", cs.get("username").asText());
		Assertions.assertTrue(cs.get("hasPassword").asBoolean());
		Assertions.assertTrue(cs.get("hasSuperPassword").asBoolean());
		Assertions.assertTrue(cs.get("hasPrivateKey").isNull());
		Assertions.assertTrue(cs.get("passwordVault").isNull());
		Assertions.assertEquals(domainId, cs.get("mgmtDomain").get("id").asLong());
		Assertions.assertFalse(cs.get("deviceSpecific").asBoolean());
		Assertions.assertFalse(cs.has("password"));
		Assertions.assertFalse(cs.has("superPassword"));
		DeviceSshAccount stored = (DeviceSshAccount) TestDevices.findCredentialSet(id);
		Assertions.assertEquals(Secret.local(PASSWORD), stored.password());
		Assertions.assertEquals(Secret.local(ENABLE), stored.superPassword());

		// The domain can also be given as an object, as read
		Answer asObject = this.admin.post("/credential-sets", ssh("Other", "\"mgmtDomain\": {\"id\": " + domainId + ", \"name\": \"x\"}"));
		Assertions.assertEquals(201, asObject.status(), asObject.toString());
		Assertions.assertEquals(domainId, asObject.json().get("mgmtDomain").get("id").asLong());
		Assertions.assertFalse(asObject.json().get("hasSuperPassword").asBoolean());

		// A read representation can be sent back (read-only properties and has* flags ignored), but misses the secrets
		Answer back = this.admin.post("/credential-sets", answer.body().replace("\"Main\"", "\"Copy\""));
		Assertions.assertEquals(400, back.status(), back.toString());
		Assertions.assertEquals("password", back.json().get("errors").get(0).get("field").asText());
	}

	@Test
	@DisplayName("Create: the secrets each type needs are required, the others are refused")
	void createValidation() throws Exception {
		String[] invalid = {
			"{\"name\": \"A\", \"type\": \"SSH\", \"username\": \"u\"}|password",
			"{\"name\": \"A\", \"type\": \"SSH\", \"password\": \"p\"}|username",
			"{\"name\": \"A\", \"type\": \"TELNET\", \"username\": \"u\"}|password",
			"{\"name\": \"A\", \"type\": \"HTTP\", \"username\": \"u\"}|password",
			"{\"name\": \"A\", \"type\": \"SSH_KEY\", \"username\": \"u\"}|privateKey",
			"{\"name\": \"A\", \"type\": \"SNMP_V2C\"}|community",
			"{\"name\": \"A\", \"type\": \"SNMP_V1\"}|community",
			"{\"name\": \"A\", \"type\": \"SNMP_V3\", \"username\": \"u\", \"authType\": \"SHA\"}|authKey",
			"{\"name\": \"A\", \"type\": \"SNMP_V3\", \"username\": \"u\", \"authType\": \"SHA\", \"authKey\": \"k\", \"privType\": \"AES128\"}|privKey",
			"{\"name\": \"A\", \"type\": \"SNMP_V3\", \"username\": \"u\", \"privType\": \"AES128\", \"privKey\": \"k\"}|privType",
			"{\"name\": \"A\", \"type\": \"SNMP_V2C\", \"community\": \"c\", \"password\": \"p\"}|password",
			"{\"name\": \"A\", \"type\": \"SSH\", \"username\": \"u\", \"password\": \"p\", \"authType\": \"SHA\"}|authType",
			"{\"name\": \"A\", \"type\": \"FOO\"}|type",
			"{\"name\": \"A\", \"type\": \"SSH\", \"username\": \"u\", \"password\": \"p\", \"mgmtDomain\": 987654}|mgmtDomain",
			"{\"name\": \"A\", \"type\": \"SSH\", \"username\": \"u\", \"password\": \"p\", \"mgmtDomain\": \"abc\"}|mgmtDomain",
			"{\"name\": \"A\", \"type\": \"SSH\", \"username\": \"u\", \"password\": \"p\", \"passwordVault\": {\"vaultInstance\": 1, \"path\": \"a/b\"}}|password",
			"{\"name\": \"A\", \"type\": \"SSH\", \"username\": \"u\", \"passwordVault\": {\"vaultInstance\": 987654, \"path\": \"a/b\"}}|passwordVault",
			"{\"type\": \"SSH\", \"username\": \"u\", \"password\": \"p\"}|name",
			"{\"name\": \"A\", \"type\": \"SSH\", \"username\": \"u\", \"password\": \"p\", \"color\": \"red\"}|color",
		};
		for (String test : invalid) {
			String[] parts = test.split("\\|");
			Answer answer = this.admin.post("/credential-sets", parts[0]);
			Assertions.assertEquals(400, answer.status(), test + " -> " + answer);
			Assertions.assertEquals(parts[1], answer.json().get("errors").get(0).get("field").asText(), test + " -> " + answer);
		}
		Assertions.assertEquals(0, this.admin.get("/credential-sets").json().get("results").size());

		// Every type can be created
		for (String body : List.of(
			"{\"name\": \"T\", \"type\": \"TELNET\", \"username\": \"u\", \"password\": \"%s\"}".formatted(PASSWORD),
			"{\"name\": \"H\", \"type\": \"HTTP\", \"username\": \"u\", \"password\": \"%s\"}".formatted(PASSWORD),
			"{\"name\": \"K\", \"type\": \"SSH_KEY\", \"username\": \"u\", \"privateKey\": \"%s\"}".formatted(KEY),
			"{\"name\": \"S1\", \"type\": \"SNMP_V1\", \"community\": \"%s\"}".formatted(COMMUNITY),
			"{\"name\": \"S2\", \"type\": \"SNMP_V2C\", \"community\": \"%s\"}".formatted(COMMUNITY),
			"{\"name\": \"S3\", \"type\": \"SNMP_V3\", \"username\": \"u\", \"authType\": \"SHA\", \"authKey\": \"%s\", \"privType\": \"AES128\", \"privKey\": \"%s\"}"
				.formatted(AUTH_KEY, PRIV_KEY),
			"{\"name\": \"S3none\", \"type\": \"SNMP_V3\", \"username\": \"u\"}")) {
			this.create(body);
		}
		Answer duplicate = this.admin.post("/credential-sets", ssh("T", null));
		Assertions.assertEquals(409, duplicate.status(), duplicate.toString());
		Assertions.assertEquals("NETSHOT_DUPLICATE_CREDENTIALS", duplicate.json().get("code").asText());
	}

	@Test
	@DisplayName("List: brief view, filters, search, sort, device specific")
	void list() throws Exception {
		long domainId = TestDevices.addDomain("Paris", null).id();
		long ssh1 = this.create(ssh("Alpha SSH", "\"mgmtDomain\": " + domainId));
		this.create(ssh("Beta SSH", null));
		this.create("{\"name\": \"Gamma SNMP\", \"type\": \"SNMP_V2C\", \"community\": \"" + COMMUNITY + "\"}");
		TestDevices.addCredentialSet(DeviceSshAccount.builder().name("DEVICESPECIFIC-1").deviceSpecific(true)
			.username(Secret.local("specific-user")).password(Secret.local("x")).build());

		Answer all = this.admin.get("/credential-sets");
		Assertions.assertEquals(200, all.status(), all.toString());
		this.assertNoSecret(all);
		Assertions.assertEquals(List.of("Alpha SSH", "Beta SSH", "Gamma SNMP"), values(all.json(), "name"));
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "type", "mgmtDomain"), names(all.json().get("results").get(0)));
		Assertions.assertEquals(3, all.json().get("results").size());

		Assertions.assertEquals(List.of("DEVICESPECIFIC-1"), values(this.admin.get("/credential-sets?deviceSpecific=true").json(), "name"));
		Assertions.assertEquals(3, this.admin.get("/credential-sets?deviceSpecific=false").json().get("results").size());
		Assertions.assertEquals(List.of("Alpha SSH", "Beta SSH"), values(this.admin.get("/credential-sets?type=SSH").json(), "name"));
		Assertions.assertEquals(List.of("Gamma SNMP"), values(this.admin.get("/credential-sets?type=SNMP_V2C").json(), "name"));
		Assertions.assertEquals(List.of("Gamma SNMP"), values(this.admin.get("/credential-sets?type__n=SSH").json(), "name"));
		Assertions.assertEquals(List.of("Alpha SSH"), values(this.admin.get("/credential-sets?mgmtDomain=" + domainId).json(), "name"));
		Assertions.assertEquals(List.of("Beta SSH", "Gamma SNMP"),
			values(this.admin.get("/credential-sets?mgmtDomain__empty=true").json(), "name"));
		Assertions.assertEquals(List.of("Alpha SSH", "Beta SSH"), values(this.admin.get("/credential-sets?name__ic=ssh").json(), "name"));
		Assertions.assertEquals(List.of("Beta SSH"), values(this.admin.get("/credential-sets?q=beta").json(), "name"));
		Assertions.assertEquals(List.of("Gamma SNMP", "Beta SSH", "Alpha SSH"), values(this.admin.get("/credential-sets?sort=-name").json(), "name"));
		Assertions.assertEquals(List.of("SNMP_V2C", "SSH", "SSH"), values(this.admin.get("/credential-sets?sort=type").json(), "type"));
		for (String query : List.of("type=FOO", "password=x", "sort=password", "deviceSpecific__ic=x", "mgmtDomain=abc")) {
			Assertions.assertEquals(400, this.admin.get("/credential-sets?" + query).status(), query);
		}

		JsonNode full = this.admin.get("/credential-sets?view=full&name=Alpha%20SSH").json().get("results").get(0);
		Assertions.assertEquals(Set.of("id", "url", "display", "version", "changeDate", "name", "type", "mgmtDomain", "deviceSpecific",
			"username", "usernameVault", "hasPassword", "passwordVault", "hasSuperPassword", "superPasswordVault", "hasPrivateKey",
			"privateKeyVault", "hasCommunity", "communityVault", "authType", "hasAuthKey", "authKeyVault", "privType", "hasPrivKey",
			"privKeyVault"), names(full));
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "description", "server4Address"), names(full.get("mgmtDomain")));

		Answer get = this.admin.get("/credential-sets/" + ssh1);
		Assertions.assertEquals(200, get.status());
		Assertions.assertEquals("\"0\"", get.header("ETag"));
		Assertions.assertEquals(304, this.admin.get("/credential-sets/" + ssh1, "If-None-Match", "\"0\"").status());
		this.assertNoSecret(get);
		Answer missing = this.admin.get("/credential-sets/" + TestServer.UNKNOWN_ID);
		Assertions.assertEquals(404, missing.status());
		Assertions.assertEquals("NETSHOT_CREDENTIALS_NOT_FOUND", missing.json().get("code").asText());
	}

	@Test
	@DisplayName("PUT without the secrets keeps them, null clears the optional ones")
	void replace() throws Exception {
		long id = this.create("{\"name\": \"Main\", \"type\": \"SSH_KEY\", \"username\": \"admin\", \"privateKey\": \"" + KEY
			+ "\", \"password\": \"" + PASSWORD + "\", \"superPassword\": \"" + ENABLE + "\"}");

		Answer renamed = this.admin.put("/credential-sets/" + id, "{\"name\": \"Renamed\", \"type\": \"SSH_KEY\"}", "If-Match", "\"0\"");
		Assertions.assertEquals(200, renamed.status(), renamed.toString());
		this.assertNoSecret(renamed);
		Assertions.assertEquals("Renamed", renamed.json().get("name").asText());
		Assertions.assertEquals(1, renamed.json().get("version").asInt());
		Assertions.assertEquals("\"1\"", renamed.header("ETag"));
		DeviceSshKeyAccount stored = (DeviceSshKeyAccount) TestDevices.findCredentialSet(id);
		Assertions.assertEquals(Secret.local("admin"), stored.username());
		Assertions.assertEquals(Secret.local(KEY), stored.privateKey());
		Assertions.assertEquals(Secret.local(PASSWORD), stored.password());
		Assertions.assertEquals(Secret.local(ENABLE), stored.superPassword());

		// A new value replaces the stored one
		Assertions.assertEquals(200, this.admin.put("/credential-sets/" + id,
			"{\"name\": \"Renamed\", \"type\": \"SSH_KEY\", \"superPassword\": \"new-enable\"}").status());
		Assertions.assertEquals(Secret.local("new-enable"), ((DeviceSshKeyAccount) TestDevices.findCredentialSet(id)).superPassword());

		// null clears the optional secrets
		Answer cleared = this.admin.put("/credential-sets/" + id,
			"{\"name\": \"Renamed\", \"type\": \"SSH_KEY\", \"superPassword\": null, \"password\": null}");
		Assertions.assertEquals(200, cleared.status(), cleared.toString());
		Assertions.assertFalse(cleared.json().get("hasSuperPassword").asBoolean());
		Assertions.assertFalse(cleared.json().get("hasPassword").asBoolean());
		Assertions.assertTrue(cleared.json().get("hasPrivateKey").asBoolean());
		stored = (DeviceSshKeyAccount) TestDevices.findCredentialSet(id);
		Assertions.assertNull(stored.superPassword());
		Assertions.assertNull(stored.password());
		Assertions.assertEquals(Secret.local(KEY), stored.privateKey());

		// ... but not the required ones
		Answer required = this.admin.put("/credential-sets/" + id, "{\"name\": \"Renamed\", \"type\": \"SSH_KEY\", \"privateKey\": null}");
		Assertions.assertEquals(400, required.status(), required.toString());
		Assertions.assertEquals("privateKey", required.json().get("errors").get(0).get("field").asText());
		Assertions.assertEquals(Secret.local(KEY), ((DeviceSshKeyAccount) TestDevices.findCredentialSet(id)).privateKey());

		// The type can't change, nor the type be unknown
		Answer type = this.admin.put("/credential-sets/" + id, ssh("Renamed", null));
		Assertions.assertEquals(400, type.status(), type.toString());
		Assertions.assertEquals("NETSHOT_INVALID_CREDENTIALS_TYPE", type.json().get("code").asText());
		Assertions.assertEquals(404, this.admin.put("/credential-sets/" + TestServer.UNKNOWN_ID, ssh("X", null)).status());

		// If-Match
		Answer stale = this.admin.put("/credential-sets/" + id, "{\"name\": \"Late\", \"type\": \"SSH_KEY\"}", "If-Match", "\"0\"");
		Assertions.assertEquals(412, stale.status(), stale.toString());
		Assertions.assertEquals("NETSHOT_VERSION_MISMATCH", stale.json().get("code").asText());
		Assertions.assertEquals("Renamed", TestDevices.findCredentialSet(id).name());
	}

	@Test
	@DisplayName("SNMPv3: dropping a protocol drops its key")
	void snmpV3() throws Exception {
		long id = this.create("{\"name\": \"S3\", \"type\": \"SNMP_V3\", \"username\": \"u\", \"authType\": \"SHA\", \"authKey\": \""
			+ AUTH_KEY + "\", \"privType\": \"AES128\", \"privKey\": \"" + PRIV_KEY + "\"}");
		Answer answer = this.admin.get("/credential-sets/" + id);
		Assertions.assertEquals("SHA", answer.json().get("authType").asText());
		Assertions.assertTrue(answer.json().get("hasAuthKey").asBoolean());
		Assertions.assertTrue(answer.json().get("hasPrivKey").asBoolean());
		Assertions.assertTrue(answer.json().get("hasCommunity").isNull());

		Answer patched = this.admin.patch("/credential-sets/" + id, "{\"privType\": \"NONE\"}");
		Assertions.assertEquals(200, patched.status(), patched.toString());
		Assertions.assertFalse(patched.json().get("hasPrivKey").asBoolean());
		Assertions.assertTrue(patched.json().get("hasAuthKey").asBoolean());
		DeviceSnmpv3Community stored = (DeviceSnmpv3Community) TestDevices.findCredentialSet(id);
		Assertions.assertEquals(Secret.local(AUTH_KEY), stored.authKey());
		Assertions.assertNull(stored.privKey());

		Answer bad = this.admin.patch("/credential-sets/" + id, "{\"authType\": \"NONE\", \"privType\": \"AES128\", \"privKey\": \"k\"}");
		Assertions.assertEquals(400, bad.status(), bad.toString());
	}

	@Test
	@DisplayName("PATCH merges, keeps the secrets, and replaces a Vault location by a value")
	void patch() throws Exception {
		long domainId = TestDevices.addDomain("Paris", null).id();
		long id = this.create(ssh("Main", "\"superPassword\": \"" + ENABLE + "\", \"mgmtDomain\": " + domainId));

		Answer renamed = this.admin.patch("/credential-sets/" + id, "{\"name\": \"Patched\", \"username\": \"root\"}");
		Assertions.assertEquals(200, renamed.status(), renamed.toString());
		this.assertNoSecret(renamed);
		Assertions.assertEquals("Patched", renamed.json().get("name").asText());
		Assertions.assertEquals("root", renamed.json().get("username").asText());
		Assertions.assertEquals(domainId, renamed.json().get("mgmtDomain").get("id").asLong());
		DeviceSshAccount stored = (DeviceSshAccount) TestDevices.findCredentialSet(id);
		Assertions.assertEquals(Secret.local(PASSWORD), stored.password());
		Assertions.assertEquals(Secret.local(ENABLE), stored.superPassword());

		// The domain: another one, then none
		long otherDomain = TestDevices.addDomain("Lyon", null).id();
		Assertions.assertEquals(otherDomain, this.admin.patch("/credential-sets/" + id, "{\"mgmtDomain\": {\"id\": " + otherDomain + "}}")
			.json().get("mgmtDomain").get("id").asLong());
		Assertions.assertTrue(this.admin.patch("/credential-sets/" + id, "{\"mgmtDomain\": null}").json().get("mgmtDomain").isNull());

		// A secret set to null is cleared (optional only)
		Answer cleared = this.admin.patch("/credential-sets/" + id, "{\"superPassword\": null}");
		Assertions.assertEquals(200, cleared.status(), cleared.toString());
		Assertions.assertFalse(cleared.json().get("hasSuperPassword").asBoolean());
		Assertions.assertNull(((DeviceSshAccount) TestDevices.findCredentialSet(id)).superPassword());
		Assertions.assertEquals(400, this.admin.patch("/credential-sets/" + id, "{\"password\": null}").status());
		Assertions.assertEquals(Secret.local(PASSWORD), ((DeviceSshAccount) TestDevices.findCredentialSet(id)).password());

		// Vault locations
		long vaultId = this.createVault("Vault");
		Answer vaulted = this.admin.patch("/credential-sets/" + id,
			"{\"passwordVault\": {\"vaultInstance\": " + vaultId + ", \"path\": \"app/creds/password\"}}");
		Assertions.assertEquals(200, vaulted.status(), vaulted.toString());
		Assertions.assertTrue(vaulted.json().get("hasPassword").asBoolean());
		Assertions.assertEquals(vaultId, vaulted.json().get("passwordVault").get("vaultInstance").get("id").asLong());
		Assertions.assertEquals("Vault", vaulted.json().get("passwordVault").get("vaultInstance").get("name").asText());
		Assertions.assertEquals("app/creds/password", vaulted.json().get("passwordVault").get("path").asText());
		stored = (DeviceSshAccount) TestDevices.findCredentialSet(id);
		Assertions.assertInstanceOf(VaultSecret.class, stored.password(), "the local value is dropped");
		Assertions.assertEquals(vaultId, Secret.vaultInstanceId(stored.password()));

		// Unrelated changes keep the Vault location; a value replaces it
		Assertions.assertEquals(200, this.admin.patch("/credential-sets/" + id, "{\"name\": \"Still vaulted\"}").status());
		Assertions.assertEquals(vaultId, Secret.vaultInstanceId(((DeviceSshAccount) TestDevices.findCredentialSet(id)).password()));
		Assertions.assertEquals(200, this.admin.put("/credential-sets/" + id,
			"{\"name\": \"Still vaulted\", \"type\": \"SSH\", \"passwordVault\": {\"vaultInstance\": {\"id\": " + vaultId + "}, \"path\": \"a/b\"}}")
			.status());
		Assertions.assertEquals("a/b", Secret.vaultPath(((DeviceSshAccount) TestDevices.findCredentialSet(id)).password()));
		Answer local = this.admin.patch("/credential-sets/" + id, "{\"password\": \"" + PASSWORD + "\"}");
		Assertions.assertEquals(200, local.status(), local.toString());
		Assertions.assertTrue(local.json().get("passwordVault").isNull());
		stored = (DeviceSshAccount) TestDevices.findCredentialSet(id);
		Assertions.assertEquals(Secret.local(PASSWORD), stored.password());
		Assertions.assertNull(Secret.vaultInstanceId(stored.password()));

		// The Vault instance is referenced: it can't be deleted
		Assertions.assertEquals(200, this.admin.patch("/credential-sets/" + id,
			"{\"usernameVault\": {\"vaultInstance\": " + vaultId + ", \"path\": \"app/creds/username\"}}").status());
		Answer inUse = this.admin.delete("/vault-instances/" + vaultId);
		Assertions.assertEquals(409, inUse.status(), inUse.toString());
		Assertions.assertEquals("NETSHOT_VAULT_INSTANCE_IN_USE", inUse.json().get("code").asText());

		// If-Match, bad patches
		Assertions.assertEquals(412, this.admin.patch("/credential-sets/" + id, "{\"name\": \"X\"}", "If-Match", "\"0\"").status());
		Assertions.assertEquals(400, this.admin.patch("/credential-sets/" + id, "{\"color\": \"red\"}").status());
		Assertions.assertEquals(400, this.admin.patch("/credential-sets/" + id, "{\"type\": \"TELNET\"}").status());
		Assertions.assertEquals(400, this.admin.patch("/credential-sets/" + id, "{\"password\": \"x\", \"passwordVault\": {\"vaultInstance\": "
			+ vaultId + ", \"path\": \"a\"}}").status());
		Assertions.assertEquals(404, this.admin.patch("/credential-sets/" + TestServer.UNKNOWN_ID, "{\"name\": \"X\"}").status());
	}

	@Test
	@DisplayName("DELETE: 204, If-Match, 404, device-specific refused")
	void delete() throws Exception {
		long id = this.create(ssh("Main", null));
		Assertions.assertEquals(412, this.admin.delete("/credential-sets/" + id, "If-Match", "\"3\"").status());
		Assertions.assertEquals(204, this.admin.delete("/credential-sets/" + id, "If-Match", "\"0\"").status());
		Answer again = this.admin.delete("/credential-sets/" + id);
		Assertions.assertEquals(404, again.status());
		Assertions.assertEquals("NETSHOT_CREDENTIALS_NOT_FOUND", again.json().get("code").asText());

		long specific = TestDevices.addCredentialSet(DeviceSshAccount.builder().name("DEVICESPECIFIC-2").deviceSpecific(true)
			.username(Secret.local("u")).password(Secret.local(PASSWORD)).build()).id();
		Answer read = this.admin.get("/credential-sets/" + specific);
		Assertions.assertEquals(200, read.status(), read.toString());
		Assertions.assertTrue(read.json().get("deviceSpecific").asBoolean());
		Assertions.assertTrue(read.json().get("url").isNull());
		this.assertNoSecret(read);
		Assertions.assertEquals(409, this.admin.delete("/credential-sets/" + specific).status());
		Assertions.assertEquals(409, this.admin.put("/credential-sets/" + specific, ssh("X", null)).status());
		Assertions.assertNotNull(TestDevices.findCredentialSet(specific));
	}

	@Test
	@DisplayName("Selection of fields never gives a secret")
	void selection() throws Exception {
		DeviceSnmpv2cCommunity community = (DeviceSnmpv2cCommunity) TestDevices.addCredentialSet(DeviceSnmpv2cCommunity.builder()
			.name("Existing").community(Secret.local(COMMUNITY)).build());
		Answer answer = this.admin.get("/credential-sets/" + community.id() + "?fields=name,hasCommunity");
		Assertions.assertEquals(Set.of("id", "name", "hasCommunity"), names(answer.json()));
		this.assertNoSecret(answer);
		Assertions.assertEquals(400, this.admin.get("/credential-sets/" + community.id() + "?fields=community").status());
	}

	@Test
	@DisplayName("Authentication and permissions")
	void permissions() throws Exception {
		long id = this.create(ssh("Main", null));
		Assertions.assertEquals(401, new V1Client(this.server, null).get("/credential-sets").status());
		V1Client readOnly = new V1Client(this.server, UiUser.LEVEL_READONLY);
		Assertions.assertEquals(200, readOnly.get("/credential-sets").status());
		Assertions.assertEquals(200, readOnly.get("/credential-sets/" + id).status());
		Assertions.assertEquals(403, readOnly.post("/credential-sets", ssh("Other", null)).status());
		Assertions.assertEquals(403, readOnly.put("/credential-sets/" + id, ssh("Other", null)).status());
		Assertions.assertEquals(403, readOnly.patch("/credential-sets/" + id, "{\"name\": \"Other\"}").status());
		Assertions.assertEquals(403, readOnly.delete("/credential-sets/" + id).status());
		V1Client operator = new V1Client(this.server, UiUser.LEVEL_EXECUTEREADWRITE);
		Assertions.assertEquals(403, operator.post("/credential-sets", ssh("Other", null)).status());
	}
}
