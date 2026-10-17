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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.netshot.netshot.ApiContractCase.Builder;
import net.netshot.netshot.ApiContractCase.Reply;
import net.netshot.netshot.ApiContractGolden.Transform;

/**
 * The catalogue of API contract cases: reads first (they do not change the
 * dataset), then writes (which do, and run in the order they are listed).
 *
 * <p>Building the catalogue does not touch the database: paths and bodies
 * are suppliers resolved when the case runs.</p>
 */
final class ApiContractCases {

	private ApiContractCases() {
	}

	/** Builds the lists of cases. */
	private static final class Catalogue {
		final ApiContractSeed seed;
		final List<ApiContractCase> cases = new ArrayList<>();

		Catalogue(ApiContractSeed seed) {
			this.seed = seed;
		}

		/** Path with seeded IDs: "%s" are replaced by the IDs of the given seeded objects. */
		Supplier<String> path(String format, Object... args) {
			return () -> {
				Object[] values = new Object[args.length];
				for (int i = 0; i < args.length; i++) {
					values[i] = args[i] instanceof String name ? this.seed.id(name) : args[i];
				}
				return format.formatted(values);
			};
		}

		/** Adds a read case in JSON and XML. */
		void get(String template, String variant, Supplier<String> path) {
			this.getJson(template, variant, path);
			this.getXml(template, variant, path);
		}

		void get(String template) {
			this.get(template, null, () -> template);
		}

		/** Adds a read case in JSON and XML, for a list which the server returns in no defined order. */
		void getUnordered(String template) {
			this.add(ApiContractCase.builder("GET", template).path(template).accept(ApiContractCase.JSON).sortRoot());
			this.add(ApiContractCase.builder("GET", template).path(template).accept(ApiContractCase.XML).sortRoot());
		}

		void getJson(String template, String variant, Supplier<String> path) {
			this.add(ApiContractCase.builder("GET", template).variant(variant).path(path).accept(ApiContractCase.JSON));
		}

		void getXml(String template, String variant, Supplier<String> path) {
			this.add(ApiContractCase.builder("GET", template).variant(variant).path(path).accept(ApiContractCase.XML));
		}

		void getOctet(String template, String variant, Supplier<String> path) {
			this.add(ApiContractCase.builder("GET", template).variant(variant).path(path).accept(ApiContractCase.OCTET));
		}

		void getYaml(String template, String variant, Supplier<String> path) {
			this.add(ApiContractCase.builder("GET", template).variant(variant).path(path).accept(ApiContractCase.YAML));
		}

		/** Adds a read case with a specific token and Accept type. */
		void getAs(String template, String variant, Supplier<String> path, String token) {
			this.add(ApiContractCase.builder("GET", template).variant(variant).path(path).token(token));
		}

		void add(Builder builder) {
			this.cases.add(builder.build());
		}

		/** Consumer remembering the "id" of a created object. */
		Consumer<Reply> remember(String name) {
			return reply -> this.rememberFrom(name, reply, "id");
		}

		/** Remembers a numeric field of the reply, if present (a failed call leaves it unknown). */
		void rememberFrom(String name, Reply reply, String field) {
			if (reply.status() >= 200 && reply.status() < 300) {
				this.seed.remember(name, reply.json().get(field).asLong());
			}
		}

		void rememberXml(String name, Reply reply, String element) {
			if (reply.status() >= 200 && reply.status() < 300) {
				this.seed.remember(name, reply.xmlLong(element));
			}
		}

		/** Adds a write case (JSON in, JSON out). */
		void write(String method, String template, String variant, Supplier<String> path, Supplier<String> body) {
			this.writeAs(method, template, variant, path, body, null, null);
		}

		void writeAs(String method, String template, String variant, Supplier<String> path, Supplier<String> body,
			String remember, String token) {
			Builder builder = ApiContractCase.builder(method, template).variant(variant).path(path);
			if (body != null) {
				builder.body(body);
			}
			if (remember != null) {
				builder.afterReply(this.remember(remember));
			}
			if (token != null) {
				builder.token(token);
			}
			this.add(builder);
		}

		/** Body with seeded IDs: "%d" are replaced by the IDs of the given seeded objects. */
		Supplier<String> body(String format, Object... args) {
			return this.path(format, args);
		}
	}

	static List<ApiContractCase> all(ApiContractSeed seed) {
		Catalogue c = new Catalogue(seed);
		reads(c);
		writes(c);
		return c.cases;
	}

