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
package net.netshot.netshot.testing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The PostgreSQL container shared by the whole test run, mirroring the static-singleton design of
 * {@link net.netshot.netshot.platform.database.Database}: started the first time a test class needs it,
 * kept in the root store of the JUnit extension context, and stopped when that store is closed at the end
 * of the run. Each test class works in its own schema.
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
			.computeIfAbsent(SharedPostgres.class, key -> new SharedPostgres(), SharedPostgres.class);
	}

	private final PostgreSQLContainer container;

	@SuppressWarnings("resource")
	private SharedPostgres() {
		this.container = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
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
		return this.buildConfig(schemaName);
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
		return this.buildConfig(schemaName);
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

	private Properties buildConfig(String schemaName) {
		Properties config = new Properties();
		config.setProperty("netshot.db.driver_class", "org.postgresql.Driver");
		config.setProperty("netshot.db.url", "jdbc:postgresql://%s:%d/%s?currentSchema=%s".formatted(
			this.container.getHost(), this.container.getFirstMappedPort(), this.container.getDatabaseName(), schemaName));
		config.setProperty("netshot.db.username", this.container.getUsername());
		config.setProperty("netshot.db.password", this.container.getPassword());
		return config;
	}

	@Override
	public void close() {
		this.container.stop();
	}
}
