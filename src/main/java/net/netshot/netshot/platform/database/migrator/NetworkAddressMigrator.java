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
package net.netshot.netshot.platform.database.migrator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import liquibase.change.custom.CustomSqlChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.UpdateStatement;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts the legacy 32-bit-int/64-bit-long IPv4/IPv6 columns on
 * "network_interface_ip4addresses", "network_interface_ip6addresses",
 * "domain" and "scan_subnets_task_subnets" into their text form, now stored
 * in sibling "*_inet" columns of native "inet" type (the caller is
 * responsible for dropping the legacy columns and renaming the "*_inet"
 * ones afterwards).
 */
@Slf4j
public class NetworkAddressMigrator implements CustomSqlChange {

	@Override
	public String getConfirmationMessage() {
		return null;
	}

	@Override
	public void setUp() throws SetupException {
	}

	@Override
	public void setFileOpener(ResourceAccessor resourceAccessor) {
		// Nothing to do
	}

	@Override
	public ValidationErrors validate(Database database) {
		return null;
	}

	@Override
	public SqlStatement[] generateStatements(Database database) throws CustomChangeException {
		List<SqlStatement> statements = new ArrayList<>();
		JdbcConnection connection = (JdbcConnection) database.getConnection();

		try {
			this.migrateIpv4ById(connection, database, statements,
				"domain", "id", "ipv4_address", "ipv4_address_inet");
			this.migrateIpv6ById(connection, database, statements,
				"domain", "id", "ipv6_address1", "ipv6_address2", "ipv6_address_inet");
			this.migrateIpv4ByKey(connection, database, statements,
				"network_interface_ip4addresses",
				List.of("network_interface", "address", "prefix_length"),
				"address", "address_inet");
			this.migrateIpv6ByKey(connection, database, statements,
				"network_interface_ip6addresses",
				List.of("network_interface", "address1", "address2", "prefix_length"),
				"address1", "address2", "address_inet");
			this.migrateIpv4ByKey(connection, database, statements,
				"scan_subnets_task_subnets",
				List.of("scan_subnets_task", "ipv4address", "ipv4mask", "ipv4usage"),
				"ipv4address", "ipv4address_inet");
		}
		catch (DatabaseException | SQLException e) {
			throw new CustomChangeException(
				"Database error while migrating legacy IPv4/IPv6 int/bigint addresses to inet", e);
		}

		return statements.toArray(new SqlStatement[0]);
	}

	/**
	 * Migrates an IPv4 int column into a sibling inet column, matching rows by
	 * primary key (single "id" column, e.g. on "domain").
	 *
	 * @param connection the JDBC connection to read from
	 * @param database the target database
	 * @param statements the list of statements to append the generated updates to
	 * @param tableName the table to migrate
	 * @param idColumn the primary key column of the table
	 * @param intColumn the legacy 32-bit-int address column to read
	 * @param inetColumn the new inet address column to write
	 */
	private void migrateIpv4ById(JdbcConnection connection, Database database,
			List<SqlStatement> statements, String tableName, String idColumn,
			String intColumn, String inetColumn) throws SQLException, DatabaseException {
		String select = "select %s, %s from %s where %s is not null"
			.formatted(idColumn, intColumn, tableName, intColumn);
		ResultSet rows = connection.createStatement().executeQuery(select);
		while (rows.next()) {
			long id = rows.getLong(idColumn);
			int address = rows.getInt(intColumn);
			String ip = AddressConverter.intToIP(address);
			UpdateStatement update = new UpdateStatement(
				database.getDefaultCatalogName(), database.getDefaultSchemaName(), tableName)
				.setWhereClause("%s = ?".formatted(idColumn)).addWhereParameter(id);
			update.addNewColumnValue(inetColumn, ip);
			statements.add(update);
		}
	}

	/**
	 * Migrates a pair of IPv6 bigint columns into a sibling inet column,
	 * matching rows by primary key (single "id" column, e.g. on "domain").
	 *
	 * @param connection the JDBC connection to read from
	 * @param database the target database
	 * @param statements the list of statements to append the generated updates to
	 * @param tableName the table to migrate
	 * @param idColumn the primary key column of the table
	 * @param longColumn1 the legacy high 64-bit-long address column to read
	 * @param longColumn2 the legacy low 64-bit-long address column to read
	 * @param inetColumn the new inet address column to write
	 */
	private void migrateIpv6ById(JdbcConnection connection, Database database,
			List<SqlStatement> statements, String tableName, String idColumn,
			String longColumn1, String longColumn2, String inetColumn) throws SQLException, DatabaseException {
		String select = "select %s, %s, %s from %s where %s is not null"
			.formatted(idColumn, longColumn1, longColumn2, tableName, longColumn1);
		ResultSet rows = connection.createStatement().executeQuery(select);
		while (rows.next()) {
			long id = rows.getLong(idColumn);
			long address1 = rows.getLong(longColumn1);
			long address2 = rows.getLong(longColumn2);
			String ip = AddressConverter.intToIP(address1, address2);
			UpdateStatement update = new UpdateStatement(
				database.getDefaultCatalogName(), database.getDefaultSchemaName(), tableName)
				.setWhereClause("%s = ?".formatted(idColumn)).addWhereParameter(id);
			update.addNewColumnValue(inetColumn, ip);
			statements.add(update);
		}
	}

