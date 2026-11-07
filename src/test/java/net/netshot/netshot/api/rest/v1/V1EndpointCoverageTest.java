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
package net.netshot.netshot.api.rest.v1;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.HttpMethod;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.api.V1Endpoints;

/**
 * Checks that every endpoint of the v1 API (every resource method of {@code api.rest.v1}) is declared as covered
 * by a test class ({@link V1Endpoints}, whose extension checks the class really calls it), and that no test class
 * declares an endpoint which doesn't exist.
 */
@UnitTest
@DisplayName("v1 endpoint coverage")
public class V1EndpointCoverageTest {

	private static final String ROOT_PACKAGE = "net.netshot.netshot";

	/**
	 * Normalizes an endpoint: single slashes, no trailing slash, path parameters as {@code {}}.
	 *
	 * @param method the HTTP method
	 * @param path the path template
	 * @return {@code "METHOD /path/{}"}
	 */
	static String key(String method, String path) {
		String normalized = ("/" + path).replaceAll("/+", "/").replaceAll("\\{[^}]*\\}", "{}");
		if (normalized.length() > 1 && normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return method + " " + normalized;
	}

	private static List<Class<?>> classes(boolean tests) throws IOException, URISyntaxException, ClassNotFoundException {
		List<Class<?>> classes = new ArrayList<>();
		String resource = ROOT_PACKAGE.replace('.', '/');
		Enumeration<URL> roots = V1EndpointCoverageTest.class.getClassLoader().getResources(resource);
		while (roots.hasMoreElements()) {
			URL root = roots.nextElement();
			if (!"file".equals(root.getProtocol())) {
				continue;
			}
			Path rootPath = Path.of(root.toURI());
			if (rootPath.toString().contains("test-classes") != tests) {
				continue;
			}
			Path classesDir = rootPath;
			for (int i = 0; i < ROOT_PACKAGE.split("\\.").length; i++) {
				classesDir = classesDir.getParent();
			}
			try (Stream<Path> files = Files.walk(rootPath)) {
				for (Path file : (Iterable<Path>) files::iterator) {
					String name = classesDir.relativize(file).toString();
					if (name.endsWith(".class") && !name.contains("$")) {
						classes.add(Class.forName(name.substring(0, name.length() - ".class".length()).replace('/', '.'), false,
							V1EndpointCoverageTest.class.getClassLoader()));
					}
				}
			}
		}
		return classes;
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
	 * Lists the endpoints of the v1 API.
	 *
	 * @return the keys of the resource methods
	 */
	static Set<String> endpoints() throws Exception {
		Set<String> keys = new TreeSet<>();
		for (Class<?> clazz : classes(false)) {
			if (!clazz.getName().startsWith(V1Application.class.getPackageName() + ".")) {
				continue;
			}
			jakarta.ws.rs.Path classPath = clazz.getAnnotation(jakarta.ws.rs.Path.class);
			if (classPath == null) {
				continue;
			}
			for (Method method : clazz.getDeclaredMethods()) {
				String httpMethod = httpMethodOf(method);
				if (httpMethod != null) {
					jakarta.ws.rs.Path methodPath = method.getAnnotation(jakarta.ws.rs.Path.class);
					keys.add(key(httpMethod, classPath.value() + "/" + (methodPath == null ? "" : methodPath.value())));
				}
			}
		}
		return keys;
	}

	@Test
	@DisplayName("Every v1 endpoint is covered by a test class, and every declared endpoint exists")
	void everyEndpointIsCovered() throws Exception {
		Set<String> endpoints = endpoints();
		Assertions.assertFalse(endpoints.isEmpty(), "No v1 resource method found, the scan is broken");
		Set<String> declared = new TreeSet<>();
		Set<String> unknown = new TreeSet<>();
		for (Class<?> testClass : classes(true)) {
			V1Endpoints annotation = testClass.getAnnotation(V1Endpoints.class);
			if (annotation == null) {
				continue;
			}
			for (String endpoint : annotation.value()) {
				String[] parts = endpoint.split(" ", 2);
				String key = key(parts[0], parts.length > 1 ? parts[1] : "");
				declared.add(key);
				if (!endpoints.contains(key)) {
					unknown.add(testClass.getSimpleName() + ": " + endpoint);
				}
			}
		}
		Set<String> missing = new TreeSet<>(endpoints);
		missing.removeAll(declared);
		Assertions.assertTrue(missing.isEmpty(), "v1 endpoints covered by no test class (declare them with @V1Endpoints "
			+ "on the test class calling them): " + missing);
		Assertions.assertTrue(unknown.isEmpty(), "Declared v1 endpoints which don't exist: " + unknown);
	}
}
