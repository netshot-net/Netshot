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

import java.nio.file.Path;
import java.util.Properties;

import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.ForcedType;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.testing.TestDatabase;

/**
 * Generates the jOOQ classes of the schema (committed under {@code src/main/jooq}).
 *
 * <p>Run after a change of the Liquibase changelog: {@code ./mvnw -Pjooq-codegen process-test-classes}
 * starts a PostgreSQL container, migrates it with {@link Database#update()} and generates the classes
 * from it. {@code JooqSchemaTest} fails when the committed classes don't match the changelog.</p>
 */
public final class JooqCodegen {

	/** The package of the generated classes. */
	public static final String PACKAGE = "net.netshot.netshot.platform.database.schema";

	private JooqCodegen() {
		// Static helpers only
	}

	/**
	 * Generates the classes from a migrated database, replacing the content of the target directory.
	 *
	 * @param url the JDBC URL of the database
	 * @param username the user name
	 * @param password the password
	 * @param directory the target source directory
	 * @throws Exception on error
	 */
	public static void generate(String url, String username, String password, Path directory) throws Exception {
		Configuration configuration = new Configuration()
			.withJdbc(new Jdbc()
				.withDriver("org.postgresql.Driver")
				.withUrl(url)
				.withUser(username)
				.withPassword(password))
			.withGenerator(new Generator()
				.withDatabase(new org.jooq.meta.jaxb.Database()
					.withName("org.jooq.meta.postgres.PostgresDatabase")
					.withInputSchema("public")
					// Not bound to a schema: the tables are found through the search path
					.withOutputSchemaToDefault(true)
					.withExcludes("databasechangelog|databasechangeloglock")
					.withForcedTypes(
						new ForcedType()
							.withUserType("java.net.InetAddress")
							.withBinding(InetBinding.class.getName())
							.withIncludeTypes("inet"),
						new ForcedType()
							.withUserType("java.time.Instant")
							.withConverter(UtcInstantConverter.class.getName())
							.withIncludeTypes("timestamp(\\(\\d+\\))?( without time zone)?")))
				.withGenerate(new Generate()
					.withGeneratedAnnotation(false)
					.withJavaTimeTypes(true)
					.withRecords(true)
					.withPojos(false)
					.withDaos(false)
					.withSequences(false))
				.withTarget(new Target()
					.withPackageName(PACKAGE)
					.withDirectory(directory.toString())
					.withClean(true)));
		GenerationTool.generate(configuration);
	}

	/**
	 * Migrates a PostgreSQL in a container and generates the classes into the given directory.
	 *
	 * @param args the target source directory
	 * @throws Exception on error
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			throw new IllegalArgumentException("Usage: JooqCodegen <target source directory>");
		}
		try (PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse(TestDatabase.postgresImage()))) {
			container.start();
			Properties config = new Properties();
			config.setProperty("netshot.db.driver_class", "org.postgresql.Driver");
			config.setProperty("netshot.db.url", container.getJdbcUrl());
			config.setProperty("netshot.db.username", container.getUsername());
			config.setProperty("netshot.db.password", container.getPassword());
			config.setProperty("netshot.log.file", "CONSOLE");
			config.setProperty("netshot.log.level", "WARN");
			TestConfig.init(config);
			Database.update();
			JooqCodegen.generate(container.getJdbcUrl(), container.getUsername(), container.getPassword(), Path.of(args[0]));
		}
	}
}
