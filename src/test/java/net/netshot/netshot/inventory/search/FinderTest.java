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
package net.netshot.netshot.inventory.search;

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PolicyRepository;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleRepository;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.diagnostic.DiagnosticRepository;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.search.Finder.FinderParseException;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

@DatabaseTest
@DisplayName("Device finder test")
public class FinderTest {

	// Test data accessible to all tests
	protected static List<Domain> testDomains = new ArrayList<>();
	protected static List<Device> testDevices = new ArrayList<>();
	protected static Map<AttributeType, Diagnostic> testDiagnostics = new HashMap<>();
	protected static List<Policy> testPolicies = new ArrayList<>();
	protected static Map<String, Rule> testRules = new HashMap<>();

	private static <A> List<A> withReplaced(List<A> attributes, Function<A, String> nameOf, A attribute) {
		List<A> result = new ArrayList<>();
		for (A a : attributes) {
			result.add(nameOf.apply(a).equals(nameOf.apply(attribute)) ? attribute : a);
		}
		return result;
	}

	private static List<DeviceAttribute> withDeviceAttribute(Device device, DeviceAttribute attribute) {
		return withReplaced(device.attributes(), DeviceAttribute::name, attribute);
	}

	private static Config withIosImageFile(Config config, String imageFile) {
		return config.toBuilder().attributes(withReplaced(config.attributes(), ConfigAttribute::name,
			new ConfigTextAttribute("iosImageFile", imageFile))).build();
	}

	private static Instant startOfDay(String day) {
		return LocalDate.parse(day).atStartOfDay(ZoneId.systemDefault()).toInstant();
	}

