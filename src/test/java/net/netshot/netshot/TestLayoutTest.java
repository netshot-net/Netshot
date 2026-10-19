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

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import net.netshot.netshot.api.rest.RestServer;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.ContractTest;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestDatabase;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.UnitTest;

/**
 * Rules on the layout of the tests: a test lives in the package of the code it tests (the root package only
 * holds the tests of the whole application and TestConfig, the shared helpers are in the testing package),
 * the simple names of the test classes are unique (the expected files of DtoContract are stored under the
 * simple name of the class), and each test class has a kind (several only among the kinds needing Docker),
 * which the unit and script engine tests respect (they run without Docker).
 */
@UnitTest
@DisplayName("Layout of the tests")
public class TestLayoutTest {

	private static final String ROOT = "net.netshot.netshot";

	/** Tests allowed in the root package, as they cover the whole application. */
	private static final Set<String> ROOT_TESTS = Set.of("ArchitectureTest", "NetshotConfigTest", "TestLayoutTest");

	/** Helpers allowed in the root package: TestConfig calls the protected Netshot.initConfig. */
	private static final Set<String> ROOT_HELPERS = Set.of("TestConfig");

	/** The annotations giving the kind of a test class (see the {@code testing} package). */
	private static final List<Class<? extends Annotation>> KINDS = List.of(
		UnitTest.class, ScriptEngineTest.class, DatabaseTest.class, ApiTest.class, ContractTest.class, SimulatorTest.class);

	/** The kinds of tests run by the {@code fast} Maven profile, without Docker. */
	private static final List<Class<? extends Annotation>> FAST_KINDS = List.of(UnitTest.class, ScriptEngineTest.class);

	/** The classes the tests of the fast kinds must not call: they would need a database, a server or a container. */
	private static final Set<String> SLOW_CLASSES = Set.of(
		Transactions.class.getName(), Wiring.class.getName(), TaskManager.class.getName(), RestServer.class.getName(),
		TestDatabase.class.getName(), TestServer.class.getName(), DeviceSimulators.class.getName());

	/** The methods of {@link Database} which need a database (the others only read the configuration). */
	private static final Set<String> DATABASE_METHODS = Set.of(
		"update", "init", "getSession", "getSystemSession", "getStalessSession", "getConnection");

	/** The package of the containers, which the tests of the fast kinds must not use. */
	private static final String CONTAINER_PACKAGE = "org.testcontainers";

	private static JavaClasses importedClasses;

	private static List<JavaClass> testClasses;

	@BeforeAll
	static void importTests() {
		importedClasses = new ClassFileImporter()
			.withImportOption(new ImportOption.OnlyIncludeTests())
			.importPackages(ROOT);
		testClasses = importedClasses
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
	@DisplayName("The shared helpers are in the testing package")
	void helpersInTestingPackage() {
		List<String> misplaced = importedClasses.stream()
			.filter(JavaClass::isTopLevelClass)
			.filter(c -> c.getPackageName().equals(ROOT))
			.map(JavaClass::getSimpleName)
			.filter(name -> !ROOT_TESTS.contains(name) && !ROOT_HELPERS.contains(name))
			.sorted()
			.toList();
		Assertions.assertEquals(List.of(), misplaced,
			"Helpers to move to net.netshot.netshot.testing (or one of its packages)");
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

	@Test
	@DisplayName("Each test class has a kind, the fast kinds alone")
	void kindOfEachClass() {
		Map<String, List<String>> wrong = new TreeMap<>();
		for (JavaClass testClass : testClasses) {
			List<String> kinds = KINDS.stream()
				.filter(testClass::isAnnotatedWith)
				.map(Class::getSimpleName)
				.toList();
			boolean fast = FAST_KINDS.stream().anyMatch(testClass::isAnnotatedWith);
			if (kinds.isEmpty() || fast && kinds.size() > 1) {
				wrong.put(testClass.getName(), kinds);
			}
		}
		Assertions.assertEquals(Map.of(), wrong,
			"Test classes to annotate with one of %s (several only among the Docker ones, such as @DatabaseTest and @SimulatorTest)"
				.formatted(KINDS.stream().map(Class::getSimpleName).toList()));
	}

	@Test
	@DisplayName("The unit and script engine tests use no database, server nor container")
	void fastTestsUseNoInfrastructure() {
		List<String> violations = importedClasses.stream()
			.filter(c -> FAST_KINDS.stream().anyMatch(topLevel(c)::isAnnotatedWith))
			.flatMap(c -> c.getAccessesFromSelf().stream())
			.filter(TestLayoutTest::isSlow)
			.map(a -> "%s -> %s.%s".formatted(topLevel(a.getOriginOwner()).getSimpleName(), a.getTargetOwner().getName(), a.getName()))
			.distinct()
			.sorted()
			.toList();
		Assertions.assertEquals(List.of(), violations,
			"@UnitTest/@ScriptEngineTest classes needing a database, a server or a container: give them the kind they need");
	}

	/**
	 * Tells whether an access (call, field access, construction) needs a database, a server or a container.
	 *
	 * @param access the access
	 * @return true if the tests of the fast kinds must not make it
	 */
	private static boolean isSlow(JavaAccess<?> access) {
		JavaClass owner = access.getTargetOwner();
		if (owner.getName().equals(Database.class.getName())) {
			return DATABASE_METHODS.contains(access.getName());
		}
		return SLOW_CLASSES.contains(owner.getName()) || owner.getPackageName().startsWith(CONTAINER_PACKAGE);
	}

	/**
	 * Gets the top-level class enclosing a class (or the class itself).
	 *
	 * @param javaClass the class
	 * @return the top-level class
	 */
	private static JavaClass topLevel(JavaClass javaClass) {
		JavaClass current = javaClass;
		Optional<JavaClass> enclosing = current.getEnclosingClass();
		while (enclosing.isPresent()) {
			current = enclosing.get();
			enclosing = current.getEnclosingClass();
		}
		return current;
	}
}
