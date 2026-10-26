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
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.Setting;

/**
 * The Database class, utilities to access the database.
 */
@Slf4j
public final class Database {

	/** The JDBC driver class. */
	static final Setting<String> DRIVER_CLASS = Setting.text("netshot.db.driverclass").alias("netshot.db.driver_class")
		.defaultValue("org.postgresql.Driver").restart();

	/** The JDBC URL of the (read-write) database. */
	static final Setting<String> URL = Setting.text("netshot.db.url")
		.defaultValue("jdbc:postgresql://localhost:5432/netshot01?sslmode=disable").secret().restart();

	/** The JDBC URL of the read-only database (null to read from the read-write one). */
	static final Setting<String> READ_URL = Setting.text("netshot.db.readurl").secret().restart();

	/** The database user name. */
	static final Setting<String> USERNAME = Setting.text("netshot.db.username").defaultValue("netshot").secret().restart();

	/** The database password. */
	static final Setting<String> PASSWORD = Setting.text("netshot.db.password").defaultValue("netshot").secret().restart();

	/** The password used to encrypt the sensitive data (with a capital P in the legacy key, for historical reasons). */
	static final Setting<String> ENCRYPTION_PASSWORD = Setting.text("netshot.db.encryptionpassword")
		.alias("netshot.db.encryptionPassword").secret().restart();

	/** Whether the encryption password must be configured (no default value). */
	static final Setting<Boolean> ENCRYPTION_PASSWORD_REQUIRED = Setting.bool("netshot.db.encryptionpassword.required")
		.defaultValue(false).restart();

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
			try (Connection connection = Database.openUnpooledConnection()) {
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
	 * Retrieve the configured driver class.
	 * 
	 * @return the configured driver class
	 */
	static String getDriverClass() {
		return Netshot.config().current().get(DRIVER_CLASS);
	}

	/**
	 * Retrieve the DB URL.
	 * 
	 * @return the configured DB URL.
	 */
	static String getUrl() {
		return Netshot.config().current().get(URL);
	}

	/**
	 * Retrieve the read-only DB URL.
	 * 
	 * @return the configured DB URL.
	 */
	static String getReadUrl() {
		return Netshot.config().current().get(READ_URL);
	}

	/**
	 * Retrieve the DB username.
	 * 
	 * @return the configured DB username
	 */
	static String getUsername() {
		return Netshot.config().current().get(USERNAME);
	}

	/**
	 * Retrieve the DB password.
	 * 
	 * @return the configured DB password
	 */
	static String getPassword() {
		return Netshot.config().current().get(PASSWORD);
	}

	/**
	 * Retrieve the password used to encrypt the sensitive data (e.g. device credentials) in the DB.
	 * Unless {@code netshot.db.encryptionpassword.required} is enabled, defaults to {@code NETSHOT}.
	 *
	 * @return the configured encryption password
	 * @throws IllegalStateException if the password is required but not configured
	 */
	public static String getEncryptionPassword() {
		Config config = Netshot.config().current();
		String password = config.get(ENCRYPTION_PASSWORD);
		if (password == null) {
			if (config.get(ENCRYPTION_PASSWORD_REQUIRED)) {
				throw new IllegalStateException("The DB encryption password (netshot.db.encryptionpassword) must be configured, "
					+ "e.g. via the NETSHOT_DB_ENCRYPTIONPASSWORD_FILE environment variable. Note that the Netshot container "
					+ "image up to version 0.25.0 had 'netshot' as built-in value.");
			}
			password = "NETSHOT";
		}
		return password;
	}

	/**
	 * Opens a raw SQL connection, out of the connection pools (for the schema update, and the
	 * long-lived connections of the task locks and the cluster notifications). The caller closes it.
	 *
	 * @return the connection
	 * @throws SQLException if the connection can't be opened
	 */
	public static Connection openUnpooledConnection() throws SQLException {
		return DriverManager.getConnection(getUrl(), getUsername(), getPassword());
	}

	private Database() {
	}

}
