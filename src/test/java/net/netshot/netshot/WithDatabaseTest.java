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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for DB-backed test classes. A single PostgreSQL container is
 * shared by every subclass (mirroring {@link net.netshot.netshot.platform.database.Database}'s
 * own static-singleton design): it is started once per JVM, in a static
 * initializer, the first time any subclass is loaded, and Testcontainers'
 * own Ryuk reaper container tears it down when the test JVM exits. Each
 * subclass gets its own schema within that container for isolation, similar
 * to how each used to get its own isolated in-memory H2 database.
 *
 * <p>This is deliberately not a {@code @Testcontainers}/{@code @Container}
 * managed field: that extension only starts/stops a container around the
 * lifecycle of the single class it is declared on (per test method, or per
 * class instance with {@code @TestInstance(PER_CLASS)}), which does not fit
 * a container meant to be shared, already running, across several unrelated
 * top-level test classes.</p>
 */
@SuppressWarnings("resource")
public abstract class WithDatabaseTest {

	private static final PostgreSQLContainer CONTAINER;

	static {
		CONTAINER = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
			.withUsername("netshot")
			.withPassword("netshot")
			.withDatabaseName("netshot");
		CONTAINER.start();
	}

	/**
	 * Builds a Netshot config pointing at a schema within the shared
	 * PostgreSQL container, creating it if it does not already exist. Since
	 * the schema is not dropped first, a schema name reused across multiple
	 * runs within the same live container (e.g. re-running a test in an IDE
	 * without restarting) keeps whatever data/state a previous run left in
	 * it - fine for most tests, which always run the full migration and
	 * manage their own data lifecycle, but not for tests that rely on the
	 * schema starting genuinely empty (see {@link #getFreshDatabaseConfig}).
	 *
	 * @param schemaName the schema to create (if needed) and use
	 * @return the Netshot config properties
	 */
	protected static Properties getDatabaseConfig(String schemaName) {
		try (Connection connection = openContainerConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA IF NOT EXISTS \"%s\"".formatted(schemaName));
		}
		catch (SQLException e) {
			throw new RuntimeException("Unable to create test schema '%s'".formatted(schemaName), e);
		}
		return buildConfig(schemaName);
	}

	/**
	 * Builds a Netshot config pointing at a schema within the shared
	 * PostgreSQL container, dropping it first if it already exists so the
	 * schema (including Liquibase's own DATABASECHANGELOG bookkeeping
	 * tables) always starts genuinely empty. Intended for tests that need
	 * that guarantee regardless of prior runs against the same live
	 * container - typically migration tests that apply the schema in
	 * stages and would otherwise get confused by a schema left mid-way (or
	 * fully) migrated by an earlier run.
	 *
	 * @param schemaName the schema to drop (if needed), recreate and use
	 * @return the Netshot config properties
	 */
	protected static Properties getFreshDatabaseConfig(String schemaName) {
		try (Connection connection = openContainerConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("DROP SCHEMA IF EXISTS \"%s\" CASCADE".formatted(schemaName));
			statement.execute("CREATE SCHEMA \"%s\"".formatted(schemaName));
		}
		catch (SQLException e) {
			throw new RuntimeException("Unable to create fresh test schema '%s'".formatted(schemaName), e);
		}
		return buildConfig(schemaName);
	}

	private static Connection openContainerConnection() throws SQLException {
		return DriverManager.getConnection(
			CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
	}

	private static Properties buildConfig(String schemaName) {
		Properties config = new Properties();
		config.setProperty("netshot.db.driver_class", "org.postgresql.Driver");
		config.setProperty("netshot.db.url", "jdbc:postgresql://%s:%d/%s?currentSchema=%s".formatted(
			CONTAINER.getHost(), CONTAINER.getFirstMappedPort(), CONTAINER.getDatabaseName(), schemaName));
		config.setProperty("netshot.db.username", CONTAINER.getUsername());
		config.setProperty("netshot.db.password", CONTAINER.getPassword());
		return config;
	}
}