	private static void reads(Catalogue c) {
		// Server
		c.get("/serverinfo");
		c.getYaml("/serverinfo", "yaml", () -> "/serverinfo");
		c.get("/cluster/members");
		c.get("/cluster/masterstatus");
		c.get("/user");
		c.get("/users");
		c.get("/apitokens");
		c.add(ApiContractCase.builder("GET", "/devicetypes").transform(Transform.DEVICE_TYPES));
		c.add(ApiContractCase.builder("GET", "/devicetypes").accept(ApiContractCase.XML).transform(Transform.DEVICE_TYPES));

		// Domains
		c.get("/domains");
		c.get("/domains", "paged", () -> "/domains?offset=1&limit=1");
		c.getYaml("/domains", "yaml", () -> "/domains");

		// Devices
		c.get("/devices");
		c.get("/devices", "details", () -> "/devices?details=true");
		c.get("/devices", "group", c.path("/devices?group=%d", "group.static"));
		c.get("/devices", "paged", () -> "/devices?offset=1&limit=2");
		c.getYaml("/devices", "yaml", () -> "/devices");
		c.get("/devices/{id}", "router1", c.path("/devices/%d", "device.router1"));
		c.get("/devices/{id}", "router2", c.path("/devices/%d", "device.router2"));
		c.get("/devices/{id}", "proxy", c.path("/devices/%d", "device.proxy"));
		c.get("/devices/{id}", "bare", c.path("/devices/%d", "device.bare"));
		c.getYaml("/devices/{id}", "yaml", c.path("/devices/%d", "device.router1"));
		c.get("/devices/{id}/interfaces", "router1", c.path("/devices/%d/interfaces", "device.router1"));
		c.get("/devices/{id}/interfaces", "bare", c.path("/devices/%d/interfaces", "device.bare"));
		c.get("/devices/{id}/modules", "router1", c.path("/devices/%d/modules", "device.router1"));
		c.get("/devices/{id}/modules", "history", c.path("/devices/%d/modules?history=true", "device.router1"));
		c.get("/devices/{id}/tasks", "router1", c.path("/devices/%d/tasks", "device.router1"));
		c.get("/devices/{id}/tasks", "paged", c.path("/devices/%d/tasks?offset=1&limit=1", "device.router1"));
		c.get("/devices/{id}/configs", "router1", c.path("/devices/%d/configs", "device.router1"));
		c.get("/devices/{id}/configs", "paged", c.path("/devices/%d/configs?offset=1&limit=1", "device.router1"));
		c.get("/devices/{id}/complianceresults", "router1", c.path("/devices/%d/complianceresults", "device.router1"));
		c.get("/devices/{id}/diagnosticresults", "router1", c.path("/devices/%d/diagnosticresults", "device.router1"));
		c.get("/devices/{id}/diagnosticresults", "router2", c.path("/devices/%d/diagnosticresults", "device.router2"));
		c.get("/devicefamilies");
		c.get("/partnumbers");
		c.get("/devicetags");
		c.get("/softwareversions");

		// Configs
		c.get("/configs");
		c.get("/configs", "filtered", c.path("/configs?after=" + (ApiContractSeed.D2021 - 1) + "&domain=%d&group=%d",
			"domain.paris", "group.static"));
		c.getOctet("/configs/{id}/{item}", "runningConfig", c.path("/configs/%d/runningConfig", "config.router1.last"));
		c.getOctet("/configs/{id}/{item}", "binaryFile", c.path("/configs/%d/backupFile", "config.router1.last"));
		c.getOctet("/configs/{id}/{item}", "unknownItem", c.path("/configs/%d/doesNotExist", "config.router1.last"));
		c.getOctet("/configs/{id}/{item}", "unknownConfig", () -> "/configs/99999999/runningConfig");
		c.getJson("/configs/{id1}/vs/{id2}", "default", c.path("/configs/%d/vs/%d", "config.router1.first", "config.router1.last"));
		c.getJson("/configs/{id1}/vs/{id2}", "nodeltas", c.path("/configs/%d/vs/%d?deltas=false", "config.router1.first", "config.router1.last"));
		c.getJson("/configs/{id1}/vs/{id2}", "fullconfigs", c.path("/configs/%d/vs/%d?fullconfigs=true", "config.router1.second", "config.router1.last"));
		c.getXml("/configs/{id1}/vs/{id2}", "xml", c.path("/configs/%d/vs/%d", "config.router1.first", "config.router1.last"));

		// Tasks
		c.get("/tasks");
		c.get("/tasks", "status", () -> "/tasks?status=SUCCESS&status=FAILURE");
		c.get("/tasks", "type", () -> "/tasks?type=TakeSnapshotTask&type=CheckComplianceTask");
		c.get("/tasks", "dates", () -> "/tasks?after=" + (ApiContractSeed.D2021 - 1) + "&before=" + (ApiContractSeed.D2021 + 25000));
		c.get("/tasks", "paged", () -> "/tasks?offset=2&limit=3");
		c.get("/tasks", "parent", c.path("/tasks?parentTaskId=%d", "task.group"));
		c.get("/tasks/{id}", "snapshot", c.path("/tasks/%d", "task.snapshot"));
		c.get("/tasks/{id}", "failed", c.path("/tasks/%d", "task.failed"));
		c.get("/tasks/{id}", "script", c.path("/tasks/%d", "task.script"));
		c.get("/tasks/{id}", "group", c.path("/tasks/%d", "task.group"));
		c.get("/tasks/{id}", "compliance", c.path("/tasks/%d", "task.compliance"));
		c.get("/tasks/{id}", "discover", c.path("/tasks/%d", "task.discover"));
		c.get("/tasks/{id}", "scan", c.path("/tasks/%d", "task.scan"));
		c.get("/tasks/{id}", "purge", c.path("/tasks/%d", "task.purge"));
		c.get("/tasks/{id}", "diagnostics", c.path("/tasks/%d", "task.diagnostics"));
		c.get("/tasks/{id}", "groupcompliance", c.path("/tasks/%d", "task.groupcompliance"));
		c.getOctet("/tasks/{id}/debuglog", "snapshot", c.path("/tasks/%d/debuglog", "task.snapshot"));
		c.getOctet("/tasks/{id}/debuglog", "nodebuglog", c.path("/tasks/%d/debuglog", "task.failed"));
		c.get("/tasks/summary");
		c.get("/tasks/summary", "parent", c.path("/tasks/summary?parentTaskId=%d", "task.group"));
		c.get("/tasks/stats", "default", () -> "/tasks/stats?after=" + (ApiContractSeed.D2021 - 1) + "&before="
			+ (ApiContractSeed.D2022 + 10000) + "&buckets=4");

		// Credential sets, Vault instances
		c.get("/credentialsets");
		c.get("/credentialsets", "paged", () -> "/credentialsets?offset=2&limit=2");
		c.get("/vaultinstances");

		// Groups
		c.get("/groups");
		c.getYaml("/groups", "yaml", () -> "/groups");
		c.get("/groups/{id}", "static", c.path("/groups/%d", "group.static"));
		c.get("/groups/{id}", "dynamic", c.path("/groups/%d", "group.dynamic"));
		c.get("/groups/{id}", "hidden", c.path("/groups/%d", "group.hidden"));
		c.get("/groups/{id}", "empty", c.path("/groups/%d", "group.empty"));

		// Policies, rules
		// The list of policies is built from a hash set of entities
		c.getUnordered("/policies");
		c.getYaml("/policies", "yaml", () -> "/policies");
		c.get("/rules");
		c.get("/policies/{id}/rules", "security", c.path("/policies/%d/rules", "policy.security"));
		c.get("/policies/{id}/rules", "routing", c.path("/policies/%d/rules", "policy.routing"));
		c.get("/policies/{id}/rules", "empty", c.path("/policies/%d/rules", "policy.empty"));
		c.get("/policies/{pid}/rules/{id}", "text", c.path("/policies/%d/rules/%d", "policy.security", "rule.text"));
		c.get("/policies/{pid}/rules/{id}", "regex", c.path("/policies/%d/rules/%d", "policy.security", "rule.regex"));
		c.get("/policies/{pid}/rules/{id}", "js", c.path("/policies/%d/rules/%d", "policy.routing", "rule.js"));
		c.get("/policies/{pid}/rules/{id}", "python", c.path("/policies/%d/rules/%d", "policy.routing", "rule.python"));
		c.get("/rule/{id}/exempteddevices", "text", c.path("/rule/%d/exempteddevices", "rule.text"));
		c.get("/rule/{id}/exempteddevices", "none", c.path("/rule/%d/exempteddevices", "rule.regex"));
		c.get("/hardwarerules");
		c.get("/softwarerules");
		c.get("/softwarerules", "paged", () -> "/softwarerules?offset=1&limit=1");

		// Reports
		c.get("/reports/last7dayschangesbyday", "default", () -> "/reports/last7dayschangesbyday?tz=UTC");
		c.get("/reports/groupconfigcompliancestats", "default", () -> "/reports/groupconfigcompliancestats");
		c.get("/reports/groupconfigcompliancestats", "filtered", c.path("/reports/groupconfigcompliancestats?domain=%d&group=%d&policy=%d",
			"domain.paris", "group.dynamic", "policy.security"));
		c.get("/reports/hardwaresupportstats", "default", () -> "/reports/hardwaresupportstats");
		c.get("/reports/hardwaresupportstats", "filtered", c.path("/reports/hardwaresupportstats?domain=%d&group=%d",
			"domain.paris", "group.dynamic"));
		c.get("/reports/groupsoftwarecompliancestats", "default", () -> "/reports/groupsoftwarecompliancestats");
		c.get("/reports/groupsoftwarecompliancestats", "filtered", c.path("/reports/groupsoftwarecompliancestats?domain=%d", "domain.paris"));
		c.get("/reports/configcompliancedevicestatuses", "default", () -> "/reports/configcompliancedevicestatuses");
		c.get("/reports/configcompliancedevicestatuses", "filtered", c.path(
			"/reports/configcompliancedevicestatuses?domain=%d&group=%d&policy=%d&result=NONCONFORMING&result=CONFORMING",
			"domain.paris", "group.dynamic", "policy.security"));
		c.get("/reports/groupconfignoncompliantdevices/{id}", "default", c.path("/reports/groupconfignoncompliantdevices/%d", "group.dynamic"));
		c.get("/reports/groupconfignoncompliantdevices/{id}", "filtered", c.path(
			"/reports/groupconfignoncompliantdevices/%d?domain=%d&policy=%d", "group.dynamic", "domain.paris", "policy.security"));
		c.get("/reports/hardwaresupportdevices/{type}/{date}", "eos", () -> "/reports/hardwaresupportdevices/eos/" + ApiContractSeed.D2040);
		c.get("/reports/hardwaresupportdevices/{type}/{date}", "eol", () -> "/reports/hardwaresupportdevices/eol/" + (ApiContractSeed.D2040 + 365L * 24 * 3600 * 1000));
		c.get("/reports/hardwaresupportdevices/{type}/{date}", "filtered", c.path(
			"/reports/hardwaresupportdevices/eos/" + ApiContractSeed.D2040 + "?domain=%d&group=%d", "domain.paris", "group.dynamic"));
		c.get("/reports/groupdevicesbysoftwarelevel/{id}/{level}", "gold", c.path("/reports/groupdevicesbysoftwarelevel/%d/GOLD", "group.dynamic"));
		c.get("/reports/groupdevicesbysoftwarelevel/{id}/{level}", "silver", c.path(
			"/reports/groupdevicesbysoftwarelevel/%d/SILVER?domain=%d", "group.static", "domain.lyon"));
		c.get("/reports/accessfailuredevices", "default", () -> "/reports/accessfailuredevices");
		c.get("/reports/accessfailuredevices", "filtered", c.path("/reports/accessfailuredevices?days=30&domain=%d", "domain.paris"));
		c.add(ApiContractCase.builder("GET", "/reports/export").variant("default").path("/reports/export")
			.accept(ApiContractCase.XLSX));
		c.add(ApiContractCase.builder("GET", "/reports/export").variant("full").accept(ApiContractCase.XLSX)
			.path(c.path("/reports/export?group=%d&domain=%d&interfaces=true&inventory=true&inventoryhistory=true"
				+ "&locations=true&compliance=true&groups=true&devicedriverattributes=true&format=xlsx", "group.dynamic", "domain.paris")));

		// Diagnostics, scripts, hooks
		c.get("/diagnostics");
		c.getYaml("/diagnostics", "yaml", () -> "/diagnostics");
		c.get("/diagnostics/{id}", "simple", c.path("/diagnostics/%d", "diag.simple.text"));
		c.get("/diagnostics/{id}", "js", c.path("/diagnostics/%d", "diag.js.longtext"));
		c.get("/diagnostics/{id}", "python", c.path("/diagnostics/%d", "diag.python.numeric"));
		c.get("/scripts");
		c.get("/scripts", "paged", () -> "/scripts?offset=1&limit=1");
		c.get("/scripts/{id}", "one", c.path("/scripts/%d", "script.1"));
		c.get("/hooks");

		// Documentation
		c.add(ApiContractCase.builder("GET", "/openapi.{type}").variant("json").path("/openapi.json").accept(ApiContractCase.JSON)
			.transform(Transform.OPENAPI));
		c.add(ApiContractCase.builder("GET", "/openapi.{type}").variant("yaml").path("/openapi.yaml").accept(ApiContractCase.YAML)
			.transform(Transform.OPENAPI));

		// Errors on reads
		c.get("/devices/{id}", "unknown", () -> "/devices/99999999");
		c.get("/groups/{id}", "unknown", () -> "/groups/99999999");
		c.get("/tasks/{id}", "unknown", () -> "/tasks/99999999");
		c.get("/diagnostics/{id}", "unknown", () -> "/diagnostics/99999999");
		c.get("/scripts/{id}", "unknown", () -> "/scripts/99999999");
		c.get("/policies/{pid}/rules/{id}", "unknown", c.path("/policies/%d/rules/99999999", "policy.security"));
		c.get("/domains", "badoffset", () -> "/domains?offset=-1");
		c.get("/domains", "badlimit", () -> "/domains?limit=abc");
		c.getAs("/devices", "notoken", () -> "/devices", "");
		c.getAs("/devices", "badtoken", () -> "/devices", "not-a-valid-token");
		c.getAs("/users", "readonly", () -> "/users", ApiContractSeed.READONLY_TOKEN);
		c.getAs("/devicetypes", "refresh-readonly", () -> "/devicetypes?refresh=true", ApiContractSeed.READONLY_TOKEN);
		c.getAs("/devices", "readonly", () -> "/devices?limit=1", ApiContractSeed.READONLY_TOKEN);
	}

