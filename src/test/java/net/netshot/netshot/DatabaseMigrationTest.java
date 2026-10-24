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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
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
import net.netshot.netshot.database.Database;
import net.netshot.netshot.database.migrator.NetworkAddressMigrator;
import net.netshot.netshot.device.Network4Address;
import net.netshot.netshot.device.Network6Address;

/**
 * Tests for the Liquibase {@code CustomSqlChange} data migrators under
 * {@link net.netshot.netshot.database.migrator} (currently just
 * {@link NetworkAddressMigrator} - add further migrators here as
 * additional {@code @Nested} classes). Testcontainers-backed tests
 * otherwise only ever see a freshly created schema, so these
 * data-migration paths (as opposed to fresh-install schema creation) are
 * never exercised elsewhere: each test here stops the schema migration
 * just before the migrator's own changeset, seeds rows directly via JDBC
 * in the legacy pre-migration shape, resumes the migration to completion,
 * and checks the result.
 */
public class DatabaseMigrationTest extends WithDatabaseTest {

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
		try (Connection connection = Database.getConnection(false)) {
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

			new CommandScope(UpdateCountCommandStep.COMMAND_NAME)
				.addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
				.addArgumentValue(UpdateCountCommandStep.CHANGELOG_FILE_ARG, "migration/netshot0.xml")
				.addArgumentValue(UpdateCountCommandStep.COUNT_ARG, count)
				.addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY_OUTPUT, UpdateSummaryOutputEnum.LOG)
				.execute();
		}
	}

	@Nested
	@DisplayName("0.25.0_24 - NetworkAddressMigrator (legacy int/bigint IP columns to native inet)")
	class Changeset_0_25_0_24_NetworkAddressMigratorTest {

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
			Netshot.initConfig(this.getNetshotConfig());

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

			try (Connection connection = Database.getConnection(false);
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

			try (Connection connection = Database.getConnection(false);
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

	/** Asserts no {@code device_access} row exists for the given (device, accessName). */
	private static void assertNoDeviceAccess(Statement statement, long deviceId, String accessName) throws SQLException {
		try (var rows = statement.executeQuery(
				"select 1 from device_access where device = %d and access_name = '%s'".formatted(deviceId, accessName))) {
			Assertions.assertFalse(rows.next(),
				"Unexpected device_access row for device %d access '%s'".formatted(deviceId, accessName));
		}
	}

	/** Asserts a single {@code device_access} row's port/address columns (pass {@code null} to assert NULL). */
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
	class Changeset_0_25_0_30_PortAddressMigrationTest {

		private Properties getNetshotConfig() {
			Properties config = getFreshDatabaseConfig("portaddressmigrationtest");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "INFO");
			return config;
		}

		@Test
		@DisplayName("Port override and connect address merge into a single device_access row per protocol, with no spurious port override when only the address differs")
		void migratesPortsAndAddressToSingleRowPerProtocol() throws Exception {
			Netshot.initConfig(this.getNetshotConfig());

			// Stop right before 0.25.0_30: device_access exists (0.25.0_29), but the
			// legacy ssh_port/telnet_port/connect_address columns are still on device.
			updateToChangeSet("0.25.0_29", "netshot");

			try (Connection connection = Database.getConnection(false);
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

			try (Connection connection = Database.getConnection(false);
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
	class Changeset_0_25_0_32_DeviceAccessMigrationTest {

		private Properties getNetshotConfig() {
			Properties config = getFreshDatabaseConfig("deviceaccessmigrationtest");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "INFO");
			return config;
		}

		@Test
		@DisplayName("Specific/pooled/auto-try device-wide credentials resolve to the right per-access pin")
		void migratesDeviceWideCredentialsToPerAccessPins() throws Exception {
			Netshot.initConfig(this.getNetshotConfig());

			// Stop right before the (modified) 0.25.0_32 changeset under test: the
			// device_access table exists (0.25.0_29) and ssh_port/telnet_port/
			// connect_address are already migrated away and dropped (0.25.0_30/31),
			// but the legacy device-wide specific_credential_set/auto_try_credentials
			// columns and the device_credential_sets pool table are still present.
			updateToChangeSet("0.25.0_31", "netshot");

			try (Connection connection = Database.getConnection(false);
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

			try (Connection connection = Database.getConnection(false);
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
}
