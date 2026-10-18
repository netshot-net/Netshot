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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;
import org.junit.jupiter.api.Assertions;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigNumericAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.Network6Address;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.DebugLog;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Deterministic dataset used by the API contract tests.
 *
 * <p>Rules for determinism: a fresh schema is used, objects are created one
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

	/** POSTs an object and remembers its ID. */
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
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			// Remove default admin account
			session.createMutationQuery("delete from net.netshot.netshot.aaa.user.UiUser").executeUpdate();
			Object[][] tokens = {
				{UiUser.LEVEL_READONLY, READONLY_TOKEN},
				{UiUser.LEVEL_OPERATOR, OPERATOR_TOKEN},
				{UiUser.LEVEL_READWRITE, READWRITE_TOKEN},
				{UiUser.LEVEL_EXECUTEREADWRITE, EXECUTE_TOKEN},
				{UiUser.LEVEL_ADMIN, ADMIN_TOKEN},
			};
			for (Object[] token : tokens) {
				session.persist(new ApiToken("Test Token - level %d".formatted((Integer) token[0]),
					(String) token[1], (Integer) token[0]));
			}
			session.getTransaction().commit();
		}
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

	private Config addConfig(Session session, Device device, long date, String author, String runningConfig,
		boolean full) {
		Config config = new Config(device);
		config.setAuthor(author);
		config.setChangeDate(new Date(date));
		device.getConfigs().add(config);
		session.persist(config);
		ConfigLongTextAttribute running = new ConfigLongTextAttribute(config, "runningConfig", runningConfig);
		config.addAttribute(running);
		session.persist(running);
		ConfigTextAttribute version = new ConfigTextAttribute(config, "iosVersion", "16.1.6");
		config.addAttribute(version);
		session.persist(version);
		if (full) {
			ConfigNumericAttribute size = new ConfigNumericAttribute(config, "configSize", 1234.5);
			config.addAttribute(size);
			session.persist(size);
			ConfigBinaryAttribute saved = new ConfigBinaryAttribute(config, "configSaved", true);
			config.addAttribute(saved);
			session.persist(saved);
			ConfigBinaryFileAttribute file = new ConfigBinaryFileAttribute(config, "backupFile", "backup-%d.tar.gz".formatted(date));
			file.setUid("00000000-0000-0000-0000-%012d".formatted(date % 1000000000000L));
			file.setFileSize(4096);
			file.setChecksum("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
			config.addAttribute(file);
			session.persist(file);
		}
		return config;
	}

	private void addDeviceAttributes(Session session, Device device, boolean full) {
		DeviceNumericAttribute memory = new DeviceNumericAttribute(device, "mainMemorySize", 2048);
		device.addAttribute(memory);
		session.persist(memory);
		DeviceTextAttribute register = new DeviceTextAttribute(device, "configRegister", "0x1202");
		device.addAttribute(register);
		session.persist(register);
		DeviceBinaryAttribute saved = new DeviceBinaryAttribute(device, "configurationSaved", true);
		device.addAttribute(saved);
		session.persist(saved);
		if (full) {
			DeviceLongTextAttribute notes = new DeviceLongTextAttribute(device, "inventoryNotes",
				"Line 1 of a long text attribute\nLine 2 with special chars: <>&\"' éè\n" + "x".repeat(300));
			device.addAttribute(notes);
			session.persist(notes);
		}
	}

	private Device createDevice(Session session, String name, String driver, String address, Domain domain,
		long createdAt) {
		Device device = new Device(driver, address, domain, "seed");
		device.setName(name);
		device.setCreatedDate(new Date(createdAt));
		device.setChangeDate(new Date(createdAt));
		session.persist(device);
		return device;
	}

	private void seedDevices() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			Domain paris = session.get(Domain.class, this.id("domain.paris"));
			Domain lyon = session.get(Domain.class, this.id("domain.lyon"));

			// Device 1: complete device
			Device router1 = this.createDevice(session, "router-paris-1", "CiscoIOS12", "10.1.1.1", paris, D2020);
			router1.setFamily("Cisco 2900");
			router1.setNetworkClass(NetworkClass.ROUTER);
			router1.setLocation("Paris, rack A1");
			router1.setContact("noc@example.net");
			router1.setComments("Main Paris router");
			router1.setSoftwareVersion("16.1.6");
			router1.setSoftwareLevel(ConformanceLevel.GOLD);
			router1.setSerialNumber("FTX1234ABCD");
			router1.setStatus(Device.Status.INPRODUCTION);
			router1.setEosDate(new Date(D2040));
			router1.setEolDate(new Date(D2040 + 365L * 24 * 3600 * 1000));
			router1.setVrfInstances(new HashSet<>(Set.of("VRF1", "VRF2")));
			router1.setVirtualDevices(new HashSet<>(Set.of("vdc1")));
			router1.replaceTags(List.of("core", "production"));
			{
				DeviceSshAccount specific = new DeviceSshAccount("specific-user", "specific-pass", "specific-enable",
					DeviceCredentialSet.generateSpecificName());
				specific.setDeviceSpecific(true);
				session.persist(specific);
				DeviceAccess ssh = new DeviceAccess(router1, "ssh");
				ssh.setSpecificCredentialSet(specific);
				ssh.setPort(2222);
				ssh.setAddress("192.0.2.10");
				router1.getAccesses().add(ssh);
				session.persist(ssh);
				DeviceAccess telnet = new DeviceAccess(router1, "telnet");
				telnet.setGlobalCredentialSet(session.get(DeviceCredentialSet.class, this.id("cred.telnet")));
				router1.getAccesses().add(telnet);
				session.persist(telnet);
			}
			Module chassis = new Module("chassis", "CISCO2911/K9", "FTX1234ABCD", router1);
			router1.getModules().add(chassis);
			session.persist(chassis);
			Module slot = new Module("slot 1", "HWIC-4ESW", "FOC5678WXYZ", router1);
			router1.getModules().add(slot);
			session.persist(slot);
			Module removed = new Module("slot 2", "HWIC-1T", "FOC0001AAAA", router1);
			removed.setRemoved(true);
			router1.getModules().add(removed);
			session.persist(removed);
			{
				NetworkInterface gi0 = new NetworkInterface(router1, "GigabitEthernet0/0", "", "VRF1", true, true, "Uplink");
				gi0.addIpAddress(ip4("10.0.0.1", 24));
				gi0.addIpAddress(ip6("2001:db8:1::1", 64));
				router1.getNetworkInterfaces().add(gi0);
				session.persist(gi0);
				NetworkInterface gi1 = new NetworkInterface(router1, "GigabitEthernet0/1", "", "VRF2", false, true, "Downlink");
				gi1.addIpAddress(ip4("10.0.1.1", 25));
				router1.getNetworkInterfaces().add(gi1);
				session.persist(gi1);
				NetworkInterface lo0 = new NetworkInterface(router1, "Loopback0", "", "", true, false, "");
				router1.getNetworkInterfaces().add(lo0);
				session.persist(lo0);
			}
			this.addDeviceAttributes(session, router1, true);
			String head = "hostname router-paris-1\n!\ninterface Gi0/0\n ip address 10.0.0.1 255.255.255.0\n";
			this.addConfig(session, router1, D2020, "tester", head + "!\nend\n", false);
			this.addConfig(session, router1, D2021, "tester", head + "!\ninterface Gi0/1\n shutdown\n!\nend\n", true);
			Config last = this.addConfig(session, router1, D2022, "snapshot",
				head + " description \"Uplink\"\n!\ninterface Gi0/1\n shutdown\n!\nline vty 0 4\n password secret\n!\nend\n", true);
			router1.setLastConfig(last);
			this.remember("device.router1", router1.getId());
			this.writeBinaryFiles(router1);
			this.remember("config.router1.first", router1.getConfigs().get(0).getId());
			this.remember("config.router1.second", router1.getConfigs().get(1).getId());
			this.remember("config.router1.last", last.getId());

			// Device 2: another domain, other family, disabled
			Device router2 = this.createDevice(session, "router-lyon-1", "CiscoIOS12", "10.2.1.1", lyon, D2021);
			router2.setFamily("Cisco 1900");
			router2.setNetworkClass(NetworkClass.SWITCHROUTER);
			router2.setLocation("Lyon");
			router2.setSoftwareVersion("15.2.4");
			router2.setSoftwareLevel(ConformanceLevel.SILVER);
			router2.setSerialNumber("FTX9999ZZZZ");
			router2.setStatus(Device.Status.DISABLED);
			router2.replaceTags(List.of("branch"));
			Module chassis2 = new Module("chassis", "CISCO1921/K9", "FTX9999ZZZZ", router2);
			router2.getModules().add(chassis2);
			session.persist(chassis2);
			this.addDeviceAttributes(session, router2, false);
			Config last2 = this.addConfig(session, router2, D2021, "tester", "hostname router-lyon-1\n!\nend\n", false);
			router2.setLastConfig(last2);
			this.remember("device.router2", router2.getId());

			// Device 3: other driver, with options, in preproduction, never snapshot
			Device other = this.createDevice(session, "proxy-paris-1", "SkyhighSWG", "10.1.2.2", paris, D2022);
			other.setFamily("Web gateway");
			other.setNetworkClass(NetworkClass.SERVER);
			other.setStatus(Device.Status.PREPRODUCTION);
			other.setOptions(new LinkedHashMap<>(Map.of("fullBackup", false)));
			other.replaceTags(List.of("production"));
			DeviceAccess otherSsh = new DeviceAccess(other, "ssh");
			otherSsh.setGlobalCredentialSet(session.get(DeviceCredentialSet.class, this.id("cred.ssh")));
			other.getAccesses().add(otherSsh);
			session.persist(otherSsh);
			this.remember("device.proxy", other.getId());

			// Device 4: bare IPv6 device
			Device bare = this.createDevice(session, "bare-1", "CiscoIOS12", "2001:db8::4", paris, D2022);
			bare.setNetworkClass(NetworkClass.UNKNOWN);
			this.remember("device.bare", bare.getId());

			session.getTransaction().commit();
		}
	}

	/** Creates the content of the seeded binary file attributes. */
	private void writeBinaryFiles(Device device) {
		Path folder = ConfigBinaryFileAttribute.SETTINGS.getStorageFolderPath();
		for (Config config : device.getConfigs()) {
			for (net.netshot.netshot.inventory.config.attribute.ConfigAttribute attribute : config.getAttributes()) {
				if (attribute instanceof ConfigBinaryFileAttribute file) {
					try {
						Files.writeString(folder.resolve(file.getUid() + ".data"), "binary content of " + file.getOriginalName());
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
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			Device router1 = session.get(Device.class, this.id("device.router1"));
			Device router2 = session.get(Device.class, this.id("device.router2"));
			Device proxy = session.get(Device.class, this.id("device.proxy"));
			Rule text = session.get(Rule.class, this.id("rule.text"));
			Rule regex = session.get(Rule.class, this.id("rule.regex"));
			Rule js = session.get(Rule.class, this.id("rule.js"));
			Rule python = session.get(Rule.class, this.id("rule.python"));
			Object[][] results = {
				{text, router1, ResultOption.NONCONFORMING, "Found text 'transport input telnet'"},
				{regex, router1, ResultOption.CONFORMING, ""},
				{js, router1, ResultOption.INVALIDRULE, "Script error"},
				{python, router1, ResultOption.NOTAPPLICABLE, ""},
				{text, router2, ResultOption.EXEMPTED, ""},
				{regex, router2, ResultOption.CONFORMING, "Matched"},
				{js, router2, ResultOption.DISABLED, ""},
				{regex, proxy, ResultOption.NOTAPPLICABLE, ""},
			};
			long checkDate = D2021;
			for (Object[] result : results) {
				CheckResult checkResult = new CheckResult((Rule) result[0], (Device) result[1], (ResultOption) result[2],
					(String) result[3]);
				checkResult.setCheckDate(new Date(checkDate));
				checkDate += 1000;
				session.persist(checkResult);
			}

			Diagnostic text1 = session.get(Diagnostic.class, this.id("diag.simple.text"));
			Diagnostic longText = session.get(Diagnostic.class, this.id("diag.js.longtext"));
			Diagnostic numeric = session.get(Diagnostic.class, this.id("diag.python.numeric"));
			Diagnostic binary = session.get(Diagnostic.class, this.id("diag.simple.binary"));
			List<net.netshot.netshot.diagnostic.DiagnosticResult> diagResults = List.of(
				new DiagnosticTextResult(router1, text1, "Reload Command"),
				new DiagnosticLongTextResult(router1, longText, "Long diagnostic\nresult\n" + "y".repeat(200)),
				new DiagnosticNumericResult(router1, numeric, 42.5),
				new DiagnosticBinaryResult(router1, binary, true),
				new DiagnosticTextResult(router2, text1, "Power failure"),
				new DiagnosticBinaryResult(router2, binary, false));
			long date = D2021;
			for (net.netshot.netshot.diagnostic.DiagnosticResult diagResult : diagResults) {
				diagResult.setCreationDate(new Date(date));
				diagResult.setLastCheckDate(new Date(date + 500));
				date += 1000;
				session.persist(diagResult);
			}
			session.getTransaction().commit();
		}
	}

	private <T extends Task> T taskData(Session session, T task, Task.Status status, long created, String log) {
		task.setStatus(status);
		task.setCreationDate(new Date(created));
		task.setChangeDate(new Date(created + 2000));
		task.setScheduleReference(new Date(created));
		if (status == Task.Status.SUCCESS || status == Task.Status.FAILURE || status == Task.Status.RUNNING) {
			task.setExecutionDate(new Date(created + 1000));
		}
		if (log != null) {
			task.setLogs(log);
		}
		session.persist(task);
		return task;
	}

	private void seedTasks() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			Device router1 = session.get(Device.class, this.id("device.router1"));
			Device router2 = session.get(Device.class, this.id("device.router2"));
			Domain paris = session.get(Domain.class, this.id("domain.paris"));
			net.netshot.netshot.inventory.group.DeviceGroup group = session.get(net.netshot.netshot.inventory.group.DeviceGroup.class,
				this.id("group.static"));

			TakeSnapshotTask snapshot = new TakeSnapshotTask(router1, "Daily snapshot", "tester", true, false, false);
			snapshot.setDebugLog(new DebugLog("[10:00:00] Connecting\n[10:00:01] Done\n"));
			snapshot.setDebugEnabled(true);
			this.taskData(session, snapshot, Task.Status.SUCCESS, D2021, "Snapshot done\nConfig 1 saved");
			this.remember("task.snapshot", snapshot.getId());

			TakeSnapshotTask failedSnapshot = new TakeSnapshotTask(router2, "Failing snapshot", "tester", false, true, true);
			this.taskData(session, failedSnapshot, Task.Status.FAILURE, D2021 + 10000, "Unable to connect");
			this.remember("task.failed", failedSnapshot.getId());

			CheckComplianceTask compliance = new CheckComplianceTask(router1, "Check compliance", "tester");
			this.taskData(session, compliance, Task.Status.SUCCESS, D2021 + 20000, "Done");
			this.remember("task.compliance", compliance.getId());

			RunDiagnosticsTask diagnostics = new RunDiagnosticsTask(router1, "Run diagnostics", "tester", false);
			this.taskData(session, diagnostics, Task.Status.CANCELLED, D2021 + 30000, "Cancelled by user");
			this.remember("task.diagnostics", diagnostics.getId());

			RunDeviceScriptTask script = new RunDeviceScriptTask(router1, "function run(cli, device) {}",
				TestDrivers.registry().getDriverByName("CiscoIOS12"), "Run script", "tester");
			this.taskData(session, script, Task.Status.SUCCESS, D2021 + 40000, "Script output");
			this.remember("task.script", script.getId());

			DiscoverDeviceTypeTask discover = new DiscoverDeviceTypeTask("10.9.9.9", paris, "Discover device", "tester");
			this.taskData(session, discover, Task.Status.WAITING, D2021 + 50000, null);
			this.remember("task.discover", discover.getId());

			ScanSubnetsTask scan = new ScanSubnetsTask(Set.of(ip4("10.9.0.0", 24)), paris, "Scan subnet", "10.9.0.0/24", "tester");
			this.taskData(session, scan, Task.Status.SUCCESS, D2021 + 60000, "No device found");
			this.remember("task.scan", scan.getId());

			PurgeDatabaseTask purge = new PurgeDatabaseTask("Purge", "tester", 90, 365, 0, 0, 30, (net.netshot.netshot.inventory.group.DeviceGroup) null);
			purge.setScheduleType(Task.ScheduleType.DAILY);
			this.taskData(session, purge, Task.Status.SCHEDULED, D2022, null);
			purge.setScheduleReference(new Date(D2040));
			this.remember("task.purge", purge.getId());

			TakeGroupSnapshotTask groupSnapshot = new TakeGroupSnapshotTask(group, "Group snapshot", "tester",
				-1, false, false);
			this.taskData(session, groupSnapshot, Task.Status.RUNNING, D2022 + 1000, "Running");
			this.remember("task.group", groupSnapshot.getId());

			CheckGroupComplianceTask groupCompliance = new CheckGroupComplianceTask(group, "Group compliance", "tester");
			this.taskData(session, groupCompliance, Task.Status.NEW, D2022 + 2000, null);
			this.remember("task.groupcompliance", groupCompliance.getId());

			session.getTransaction().commit();
		}
	}
}
