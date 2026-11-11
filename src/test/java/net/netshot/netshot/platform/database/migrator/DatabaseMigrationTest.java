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
package net.netshot.netshot.platform.database.migrator;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import liquibase.UpdateSummaryOutputEnum;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCountCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.command.core.helpers.ShowSummaryArgument;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DatabaseTest.Boot;
import net.netshot.netshot.testing.TestDatabase;

/**
 * Tests for the Liquibase {@code CustomSqlChange} data migrators under
 * {@link net.netshot.netshot.platform.database.migrator} (currently just
 * {@link NetworkAddressMigrator} - add further migrators here as
 * additional {@code @Nested} classes). Testcontainers-backed tests
 * otherwise only ever see a freshly created schema, so these
 * data-migration paths (as opposed to fresh-install schema creation) are
 * never exercised elsewhere: each test here stops the schema migration
 * just before the migrator's own changeset, seeds rows directly via JDBC
 * in the legacy pre-migration shape, resumes the migration to completion,
 * and checks the result.
 */
@DatabaseTest(boot = Boot.MANUAL)
class DatabaseMigrationTest {

	private TestDatabase database;

	@BeforeEach
	void setDatabase(TestDatabase testDatabase) {
		this.database = testDatabase;
	}

	/**
	 * Builds the database configuration of a schema which starts empty.
	 *
	 * @param schemaName the schema
	 * @return the configuration
	 */
	private Properties getFreshDatabaseConfig(String schemaName) {
		return this.database.freshConfig(schemaName);
	}

	private static void assertInetColumn(Statement statement, String query, String expectedAddress)
			throws SQLException {
		try (var rows = statement.executeQuery(query)) {
			Assertions.assertTrue(rows.next(), "No row returned for: %s".formatted(query));
			Assertions.assertEquals(expectedAddress, rows.getString(1),
				"Unexpected migrated inet value for: %s".formatted(query));
		}
	}

	/**
	 * Updates the database schema up to (and including) the given changeset
	 * only, leaving any later changesets unapplied, so a test can seed rows
	 * in that intermediate schema shape, then resume the migration (via
	 * {@link Database#update()}) and check the result. The changeset count
	 * to stop at is resolved dynamically by parsing the changelog and
	 * locating the given id/author, rather than requiring a permanent tag
	 * changeset in the real changelog just for test purposes.
	 *
	 * @param changeSetId the id of the changeset to stop at (inclusive)
	 * @param changeSetAuthor the author of the changeset to stop at
	 * @throws Exception
	 */
	private static void updateToChangeSet(String changeSetId, String changeSetAuthor) throws Exception {
		System.setProperty("liquibase.analytics.enabled", "false");
		try (Connection connection = Database.openUnpooledConnection()) {
			liquibase.database.Database database = DatabaseFactory.getInstance()
				.findCorrectDatabaseImplementation(new JdbcConnection(connection));

			ResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
			DatabaseChangeLog changeLog = ChangeLogParserFactory.getInstance()
				.getParser("migration/netshot0.xml", resourceAccessor)
				.parse("migration/netshot0.xml", new ChangeLogParameters(database), resourceAccessor);

			int count = 0;
			boolean found = false;
			for (ChangeSet changeSet : changeLog.getChangeSets()) {
				count += 1;
				if (changeSet.getId().equals(changeSetId) && changeSet.getAuthor().equals(changeSetAuthor)) {
					found = true;
					break;
				}
			}
			if (!found) {
				throw new IllegalArgumentException(
					"No such changeset '%s' by '%s' in the changelog".formatted(changeSetId, changeSetAuthor));
			}

			updateCount(database, count);
		}
	}

