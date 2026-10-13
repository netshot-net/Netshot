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
import net.netshot.netshot.inventory.device.Network4Address;

/**
 * Converts the legacy 32-bit-int management/connect/discovery addresses
 * ("ipv4_address" and "connect_ipv4_address" on "device"; "ipv4_address" on
 * "task", for DiscoverDeviceTypeTask rows) into their dotted-quad string
 * form, now stored as plain varchar columns ("mgmt_address",
 * "connect_address", "device_address" respectively).
 */
@Slf4j
public class MgmtAddressMigrator implements CustomSqlChange {

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
			this.migrateAddressColumn(connection, database, statements,
				"device", "id", "ipv4_address", "mgmt_address");
			this.migrateAddressColumn(connection, database, statements,
				"device", "id", "connect_ipv4_address", "connect_address");
			this.migrateAddressColumn(connection, database, statements,
				"task", "id", "ipv4_address", "device_address");
		}
		catch (DatabaseException | SQLException e) {
			throw new CustomChangeException(
				"Database error while migrating legacy IPv4 int addresses to strings", e);
		}

		return statements.toArray(new SqlStatement[0]);
	}

	/**
	 * Reads every non-null value of the legacy int column and queues an update
	 * converting it to its dotted-quad string form in the new column.
	 *
	 * @param connection the JDBC connection to read from
	 * @param database the target database
	 * @param statements the list of statements to append the generated updates to
	 * @param tableName the table to migrate
	 * @param idColumn the primary key column of the table
	 * @param intColumn the legacy 32-bit-int address column to read
	 * @param stringColumn the new string address column to write
	 */
	private void migrateAddressColumn(JdbcConnection connection, Database database,
			List<SqlStatement> statements, String tableName, String idColumn,
			String intColumn, String stringColumn) throws SQLException, DatabaseException {
		String select = "select %s, %s from %s where %s is not null"
			.formatted(idColumn, intColumn, tableName, intColumn);
		ResultSet rows = connection.createStatement().executeQuery(select);
		while (rows.next()) {
			long id = rows.getLong(idColumn);
			int address = rows.getInt(intColumn);
			String ip = Network4Address.intToIP(address);
			UpdateStatement update = new UpdateStatement(
				database.getDefaultCatalogName(), database.getDefaultSchemaName(), tableName)
				.setWhereClause("%s = ?".formatted(idColumn)).addWhereParameter(id);
			update.addNewColumnValue(stringColumn, ip);
			statements.add(update);
		}
	}
}
