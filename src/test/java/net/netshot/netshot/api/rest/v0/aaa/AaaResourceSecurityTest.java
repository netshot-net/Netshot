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
package net.netshot.netshot.api.rest.v0.aaa;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v0.system.ClusterResource;
import net.netshot.netshot.api.rest.v0.system.ServerInfoResource;
import net.netshot.netshot.testing.UnitTest;

/**
 * Pins the access control of the authentication, user, API token and system endpoints: the role
 * required by each method (as it was on the former {@code RestService}), and the paths they answer.
 */
@UnitTest
class AaaResourceSecurityTest {

	private static void collect(Map<String, String> into, Class<?> resource) {
		for (Method method : resource.getDeclaredMethods()) {
			Path path = method.getAnnotation(Path.class);
			if (path == null) {
				continue;
			}
			String verb = method.isAnnotationPresent(GET.class) ? "GET"
				: method.isAnnotationPresent(POST.class) ? "POST"
				: method.isAnnotationPresent(PUT.class) ? "PUT"
				: method.isAnnotationPresent(DELETE.class) ? "DELETE" : "?";
			RolesAllowed roles = method.getAnnotation(RolesAllowed.class);
			String access;
			if (roles != null) {
				Assertions.assertFalse(method.isAnnotationPresent(PermitAll.class), method.getName());
				access = String.join(",", roles.value());
			}
			else {
				Assertions.assertTrue(method.isAnnotationPresent(PermitAll.class), method.getName() + " has no access control at all");
				access = "PermitAll";
			}
			into.put(verb + " " + path.value() + " (" + method.getName() + ")", access);
		}
	}

	@Test
	@DisplayName("Each endpoint requires the same role as before the migration")
	void rolesAndPaths() {
		Map<String, String> actual = new TreeMap<>();
		for (Class<?> resource : new Class<?>[] {LoginResource.class, UserResource.class, ApiTokenResource.class,
			ClusterResource.class, ServerInfoResource.class}) {
			Assertions.assertEquals("/", resource.getAnnotation(Path.class).value(), resource.getSimpleName());
			collect(actual, resource);
		}
		Map<String, String> expected = new TreeMap<>();
		expected.put("POST /user (login)", "PermitAll");
		expected.put("GET /user (getUser)", User.ROLE_READONLY);
		expected.put("PUT /user/{id} (setSelfUser)", User.ROLE_READONLY);
		expected.put("DELETE /user/{id} (logout)", User.ROLE_READONLY);
		expected.put("GET /users (getUsers)", User.ROLE_ADMIN);
		expected.put("POST /users (addUser)", User.ROLE_ADMIN);
		expected.put("PUT /users/{id} (setUser)", User.ROLE_ADMIN);
		expected.put("DELETE /users/{id} (deleteUser)", User.ROLE_ADMIN);
		expected.put("GET /apitokens (getApiTokens)", User.ROLE_ADMIN);
		expected.put("POST /apitokens (addApiToken)", User.ROLE_ADMIN);
		expected.put("DELETE /apitokens/{id} (deleteApiToken)", User.ROLE_ADMIN);
		expected.put("GET /cluster/members (getClusterMembers)", User.ROLE_ADMIN);
		expected.put("GET /cluster/masterstatus (getClusterMasterStatus)", "PermitAll");
		expected.put("GET /serverinfo (getServerInfo)", User.ROLE_READONLY);
		Assertions.assertEquals(expected, actual);
	}
}