	protected static void createDevices() throws IOException, InterruptedException, GeneralSecurityException {
		// Create domains
		for (int i = 1; i < 3; i++) {
			testDomains.add(TestDevices.addDomain(Domain.builder()
				.name("Domain %d".formatted(i)).description("Test Domain for devices")
				.server4Address(new Network4Address("10.%d.1.1".formatted(i))).build()));
		}

		new Transactions().runDsl(dsl -> {
			// Create test diagnostics BEFORE devices
			Diagnostic textDiagnostic = new SimpleDiagnostic(0,
				"System Status", true, null, AttributeType.TEXT,
				"CiscoIOS12", "enable", "show version", null, null);
			textDiagnostic = textDiagnostic.withId(new DiagnosticRepository().add(dsl, textDiagnostic));
			testDiagnostics.put(AttributeType.TEXT, textDiagnostic);

			Diagnostic numericDiagnostic = new SimpleDiagnostic(0,
				"CPU Usage", true, null, AttributeType.NUMERIC,
				"CiscoIOS12", "enable", "show proc cpu", null, null);
			numericDiagnostic = numericDiagnostic.withId(new DiagnosticRepository().add(dsl, numericDiagnostic));
			testDiagnostics.put(AttributeType.NUMERIC, numericDiagnostic);

			Diagnostic binaryDiagnostic = new SimpleDiagnostic(0,
				"Is Reachable", true, null, AttributeType.BINARY,
				"CiscoIOS12", "enable", "ping 8.8.8.8", null, null);
			binaryDiagnostic = binaryDiagnostic.withId(new DiagnosticRepository().add(dsl, binaryDiagnostic));
			testDiagnostics.put(AttributeType.BINARY, binaryDiagnostic);

			Policy securityPolicy = new Policy(0, "Security Policy", null);
			securityPolicy = securityPolicy.withId(new PolicyRepository().add(dsl, securityPolicy));
			testPolicies.add(securityPolicy);

			Policy configPolicy = new Policy(0, "Config Policy", null);
			configPolicy = configPolicy.withId(new PolicyRepository().add(dsl, configPolicy));
			testPolicies.add(configPolicy);

			TextRule passwordRuleDraft = TextRule.builder("Password Check", securityPolicy.id()).enabled(true).build();
			TextRule passwordRule = passwordRuleDraft.withId(new RuleRepository().add(dsl, passwordRuleDraft));
			testRules.put("Security Policy/Password Check", passwordRule);

			TextRule hostnameRuleDraft = TextRule.builder("Hostname Standard", configPolicy.id()).enabled(true).build();
			TextRule hostnameRule = hostnameRuleDraft.withId(new RuleRepository().add(dsl, hostnameRuleDraft));
			testRules.put("Config Policy/Hostname Standard", hostnameRule);

			TextRule snmpRuleDraft = TextRule.builder("SNMP Community", securityPolicy.id()).enabled(true).build();
			TextRule snmpRule = snmpRuleDraft.withId(new RuleRepository().add(dsl, snmpRuleDraft));
			testRules.put("Security Policy/SNMP Community", snmpRule);
		});

		// Create devices
		for (int i = 0; i < 100; i++) {
			Device device = FakeDeviceFactory.getFakeCiscoIosDevice(
				testDomains.get(0), null, i);
			Config config = FakeDeviceFactory.getFakeCiscoIosConfig(device);
			Device.DeviceBuilder builder = device.toBuilder();
			if (i < 10) {
				builder.status(Device.Status.DISABLED);
			}
			if (i == 10) {
				List<NetworkInterface> interfaces = new ArrayList<>(device.networkInterfaces());
				interfaces.set(0, interfaces.get(0).toBuilder().description("Specific description").build());
				builder.networkInterfaces(interfaces).serialNumber("SERIALNUMBER10");
			}
			if (i == 11) {
				List<Module> modules = new ArrayList<>(device.modules());
				modules.set(0, modules.get(0).toBuilder().partNumber("SPECSN11").build());
				builder.modules(modules);
			}
			if (i == 12) {
				builder.driver("JuniperJunos").family("SRX300").networkClass(NetworkClass.FIREWALL);
			}
			if (i == 13) {
				builder.driver("CiscoIOSXR");
			}
			if (i == 14) {
				builder.createdDate(startOfDay("2070-10-10"));
			}
			if (i == 15) {
				builder.createdDate(startOfDay("2023-01-02"));
			}
			if (i == 16) {
				config = withIosImageFile(config, "ciscoios-advanced-16.1.6.bin");
			}
			if (i == 17) {
				builder.attributes(withDeviceAttribute(device, new DeviceNumericAttribute("mainMemorySize", 4096.0)));
			}
			if (i == 18) {
				builder.softwareVersion("15.2.3").softwareLevel(ConformanceLevel.UNKNOWN);
				config = withIosImageFile(config, "ciscoios-advanced-15.2.3.bin");
			}
			if (i == 19) {
				builder.softwareVersion("15.2.4").softwareLevel(ConformanceLevel.BRONZE);
				config = withIosImageFile(config, "ciscoios-advanced-15.2.4.bin");
			}
			if (i == 20) {
				builder.attributes(withDeviceAttribute(device, new DeviceBinaryAttribute("configurationSaved", false)));
			}
			if (i == 21) {
				builder.attributes(device.attributes().stream().filter(a -> !"configurationSaved".equals(a.name())).toList());
			}
			if (i == 40) {
				builder.tags(Device.normalizeTags(List.of("core", "Paris-DC1")));
			}
			if (i == 41) {
				builder.tags(Device.normalizeTags(List.of(" core ", "lab")));
			}

			// Add diagnostic results to specific devices
			List<DiagnosticResult> results = new ArrayList<>();
			if (i == 22) {
				results.add(DiagnosticTextResult.of(testDiagnostics.get(AttributeType.TEXT), "OK"));
			}
			if (i == 23) {
				results.add(DiagnosticTextResult.of(testDiagnostics.get(AttributeType.TEXT), "WARNING"));
			}
			if (i == 24) {
				results.add(DiagnosticTextResult.of(testDiagnostics.get(AttributeType.TEXT), "CRITICAL"));
			}
			if (i == 25) {
				results.add(DiagnosticNumericResult.of(testDiagnostics.get(AttributeType.NUMERIC), 45.5));
			}
			if (i == 26) {
				results.add(DiagnosticNumericResult.of(testDiagnostics.get(AttributeType.NUMERIC), 78.2));
			}
			if (i == 27) {
				results.add(DiagnosticNumericResult.of(testDiagnostics.get(AttributeType.NUMERIC), 92.8));
			}
			if (i == 28) {
				results.add(DiagnosticBinaryResult.of(testDiagnostics.get(AttributeType.BINARY), true));
			}
			if (i == 29) {
				results.add(DiagnosticBinaryResult.of(testDiagnostics.get(AttributeType.BINARY), false));
			}
			if (i == 30) {
				results.add(DiagnosticBinaryResult.of(testDiagnostics.get(AttributeType.BINARY), true));
			}

			Device stored = TestDevices.add(builder.build(), config, results.toArray(new DiagnosticResult[0]));
			testDevices.add(stored);

			// Add compliance check results AFTER device is persisted
			CheckResult checkResult = switch (i) {
				case 31 -> CheckResult.of(testRules.get("Security Policy/Password Check").id(), stored.id(), CheckResult.ResultOption.CONFORMING);
				case 32 -> CheckResult.of(testRules.get("Security Policy/Password Check").id(), stored.id(), CheckResult.ResultOption.NONCONFORMING);
				case 33 -> CheckResult.of(testRules.get("Config Policy/Hostname Standard").id(), stored.id(), CheckResult.ResultOption.CONFORMING);
				case 34 -> CheckResult.of(testRules.get("Security Policy/SNMP Community").id(), stored.id(), CheckResult.ResultOption.NOTAPPLICABLE);
				case 35 -> CheckResult.of(testRules.get("Security Policy/Password Check").id(), stored.id(), CheckResult.ResultOption.EXEMPTED);
				case 36 -> CheckResult.of(testRules.get("Config Policy/Hostname Standard").id(), stored.id(), CheckResult.ResultOption.DISABLED);
				default -> null;
			};
			if (checkResult != null) {
				new Transactions().runDsl(dsl -> new RuleRepository().saveCheckResults(dsl, List.of(checkResult)));
			}
		}
	}

