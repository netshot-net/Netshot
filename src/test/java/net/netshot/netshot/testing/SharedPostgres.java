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
package net.netshot.netshot.testing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.platform.database.Database;

/**
 * The PostgreSQL container shared by the whole test run, mirroring the static-singleton design of
 * {@link net.netshot.netshot.platform.database.Database}: started the first time a test class needs it,
 * kept in the root store of the JUnit extension context, and stopped when that store is closed at the end
 * of the run. Each booted test class works in its own database, cloned from a template database migrated
 * once per run (a clone takes a few tens of milliseconds, a migration about a second); the tests driving
 * the schema themselves ({@link DatabaseTest.Boot#MANUAL}) work in schemas of the main database.
 */
final class SharedPostgres implements AutoCloseable {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(SharedPostgres.class);

	/**
	 * Gets the shared container, starting it the first time.
	 *
	 * @param context any extension context of the run
	 * @return the shared container
	 */
	static SharedPostgres get(ExtensionContext context) {
		return context.getRoot().getStore(NAMESPACE)
			.computeIfAbsent(SharedPostgres.class, _ -> new SharedPostgres(), SharedPostgres.class);
	}

	/** Database migrated once per run, which the database of each test class is a copy of. */
	private static final String TEMPLATE_DATABASE = "netshot_template";

	/** The default PostgreSQL image of the tests: the oldest supported version. */
	static final String DEFAULT_IMAGE = "postgres:15";

	/**
	 * Gets the PostgreSQL image of the tests: the oldest supported version by default, another one with
	 * {@code -Dnetshot.test.postgres=postgres:18} (the CI also runs the database tests on the latest version).
	 *
	 * @return the image name
	 */
	static String image() {
		return System.getProperty("netshot.test.postgres", DEFAULT_IMAGE);
	}

	private final PostgreSQLContainer container;

	private boolean templateMigrated;

	@SuppressWarnings("resource")
	private SharedPostgres() {
		this.container = new PostgreSQLContainer(DockerImageName.parse(image()))
			.withUsername("netshot")
			.withPassword("netshot")
			.withDatabaseName("netshot");
		this.container.start();
	}

	/**
	 * Builds a Netshot configuration pointing at a schema, creating the schema if it does not exist. A
	 * schema reused across runs within the same live container keeps what a previous run left in it.
	 *
	 * @param schemaName the schema
	 * @return the database properties of the Netshot configuration
	 */
	Properties config(String schemaName) {
		this.execute("CREATE SCHEMA IF NOT EXISTS \"%s\"".formatted(schemaName));
		return this.buildConfig(this.container.getDatabaseName(), schemaName);
	}

	/**
	 * Builds a Netshot configuration pointing at a schema, dropping it first if it exists, so that the
	 * schema (including the Liquibase bookkeeping tables) always starts empty.
	 *
	 * @param schemaName the schema
	 * @return the database properties of the Netshot configuration
	 */
	Properties freshConfig(String schemaName) {
		this.execute("DROP SCHEMA IF EXISTS \"%s\" CASCADE".formatted(schemaName));
		this.execute("CREATE SCHEMA \"%s\"".formatted(schemaName));
		return this.buildConfig(this.container.getDatabaseName(), schemaName);
	}

	/**
	 * Builds a Netshot configuration pointing at a database of its own, a copy of the migrated template
	 * (migrated first if it is the first copy of the run). A database left by a previous run within the
	 * same live container is dropped first, so that the copy always starts as the template.
	 *
	 * @param databaseName the database
	 * @return the database properties of the Netshot configuration
	 * @throws Exception if the template cannot be migrated
	 */
	Properties cloneConfig(String databaseName) throws Exception {
		if (!this.templateMigrated) {
			this.execute("DROP DATABASE IF EXISTS \"%s\"".formatted(TEMPLATE_DATABASE));
			this.execute("CREATE DATABASE \"%s\"".formatted(TEMPLATE_DATABASE));
			Properties config = this.buildConfig(TEMPLATE_DATABASE, "public");
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "WARN");
			TestConfig.init(config);
			// Plain connection, closed after the update: nothing stays connected to the template
			Database.update();
			this.templateMigrated = true;
		}
		this.execute("DROP DATABASE IF EXISTS \"%s\"".formatted(databaseName));
		this.execute("CREATE DATABASE \"%s\" TEMPLATE \"%s\" STRATEGY FILE_COPY".formatted(databaseName, TEMPLATE_DATABASE));
		return this.buildConfig(databaseName, "public");
	}

	private void execute(String sql) {
		try (Connection connection = DriverManager.getConnection(
				this.container.getJdbcUrl(), this.container.getUsername(), this.container.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
		catch (SQLException e) {
			throw new IllegalStateException("Unable to run '%s' on the test database".formatted(sql), e);
		}
	}

	private Properties buildConfig(String databaseName, String schemaName) {
		Properties config = new Properties();
		config.setProperty("netshot.db.driver_class", "org.postgresql.Driver");
		config.setProperty("netshot.db.url", "jdbc:postgresql://%s:%d/%s?currentSchema=%s".formatted(
			this.container.getHost(), this.container.getFirstMappedPort(), databaseName, schemaName));
		config.setProperty("netshot.db.username", this.container.getUsername());
		config.setProperty("netshot.db.password", this.container.getPassword());
		return config;
	}

	@Override
	public void close() {
		this.container.stop();
	}
}
