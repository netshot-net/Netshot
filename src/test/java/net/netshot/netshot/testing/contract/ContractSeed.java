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
package net.netshot.netshot.testing.contract;

import static net.netshot.netshot.platform.database.schema.Tables.DEBUG_LOG;
import static net.netshot.netshot.platform.database.schema.Tables.TASK;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;


import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.ApiTokenRepository;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.UserCreation;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.compliance.HardwareRuleDefinition;
import net.netshot.netshot.compliance.HardwareRuleService;
import net.netshot.netshot.compliance.PolicyDefinition;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleDefinition;
import net.netshot.netshot.compliance.RuleRepository;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.compliance.SoftwareRuleDefinition;
import net.netshot.netshot.compliance.SoftwareRuleService;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.CredentialSetService;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticDefinition;
import net.netshot.netshot.diagnostic.DiagnosticRepository;
import net.netshot.netshot.diagnostic.DiagnosticService;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.domain.DomainDefinition;
import net.netshot.netshot.domain.DomainRepository;
import net.netshot.netshot.domain.DomainService;
import net.netshot.netshot.hook.HookService;
import net.netshot.netshot.hook.HookTrigger.TriggerType;
import net.netshot.netshot.hook.HookTrigger;
import net.netshot.netshot.hook.WebHook.Action;
import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.inventory.compliance.CheckResult.ResultOption;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigNumericAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResultRepository;
import net.netshot.netshot.inventory.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.inventory.group.GroupDefinition;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.platform.net.NetworkAddress;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
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
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.userscript.ScriptCreation;
import net.netshot.netshot.userscript.ScriptService;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;
import net.netshot.netshot.vault.VaultInstanceService;

/**
 * Deterministic dataset used by the contract tests (API responses, webhook payloads), created through the
 * services and the repositories, so that it depends on no version of the API.
 *
 * <p>Rules for determinism: a fresh database is used, objects are created one
 * after the other in a fixed order (IDs are database identity values, so they
 * come out the same on every run), every object whose date is exposed by the API
 * gets a fixed date (in 2020-2022 or 2040, far away from the "now" window the
 * normalizer masks), and entities with children are persisted child by child
 * rather than through a cascade over an unordered set.</p>
 */
public final class ContractSeed {

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

	/** The author of the seeded scripts (the admin API token, as when the dataset was created through the API). */
	private static final String SCRIPT_AUTHOR = "API Token [5: Test Token - level 1000]";

