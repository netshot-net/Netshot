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
package net.netshot.netshot.api.rest.v0;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.net.JarURLConnection;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.HttpMethod;

import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.api.v0.V0ContractCase;
import net.netshot.netshot.testing.api.v0.V0ContractCases;
import net.netshot.netshot.testing.api.v0.V0ContractGolden;
import net.netshot.netshot.testing.contract.ContractSeed;

/**
 * Guard for the API contract tests: every JAX-RS resource method of the
 * application must be covered by a contract case (see {@link V0ContractCases}),
 * or listed, with a reason, in {@link #EXCLUSIONS}.
 *
 * <p>The resource methods are discovered by scanning the classes of the
 * application ({@code net.netshot.netshot} package and sub-packages, except the
 * v1 API) for the {@code @Path} annotation, so resources moved to other packages or classes
 * are still found. Cases are matched on the HTTP method and the full path
 * template (path parameter names do not matter).</p>
 *
 * <p>This test does not need any database or server.</p>
 */
@UnitTest
class V0ContractCoverageTest {

	/**
	 * Resource methods with no contract case, and why. The key is
	 * {@code "<HTTP method> <path template>"}, as in {@link V0ContractCase#key()}.
	 * There is no GET exclusion: every read endpoint has a case.
	 */
	private static final Map<String, String> EXCLUSIONS = Map.of();

	/** Cases which are not about a resource method of the application (third party resources). */
	private static final Set<String> EXTERNAL_CASES = Set.of();

	private static final String ROOT_PACKAGE = "net.netshot.netshot";

	/** The package of the v1 API, whose resources are not part of the legacy contract. */
	private static final String V1_PACKAGE = ROOT_PACKAGE + ".api.rest.v1.";

	/**
	 * Finds the "METHOD /path" keys of all the resource methods of the application.
	 * @return the keys
	 */
	static Set<String> findResourceMethods() throws IOException, URISyntaxException, ClassNotFoundException {
		Set<String> keys = new TreeSet<>();
		for (String className : findClassNames()) {
			if (className.startsWith(V1_PACKAGE)) {
				// The v1 API has its own contract tests
				continue;
			}
			Class<?> clazz = Class.forName(className, false, V0ContractCoverageTest.class.getClassLoader());
			jakarta.ws.rs.Path classPath = clazz.getAnnotation(jakarta.ws.rs.Path.class);
			if (classPath == null) {
				continue;
			}
			for (Method method : clazz.getDeclaredMethods()) {
				String httpMethod = httpMethodOf(method);
				if (httpMethod == null) {
					continue;
				}
				jakarta.ws.rs.Path methodPath = method.getAnnotation(jakarta.ws.rs.Path.class);
				String path = classPath.value() + "/" + (methodPath == null ? "" : methodPath.value());
				keys.add(httpMethod + " " + V0ContractCase.normalizeTemplate(path));
			}
		}
		return keys;
	}

	private static String httpMethodOf(Method method) {
		for (Annotation annotation : method.getAnnotations()) {
			HttpMethod httpMethod = annotation.annotationType().getAnnotation(HttpMethod.class);
			if (httpMethod != null) {
				return httpMethod.value();
			}
		}
		return null;
	}

	/**
	 * Lists the application classes found in the class path (directories and jars).
	 * @return the fully qualified class names
	 */
	private static List<String> findClassNames() throws IOException, URISyntaxException {
		List<String> names = new ArrayList<>();
		String resource = ROOT_PACKAGE.replace('.', '/');
		Enumeration<URL> roots = V0ContractCoverageTest.class.getClassLoader().getResources(resource);
		while (roots.hasMoreElements()) {
			URL root = roots.nextElement();
			if ("file".equals(root.getProtocol())) {
				Path rootPath = Path.of(root.toURI());
				if (rootPath.toString().contains("test-classes")) {
					continue;
				}
				Path classesDir = rootPath;
				for (int i = 0; i < ROOT_PACKAGE.split("\\.").length; i++) {
					classesDir = classesDir.getParent();
				}
				try (Stream<Path> files = Files.walk(rootPath)) {
					for (Path file : (Iterable<Path>) files::iterator) {
						String name = classesDir.relativize(file).toString();
						if (name.endsWith(".class") && !name.endsWith("module-info.class")) {
							names.add(name.substring(0, name.length() - ".class".length()).replace('/', '.').replace('\\', '.'));
						}
					}
				}
			}
			else if ("jar".equals(root.getProtocol())) {
				JarURLConnection connection = (JarURLConnection) root.openConnection();
				try (JarFile jar = connection.getJarFile()) {
					Enumeration<JarEntry> entries = jar.entries();
					while (entries.hasMoreElements()) {
						String name = entries.nextElement().getName();
						if (name.startsWith(resource + "/") && name.endsWith(".class")) {
							names.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
						}
					}
				}
			}
		}
		return names;
	}

