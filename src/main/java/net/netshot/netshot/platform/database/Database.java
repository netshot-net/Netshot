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
package net.netshot.netshot.platform.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import liquibase.UpdateSummaryOutputEnum;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.command.core.helpers.ShowSummaryArgument;
import liquibase.Scope;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.Setting;

/**
 * The database settings, and the access to the database out of the connection pools ({@link DatabaseService}): the
 * schema migration, the raw connections. The wired components go through the {@link DatabaseService}; the static
 * methods are for the code which runs without the wiring (the Java migrations, the tests).
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
	 * The settings of the database connections.
	 *
	 * @param driverClass the JDBC driver class
	 * @param url the JDBC URL of the (read-write) database
	 * @param readUrl the JDBC URL of the read-only database, or null to read from the read-write one
	 * @param username the user name
	 * @param password the password
	 */
	public record Settings(String driverClass, String url, String readUrl, String username, String password) {

		/**
		 * Reads the settings from the configuration.
		 *
		 * @param config the configuration
		 * @return the settings
		 */
		public static Settings from(Config config) {
			return new Settings(config.get(DRIVER_CLASS), config.get(URL), config.get(READ_URL), config.get(USERNAME),
				config.get(PASSWORD));
		}

		/**
		 * Opens a raw SQL connection to the (read-write) database. The caller closes it.
		 *
		 * @return the connection
		 * @throws SQLException if the connection can't be opened
		 */
		Connection openUnpooledConnection() throws SQLException {
			return DriverManager.getConnection(this.url, connectionProperties(this.url, this.username, this.password));
		}
	}

	/**
	 * The key of the configuration in the Liquibase scope, for the Java migrations (which Liquibase builds itself).
	 */
	public static final String CONFIG_SCOPE_KEY = "netshot.config";

	/**
	 * Gets the configuration given to the migrations which run (see {@link #migrate(Config)}).
	 *
	 * @return the configuration
	 * @throws IllegalStateException if no migration runs
	 */
	public static Config migrationConfig() {
		Config config = Scope.getCurrentScope().get(CONFIG_SCOPE_KEY, Config.class);
		if (config == null) {
			throw new IllegalStateException("No configuration in the Liquibase scope");
		}
		return config;
	}

	/**
	 * Migrates the database schema to the current version, if necessary (at startup, before the connection pools are
	 * opened).
	 *
	 * @param config the configuration (the database, and the settings the Java migrations read)
	 * @throws Exception if the schema can't be migrated
	 */
	public static void migrate(Config config) throws Exception {
		try {
			System.setProperty("liquibase.analytics.enabled", "false");
			Class.forName("org.postgresql.Driver");
			try (Connection connection = Database.openUnpooledConnection(config)) {
				liquibase.database.Database database = DatabaseFactory.getInstance()
					.findCorrectDatabaseImplementation(new JdbcConnection(connection));
				Scope.child(Map.of(CONFIG_SCOPE_KEY, config), () -> new CommandScope(UpdateCommandStep.COMMAND_NAME)
					.addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
					.addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, "migration/netshot0.xml")
					.addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY_OUTPUT, UpdateSummaryOutputEnum.LOG)
					.execute());
			}
		}
		catch (Exception e) {
			log.error("Unable to perform initial schema update", e);
			throw e;
		}
	}

	/**
	 * Gets the password used to encrypt the sensitive data (e.g. device credentials) in the DB.
	 * Unless {@code netshot.db.encryptionpassword.required} is enabled, defaults to {@code NETSHOT}.
	 *
	 * @param config the configuration
	 * @return the configured encryption password
	 * @throws IllegalStateException if the password is required but not configured
	 */
	public static String encryptionPassword(Config config) {
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
	 * @param config the configuration
	 * @return the connection
	 * @throws SQLException if the connection can't be opened
	 */
	public static Connection openUnpooledConnection(Config config) throws SQLException {
		return Settings.from(config).openUnpooledConnection();
	}

	/**
	 * Gets the properties of the connections to a database, given to the JDBC driver. On PostgreSQL, the sessions run
	 * without JIT compilation ({@code jit=off}): it doesn't speed up the short queries of Netshot, and compiling the
	 * plans of its large generated statements took longer than running them. An {@code options} parameter of the JDBC
	 * URL replaces this one.
	 *
	 * @param url the JDBC URL
	 * @param username the user name
	 * @param password the password
	 * @return the properties
	 */
	static Properties connectionProperties(String url, String username, String password) {
		Properties properties = new Properties();
		if (url != null && url.startsWith("jdbc:postgresql:")) {
			properties.setProperty("options", "-c jit=off");
		}
		if (username != null) {
			properties.setProperty("user", username);
		}
		if (password != null) {
			properties.setProperty("password", password);
		}
		return properties;
	}

	private Database() {
	}

}