	private static void writes(Catalogue c) {
		domainWrites(c);
		credentialWrites(c);
		groupWrites(c);
		deviceWrites(c);
		policyWrites(c);
		diagnosticWrites(c);
		hookWrites(c);
		userWrites(c);
		scriptWrites(c);
		taskWrites(c);
		cleanupWrites(c);
	}

	private static void domainWrites(Catalogue c) {
		c.writeAs("POST", "/domains", "created", () -> "/domains", () -> """
			{"name": "Marseille DC", "description": "Marseille data center", "ipAddress": "10.255.2.1"}""",
			"new.domain", null);
		c.add(ApiContractCase.builder("POST", "/domains").variant("created-xml").path("/domains")
			.requestContentType(ApiContractCase.XML).accept(ApiContractCase.XML)
			.body("<domain><name>Nice DC</name><description>Nice</description><ipAddress>10.255.3.1</ipAddress></domain>")
			.afterReply(reply -> c.rememberXml("new.domain.xml", reply, "id")));
		c.write("POST", "/domains", "duplicate", () -> "/domains", () -> """
			{"name": "Marseille DC", "description": "Duplicate", "ipAddress": "10.255.2.2"}""");
		c.write("POST", "/domains", "blankname", () -> "/domains", () -> """
			{"name": "  ", "description": "No name", "ipAddress": "10.255.2.3"}""");
		c.write("POST", "/domains", "nullname", () -> "/domains", () -> """
			{"name": null, "description": null, "ipAddress": "10.255.2.4"}""");
		c.write("POST", "/domains", "badaddress", () -> "/domains", () -> """
			{"name": "Bad address", "description": "", "ipAddress": "not-an-ip"}""");
		c.write("PUT", "/domains/{id}", "updated", c.path("/domains/%d", "new.domain"), c.body("""
			{"id": %d, "name": "Marseille DC 2", "description": "Renamed", "ipAddress": "10.255.2.9"}""", "new.domain"));
		c.write("PUT", "/domains/{id}", "unknown", () -> "/domains/99999999", () -> """
			{"id": 99999999, "name": "Nope", "description": "", "ipAddress": "10.255.2.9"}""");
		c.write("DELETE", "/domains/{id}", "unknown", () -> "/domains/99999999", null);
		c.write("DELETE", "/domains/{id}", "inuse", c.path("/domains/%d", "domain.paris"), null);
	}

