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
package net.netshot.netshot.api.rest.v1;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import jakarta.ws.rs.core.MediaType;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.compliance.HardwareRuleMapper;
import net.netshot.netshot.api.rest.v1.compliance.HardwareRuleRequest;
import net.netshot.netshot.api.rest.v1.compliance.PolicyMapper;
import net.netshot.netshot.api.rest.v1.compliance.PolicyRequest;
import net.netshot.netshot.api.rest.v1.compliance.RuleMapper;
import net.netshot.netshot.api.rest.v1.compliance.RuleRequest;
import net.netshot.netshot.api.rest.v1.credentials.CredentialSetMapper;
import net.netshot.netshot.api.rest.v1.credentials.CredentialSetRequest;
import net.netshot.netshot.api.rest.v1.device.DeviceMapper;
import net.netshot.netshot.api.rest.v1.device.DeviceRequest;
import net.netshot.netshot.api.rest.v1.diagnostic.DiagnosticMapper;
import net.netshot.netshot.api.rest.v1.diagnostic.DiagnosticRequest;
import net.netshot.netshot.api.rest.v1.domain.DomainMapper;
import net.netshot.netshot.api.rest.v1.domain.DomainRequest;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.api.rest.v1.group.GroupRequest;
import net.netshot.netshot.api.rest.v1.hook.HookMapper;
import net.netshot.netshot.api.rest.v1.hook.HookRequest;
import net.netshot.netshot.api.rest.v1.script.ScriptMapper;
import net.netshot.netshot.api.rest.v1.script.ScriptRequest;
import net.netshot.netshot.api.rest.v1.user.UserMapper;
import net.netshot.netshot.api.rest.v1.user.UserRequest;
import net.netshot.netshot.api.rest.v1.vault.VaultInstanceMapper;
import net.netshot.netshot.api.rest.v1.vault.VaultInstanceRequest;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.compliance.RankedSoftwareRule;
import net.netshot.netshot.compliance.SoftwareRuleService.Replacement;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.v1.V1Client;
import net.netshot.netshot.testing.api.v1.V1Client.Answer;
import net.netshot.netshot.testing.api.v1.V1Resources;
import net.netshot.netshot.testing.api.v1.V1Resources.Endpoint;

/**
 * Checks the versions of the v1 API on every object which can be changed, found by scanning the resources (an
 * object with a {@code PUT}, {@code PATCH} or {@code DELETE} on {@code /{id}} needs a sample here): the version is
 * the ETag, a stale {@code If-Match} is refused with a 412, a {@code PATCH} without {@code If-Match} applies to the
 * latest version, and a {@code DELETE} takes {@code If-Match}.
 */
@ApiTest
@DisplayName("v1 versions of the objects")
@ResourceLock("DB")
class V1VersioningSweepTest {

	/** The objects which can be deleted but not changed, and have no version (why). */
	private static final Map<String, String> UNVERSIONED = Map.of(
		"/api-tokens/{id}", "An API token can't change: it only has the date it was created");

	/** Creates an object for the test, and gives its path. */
	@FunctionalInterface
	private interface Sample {
		String create(V1Client admin) throws Exception;
	}

