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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
 * Sets the missing "cached_ip_address" of the devices from their
 * "mgmt_address", which MgmtAddressMigrator (0.25.0_18) didn't do: the devices
 * upgraded from 0.24 had no cached IP address until a successful connection,
 * so a search by IP couldn't find them. Only literal IP addresses are
 * converted; host names are left for the next connection to resolve.
 */
@Slf4j
public class CachedIpAddressMigrator implements CustomSqlChange {

	/** A literal IPv4 address (an IPv6 address is recognized by its colons, which a host name can't have). */
	private static final Pattern IPV4_LITERAL = Pattern.compile("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$");

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

	/**
	 * Parses a literal IP address, without any DNS lookup.
	 *
	 * @param address the address
	 * @return the IP address, or null if the address isn't a valid literal IP address
	 */
	static InetAddress parseLiteral(String address) {
		if (address == null) {
			return null;
		}
		String trimmed = address.trim();
		if (!IPV4_LITERAL.matcher(trimmed).matches() && !trimmed.contains(":")) {
			return null;
		}
		try {
			// A literal (IPv4 dotted quad, or an address with a colon) is parsed
			// by InetAddress without any lookup
			return InetAddress.getByName(trimmed);
		}
		catch (UnknownHostException | SecurityException e) {
			return null;
		}
	}

	@Override
	public SqlStatement[] generateStatements(Database database) throws CustomChangeException {
		List<SqlStatement> statements = new ArrayList<>();
		JdbcConnection connection = (JdbcConnection) database.getConnection();

		try (ResultSet rows = connection.createStatement().executeQuery(
				"select id, mgmt_address from device where cached_ip_address is null and mgmt_address is not null")) {
			while (rows.next()) {
				long id = rows.getLong("id");
				InetAddress ip = parseLiteral(rows.getString("mgmt_address"));
				if (ip == null) {
					continue;
				}
				UpdateStatement update = new UpdateStatement(
					database.getDefaultCatalogName(), database.getDefaultSchemaName(), "device")
					.setWhereClause("id = ?").addWhereParameter(id);
				update.addNewColumnValue("cached_ip_address", ip.getHostAddress());
				statements.add(update);
			}
		}
		catch (DatabaseException | SQLException e) {
			throw new CustomChangeException(
				"Database error while setting the cached IP address of the devices", e);
		}
		log.info("Setting the missing cached IP address of {} device(s).", statements.size());

		return statements.toArray(new SqlStatement[0]);
	}
}