	private static void credentialWrites(Catalogue c) {
		c.writeAs("POST", "/credentialsets", "ssh", () -> "/credentialsets", c.body("""
			{"type": "SSH", "name": "New SSH account", "username": "newuser", "password": "newpass",
			 "superPassword": "newenable", "mgmtDomain": {"id": %d}}""", "domain.paris"), "new.cred", null);
		c.writeAs("POST", "/credentialsets", "snmp3", () -> "/credentialsets", () -> """
			{"type": "SNMP v3", "name": "New SNMP v3", "username": "snmpuser2", "authType": "MD5",
			 "authKey": "authkey2", "privType": "NONE"}""", "new.cred.snmp3", null);
		c.write("POST", "/credentialsets", "duplicate", () -> "/credentialsets", () -> """
			{"type": "SSH", "name": "New SSH account", "username": "other", "password": "other"}""");
		c.write("POST", "/credentialsets", "blankname", () -> "/credentialsets", () -> """
			{"type": "SSH", "name": "", "username": "other", "password": "other"}""");
		c.write("POST", "/credentialsets", "invaliddomain", () -> "/credentialsets", () -> """
			{"type": "Telnet", "name": "Bad domain", "username": "other", "password": "other",
			 "mgmtDomain": {"id": 99999999}}""");
		c.write("PUT", "/credentialsets/{id}", "updated", c.path("/credentialsets/%d", "new.cred"), c.body("""
			{"type": "SSH", "id": %d, "name": "New SSH account renamed", "username": "newuser2", "password": "=",
			 "superPassword": "changed-enable"}""", "new.cred"));
		c.write("PUT", "/credentialsets/{id}", "unknown", () -> "/credentialsets/99999999", () -> """
			{"type": "SSH", "id": 99999999, "name": "Nope", "username": "x"}""");
		c.writeAs("POST", "/vaultinstances", "created", () -> "/vaultinstances", () -> """
			{"type": "Vault KV v2", "name": "New vault", "baseUrl": "https://vault3.example.net:8200",
			 "authMethod": "APPROLE", "appRoleId": "role-9", "appRoleSecretId": "secret-9"}""", "new.vault", null);
		c.write("POST", "/vaultinstances", "duplicate", () -> "/vaultinstances", () -> """
			{"type": "Vault KV v2", "name": "New vault", "baseUrl": "https://vault3.example.net:8200",
			 "authMethod": "APPROLE", "appRoleId": "role-9", "appRoleSecretId": "secret-9"}""");
		c.write("POST", "/vaultinstances", "blankname", () -> "/vaultinstances", () -> """
			{"type": "Vault KV v2", "name": "", "baseUrl": "https://vault3.example.net:8200", "authMethod": "APPROLE"}""");
		c.write("PUT", "/vaultinstances/{id}", "updated", c.path("/vaultinstances/%d", "new.vault"), c.body("""
			{"type": "Vault KV v2", "id": %d, "name": "New vault renamed", "baseUrl": "https://vault4.example.net:8200",
			 "authMethod": "APPROLE", "appRoleId": "role-10", "appRoleSecretId": "="}""", "new.vault"));
		c.write("PUT", "/vaultinstances/{id}", "unknown", () -> "/vaultinstances/99999999", () -> """
			{"type": "Vault KV v2", "id": 99999999, "name": "Nope", "baseUrl": "https://x.example.net", "authMethod": "APPROLE"}""");
		c.write("POST", "/vaultinstances/test", "unreachable", () -> "/vaultinstances/test", () -> """
			{"type": "Vault KV v2", "name": "Unreachable", "baseUrl": "http://127.0.0.1:1",
			 "authMethod": "APPROLE", "appRoleId": "role", "appRoleSecretId": "secret"}""");
		c.write("DELETE", "/vaultinstances/{id}", "deleted", c.path("/vaultinstances/%d", "new.vault"), null);
		c.write("DELETE", "/vaultinstances/{id}", "unknown", () -> "/vaultinstances/99999999", null);
		c.write("DELETE", "/credentialsets/{id}", "deleted", c.path("/credentialsets/%d", "new.cred.snmp3"), null);
		c.write("DELETE", "/credentialsets/{id}", "unknown", () -> "/credentialsets/99999999", null);
	}

	private static void groupWrites(Catalogue c) {
		c.writeAs("POST", "/groups", "static", () -> "/groups", c.body("""
			{"type": "StaticDeviceGroup", "name": "New static group", "folder": "Tests",
			 "staticDevices": [%d, %d], "hiddenFromReports": false}""", "device.router1", "device.proxy"), "new.group.static", null);
		c.writeAs("POST", "/groups", "dynamic", () -> "/groups", () -> """
			{"type": "DynamicDeviceGroup", "name": "New dynamic group", "folder": "Tests",
			 "query": "[Name] CONTAINS \\"router\\"", "hiddenFromReports": true}""", "new.group.dynamic", null);
		c.write("POST", "/groups", "duplicate", () -> "/groups", () -> """
			{"type": "StaticDeviceGroup", "name": "New static group", "staticDevices": []}""");
		c.write("POST", "/groups", "blankname", () -> "/groups", () -> """
			{"type": "StaticDeviceGroup", "name": " ", "staticDevices": []}""");
		c.write("POST", "/groups", "nullname", () -> "/groups", () -> """
			{"type": "StaticDeviceGroup", "name": null, "staticDevices": []}""");
		c.write("POST", "/groups", "badtype", () -> "/groups", () -> """
			{"type": "UnknownGroup", "name": "Bad type", "staticDevices": []}""");
		c.write("POST", "/groups", "badquery", () -> "/groups", () -> """
			{"type": "DynamicDeviceGroup", "name": "Bad query", "query": "[Nope] ???"}""");
		c.write("POST", "/groups", "unknowndevice", () -> "/groups", () -> """
			{"type": "StaticDeviceGroup", "name": "Unknown device", "staticDevices": [99999999]}""");
		c.write("PUT", "/groups/{id}", "static", c.path("/groups/%d", "new.group.static"), c.body("""
			{"id": %d, "type": "StaticDeviceGroup", "name": "New static group renamed", "folder": "Tests/Sub",
			 "staticDevices": [%d], "hiddenFromReports": true}""", "new.group.static", "device.router2"));
		c.write("PUT", "/groups/{id}", "dynamic", c.path("/groups/%d", "new.group.dynamic"), c.body("""
			{"id": %d, "type": "DynamicDeviceGroup", "name": "New dynamic group", "folder": "",
			 "query": "[Family] CONTAINS \\"Cisco\\"", "hiddenFromReports": false}""", "new.group.dynamic"));
		c.write("PUT", "/groups/{id}", "unknown", () -> "/groups/99999999", () -> """
			{"id": 99999999, "type": "StaticDeviceGroup", "name": "Nope", "staticDevices": []}""");
		c.write("DELETE", "/groups/{id}", "unknown", () -> "/groups/99999999", null);
	}

