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
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.compliance.rules.TextRule;
import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.Config;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.DeviceDriver.AccessDefinition;
import net.netshot.netshot.device.DeviceDriver.DriverProtocol;
import net.netshot.netshot.device.Domain;
import net.netshot.netshot.device.Finder;
import net.netshot.netshot.device.Finder.FinderParseException;
import net.netshot.netshot.device.NetworkAddress;
import net.netshot.netshot.device.access.AccessAuthenticationException;
import net.netshot.netshot.device.access.AccessManager;
import net.netshot.netshot.device.access.AccessManager.Resolution;
import net.netshot.netshot.device.access.DeviceAccess;
import net.netshot.netshot.device.access.Client;
import net.netshot.netshot.device.access.InvalidCredentialsException;
import net.netshot.netshot.device.attribute.ConfigTextAttribute;
import net.netshot.netshot.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.device.Network4Address;
import net.netshot.netshot.device.Device.NetworkClass;
import net.netshot.netshot.device.attribute.AttributeDefinition.AttributeType;
import net.netshot.netshot.device.credentials.DeviceCredentialSet;
import net.netshot.netshot.device.credentials.DeviceSshAccount;
import net.netshot.netshot.device.credentials.DeviceTelnetAccount;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.utils.HttpsCaTrustMode;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class DeviceTest extends WithDatabaseTest {

	@Nested
	@DisplayName("Device finder test")
	class DeviceFinderTest {

		// Test data accessible to all tests
		protected static List<Domain> testDomains = new ArrayList<>();
		protected static List<Device> testDevices = new ArrayList<>();
		protected static Map<AttributeType, Diagnostic> testDiagnostics = new HashMap<>();
		protected static List<Policy> testPolicies = new ArrayList<>();
		protected static Map<String, Rule> testRules = new HashMap<>();

		protected static Properties getNetshotConfig() {
			Properties config = getDatabaseConfig("devicetest");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "INFO");
			return config;
		}


		protected static void createDevices() throws IOException, InterruptedException, GeneralSecurityException {
			try (Session session = Database.getSession()) {
				session.beginTransaction();

				// Create domains
				for (int i = 1; i < 3; i++) {
					Domain domain = new Domain(
						"Domain %d".formatted(i), "Test Domain for devices",
						new Network4Address("10.%d.1.1".formatted(i)),
						null
					);
					testDomains.add(domain);
					session.persist(domain);
				}

				// Create test diagnostics BEFORE devices
				Diagnostic textDiagnostic = new SimpleDiagnostic(
					"System Status", true, null, AttributeType.TEXT,
					"CiscoIOS12", "enable", "show version", null, null);
				session.persist(textDiagnostic);
				testDiagnostics.put(AttributeType.TEXT, textDiagnostic);

				Diagnostic numericDiagnostic = new SimpleDiagnostic(
					"CPU Usage", true, null, AttributeType.NUMERIC,
					"CiscoIOS12", "enable", "show proc cpu", null, null);
				session.persist(numericDiagnostic);
				testDiagnostics.put(AttributeType.NUMERIC, numericDiagnostic);

				Diagnostic binaryDiagnostic = new SimpleDiagnostic(
					"Is Reachable", true, null, AttributeType.BINARY,
					"CiscoIOS12", "enable", "ping 8.8.8.8", null, null);
				session.persist(binaryDiagnostic);
				testDiagnostics.put(AttributeType.BINARY, binaryDiagnostic);

				Policy securityPolicy = new Policy("Security Policy", null);
				session.persist(securityPolicy);
				testPolicies.add(securityPolicy);

				Policy configPolicy = new Policy("Config Policy", null);
				session.persist(configPolicy);
				testPolicies.add(configPolicy);

				TextRule passwordRule = new TextRule("Password Check", securityPolicy);
				passwordRule.setEnabled(true);
				session.persist(passwordRule);
				testRules.put("Security Policy/Password Check", passwordRule);

				TextRule hostnameRule = new TextRule("Hostname Standard", configPolicy);
				hostnameRule.setEnabled(true);
				session.persist(hostnameRule);
				testRules.put("Config Policy/Hostname Standard", hostnameRule);

				TextRule snmpRule = new TextRule("SNMP Community", securityPolicy);
				snmpRule.setEnabled(true);
				session.persist(snmpRule);
				testRules.put("Security Policy/SNMP Community", snmpRule);

				// Create devices
				for (int i = 0; i < 100; i++) {
					Device device = FakeDeviceFactory.getFakeCiscoIosDevice(
						testDomains.get(0), null, i);
					if (i < 10) {
						device.setStatus(Device.Status.DISABLED);
					}
					if (i == 10) {
						device.getNetworkInterfaces().get(0)
							.setDescription("Specific description");
						device.setSerialNumber("SERIALNUMBER10");
					}
					if (i == 11) {
						device.getModules().get(0)
							.setPartNumber("SPECSN11");
					}
					if (i == 12) {
						device.setDriver("JuniperJunos");
						device.setFamily("SRX300");
						device.setNetworkClass(NetworkClass.FIREWALL);
					}
					if (i == 13) {
						device.setDriver("CiscoIOSXR");
					}
					if (i == 14) {
						device.setCreatedDate(Date.from(LocalDate.parse("2070-10-10").atStartOfDay(ZoneId.systemDefault()).toInstant()));
					}
					if (i == 15) {
						device.setCreatedDate(Date.from(LocalDate.parse("2023-01-02").atStartOfDay(ZoneId.systemDefault()).toInstant()));
					}
					if (i == 16) {
						((ConfigTextAttribute) device.getLastConfig().getAttribute("iosImageFile"))
							.setText("ciscoios-advanced-16.1.6.bin");
					}
					if (i == 17) {
						((DeviceNumericAttribute) device.getAttribute("mainMemorySize"))
							.setNumber(4096.0);
					}
					if (i == 18) {
						device.setSoftwareVersion("15.2.3");
						device.setSoftwareLevel(ConformanceLevel.UNKNOWN);
						((ConfigTextAttribute) device.getLastConfig().getAttribute("iosImageFile"))
							.setText("ciscoios-advanced-15.2.3.bin");
					}
					if (i == 19) {
						device.setSoftwareVersion("15.2.4");
						device.setSoftwareLevel(ConformanceLevel.BRONZE);
						((ConfigTextAttribute) device.getLastConfig().getAttribute("iosImageFile"))
							.setText("ciscoios-advanced-15.2.4.bin");
					}
					if (i == 20) {
						((DeviceBinaryAttribute) device.getAttribute("configurationSaved"))
							.setAssumption(false);
					}
					if (i == 21) {
						device.removeAttribute("configurationSaved");
					}
					if (i == 40) {
						device.addTag("core");
						device.addTag("Paris-DC1");
					}
					if (i == 41) {
						device.addTag(" core ");
						device.addTag("lab");
					}

					// Add diagnostic results to specific devices
					if (i == 22) {
						DiagnosticTextResult textResult = new DiagnosticTextResult(
							device, testDiagnostics.get(AttributeType.TEXT), "OK");
						device.addDiagnosticResult(textResult);
					}
					if (i == 23) {
						DiagnosticTextResult textResult = new DiagnosticTextResult(
							device, testDiagnostics.get(AttributeType.TEXT), "WARNING");
						device.addDiagnosticResult(textResult);
					}
					if (i == 24) {
						DiagnosticTextResult textResult = new DiagnosticTextResult(
							device, testDiagnostics.get(AttributeType.TEXT), "CRITICAL");
						device.addDiagnosticResult(textResult);
					}
					if (i == 25) {
						DiagnosticNumericResult numResult = new DiagnosticNumericResult(
							device, testDiagnostics.get(AttributeType.NUMERIC), 45.5);
						device.addDiagnosticResult(numResult);
					}
					if (i == 26) {
						DiagnosticNumericResult numResult = new DiagnosticNumericResult(
							device, testDiagnostics.get(AttributeType.NUMERIC), 78.2);
						device.addDiagnosticResult(numResult);
					}
					if (i == 27) {
						DiagnosticNumericResult numResult = new DiagnosticNumericResult(
							device, testDiagnostics.get(AttributeType.NUMERIC), 92.8);
						device.addDiagnosticResult(numResult);
					}
					if (i == 28) {
						DiagnosticBinaryResult binResult = new DiagnosticBinaryResult(
							device, testDiagnostics.get(AttributeType.BINARY), true);
						device.addDiagnosticResult(binResult);
					}
					if (i == 29) {
						DiagnosticBinaryResult binResult = new DiagnosticBinaryResult(
							device, testDiagnostics.get(AttributeType.BINARY), false);
						device.addDiagnosticResult(binResult);
					}
					if (i == 30) {
						DiagnosticBinaryResult binResult = new DiagnosticBinaryResult(
							device, testDiagnostics.get(AttributeType.BINARY), true);
						device.addDiagnosticResult(binResult);
					}

					testDevices.add(device);
					session.persist(device);

					// Add compliance check results AFTER device is persisted
					if (i == 31) {
						CheckResult conformingResult = new CheckResult(
							testRules.get("Security Policy/Password Check"), device, CheckResult.ResultOption.CONFORMING);
						session.persist(conformingResult);
					}
					if (i == 32) {
						CheckResult nonconformingResult = new CheckResult(
							testRules.get("Security Policy/Password Check"), device, CheckResult.ResultOption.NONCONFORMING);
						session.persist(nonconformingResult);
					}
					if (i == 33) {
						CheckResult conformingResult = new CheckResult(
							testRules.get("Config Policy/Hostname Standard"), device, CheckResult.ResultOption.CONFORMING);
						session.persist(conformingResult);
					}
					if (i == 34) {
						CheckResult notapplicableResult = new CheckResult(
							testRules.get("Security Policy/SNMP Community"), device, CheckResult.ResultOption.NOTAPPLICABLE);
						session.persist(notapplicableResult);
					}
					if (i == 35) {
						CheckResult exemptedResult = new CheckResult(
							testRules.get("Security Policy/Password Check"), device, CheckResult.ResultOption.EXEMPTED);
						session.persist(exemptedResult);
					}
					if (i == 36) {
						CheckResult disabledResult = new CheckResult(
							testRules.get("Config Policy/Hostname Standard"), device, CheckResult.ResultOption.DISABLED);
						session.persist(disabledResult);
					}
				}

				session.getTransaction().commit();
			}
		}

		@BeforeAll
		protected static void init() throws Exception {
			Netshot.initConfig(DeviceFinderTest.getNetshotConfig());
			Netshot.loadModuleConfigs();
			DeviceDriver.refreshDrivers();
			Database.update();
			Database.init();
			DeviceFinderTest.createDevices();
		}

		/**
		 * Assert finder HQL and parameters results equal the expected values.
		 * @param nsQuery = Netshot query
		 * @param expectedFormattedQuery = the expected formatted query (nsQuery if false)
		 * @param expectedHql = the expected HQL
		 * @param expectedParameters = the expected query parameters
		 * @throws Exception
		 */
		private void assertFinder(String nsQuery, String expectedFormattedQuery,
				String expectedHql,
				Map<String, Object> expectedParameters,
				List<String> expectedDeviceNames) throws Exception {
			FakeQuery<Device> fakeQuery = new FakeQuery<>();
			Finder finder = new Finder(nsQuery);
			finder.setVariables(fakeQuery);
			Assertions.assertEquals(
				expectedFormattedQuery == null ? nsQuery : expectedFormattedQuery,
				finder.getFormattedQuery(),
				"Unexpected formatted query");
			String hqlQuery = finder.getHql();
			if (expectedHql != null) {
				Assertions.assertEquals(expectedHql, hqlQuery, "Unexpected HQL");
			}
			// Skip parameter checking if expectedParameters is null
			if (expectedParameters != null) {
				for (Map.Entry<String, Object> actualEntry : fakeQuery.getParameterHash().entrySet()) {
					if (actualEntry.getValue() instanceof Date) {
						// For relative dates, as the reference "now" is not passed to finder,
						// if the expected and actual dates are very close,
						// we manually override the expected value to let the test pass
						Date actualDate = (Date) actualEntry.getValue();
						Object expectedValue = expectedParameters.get(actualEntry.getKey());
						if (expectedValue instanceof Date) {
							Date expectedDate = (Date) expectedValue;
							Duration diff = Duration.between(actualDate.toInstant(), expectedDate.toInstant());
							if (diff.abs().toMillis() < 500) {
								actualEntry.setValue(expectedDate);
							}
						}
					}
				}
				Assertions.assertEquals(
					expectedParameters,
					fakeQuery.getParameterHash(),
					"Unexpected query parameters");
			}
			try (Session session = Database.getSession()) {
				Query<String> query = session.createQuery(
					"select d.name " + hqlQuery, String.class);
				finder.setVariables(query);
				List<String> deviceNames = query.list();
				// The generated HQL carries no ORDER BY, so row order is not guaranteed
				// by the database (unlike H2's incidental insertion-order behavior,
				// PostgreSQL's query planner is free to return rows in any order) -
				// compare as sets, sorted only for a readable failure diff.
				List<String> sortedExpected = new ArrayList<>(expectedDeviceNames);
				List<String> sortedActual = new ArrayList<>(deviceNames);
				Collections.sort(sortedExpected);
				Collections.sort(sortedActual);
				Assertions.assertArrayEquals(
					sortedExpected.toArray(new String[0]),
					sortedActual.toArray(new String[0]),
					"Unexpected device list");
			}
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
				" from Device d where 1 = 1",
				Map.of(),
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
				" from Device d where d.status = :var",
				Map.of("var", Device.Status.DISABLED),
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
				" from Device d where d.status = :var",
				Map.of("var", Device.Status.DISABLED),
				routers0To9);
		}

		@Test
		@DisplayName("Query device by family")
		void queryByFamilyMatch() throws Exception {
			assertFinder("[Family] matches \"^SRX[0-9]+$\"",
				null,
				" from Device d where regexp_like(d.family, :var)",
				Map.of("var", "^SRX[0-9]+$"),
				List.of("router00012"));
		}

		@Test
		@DisplayName("Query device by network class")
		void queryByNetworkClass() throws Exception {
			assertFinder("[Network Class] is FIREWALL",
				null,
				" from Device d where d.networkClass = :var",
				Map.of("var", Device.NetworkClass.FIREWALL),
				List.of("router00012"));
		}

		@Test
		@DisplayName("Query device by name")
		void queryByName() throws Exception {
			assertFinder("[Name] is \"router00010\"",
				null,
				" from Device d where d.name like :var",
				Map.of("var", "router00010"),
				List.of("router00010"));
		}

		@Test
		@DisplayName("Query device by name starting with text")
		void queryByNameStartingWith() throws Exception {
			assertFinder("[Name] startswith \"router0001\"",
				null,
				" from Device d where d.name like :var",
				Map.of("var", "router0001%"),
				List.of("router00010", "router00011", "router00012",
					"router00013", "router00014", "router00015", "router00016",
					"router00017", "router00018", "router00019"));
		}

		@Test
		@DisplayName("Query device by name ending with text")
		void queryByNameEndingWith() throws Exception {
			assertFinder("[Name] endswith \"09\"",
				null,
				" from Device d where d.name like :var",
				Map.of("var", "%09"),
				List.of("router00009"));
		}

		@Test
		@DisplayName("Query device by name containing text")
		void queryByNameContaining() throws Exception {
			assertFinder("[Name] contains \"ter0001\"",
				null,
				" from Device d where d.name like :var",
				Map.of("var", "%ter0001%"),
				List.of("router00010", "router00011", "router00012",
					"router00013", "router00014", "router00015", "router00016",
					"router00017", "router00018", "router00019"));
		}

		@Test
		@DisplayName("Query device by name containing text, case insensitive")
		void queryByNameContainingNoCase() throws Exception {
			assertFinder("[Name] containsnocase \"OUter00010\"",
				null,
				" from Device d where lower(d.name) like :var",
				Map.of("var", "%outer00010%"),
				List.of("router00010"));
		}

		@Test
		@DisplayName("Query device by name matching text")
		void queryByNameMatching() throws Exception {
			assertFinder("[Name] matches \"^rou.*[12]1$\"",
				null,
				" from Device d where regexp_like(d.name, :var)",
				Map.of("var", "^rou.*[12]1$"),
				List.of("router00011", "router00021"));
		}

		@Test
		@DisplayName("Query device by ID")
		void queryById() throws Exception {
			Device device = testDevices.get(1);
			long id = device.getId();
			assertFinder("[ID] is %d".formatted(id),
				null,
				" from Device d where d.id = :var",
				Map.of("var", id),
				List.of(device.getName()));
		}

		@Test
		@DisplayName("Query device by serial number")
		void queryBySerialNumber() throws Exception {
			Device device = testDevices.get(10);
			String serial = device.getSerialNumber();
			assertFinder("[Serial Number] is \"%s\"".formatted(serial),
				null,
				" from Device d where d.serialNumber like :var",
				Map.of("var", serial),
				List.of(device.getName()));
		}

		@Test
		@DisplayName("Query device by driver starting with text")
		void queryByDriverMatching() throws Exception {
			assertFinder("[Driver] startswith \"Juniper\"",
				"[Type] startswith \"Juniper\"",
				null, // Generated HQL depends on current driver list
				Map.of("var", "Juniper%"),
				List.of("router00012"));
		}

		@Test
		@DisplayName("Query device by type starting with text")
		void queryByTypeMatching() throws Exception {
			assertFinder("[Type] startswith \"Juniper\"",
				null,
				null, // Generated HQL depends on current driver list
				Map.of("var", "Juniper%"),
				List.of("router00012"));
		}

		@Test
		@DisplayName("Query device by IP")
		void queryByIp() throws Exception {
			assertFinder("[IP] is 10.0.2.1",
				null,
				" from Device d where d.id in (select d.id from Device d left join d.networkInterfaces ni left join ni.ip4Addresses ip4 where d.cachedIpAddress = :var_ip or ip4.address = :var_ip)",
				Map.of("var_ip", InetAddress.getByName("10.0.2.1")),
				List.of("router00002"));
		}

		@Test
		@DisplayName("Query device by IP range")
		void queryByIpRange() throws Exception {
			assertFinder("[IP] in 10.0.0.0/22",
				null,
				" from Device d where d.id in (select d.id from Device d left join d.networkInterfaces ni left join ni.ip4Addresses ip4 where net_contains(d.cachedIpAddress, :var_cidr) or net_contains(ip4.address, :var_cidr))",
				Map.of("var_cidr", "10.0.0.0/22"),
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
				null,
				null,
				List.of("router00050"));
		}

		@Test
		@DisplayName("Query device by cached IP subnet (not on any configured interface)")
		void queryByCachedIpRange() throws Exception {
			assertFinder("[IP] in 172.16.0.60/30",
				null,
				null,
				null,
				List.of("router00060", "router00061", "router00062", "router00063"));
		}

		@Test
		@DisplayName("Query device by interface description")
		void queryByInterfaceDescription() throws Exception {
			assertFinder("[Interface] is \"Specific description\"",
				null,
				" from Device d where d.id in (select d.id from Device d left join d.networkInterfaces ni where ni.interfaceName like :var or ni.description like :var)",
				Map.of("var", "Specific description"),
				List.of("router00010"));
		}

		@Test
		@DisplayName("Query device by module containing text, case insensitive")
		void queryByModuleText() throws Exception {
			assertFinder("[Module] containsnocase \"csn11\"",
				null,
				" from Device d where d.id in (select d.id from Device d left join d.modules m where m.removed is not true and (lower(m.serialNumber) like :var or lower(m.partNumber) like :var))",
				Map.of("var", "%csn11%"),
				List.of("router00011"));
		}

		@Test
		@DisplayName("Query device by tag")
		void queryByTag() throws Exception {
			assertFinder("[Tag] is \"core\"",
				null,
				" from Device d where d.id in (select d.id from Device d join d.tags t where t like :var)",
				Map.of("var", "core"),
				List.of("router00040", "router00041"));
		}

		@Test
		@DisplayName("Query device by tag, case insensitive, lower case token")
		void queryByTagContainsNoCase() throws Exception {
			assertFinder("[tag] containsnocase \"paris\"",
				"[Tag] containsnocase \"paris\"",
				" from Device d where d.id in (select d.id from Device d join d.tags t where lower(t) like :var)",
				Map.of("var", "%paris%"),
				List.of("router00040"));
		}

		@Test
		@DisplayName("Query device by tag with regular expression")
		void queryByTagMatches() throws Exception {
			assertFinder("[Tag] matches \"^(lab|test)$\"",
				null,
				" from Device d where d.id in (select d.id from Device d join d.tags t where regexp_like(t, :var))",
				Map.of("var", "^(lab|test)$"),
				List.of("router00041"));
		}

		@Test
		@DisplayName("Query device without a given tag")
		void queryByNotTag() throws Exception {
			FakeQuery<Device> fakeQuery = new FakeQuery<>();
			Finder finder = new Finder("not ([Tag] is \"core\")");
			finder.setVariables(fakeQuery);
			try (Session session = Database.getSession()) {
				Query<String> query = session.createQuery("select d.name " + finder.getHql(), String.class);
				finder.setVariables(query);
				List<String> deviceNames = query.list();
				Assertions.assertEquals(98, deviceNames.size(), "Unexpected number of devices");
				Assertions.assertFalse(deviceNames.contains("router00040"));
			}
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
				" from Device d where d.id in (select d.id from Device d, Config c join c.attributes var_ca with var_ca.name = :var_name where d.driver = :var_driver and d.lastConfig = c and (var_ca.text like :var))",
				Map.of(
					"var", "ciscoios-advanced-16.1.6.bin",
					"var_name", "iosImageFile",
					"var_driver", "CiscoIOS12"
				),
				List.of("router00016"));
		}

		@Test
		@DisplayName("Query device by attribute - configuration saved - positive")
		void queryByConfigurationSaved1() throws Exception {
			assertFinder("[Cisco IOS and IOS-XE > Configuration saved] is false",
				null,
				" from Device d where d.id in (select d.id from Device d join d.attributes var_da with var_da.name = :var_name where d.driver = :var_driver and (var_da.assumption = :var))",
				Map.of(
					"var", false,
					"var_name", "configurationSaved",
					"var_driver", "CiscoIOS12"
				),
				List.of("router00020"));
		}

		@Test
		@DisplayName("Query device by attribute - configuration saved - negative")
		void queryByConfigurationSaved2() throws Exception {
			assertFinder("not ([Cisco IOS and IOS-XE > Configuration saved] is true)",
				null,
				" from Device d where not (d.id in (select d.id from Device d join d.attributes var_0_da with var_0_da.name = :var_0_name where d.driver = :var_0_driver and (var_0_da.assumption = :var_0)))",
				Map.of(
					"var_0", true,
					"var_0_name", "configurationSaved",
					"var_0_driver", "CiscoIOS12"
				),
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
				" from Device d where not (d.softwareLevel = :var_0)",
				Map.of(
					"var_0", ConformanceLevel.GOLD
				),
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
				" from Device d where (d.name like :var_0) or ((d.name like :var_1_0) and (not (d.name like :var_1_1_0)))",
				Map.of(
					"var_0", "router0001%",
					"var_1_0", "router0002%",
					"var_1_1_0", "router00010"
				),
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
				" from Device d where ((d.name like :var_0_0) or (d.name like :var_0_1)) and (not (d.name like :var_1_0))",
				Map.of(
					"var_0_0", "router0001%",
					"var_0_1", "router0002%",
					"var_1_0", "router00010"
				),
				routers10To29);
		}

		@Test
		@DisplayName("Query device by creation date with absolute date")
		void queryByChangeDateAbsolute1() throws Exception {
			assertFinder("[Creation Date] after \"2050-05-15\"",
				null,
				" from Device d where d.createdDate >= :var",
				Map.of(
					"var", Date.from(LocalDate.parse("2050-05-15").atStartOfDay(ZoneId.systemDefault()).toInstant())
				),
				List.of("router00014"));
		}

		@Test
		@DisplayName("Query device by creation date range with absolute date")
		void queryByChangeDateAbsolute2() throws Exception {
			assertFinder("[CREATION DATE] IS \"2023-01-02\"",
				"[Creation Date] is \"2023-01-02\"",
				" from Device d where (d.createdDate >= :var_1 and d.createdDate <= :var_2)",
				Map.of(
					"var_1", Date.from(LocalDate.parse("2023-01-02").atStartOfDay(ZoneId.systemDefault()).toInstant()),
					"var_2", Date.from(LocalDate.parse("2023-01-02").plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
				),
				List.of("router00015"));
		}

		@Test
		@DisplayName("Query device by creation date range with TODAY keyword")
		void queryByChangeDateRelative1() throws Exception {
			assertFinder("not ([Creation Date] is \"Today\")",
				null,
				" from Device d where not ((d.createdDate >= :var_0_1 and d.createdDate <= :var_0_2))",
				Map.of(
					"var_0_1", Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()),
					"var_0_2", Date.from(LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
				),
				List.of("router00014", "router00015"));
		}

		@Test
		@DisplayName("Query device by creation date with NOW -7d keyword")
		void queryByChangeDateRelative2() throws Exception {
			assertFinder("[Creation Date] before \"Now -7d\"",
				null,
				" from Device d where d.createdDate <= :var",
				Map.of(
					"var", Date.from(LocalDateTime.now().minusDays(7).atZone(ZoneId.systemDefault()).toInstant())
				),
				List.of("router00015"));
		}

		@Test
		@DisplayName("Query device by creation date with NOW - 1 D keyword")
		void queryByChangeDateRelative3() throws Exception {
			assertFinder("[CREATION DATE] IS \"TODAY - 1 D\"",
				"[Creation Date] is \"Today -1d\"",
				" from Device d where (d.createdDate >= :var_1 and d.createdDate <= :var_2)",
				Map.of(
					"var_1", Date.from(LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()),
					"var_2", Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant())
				),
				List.of());
		}

		@Test
		@DisplayName("Query device by text diagnostic - IS")
		void queryByTextDiagnosticIs() throws Exception {
			assertFinder("[Diagnostic > System Status] is \"OK\"",
				null,
				" from Device d where d.id in (select d.id from Device d join d.diagnosticResults var_dr where var_dr.diagnostic = :var_diagnostic and (var_dr.text like :var))",
				Map.of(
					"var_diagnostic", testDiagnostics.get(AttributeType.TEXT),  // System Status
					"var", "OK"
				),
				List.of("router00022"));
		}

		@Test
		@DisplayName("Query device by text diagnostic - CONTAINS")
		void queryByTextDiagnosticContains() throws Exception {
			assertFinder("[Diagnostic > System Status] contains \"WARN\"",
				null,
				" from Device d where d.id in (select d.id from Device d join d.diagnosticResults var_dr where var_dr.diagnostic = :var_diagnostic and (var_dr.text like :var))",
				Map.of(
					"var_diagnostic", testDiagnostics.get(AttributeType.TEXT),
					"var", "%WARN%"
				),
				List.of("router00023"));
		}

		@Test
		@DisplayName("Query device by text diagnostic - STARTSWITH")
		void queryByTextDiagnosticStartsWith() throws Exception {
			assertFinder("[Diagnostic > System Status] startswith \"CRIT\"",
				null,
				" from Device d where d.id in (select d.id from Device d join d.diagnosticResults var_dr where var_dr.diagnostic = :var_diagnostic and (var_dr.text like :var))",
				Map.of(
					"var_diagnostic", testDiagnostics.get(AttributeType.TEXT),
					"var", "CRIT%"
				),
				List.of("router00024"));
		}

		@Test
		@DisplayName("Query device by numeric diagnostic - IS")
		void queryByNumericDiagnosticIs() throws Exception {
			assertFinder("[Diagnostic > CPU Usage] is 78.2",
				null,
				" from Device d where d.id in (select d.id from Device d join d.diagnosticResults var_dr where var_dr.diagnostic = :var_diagnostic and (var_dr.number = :var))",
				Map.of(
					"var_diagnostic", testDiagnostics.get(AttributeType.NUMERIC),
					"var", 78.2
				),
				List.of("router00026"));
		}

		@Test
		@DisplayName("Query device by numeric diagnostic - GREATER THAN")
		void queryByNumericDiagnosticGreaterThan() throws Exception {
			assertFinder("[Diagnostic > CPU Usage] greaterthan 80",
				null,
				" from Device d where d.id in (select d.id from Device d join d.diagnosticResults var_dr where var_dr.diagnostic = :var_diagnostic and (var_dr.number > :var))",
				Map.of(
					"var_diagnostic", testDiagnostics.get(AttributeType.NUMERIC),
					"var", 80.0
				),
				List.of("router00027"));
		}

		@Test
		@DisplayName("Query device by numeric diagnostic - LESS THAN")
		void queryByNumericDiagnosticLessThan() throws Exception {
			assertFinder("[Diagnostic > CPU Usage] lessthan 50",
				null,
				" from Device d where d.id in (select d.id from Device d join d.diagnosticResults var_dr where var_dr.diagnostic = :var_diagnostic and (var_dr.number < :var))",
				Map.of(
					"var_diagnostic", testDiagnostics.get(AttributeType.NUMERIC),
					"var", 50.0
				),
				List.of("router00025"));
		}

		@Test
		@DisplayName("Query device by binary diagnostic - IS TRUE")
		void queryByBinaryDiagnosticTrue() throws Exception {
			assertFinder("[Diagnostic > Is Reachable] is true",
				null,
				" from Device d where d.id in (select d.id from Device d join d.diagnosticResults var_dr where var_dr.diagnostic = :var_diagnostic and (var_dr.assumption = :var))",
				Map.of(
					"var_diagnostic", testDiagnostics.get(AttributeType.BINARY),  // Is Reachable
					"var", true
				),
				List.of("router00028", "router00030"));
		}

		@Test
		@DisplayName("Query device by binary diagnostic - IS FALSE")
		void queryByBinaryDiagnosticFalse() throws Exception {
			assertFinder("[Diagnostic > Is Reachable] is false",
				null,
				" from Device d where d.id in (select d.id from Device d join d.diagnosticResults var_dr where var_dr.diagnostic = :var_diagnostic and (var_dr.assumption = :var))",
				Map.of(
					"var_diagnostic", testDiagnostics.get(AttributeType.BINARY),
					"var", false
				),
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
				" from Device d where d.id in (select d.id from Device d join d.diagnosticResults var_dr where var_dr.diagnostic = :var_diagnostic and (regexp_like(var_dr.text, :var)))",
				Map.of(
					"var_diagnostic", testDiagnostics.get(AttributeType.TEXT),
					"var", "^(OK|WARNING)$"
				),
				List.of("router00022", "router00023"));
		}

		@Test
		@DisplayName("Combined query - diagnostic AND status")
		void queryCombinedDiagnosticAndStatus() throws Exception {
			assertFinder("[DIAGNOSTIC > CPU Usage] GREATERTHAN 70 AND [STATUS] IS \"INPRODUCTION\"",
				"([Diagnostic > CPU Usage] greaterthan 70) and ([Status] is INPRODUCTION)",
				" from Device d where (d.id in (select d.id from Device d join d.diagnosticResults var_0_dr where var_0_dr.diagnostic = :var_0_diagnostic and (var_0_dr.number > :var_0))) and (d.status = :var_1)",
				Map.of(
					"var_0_diagnostic", testDiagnostics.get(AttributeType.NUMERIC),  // CPU Usage
					"var_0", 70.0,
					"var_1", Device.Status.INPRODUCTION
				),
				List.of("router00026", "router00027"));
		}

	@Test
	@DisplayName("Query device by compliance rule - IS CONFORMING")
	void queryByComplianceRuleConforming() throws Exception {
		assertFinder("[Rule > Security Policy > Password Check] is CONFORMING",
			null,
			" from Device d where d.id in (select d.id from Device d join d.complianceCheckResults var_cr where var_cr.key.rule = :var_rule and var_cr.result = :var)",
			Map.of(
				"var_rule", testRules.get("Security Policy/Password Check"),
				"var", CheckResult.ResultOption.CONFORMING
			),
			List.of("router00031"));
	}

	@Test
	@DisplayName("Query device by compliance rule - IS NONCONFORMING")
	void queryByComplianceRuleNonconforming() throws Exception {
		assertFinder("[Rule > Security Policy > Password Check] is \"NONCONFORMING\"",
			"[Rule > Security Policy > Password Check] is NONCONFORMING",
			" from Device d where d.id in (select d.id from Device d join d.complianceCheckResults var_cr where var_cr.key.rule = :var_rule and var_cr.result = :var)",
			Map.of(
				"var_rule", testRules.get("Security Policy/Password Check"),
				"var", CheckResult.ResultOption.NONCONFORMING
			),
			List.of("router00032"));
	}

	@Test
	@DisplayName("Query device by compliance rule - IS NOTAPPLICABLE")
	void queryByComplianceRuleNotapplicable() throws Exception {
		assertFinder("[Rule > Security Policy > SNMP Community] is NOTAPPLICABLE",
			null,
			" from Device d where d.id in (select d.id from Device d join d.complianceCheckResults var_cr where var_cr.key.rule = :var_rule and var_cr.result = :var)",
			Map.of(
				"var_rule", testRules.get("Security Policy/SNMP Community"),
				"var", CheckResult.ResultOption.NOTAPPLICABLE
			),
			List.of("router00034"));
	}

	@Test
	@DisplayName("Query device by compliance rule - IS EXEMPTED")
	void queryByComplianceRuleExempted() throws Exception {
		assertFinder("[Rule > Security Policy > Password Check] is EXEMPTED",
			null,
			" from Device d where d.id in (select d.id from Device d join d.complianceCheckResults var_cr where var_cr.key.rule = :var_rule and var_cr.result = :var)",
			Map.of(
				"var_rule", testRules.get("Security Policy/Password Check"),
				"var", CheckResult.ResultOption.EXEMPTED
			),
			List.of("router00035"));
	}

	@Test
	@DisplayName("Query device by compliance rule - IS DISABLED")
	void queryByComplianceRuleDisabled() throws Exception {
		assertFinder("[Rule > Config Policy > Hostname Standard] is DISABLED",
			null,
			" from Device d where d.id in (select d.id from Device d join d.complianceCheckResults var_cr where var_cr.key.rule = :var_rule and var_cr.result = :var)",
			Map.of(
				"var_rule", testRules.get("Config Policy/Hostname Standard"),
				"var", CheckResult.ResultOption.DISABLED
			),
			List.of("router00036"));
	}

	@Test
	@DisplayName("Query device by compliance rule - different rule")
	void queryByComplianceRuleDifferentRule() throws Exception {
		assertFinder("[Rule > Config Policy > Hostname Standard] is CONFORMING",
			null,
			" from Device d where d.id in (select d.id from Device d join d.complianceCheckResults var_cr where var_cr.key.rule = :var_rule and var_cr.result = :var)",
			Map.of(
				"var_rule", testRules.get("Config Policy/Hostname Standard"),
				"var", CheckResult.ResultOption.CONFORMING
			),
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
			" from Device d where (d.id in (select d.id from Device d join d.complianceCheckResults var_0_cr where var_0_cr.key.rule = :var_0_rule and var_0_cr.result = :var_0)) and (d.status = :var_1)",
			Map.of(
				"var_0_rule", testRules.get("Security Policy/Password Check"),
				"var_0", CheckResult.ResultOption.CONFORMING,
				"var_1", Device.Status.INPRODUCTION
			),
			List.of("router00031"));
	}


		@Test
		@DisplayName("Simple parser exception")
		void throwParserExceptionSimple() throws Exception {
			Assertions.assertThrows(FinderParseException.class,
				() -> new Finder("[INVALID] is true"),
				"Parser didn't throw exception as expected");
		}
	}

	@Nested
	@DisplayName("Device config tests")
	class ConfigTest {

		@Test
		@DisplayName("Config line parents")
		void configLineParents() {
			String[] configLines = new String[] {
				/* 00 */ "version 16",
				/* 01 */ "!",
				/* 02 */ "service timestamps debug datetime msec",
				/* 03 */ "no service password-encryption",
				/* 04 */ "",
				/* 05 */ "vrf definition VRF1",
				/* 06 */ " rd 16:16",
				/* 07 */ " address-family ipv4",
				/* 08 */ " exit-address-family",
				/* 09 */ "!",
				/* 10 */ "!",
				/* 11 */ "ip cef",
				/* 12 */ "no ipv6 cef",
				/* 13 */ "!",
				/* 14 */ "!",
				/* 15 */ "spanning-tree mode rapid-pvst",
				/* 16 */ "!",
				/* 17 */ "interface GigabitEthernet0/1",
				/* 18 */ " description Some description",
				/* 19 */ " ip address 192.168.1.254 255.255.255.0",
				/* 20 */ " bandwidth 100000",
				/* 21 */ " negotiation auto",
				/* 22 */ "!",
				/* 23 */ "interface GigabitEthernet0/2",
				/* 24 */ " description Some description",
				/* 25 */ " ip address 192.168.2.254 255.255.255.0",
				/* 26 */ " ip ospf network point-to-point",
				/* 27 */ " negotiation auto",
				/* 28 */ " shutdown",
				/* 29 */ "!",
				/* 30 */ "router bgp 16",
				/* 31 */ " bgp log-neighbor-changes",
				/* 32 */ " no bgp default ipv4-unicast",
				/* 33 */ " neighbor 192.168.255.1 remote-as 17",
				/* 34 */ " !",
				/* 35 */ " address-family ipv4",
				/* 36 */ "  neigbor 192.168.255.1 activate",
				/* 37 */ " exit-address-family",
				/* 38 */ " !",
				/* 39 */ " address-family ipv4 vrf VRF1",
				/* 40 */ "  neighbor 192.168.254.3 remote-as 18",
				/* 41 */ "  neighbor 192.168.254.3 activate",
				/* 42 */ " exit-address-family",
				/* 43 */ "!",
				/* 44 */ "ip forward-protocol nd",
				/* 45 */ "!",
				/* 46 */ "no ip http server",
				/* 47 */ "end",
				/* 48 */ ""
			};
			int[] parents = new int[] {
				-1, -1, -1, -1, -1, -1,
				5, 5, 5,
				-1, -1, -1, -1, -1, -1, -1, -1, -1,
				17, 17, 17, 17,
				-1, -1,
				23, 23, 23, 23, 23,
				-1, -1,
				30, 30, 30, 30, 30,
				35,
				30, 30, 30,
				39, 39,
				30,
				-1, -1, -1, -1, -1, -1,
			};
			int[] computedParents = Config.getLineParents(Arrays.asList(configLines));
			Assertions.assertArrayEquals(parents, computedParents,
				"Parent line indices do not match the expected ones");
		}

	}

	/**
	 * Unit tests for {@link AccessManager}, the generalized lazy connect +
	 * credential-set fallback engine that replaced the triplicated SSH/Telnet/SNMP
	 * loops previously duplicated in {@code DeviceScript.connectRun}.
	 */
	@Nested
	@DisplayName("AccessManager tests")
	class AccessManagerTest {

		@BeforeAll
		protected static void init() throws Exception {
			Properties config = getDatabaseConfig("accessmanagertest");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "INFO");
			Netshot.initConfig(config);
			Database.update();
			Database.init();
			DeviceDriver.refreshDrivers();
		}

		/** A minimal fake {@link Client} whose connect() behavior is scripted by the test. */
		private final class FakeClient implements Client {
			private final AtomicInteger connectCount;
			private final IOException failure;

			FakeClient(AtomicInteger connectCount, IOException failure) {
				this.connectCount = connectCount;
				this.failure = failure;
			}

			@Override
			public void connect() throws IOException {
				this.connectCount.incrementAndGet();
				if (this.failure != null) {
					throw this.failure;
				}
			}

			@Override
			public void disconnect() {
			}
		}

		private AccessDefinition sshAccess(String name) {
			return new AccessDefinition(name, DriverProtocol.SSH, null, null, null, null, 22);
		}

		private AccessDefinition telnetAccess(String name) {
			return new AccessDefinition(name, DriverProtocol.TELNET, null, null, null, null, 23);
		}

		private Device fakeDevice() {
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			return new Device("CiscoIOS12", null, domain, "test");
		}

		@Test
		@DisplayName("Resolution is lazy: no connect() attempt happens before first use")
		void resolutionIsLazy() throws IOException {
			Device device = fakeDevice();
			DeviceCredentialSet cred = new DeviceSshAccount("admin", "admin", null, "cred1");
			DeviceAccess sshAccess = new DeviceAccess(device, "ssh");
			sshAccess.setGlobalCredentialSet(cred);
			device.getAccesses().add(sshAccess);

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(null, device, null, new FakeTaskContext(), null);
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
				(accessDef, credentialSet) -> new FakeClient(connectCount, null));

			Assertions.assertEquals(0, connectCount.get(), "No connection attempt should happen before first use");
			Assertions.assertNull(resolution.getCurrentClient());

			resolution.ensureResolved(true);
			Assertions.assertEquals(1, connectCount.get(), "Exactly one connection attempt should happen on first use");
		}

		@Test
		@DisplayName("Auto-try cycles through the supplied credentials in order until one succeeds")
		void autoTryCyclesScopedCredentials() throws IOException {
			Device device = fakeDevice();
			DeviceCredentialSet credA = new DeviceSshAccount("admin", "wrong", null, "credA");
			DeviceCredentialSet credB = new DeviceSshAccount("admin", "right", null, "credB");

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(null, device, null, new FakeTaskContext(),
				new LinkedHashSet<>(List.of(credA, credB)));
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")), (accessDef, credentialSet) -> {
				if (credentialSet == credA) {
					return new FakeClient(connectCount, new InvalidCredentialsException("bad password"));
				}
				return new FakeClient(connectCount, null);
			});

			Client client = resolution.ensureResolved(true);
			Assertions.assertNotNull(client);
			Assertions.assertEquals(credB, resolution.getCurrentCredentialSet(), "Should have connected using credB");
			Assertions.assertEquals(2, connectCount.get(), "Both credentials should have been attempted (credA then credB)");
		}

		@Test
		@DisplayName("A non-auth IOException aborts remaining candidates of that access, but a different access is still tried")
		void connectFailureAbortsOnlyThatAccess() throws IOException {
			Device device = fakeDevice();
			DeviceCredentialSet sshCred = new DeviceSshAccount("admin", "admin", null, "sshCred");
			DeviceCredentialSet telnetCred = new DeviceTelnetAccount("admin", "admin", null, "telnetCred");
			DeviceAccess sshAccess = new DeviceAccess(device, "ssh");
			sshAccess.setGlobalCredentialSet(sshCred);
			device.getAccesses().add(sshAccess);
			DeviceAccess telnetAccess = new DeviceAccess(device, "telnet");
			telnetAccess.setGlobalCredentialSet(telnetCred);
			device.getAccesses().add(telnetAccess);

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(null, device, null, new FakeTaskContext(), null);
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh"), telnetAccess("telnet")),
				(accessDef, credentialSet) -> {
					if ("ssh".equals(accessDef.getName())) {
						return new FakeClient(connectCount, new IOException("connection refused"));
					}
					return new FakeClient(connectCount, null);
				});

			Client client = resolution.ensureResolved(true);
			Assertions.assertNotNull(client);
			Assertions.assertEquals("telnet", resolution.getCurrentAccessDef().getName(),
				"Should have fallen back to the Telnet access after SSH was unreachable");
			Assertions.assertEquals(telnetCred, resolution.getCurrentCredentialSet());
		}

		@Test
		@DisplayName("Manual mode (autoTryCredentials=false) surfaces an AccessAuthenticationException instead of auto-cycling")
		void manualModeSurfacesAuthFailure() throws IOException {
			Device device = fakeDevice();
			DeviceCredentialSet credA = new DeviceSshAccount("admin", "wrong", null, "credA");
			DeviceCredentialSet credB = new DeviceSshAccount("admin", "right", null, "credB");

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(null, device, null, new FakeTaskContext(),
				new LinkedHashSet<>(List.of(credA, credB)));
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")), (accessDef, credentialSet) -> {
				if (credentialSet == credA) {
					return new FakeClient(connectCount, new InvalidCredentialsException("bad password"));
				}
				return new FakeClient(connectCount, null);
			});

			Assertions.assertThrows(AccessAuthenticationException.class, () -> resolution.ensureResolved(false),
				"The first (failing) candidate should surface as a catchable authentication error, not auto-cycle");
			Assertions.assertEquals(1, connectCount.get(), "Only the first candidate should have been tried so far");
			Assertions.assertNull(resolution.getCurrentClient());

			boolean advanced = resolution.tryNextCredentials();
			Assertions.assertTrue(advanced, "tryNextCredentials() should move to credB and succeed");
			Assertions.assertEquals(credB, resolution.getCurrentCredentialSet());
			Assertions.assertEquals(2, connectCount.get());
		}

		@Test
		@DisplayName("Exhausting all candidates without any credential configured throws immediately")
		void noCredentialsConfigured() {
			Device device = fakeDevice();
			AccessManager manager = new AccessManager(null, device, null, new FakeTaskContext(), null);
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
				(accessDef, credentialSet) -> new FakeClient(new AtomicInteger(), null));

			Assertions.assertThrows(InvalidCredentialsException.class, () -> resolution.ensureResolved(true));
		}

		@Test
		@DisplayName("A per-access global credential pin is used exclusively")
		void globalCredentialPinResolution() throws IOException {
			Device device = fakeDevice();
			DeviceCredentialSet pinnedCred = new DeviceSshAccount("admin", "pinned", null, "pinnedCred");

			DeviceAccess access = new DeviceAccess(device, "ssh");
			access.setGlobalCredentialSet(pinnedCred);
			device.getAccesses().add(access);

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(null, device, null, new FakeTaskContext(), null);
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
				(accessDef, credentialSet) -> new FakeClient(connectCount, null));

			Client client = resolution.ensureResolved(true);
			Assertions.assertNotNull(client);
			Assertions.assertEquals(pinnedCred, resolution.getCurrentCredentialSet(),
				"The pinned global credential set should be used");
			Assertions.assertEquals(1, connectCount.get(), "Only the pinned candidate should have been tried");
		}

		@Test
		@DisplayName("A per-access owned specific credential pin is used exclusively")
		void specificCredentialPinResolution() throws IOException {
			Device device = fakeDevice();
			DeviceCredentialSet specificCred = new DeviceSshAccount("admin", "specific", null, "DEVICESPECIFIC-test");

			DeviceAccess access = new DeviceAccess(device, "ssh");
			access.setSpecificCredentialSet(specificCred);
			device.getAccesses().add(access);

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(null, device, null, new FakeTaskContext(), null);
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
				(accessDef, credentialSet) -> new FakeClient(connectCount, null));

			Client client = resolution.ensureResolved(true);
			Assertions.assertNotNull(client);
			Assertions.assertEquals(specificCred, resolution.getCurrentCredentialSet(),
				"The pinned specific credential set should be used");
			Assertions.assertEquals(1, connectCount.get(), "Only the pinned candidate should have been tried");
		}

		@Test
		@DisplayName("A pinned credential that fails auth does NOT fall back to auto-try")
		void pinnedCredentialFailureDoesNotFallBackToAutoTry() {
			Device device = fakeDevice();
			DeviceCredentialSet pinnedCred = new DeviceSshAccount("admin", "wrong", null, "pinnedCred");

			DeviceAccess access = new DeviceAccess(device, "ssh");
			access.setGlobalCredentialSet(pinnedCred);
			device.getAccesses().add(access);

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(null, device, null, new FakeTaskContext(), null);
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")), (accessDef, credentialSet) -> {
				if (credentialSet == pinnedCred) {
					return new FakeClient(connectCount, new InvalidCredentialsException("bad password"));
				}
				return new FakeClient(connectCount, null);
			});

			Assertions.assertThrows(InvalidCredentialsException.class, () -> resolution.ensureResolved(true),
				"A failed pin must not fall back to auto-try");
			Assertions.assertEquals(1, connectCount.get(), "Only the pinned candidate should ever have been tried");
		}

		@Test
		@DisplayName("Auto (no pin configured) resolves via the domain-scoped credential pool, cycling in order")
		void autoModeUsesDomainScopedPool() throws IOException {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				Domain domain = new Domain("Auto pool test domain", "", null, null);
				session.persist(domain);
				Device device = new Device("CiscoIOS12", null, domain, "test");
				device.getAccesses().add(new DeviceAccess(device, "ssh"));
				session.persist(device);
				DeviceCredentialSet credA = new DeviceSshAccount("admin", "wrong", null, "autoPoolCredA");
				DeviceCredentialSet credB = new DeviceSshAccount("admin", "right", null, "autoPoolCredB");
				session.persist(credA);
				session.persist(credB);
				session.getTransaction().commit();

				AtomicInteger connectCount = new AtomicInteger(0);
				AccessManager manager = new AccessManager(session, device, null, new FakeTaskContext(), null);
				Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")), (accessDef, credentialSet) -> {
					if (credentialSet.getName().equals("autoPoolCredA")) {
						return new FakeClient(connectCount, new InvalidCredentialsException("bad password"));
					}
					return new FakeClient(connectCount, null);
				});

				Client client = resolution.ensureResolved(true);
				Assertions.assertNotNull(client);
				Assertions.assertEquals("autoPoolCredB", resolution.getCurrentCredentialSet().getName(),
					"Should have connected using the second domain-pool credential after the first failed");
				Assertions.assertEquals(2, connectCount.get(), "Both domain-pool credentials should have been attempted");
			}
			finally {
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session.createMutationQuery("delete from Device").executeUpdate();
					session.createMutationQuery("delete from DeviceCredentialSet").executeUpdate();
					session.createMutationQuery("delete from Domain").executeUpdate();
					session.getTransaction().commit();
				}
			}
		}

		@Test
		@DisplayName("A successful auto-pool connection is pinned as the access's global credential set")
		void autoModeSuccessPinsCredentialSet() throws IOException {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				Domain domain = new Domain("Auto pin test domain", "", null, null);
				session.persist(domain);
				Device device = new Device("CiscoIOS12", null, domain, "test");
				device.getAccesses().add(new DeviceAccess(device, "ssh"));
				session.persist(device);
				DeviceCredentialSet credA = new DeviceSshAccount("admin", "wrong", null, "autoPinCredA");
				DeviceCredentialSet credB = new DeviceSshAccount("admin", "right", null, "autoPinCredB");
				session.persist(credA);
				session.persist(credB);
				session.getTransaction().commit();

				AtomicInteger connectCount = new AtomicInteger(0);
				AccessManager manager = new AccessManager(session, device, null, new FakeTaskContext(), null);
				Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")), (accessDef, credentialSet) -> {
					if (credentialSet.getName().equals("autoPinCredA")) {
						return new FakeClient(connectCount, new InvalidCredentialsException("bad password"));
					}
					return new FakeClient(connectCount, null);
				});

				session.beginTransaction();
				Client client = resolution.ensureResolved(true);
				Assertions.assertNotNull(client);
				resolution.confirmCredentialWorks();
				session.getTransaction().commit();

				Assertions.assertNotNull(device.getDeviceAccess("ssh"),
					"A DeviceAccess row should have been created to hold the pin");
				Assertions.assertEquals("autoPinCredB", device.getDeviceAccess("ssh").getGlobalCredentialSet().getName(),
					"The successful credential set should be pinned as the access's global credential set");

				// Confirm the pin was actually persisted, not just mutated in memory.
				try (Session verifySession = Database.getSession()) {
					Device reloaded = verifySession.get(Device.class, device.getId());
					Assertions.assertEquals("autoPinCredB",
						reloaded.getDeviceAccess("ssh").getGlobalCredentialSet().getName(),
						"The pin should survive a reload from the database");
				}
			}
			finally {
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session.createMutationQuery("delete from Device").executeUpdate();
					session.createMutationQuery("delete from DeviceCredentialSet").executeUpdate();
					session.createMutationQuery("delete from Domain").executeUpdate();
					session.getTransaction().commit();
				}
			}
		}

		@Test
		@DisplayName("confirmCredentialWorks() does not pin an already-pinned (global) credential")
		void confirmCredentialWorksDoesNotRePinAlreadyPinnedAccess() throws IOException {
			Device device = fakeDevice();
			DeviceCredentialSet pinnedCred = new DeviceSshAccount("admin", "pinned", null, "pinnedCred");
			DeviceAccess access = new DeviceAccess(device, "ssh");
			access.setGlobalCredentialSet(pinnedCred);
			device.getAccesses().add(access);

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(null, device, null, new FakeTaskContext(), null);
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
				(accessDef, credentialSet) -> new FakeClient(connectCount, null));

			resolution.ensureResolved(true);
			resolution.confirmCredentialWorks();

			Assertions.assertSame(pinnedCred, device.getDeviceAccess("ssh").getGlobalCredentialSet(),
				"An already-pinned access must not be re-pinned/altered by confirmCredentialWorks()");
		}

		@Test
		@DisplayName("An access with no DeviceAccess row is never used, even with compatible domain-pool credentials")
		void accessWithNoRowIsNeverUsed() throws IOException {
			try (Session session = Database.getSession()) {
				session.beginTransaction();
				Domain domain = new Domain("No-row test domain", "", null, null);
				session.persist(domain);
				Device device = new Device("CiscoIOS12", null, domain, "test");
				// Deliberately no DeviceAccess row for "ssh" at all.
				session.persist(device);
				DeviceCredentialSet cred = new DeviceSshAccount("admin", "admin", null, "noRowCred");
				session.persist(cred);
				session.getTransaction().commit();

				AtomicInteger connectCount = new AtomicInteger(0);
				AccessManager manager = new AccessManager(session, device, null, new FakeTaskContext(), null);
				Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
					(accessDef, credentialSet) -> new FakeClient(connectCount, null));

				Assertions.assertThrows(InvalidCredentialsException.class, () -> resolution.ensureResolved(true),
					"No DeviceAccess row means the access is never used, regardless of eligible domain-pool credentials");
				Assertions.assertEquals(0, connectCount.get(), "No connection attempt should ever happen");
			}
			finally {
				try (Session session = Database.getSession()) {
					session.beginTransaction();
					session.createMutationQuery("delete from Device").executeUpdate();
					session.createMutationQuery("delete from DeviceCredentialSet").executeUpdate();
					session.createMutationQuery("delete from Domain").executeUpdate();
					session.getTransaction().commit();
				}
			}
		}

		@Test
		@DisplayName("A one-time credential set is tried regardless of whether a DeviceAccess row exists")
		void oneTimeCredentialSetBypassesEnabledCheck() throws IOException {
			Device device = fakeDevice();
			// Deliberately no DeviceAccess row for "ssh" at all.
			DeviceCredentialSet oneTimeCred = new DeviceSshAccount("admin", "admin", null, "oneTimeCred");

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(null, device, null, new FakeTaskContext(),
				new LinkedHashSet<>(List.of(oneTimeCred)));
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
				(accessDef, credentialSet) -> new FakeClient(connectCount, null));

			Client client = resolution.ensureResolved(true);
			Assertions.assertNotNull(client, "A one-time credential set should be tried even with no DeviceAccess row");
			Assertions.assertEquals(oneTimeCred, resolution.getCurrentCredentialSet());
		}

		@Test
		@DisplayName("A successful connection removes sibling accesses in the same group")
		void confirmCredentialWorksRemovesSiblingAccessesInSameGroup() throws IOException {
			Device device = fakeDevice();
			DeviceAccess sshRow = new DeviceAccess(device, "ssh");
			sshRow.setGlobalCredentialSet(new DeviceSshAccount("admin", "admin", null, "sshCred"));
			device.getAccesses().add(sshRow);
			DeviceAccess telnetRow = new DeviceAccess(device, "telnet");
			telnetRow.setGlobalCredentialSet(new DeviceTelnetAccount("admin", "admin", null, "telnetCred"));
			device.getAccesses().add(telnetRow);

			AtomicInteger connectCount = new AtomicInteger(0);
			AccessManager manager = new AccessManager(null, device, null, new FakeTaskContext(), null);
			Resolution resolution = manager.newResolution(List.of(sshAccess("ssh")),
				(accessDef, credentialSet) -> new FakeClient(connectCount, null));

			resolution.ensureResolved(true);
			Assertions.assertNotNull(device.getDeviceAccess("telnet"), "Sanity check: telnet access exists before confirming");

			resolution.confirmCredentialWorks();

			Assertions.assertNull(device.getDeviceAccess("telnet"),
				"The sibling telnet access should be removed once ssh is confirmed to work");
			Assertions.assertNotNull(device.getDeviceAccess("ssh"), "The access that succeeded should remain");
		}

		@Test
		@DisplayName("resolveAddress/resolvePort use the device's per-access override when set, for any access name")
		void perAccessOverrideResolution() throws IOException {
			Device device = fakeDevice();
			NetworkAddress defaultAddress = NetworkAddress.getNetworkAddress(InetAddress.getByName("10.0.0.1"));

			// A brand new, non-legacy access ("alternateSsh") with no override at all:
			// should fall back to the default address and the driver-declared default port.
			AccessDefinition alternateSsh = new AccessDefinition("alternateSsh", DriverProtocol.SSH, null,
				null, null, null, 2222);
			AccessManager manager = new AccessManager(null, device, defaultAddress, new FakeTaskContext(), null);
			Assertions.assertEquals(defaultAddress, manager.resolveAddress(alternateSsh));
			Assertions.assertEquals(2222, manager.resolvePort(alternateSsh));

			// Now add a per-access override for that same access, with both an address and a port.
			DeviceAccess override = new DeviceAccess(device, "alternateSsh");
			override.setAddress("192.168.1.50");
			override.setPort(2223);
			device.getAccesses().add(override);
			NetworkAddress overrideAddress = NetworkAddress.getNetworkAddress(InetAddress.getByName("192.168.1.50"));
			Assertions.assertEquals(overrideAddress, manager.resolveAddress(alternateSsh));
			Assertions.assertEquals(2223, manager.resolvePort(alternateSsh));

			// The plain "ssh" access is unaffected by the "alternateSsh" override.
			AccessDefinition ssh = sshAccess("ssh");
			Assertions.assertEquals(defaultAddress, manager.resolveAddress(ssh));
			Assertions.assertEquals(22, manager.resolvePort(ssh));
		}

	}

	@Nested
	@DisplayName("Device access replace test")
	class DeviceAccessReplaceTest {

		private Device fakeDevice() {
			Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
			return new Device("CiscoIOS12", null, domain, "test");
		}

		@Test
		@DisplayName("replaceAccesses copies the SSH/HTTPS trust fields onto the managed access")
		void replaceAccessesCopiesTrustFields() {
			Device device = fakeDevice();
			DeviceAccess sshIncoming = new DeviceAccess(device, "ssh");
			sshIncoming.setSshHostKeyVerification(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN);
			sshIncoming.setSshTrustedHostKeys("ssh-ed25519 AAAAKEY");
			DeviceAccess httpsIncoming = new DeviceAccess(device, "https");
			httpsIncoming.setHttpsCaTrustMode(HttpsCaTrustMode.CUSTOM_CA);
			httpsIncoming.setHttpsCustomCaCertificate("-----BEGIN CERTIFICATE-----FAKE-----END CERTIFICATE-----");

			device.replaceAccesses(List.of(sshIncoming, httpsIncoming));

			DeviceAccess sshAccess = device.getDeviceAccess("ssh");
			Assertions.assertEquals(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN, sshAccess.getSshHostKeyVerification());
			Assertions.assertEquals("ssh-ed25519 AAAAKEY", sshAccess.getSshTrustedHostKeys());
			Assertions.assertNotNull(sshAccess.getSshTrustedHostKeysSince(),
				"Setting a trusted key for the first time should stamp 'since'");

			DeviceAccess httpsAccess = device.getDeviceAccess("https");
			Assertions.assertEquals(HttpsCaTrustMode.CUSTOM_CA, httpsAccess.getHttpsCaTrustMode());
			Assertions.assertEquals("-----BEGIN CERTIFICATE-----FAKE-----END CERTIFICATE-----",
				httpsAccess.getHttpsCustomCaCertificate());
		}

		@Test
		@DisplayName("replaceAccesses actually clears the trusted host keys when resubmitted empty (regression test)")
		void replaceAccessesClearsTrustedHostKeys() {
			Device device = fakeDevice();
			DeviceAccess initial = new DeviceAccess(device, "ssh");
			initial.setSshHostKeyVerification(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN);
			initial.setSshTrustedHostKeys("ssh-ed25519 AAAAKEY");
			device.replaceAccesses(List.of(initial));
			Assertions.assertEquals("ssh-ed25519 AAAAKEY", device.getDeviceAccess("ssh").getSshTrustedHostKeys());
			Assertions.assertNotNull(device.getDeviceAccess("ssh").getSshTrustedHostKeysSince());

			// Re-submit with the field cleared, as the "clear" button in the device edit form does.
			DeviceAccess cleared = new DeviceAccess(device, "ssh");
			cleared.setSshHostKeyVerification(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN);
			cleared.setSshTrustedHostKeys(null);
			device.replaceAccesses(List.of(cleared));

			DeviceAccess sshAccess = device.getDeviceAccess("ssh");
			Assertions.assertNull(sshAccess.getSshTrustedHostKeys(), "The trusted host keys should actually be cleared");
			Assertions.assertNull(sshAccess.getSshTrustedHostKeysSince(),
				"The 'since' timestamp should be cleared along with the keys");
		}

		@Test
		@DisplayName("replaceAccesses does not bump sshTrustedHostKeysSince when resubmitted unchanged")
		void replaceAccessesKeepsSinceWhenUnchanged() throws InterruptedException {
			Device device = fakeDevice();
			DeviceAccess initial = new DeviceAccess(device, "ssh");
			initial.setSshTrustedHostKeys("ssh-ed25519 AAAAKEY");
			device.replaceAccesses(List.of(initial));
			Date firstSince = device.getDeviceAccess("ssh").getSshTrustedHostKeysSince();
			Assertions.assertNotNull(firstSince);

			Thread.sleep(5);

			DeviceAccess resubmitted = new DeviceAccess(device, "ssh");
			resubmitted.setSshTrustedHostKeys("ssh-ed25519 AAAAKEY");
			device.replaceAccesses(List.of(resubmitted));

			Assertions.assertEquals(firstSince, device.getDeviceAccess("ssh").getSshTrustedHostKeysSince(),
				"Resubmitting the same value should not touch the 'since' timestamp");
		}
	}
}