	private V1Client admin;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) {
		this.admin = new V1Client(server, User.LEVEL_ADMIN);
	}

	private static String created(Answer answer) {
		Assertions.assertEquals(201, answer.status(), answer.toString());
		return answer.header("Location").replaceFirst("^.*/api/v1", "");
	}

	private static String unique(String name) {
		return name + "-" + System.nanoTime();
	}

	// The samples, by path template of the objects
	private static Map<String, Sample> samples() {
		Map<String, Sample> samples = new LinkedHashMap<>();
		samples.put("/devices/{id}", _ -> {
			Domain domain = TestDevices.addDomain(unique("Versions"), "Versions");
			return "/devices/" + TestDevices.addFakeCiscoIos(domain, (int) (System.nanoTime() % 200)).id();
		});
		samples.put("/groups/{id}", admin -> created(admin.post("/groups", """
			{"name": "%s", "type": "STATIC"}""".formatted(unique("Group")))));
		samples.put("/domains/{id}", admin -> created(admin.post("/domains", """
			{"name": "%s", "description": "Versions", "server4Address": "10.254.0.1"}""".formatted(unique("Domain")))));
		samples.put("/credential-sets/{id}", admin -> created(admin.post("/credential-sets", """
			{"name": "%s", "type": "SSH", "username": "admin", "password": "secret"}""".formatted(unique("Credentials")))));
		samples.put("/vault-instances/{id}", admin -> created(admin.post("/vault-instances", """
			{"name": "%s", "type": "HASHICORP_KV2", "baseUrl": "http://127.0.0.1:1", "authMethod": "APPROLE",
			"appRoleId": "role", "appRoleSecretId": "secret"}""".formatted(unique("Vault")))));
		samples.put("/diagnostics/{id}", admin -> created(admin.post("/diagnostics", """
			{"name": "%s", "type": "SIMPLE", "resultType": "TEXT", "cliMode": "enable", "command": "show version",
			"deviceDriver": "CiscoIOS12"}""".formatted(unique("Diagnostic")))));
		samples.put("/scripts/{id}", admin -> created(admin.post("/scripts", """
			{"name": "%s", "deviceDriver": "CiscoIOS12", "script": "function run(cli, device) {\\n  cli.command('show clock');\\n}"}"""
			.formatted(unique("Script")))));
		samples.put("/hooks/{id}", admin -> created(admin.post("/hooks", """
			{"type": "WEB", "name": "%s", "action": "POST_JSON", "targetUrl": "https://example.com/hook"}""".formatted(unique("Hook")))));
		samples.put("/users/{id}", admin -> created(admin.post("/users", """
			{"username": "%s", "local": true, "level": 10, "password": "Versionpassword1"}""".formatted(unique("user")))));
		samples.put("/compliance/policies/{id}", admin -> created(admin.post("/compliance/policies", """
			{"name": "%s"}""".formatted(unique("Policy")))));
		samples.put("/compliance/rules/{id}", admin -> {
			String policy = created(admin.post("/compliance/policies", "{\"name\": \"%s\"}".formatted(unique("Policy"))));
			return created(admin.post("/compliance/rules", """
				{"type": "JAVASCRIPT", "name": "%s", "policy": %s}""".formatted(unique("Rule"), policy.replaceFirst("^.*/", ""))));
		});
		samples.put("/compliance/software-rules/{id}", admin -> created(admin.post("/compliance/software-rules", """
			{"level": "GOLD", "softwareVersion": "15.2"}""")));
		samples.put("/compliance/hardware-rules/{id}", admin -> created(admin.post("/compliance/hardware-rules", """
			{"partNumber": "WS-C3750", "endOfLife": "2035-01-01T00:00:00Z"}""")));
		return samples;
	}

	@Test
	@DisplayName("Every object which can be changed or deleted has a sample")
	void everyObjectHasASample() throws Exception {
		Set<String> found = new TreeSet<>();
		for (Endpoint endpoint : V1Resources.endpoints()) {
			if (Set.of("PUT", "PATCH", "DELETE").contains(endpoint.httpMethod()) && endpoint.template().endsWith("/{id}")
				&& !UNVERSIONED.containsKey(endpoint.template())) {
				found.add(endpoint.template());
			}
		}
		Assertions.assertEquals(found, new TreeSet<>(samples().keySet()), "Objects with a PUT, PATCH or DELETE on /{id} "
			+ "(a new one must get a sample here, or be added to UNVERSIONED on purpose)");
	}

	private static String etag(long version) {
		return "\"" + version + "\"";
	}

	private static void assertMismatch(Answer answer, String context) {
		// 412 is only a version mismatch in v1
		Assertions.assertEquals(412, answer.status(), context + " -> " + answer);
	}

	@TestFactory
	@DisplayName("The version is the ETag; If-Match is checked by PATCH and DELETE")
	Stream<DynamicTest> versions() {
		return samples().entrySet().stream().map(sample -> DynamicTest.dynamicTest(sample.getKey(), () -> {
			String path = sample.getValue().create(this.admin);
			Answer got = this.admin.get(path);
			Assertions.assertEquals(200, got.status(), got.toString());
			long version = got.json().get("version").asLong();
			Assertions.assertEquals(etag(version), got.header("ETag"), "ETag of " + path);
			Assertions.assertTrue(got.json().hasNonNull("changeDate"), "changeDate of " + path);

			assertMismatch(this.admin.patch(path, "{}", "If-Match", etag(version + 1000)), "PATCH with a stale If-Match");
			Answer patched = this.admin.patch(path, "{}");
			Assertions.assertEquals(200, patched.status(), patched.toString());
			JsonNode object = patched.json();
			Assertions.assertEquals(etag(object.get("version").asLong()), patched.header("ETag"), "ETag of the PATCH answer");
			Answer patchedAgain = this.admin.patch(path, "{}", "If-Match", patched.header("ETag"));
			Assertions.assertEquals(200, patchedAgain.status(), "PATCH with the current If-Match -> " + patchedAgain);

			assertMismatch(this.admin.delete(path, "If-Match", etag(version + 1000)), "DELETE with a stale If-Match");
			Answer deleted = this.admin.delete(path, "If-Match", this.admin.get(path).header("ETag"));
			Assertions.assertEquals(204, deleted.status(), deleted.toString());
			Assertions.assertEquals(404, this.admin.get(path).status());
		}));
	}

	/** Changes an object (of ID given) through its service, with a change (from the current object) to wrap. */
	@FunctionalInterface
	private interface Change {
		void run(Wiring wiring, long id, Function<Function<Object, Object>, Function<Object, Object>> wrap);
	}

	// An empty merge patch, as a change of the service (the object is saved as it is)
	@SuppressWarnings("unchecked")
	private static <T, R, D> Function<T, D> unchanged(Function<Function<Object, Object>, Function<Object, Object>> wrap,
		Class<R> requestType, Function<T, R> toRequest, Function<R, D> toDefinition) {
		Function<T, D> change = MergePatch.of(JsonNodeFactory.instance.objectNode(), MediaType.APPLICATION_JSON_TYPE, requestType,
			toRequest, toDefinition);
		return (Function<T, D>) (Function<?, ?>) wrap.apply((Function<Object, Object>) (Function<?, ?>) change);
	}

	@SuppressWarnings("unchecked")
	private static <T> Function<T, T> same(Function<Function<Object, Object>, Function<Object, Object>> wrap) {
		return (Function<T, T>) (Function<?, ?>) wrap.apply(Function.identity());
	}

	// The tables of the objects, and how their services change them
	private static Map<String, String> tables() {
		Map<String, String> tables = new LinkedHashMap<>();
		tables.put("/devices/{id}", "device");
		tables.put("/groups/{id}", "device_group");
		tables.put("/domains/{id}", "domain");
		tables.put("/credential-sets/{id}", "device_credential_set");
		tables.put("/vault-instances/{id}", "vault_instance");
		tables.put("/diagnostics/{id}", "diagnostic");
		tables.put("/scripts/{id}", "device_js_script");
		tables.put("/hooks/{id}", "hook");
		tables.put("/users/{id}", "user");
		tables.put("/compliance/policies/{id}", "policy");
		tables.put("/compliance/rules/{id}", "rule");
		tables.put("/compliance/software-rules/{id}", "software_rule");
		tables.put("/compliance/hardware-rules/{id}", "hardware_rule");
		return tables;
	}

	private static Map<String, Change> changes() {
		Map<String, Change> changes = new LinkedHashMap<>();
		changes.put("/devices/{id}", (w, id, wrap) -> w.deviceService().update(id,
			unchanged(wrap, DeviceRequest.class, DeviceMapper::toRequest, DeviceMapper::toChange), null));
		changes.put("/groups/{id}", (w, id, wrap) -> w.groupService().update(id,
			unchanged(wrap, GroupRequest.class, GroupMapper::toRequest, GroupMapper::toDefinition), null));
		changes.put("/domains/{id}", (w, id, wrap) -> w.domainService().update(id,
			unchanged(wrap, DomainRequest.class, DomainMapper::toRequest, DomainMapper::toDefinition), null));
		changes.put("/credential-sets/{id}", (w, id, wrap) -> w.credentialSetService().update(id,
			unchanged(wrap, CredentialSetRequest.class, CredentialSetMapper::toRequest, CredentialSetMapper::toDefinition), null));
		changes.put("/vault-instances/{id}", (w, id, wrap) -> w.vaultInstanceService().update(id,
			unchanged(wrap, VaultInstanceRequest.class, VaultInstanceMapper::toRequest, VaultInstanceMapper::toDefinition), null));
		changes.put("/diagnostics/{id}", (w, id, wrap) -> w.diagnosticService().update(id,
			unchanged(wrap, DiagnosticRequest.class, DiagnosticMapper::toRequest, DiagnosticMapper::toDefinition), null));
		changes.put("/scripts/{id}", (w, id, wrap) -> w.scriptService().update(id,
			unchanged(wrap, ScriptRequest.class, ScriptMapper::toRequest, r -> ScriptMapper.toCreation(r, null)), null));
		changes.put("/hooks/{id}", (w, id, wrap) -> w.hookService().update(id,
			unchanged(wrap, HookRequest.class, HookMapper::toRequest, HookMapper::toHook), null));
		changes.put("/users/{id}", (w, id, wrap) -> w.userService().update(id, "nobody",
			unchanged(wrap, UserRequest.class, UserMapper::toRequest, UserMapper::toUpdate), null));
		changes.put("/compliance/policies/{id}", (w, id, wrap) -> w.policyService().replace(id,
			unchanged(wrap, PolicyRequest.class, PolicyMapper::toRequest, PolicyMapper::toDefinition), null));
		changes.put("/compliance/rules/{id}", (w, id, wrap) -> w.ruleService().replace(id,
			unchanged(wrap, RuleRequest.class, RuleMapper::toRequest, RuleMapper::toRule), null));
		changes.put("/compliance/software-rules/{id}", (w, id, wrap) -> {
			Function<RankedSoftwareRule, RankedSoftwareRule> same = same(wrap);
			w.softwareRuleService().replace(id, same.andThen(r -> new Replacement(r.rule(), null)), null);
		});
		changes.put("/compliance/hardware-rules/{id}", (w, id, wrap) -> w.hardwareRuleService().replace(id,
			unchanged(wrap, HardwareRuleRequest.class, HardwareRuleMapper::toRequest, HardwareRuleMapper::toRule), null));
		return changes;
	}

	@Test
	@DisplayName("Every object has a table and a change for the lock test")
	void everyObjectHasAChange() {
		Assertions.assertEquals(samples().keySet(), tables().keySet());
		Assertions.assertEquals(samples().keySet(), changes().keySet());
	}

	@TestFactory
	@DisplayName("A change locks the object: a concurrent change of it waits for the end of the change")
	Stream<DynamicTest> locks() {
		return changes().entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
			String path = samples().get(entry.getKey()).create(this.admin);
			long id = Long.parseLong(path.replaceFirst("^.*/", ""));
			String table = tables().get(entry.getKey());
			long before = this.admin.get(path).json().get("version").asLong();
			AtomicReference<Thread> concurrent = new AtomicReference<>();
			entry.getValue().run(TestWiring.get(), id, change -> current -> {
				// Another transaction tries to change the object while it is being changed
				Thread thread = new Thread(() -> TestWiring.get().transactions().run(dsl ->
					dsl.execute("update \"%s\" set version = version + 1 where id = %d".formatted(table, id))));
				thread.start();
				try {
					thread.join(500);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				Assertions.assertTrue(thread.isAlive(), "The concurrent change waits for the lock");
				concurrent.set(thread);
				return change.apply(current);
			});
			concurrent.get().join(10000);
			Assertions.assertFalse(concurrent.get().isAlive(), "The concurrent change runs once the change is done");
			long after = this.admin.get(path).json().get("version").asLong();
			Assertions.assertTrue(after > before, "Both changes are kept (version %d, then %d)".formatted(before, after));
			Assertions.assertEquals(204, this.admin.delete(path).status());
		}));
	}

	@Test
	@DisplayName("The exemptions of a rule change its version, and take its If-Match")
	void exemptions() throws Exception {
		String rule = samples().get("/compliance/rules/{id}").create(this.admin);
		Domain domain = TestDevices.addDomain(unique("Exemptions"), "Exemptions");
		long deviceId = TestDevices.addFakeCiscoIos(domain, 201).id();
		String body = "{\"exemptions\": [{\"device\": %d, \"expirationDate\": \"2030-01-01T00:00:00Z\"}]}".formatted(deviceId);
		long version = this.admin.get(rule).json().get("version").asLong();
		assertMismatch(this.admin.put(rule + "/exemptions", body, "If-Match", etag(version + 1000)), "stale If-Match");
		Answer replaced = this.admin.put(rule + "/exemptions", body, "If-Match", etag(version));
		Assertions.assertEquals(200, replaced.status(), replaced.toString());
		Assertions.assertEquals(version + 1, this.admin.get(rule).json().get("version").asLong(), "version after the exemptions");
		Assertions.assertEquals(204, this.admin.delete("/devices/" + deviceId).status());
		Assertions.assertEquals(204, this.admin.delete(rule).status());
	}
}