	@Test
	@DisplayName("Every resource method has a contract case or a documented exclusion")
	void everyResourceMethodIsCovered() throws Exception {
		Set<String> resourceMethods = findResourceMethods();
		Assertions.assertFalse(resourceMethods.isEmpty(), "No JAX-RS resource method found, the scan is broken");
		Set<String> covered = new HashSet<>();
		for (V0ContractCase contractCase : V0ContractCases.all(new ContractSeed())) {
			covered.add(contractCase.key());
		}

		List<String> missing = new ArrayList<>();
		for (String key : resourceMethods) {
			if (!covered.contains(key) && !EXCLUSIONS.containsKey(key)) {
				missing.add(key);
			}
		}
		Assertions.assertTrue(missing.isEmpty(),
			"Resource methods with neither an API contract case nor an exclusion (add cases in "
				+ "V0ContractCases, or exclusions with a reason in V0ContractCoverageTest): " + missing);

		List<String> staleExclusions = new ArrayList<>();
		for (Map.Entry<String, String> exclusion : EXCLUSIONS.entrySet()) {
			if (!resourceMethods.contains(exclusion.getKey()) || covered.contains(exclusion.getKey())
				|| exclusion.getValue().isBlank() || exclusion.getKey().startsWith("GET ")) {
				staleExclusions.add(exclusion.getKey());
			}
		}
		Assertions.assertTrue(staleExclusions.isEmpty(),
			"Invalid exclusions (unknown or covered resource method, no reason, or GET): " + staleExclusions);
	}

	@Test
	@DisplayName("Every contract case targets an existing resource method")
	void everyCaseTargetsAResourceMethod() throws Exception {
		Set<String> resourceMethods = findResourceMethods();
		List<String> unknown = new ArrayList<>();
		for (V0ContractCase contractCase : V0ContractCases.all(new ContractSeed())) {
			if (!resourceMethods.contains(contractCase.key()) && !EXTERNAL_CASES.contains(contractCase.key())) {
				unknown.add(contractCase.key() + " (" + contractCase.fileName() + ")");
			}
		}
		Assertions.assertTrue(unknown.isEmpty(), "Cases for unknown resource methods: " + unknown);
	}

	@Test
	@DisplayName("Case names are unique and there is no orphan golden file")
	void goldenFilesMatchCases() throws IOException {
		Set<String> names = new HashSet<>();
		List<String> duplicates = new ArrayList<>();
		for (V0ContractCase contractCase : V0ContractCases.all(new ContractSeed())) {
			if (!names.add(contractCase.fileName())) {
				duplicates.add(contractCase.fileName());
			}
		}
		Assertions.assertTrue(duplicates.isEmpty(), "Duplicate case names: " + duplicates);

		Set<String> orphans = new TreeSet<>();
		if (Files.isDirectory(V0ContractGolden.DIR)) {
			try (Stream<Path> files = Files.list(V0ContractGolden.DIR)) {
				files.map(file -> file.getFileName().toString())
					.filter(name -> !name.equals(V0ContractGolden.STATUS_FILE) && !names.contains(name))
					.forEach(orphans::add);
			}
		}
		Assertions.assertTrue(orphans.isEmpty(), "Golden files without a case (stale?): " + orphans);

		Set<String> statusKeys = new TreeMap<String, String>(
			V0ContractGolden.readStatuses().entrySet().stream()
				.collect(java.util.stream.Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()))).keySet();
		Set<String> missingStatuses = new TreeSet<>(names);
		missingStatuses.removeAll(statusKeys);
		Assertions.assertTrue(missingStatuses.isEmpty(), "Cases without a recorded status: " + missingStatuses);
	}
}