	private static void deviceWrites(Catalogue c) {
		c.add(ApiContractCase.builder("POST", "/devices").variant("created").path("/devices").body(c.body("""
			{"autoDiscover": false, "ipAddress": "10.1.9.9", "domainId": %d, "name": "new-device",
			 "deviceType": "CiscoIOS12", "comments": "Created by the contract test", "tags": ["new", "test"],
			 "accesses": [
			   {"accessName": "ssh", "port": 2200, "address": "10.1.9.100", "globalCredentialSet": {"type": "SSH", "id": %d}},
			   {"accessName": "telnet", "specificCredentialSet": {"type": "Telnet", "name": "ignored",
			      "username": "telnetuser", "password": "telnetpass"}}]}""", "domain.paris", "cred.ssh"))
			.afterReply(reply -> c.rememberFrom("new.device", reply, "deviceId")));
		c.add(ApiContractCase.builder("POST", "/devices").variant("created-xml").path("/devices")
			.requestContentType(ApiContractCase.XML).accept(ApiContractCase.XML).body(c.body("""
			<RsNewDevice><autoDiscover>false</autoDiscover><ipAddress>10.1.9.20</ipAddress><domainId>%d</domainId>
			<name>xml-device</name><deviceType>SkyhighSWG</deviceType><options><fullBackup>true</fullBackup></options>
			<tags><tags>xml</tags></tags></RsNewDevice>""", "domain.paris"))
			.afterReply(reply -> {
				if (reply.status() == 201) {
					c.seed.remember("new.device.xml", reply.xmlLong("deviceId"));
				}
			}));
		c.write("POST", "/devices", "autodiscover", () -> "/devices", c.body("""
			{"autoDiscover": true, "ipAddress": "10.1.9.10", "domainId": %d, "comments": "discovered", "tags": ["disc"]}""",
			"domain.paris"));
		c.write("POST", "/devices", "autodiscover-nocommunity", () -> "/devices", c.body("""
			{"autoDiscover": true, "ipAddress": "10.1.9.11", "domainId": %d}""", "new.domain"));
		c.write("POST", "/devices", "duplicate", () -> "/devices", c.body("""
			{"autoDiscover": false, "ipAddress": "10.1.9.9", "domainId": %d, "name": "dup", "deviceType": "CiscoIOS12"}""",
			"domain.paris"));
		c.write("POST", "/devices", "baddomain", () -> "/devices", () -> """
			{"autoDiscover": false, "ipAddress": "10.1.9.30", "domainId": 99999999, "name": "x", "deviceType": "CiscoIOS12"}""");
		c.write("POST", "/devices", "baddriver", () -> "/devices", c.body("""
			{"autoDiscover": false, "ipAddress": "10.1.9.31", "domainId": %d, "name": "x", "deviceType": "NoSuchDriver"}""",
			"domain.paris"));
		c.write("POST", "/devices", "badaddress", () -> "/devices", c.body("""
			{"autoDiscover": false, "ipAddress": "not an address!", "domainId": %d, "name": "x", "deviceType": "CiscoIOS12"}""",
			"domain.paris"));
		c.write("POST", "/devices", "badtag", () -> "/devices", c.body("""
			{"autoDiscover": false, "ipAddress": "10.1.9.32", "domainId": %d, "name": "x", "deviceType": "CiscoIOS12",
			 "tags": ["bad tag!"]}""", "domain.paris"));
		c.write("PUT", "/devices/{id}", "updated", c.path("/devices/%d", "new.device"), c.body("""
			{"id": %d, "enabled": false, "comments": "Edited by the contract test", "ipAddress": "10.1.9.19",
			 "mgmtDomain": %d, "tags": ["edited"],
			 "accesses": [{"accessName": "ssh", "globalCredentialSet": {"type": "SSH", "id": %d}}], "options": {}}""",
			"new.device", "domain.lyon", "cred.sshvault"));
		c.write("PUT", "/devices/{id}", "options", c.path("/devices/%d", "device.proxy"), c.body("""
			{"id": %d, "enabled": true, "comments": "Proxy", "options": {"fullBackup": true}}""", "device.proxy"));
		c.write("PUT", "/devices/{id}", "badoptions", c.path("/devices/%d", "device.proxy"), c.body("""
			{"id": %d, "options": {"unknownOption": 1}}""", "device.proxy"));
		c.write("PUT", "/devices/{id}", "unknown", () -> "/devices/99999999", () -> """
			{"id": 99999999, "comments": "x"}""");
		c.write("POST", "/devices/search", "query", () -> "/devices/search", () -> """
			{"query": "[Family] CONTAINS \\"Cisco\\""}""");
		c.write("POST", "/devices/search", "paged", () -> "/devices/search?offset=1&limit=1", () -> """
			{"query": "[Name] CONTAINS \\"\\""}""");
		c.write("POST", "/devices/search", "badquery", () -> "/devices/search", () -> """
			{"query": "[Nope] ???"}""");
		c.add(ApiContractCase.builder("POST", "/devices/search").variant("xml").path("/devices/search")
			.accept(ApiContractCase.XML).requestContentType(ApiContractCase.XML)
			.body("<RsSearchCriteria><query>[Family] CONTAINS \"Cisco\"</query></RsSearchCriteria>"));
		c.write("DELETE", "/devices/{id}", "deleted", c.path("/devices/%d", "new.device"), null);
		c.write("DELETE", "/devices/{id}", "unknown", () -> "/devices/99999999", null);
	}

