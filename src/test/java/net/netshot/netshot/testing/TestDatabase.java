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

import java.nio.file.Path;
import java.util.Properties;

/**
 * The database of a {@link DatabaseTest} (or {@link ApiTest}, {@link ContractTest}) class, which the test
 * methods (including {@code @BeforeAll} and {@code @BeforeEach}) can receive as a parameter.
 */
public final class TestDatabase {

	private final SharedPostgres postgres;

	private final String name;

	private final Path binaryFolder;

	private Properties netshotConfig;

	/**
	 * Creates the database of a test class.
	 *
	 * @param postgres the shared container
	 * @param name the name of the database of the class
	 * @param binaryFolder the folder of the binary snapshot files of the class
	 */
	TestDatabase(SharedPostgres postgres, String name, Path binaryFolder) {
		this.postgres = postgres;
		this.name = name;
		this.binaryFolder = binaryFolder;
	}

	/**
	 * Gets the PostgreSQL image of the tests: the oldest supported version by default, another one with
	 * {@code -Dnetshot.test.postgres=postgres:18}.
	 *
	 * @return the image name
	 */
	public static String postgresImage() {
		return SharedPostgres.image();
	}

	/**
	 * Gets the name of the database of the test class (a copy of the migrated template, see
	 * {@link DatabaseTest}).
	 *
	 * @return the name of the database
	 */
	public String name() {
		return this.name;
	}

	/**
	 * Gets the temporary folder set as {@code netshot.snapshots.binary.path}, deleted after the class.
	 *
	 * @return the folder
	 */
	public Path binaryFolder() {
		return this.binaryFolder;
	}

	/**
	 * Gets a copy of the Netshot configuration set for the test class, to change some properties and set
	 * it again ({@code TestConfig.init}).
	 *
	 * @return a copy of the configuration
	 */
	public Properties netshotConfig() {
		if (this.netshotConfig == null) {
			throw new IllegalStateException("No configuration was set for the class (Boot.MANUAL)");
		}
		Properties copy = new Properties();
		copy.putAll(this.netshotConfig);
		return copy;
	}

	/**
	 * Records the Netshot configuration set for the test class.
	 *
	 * @param config the configuration
	 */
	void setNetshotConfig(Properties config) {
		this.netshotConfig = config;
	}

	/**
	 * Builds the database properties of a Netshot configuration pointing at a schema of the main database
	 * (for the tests with {@link DatabaseTest.Boot#MANUAL}), creating the schema if it does not exist.
	 *
	 * @param schemaName the schema
	 * @return the properties
	 */
	public Properties config(String schemaName) {
		return this.postgres.config(schemaName);
	}

	/**
	 * Builds the database properties of a Netshot configuration pointing at a schema of the main database
	 * (for the tests with {@link DatabaseTest.Boot#MANUAL}), dropping the schema first so that it starts empty.
	 *
	 * @param schemaName the schema
	 * @return the properties
	 */
	public Properties freshConfig(String schemaName) {
		return this.postgres.freshConfig(schemaName);
	}
}
