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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DatabaseTest.Boot;
import net.netshot.netshot.testing.TestDatabase;

@DatabaseTest(boot = Boot.MIGRATED)
@DisplayName("jOOQ schema")
class JooqSchemaTest {

	/** The committed generated classes (the tests run from the project directory). */
	private static final Path COMMITTED = Path.of("src", "main", "jooq");

	private static Map<String, String> sources(Path root) throws IOException {
		Map<String, String> sources = new TreeMap<>();
		if (!Files.isDirectory(root)) {
			return sources;
		}
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				sources.put(root.relativize(file).toString().replace('\\', '/'), Files.readString(file));
			}
		}
		return sources;
	}

	@Test
	@DisplayName("The committed jOOQ classes match the schema built by the Liquibase changelog")
	void committedClassesMatchSchema(TestDatabase database, @TempDir Path generated) throws Exception {
		Properties config = database.netshotConfig();
		JooqCodegen.generate(config.getProperty("netshot.db.url"), config.getProperty("netshot.db.username"),
			config.getProperty("netshot.db.password"), generated);
		Map<String, String> expected = sources(generated);
		Map<String, String> actual = sources(COMMITTED);
		List<String> differences = new ArrayList<>();
		for (Map.Entry<String, String> entry : expected.entrySet()) {
			String committed = actual.get(entry.getKey());
			if (committed == null) {
				differences.add("missing: " + entry.getKey());
			}
			else if (!committed.equals(entry.getValue())) {
				differences.add("different: " + entry.getKey());
			}
		}
		for (String path : actual.keySet()) {
			if (!expected.containsKey(path)) {
				differences.add("obsolete: " + path);
			}
		}
		Assertions.assertFalse(expected.isEmpty(), "Nothing was generated");
		Assertions.assertEquals(List.of(), differences,
			"The jOOQ classes under src/main/jooq don't match the changelog, regenerate them with "
				+ "'./mvnw -Pjooq-codegen process-test-classes' and commit the result");
	}
}