	private final Map<String, Long> ids = new HashMap<>();

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
	 * Creates an object and remembers its ID.
	 * @param name the name to remember the ID under
	 * @param id the ID of the created object
	 */
	private void created(String name, long id) {
		this.remember(name, id);
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
		TestWiring.get().transactions().run(dsl -> {
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
		DomainService domains = TestWiring.get().domainService();
		this.created("domain.paris", domains.create(new DomainDefinition("Paris DC", "Paris data center", "10.255.0.1")).id());
		this.created("domain.lyon", domains.create(new DomainDefinition("Lyon DC", "Lyon data center", "10.255.1.1")).id());
	}

	private void seedVault() {
		VaultInstanceService vaults = TestWiring.get().vaultInstanceService();
		this.created("vault.main", vaults.create(HashicorpVaultKv2Instance.builder("Main vault")
			.baseUrl("https://vault.example.net:8200").namespace("netshot").kvMountPath("kv")
			.authMethod(AuthMethod.APPROLE).appRoleMountPath("approle").appRoleId("role-1234")
			.appRoleSecretId(Secret.local("secret-5678")).jwtClientSecret(Secret.local(null))
			.build()).id());
		this.created("vault.jwt", vaults.create(HashicorpVaultKv2Instance.builder("JWT vault")
			.baseUrl("https://vault2.example.net:8200").httpsCaTrustMode(HttpsCaTrustMode.TRUST_ANY)
			.authMethod(AuthMethod.JWT).jwtMountPath("jwt").jwtIdpTokenEndpoint("https://idp.example.net/token")
			.jwtClientId("netshot").jwtClientSecret(Secret.local("client-secret")).jwtVaultRole("netshot-role").jwtScope("vault")
			.appRoleSecretId(Secret.local(null))
			.build()).id());
	}

	private void seedCredentialSets() {
		CredentialSetService credentialSets = TestWiring.get().credentialSetService();
		Domain paris = Domain.builder().id(this.id("domain.paris")).build();
		Domain lyon = Domain.builder().id(this.id("domain.lyon")).build();
		this.created("cred.snmp1", credentialSets.create(new DeviceSnmpv1Community(0, 0, null, "SNMP v1 community", null, false,
			local("public-v1"))).id());
		this.created("cred.snmp2", credentialSets.create(new DeviceSnmpv2cCommunity(0, 0, null, "SNMP v2 community", paris, false,
			local("public-v2"))).id());
		this.created("cred.snmp3", credentialSets.create(new DeviceSnmpv3Community(0, 0, null, "SNMP v3 account", null, false,
			local(null), local("snmpuser"), AuthProtocol.SHA, local("authkey123"), PrivProtocol.AES128, local("privkey456"))).id());
		this.created("cred.ssh", credentialSets.create(new DeviceSshAccount(0, 0, null, "SSH account", null, false,
			local("admin"), local("ssh-pass"), local("ssh-enable"))).id());
		this.created("cred.sshkey", credentialSets.create(new DeviceSshKeyAccount(0, 0, null, "SSH key account", lyon, false,
			local("keyuser"), local("key-pass"), local("key-enable"), local("-----BEGIN KEY-----"))).id());
		this.created("cred.telnet", credentialSets.create(new DeviceTelnetAccount(0, 0, null, "Telnet account", null, false,
			local("telnetuser"), local("telnet-pass"), local("telnet-enable"))).id());
		this.created("cred.http", credentialSets.create(new DeviceHttpAccount(0, 0, null, "HTTP account", null, false,
			local("apiuser"), local("http-pass"))).id());
		long vault = this.id("vault.main");
		this.created("cred.sshvault", credentialSets.create(new DeviceSshAccount(0, 0, null, "SSH account from vault", null, false,
			Secret.of(Secret.local(null), vault, "netshot/ssh/username"), Secret.of(Secret.local(null), vault, "netshot/ssh/password"),
			local(null))).id());
	}

	/**
	 * Builds a secret with a local value.
	 * @param value the value (or null)
	 * @return the secret
	 */
	private static Secret local(String value) {
		return Secret.of(Secret.local(value), null, null);
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
		TestWiring.get().transactions().run(dsl -> {
			DomainRepository domainRepository = new DomainRepository();
			CredentialSetRepository credentialSetRepository = new CredentialSetRepository();
			DeviceRepository deviceRepository = new DeviceRepository();
			Domain paris = domainRepository.find(dsl, this.id("domain.paris"));
			Domain lyon = domainRepository.find(dsl, this.id("domain.lyon"));

			// Device 1: complete device
			DeviceCredentialSet specific = credentialSetRepository.add(dsl, DeviceSshAccount.builder()
				.username(Secret.local("specific-user")).password(Secret.local("specific-pass")).superPassword(Secret.local("specific-enable"))
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
		List<Config> configs = TestWiring.get().transactions().read(dsl -> new ConfigRepository().findByDevice(dsl, deviceId, Page.ALL));
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
		GroupService groups = TestWiring.get().groupService();
		this.created("group.static", groups.create(new GroupDefinition("StaticDeviceGroup", "Core routers",
			List.of(this.id("device.router1"), this.id("device.router2")), null, "Production/Core", false)).id());
		this.created("group.dynamic", groups.create(new GroupDefinition("DynamicDeviceGroup", "All Cisco devices",
			null, "[Family] CONTAINS \"Cisco\"", "Production", false)).id());
		this.created("group.hidden", groups.create(new GroupDefinition("DynamicDeviceGroup", "Everything",
			null, "[Name] CONTAINS \"\"", "", true)).id());
		this.created("group.empty", groups.create(new GroupDefinition("StaticDeviceGroup", "Empty group",
			List.of(), null, "", false)).id());
	}

	private void seedPolicies() {
		PolicyService policies = TestWiring.get().policyService();
		this.created("policy.security", policies.create(new PolicyDefinition("Security baseline",
			Set.of(this.id("group.dynamic")))).policy().id());
		this.created("policy.routing", policies.create(new PolicyDefinition("Routing policy",
			new LinkedHashSet<>(List.of(this.id("group.static"), this.id("group.dynamic"))))).policy().id());
		this.created("policy.empty", policies.create(new PolicyDefinition("Empty policy", Set.of())).policy().id());

		RuleService rules = TestWiring.get().ruleService();
		this.created("rule.text", rules.create(textRule(this.id("policy.security"), false, null)).id());
		this.created("rule.regex", rules.create(new RuleDefinition("TextRule", "Hostname set", this.id("policy.security"), false,
			null, null, "^hostname .+$", true, null, null, "runningConfig", true, true, false, false)).id());
		this.created("rule.js", rules.create(new RuleDefinition("JavaScriptRule", "JS check", this.id("policy.routing"), false,
			null, "function check(device) {\n  return { result: CONFORMING, comment: 'ok' };\n}",
			null, null, null, null, null, null, null, null, null)).id());
		this.created("rule.python", rules.create(new RuleDefinition("PythonRule", "Python check", this.id("policy.routing"), false,
			null, "def check(device):\n  return {'result': result_option.CONFORMING, 'comment': 'ok'}",
			null, null, null, null, null, null, null, null, null)).id());

		SoftwareRuleService softwareRules = TestWiring.get().softwareRuleService();
		this.created("rule.software.gold", softwareRules.create(new SoftwareRuleDefinition(this.id("group.dynamic"),
			"CiscoIOS12", "", false, "16.1.6", false, "", false, ConformanceLevel.GOLD)).rule().id());
		this.created("rule.software.silver", softwareRules.create(new SoftwareRuleDefinition(null,
			"CiscoIOS12", "Cisco 1900", false, "15\\..*", true, "", false, ConformanceLevel.SILVER)).rule().id());
		HardwareRuleService hardwareRules = TestWiring.get().hardwareRuleService();
		this.created("rule.hardware.1", hardwareRules.create(new HardwareRuleDefinition(this.id("group.dynamic"),
			"CiscoIOS12", "", false, "CISCO2911/K9", false, new Date(D2040), new Date(D2040 + 365L * 24 * 3600 * 1000))).rule().id());
		this.created("rule.hardware.2", hardwareRules.create(new HardwareRuleDefinition(null,
			"", "Cisco 1900.*", true, "", false, new Date(D2022), null)).rule().id());
	}

	/**
	 * Builds the definition of the "No telnet" text rule.
	 * @param policyId the ID of the policy
	 * @param enabled whether the rule is enabled
	 * @param exemptions the exemptions (or null)
	 * @return the definition
	 */
	private static RuleDefinition textRule(long policyId, boolean enabled, Map<Long, Date> exemptions) {
		return new RuleDefinition("TextRule", "No telnet", policyId, enabled, exemptions, null, "transport input telnet", false,
			"line vty", "CiscoIOS12", "runningConfig", false, false, true, true);
	}

	private void seedDiagnostics() {
		DiagnosticService diagnostics = TestWiring.get().diagnosticService();
		this.created("diag.simple.text", diagnostics.create(new DiagnosticDefinition("SimpleDiagnostic", "Reload reason",
			this.id("group.dynamic"), true, "TEXT", null, "CiscoIOS12", "enable", "show version | include reason",
			"(?s).*reason: (.+?)\\n.*", "$1")).diagnostic().id());
		this.created("diag.js.longtext", diagnostics.create(new DiagnosticDefinition("JavaScriptDiagnostic", "Full inventory",
			-1, true, "LONGTEXT", "function diagnose(cli, device, diagnostic) {\n  diagnostic.set('x');\n}",
			null, null, null, null, null)).diagnostic().id());
		this.created("diag.python.numeric", diagnostics.create(new DiagnosticDefinition("PythonDiagnostic", "CPU load",
			this.id("group.static"), false, "NUMERIC", "def diagnose(cli, device, diagnostic):\n  diagnostic.set(1)",
			null, null, null, null, null)).diagnostic().id());
		this.created("diag.simple.binary", diagnostics.create(new DiagnosticDefinition("SimpleDiagnostic", "Config saved",
			-1, true, "BINARY", null, "CiscoIOS12", "enable", "show startup-config", "", "")).diagnostic().id());
	}

	private void seedHooks() {
		HookService hooks = TestWiring.get().hookService();
		Set<HookTrigger> triggers = new LinkedHashSet<>(List.of(new HookTrigger(TriggerType.POST_TASK, "TakeSnapshotTask"),
			new HookTrigger(TriggerType.POST_TASK, "CheckComplianceTask")));
		this.created("hook.web1", hooks.create(new WebHook(0, 0, null, "Notify on tasks", true, triggers, Action.POST_JSON,
			"https://example.net/callback1", null, null)).id());
		this.created("hook.web2", hooks.create(new WebHook(0, 0, null, "Disabled XML hook", false, new LinkedHashSet<>(),
			Action.POST_XML, "http://example.net/callback2", HttpsCaTrustMode.TRUST_ANY, null)).id());
	}

	private void seedScripts() {
		ScriptService scripts = TestWiring.get().scriptService();
		this.created("script.1", scripts.create(new ScriptCreation("Show version", "CiscoIOS12",
			"function run(cli, device) {\n  cli.macro(\"enable\");\n  cli.command(\"show version\");\n}", SCRIPT_AUTHOR,
			"Maintenance")).id());
		this.created("script.2", scripts.create(new ScriptCreation("Other script", "CiscoIOS12",
			"function run(cli, device) {\n  cli.command(\"show clock\");\n}", SCRIPT_AUTHOR, "")).id());
	}

	private void seedUsers() {
		UserService users = TestWiring.get().userService();
		this.created("user.readonly", users.create(new UserCreation("reader", true, USER_PASSWORD, UiUser.LEVEL_READONLY)).id());
		this.created("user.rw", users.create(new UserCreation("writer", true, USER_PASSWORD, UiUser.LEVEL_READWRITE)).id());
		this.created("user.admin", users.create(new UserCreation("boss", true, USER_PASSWORD, UiUser.LEVEL_ADMIN)).id());
		this.created("user.remote", users.create(new UserCreation("radius-user", false, null, UiUser.LEVEL_OPERATOR)).id());
	}

	// -------------------------------------------------------------------
	// State which cannot be created through the API
	// -------------------------------------------------------------------

	private void seedComplianceAndDiagnosticResults() {
		// Exemption, through the service
		TestWiring.get().ruleService().update(this.id("rule.text"), textRule(this.id("policy.security"), true,
			Map.of(this.id("device.router2"), new Date(D2040))));
		TestWiring.get().transactions().run(dsl -> {
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
			List<net.netshot.netshot.inventory.diagnostic.DiagnosticResult> results1 = List.of(
				DiagnosticTextResult.of(text1.id(), text1.name(), "Reload Command"),
				DiagnosticLongTextResult.of(longText.id(), longText.name(), "Long diagnostic\nresult\n" + "y".repeat(200)),
				DiagnosticNumericResult.of(numeric.id(), numeric.name(), 42.5),
				DiagnosticBinaryResult.of(binary.id(), binary.name(), true));
			List<net.netshot.netshot.inventory.diagnostic.DiagnosticResult> results2 = List.of(
				DiagnosticTextResult.of(text1.id(), text1.name(), "Power failure"),
				DiagnosticBinaryResult.of(binary.id(), binary.name(), false));
			long date = D2021;
			DiagnosticResultRepository resultRepository = new DiagnosticResultRepository();
			for (Device device : List.of(router1, router2)) {
				List<net.netshot.netshot.inventory.diagnostic.DiagnosticResult> dated = new ArrayList<>();
				for (net.netshot.netshot.inventory.diagnostic.DiagnosticResult result : device == router1 ? results1 : results2) {
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
		TestWiring.get().transactions().run(dsl -> {
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
