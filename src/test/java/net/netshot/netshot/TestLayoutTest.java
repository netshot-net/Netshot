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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Rules on the layout of the tests: a test lives in the package of the code it tests (the root package only
 * holds the shared helpers and the tests of the whole application), and the simple names of the test
 * classes are unique (the expected files of DtoContract are stored under the simple name of the class).
 */
@DisplayName("Layout of the tests")
public class TestLayoutTest {

	private static final String ROOT = "net.netshot.netshot";

	/** Tests allowed in the root package, as they cover the whole application. */
	private static final Set<String> ROOT_TESTS = Set.of("ArchitectureTest", "NetshotConfigTest", "TestLayoutTest");

	private static List<JavaClass> testClasses;

	@BeforeAll
	static void importTests() {
		testClasses = new ClassFileImporter()
			.withImportOption(new ImportOption.OnlyIncludeTests())
			.importPackages(ROOT)
			.stream()
			.filter(JavaClass::isTopLevelClass)
			.filter(c -> c.getSimpleName().endsWith("Test"))
			.filter(c -> !c.getModifiers().contains(JavaModifier.ABSTRACT))
			.toList();
		Assertions.assertFalse(testClasses.isEmpty(), "No test class found");
	}

	@Test
	@DisplayName("Only the tests of the whole application are in the root package")
	void testsInFeaturePackages() {
		List<String> misplaced = testClasses.stream()
			.filter(c -> c.getPackageName().equals(ROOT))
			.map(JavaClass::getSimpleName)
			.filter(name -> !ROOT_TESTS.contains(name))
			.sorted()
			.toList();
		Assertions.assertEquals(List.of(), misplaced,
			"Test classes to move to the package of the code they test");
	}

	@Test
	@DisplayName("The simple names of the test classes are unique")
	void uniqueSimpleNames() {
		Map<String, List<String>> duplicates = testClasses.stream()
			.collect(Collectors.groupingBy(JavaClass::getSimpleName,
				Collectors.mapping(JavaClass::getName, Collectors.toList())))
			.entrySet().stream()
			.filter(e -> e.getValue().size() > 1)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		Assertions.assertEquals(Map.of(), duplicates, "Test classes sharing a simple name");
	}
}