	private static void policyWrites(Catalogue c) {
		c.writeAs("POST", "/policies", "created", () -> "/policies", c.body("""
			{"name": "New policy", "targetGroups": [%d]}""", "group.static"), "new.policy", null);
		c.write("POST", "/policies", "duplicate", () -> "/policies", () -> """
			{"name": "New policy", "targetGroups": []}""");
		c.write("POST", "/policies", "blankname", () -> "/policies", () -> """
			{"name": " ", "targetGroups": []}""");
		c.write("PUT", "/policies/{id}", "updated", c.path("/policies/%d", "new.policy"), c.body("""
			{"id": %d, "name": "New policy renamed", "targetGroups": [%d, %d]}""", "new.policy", "group.static", "group.dynamic"));
		c.write("PUT", "/policies/{id}", "unknown", () -> "/policies/99999999", () -> """
			{"id": 99999999, "name": "Nope", "targetGroups": []}""");

		c.writeAs("POST", "/rules", "text", () -> "/rules", c.body("""
			{"type": "TextRule", "name": "New text rule", "policy": %d, "text": "ntp server", "regExp": false,
			 "invert": false, "matchAll": true, "anyBlock": false, "normalize": false, "context": "", "field": "runningConfig"}""",
			"new.policy"), "new.rule.text", null);
		c.writeAs("POST", "/rules", "js", () -> "/rules", c.body("""
			{"type": "JavaScriptRule", "name": "New JS rule", "policy": %d,
			 "script": "function check(device) { return { result: NONCONFORMING, comment: 'no' }; }"}""",
			"new.policy"), "new.rule.js", null);
		c.writeAs("POST", "/rules", "python", () -> "/rules", c.body("""
			{"type": "PythonRule", "name": "New Python rule", "policy": %d,
			 "script": "def check(device):\\n  return {'result': result_option.CONFORMING}"}""",
			"new.policy"), "new.rule.python", null);
		c.write("POST", "/rules", "duplicate", () -> "/rules", c.body("""
			{"type": "TextRule", "name": "New text rule", "policy": %d, "text": "x"}""", "new.policy"));
		c.write("POST", "/rules", "badtype", () -> "/rules", c.body("""
			{"type": "NoSuchRule", "name": "Bad type", "policy": %d}""", "new.policy"));
		c.write("POST", "/rules", "badpolicy", () -> "/rules", () -> """
			{"type": "TextRule", "name": "Bad policy", "policy": 99999999}""");
		c.write("POST", "/rules", "blankname", () -> "/rules", c.body("""
			{"type": "TextRule", "name": "", "policy": %d}""", "new.policy"));
		c.write("PUT", "/rules/{id}", "text", c.path("/rules/%d", "new.rule.text"), c.body("""
			{"id": %d, "type": "TextRule", "name": "New text rule renamed", "policy": %d, "text": "ntp server .*",
			 "regExp": true, "enabled": true, "invert": true, "matchAll": false, "anyBlock": true, "normalize": true,
			 "context": "", "field": "runningConfig", "driver": "CiscoIOS12",
			 "exemptions": {"%d": %d}}""", "new.rule.text", "new.policy", "device.router1", ApiContractSeed.D2040));
		c.write("PUT", "/rules/{id}", "js", c.path("/rules/%d", "new.rule.js"), c.body("""
			{"id": %d, "type": "JavaScriptRule", "name": "New JS rule", "policy": %d, "enabled": false,
			 "script": "function check(device) { return { result: CONFORMING }; }"}""", "new.rule.js", "new.policy"));
		c.write("PUT", "/rules/{id}", "unknown", () -> "/rules/99999999", () -> """
			{"id": 99999999, "type": "TextRule", "name": "Nope", "policy": 1}""");
		c.write("POST", "/rules/test", "text", () -> "/rules/test", c.body("""
			{"type": "TextRule", "name": "Test", "policy": %d, "text": "hostname", "regExp": false, "invert": false,
			 "matchAll": true, "anyBlock": false, "normalize": false, "context": "", "field": "runningConfig", "device": %d}""",
			"policy.security", "device.router1"));
		c.write("POST", "/rules/test", "js", () -> "/rules/test", c.body("""
			{"type": "JavaScriptRule", "name": "Test", "policy": %d, "device": %d,
			 "script": "function check(device) { return { result: CONFORMING, comment: 'tested' }; }"}""",
			"policy.security", "device.router1"));
		c.write("POST", "/rules/test", "jserror", () -> "/rules/test", c.body("""
			{"type": "JavaScriptRule", "name": "Test", "policy": %d, "device": %d,
			 "script": "function check(device) { throw new Error('boom'); }"}""",
			"policy.security", "device.router1"));
		c.write("POST", "/rules/test", "python", () -> "/rules/test", c.body("""
			{"type": "PythonRule", "name": "Test", "policy": %d, "device": %d,
			 "script": "def check(device):\\n  return {'result': result_option.NONCONFORMING, 'comment': 'py'}"}""",
			"policy.security", "device.router1"));
		c.write("POST", "/rules/test", "unknowndevice", () -> "/rules/test", c.body("""
			{"type": "TextRule", "name": "Test", "policy": %d, "text": "x", "device": 99999999}""", "policy.security"));
		c.write("DELETE", "/rules/{id}", "deleted", c.path("/rules/%d", "new.rule.python"), null);
		c.write("DELETE", "/rules/{id}", "unknown", () -> "/rules/99999999", null);

		c.writeAs("POST", "/softwarerules", "created", () -> "/softwarerules", c.body("""
			{"group": %d, "driver": "CiscoIOS12", "version": "17.1", "versionRegExp": false, "family": "",
			 "familyRegExp": false, "partNumber": "", "partNumberRegExp": false, "level": "BRONZE"}""", "group.static"),
			"new.softwarerule", null);
		c.writeAs("POST", "/softwarerules", "second", () -> "/softwarerules", () -> """
			{"group": -1, "driver": "", "version": "18.*", "versionRegExp": true, "family": "", "familyRegExp": false,
			 "partNumber": "", "partNumberRegExp": false, "level": "GOLD"}""", "new.softwarerule2", null);
		c.write("POST", "/softwarerules", "badgroup", () -> "/softwarerules", () -> """
			{"group": 99999999, "driver": "", "version": "1", "level": "GOLD"}""");
		c.write("PUT", "/softwarerules/{id}", "updated", c.path("/softwarerules/%d", "new.softwarerule"), c.body("""
			{"id": %d, "group": %d, "driver": "CiscoIOS12", "version": "17.2", "versionRegExp": false, "family": "Cisco 2900",
			 "familyRegExp": false, "partNumber": "CISCO2911/K9", "partNumberRegExp": false, "level": "SILVER"}""",
			"new.softwarerule", "group.dynamic"));
		c.write("PUT", "/softwarerules/{id}", "unknown", () -> "/softwarerules/99999999", () -> """
			{"id": 99999999, "group": -1, "version": "1", "level": "GOLD"}""");
		c.write("POST", "/softwarerules/{id}/sort", "before", c.path("/softwarerules/%d/sort?next=%d", "new.softwarerule2",
			"rule.software.gold"), null);
		c.write("POST", "/softwarerules/{id}/sort", "last", c.path("/softwarerules/%d/sort", "rule.software.gold"), null);
		c.write("POST", "/softwarerules/{id}/sort", "unknown", () -> "/softwarerules/99999999/sort", null);
		c.write("DELETE", "/softwarerules/{id}", "deleted", c.path("/softwarerules/%d", "new.softwarerule"), null);
		c.write("DELETE", "/softwarerules/{id}", "unknown", () -> "/softwarerules/99999999", null);

		c.writeAs("POST", "/hardwarerules", "created", () -> "/hardwarerules", c.body("""
			{"group": %d, "driver": "CiscoIOS12", "partNumber": "CISCO29.*", "partNumberRegExp": true,
			 "family": "", "familyRegExp": false, "endOfSale": %d, "endOfLife": %d}""", "group.static",
			ApiContractSeed.D2040, ApiContractSeed.D2040 + 86400000L), "new.hardwarerule", null);
		c.write("POST", "/hardwarerules", "badgroup", () -> "/hardwarerules", () -> """
			{"group": 99999999, "driver": "", "partNumber": "x", "endOfSale": 1}""");
		c.write("PUT", "/hardwarerules/{id}", "updated", c.path("/hardwarerules/%d", "new.hardwarerule"), c.body("""
			{"id": %d, "group": -1, "driver": "", "partNumber": "", "partNumberRegExp": false,
			 "family": "Cisco.*", "familyRegExp": true, "endOfSale": null, "endOfLife": %d}""", "new.hardwarerule",
			ApiContractSeed.D2040));
		c.write("PUT", "/hardwarerules/{id}", "unknown", () -> "/hardwarerules/99999999", () -> """
			{"id": 99999999, "group": -1, "driver": "", "partNumber": "x"}""");
		c.write("DELETE", "/hardwarerules/{id}", "deleted", c.path("/hardwarerules/%d", "new.hardwarerule"), null);
		c.write("DELETE", "/hardwarerules/{id}", "unknown", () -> "/hardwarerules/99999999", null);

		c.write("DELETE", "/policies/{id}", "deleted", c.path("/policies/%d", "new.policy"), null);
		c.write("DELETE", "/policies/{id}", "unknown", () -> "/policies/99999999", null);
	}

	private static void diagnosticWrites(Catalogue c) {
		c.writeAs("POST", "/diagnostics", "simple", () -> "/diagnostics", c.body("""
			{"type": "SimpleDiagnostic", "name": "New simple diagnostic", "enabled": true, "targetGroup": %d,
			 "resultType": "LONGTEXT", "deviceDriver": "CiscoIOS12", "cliMode": "enable", "command": "show clock",
			 "modifierPattern": "", "modifierReplacement": ""}""", "group.static"), "new.diag", null);
		c.writeAs("POST", "/diagnostics", "js", () -> "/diagnostics", () -> """
			{"type": "JavaScriptDiagnostic", "name": "New JS diagnostic", "enabled": true, "targetGroup": -1,
			 "resultType": "NUMERIC", "script": "function diagnose(cli, device, diagnostic) { diagnostic.set(1); }"}""",
			"new.diag.js", null);
		c.writeAs("POST", "/diagnostics", "python", () -> "/diagnostics", () -> """
			{"type": "PythonDiagnostic", "name": "New Python diagnostic", "enabled": false, "targetGroup": -1,
			 "resultType": "BINARY", "script": "def diagnose(cli, device, diagnostic):\\n  diagnostic.set(True)"}""",
			"new.diag.python", null);
		c.write("POST", "/diagnostics", "duplicate", () -> "/diagnostics", () -> """
			{"type": "PythonDiagnostic", "name": "New Python diagnostic", "enabled": false, "targetGroup": -1,
			 "resultType": "BINARY", "script": "x = 1"}""");
		c.write("POST", "/diagnostics", "blankname", () -> "/diagnostics", () -> """
			{"type": "PythonDiagnostic", "name": "", "enabled": false, "targetGroup": -1, "resultType": "BINARY", "script": "x"}""");
		c.write("POST", "/diagnostics", "badtype", () -> "/diagnostics", () -> """
			{"type": "NoSuchDiagnostic", "name": "Bad type", "enabled": false, "targetGroup": -1, "resultType": "BINARY"}""");
		c.write("POST", "/diagnostics", "badresulttype", () -> "/diagnostics", () -> """
			{"type": "PythonDiagnostic", "name": "Bad result type", "enabled": false, "targetGroup": -1,
			 "resultType": "NOPE", "script": "x"}""");
		c.write("POST", "/diagnostics", "badgroup", () -> "/diagnostics", () -> """
			{"type": "PythonDiagnostic", "name": "Bad group", "enabled": false, "targetGroup": 99999999,
			 "resultType": "TEXT", "script": "x"}""");
		c.write("PUT", "/diagnostics/{id}", "simple", c.path("/diagnostics/%d", "new.diag"), c.body("""
			{"id": %d, "type": "SimpleDiagnostic", "name": "New simple diagnostic renamed", "enabled": false, "targetGroup": %d,
			 "resultType": "LONGTEXT", "deviceDriver": "CiscoIOS12", "cliMode": "enable", "command": "show clock detail",
			 "modifierPattern": "(.*)", "modifierReplacement": "$1"}""", "new.diag", "group.dynamic"));
		c.write("PUT", "/diagnostics/{id}", "js", c.path("/diagnostics/%d", "new.diag.js"), c.body("""
			{"id": %d, "type": "JavaScriptDiagnostic", "name": "New JS diagnostic", "enabled": true, "targetGroup": -1,
			 "resultType": "NUMERIC", "script": "function diagnose(cli, device, diagnostic) { diagnostic.set(2); }"}""",
			"new.diag.js"));
		c.write("PUT", "/diagnostics/{id}", "unknown", () -> "/diagnostics/99999999", () -> """
			{"id": 99999999, "type": "PythonDiagnostic", "name": "Nope", "enabled": false, "targetGroup": -1,
			 "resultType": "TEXT", "script": "x"}""");
		c.write("DELETE", "/diagnostics/{id}", "deleted", c.path("/diagnostics/%d", "new.diag.python"), null);
		c.write("DELETE", "/diagnostics/{id}", "unknown", () -> "/diagnostics/99999999", null);
	}