	@BeforeAll
	protected static void init() throws Exception {
		FinderTest.createDevices();
	}

	/**
	 * Checks a query: its formatted form, and the devices it finds.
	 *
	 * @param nsQuery = the query to test
	 * @param expectedFormattedQuery = the expected formatted query (null: the query itself)
	 * @param expectedDeviceNames the names of the devices the query should match
	 * @throws Exception
	 */
	private void assertFinder(String nsQuery, String expectedFormattedQuery,
			List<String> expectedDeviceNames) throws Exception {
		Finder finder = new Finder(nsQuery);
		Assertions.assertEquals(
			expectedFormattedQuery == null ? nsQuery : expectedFormattedQuery,
			finder.getFormattedQuery(),
			"Unexpected formatted query");
		// The query carries no ORDER BY, so row order is not guaranteed by the database:
		// compare as sets, sorted only for a readable failure diff.
		List<String> sortedExpected = new ArrayList<>(expectedDeviceNames);
		List<String> sortedActual = new ArrayList<>(findDeviceNames(finder));
		Collections.sort(sortedExpected);
		Collections.sort(sortedActual);
		Assertions.assertArrayEquals(
			sortedExpected.toArray(new String[0]),
			sortedActual.toArray(new String[0]),
			"Unexpected device list");
	}

	/**
	 * Finds the names of the devices matching a query.
	 *
	 * @param finder the parsed query
	 * @return the device names
	 */
	private static List<String> findDeviceNames(Finder finder) {
		return new Transactions().readDsl(dsl -> dsl.select(DEVICE.NAME).from(DEVICE).where(finder.getCondition()).fetch(DEVICE.NAME));
	}

