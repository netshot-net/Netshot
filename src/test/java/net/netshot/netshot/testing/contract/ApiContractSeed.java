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
package net.netshot.netshot.testing.contract;

import static net.netshot.netshot.platform.database.schema.Tables.DEBUG_LOG;
import static net.netshot.netshot.platform.database.schema.Tables.TASK;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Assertions;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.ApiTokenRepository;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.RuleRepository;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.diagnostic.DiagnosticRepository;
import net.netshot.netshot.diagnostic.DiagnosticResultRepository;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.domain.DomainRepository;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigNumericAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.platform.net.NetworkAddress;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.TestDrivers;

/**
 * Deterministic dataset used by the API contract tests.
 *
 * <p>Rules for determinism: a fresh database is used, objects are created one
 * after the other in a fixed order (IDs are database identity values, so they
 * come out the same on every run), every object whose date is exposed by the API
 * gets a fixed date (in 2020-2022 or 2040, far away from the "now" window the
 * normalizer masks), and entities with children are persisted child by child
 * rather than through a cascade over an unordered set.</p>
 */
public final class ApiContractSeed {

	public static final String ADMIN_TOKEN = "hLXg3ABu1qWJPle3Z3Z22X0aLkx5T354";
	public static final String READONLY_TOKEN = "mwAPEe0mQlBvKuUYYS5MiFsmnVRWZpca";
	public static final String OPERATOR_TOKEN = "vMLKSPWMq9hFRtKkhXbvHhveXEn3Y4BZ";
	public static final String READWRITE_TOKEN = "rCSrr3S3PGsLmi54HFw6a3PbZClpCivk";
	public static final String EXECUTE_TOKEN = "PXlJ4r8VvTObn6hyknI1lwOoxEPJjCDO";

	/** Password of the seeded local users. */
	public static final String USER_PASSWORD = "Str0ng!Passw0rd#1";

	/** Fixed dates (milliseconds since 1970). */
	public static final long D2020 = 1577880000000L; // 2020-01-01T12:00:00Z
	public static final long D2021 = 1609502400000L; // 2021-01-01T12:00:00Z
	public static final long D2022 = 1641038400000L; // 2022-01-01T12:00:00Z
	public static final long D2040 = 2208988800000L; // 2040-01-01T00:00:00Z

	private final String apiUrl;
	private final HttpClient httpClient;
	private final Map<String, Long> ids = new HashMap<>();

	public ApiContractSeed(String apiUrl, HttpClient httpClient) {
		this.apiUrl = apiUrl;
		this.httpClient = httpClient;
	}

	/**
	 * Gets the ID of a seeded object.
	 * @param name the seed name of the object
	 * @return its ID
	 */
	public long id(String name) {
		Long id = this.ids.get(name);
		if (id == null) {
			throw new IllegalStateException("Unknown seeded object " + name);
		}
		return id;
	}

	public void remember(String name, long id) {
		this.ids.put(name, id);
	}