	private static void hookWrites(Catalogue c) {
		c.writeAs("POST", "/hooks", "created", () -> "/hooks", () -> """
			{"type": "Web", "name": "New hook", "enabled": true, "action": "POST_YAML", "url": "https://example.net/new",
			 "httpsCaTrustMode": "SYSTEM_TRUSTSTORE",
			 "triggers": [{"type": "POST_TASK", "item": "TakeSnapshotTask"}]}""", "new.hook", null);
		c.write("POST", "/hooks", "duplicate", () -> "/hooks", () -> """
			{"type": "Web", "name": "New hook", "enabled": true, "action": "POST_JSON", "url": "https://example.net/new"}""");
		c.write("POST", "/hooks", "blankname", () -> "/hooks", () -> """
			{"type": "Web", "name": "", "enabled": true, "action": "POST_JSON", "url": "https://example.net/new"}""");
		c.write("POST", "/hooks", "badurl", () -> "/hooks", () -> """
			{"type": "Web", "name": "Bad URL", "enabled": true, "action": "POST_JSON", "url": "not a url"}""");
		c.write("POST", "/hooks", "noaction", () -> "/hooks", () -> """
			{"type": "Web", "name": "No action", "enabled": true, "url": "https://example.net/new"}""");
		c.write("PUT", "/hooks/{id}", "updated", c.path("/hooks/%d", "new.hook"), c.body("""
			{"type": "Web", "id": %d, "name": "New hook renamed", "enabled": false, "action": "POST_JSON",
			 "url": "https://example.net/renamed",
			 "triggers": [{"type": "POST_TASK", "item": "CheckComplianceTask"}, {"type": "POST_TASK", "item": "RunDiagnosticsTask"}]}""",
			"new.hook"));
		c.write("PUT", "/hooks/{id}", "unknown", () -> "/hooks/99999999", () -> """
			{"type": "Web", "id": 99999999, "name": "Nope", "enabled": true, "action": "POST_JSON", "url": "https://example.net/x"}""");
		c.write("DELETE", "/hooks/{id}", "deleted", c.path("/hooks/%d", "new.hook"), null);
		c.write("DELETE", "/hooks/{id}", "unknown", () -> "/hooks/99999999", null);
	}

	private static void userWrites(Catalogue c) {
		c.writeAs("POST", "/users", "created", () -> "/users", () -> """
			{"username": "newuser", "password": "%s", "level": 100, "local": true}""".formatted(ApiContractSeed.USER_PASSWORD),
			"new.user", null);
		c.write("POST", "/users", "duplicate", () -> "/users", () -> """
			{"username": "newuser", "password": "%s", "level": 100, "local": true}""".formatted(ApiContractSeed.USER_PASSWORD));
		c.write("POST", "/users", "weakpassword", () -> "/users", () -> """
			{"username": "weakuser", "password": "a", "level": 10, "local": true}""");
		c.write("POST", "/users", "blankname", () -> "/users", () -> """
			{"username": " ", "password": "%s", "level": 10, "local": true}""".formatted(ApiContractSeed.USER_PASSWORD));
		c.write("PUT", "/users/{id}", "updated", c.path("/users/%d", "new.user"), c.body("""
			{"id": %d, "username": "newuser", "level": 500, "local": true}""", "new.user"));
		c.write("PUT", "/users/{id}", "password", c.path("/users/%d", "new.user"), c.body("""
			{"id": %d, "username": "newuser", "password": "An0ther!Passw0rd#2", "level": 500, "local": true}""", "new.user"));
		c.write("PUT", "/users/{id}", "unknown", () -> "/users/99999999", () -> """
			{"id": 99999999, "username": "nope", "level": 10, "local": true}""");
		c.add(ApiContractCase.builder("POST", "/user").variant("login").path("/user").token("").body(() -> """
			{"username": "writer", "password": "%s"}""".formatted(ApiContractSeed.USER_PASSWORD)));
		c.add(ApiContractCase.builder("POST", "/user").variant("badlogin").path("/user").token("").body(() -> """
			{"username": "writer", "password": "wrong"}"""));
		c.add(ApiContractCase.builder("POST", "/user").variant("unknownuser").path("/user").token("").body(() -> """
			{"username": "nobody", "password": "wrong"}"""));
		c.write("PUT", "/user/{id}", "password", () -> "/user/0", () -> """
			{"username": "writer", "password": "%s", "newPassword": "Ch4nged!Passw0rd#3"}""".formatted(ApiContractSeed.USER_PASSWORD));
		c.write("PUT", "/user/{id}", "badpassword", () -> "/user/0", () -> """
			{"username": "writer", "password": "wrong", "newPassword": "Ch4nged!Passw0rd#4"}""");
		c.write("DELETE", "/user/{id}", "logout", () -> "/user/0", null);
		c.write("DELETE", "/users/{id}", "deleted", c.path("/users/%d", "new.user"), null);
		c.write("DELETE", "/users/{id}", "unknown", () -> "/users/99999999", null);

		c.writeAs("POST", "/apitokens", "created", () -> "/apitokens", () -> """
			{"description": "New token", "token": "Ab3dEf6hIj9lMn2pQr5tUv8xYz1Bc4De", "level": 10}""",
			"new.token", null);
		c.write("POST", "/apitokens", "duplicate", () -> "/apitokens", () -> """
			{"description": "Duplicate token", "token": "Ab3dEf6hIj9lMn2pQr5tUv8xYz1Bc4De", "level": 10}""");
		c.write("POST", "/apitokens", "shorttoken", () -> "/apitokens", () -> """
			{"description": "Short token", "token": "abc", "level": 10}""");
		c.writeAs("POST", "/apitokens", "forbidden", () -> "/apitokens", () -> """
			{"description": "Forbidden", "token": "Gh7jKl0nOp3rSt6vWx9zAb2dEf5hIj8L", "level": 10}""", null,
			ApiContractSeed.READWRITE_TOKEN);
		c.write("DELETE", "/apitokens/{id}", "deleted", c.path("/apitokens/%d", "new.token"), null);
		c.write("DELETE", "/apitokens/{id}", "unknown", () -> "/apitokens/99999999", null);
	}

