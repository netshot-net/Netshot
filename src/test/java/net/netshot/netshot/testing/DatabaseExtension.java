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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.testing.DatabaseTest.Boot;

/**
 * Extension of {@link DatabaseTest}, {@link ApiTest} and {@link ContractTest}: before the annotated class,
 * creates its database in the shared PostgreSQL container (a copy of the migrated template) and boots
 * Netshot on it (see
 * {@link DatabaseTest}); after it, deletes the folder of the binary snapshot files. Resolves the
 * {@link TestDatabase} parameters.
 */
public final class DatabaseExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		Class<?> testClass = context.getRequiredTestClass();
		if (!Kinds.usesDatabase(testClass)) {
			return;
		}
		SharedPostgres postgres = SharedPostgres.get(context);
		String name = databaseName(testClass);
		Path binaryFolder = Files.createTempDirectory("netshot-" + name + "-");
		TestDatabase database = new TestDatabase(postgres, name, binaryFolder);
		context.getStore(Kinds.NAMESPACE).put(TestDatabase.class, database);

		Boot boot = Kinds.usesRestServer(testClass) ? Boot.WIRED
			: AnnotationSupport.findAnnotation(testClass, DatabaseTest.class).map(DatabaseTest::boot).orElse(Boot.WIRED);
		if (boot == Boot.MANUAL) {
			return;
		}
		Properties base = postgres.cloneConfig(name);
		base.setProperty("netshot.snapshots.binary.path", binaryFolder.toString());
		if (Kinds.usesRestServer(testClass)) {
			TestServer server = new TestServer(TestNetwork.freeLocalApiUrl());
			base.setProperty("netshot.http.ssl.enabled", "false");
			base.setProperty("netshot.http.baseurl", server.baseUrl());
			context.getStore(Kinds.NAMESPACE).put(TestServer.class, server);
		}
		Properties config = Kinds.config(testClass, base);
		database.setNetshotConfig(config);
		TestConfig.init(config);
		TestDrivers.reload();
		if (boot == Boot.WIRED) {
			Wiring.init(TestDrivers.registry());
		}
	}

	@Override
	public void afterAll(ExtensionContext context) throws IOException {
		if (!Kinds.usesDatabase(context.getRequiredTestClass())) {
			return;
		}
		TestDatabase database = context.getStore(Kinds.NAMESPACE).remove(TestDatabase.class, TestDatabase.class);
		if (database != null) {
			try (Stream<Path> files = Files.walk(database.binaryFolder())) {
				files.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
			}
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
		return parameterContext.getParameter().getType() == TestDatabase.class;
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
		TestDatabase database = extensionContext.getStore(Kinds.NAMESPACE).get(TestDatabase.class, TestDatabase.class);
		if (database == null) {
			throw new IllegalStateException("No test database: annotate the test class with @DatabaseTest");
		}
		return database;
	}

	/**
	 * Gets the name of the database of a test class: its name without the package, in lower case, the names
	 * of the nested classes separated by underscores. Unique since the simple names of the test classes are.
	 *
	 * @param testClass the test class
	 * @return the name of the database
	 */
	static String databaseName(Class<?> testClass) {
		String name = testClass.getName().substring(testClass.getPackageName().length() + 1);
		return name.replace('$', '_').toLowerCase(Locale.ROOT);
	}
}