	/**
	 * Calls the API as administrator, expecting a successful response.
	 * @param method the HTTP method
	 * @param path the path
	 * @param body the JSON body (or null)
	 * @return the response body
	 */
	public JsonNode call(String method, String path, String body) {
		try {
			HttpRequest.Builder request = HttpRequest.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.uri(URI.create(this.apiUrl + path))
				.header("Accept", "application/json")
				.header("X-Netshot-API-Token", ADMIN_TOKEN);
			if (body != null) {
				request.header("Content-Type", "application/json");
				request.method(method, HttpRequest.BodyPublishers.ofString(body));
			}
			else {
				request.method(method, HttpRequest.BodyPublishers.noBody());
			}
			HttpResponse<String> response = this.httpClient.send(request.build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			Assertions.assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
				"Seed call %s %s failed with %d: %s".formatted(method, path, response.statusCode(), response.body()));
			return response.body().isBlank() ? null : ApiContractGolden.parseJson(response.body());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}

	/**
	 * POSTs an object and remembers its ID.
	 * @param name the name to remember the ID under
	 * @param path the API path to post to
	 * @param body the JSON body
	 */
	private void create(String name, String path, String body) {
		JsonNode created = this.call("POST", path, body);
		this.remember(name, created.get("id").asLong());
	}

	public void seed() throws Exception {
		this.seedAuth();
		this.seedDomains();
		this.seedVault();
		this.seedCredentialSets();
		this.seedDevices();
		this.seedGroups();
		this.seedPolicies();
		this.seedDiagnostics();
		this.seedHooks();
		this.seedScripts();
		this.seedUsers();
		this.seedComplianceAndDiagnosticResults();
		this.seedTasks();
	}

	private void seedAuth() {
		ApiTokenRepository repository = new ApiTokenRepository();
		new Transactions().run(dsl -> {
			// Remove default admin account
			dsl.deleteFrom(Tables.USER).execute();
			Object[][] tokens = {
				{UiUser.LEVEL_READONLY, READONLY_TOKEN},
				{UiUser.LEVEL_OPERATOR, OPERATOR_TOKEN},
				{UiUser.LEVEL_READWRITE, READWRITE_TOKEN},
				{UiUser.LEVEL_EXECUTEREADWRITE, EXECUTE_TOKEN},
				{UiUser.LEVEL_ADMIN, ADMIN_TOKEN},
			};
			for (Object[] token : tokens) {
				repository.add(dsl, ApiToken.of("Test Token - level %d".formatted((Integer) token[0]),
					(String) token[1], (Integer) token[0]));
			}
		});
	}

	private void seedDomains() {
		this.create("domain.paris", "/domains", """
			{"name": "Paris DC", "description": "Paris data center", "ipAddress": "10.255.0.1"}""");
		this.create("domain.lyon", "/domains", """
			{"name": "Lyon DC", "description": "Lyon data center", "ipAddress": "10.255.1.1"}""");
	}

	private void seedVault() {
		this.create("vault.main", "/vaultinstances", """
			{"type": "Vault KV v2", "name": "Main vault", "baseUrl": "https://vault.example.net:8200",
			 "namespace": "netshot", "kvMountPath": "kv", "authMethod": "APPROLE",
			 "appRoleMountPath": "approle", "appRoleId": "role-1234", "appRoleSecretId": "secret-5678"}""");
		this.create("vault.jwt", "/vaultinstances", """
			{"type": "Vault KV v2", "name": "JWT vault", "baseUrl": "https://vault2.example.net:8200",
			 "httpsCaTrustMode": "TRUST_ANY", "authMethod": "JWT", "jwtMountPath": "jwt",
			 "jwtIdpTokenEndpoint": "https://idp.example.net/token", "jwtClientId": "netshot",
			 "jwtClientSecret": "client-secret", "jwtVaultRole": "netshot-role", "jwtScope": "vault"}""");
	}

	private void seedCredentialSets() {
		this.create("cred.snmp1", "/credentialsets", """
			{"type": "SNMP v1", "name": "SNMP v1 community", "community": "public-v1"}""");
		this.create("cred.snmp2", "/credentialsets", """
			{"type": "SNMP v2", "name": "SNMP v2 community", "community": "public-v2",
			 "mgmtDomain": {"id": %d}}""".formatted(this.id("domain.paris")));
		this.create("cred.snmp3", "/credentialsets", """
			{"type": "SNMP v3", "name": "SNMP v3 account", "username": "snmpuser",
			 "authType": "SHA", "authKey": "authkey123", "privType": "AES128", "privKey": "privkey456"}""");
		this.create("cred.ssh", "/credentialsets", """
			{"type": "SSH", "name": "SSH account", "username": "admin", "password": "ssh-pass",
			 "superPassword": "ssh-enable"}""");
		this.create("cred.sshkey", "/credentialsets", """
			{"type": "SSH Key", "name": "SSH key account", "username": "keyuser",
			 "privateKey": "-----BEGIN KEY-----", "password": "key-pass",
			 "superPassword": "key-enable", "mgmtDomain": {"id": %d}}""".formatted(this.id("domain.lyon")));
		this.create("cred.telnet", "/credentialsets", """
			{"type": "Telnet", "name": "Telnet account", "username": "telnetuser", "password": "telnet-pass",
			 "superPassword": "telnet-enable"}""");
		this.create("cred.http", "/credentialsets", """
			{"type": "HTTP", "name": "HTTP account", "username": "apiuser", "password": "http-pass"}""");
		this.create("cred.sshvault", "/credentialsets", """
			{"type": "SSH", "name": "SSH account from vault",
			 "usernameVaultInstanceId": %d, "usernameVaultPath": "netshot/ssh/username",
			 "passwordVaultInstanceId": %d, "passwordVaultPath": "netshot/ssh/password"}"""
			.formatted(this.id("vault.main"), this.id("vault.main")));
	}

	// -------------------------------------------------------------------
	// Devices (inserted directly: the API creates devices through tasks)
	// -------------------------------------------------------------------

	private static Network4Address ip4(String address, int prefixLength) {
		try {
			return new Network4Address(address, prefixLength);
		}
		catch (UnknownHostException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Network6Address ip6(String address, int prefixLength) {
		try {
			return new Network6Address(address, prefixLength);
		}
		catch (UnknownHostException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Config config(long date, String author, String runningConfig, boolean full) {
		List<ConfigAttribute> attributes = new ArrayList<>();
		attributes.add(new ConfigLongTextAttribute("runningConfig", runningConfig));
		attributes.add(new ConfigTextAttribute("iosVersion", "16.1.6"));
		if (full) {
			attributes.add(new ConfigNumericAttribute("configSize", 1234.5));
			attributes.add(new ConfigBinaryAttribute("configSaved", true));
			attributes.add(new ConfigBinaryFileAttribute("backupFile", "00000000-0000-0000-0000-%012d".formatted(date % 1000000000000L),
				"backup-%d.tar.gz".formatted(date), 4096, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
		}
		return Config.builder().author(author).changeDate(Instant.ofEpochMilli(date)).attributes(attributes).build();
	}

	private static List<DeviceAttribute> deviceAttributes(boolean full) {
		List<DeviceAttribute> attributes = new ArrayList<>(List.of(
			new DeviceNumericAttribute("mainMemorySize", 2048.0),
			new DeviceTextAttribute("configRegister", "0x1202"),
			new DeviceBinaryAttribute("configurationSaved", true)));
		if (full) {
			attributes.add(new DeviceLongTextAttribute("inventoryNotes",
				"Line 1 of a long text attribute\nLine 2 with special chars: <>&\"' éè\n" + "x".repeat(300)));
		}
		return attributes;
	}

	private static Device.DeviceBuilder device(String name, String driver, String address, Domain domain, long createdAt) {
		return Device.create(driver, address, domain, "seed").toBuilder()
			.name(name)
			.createdDate(Instant.ofEpochMilli(createdAt))
			.changeDate(Instant.ofEpochMilli(createdAt));
	}

	private static Module module(String slot, String partNumber, String serialNumber) {
		// (No first and last seen dates, as the former seed)
		return Module.builder().slot(slot).partNumber(partNumber).serialNumber(serialNumber).build();
	}

	private static NetworkInterface networkInterface(String name, String vrf, boolean enabled, boolean level3, String description,
			NetworkAddress... addresses) {
		Set<Network4Address> ip4Addresses = new LinkedHashSet<>();
		Set<Network6Address> ip6Addresses = new LinkedHashSet<>();
		for (NetworkAddress address : addresses) {
			NetworkInterface.addIpAddress(ip4Addresses, ip6Addresses, address);
		}
		return NetworkInterface.builder().interfaceName(name).virtualDevice("").vrfInstance(vrf).enabled(enabled).level3(level3)
			.description(description).ip4Addresses(ip4Addresses).ip6Addresses(ip6Addresses).build();
	}

	// Stores the configurations of a device (the last given one becoming its last configuration)
	private static List<Long> addConfigs(DSLContext dsl, Device device, Config... configs) {
		ConfigRepository configRepository = new ConfigRepository();
		List<Long> ids = new ArrayList<>();
		for (Config config : configs) {
			ids.add(configRepository.add(dsl, config.toBuilder().deviceId(device.id()).build()));
		}
		DeviceRepository deviceRepository = new DeviceRepository();
		Device stored = deviceRepository.find(dsl, device.id());
		deviceRepository.save(dsl, stored, stored.toBuilder().lastConfigId(ids.get(ids.size() - 1)).build());
		return ids;
	}

	private void seedDevices() {
		new Transactions().run(dsl -> {
			DomainRepository domainRepository = new DomainRepository();
			CredentialSetRepository credentialSetRepository = new CredentialSetRepository();
			DeviceRepository deviceRepository = new DeviceRepository();
			Domain paris = domainRepository.find(dsl, this.id("domain.paris"));
			Domain lyon = domainRepository.find(dsl, this.id("domain.lyon"));

			// Device 1: complete device
			DeviceCredentialSet specific = credentialSetRepository.add(dsl, DeviceSshAccount.builder()
				.username("specific-user").password("specific-pass").superPassword("specific-enable")
				.name(DeviceCredentialSet.generateSpecificName()).deviceSpecific(true).build());
			Device router1 = deviceRepository.add(dsl, device("router-paris-1", "CiscoIOS12", "10.1.1.1", paris, D2020)
				.family("Cisco 2900")
				.networkClass(NetworkClass.ROUTER)
				.location("Paris, rack A1")
				.contact("noc@example.net")
				.comments("Main Paris router")
				.softwareVersion("16.1.6")
				.softwareLevel(ConformanceLevel.GOLD)
				.serialNumber("FTX1234ABCD")
				.status(Device.Status.INPRODUCTION)
				.eosDate(Instant.ofEpochMilli(D2040))
				.eolDate(Instant.ofEpochMilli(D2040 + 365L * 24 * 3600 * 1000))
				.vrfInstances(Set.of("VRF1", "VRF2"))
				.virtualDevices(Set.of("vdc1"))
				.tags(Device.normalizeTags(List.of("core", "production")))
				.accesses(List.of(
					DeviceAccess.of("ssh").toBuilder().specificCredentialSet(specific).port(2222).address("192.0.2.10").build(),
					DeviceAccess.of("telnet").toBuilder().globalCredentialSet(credentialSetRepository.find(dsl, this.id("cred.telnet"))).build()))
				.modules(List.of(
					module("chassis", "CISCO2911/K9", "FTX1234ABCD"),
					module("slot 1", "HWIC-4ESW", "FOC5678WXYZ"),
					module("slot 2", "HWIC-1T", "FOC0001AAAA").toBuilder().removed(true).build()))
				.networkInterfaces(List.of(
					networkInterface("GigabitEthernet0/0", "VRF1", true, true, "Uplink", ip4("10.0.0.1", 24), ip6("2001:db8:1::1", 64)),
					networkInterface("GigabitEthernet0/1", "VRF2", false, true, "Downlink", ip4("10.0.1.1", 25)),
					networkInterface("Loopback0", "", true, false, "")))
				.attributes(deviceAttributes(true))
				.build());
			String head = "hostname router-paris-1\n!\ninterface Gi0/0\n ip address 10.0.0.1 255.255.255.0\n";
			List<Long> configIds = addConfigs(dsl, router1,
				config(D2020, "tester", head + "!\nend\n", false),
				config(D2021, "tester", head + "!\ninterface Gi0/1\n shutdown\n!\nend\n", true),
				config(D2022, "snapshot",
					head + " description \"Uplink\"\n!\ninterface Gi0/1\n shutdown\n!\nline vty 0 4\n password secret\n!\nend\n", true));
			this.remember("device.router1", router1.id());
			this.remember("config.router1.first", configIds.get(0));
			this.remember("config.router1.second", configIds.get(1));
			this.remember("config.router1.last", configIds.get(2));

			// Device 2: another domain, other family, disabled
			Device router2 = deviceRepository.add(dsl, device("router-lyon-1", "CiscoIOS12", "10.2.1.1", lyon, D2021)
				.family("Cisco 1900")
				.networkClass(NetworkClass.SWITCHROUTER)
				.location("Lyon")
				.softwareVersion("15.2.4")
				.softwareLevel(ConformanceLevel.SILVER)
				.serialNumber("FTX9999ZZZZ")
				.status(Device.Status.DISABLED)
				.tags(Device.normalizeTags(List.of("branch")))
				.modules(List.of(module("chassis", "CISCO1921/K9", "FTX9999ZZZZ")))
				.attributes(deviceAttributes(false))
				.build());
			addConfigs(dsl, router2, config(D2021, "tester", "hostname router-lyon-1\n!\nend\n", false));
			this.remember("device.router2", router2.id());

			// Device 3: other driver, with options, in preproduction, never snapshot (changed after its creation)
			Device other = deviceRepository.add(dsl, device("proxy-paris-1", "SkyhighSWG", "10.1.2.2", paris, D2022)
				.changeDate(null)
				.family("Web gateway")
				.networkClass(NetworkClass.SERVER)
				.status(Device.Status.PREPRODUCTION)
				.options(new LinkedHashMap<>(Map.of("fullBackup", false)))
				.tags(Device.normalizeTags(List.of("production")))
				.accesses(List.of(DeviceAccess.of("ssh").toBuilder()
					.globalCredentialSet(credentialSetRepository.find(dsl, this.id("cred.ssh"))).build()))
				.build());
			this.remember("device.proxy", other.id());

			// Device 4: bare IPv6 device
			Device bare = deviceRepository.add(dsl, device("bare-1", "CiscoIOS12", "2001:db8::4", paris, D2022)
				.networkClass(NetworkClass.UNKNOWN)
				.build());
			this.remember("device.bare", bare.id());
		});
		this.writeBinaryFiles(this.id("device.router1"));
	}

	/**
	 * Creates the content of the seeded binary file attributes.
	 * @param deviceId the ID of the device holding the attributes
	 */
	private void writeBinaryFiles(long deviceId) {
		Path folder = ConfigBinaryFileAttribute.settings().storageFolderPath();
		List<Config> configs = new Transactions().read(dsl -> new ConfigRepository().findByDevice(dsl, deviceId, Page.ALL));
		for (Config config : configs) {
			for (net.netshot.netshot.inventory.config.attribute.ConfigAttribute attribute : config.attributes()) {
				if (attribute instanceof ConfigBinaryFileAttribute file) {
					try {
						Files.writeString(folder.resolve(file.uid() + ".data"), "binary content of " + file.originalName());
					}
					catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
			}
		}
	}

	private void seedGroups() {
		this.create("group.static", "/groups", """
			{"type": "StaticDeviceGroup", "name": "Core routers", "folder": "Production/Core",
			 "staticDevices": [%d, %d]}""".formatted(this.id("device.router1"), this.id("device.router2")));
		this.create("group.dynamic", "/groups", """
			{"type": "DynamicDeviceGroup", "name": "All Cisco devices", "folder": "Production",
			 "query": "[Family] CONTAINS \\"Cisco\\""}""");
		this.create("group.hidden", "/groups", """
			{"type": "DynamicDeviceGroup", "name": "Everything", "folder": "", "hiddenFromReports": true,
			 "query": "[Name] CONTAINS \\"\\""}""");
		this.create("group.empty", "/groups", """
			{"type": "StaticDeviceGroup", "name": "Empty group", "staticDevices": []}""");
	}

	private void seedPolicies() {
		this.create("policy.security", "/policies", """
			{"name": "Security baseline", "targetGroups": [%d]}""".formatted(this.id("group.dynamic")));
		this.create("policy.routing", "/policies", """
			{"name": "Routing policy", "targetGroups": [%d, %d]}"""
			.formatted(this.id("group.static"), this.id("group.dynamic")));
		this.create("policy.empty", "/policies", """
			{"name": "Empty policy", "targetGroups": []}""");

		this.create("rule.text", "/rules", """
			{"type": "TextRule", "name": "No telnet", "policy": %d, "text": "transport input telnet",
			 "regExp": false, "invert": true, "matchAll": false, "anyBlock": false, "normalize": true,
			 "context": "line vty", "field": "runningConfig", "driver": "CiscoIOS12"}"""
			.formatted(this.id("policy.security")));
		this.create("rule.regex", "/rules", """
			{"type": "TextRule", "name": "Hostname set", "policy": %d, "text": "^hostname .+$",
			 "regExp": true, "invert": false, "matchAll": true, "anyBlock": true, "normalize": false,
			 "field": "runningConfig"}""".formatted(this.id("policy.security")));
		this.create("rule.js", "/rules", """
			{"type": "JavaScriptRule", "name": "JS check", "policy": %d,
			 "script": "function check(device) {\\n  return { result: CONFORMING, comment: 'ok' };\\n}"}"""
			.formatted(this.id("policy.routing")));
		this.create("rule.python", "/rules", """
			{"type": "PythonRule", "name": "Python check", "policy": %d,
			 "script": "def check(device):\\n  return {'result': result_option.CONFORMING, 'comment': 'ok'}"}"""
			.formatted(this.id("policy.routing")));

		this.create("rule.software.gold", "/softwarerules", """
			{"group": %d, "driver": "CiscoIOS12", "version": "16.1.6", "versionRegExp": false,
			 "family": "", "familyRegExp": false, "partNumber": "", "partNumberRegExp": false, "level": "GOLD"}"""
			.formatted(this.id("group.dynamic")));
		this.create("rule.software.silver", "/softwarerules", """
			{"group": -1, "driver": "CiscoIOS12", "version": "15\\\\..*", "versionRegExp": true,
			 "family": "Cisco 1900", "familyRegExp": false, "partNumber": "", "partNumberRegExp": false, "level": "SILVER"}""");
		this.create("rule.hardware.1", "/hardwarerules", """
			{"group": %d, "driver": "CiscoIOS12", "partNumber": "CISCO2911/K9", "partNumberRegExp": false,
			 "family": "", "familyRegExp": false, "endOfSale": %d, "endOfLife": %d}"""
			.formatted(this.id("group.dynamic"), D2040, D2040 + 365L * 24 * 3600 * 1000));
		this.create("rule.hardware.2", "/hardwarerules", """
			{"group": -1, "driver": "", "partNumber": "", "partNumberRegExp": false,
			 "family": "Cisco 1900.*", "familyRegExp": true, "endOfSale": %d}""".formatted(D2022));
	}

	private void seedDiagnostics() {
		this.create("diag.simple.text", "/diagnostics", """
			{"type": "SimpleDiagnostic", "name": "Reload reason", "enabled": true, "targetGroup": %d,
			 "resultType": "TEXT", "deviceDriver": "CiscoIOS12", "cliMode": "enable",
			 "command": "show version | include reason", "modifierPattern": "(?s).*reason: (.+?)\\\\n.*",
			 "modifierReplacement": "$1"}""".formatted(this.id("group.dynamic")));
		this.create("diag.js.longtext", "/diagnostics", """
			{"type": "JavaScriptDiagnostic", "name": "Full inventory", "enabled": true, "targetGroup": -1,
			 "resultType": "LONGTEXT", "script": "function diagnose(cli, device, diagnostic) {\\n  diagnostic.set('x');\\n}"}""");
		this.create("diag.python.numeric", "/diagnostics", """
			{"type": "PythonDiagnostic", "name": "CPU load", "enabled": false, "targetGroup": %d,
			 "resultType": "NUMERIC", "script": "def diagnose(cli, device, diagnostic):\\n  diagnostic.set(1)"}"""
			.formatted(this.id("group.static")));
		this.create("diag.simple.binary", "/diagnostics", """
			{"type": "SimpleDiagnostic", "name": "Config saved", "enabled": true, "targetGroup": -1,
			 "resultType": "BINARY", "deviceDriver": "CiscoIOS12", "cliMode": "enable",
			 "command": "show startup-config", "modifierPattern": "", "modifierReplacement": ""}""");
	}

	private void seedHooks() {
		this.create("hook.web1", "/hooks", """
			{"type": "Web", "name": "Notify on tasks", "enabled": true, "action": "POST_JSON",
			 "url": "https://example.net/callback1",
			 "triggers": [{"type": "POST_TASK", "item": "TakeSnapshotTask"}, {"type": "POST_TASK", "item": "CheckComplianceTask"}]}""");
		this.create("hook.web2", "/hooks", """
			{"type": "Web", "name": "Disabled XML hook", "enabled": false, "action": "POST_XML",
			 "url": "http://example.net/callback2", "httpsCaTrustMode": "TRUST_ANY", "triggers": []}""");
	}

	private void seedScripts() {
		this.create("script.1", "/scripts", """
			{"name": "Show version", "deviceDriver": "CiscoIOS12", "folder": "Maintenance",
			 "script": "function run(cli, device) {\\n  cli.macro(\\"enable\\");\\n  cli.command(\\"show version\\");\\n}"}""");
		this.create("script.2", "/scripts", """
			{"name": "Other script", "deviceDriver": "CiscoIOS12",
			 "script": "function run(cli, device) {\\n  cli.command(\\"show clock\\");\\n}"}""");
	}

	private void seedUsers() {
		this.create("user.readonly", "/users", """
			{"username": "reader", "password": "%s", "level": %d, "local": true}"""
			.formatted(USER_PASSWORD, UiUser.LEVEL_READONLY));
		this.create("user.rw", "/users", """
			{"username": "writer", "password": "%s", "level": %d, "local": true}"""
			.formatted(USER_PASSWORD, UiUser.LEVEL_READWRITE));
		this.create("user.admin", "/users", """
			{"username": "boss", "password": "%s", "level": %d, "local": true}"""
			.formatted(USER_PASSWORD, UiUser.LEVEL_ADMIN));
		this.create("user.remote", "/users", """
			{"username": "radius-user", "level": %d, "local": false}""".formatted(UiUser.LEVEL_OPERATOR));
	}

	// -------------------------------------------------------------------
	// State which cannot be created through the API
	// -------------------------------------------------------------------

	private void seedComplianceAndDiagnosticResults() {
		// Exemption, through the API
		this.call("PUT", "/rules/" + this.id("rule.text"), """
			{"id": %d, "type": "TextRule", "name": "No telnet", "policy": %d, "text": "transport input telnet",
			 "regExp": false, "invert": true, "matchAll": false, "anyBlock": false, "normalize": true,
			 "context": "line vty", "field": "runningConfig", "driver": "CiscoIOS12", "enabled": true,
			 "exemptions": {"%d": %d}}"""
			.formatted(this.id("rule.text"), this.id("policy.security"), this.id("device.router2"), D2040));
		new Transactions().run(dsl -> {
			DeviceRepository deviceRepository = new DeviceRepository();
			Device router1 = deviceRepository.find(dsl, this.id("device.router1"));
			Device router2 = deviceRepository.find(dsl, this.id("device.router2"));
			Device proxy = deviceRepository.find(dsl, this.id("device.proxy"));
			long text = this.id("rule.text");
			long regex = this.id("rule.regex");
			long js = this.id("rule.js");
			long python = this.id("rule.python");
			Object[][] results = {
				{text, router1.id(), ResultOption.NONCONFORMING, "Found text 'transport input telnet'"},
				{regex, router1.id(), ResultOption.CONFORMING, ""},
				{js, router1.id(), ResultOption.INVALIDRULE, "Script error"},
				{python, router1.id(), ResultOption.NOTAPPLICABLE, ""},
				{text, router2.id(), ResultOption.EXEMPTED, ""},
				{regex, router2.id(), ResultOption.CONFORMING, "Matched"},
				{js, router2.id(), ResultOption.DISABLED, ""},
				{regex, proxy.id(), ResultOption.NOTAPPLICABLE, ""},
			};
			long checkDate = D2021;
			List<CheckResult> checkResults = new ArrayList<>();
			for (Object[] result : results) {
				checkResults.add(new CheckResult((Long) result[0], (Long) result[1], Instant.ofEpochMilli(checkDate),
					(String) result[3], (ResultOption) result[2]));
				checkDate += 1000;
			}
			new RuleRepository().saveCheckResults(dsl, checkResults);

			Diagnostic text1 = new DiagnosticRepository().find(dsl, this.id("diag.simple.text"));
			Diagnostic longText = new DiagnosticRepository().find(dsl, this.id("diag.js.longtext"));
			Diagnostic numeric = new DiagnosticRepository().find(dsl, this.id("diag.python.numeric"));
			Diagnostic binary = new DiagnosticRepository().find(dsl, this.id("diag.simple.binary"));
			List<net.netshot.netshot.diagnostic.DiagnosticResult> results1 = List.of(
				DiagnosticTextResult.of(text1, "Reload Command"),
				DiagnosticLongTextResult.of(longText, "Long diagnostic\nresult\n" + "y".repeat(200)),
				DiagnosticNumericResult.of(numeric, 42.5),
				DiagnosticBinaryResult.of(binary, true));
			List<net.netshot.netshot.diagnostic.DiagnosticResult> results2 = List.of(
				DiagnosticTextResult.of(text1, "Power failure"),
				DiagnosticBinaryResult.of(binary, false));
			long date = D2021;
			DiagnosticResultRepository resultRepository = new DiagnosticResultRepository();
			for (Device device : List.of(router1, router2)) {
				List<net.netshot.netshot.diagnostic.DiagnosticResult> dated = new ArrayList<>();
				for (net.netshot.netshot.diagnostic.DiagnosticResult result : device == router1 ? results1 : results2) {
					dated.add(result.withDates(Instant.ofEpochMilli(date), Instant.ofEpochMilli(date + 500)));
					date += 1000;
				}
				resultRepository.save(dsl, device.id(), dated);
			}
		});
	}

	private Task taskData(DSLContext dsl, Task task, Task.Status status, long created, String log) {
		Task.TaskBuilder builder = task.toBuilder()
			.status(status)
			.creationDate(Instant.ofEpochMilli(created))
			.changeDate(Instant.ofEpochMilli(created + 2000))
			.scheduleReference(Instant.ofEpochMilli(created));
		if (status == Task.Status.SUCCESS || status == Task.Status.FAILURE || status == Task.Status.RUNNING) {
			builder.executionDate(Instant.ofEpochMilli(created + 1000));
		}
		if (log != null) {
			builder.log(log);
		}
		return new TaskRepository().add(dsl, builder.build());
	}

	private void seedTasks() {
		new Transactions().run(dsl -> {
			DeviceRepository deviceRepository = new DeviceRepository();
			Device router1 = deviceRepository.find(dsl, this.id("device.router1"));
			Device router2 = deviceRepository.find(dsl, this.id("device.router2"));
			long paris = this.id("domain.paris");
			net.netshot.netshot.inventory.group.DeviceGroup group = new GroupRepository().find(dsl, this.id("group.static"));

			Task snapshot = this.taskData(dsl, TakeSnapshotTask.of(router1, "Daily snapshot", "tester", true, false, false)
				.toBuilder().debugEnabled(true).build(), Task.Status.SUCCESS, D2021, "Snapshot done\nConfig 1 saved");
			long debugLogId = dsl.insertInto(DEBUG_LOG).set(DEBUG_LOG.TEXT, "[10:00:00] Connecting\n[10:00:01] Done\n")
				.returning(DEBUG_LOG.ID).fetchOne(DEBUG_LOG.ID);
			dsl.update(TASK).set(TASK.DEBUG_LOG, debugLogId).where(TASK.ID.eq(snapshot.id())).execute();
			this.remember("task.snapshot", snapshot.id());

			Task failedSnapshot = this.taskData(dsl, TakeSnapshotTask.of(router2, "Failing snapshot", "tester", false, true, true),
				Task.Status.FAILURE, D2021 + 10000, "Unable to connect");
			this.remember("task.failed", failedSnapshot.id());

			Task compliance = this.taskData(dsl, CheckComplianceTask.of(router1, "Check compliance", "tester"),
				Task.Status.SUCCESS, D2021 + 20000, "Done");
			this.remember("task.compliance", compliance.id());

			Task diagnostics = this.taskData(dsl, RunDiagnosticsTask.of(router1, "Run diagnostics", "tester", false),
				Task.Status.CANCELLED, D2021 + 30000, "Cancelled by user");
			this.remember("task.diagnostics", diagnostics.id());

			Task script = this.taskData(dsl, RunDeviceScriptTask.of(router1, new RunDeviceScriptTask(null, "function run(cli, device) {}",
				TestDrivers.registry().getDriverByName("CiscoIOS12").getName(), null, false, false, false), "Run script", "tester"),
				Task.Status.SUCCESS, D2021 + 40000, "Script output");
			this.remember("task.script", script.id());

			Task discover = this.taskData(dsl, DiscoverDeviceTypeTask.of("10.9.9.9", paris, Set.of(), "Discover device", "tester"),
				Task.Status.WAITING, D2021 + 50000, null);
			this.remember("task.discover", discover.id());

			Task scan = this.taskData(dsl, ScanSubnetsTask.of(Set.of(ip4("10.9.0.0", 24)), paris, "Scan subnet", "10.9.0.0/24", "tester"),
				Task.Status.SUCCESS, D2021 + 60000, "No device found");
			this.remember("task.scan", scan.id());

			Task purge = this.taskData(dsl, Task.of(PurgeDatabaseTask.builder().days(90).configDays(365).moduleDays(30).build(),
				"Purge", "Global", "tester").toBuilder().scheduleType(Task.ScheduleType.DAILY).build(), Task.Status.SCHEDULED, D2022, null);
			// Changed afterwards, as the former seed did (the change date is then now)
			dsl.update(TASK).set(TASK.SCHEDULE_REFERENCE, Instant.ofEpochMilli(D2040)).set(TASK.CHANGE_DATE, Instant.now())
				.where(TASK.ID.eq(purge.id())).execute();
			this.remember("task.purge", purge.id());

			Task groupSnapshot = this.taskData(dsl, GroupBasedTask.of(new TakeGroupSnapshotTask(group.id(), null, -1, false, false),
				group.name(), "Group snapshot", "tester"), Task.Status.RUNNING, D2022 + 1000, "Running");
			this.remember("task.group", groupSnapshot.id());

			Task groupCompliance = this.taskData(dsl, GroupBasedTask.of(new CheckGroupComplianceTask(group.id(), null),
				group.name(), "Group compliance", "tester"), Task.Status.NEW, D2022 + 2000, null);
			this.remember("task.groupcompliance", groupCompliance.id());
		});
	}
}
