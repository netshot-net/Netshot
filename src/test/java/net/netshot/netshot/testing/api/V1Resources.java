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
package net.netshot.netshot.testing.api;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Produces;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.Unpaged;

/**
 * The endpoints of the v1 API, found by scanning its resource classes (the resource methods of
 * {@code net.netshot.netshot.api.rest.v1}), for the tests which check every endpoint.
 */
public final class V1Resources {

	private static final String V1_PACKAGE = "net.netshot.netshot.api.rest.v1";

	/** The value given to the ID path parameters (no such object). */
	public static final String UNKNOWN_ID = "99999999";

	/**
	 * An endpoint.
	 *
	 * @param httpMethod the HTTP method
	 * @param template the path template, under {@code /api/v1} ({@code /devices/{id}})
	 * @param method the resource method
	 */
	public record Endpoint(String httpMethod, String template, Method method) {

		/**
		 * Gets the key of the endpoint, as in {@link V1Endpoints}.
		 *
		 * @return {@code "METHOD /path/{param}"}
		 */
		public String key() {
			return this.httpMethod + " " + this.template;
		}

		/**
		 * Tells whether the path has parameters.
		 *
		 * @return true for {@code /devices/{id}...}
		 */
		public boolean hasPathParameters() {
			return this.template.contains("{");
		}

		/**
		 * Builds a path of the endpoint, with an unknown ID for every path parameter.
		 *
		 * @return the path
		 */
		public String unknownPath() {
			return this.template.replaceAll("\\{[^}]*\\}", UNKNOWN_ID);
		}

		/**
		 * Gets the role the endpoint requires.
		 *
		 * @return the role, or null if the endpoint is public
		 */
		public User.Role role() {
			if (this.method.isAnnotationPresent(PermitAll.class)) {
				return null;
			}
			RolesAllowed allowed = this.method.getAnnotation(RolesAllowed.class);
			if (allowed == null) {
				if (this.method.getDeclaringClass().isAnnotationPresent(PermitAll.class)) {
					return null;
				}
				allowed = this.method.getDeclaringClass().getAnnotation(RolesAllowed.class);
			}
			if (allowed == null) {
				return null;
			}
			User.Role lowest = null;
			for (String name : allowed.value()) {
				for (User.Role role : User.Role.values()) {
					if (role.getName().equals(name) && (lowest == null || role.getLevel() < lowest.getLevel())) {
						lowest = role;
					}
				}
			}
			return lowest;
		}

		/**
		 * Tells whether the endpoint answers a list (the list envelope).
		 *
		 * @return true for a list
		 */
		public boolean isList() {
			return "GET".equals(this.httpMethod) && this.method.getReturnType().getSimpleName().equals("ListResponse");
		}

		/**
		 * Tells whether the endpoint is a list given whole in a single page ({@link Unpaged}).
		 *
		 * @return true for an unpaged list
		 */
		public boolean isUnpaged() {
			return this.method.isAnnotationPresent(Unpaged.class);
		}

		/**
		 * Tells whether the endpoint answers statistics.
		 *
		 * @return true for a {@code /stats} endpoint
		 */
		public boolean isStats() {
			return "GET".equals(this.httpMethod) && this.method.getReturnType().getSimpleName().equals("StatsResponse");
		}

		/**
		 * Gets the media type to ask for (the first the endpoint produces).
		 *
		 * @return the media type, JSON by default
		 */
		public String accept() {
			Produces produces = this.method.getAnnotation(Produces.class);
			return produces == null || produces.value().length == 0 ? "application/json" : produces.value()[0];
		}

		/**
		 * Gets the media type of a request body (merge patch for a PATCH which takes it, JSON otherwise).
		 *
		 * @return the media type
		 */
		public String contentType() {
			Consumes consumes = this.method.getAnnotation(Consumes.class);
			if (consumes == null || consumes.value().length == 0) {
				return "application/json";
			}
			return consumes.value()[0];
		}

		@Override
		public String toString() {
			return this.key();
		}
	}

	private V1Resources() {
	}

	/**
	 * Lists the endpoints of the v1 API.
	 *
	 * @return the endpoints, sorted by path then method
	 * @throws IOException if the classes can't be listed
	 */
	public static List<Endpoint> endpoints() throws IOException {
		List<Endpoint> endpoints = new ArrayList<>();
		for (Class<?> clazz : classes()) {
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
				String template = ("/" + classPath.value() + "/" + (methodPath == null ? "" : methodPath.value()))
					.replaceAll("/+", "/");
				if (template.length() > 1 && template.endsWith("/")) {
					template = template.substring(0, template.length() - 1);
				}
				endpoints.add(new Endpoint(httpMethod, template, method));
			}
		}
		endpoints.sort(Comparator.comparing(Endpoint::template).thenComparing(Endpoint::httpMethod));
		return endpoints;
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

	private static List<Class<?>> classes() throws IOException {
		List<Class<?>> classes = new ArrayList<>();
		String resource = V1_PACKAGE.replace('.', '/');
		Enumeration<URL> roots = V1Resources.class.getClassLoader().getResources(resource);
		while (roots.hasMoreElements()) {
			URL root = roots.nextElement();
			if (!"file".equals(root.getProtocol())) {
				continue;
			}
			Path rootPath;
			try {
				rootPath = Path.of(root.toURI());
			}
			catch (URISyntaxException e) {
				throw new IOException(e);
			}
			if (rootPath.toString().contains("test-classes")) {
				continue;
			}
			Path classesDir = rootPath;
			for (int i = 0; i < V1_PACKAGE.split("\\.").length; i++) {
				classesDir = classesDir.getParent();
			}
			try (Stream<Path> files = Files.walk(rootPath)) {
				for (Path file : (Iterable<Path>) files::iterator) {
					String name = classesDir.relativize(file).toString();
					if (name.endsWith(".class") && !name.contains("$")) {
						try {
							classes.add(Class.forName(name.substring(0, name.length() - ".class".length()).replace('/', '.'), false,
								V1Resources.class.getClassLoader()));
						}
						catch (ClassNotFoundException e) {
							throw new IOException(e);
						}
					}
				}
			}
		}
		return classes;
	}
}
