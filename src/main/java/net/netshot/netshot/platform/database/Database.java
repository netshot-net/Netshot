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
package net.netshot.netshot.platform.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import liquibase.UpdateSummaryOutputEnum;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.command.core.helpers.ShowSummaryArgument;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;

/**
 * The Database class, utilities to access the database.
 */
@Slf4j
public final class Database {

	/** The connection pools. */
	private static ConnectionPools pools;

	/**
	 * Update the database schema (to be run at startup).
	 * @throws Exception 
	 */
	public static void update() throws Exception {
		// Check the encryption password is available before touching the DB
		Database.getEncryptionPassword();
		try {
			System.setProperty("liquibase.analytics.enabled", "false");
			Class.forName("org.postgresql.Driver");
			try (Connection connection = Database.getConnection(false)) {
				liquibase.database.Database database = DatabaseFactory.getInstance()
					.findCorrectDatabaseImplementation(new JdbcConnection(connection));
				new CommandScope(UpdateCommandStep.COMMAND_NAME)
					.addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
					.addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, "migration/netshot0.xml")
					.addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY_OUTPUT, UpdateSummaryOutputEnum.LOG)
					.execute();
			}
		}
		catch (Exception e) {
			log.error("Unable to perform initial schema update", e);
			throw e;
		}
	}

	/**
	 * Initializes the database access: creates the connection pools.
	 */
	public static synchronized void init() {
		// Initialized again (tests): release the connections of the previous initialization
		shutdown();
		pools = new ConnectionPools(getDriverClass(), getUrl(), getReadUrl(), getUsername(), getPassword());
	}

	/**
	 * Closes the connection pools, if initialized.
	 */
	public static synchronized void shutdown() {
		if (pools != null) {
			pools.close();
			pools = null;
		}
	}

	/**
	 * Retrieve the configured driver class.
	 * 
	 * @return the configured driver class
	 */
	private static String getDriverClass() {
		return Netshot.getConfig("netshot.db.driverclass",
			Netshot.getConfig("netshot.db.driver_class", "org.postgresql.Driver"));
	}

	/**
	 * Retrieve the DB URL.
	 * 
	 * @return the configured DB URL.
	 */
	private static String getUrl() {
		return Netshot.getSecretConfig("netshot.db.url", "jdbc:postgresql://localhost:5432/netshot01?sslmode=disable");
	}

	/**
	 * Retrieve the read-only DB URL.
	 * 
	 * @return the configured DB URL.
	 */
	private static String getReadUrl() {
		return Netshot.getSecretConfig("netshot.db.readurl");
	}

	/**
	 * Retrieve the DB username.
	 * 
	 * @return the configured DB username
	 */
	private static String getUsername() {
		return Netshot.getSecretConfig("netshot.db.username", "netshot");
	}

	/**
	 * Retrieve the DB password.
	 * 
	 * @return the configured DB password
	 */
	private static String getPassword() {
		return Netshot.getSecretConfig("netshot.db.password", "netshot");
	}

	/**
	 * Retrieve the password used to encrypt the sensitive data (e.g. device credentials) in the DB.
	 * Unless {@code netshot.db.encryptionpassword.required} is enabled, defaults to {@code NETSHOT}.
	 *
	 * @return the configured encryption password
	 * @throws IllegalStateException if the password is required but not configured
	 */
	public static String getEncryptionPassword() {
		String password = Netshot.getSecretConfig("netshot.db.encryptionpassword");
		if (password == null) {
			// With capital P, for historical reasons
			password = Netshot.getSecretConfig("netshot.db.encryptionPassword");
		}
		if (password == null) {
			if (Netshot.getConfig("netshot.db.encryptionpassword.required", false)) {
				throw new IllegalStateException("The DB encryption password (netshot.db.encryptionpassword) must be configured, "
					+ "e.g. via the NETSHOT_DB_ENCRYPTIONPASSWORD_FILE environment variable. Note that the Netshot container "
					+ "image up to version 0.25.0 had 'netshot' as built-in value.");
			}
			password = "NETSHOT";
		}
		return password;
	}

	/**
	 * Retrieves a raw SQL connection.
	 * 
	 * @return the connection
	 * @throws SQLException
	 */
	public static Connection getConnection() throws SQLException {
		return getConnection(PoolKind.READ_WRITE);
	}

	/**
	 * Retrieves a connection from one of the pools.
	 *
	 * @param pool the kind of connection
	 * @return the connection
	 * @throws SQLException if no connection can be obtained
	 */
	static Connection getConnection(PoolKind pool) throws SQLException {
		return pools.get(pool).getConnection();
	}

	/**
	 * Retrieves a raw SQL connection.
	 * @param pooled = whether to take the connection from the (C3P0) pool or not
	 *                  (pooled connection expire with C3P0 timeout mechanisms).
	 * @return the connection
	 * @throws SQLException
	 */
	public static Connection getConnection(boolean pooled) throws SQLException {
		if (pooled) {
			return Database.getConnection();
		}
		return DriverManager.getConnection(getUrl(), getUsername(), getPassword());
	}

	private Database() {
	}

}