	private static void scriptWrites(Catalogue c) {
		String script = "function run(cli, device) {\\n  cli.command('show clock');\\n}";
		c.write("POST", "/scripts", "validateonly", () -> "/scripts?validateonly=true", () -> """
			{"name": "Validated only", "deviceDriver": "CiscoIOS12", "script": "%s"}""".formatted(script));
		c.writeAs("POST", "/scripts", "created", () -> "/scripts", () -> """
			{"name": "New script", "deviceDriver": "CiscoIOS12", "folder": "Tests", "script": "%s"}""".formatted(script),
			"new.script", null);
		c.write("POST", "/scripts", "duplicate", () -> "/scripts", () -> """
			{"name": "New script", "deviceDriver": "CiscoIOS12", "folder": "Tests", "script": "%s"}""".formatted(script));
		c.write("POST", "/scripts", "baddriver", () -> "/scripts", () -> """
			{"name": "Bad driver", "deviceDriver": "NoSuchDriver", "script": "%s"}""".formatted(script));
		c.write("POST", "/scripts", "blankname", () -> "/scripts", () -> """
			{"name": "", "deviceDriver": "CiscoIOS12", "script": "%s"}""".formatted(script));
		c.write("POST", "/scripts", "syntaxerror", () -> "/scripts", () -> """
			{"name": "Syntax error", "deviceDriver": "CiscoIOS12", "script": "function run( {"}""");
		c.write("POST", "/scripts", "syntaxerror-definitions", () -> "/scripts", () -> """
			{"name": "Syntax error", "deviceDriver": "CiscoIOS12", "script": "function run( {", "userInputDefinitions": {}}""");
		c.write("DELETE", "/scripts/{id}", "deleted", c.path("/scripts/%d", "new.script"), null);
		c.write("DELETE", "/scripts/{id}", "unknown", () -> "/scripts/99999999", null);
	}

	private static void taskWrites(Catalogue c) {
		String at = "\"scheduleType\": \"AT\", \"scheduleReference\": " + ApiContractSeed.D2040;
		c.writeAs("POST", "/tasks", "snapshot", () -> "/tasks", c.body("""
			{"type": "TakeSnapshotTask", "device": %d, "comments": "Snapshot from API", "debugEnabled": true, @AT@}""".replace("@AT@", at),
			"device.router1"), "new.task", null);
		c.write("POST", "/tasks", "compliance", () -> "/tasks", c.body("""
			{"type": "CheckComplianceTask", "device": %d, "comments": "Compliance from API", @AT@}""".replace("@AT@", at), "device.router2"));
		c.write("POST", "/tasks", "diagnostics", () -> "/tasks", c.body("""
			{"type": "RunDiagnosticsTask", "device": %d, "comments": "Diagnostics from API", "dontCheckCompliance": true, @AT@}""".replace("@AT@", at),
			"device.router2"));
		c.write("POST", "/tasks", "script", () -> "/tasks", c.body("""
			{"type": "RunDeviceScriptTask", "device": %d, "driver": "CiscoIOS12", "comments": "Script from API",
			 "script": "function run(cli, device) { cli.command('show clock'); }", @AT@}""".replace("@AT@", at), "device.router1"));
		c.write("POST", "/tasks", "groupsnapshot", () -> "/tasks", c.body("""
			{"type": "TakeGroupSnapshotTask", "group": %d, "comments": "Group snapshot from API", "limitToOutofdateDeviceHours": 12,
			 "dontRunDiagnostics": true, "dontCheckCompliance": true, @AT@}""".replace("@AT@", at), "group.static"));
		c.write("POST", "/tasks", "groupcompliance", () -> "/tasks", c.body("""
			{"type": "CheckGroupComplianceTask", "group": %d, "comments": "Group compliance from API", @AT@}""".replace("@AT@", at),
			"group.dynamic"));
		c.write("POST", "/tasks", "groupsoftware", () -> "/tasks", c.body("""
			{"type": "CheckGroupSoftwareTask", "group": %d, "comments": "Group software from API", @AT@}""".replace("@AT@", at), "group.dynamic"));
		c.write("POST", "/tasks", "groupdiagnostics", () -> "/tasks", c.body("""
			{"type": "RunGroupDiagnosticsTask", "group": %d, "comments": "Group diagnostics from API", @AT@}""".replace("@AT@", at),
			"group.dynamic"));
		c.write("POST", "/tasks", "groupscript", () -> "/tasks", c.body("""
			{"type": "RunDeviceGroupScriptTask", "group": %d, "driver": "CiscoIOS12", "comments": "Group script from API",
			 "script": "function run(cli, device) { cli.command('show clock'); }", "scheduleMode": "SEQUENTIAL", "stopOnFailure": true,
			 @AT@}""".replace("@AT@", at), "group.dynamic"));
		c.write("POST", "/tasks", "scan", () -> "/tasks", c.body("""
			{"type": "ScanSubnetsTask", "domain": %d, "subnets": "10.8.0.0/24", "comments": "Scan from API", @AT@}""".replace("@AT@", at),
			"domain.paris"));
		c.write("POST", "/tasks", "purge", () -> "/tasks", c.body("""
			{"type": "PurgeDatabaseTask", "daysToPurge": 30, "configDaysToPurge": 60, "configSizeToPurge": 10,
			 "configKeepDays": 5, "moduleDaysToPurge": 40, "comments": "Purge from API", "group": %d, @AT@}""".replace("@AT@", at), "group.static"));
		c.write("POST", "/tasks", "daily", () -> "/tasks", c.body("""
			{"type": "TakeSnapshotTask", "device": %d, "comments": "Daily from API", "scheduleType": "DAILY",
			 "scheduleFactor": 2, "scheduleReference": %d}""", "device.router2", ApiContractSeed.D2040));
		c.write("POST", "/tasks", "badtype", () -> "/tasks", () -> """
			{"type": "NoSuchTask", "comments": "x"}""");
		c.write("POST", "/tasks", "baddevice", () -> "/tasks", () -> """
			{"type": "TakeSnapshotTask", "device": 99999999}""");
		c.write("POST", "/tasks", "badgroup", () -> "/tasks", () -> """
			{"type": "TakeGroupSnapshotTask", "group": 99999999}""");
		c.write("POST", "/tasks", "badsubnet", () -> "/tasks", c.body("""
			{"type": "ScanSubnetsTask", "domain": %d, "subnets": "not-a-subnet"}""", "domain.paris"));
		c.write("POST", "/tasks", "subnettoobig", () -> "/tasks", c.body("""
			{"type": "ScanSubnetsTask", "domain": %d, "subnets": "10.0.0.0/8"}""", "domain.paris"));
		c.write("POST", "/tasks", "badpurge", () -> "/tasks", () -> """
			{"type": "PurgeDatabaseTask", "daysToPurge": 1}""");
		c.writeAs("POST", "/tasks", "purge-forbidden", () -> "/tasks", () -> """
			{"type": "PurgeDatabaseTask", "daysToPurge": 30}""", null, ApiContractSeed.READWRITE_TOKEN);
		c.writeAs("POST", "/tasks", "script-forbidden", () -> "/tasks", c.body("""
			{"type": "RunDeviceScriptTask", "device": %d, "driver": "CiscoIOS12", "script": "x"}""", "device.router1"), null,
			ApiContractSeed.READONLY_TOKEN);
		c.write("PUT", "/tasks/{id}", "cancelled", c.path("/tasks/%d", "new.task"), () -> """
			{"cancelled": true}""");
		c.write("PUT", "/tasks/{id}", "notcancellable", c.path("/tasks/%d", "task.snapshot"), () -> """
			{"cancelled": true}""");
		c.write("PUT", "/tasks/{id}", "unknown", () -> "/tasks/99999999", () -> """
			{"cancelled": true}""");
	}

	private static void cleanupWrites(Catalogue c) {
		c.write("DELETE", "/groups/{id}", "static", c.path("/groups/%d", "new.group.static"), null);
		c.write("DELETE", "/groups/{id}", "dynamic", c.path("/groups/%d", "new.group.dynamic"), null);
		c.write("DELETE", "/domains/{id}", "deleted", c.path("/domains/%d", "new.domain"), null);
	}
}