	/**
	 * Migrates an IPv4 int column into a sibling inet column on a table with
	 * no single-column primary key (element collection join tables), matching
	 * rows by the full set of original key columns instead.
	 *
	 * @param connection the JDBC connection to read from
	 * @param database the target database
	 * @param statements the list of statements to append the generated updates to
	 * @param tableName the table to migrate
	 * @param keyColumns the columns identifying each row
	 * @param intColumn the legacy 32-bit-int address column to read
	 * @param inetColumn the new inet address column to write
	 */
	private void migrateIpv4ByKey(JdbcConnection connection, Database database,
			List<SqlStatement> statements, String tableName, List<String> keyColumns,
			String intColumn, String inetColumn) throws SQLException, DatabaseException {
		String columns = String.join(", ", keyColumns);
		String select = "select %s from %s where %s is not null"
			.formatted(columns, tableName, intColumn);
		ResultSet rows = connection.createStatement().executeQuery(select);
		while (rows.next()) {
			int address = rows.getInt(intColumn);
			String ip = AddressConverter.intToIP(address);
			UpdateStatement update = this.buildKeyedUpdate(database, tableName, keyColumns, rows);
			update.addNewColumnValue(inetColumn, ip);
			statements.add(update);
		}
	}

	/**
	 * Migrates a pair of IPv6 bigint columns into a sibling inet column on a
	 * table with no single-column primary key (element collection join
	 * tables), matching rows by the full set of original key columns instead.
	 *
	 * @param connection the JDBC connection to read from
	 * @param database the target database
	 * @param statements the list of statements to append the generated updates to
	 * @param tableName the table to migrate
	 * @param keyColumns the columns identifying each row
	 * @param longColumn1 the legacy high 64-bit-long address column to read
	 * @param longColumn2 the legacy low 64-bit-long address column to read
	 * @param inetColumn the new inet address column to write
	 */
	private void migrateIpv6ByKey(JdbcConnection connection, Database database,
			List<SqlStatement> statements, String tableName, List<String> keyColumns,
			String longColumn1, String longColumn2, String inetColumn) throws SQLException, DatabaseException {
		String columns = String.join(", ", keyColumns);
		String select = "select %s from %s where %s is not null"
			.formatted(columns, tableName, longColumn1);
		ResultSet rows = connection.createStatement().executeQuery(select);
		while (rows.next()) {
			long address1 = rows.getLong(longColumn1);
			long address2 = rows.getLong(longColumn2);
			String ip = AddressConverter.intToIP(address1, address2);
			UpdateStatement update = this.buildKeyedUpdate(database, tableName, keyColumns, rows);
			update.addNewColumnValue(inetColumn, ip);
			statements.add(update);
		}
	}

	/**
	 * Builds an UPDATE statement matching the exact values of the given key
	 * columns in the current result set row.
	 *
	 * @param database the target database
	 * @param tableName the table to update
	 * @param keyColumns the columns identifying the row to update
	 * @param rows the current result set row, positioned on the row to match
	 * @return the update statement matching the given row
	 */
	private UpdateStatement buildKeyedUpdate(Database database, String tableName,
			List<String> keyColumns, ResultSet rows) throws SQLException {
		StringBuilder whereClause = new StringBuilder();
		List<Object> whereValues = new ArrayList<>();
		for (String keyColumn : keyColumns) {
			if (whereClause.length() > 0) {
				whereClause.append(" and ");
			}
			whereClause.append(keyColumn).append(" = ?");
			whereValues.add(rows.getObject(keyColumn));
		}
		UpdateStatement update = new UpdateStatement(
			database.getDefaultCatalogName(), database.getDefaultSchemaName(), tableName)
			.setWhereClause(whereClause.toString());
		for (Object value : whereValues) {
			update.addWhereParameter(value);
		}
		return update;
	}
}