	@Test
	@DisplayName("Empty query - all devices")
	void queryEmpty() throws Exception {
		List<String> routers0To99 = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			routers0To99.add("router%05d".formatted(i));
		}
		assertFinder("",
			null,
			routers0To99);
	}

	@Test
	@DisplayName("Query disabled devices")
	void queryDisabled() throws Exception {
		List<String> routers0To9 = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			routers0To9.add("router%05d".formatted(i));
		}
		assertFinder("[Status] is \"DISABLED\"",
			"[Status] is DISABLED",
			routers0To9);
	}

	@Test
	@DisplayName("Query disabled devices without quotes")
	void queryDisabledNoQuote() throws Exception {
		List<String> routers0To9 = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			routers0To9.add("router%05d".formatted(i));
		}
		assertFinder("[Status] is DISABLED",
			null,
			routers0To9);
	}

	@Test
	@DisplayName("Query device by family")
	void queryByFamilyMatch() throws Exception {
		assertFinder("[Family] matches \"^SRX[0-9]+$\"",
			null,
			List.of("router00012"));
	}

	@Test
	@DisplayName("Query device by network class")
	void queryByNetworkClass() throws Exception {
		assertFinder("[Network Class] is FIREWALL",
			null,
			List.of("router00012"));
	}

	@Test
	@DisplayName("Query device by name")
	void queryByName() throws Exception {
		assertFinder("[Name] is \"router00010\"",
			null,
			List.of("router00010"));
	}

	@Test
	@DisplayName("Query device by name starting with text")
	void queryByNameStartingWith() throws Exception {
		assertFinder("[Name] startswith \"router0001\"",
			null,
			List.of("router00010", "router00011", "router00012",
				"router00013", "router00014", "router00015", "router00016",
				"router00017", "router00018", "router00019"));
	}

	@Test
	@DisplayName("Query device by name ending with text")
	void queryByNameEndingWith() throws Exception {
		assertFinder("[Name] endswith \"09\"",
			null,
			List.of("router00009"));
	}

	@Test
	@DisplayName("Query device by name containing text")
	void queryByNameContaining() throws Exception {
		assertFinder("[Name] contains \"ter0001\"",
			null,
			List.of("router00010", "router00011", "router00012",
				"router00013", "router00014", "router00015", "router00016",
				"router00017", "router00018", "router00019"));
	}

	@Test
	@DisplayName("Query device by name containing text, case insensitive")
	void queryByNameContainingNoCase() throws Exception {
		assertFinder("[Name] containsnocase \"OUter00010\"",
			null,
			List.of("router00010"));
	}

	@Test
	@DisplayName("Query device by name matching text")
	void queryByNameMatching() throws Exception {
		assertFinder("[Name] matches \"^rou.*[12]1$\"",
			null,
			List.of("router00011", "router00021"));
	}

	@Test
	@DisplayName("Query device by ID")
	void queryById() throws Exception {
		Device device = testDevices.get(1);
		long id = device.id();
		assertFinder("[ID] is %d".formatted(id),
			null,
			List.of(device.name()));
	}

	@Test
	@DisplayName("Query device by serial number")
	void queryBySerialNumber() throws Exception {
		Device device = testDevices.get(10);
		String serial = device.serialNumber();
		assertFinder("[Serial Number] is \"%s\"".formatted(serial),
			null,
			List.of(device.name()));
	}

	@Test
	@DisplayName("Query device by driver starting with text")
	void queryByDriverMatching() throws Exception {
		assertFinder("[Driver] startswith \"Juniper\"",
			"[Type] startswith \"Juniper\"",
			List.of("router00012"));
	}

	@Test
	@DisplayName("Query device by type starting with text")
	void queryByTypeMatching() throws Exception {
		assertFinder("[Type] startswith \"Juniper\"",
			null,
			List.of("router00012"));
	}

	@Test
	@DisplayName("Query device by IP")
	void queryByIp() throws Exception {
		assertFinder("[IP] is 10.0.2.1",
			null,
			List.of("router00002"));
	}

	@Test
	@DisplayName("Query device by IP range")
	void queryByIpRange() throws Exception {
		assertFinder("[IP] in 10.0.0.0/22",
			null,
			List.of("router00000", "router00001", "router00002", "router00003"));
	}

	@Test
	@DisplayName("Query device by cached IP address (not on any configured interface)")
	void queryByCachedIp() throws Exception {
		// Every test device's cachedIpAddress is auto-resolved from its mgmt
		// address (172.16.0.<shift>) at creation time; that mgmt address doesn't
		// match any configured interface, so a match here can only come from
		// the new cachedIpAddress column.
		assertFinder("[IP] is 172.16.0.50",
			null,
			List.of("router00050"));
	}

	@Test
	@DisplayName("Query device by cached IP subnet (not on any configured interface)")
	void queryByCachedIpRange() throws Exception {
		assertFinder("[IP] in 172.16.0.60/30",
			null,
			List.of("router00060", "router00061", "router00062", "router00063"));
	}

	@Test
	@DisplayName("Query device by interface description")
	void queryByInterfaceDescription() throws Exception {
		assertFinder("[Interface] is \"Specific description\"",
			null,
			List.of("router00010"));
	}

	@Test
	@DisplayName("Query device by module containing text, case insensitive")
	void queryByModuleText() throws Exception {
		assertFinder("[Module] containsnocase \"csn11\"",
			null,
			List.of("router00011"));
	}

	@Test
	@DisplayName("Query device by tag")
	void queryByTag() throws Exception {
		assertFinder("[Tag] is \"core\"",
			null,
			List.of("router00040", "router00041"));
	}

	@Test
	@DisplayName("Query device by tag, case insensitive, lower case token")
	void queryByTagContainsNoCase() throws Exception {
		assertFinder("[tag] containsnocase \"paris\"",
			"[Tag] containsnocase \"paris\"",
			List.of("router00040"));
	}

	@Test
	@DisplayName("Query device by tag with regular expression")
	void queryByTagMatches() throws Exception {
		assertFinder("[Tag] matches \"^(lab|test)$\"",
			null,
			List.of("router00041"));
	}

	@Test
	@DisplayName("Query device without a given tag")
	void queryByNotTag() throws Exception {
		List<String> deviceNames = findDeviceNames(new Finder("not ([Tag] is \"core\")"));
		Assertions.assertEquals(98, deviceNames.size(), "Unexpected number of devices");
		Assertions.assertFalse(deviceNames.contains("router00040"));
	}

	@Test
	@DisplayName("Invalid operator for tag")
	void queryByTagInvalidOperator() {
		Assertions.assertThrows(FinderParseException.class, () -> new Finder("[Tag] lessthan \"core\""));
	}

	@Test
	@DisplayName("Query device by attribute - IOS device by image file")
	void queryByIosImageFile() throws Exception {
		assertFinder("[Cisco IOS and IOS-XE > IOS image file] is \"ciscoios-advanced-16.1.6.bin\"",
			null,
			List.of("router00016"));
	}

	@Test
	@DisplayName("Query device by attribute - configuration saved - positive")
	void queryByConfigurationSaved1() throws Exception {
		assertFinder("[Cisco IOS and IOS-XE > Configuration saved] is false",
			null,
			List.of("router00020"));
	}

	@Test
	@DisplayName("Query device by attribute - configuration saved - negative")
	void queryByConfigurationSaved2() throws Exception {
		assertFinder("not ([Cisco IOS and IOS-XE > Configuration saved] is true)",
			null,
			List.of(
				"router00012",  // Juniper device
				"router00013",  // Cisco IOS-XR device
				"router00020",  // configurationSaved false
				"router00021"   // no configurationSaved attribute
			));
	}

	@Test
	@DisplayName("Query device by software level")
	void queryBySoftwareLevel() throws Exception {
		assertFinder("not ([Software Level] is GOLD)",
			null,
			List.of("router00018", "router00019"));
	}

	@Test
	@DisplayName("Query device by name with advanced boolean expression")
	void queryByNameAdvancedBoolean1() throws Exception {
		List<String> routers10To29 = new ArrayList<>();
		for (int i = 10; i < 30; i++) {
			routers10To29.add("router%05d".formatted(i));
		}
		assertFinder("[NAME] STARTSWITH \"router0001\" OR [NAME] STARTSWITH \"router0002\" AND NOT([NAME] IS \"router00010\")",
			"([Name] startswith \"router0001\") or (([Name] startswith \"router0002\") and (not ([Name] is \"router00010\")))",
			routers10To29);
	}

	@Test
	@DisplayName("Query device by name with advanced boolean expression")
	void queryByNameAdvancedBoolean2() throws Exception {
		List<String> routers10To29 = new ArrayList<>();
		for (int i = 10; i < 30; i++) {
			routers10To29.add("router%05d".formatted(i));
		}
		routers10To29.remove("router00010");
		assertFinder("([NAME] STARTSWITH \"router0001\" OR [NAME] STARTSWITH \"router0002\") AND NOT([NAME] IS \"router00010\")",
			"(([Name] startswith \"router0001\") or ([Name] startswith \"router0002\")) and (not ([Name] is \"router00010\"))",
			routers10To29);
	}

	@Test
	@DisplayName("Query device by creation date with absolute date")
	void queryByChangeDateAbsolute1() throws Exception {
		assertFinder("[Creation Date] after \"2050-05-15\"",
			null,
			List.of("router00014"));
	}

	@Test
	@DisplayName("Query device by creation date range with absolute date")
	void queryByChangeDateAbsolute2() throws Exception {
		assertFinder("[CREATION DATE] IS \"2023-01-02\"",
			"[Creation Date] is \"2023-01-02\"",
			List.of("router00015"));
	}

	@Test
	@DisplayName("Query device by creation date range with TODAY keyword")
	void queryByChangeDateRelative1() throws Exception {
		assertFinder("not ([Creation Date] is \"Today\")",
			null,
			List.of("router00014", "router00015"));
	}

	@Test
	@DisplayName("Query device by creation date with NOW -7d keyword")
	void queryByChangeDateRelative2() throws Exception {
		assertFinder("[Creation Date] before \"Now -7d\"",
			null,
			List.of("router00015"));
	}

	@Test
	@DisplayName("Query device by creation date with NOW - 1 D keyword")
	void queryByChangeDateRelative3() throws Exception {
		assertFinder("[CREATION DATE] IS \"TODAY - 1 D\"",
			"[Creation Date] is \"Today -1d\"",
			List.of());
	}

	@Test
	@DisplayName("Query device by text diagnostic - IS")
	void queryByTextDiagnosticIs() throws Exception {
		assertFinder("[Diagnostic > System Status] is \"OK\"",
			null,
			List.of("router00022"));
	}

	@Test
	@DisplayName("Query device by text diagnostic - CONTAINS")
	void queryByTextDiagnosticContains() throws Exception {
		assertFinder("[Diagnostic > System Status] contains \"WARN\"",
			null,
			List.of("router00023"));
	}

	@Test
	@DisplayName("Query device by text diagnostic - STARTSWITH")
	void queryByTextDiagnosticStartsWith() throws Exception {
		assertFinder("[Diagnostic > System Status] startswith \"CRIT\"",
			null,
			List.of("router00024"));
	}

	@Test
	@DisplayName("Query device by numeric diagnostic - IS")
	void queryByNumericDiagnosticIs() throws Exception {
		assertFinder("[Diagnostic > CPU Usage] is 78.2",
			null,
			List.of("router00026"));
	}

	@Test
	@DisplayName("Query device by numeric diagnostic - GREATER THAN")
	void queryByNumericDiagnosticGreaterThan() throws Exception {
		assertFinder("[Diagnostic > CPU Usage] greaterthan 80",
			null,
			List.of("router00027"));
	}

	@Test
	@DisplayName("Query device by numeric diagnostic - LESS THAN")
	void queryByNumericDiagnosticLessThan() throws Exception {
		assertFinder("[Diagnostic > CPU Usage] lessthan 50",
			null,
			List.of("router00025"));
	}

	@Test
	@DisplayName("Query device by binary diagnostic - IS TRUE")
	void queryByBinaryDiagnosticTrue() throws Exception {
		assertFinder("[Diagnostic > Is Reachable] is true",
			null,
			List.of("router00028", "router00030"));
	}

	@Test
	@DisplayName("Query device by binary diagnostic - IS FALSE")
	void queryByBinaryDiagnosticFalse() throws Exception {
		assertFinder("[Diagnostic > Is Reachable] is false",
			null,
			List.of("router00029"));
	}

	@Test
	@DisplayName("Query device by unknown diagnostic - throws exception")
	void queryByUnknownDiagnostic() throws Exception {
		// Unknown diagnostic should throw FinderParseException
		Assertions.assertThrows(FinderParseException.class,
			() -> new Finder("[Diagnostic > Nonexistent Diagnostic] is \"value\""));
	}

	@Test
	@DisplayName("Query device by diagnostic with wrong type - throws exception")
	void queryByDiagnosticWrongType() throws Exception {
		// Try to use IS TRUE on a text diagnostic - should throw FinderParseException
		Assertions.assertThrows(FinderParseException.class,
			() -> new Finder("[Diagnostic > System Status] is true"));
	}

	@Test
	@DisplayName("Query device by text diagnostic with MATCHES")
	void queryByTextDiagnosticMatches() throws Exception {
		assertFinder("[Diagnostic > System Status] matches \"^(OK|WARNING)$\"",
			null,
			List.of("router00022", "router00023"));
	}

	@Test
	@DisplayName("Combined query - diagnostic AND status")
	void queryCombinedDiagnosticAndStatus() throws Exception {
		assertFinder("[DIAGNOSTIC > CPU Usage] GREATERTHAN 70 AND [STATUS] IS \"INPRODUCTION\"",
			"([Diagnostic > CPU Usage] greaterthan 70) and ([Status] is INPRODUCTION)",
			List.of("router00026", "router00027"));
	}

	@Test
	@DisplayName("Query device by compliance rule - IS CONFORMING")
	void queryByComplianceRuleConforming() throws Exception {
		assertFinder("[Rule > Security Policy > Password Check] is CONFORMING",
			null,
			List.of("router00031"));
	}

	@Test
	@DisplayName("Query device by compliance rule - IS NONCONFORMING")
	void queryByComplianceRuleNonconforming() throws Exception {
		assertFinder("[Rule > Security Policy > Password Check] is \"NONCONFORMING\"",
			"[Rule > Security Policy > Password Check] is NONCONFORMING",
			List.of("router00032"));
	}

	@Test
	@DisplayName("Query device by compliance rule - IS NOTAPPLICABLE")
	void queryByComplianceRuleNotapplicable() throws Exception {
		assertFinder("[Rule > Security Policy > SNMP Community] is NOTAPPLICABLE",
			null,
			List.of("router00034"));
	}

	@Test
	@DisplayName("Query device by compliance rule - IS EXEMPTED")
	void queryByComplianceRuleExempted() throws Exception {
		assertFinder("[Rule > Security Policy > Password Check] is EXEMPTED",
			null,
			List.of("router00035"));
	}

	@Test
	@DisplayName("Query device by compliance rule - IS DISABLED")
	void queryByComplianceRuleDisabled() throws Exception {
		assertFinder("[Rule > Config Policy > Hostname Standard] is DISABLED",
			null,
			List.of("router00036"));
	}

	@Test
	@DisplayName("Query device by compliance rule - different rule")
	void queryByComplianceRuleDifferentRule() throws Exception {
		assertFinder("[Rule > Config Policy > Hostname Standard] is CONFORMING",
			null,
			List.of("router00033"));
	}

	@Test
	@DisplayName("Query device by unknown policy - throws exception")
	void queryByUnknownPolicy() throws Exception {
		Assertions.assertThrows(FinderParseException.class,
			() -> new Finder("[Rule > Unknown Policy > Some Rule] is CONFORMING"));
	}

	@Test
	@DisplayName("Query device by unknown rule - throws exception")
	void queryByUnknownRule() throws Exception {
		Assertions.assertThrows(FinderParseException.class,
			() -> new Finder("[Rule > Security Policy > Unknown Rule] is CONFORMING"));
	}

	@Test
	@DisplayName("Combined query - compliance rule AND status")
	void queryCombinedComplianceAndStatus() throws Exception {
		assertFinder("[RULE > Security Policy > Password Check] IS \"CONFORMING\" AND [STATUS] IS \"INPRODUCTION\"",
			"([Rule > Security Policy > Password Check] is CONFORMING) and ([Status] is INPRODUCTION)",
			List.of("router00031"));
	}

	@Test
	@DisplayName("Simple parser exception")
	void throwParserExceptionSimple() throws Exception {
		Assertions.assertThrows(FinderParseException.class,
			() -> new Finder("[INVALID] is true"),
			"Parser didn't throw exception as expected");
	}

	/**
	 * Queries with the number of devices they find, covering every kind of expression and operator
	 * (the counts were recorded with the former HQL generation, which found the same devices).
	 *
	 * @return the queries and the counts
	 */
	private static List<Entry<String, Integer>> countedQueries() {
		return List.of(
			Map.entry("[Domain] is " + testDomains.get(0).id(), 100),
			Map.entry("[Domain] is " + testDomains.get(1).id(), 0),
			Map.entry("not ([Domain] is " + testDomains.get(0).id() + ")", 0),
			Map.entry("[MAC] is 0011.2233.4455", 0),
			Map.entry("[MAC] in 0011.2233.0000/32", 0),
			Map.entry("[IP] is 2016:1:5:1::1", 1),
			Map.entry("[IP] is 2016:1:5:1::1/64", 1),
			Map.entry("[IP] in 2016:1:5::/48", 1),
			Map.entry("[IP] in 2016:1::/32", 100),
			Map.entry("[IP] is 10.0.5.1/25", 1),
			Map.entry("[IP] in 10.0.5.0/24", 1),
			Map.entry("not ([IP] in 10.0.0.0/16)", 0),
			Map.entry("[VRF] is \"VRF1\"", 100),
			Map.entry("[VRF] contains \"RF\"", 100),
			Map.entry("[VRF] containsnocase \"vrf2\"", 100),
			Map.entry("[VRF] matches \"^VRF[0-9]$\"", 100),
			Map.entry("[VRF] startswith \"VR\"", 100),
			Map.entry("[VRF] endswith \"2\"", 100),
			Map.entry("not ([VRF] is \"VRF1\")", 0),
			Map.entry("[Virtual Name] is \"router00004\"", 1),
			Map.entry("[Virtual Name] containsnocase \"ROUTER0001\"", 10),
			Map.entry("[Virtual Name] matches \"^router0000[1-3]$\"", 3),
			Map.entry("[Module] containsnocase \"testslot\"", 100),
			Map.entry("[Module] matches \"^16161616\"", 100),
			Map.entry("[Module] endswith \"POSD203\"", 100),
			Map.entry("[Interface] contains \"0/1\"", 100),
			Map.entry("[Interface] matches \"^Desc\"", 100),
			Map.entry("[Interface] containsnocase \"GIGABIT\"", 100),
			Map.entry("[Driver] containsnocase \"cisco ios\"", 99),
			Map.entry("[Driver] matches \"^Cisco\"", 99),
			Map.entry("[Cisco IOS and IOS-XE > Running configuration] contains \"hostname\"", 98),
			Map.entry("[Cisco IOS and IOS-XE > Running configuration] containsnocase \"INTERFACE\"", 98),
			Map.entry("[Cisco IOS and IOS-XE > Running configuration] matches \"interface\"", 98),
			Map.entry("not ([Cisco IOS and IOS-XE > Running configuration] contains \"hostname\")", 2),
			Map.entry("[Cisco IOS and IOS-XE > IOS version] startswith \"16\"", 98),
			Map.entry("[Cisco IOS and IOS-XE > IOS version] is \"16.1.6\"", 98),
			Map.entry("[Cisco IOS and IOS-XE > Main memory size (MB)] greaterthan 1000", 98),
			Map.entry("[Cisco IOS and IOS-XE > Main memory size (MB)] lessthan 1000", 0),
			Map.entry("[Cisco IOS and IOS-XE > Main memory size (MB)] is 2048", 97),
			Map.entry("[Cisco IOS and IOS-XE > IOS config register] is \"0x1202\"", 98),
			Map.entry("[Cisco IOS and IOS-XE > IOS config register] containsnocase \"x12\"", 98),
			Map.entry("[Software Level] is GOLD", 98),
			Map.entry("[Network Class] is ROUTER", 99),
			Map.entry("[Status] is INPRODUCTION or [Status] is DISABLED", 100),
			Map.entry("[Comments] contains \"testing\"", 100),
			Map.entry("[Contact] is \"\"", 0),
			Map.entry("[Location] containsnocase \"x\"", 0),
			Map.entry("[Software Version] startswith \"16\"", 98),
			Map.entry("[End of Sale Date] before \"2030-01-01\"", 0),
			Map.entry("[End of Sale Date] is \"2040-01-01\"", 0),
			Map.entry("[End of Life Date] after \"2000-01-01\"", 0),
			Map.entry("[Last Change Date] after \"2000-01-01\"", 100),
			Map.entry("[Last Change Date] before \"2000-01-01\"", 0),
			Map.entry("[Tag] is \"core\" or [VRF] is \"VRF1\"", 100),
			Map.entry("[Rule > Security Policy > Password Check] is CONFORMING or not ([Diagnostic > CPU Usage] greaterthan 80)", 99)
		);
	}

	@TestFactory
	@DisplayName("Query finds the expected number of devices")
	Stream<DynamicTest> queryCount() {
		return countedQueries().stream().map(c -> DynamicTest.dynamicTest(c.getKey(),
			() -> Assertions.assertEquals(c.getValue().intValue(), findDeviceNames(new Finder(c.getKey())).size())));
	}
}