	/**
	 * Applies the given number of changesets among those not applied yet (for a test which stops
	 * the migration at an intermediate changeset again after {@link #updateToChangeSet}).
	 *
	 * @param count the number of changesets to apply
	 * @throws Exception
	 */
	private static void updateNext(int count) throws Exception {
		try (Connection connection = Database.openUnpooledConnection()) {
			updateCount(DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)), count);
		}
	}

	private static void updateCount(liquibase.database.Database database, int count) throws Exception {
		new CommandScope(UpdateCountCommandStep.COMMAND_NAME)
			.addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
			.addArgumentValue(UpdateCountCommandStep.CHANGELOG_FILE_ARG, "migration/netshot0.xml")
			.addArgumentValue(UpdateCountCommandStep.COUNT_ARG, count)
			.addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY_OUTPUT, UpdateSummaryOutputEnum.LOG)
			.execute();
	}

	@Nested
	@DisplayName("0.25.0_24 - NetworkAddressMigrator (legacy int/bigint IP columns to native inet)")
	class NetworkAddressMigratorTest {

		private Properties getNetshotConfig() {
			// Fresh (dropped and recreated) schema: this test drives the schema
			// migration through an intermediate stage itself, so it cannot
			// tolerate a schema left mid-migration (or fully migrated) by an
			// earlier run against the same live container.
			Properties config = getFreshDatabaseConfig("networkaddressmigratortest");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "INFO");
			return config;
		}

		@Test
		@DisplayName("Legacy int/bigint IP columns are converted to inet on upgrade")
		void migratesLegacyAddressesToInet() throws Exception {
			TestConfig.init(this.getNetshotConfig());

			// Bring the schema up to right before the inet conversion (the last
			// changeset before the block that adds the inet columns).
			updateToChangeSet("0.25.0_21", "netshot");

			// Compute the legacy int/bigint representations the same way the
			// pre-migration application code used to.
			int domainIpv4Int = Network4Address.inetAddressToInt(
				(Inet4Address) InetAddress.getByName("10.20.30.40"));
			Network6Address domainIpv6 = new Network6Address("2001:db8:1234::5678", 64);
			int interfaceIpv4Int = Network4Address.inetAddressToInt(
				(Inet4Address) InetAddress.getByName("192.168.100.1"));
			Network6Address interfaceIpv6 = new Network6Address("fe80::dead:beef", 64);
			int subnetIpv4Int = Network4Address.inetAddressToInt(
				(Inet4Address) InetAddress.getByName("172.16.0.0"));

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				statement.execute(
					"insert into domain (id, name, version, ipv4_address, ipv4_pfxlen, ipv4_usage, "
						+ "ipv6_address1, ipv6_address2, ipv6_usage) values "
						+ "(1, 'MigrationTestDomain', 0, %d, 24, 0, %d, %d, 0)".formatted(
							domainIpv4Int, domainIpv6.getAddress1(), domainIpv6.getAddress2()));

				statement.execute(
					"insert into device (id, auto_try_credentials, version, ssh_port, telnet_port) "
						+ "values (1, false, 0, 22, 23)");
				statement.execute(
					"insert into network_interface (id, enabled, level3, device) values (1, true, true, 1)");
				statement.execute(
					"insert into network_interface_ip4addresses (network_interface, address, prefix_length, address_usage) "
						+ "values (1, %d, 24, 0)".formatted(interfaceIpv4Int));
				statement.execute(
					"insert into network_interface_ip6addresses (network_interface, address1, address2, prefix_length, address_usage) "
						+ "values (1, %d, %d, 64, 0)".formatted(
							interfaceIpv6.getAddress1(), interfaceIpv6.getAddress2()));

				statement.execute(
					"insert into task (id, version, dtype, debug_enabled, priority, "
						+ "run_snapshot, run_diagnostics, check_compliance, dont_run_diagnostics, "
						+ "dont_check_compliance, child_order, schedule_mode, stop_on_failure, cancel_requested) "
						+ "values (1, 0, 'ScanSubnetsTask', false, 5, "
						+ "false, false, false, false, false, 0, 0, false, false)");
				statement.execute(
					"insert into scan_subnets_task_subnets (scan_subnets_task, ipv4address, ipv4mask, ipv4usage) "
						+ "values (1, %d, 16, 0)".formatted(subnetIpv4Int));
			}

			// Resume the migration to completion: this is where NetworkAddressMigrator runs.
			Database.update();

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				assertInetColumn(statement,
					"select ipv4_address from domain where id = 1", "10.20.30.40");
				assertInetColumn(statement,
					"select ipv6_address from domain where id = 1", "2001:db8:1234::5678");
				assertInetColumn(statement,
					"select address from network_interface_ip4addresses where network_interface = 1",
					"192.168.100.1");
				assertInetColumn(statement,
					"select address from network_interface_ip6addresses where network_interface = 1",
					"fe80::dead:beef");
				assertInetColumn(statement,
					"select ipv4address from scan_subnets_task_subnets where scan_subnets_task = 1",
					"172.16.0.0");
			}
		}
	}

	/**
	 * Asserts a single {@code device_access} row's pin columns. Pass {@code null}
	 * for {@code expectedGlobalCredId}/{@code expectedSpecificCredId} to assert
	 * that column is NULL.
	 * @param statement the statement to query with
	 * @param deviceId the ID of the device
	 * @param accessName the name of the access
	 * @param expectedGlobalCredId the expected ID of the global credential set
	 * @param expectedSpecificCredId the expected ID of the specific credential set
	 */
	private static void assertDeviceAccess(Statement statement, long deviceId, String accessName,
			Long expectedGlobalCredId, Long expectedSpecificCredId) throws SQLException {
		try (var rows = statement.executeQuery(
				"select global_credential_set, specific_credential_set from device_access "
					+ "where device = %d and access_name = '%s'".formatted(deviceId, accessName))) {
			Assertions.assertTrue(rows.next(),
				"No device_access row for device %d access '%s'".formatted(deviceId, accessName));
			Assertions.assertEquals(expectedGlobalCredId, rows.getObject("global_credential_set", Long.class),
				"Unexpected 'global_credential_set' for device %d access '%s'".formatted(deviceId, accessName));
			Assertions.assertEquals(expectedSpecificCredId, rows.getObject("specific_credential_set", Long.class),
				"Unexpected 'specific_credential_set' for device %d access '%s'".formatted(deviceId, accessName));
		}
	}

	/**
	 * Asserts no {@code device_access} row exists for the given (device, accessName).
	 * @param statement the statement to query with
	 * @param deviceId the ID of the device
	 * @param accessName the name of the access
	 */
	private static void assertNoDeviceAccess(Statement statement, long deviceId, String accessName) throws SQLException {
		try (var rows = statement.executeQuery(
				"select 1 from device_access where device = %d and access_name = '%s'".formatted(deviceId, accessName))) {
			Assertions.assertFalse(rows.next(),
				"Unexpected device_access row for device %d access '%s'".formatted(deviceId, accessName));
		}
	}

	/**
	 * Asserts a single {@code device_access} row's port/address columns (pass {@code null} to assert NULL).
	 * @param statement the statement to query with
	 * @param deviceId the ID of the device
	 * @param accessName the name of the access
	 * @param expectedPort the expected port
	 * @param expectedAddress the expected address
	 */
	private static void assertDeviceAccessPortAddress(Statement statement, long deviceId, String accessName,
			Integer expectedPort, String expectedAddress) throws SQLException {
		try (var rows = statement.executeQuery(
				"select port, address from device_access where device = %d and access_name = '%s'"
					.formatted(deviceId, accessName))) {
			Assertions.assertTrue(rows.next(),
				"No device_access row for device %d access '%s'".formatted(deviceId, accessName));
			Assertions.assertEquals(expectedPort, rows.getObject("port", Integer.class),
				"Unexpected 'port' for device %d access '%s'".formatted(deviceId, accessName));
			Assertions.assertEquals(expectedAddress, rows.getString("address"),
				"Unexpected 'address' for device %d access '%s'".formatted(deviceId, accessName));
		}
	}

	@Nested
	@DisplayName("0.25.0_30 - migrate legacy ssh_port/telnet_port/connect_address into per-access device_access rows")
	class PortAddressMigrationTest {

		private Properties getNetshotConfig() {
			Properties config = getFreshDatabaseConfig("portaddressmigrationtest");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "INFO");
			return config;
		}

		@Test
		@DisplayName("Port override and connect address merge into a single device_access row per protocol, with no spurious port override when only the address differs")
		void migratesPortsAndAddressToSingleRowPerProtocol() throws Exception {
			TestConfig.init(this.getNetshotConfig());

			// Stop right before 0.25.0_30: device_access exists (0.25.0_29), but the
			// legacy ssh_port/telnet_port/connect_address columns are still on device.
			updateToChangeSet("0.25.0_29", "netshot");

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				// 301: both ports already at their protocol default, no address -> no row at all.
				statement.execute("insert into device (id, name, auto_try_credentials, version, ssh_port, telnet_port) "
					+ "values (301, 'dev301', false, 0, 22, 23)");
				// 302: custom ssh port, default telnet port, no address -> ssh row (port=2222), no telnet row.
				statement.execute("insert into device (id, name, auto_try_credentials, version, ssh_port, telnet_port) "
					+ "values (302, 'dev302', false, 0, 2222, 23)");
				// 303: both ports at their protocol default (22/23, not the 0 sentinel), but a connect address set ->
				// one row per protocol, address set, port left NULL (22/23 already is the default, not an override).
				statement.execute("insert into device (id, name, auto_try_credentials, version, ssh_port, telnet_port, connect_address) "
					+ "values (303, 'dev303', false, 0, 22, 23, '10.0.0.1')");
				// 304: custom ports on both protocols AND a connect address -> one row per protocol with both set.
				statement.execute("insert into device (id, name, auto_try_credentials, version, ssh_port, telnet_port, connect_address) "
					+ "values (304, 'dev304', false, 0, 2222, 2323, '10.0.0.2')");
			}

			// Resume the migration to completion: this is where 0.25.0_30 runs.
			Database.update();

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				assertNoDeviceAccess(statement, 301, "ssh");
				assertNoDeviceAccess(statement, 301, "telnet");

				assertDeviceAccessPortAddress(statement, 302, "ssh", 2222, null);
				assertNoDeviceAccess(statement, 302, "telnet");

				assertDeviceAccessPortAddress(statement, 303, "ssh", null, "10.0.0.1");
				assertDeviceAccessPortAddress(statement, 303, "telnet", null, "10.0.0.1");

				assertDeviceAccessPortAddress(statement, 304, "ssh", 2222, "10.0.0.2");
				assertDeviceAccessPortAddress(statement, 304, "telnet", 2323, "10.0.0.2");
			}
		}
	}

	@Nested
	@DisplayName("0.25.0_32 - migrate device-wide SNMP/SSH/Telnet credentials into per-access device_access rows")
	class DeviceAccessMigrationTest {

		private Properties getNetshotConfig() {
			Properties config = getFreshDatabaseConfig("deviceaccessmigrationtest");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "INFO");
			return config;
		}

		@Test
		@DisplayName("Specific/pooled/auto-try device-wide credentials resolve to the right per-access pin")
		void migratesDeviceWideCredentialsToPerAccessPins() throws Exception {
			TestConfig.init(this.getNetshotConfig());

			// Stop right before the (modified) 0.25.0_32 changeset under test: the
			// device_access table exists (0.25.0_29) and ssh_port/telnet_port/
			// connect_address are already migrated away and dropped (0.25.0_30/31),
			// but the legacy device-wide specific_credential_set/auto_try_credentials
			// columns and the device_credential_sets pool table are still present.
			updateToChangeSet("0.25.0_31", "netshot");

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {

				// A credential set for each scenario below.
				statement.execute("insert into device_credential_set (id, dtype, name, version, device_specific, community) "
					+ "values (201, 'DeviceSnmpv2cCommunity', 'cred-201', 0, true, 'specific-v2c')");
				statement.execute("insert into device_credential_set (id, dtype, name, version, device_specific, community) "
					+ "values (202, 'DeviceSnmpv3Community', 'cred-202', 0, false, 'pool-v3')");
				statement.execute("insert into device_credential_set (id, dtype, name, version, device_specific, community) "
					+ "values (203, 'DeviceSnmpv1Community', 'cred-203', 0, false, 'pool-v1-a')");
				statement.execute("insert into device_credential_set (id, dtype, name, version, device_specific, community) "
					+ "values (204, 'DeviceSnmpv2cCommunity', 'cred-204', 0, false, 'pool-v2c-b')");
				statement.execute("insert into device_credential_set (id, dtype, name, version, device_specific, username, password) "
					+ "values (205, 'DeviceSshAccount', 'cred-205', 0, true, 'admin', 'pwd')");
				statement.execute("insert into device_credential_set (id, dtype, name, version, device_specific, username, private_key) "
					+ "values (206, 'DeviceSshKeyAccount', 'cred-206', 0, false, 'admin', 'key-material')");
				statement.execute("insert into device_credential_set (id, dtype, name, version, device_specific, username, password) "
					+ "values (208, 'DeviceTelnetAccount', 'cred-208', 0, true, 'admin', 'pwd')");
				statement.execute("insert into device_credential_set (id, dtype, name, version, device_specific, username, password) "
					+ "values (209, 'DeviceTelnetAccount', 'cred-209', 0, false, 'admin', 'pwd')");
				statement.execute("insert into device_credential_set (id, dtype, name, version, device_specific, username, password) "
					+ "values (210, 'DeviceSshAccount', 'cred-210', 0, false, 'admin', 'pwd')");

				// Device 101 (GenericSNMP): specific SNMPv2c credential -> "specific" pin on snmpv2c.
				statement.execute("insert into device (id, name, driver, auto_try_credentials, version, specific_credential_set) "
					+ "values (101, 'dev101', 'GenericSNMP', false, 0, 201)");

				// Device 102 (GenericSNMP): no specific, exactly one pooled SNMP credential (v3) -> "global" pin on snmpv3.
				statement.execute("insert into device (id, name, driver, auto_try_credentials, version) "
					+ "values (102, 'dev102', 'GenericSNMP', true, 0)");
				statement.execute("insert into device_credential_sets (device, credential_sets) values (102, 202)");

				// Device 103 (GenericSNMP): no specific, TWO pooled SNMP credentials (203 then 204) ->
				// the first one (lowest id, 203/snmpv1) is pinned as "global" on snmpv1.
				statement.execute("insert into device (id, name, driver, auto_try_credentials, version) "
					+ "values (103, 'dev103', 'GenericSNMP', true, 0)");
				statement.execute("insert into device_credential_sets (device, credential_sets) values (103, 203)");
				statement.execute("insert into device_credential_sets (device, credential_sets) values (103, 204)");

				// Device 104 (GenericSNMP): no specific, no pooled credential, auto-try disabled ->
				// no device_access row at all (no row already means "never used").
				statement.execute("insert into device (id, name, driver, auto_try_credentials, version) "
					+ "values (104, 'dev104', 'GenericSNMP', false, 0)");

				// Device 105 (non-GenericSNMP): specific SSH credential -> "specific" pin on ssh.
				statement.execute("insert into device (id, name, driver, auto_try_credentials, version, specific_credential_set) "
					+ "values (105, 'dev105', 'CiscoIOS12', false, 0, 205)");

				// Device 106 (non-GenericSNMP): no specific, exactly one pooled SSH-key credential -> "global" pin on ssh.
				statement.execute("insert into device (id, name, driver, auto_try_credentials, version) "
					+ "values (106, 'dev106', 'CiscoIOS12', true, 0)");
				statement.execute("insert into device_credential_sets (device, credential_sets) values (106, 206)");

				// Device 107 (non-GenericSNMP): no specific, no pooled credential, auto-try disabled ->
				// no device_access row at all.
				statement.execute("insert into device (id, name, driver, auto_try_credentials, version) "
					+ "values (107, 'dev107', 'CiscoIOS12', false, 0)");

				// Device 108 (non-GenericSNMP): BOTH a specific Telnet credential AND a (different) pooled Telnet
				// credential -> specific must win, pinning cred-208 (not the pooled cred-209) on telnet.
				statement.execute("insert into device (id, name, driver, auto_try_credentials, version, specific_credential_set) "
					+ "values (108, 'dev108', 'CiscoIOS12', false, 0, 208)");
				statement.execute("insert into device_credential_sets (device, credential_sets) values (108, 209)");

				// Device 109 (GenericSNMP): only a pooled SSH credential (no SNMP credential at all), auto-try
				// disabled -> SSH must NOT be migrated (GenericSNMP devices are excluded from ssh/telnet), and
				// SNMP gets no row either (no usable SNMP credential, auto-try disabled).
				statement.execute("insert into device (id, name, driver, auto_try_credentials, version) "
					+ "values (109, 'dev109', 'GenericSNMP', false, 0)");
				statement.execute("insert into device_credential_sets (device, credential_sets) values (109, 210)");

				// Device 110 (GenericSNMP): no specific, no pooled credential, auto-try enabled -> bare
				// (unpinned, enabled) device_access rows for all three snmpv1/snmpv2c/snmpv3.
				statement.execute("insert into device (id, name, driver, auto_try_credentials, version) "
					+ "values (110, 'dev110', 'GenericSNMP', true, 0)");

				// Device 111 (non-GenericSNMP): no specific, no pooled credential, auto-try enabled -> bare
				// (unpinned, enabled) device_access rows for both ssh and telnet.
				statement.execute("insert into device (id, name, driver, auto_try_credentials, version) "
					+ "values (111, 'dev111', 'CiscoIOS12', true, 0)");

				// Device 112 (non-GenericSNMP): an 'ssh' device_access row already exists (simulating what
				// 0.25.0_30 would have produced from a custom ssh_port - by this point in the changelog
				// ssh_port/telnet_port are already dropped, so it's seeded directly), no specific/pooled
				// credential, auto-try enabled -> the bare-row insert must leave that pre-existing row
				// alone (ON CONFLICT DO NOTHING), while still creating a fresh bare 'telnet' row.
				statement.execute("insert into device (id, name, driver, auto_try_credentials, version) "
					+ "values (112, 'dev112', 'CiscoIOS12', true, 0)");
				statement.execute("insert into device_access (device, access_name, port) values (112, 'ssh', 2222)");
			}

			// Resume the migration to completion: this is where the enhanced 0.25.0_32 logic runs.
			Database.update();

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				assertDeviceAccess(statement, 101, "snmpv2c", null, 201L);
				assertNoDeviceAccess(statement, 101, "snmpv1");
				assertNoDeviceAccess(statement, 101, "snmpv3");

				assertDeviceAccess(statement, 102, "snmpv3", 202L, null);

				assertDeviceAccess(statement, 103, "snmpv1", 203L, null);
				assertNoDeviceAccess(statement, 103, "snmpv2c");
				assertNoDeviceAccess(statement, 103, "snmpv3");

				assertNoDeviceAccess(statement, 104, "snmpv1");
				assertNoDeviceAccess(statement, 104, "snmpv2c");
				assertNoDeviceAccess(statement, 104, "snmpv3");

				assertDeviceAccess(statement, 105, "ssh", null, 205L);

				assertDeviceAccess(statement, 106, "ssh", 206L, null);

				assertNoDeviceAccess(statement, 107, "ssh");

				assertDeviceAccess(statement, 108, "telnet", null, 208L);

				assertNoDeviceAccess(statement, 109, "ssh");
				assertNoDeviceAccess(statement, 109, "snmpv1");
				assertNoDeviceAccess(statement, 109, "snmpv2c");
				assertNoDeviceAccess(statement, 109, "snmpv3");

				assertDeviceAccess(statement, 110, "snmpv1", null, null);
				assertDeviceAccess(statement, 110, "snmpv2c", null, null);
				assertDeviceAccess(statement, 110, "snmpv3", null, null);

				assertDeviceAccess(statement, 111, "ssh", null, null);
				assertDeviceAccess(statement, 111, "telnet", null, null);

				assertDeviceAccess(statement, 112, "ssh", null, null);
				assertDeviceAccessPortAddress(statement, 112, "ssh", 2222, null);
				assertDeviceAccess(statement, 112, "telnet", null, null);
			}
		}
	}

	@Nested
	@DisplayName("0.25.1_1 - CachedIpAddressMigrator (missing cached IP address of the upgraded devices)")
	class CachedIpAddressMigratorTest {

		private Properties getNetshotConfig(String databaseName) {
			Properties config = getFreshDatabaseConfig(databaseName);
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "INFO");
			return config;
		}

		@Test
		@DisplayName("A device upgraded from 0.24 gets its cached IP address, so a search by IP finds it before any snapshot")
		void setsCachedIpAddressOnUpgradeFrom024() throws Exception {
			TestConfig.init(this.getNetshotConfig("cachedipfrom024test"));

			// Stop right before 0.25.0_18 (MgmtAddressMigrator): the legacy int ipv4_address column is still there.
			updateToChangeSet("0.25.0_17", "netshot");

			int mgmtIpv4Int = Network4Address.inetAddressToInt(
				(Inet4Address) InetAddress.getByName("10.1.2.3"));

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				// A device that was never snapshotted: no name, no interface.
				statement.execute(
					"insert into device (id, name, auto_try_credentials, version, ssh_port, telnet_port, "
						+ "ipv4_address, ipv4_pfxlen, ipv4_usage) "
						+ "values (501, '[NONAME]', false, 0, 22, 23, %d, 0, 0)".formatted(mgmtIpv4Int));
			}

			Database.update();

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				assertInetColumn(statement,
					"select mgmt_address from device where id = 501", "10.1.2.3");
				assertInetColumn(statement,
					"select cached_ip_address from device where id = 501", "10.1.2.3");
			}
		}

		@Test
		@DisplayName("On a 0.25.0 database, only the missing cached IP addresses of literal management addresses are set")
		void setsMissingCachedIpAddressOn0250() throws Exception {
			TestConfig.init(this.getNetshotConfig("cachedipfrom0250test"));

			// The database of a 0.25.0 installation.
			updateToChangeSet("0.25.0_41", "netshot");

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				String insert = "insert into device (id, name, version, mgmt_address, cached_ip_address) "
					+ "values (%d, '[NONAME]', 0, %s, %s)";
				statement.execute(insert.formatted(601, "'10.1.2.4'", "null"));
				statement.execute(insert.formatted(602, "'2001:db8::1'", "null"));
				statement.execute(insert.formatted(603, "'router.example.com'", "null"));
				statement.execute(insert.formatted(604, "'999.1.2.3'", "null"));
				statement.execute(insert.formatted(605, "'10.1.2.5'", "'10.9.9.9'"));
			}

			Database.update();

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				assertInetColumn(statement,
					"select cached_ip_address from device where id = 601", "10.1.2.4");
				assertInetColumn(statement,
					"select cached_ip_address from device where id = 602", "2001:db8::1");
				// A host name isn't resolved during the migration
				assertInetColumn(statement,
					"select cached_ip_address from device where id = 603", null);
				assertInetColumn(statement,
					"select cached_ip_address from device where id = 604", null);
				// An existing cached IP address is kept
				assertInetColumn(statement,
					"select cached_ip_address from device where id = 605", "10.9.9.9");
			}
		}
	}

	@Nested
	@DisplayName("0.25.1_2 - indexes of the device columns of the task table")
	class TaskDeviceIndexMigrationTest {

		@Test
		@DisplayName("The device and discovered device columns of the task table are indexed")
		void indexesTaskDeviceColumns() throws Exception {
			TestConfig.init(DatabaseMigrationTest.this.getFreshDatabaseConfig("taskdeviceindexmigrationtest"));
			Database.update();

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				Assertions.assertEquals(List.of(List.of("device"), List.of("discover_result_device")),
					rows(statement, "select a.attname from pg_index i "
						+ "join pg_class ic on ic.oid = i.indexrelid join pg_class t on t.oid = i.indrelid "
						+ "join pg_attribute a on a.attrelid = t.oid and a.attnum = i.indkey[0] "
						+ "where t.relname = 'task' and t.relnamespace = current_schema()::regnamespace "
						+ "and ic.relname in ('ix_task_device', 'ix_task_discover_result_device') order by ic.relname collate \"C\"", 1));
			}
		}
	}

	@Nested
	@DisplayName("0.26.0_4 - hooks in one table, with their enumerations stored by name")
	class HookSingleTableMigrationTest {

		@Test
		@DisplayName("The Web hooks and their triggers are kept, the ordinals become names, a hook without a type is dropped")
		void migratesHooks() throws Exception {
			TestConfig.init(DatabaseMigrationTest.this.getFreshDatabaseConfig("hooksingletablemigrationtest"));
			updateToChangeSet("0.26.0_3", "netshot");

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				statement.execute("insert into hook (id, enabled, name) values (401, true, 'json hook'), (402, false, 'yaml hook'), "
					+ "(403, true, 'orphan')");
				statement.execute("insert into web_hook (id, action, url, https_ca_trust_mode, https_custom_ca_certificate) "
					+ "values (401, 1, 'https://example.net/a', 'TRUST_ANY', null), (402, 2, 'https://example.net/b', 'CUSTOM_CA', 'PEM')");
				statement.execute("insert into hook_trigger (type, item, hook) values (0, 'TakeSnapshotTask', 401), "
					+ "(0, 'CheckComplianceTask', 401), (0, 'TakeSnapshotTask', 402)");
			}

			Database.update();

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				try (var rows = statement.executeQuery("select id, type, name, enabled, action, url, https_ca_trust_mode, "
						+ "https_custom_ca_certificate from hook order by id")) {
					Assertions.assertTrue(rows.next());
					Assertions.assertEquals(List.of("401", "WEB", "json hook", "t", "POST_JSON", "https://example.net/a", "TRUST_ANY", "null"),
						row(rows, 8));
					Assertions.assertTrue(rows.next());
					Assertions.assertEquals(List.of("402", "WEB", "yaml hook", "f", "POST_YAML", "https://example.net/b", "CUSTOM_CA", "PEM"),
						row(rows, 8));
					Assertions.assertFalse(rows.next(), "The hook without a type is dropped");
				}
				try (var rows = statement.executeQuery("select hook, type, item from hook_trigger order by hook, item")) {
					List<List<String>> triggers = new ArrayList<>();
					while (rows.next()) {
						triggers.add(row(rows, 3));
					}
					Assertions.assertEquals(List.of(
						List.of("401", "POST_TASK", "CheckComplianceTask"),
						List.of("401", "POST_TASK", "TakeSnapshotTask"),
						List.of("402", "POST_TASK", "TakeSnapshotTask")), triggers);
				}
				try (var rows = statement.executeQuery("select count(*) from information_schema.tables "
						+ "where table_schema = current_schema() and table_name = 'web_hook'")) {
					rows.next();
					Assertions.assertEquals(0, rows.getInt(1), "The web_hook table is dropped");
				}
				// The triggers now follow the hook table
				statement.execute("delete from hook where id = 401");
				try (var rows = statement.executeQuery("select count(*) from hook_trigger where hook = 401")) {
					rows.next();
					Assertions.assertEquals(0, rows.getInt(1));
				}
			}
		}
	}

	private static List<String> row(ResultSet rows, int columns) throws SQLException {
		List<String> values = new ArrayList<>();
		for (int i = 1; i <= columns; i++) {
			values.add(String.valueOf(rows.getString(i)));
		}
		return values;
	}

	private static List<List<String>> rows(Statement statement, String query, int columns) throws SQLException {
		List<List<String>> result = new ArrayList<>();
		try (ResultSet rows = statement.executeQuery(query)) {
			while (rows.next()) {
				result.add(row(rows, columns));
			}
		}
		return result;
	}

	private static boolean tableExists(Statement statement, String table) throws SQLException {
		try (ResultSet rows = statement.executeQuery("select count(*) from information_schema.tables "
				+ "where table_schema = current_schema() and table_name = '%s'".formatted(table))) {
			rows.next();
			return rows.getInt(1) > 0;
		}
	}

	@Nested
	@DisplayName("0.26.0_5 to 0.26.0_7 - rules, diagnostics and device groups in one table each")
	class SingleTableHierarchiesMigrationTest {

		@Test
		@DisplayName("The rows of the subclass tables move to the parent table, with their type; a row without subclass is dropped")
		void migratesHierarchies() throws Exception {
			TestConfig.init(DatabaseMigrationTest.this.getFreshDatabaseConfig("singletablehierarchiesmigrationtest"));
			updateToChangeSet("0.26.0_4", "netshot");

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				statement.execute("insert into rule (id, enabled, name) values (501, true, 'text'), (502, false, 'js'), "
					+ "(503, true, 'python'), (504, true, 'orphan')");
				statement.execute("insert into text_rule (id, any_block, context, device_driver, field, invert, match_all, normalize, reg_exp, text) "
					+ "values (501, true, 'interface .*', 'CiscoIOS12', 'runningConfig', false, true, true, false, 'shutdown')");
				statement.execute("insert into java_script_rule (id, script) values (502, 'function check() {}')");
				statement.execute("insert into python_rule (id, script) values (503, 'def check(): pass')");

				statement.execute("insert into diagnostic (id, enabled, name, result_type) values (601, true, 'simple', 1), "
					+ "(602, true, 'js', 2), (603, false, 'python', 3), (604, true, 'orphan', 1)");
				statement.execute("insert into simple_diagnostic (id, cli_mode, command, device_driver, modifier_pattern, modifier_replacement) "
					+ "values (601, 'enable', 'show version', 'CiscoIOS12', '^.*(Version .*)$', '$1')");
				statement.execute("insert into java_script_diagnostic (id, script) values (602, 'function diagnose() {}')");
				statement.execute("insert into python_diagnostic (id, script) values (603, 'def diagnose(): pass')");

				statement.execute("insert into device_group (id, name, folder, hidden_from_reports, version) values "
					+ "(701, 'static', 'a/b', false, 0), (702, 'dynamic', '', true, 3), (703, 'orphan', '', false, 0)");
				statement.execute("insert into static_device_group (id) values (701)");
				statement.execute("insert into dynamic_device_group (id, query) values (702, '[Name] contains \"r\"')");
			}

			Database.update();

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				Assertions.assertEquals(List.of(
					List.of("501", "TEXT", "text", "t", "null", "t", "interface .*", "CiscoIOS12", "runningConfig", "f", "t", "t", "f", "shutdown"),
					List.of("502", "JAVASCRIPT", "js", "f", "function check() {}", "null", "null", "null", "null", "null", "null", "null", "null", "null"),
					List.of("503", "PYTHON", "python", "t", "def check(): pass", "null", "null", "null", "null", "null", "null", "null", "null", "null")),
					rows(statement, "select id, type, name, enabled, script, any_block, context, device_driver, field, invert, match_all, "
						+ "normalize, reg_exp, text from rule order by id", 14), "The rules are migrated, the orphan is dropped");
				Assertions.assertEquals(List.of(
					List.of("601", "SIMPLE", "simple", "TEXT", "null", "enable", "show version", "CiscoIOS12", "^.*(Version .*)$", "$1"),
					List.of("602", "JAVASCRIPT", "js", "LONGTEXT", "function diagnose() {}", "null", "null", "null", "null", "null"),
					List.of("603", "PYTHON", "python", "DATE", "def diagnose(): pass", "null", "null", "null", "null", "null")),
					rows(statement, "select id, type, name, result_type, script, cli_mode, command, device_driver, modifier_pattern, "
						+ "modifier_replacement from diagnostic order by id", 10), "The diagnostics are migrated, the orphan is dropped");
				Assertions.assertEquals(List.of(
					List.of("701", "STATIC", "static", "a/b", "f", "0", "null"),
					List.of("702", "DYNAMIC", "dynamic", "", "t", "3", "[Name] contains \"r\"")),
					rows(statement, "select id, type, name, folder, hidden_from_reports, version, query from device_group order by id", 7),
					"The groups are migrated, the orphan is dropped");
				for (String table : List.of("text_rule", "java_script_rule", "python_rule", "simple_diagnostic", "java_script_diagnostic",
						"python_diagnostic", "static_device_group", "dynamic_device_group")) {
					Assertions.assertFalse(tableExists(statement, table), "The table " + table + " is dropped");
				}
				Assertions.assertThrows(SQLException.class,
					() -> statement.execute("insert into rule (id, enabled, name, type) values (505, true, 'bad', 'OTHER')"));
				Assertions.assertThrows(SQLException.class,
					() -> statement.execute("insert into rule (id, enabled, name, type) values (506, true, 'flagless', 'TEXT')"),
					"A text rule needs its flags");
			}
		}
	}

	@Nested
	@DisplayName("0.26.0_8 - enumerations stored by name instead of ordinal")
	class EnumNamesMigrationTest {

		@Test
		@DisplayName("Each ordinal becomes the name of the constant at that position")
		void migratesOrdinals() throws Exception {
			TestConfig.init(DatabaseMigrationTest.this.getFreshDatabaseConfig("enumnamesmigrationtest"));
			updateToChangeSet("0.26.0_7", "netshot");

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				statement.execute("insert into device (id, version, name, status, network_class, software_level) values "
					+ "(801, 0, 'r1', 1, 2, 1), (802, 0, 'r2', 2, 10, 3)");
				statement.execute("insert into domain (id, version, name, ipv4_usage, ipv6_usage) values (811, 0, 'd', 0, 3)");
				statement.execute("insert into network_interface (id, enabled, level3, device) values (821, true, true, 801)");
				statement.execute("insert into network_interface_ip4addresses (network_interface, address, prefix_length, address_usage) values (821, '192.0.2.1', 24, 2)");
				statement.execute("insert into network_interface_ip6addresses (network_interface, address, prefix_length, address_usage) values (821, '2001:db8::1', 64, 5)");
				statement.execute("insert into rule (id, enabled, name, type) values (901, true, 'r', 'JAVASCRIPT')");
				statement.execute("insert into check_result (device, rule, result) values (801, 901, 4)");
				statement.execute("insert into software_rule (id, family_reg_exp, priority, version_reg_exp, part_number_reg_exp, level) "
					+ "values (902, false, 1, false, false, 2)");
				statement.execute("insert into diagnostic (id, type, enabled, name, result_type) values (903, 'SIMPLE', true, 'd', 6)");
				statement.execute("insert into task (id, version, debug_enabled, priority, dtype, run_snapshot, run_diagnostics, check_compliance, "
					+ "dont_run_diagnostics, dont_check_compliance, child_order, stop_on_failure, cancel_requested, status, schedule_type, schedule_mode) "
					+ "values (951, 0, false, 5, 'ScanSubnetsTask', false, false, false, false, false, 0, false, false, 7, 5, 1), "
					+ "(952, 0, false, 5, 'PurgeDatabaseTask', false, false, false, false, false, 0, false, false, 0, 0, 0)");
				statement.execute("insert into scan_subnets_task_subnets (scan_subnets_task, ipv4usage) values (951, 5)");
			}

			// 0.26.0_8 only: 0.26.0_11 drops the address usages of the domains
			updateNext(1);

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				Assertions.assertEquals(List.of(
					List.of("801", "INPRODUCTION", "ROUTER", "SILVER"),
					List.of("802", "PREPRODUCTION", "VOICEGATEWAY", "UNKNOWN")),
					rows(statement, "select id, status, network_class, software_level from device order by id", 4));
				Assertions.assertEquals(List.of(List.of("PRIMARY", "HSRP")),
					rows(statement, "select ipv4_usage, ipv6_usage from domain", 2));
				Assertions.assertEquals(List.of(List.of("VRRP")),
					rows(statement, "select address_usage from network_interface_ip4addresses", 1));
				Assertions.assertEquals(List.of(List.of("SECONDARYHSRP")),
					rows(statement, "select address_usage from network_interface_ip6addresses", 1));
				Assertions.assertEquals(List.of(List.of("NONCONFORMING")), rows(statement, "select result from check_result", 1));
				Assertions.assertEquals(List.of(List.of("BRONZE")), rows(statement, "select level from software_rule", 1));
				Assertions.assertEquals(List.of(List.of("ENUM")), rows(statement, "select result_type from diagnostic", 1));
				Assertions.assertEquals(List.of(
					List.of("951", "DELAYED", "HOURLY", "SEQUENTIAL"),
					List.of("952", "CANCELLED", "ASAP", "PARALLEL")),
					rows(statement, "select id, status, schedule_type, schedule_mode from task order by id", 4));
				Assertions.assertEquals(List.of(List.of("SECONDARYHSRP")),
					rows(statement, "select ipv4usage from scan_subnets_task_subnets", 1));
			}
		}
	}

	@Nested
	@DisplayName("0.26.0_10 - orphaned long texts deleted")
	class OrphanedLongTextMigrationTest {

		@Test
		@DisplayName("The long texts no attribute or diagnostic result refers to are deleted, the others are kept")
		void deletesOrphanedLongTexts() throws Exception {
			TestConfig.init(DatabaseMigrationTest.this.getFreshDatabaseConfig("orphanedlongtextmigrationtest"));
			updateToChangeSet("0.26.0_9", "netshot");

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				statement.execute("insert into long_text_configuration (id, text) values "
					+ "(1001, 'device attribute'), (1002, 'config attribute'), (1003, 'diagnostic result'), (1004, 'orphan')");
				statement.execute("insert into device (id, version, name, status) values (1011, 0, 'r1', 'INPRODUCTION')");
				statement.execute("insert into device_attribute (id, type, name, device, long_text) values (1021, 'L', 'a', 1011, 1001)");
				statement.execute("insert into config (id, version, device) values (1031, 0, 1011)");
				statement.execute("insert into config_attribute (id, type, name, config, long_text) values (1041, 'L', 'a', 1031, 1002)");
				statement.execute("insert into diagnostic (id, type, enabled, name, result_type) values (1051, 'SIMPLE', true, 'd', 'LONGTEXT')");
				statement.execute("insert into diagnostic_result (id, type, device, diagnostic, long_text) values (1061, 'L', 1011, 1051, 1003)");
			}

			Database.update();

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				Assertions.assertEquals(List.of(List.of("1001"), List.of("1002"), List.of("1003")),
					rows(statement, "select id from long_text_configuration order by id", 1));
			}
		}
	}

	@Nested
	@DisplayName("0.26.0_11 - prefix length and usage of the domain server addresses dropped")
	class DomainAddressColumnsMigrationTest {

		@Test
		@DisplayName("The columns are dropped and the addresses kept")
		void dropsColumns() throws Exception {
			TestConfig.init(DatabaseMigrationTest.this.getFreshDatabaseConfig("domainaddresscolumnsmigrationtest"));
			updateToChangeSet("0.26.0_10", "netshot");

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				// As a domain created before 0.24 was stored (prefix length 0)
				statement.execute("insert into domain (id, version, name, ipv4_address, ipv4_pfxlen, ipv4_usage, ipv6_address, ipv6_pfxlen, ipv6_usage) "
					+ "values (1101, 0, 'd', '10.0.0.1', 0, 'PRIMARY', '2001:db8::1', 0, 'PRIMARY')");
			}

			Database.update();

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				Assertions.assertEquals(List.of(List.of("10.0.0.1", "2001:db8::1")),
					rows(statement, "select host(ipv4_address), host(ipv6_address) from domain", 2));
				Assertions.assertEquals(List.of(List.of("0")),
					rows(statement, "select count(*) from information_schema.columns where table_schema = current_schema() and table_name = 'domain' "
						+ "and column_name in ('ipv4_pfxlen', 'ipv4_usage', 'ipv6_pfxlen', 'ipv6_usage')", 1));
			}
		}
	}

	@Nested
	@DisplayName("0.26.0_12 - last snapshot dates stored on the devices")
	class SnapshotDatesMigrationTest {

		@Test
		@DisplayName("The dates are initialized from the latest successful and failed snapshot tasks of each device")
		void backfillsSnapshotDates() throws Exception {
			TestConfig.init(DatabaseMigrationTest.this.getFreshDatabaseConfig("snapshotdatesmigrationtest"));
			updateToChangeSet("0.26.0_11", "netshot");

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				statement.execute("insert into device (id, version, name, status) values (1201, 0, 'both', 'INPRODUCTION'), "
					+ "(1202, 0, 'never', 'INPRODUCTION'), (1203, 0, 'failedonly', 'INPRODUCTION')");
				String columns = "insert into task (id, version, debug_enabled, priority, dtype, run_snapshot, run_diagnostics, check_compliance, "
					+ "dont_run_diagnostics, dont_check_compliance, child_order, stop_on_failure, cancel_requested, schedule_type, schedule_mode, "
					+ "device, status, execution_date) values ";
				String common = "false, 5, 'TakeSnapshotTask', false, false, false, false, false, 0, false, false, 'ASAP', 'PARALLEL'";
				statement.execute(columns
					+ "(1301, 0, " + common + ", 1201, 'SUCCESS', '2026-01-01 10:00:00'), "
					+ "(1302, 0, " + common + ", 1201, 'SUCCESS', '2026-03-01 10:00:00'), "
					+ "(1303, 0, " + common + ", 1201, 'FAILURE', '2026-02-01 10:00:00'), "
					+ "(1304, 0, " + common + ", 1201, 'CANCELLED', '2026-04-01 10:00:00'), "
					+ "(1305, 0, " + common + ", 1203, 'FAILURE', '2026-05-01 10:00:00'), "
					+ "(1306, 0, false, 5, 'CheckComplianceTask', false, false, false, false, false, 0, false, false, 'ASAP', 'PARALLEL', 1202, "
					+ "'SUCCESS', '2026-06-01 10:00:00')");
			}

			Database.update();

			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				Assertions.assertEquals(
					List.of(List.of("1201", "2026-03-01 10:00:00", "2026-02-01 10:00:00"), List.of("1202", "none", "none"),
						List.of("1203", "none", "2026-05-01 10:00:00")),
					rows(statement, "select id, coalesce(to_char(last_snapshot_success_date, 'YYYY-MM-DD HH24:MI:SS'), 'none'), "
						+ "coalesce(to_char(last_snapshot_failure_date, 'YYYY-MM-DD HH24:MI:SS'), 'none') from device order by id", 3));
			}
		}
	}

	@Nested
	@DisplayName("Custom changes moved to platform.database.migrator keep upgrades of existing databases valid")
	class CustomChangeChecksumTest {

		/**
		 * Checksums recorded in the DATABASECHANGELOG table by Netshot versions whose
		 * Liquibase custom changes still lived in net.netshot.netshot.database.migrator
		 * (the class name is part of the checksum, hence the validCheckSum elements).
		 */
		private static final java.util.Map<String, String> LEGACY_CHECKSUMS = java.util.Map.of(
			"0.21.0_1", "9:a346810332ef3abb4e6ca55aeb24f9f3",
			"0.24.0_2", "9:6fae930e4fda7d19cc20ca232b0ef395",
			"0.25.0_18", "9:c3fe37ff86d1bf5735e5c8631652f9c0",
			"0.25.0_24", "9:4319e4962071801f673e8e22fe1f936d",
			"0.25.1_1", "9:36a251399f92c7def86fb996b68fa834");

		private Properties getNetshotConfig() {
			Properties config = getFreshDatabaseConfig("customchangechecksumtest");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "INFO");
			return config;
		}

		@Test
		@DisplayName("A database migrated with the legacy custom change class names still validates and upgrades")
		void legacyChecksumsRemainValid() throws Exception {
			TestConfig.init(this.getNetshotConfig());
			Database.update();

			// Simulate a database created by a version using the legacy class names
			try (Connection connection = Database.openUnpooledConnection();
					Statement statement = connection.createStatement()) {
				for (var entry : LEGACY_CHECKSUMS.entrySet()) {
					int updated = statement.executeUpdate(
						"update databasechangelog set md5sum = '%s' where id = '%s' and author = 'netshot'"
							.formatted(entry.getValue(), entry.getKey()));
					Assertions.assertEquals(1, updated, "Changeset %s not found".formatted(entry.getKey()));
				}
			}

			// Liquibase validates the stored checksums before applying anything: this throws if the
			// legacy checksums are not accepted by the current changelog.
			Assertions.assertDoesNotThrow(() -> Database.update());

			// The changesets now compute a different checksum (so the validCheckSum elements are required)
			for (var entry : LEGACY_CHECKSUMS.entrySet()) {
				Assertions.assertNotEquals(entry.getValue(), this.currentChecksum(entry.getKey()));
			}
		}

		private String currentChecksum(String changeSetId) throws Exception {
			ResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
			DatabaseChangeLog changeLog = ChangeLogParserFactory.getInstance()
				.getParser("migration/netshot0.xml", resourceAccessor)
				.parse("migration/netshot0.xml", new ChangeLogParameters(new liquibase.database.core.PostgresDatabase()), resourceAccessor);
			for (ChangeSet changeSet : changeLog.getChangeSets()) {
				if (changeSet.getId().equals(changeSetId) && changeSet.getAuthor().equals("netshot")) {
					return changeSet.generateCheckSum(liquibase.ChecksumVersion.latest()).toString();
				}
			}
			throw new IllegalArgumentException(changeSetId);
		}
	}
}
