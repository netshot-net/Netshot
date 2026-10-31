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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Checks, after a test class annotated with {@link V1Endpoints}, that the class called each endpoint it declares
 * (with any status: a 404 or a 400 from the resource counts, as the resource method ran).
 */
public class V1EndpointsExtension implements BeforeAllCallback, AfterAllCallback {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(V1EndpointsExtension.class);

	/**
	 * Builds the pattern matching the requests of an endpoint.
	 *
	 * @param endpoint {@code "METHOD /path/{param}"}
	 * @return the pattern of {@code "METHOD /path/value"}
	 */
	static Pattern pattern(String endpoint) {
		StringBuilder regex = new StringBuilder();
		for (String part : endpoint.split("\\{[^}]*\\}", -1)) {
			if (!regex.isEmpty()) {
				regex.append("[^/]+");
			}
			regex.append(Pattern.quote(part));
		}
		return Pattern.compile(regex.toString());
	}

	@Override
	public void beforeAll(ExtensionContext context) {
		context.getStore(NAMESPACE).put("start", V1Client.requests().size());
	}

	@Override
	public void afterAll(ExtensionContext context) {
		V1Endpoints declared = context.getRequiredTestClass().getAnnotation(V1Endpoints.class);
		Integer start = context.getStore(NAMESPACE).get("start", Integer.class);
		if (declared == null || start == null) {
			return;
		}
		List<String> requests = V1Client.requests();
		List<String> sent = requests.subList(Math.min(start, requests.size()), requests.size());
		List<String> missing = new ArrayList<>();
		for (String endpoint : declared.value()) {
			Pattern pattern = pattern(endpoint);
			if (sent.stream().noneMatch(r -> pattern.matcher(r).matches())) {
				missing.add(endpoint);
			}
		}
		Assertions.assertTrue(missing.isEmpty(), context.getRequiredTestClass().getSimpleName()
			+ " declares v1 endpoints it never called: " + missing);
	}
}
