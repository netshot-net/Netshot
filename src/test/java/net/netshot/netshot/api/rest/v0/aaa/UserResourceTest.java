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

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Iterator;

import org.hibernate.Session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.NetshotApiClient;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.testing.ApiTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ApiTest
@DisplayName("User management API Tests")
public class UserResourceTest {

	/** API client of the current test, authenticated with the admin token. */
	private NetshotApiClient apiClient;

	@BeforeEach
	void createApiClient(NetshotApiClient client) {
		this.apiClient = client;
	}

	@AfterEach
	void cleanUpData() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session
				.createMutationQuery("delete from net.netshot.netshot.aaa.user.UiUser")
				.executeUpdate();
			session.getTransaction().commit();
		}
	}

	@Test
	@DisplayName("List users")
	@ResourceLock("DB")
	void listUsers() throws IOException, InterruptedException {
		{
			HttpResponse<JsonNode> response = apiClient.get("/users");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for initial list");
			Assertions.assertEquals(0, response.body().size(),
				"User list is not empty");
		}
		UiUser user1 = new UiUser("user1", true, "pass1");
		user1.setLevel(UiUser.LEVEL_EXECUTEREADWRITE);
		UiUser user2 = new UiUser("user2", false, "pass2");
		user2.setLevel(UiUser.LEVEL_READONLY);
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(user1);
			session.persist(user2);
			session.getTransaction().commit();
		}
		{
			HttpResponse<JsonNode> response = apiClient.get("/users");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for user list");
			Assertions.assertEquals(2, response.body().size(),
				"User list doesn't have 2 elements");
			Iterator<JsonNode> userNodeIt = response.body().iterator();
			JsonNode userNode1 = userNodeIt.next();
			Assertions.assertEquals(
				JsonNodeFactory.instance.objectNode()
					.put("id", user1.getId())
					.put("local", user1.isLocal())
					.put("username", user1.getUsername())
					.put("level", Long.valueOf(user1.getLevel())),
				userNode1,
				"Retrieved user doesn't match expected object");
			JsonNode userNode2 = userNodeIt.next();
			Assertions.assertEquals(
				JsonNodeFactory.instance.objectNode()
					.put("id", user2.getId())
					.put("local", user2.isLocal())
					.put("username", user2.getUsername())
					.put("level", Long.valueOf(user2.getLevel())),
				userNode2,
				"Retrieved user doesn't match expected object");
		}
	}


	@Test
	@DisplayName("Create user")
	@ResourceLock("DB")
	void createUser() throws IOException, InterruptedException {
		String password = "userpass";
		UiUser user = new UiUser("newuser", true, password);
		user.setLevel(UiUser.LEVEL_EXECUTEREADWRITE);
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("username", user.getUsername())
			.put("password", password)
			.put("level", Long.valueOf(user.getLevel()))
			.put("local", user.isLocal());
		HttpResponse<JsonNode> response = apiClient.post("/users", data);
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for created user");

		try (Session session = Database.getSession()) {
			UiUser newUser = session
				.createQuery("from net.netshot.netshot.aaa.user.UiUser u where u.username = :name", UiUser.class)
				.setParameter("name", user.getUsername())
				.uniqueResult();
			Assertions.assertEquals(user.getUsername(), newUser.getUsername(), "User not created as expected");
			Assertions.assertEquals(user.getLevel(), newUser.getLevel(), "User not created as expected");
			Assertions.assertEquals(user.isLocal(), newUser.isLocal(), "User not created as expected");
		}
	}

	@Test
	@DisplayName("Delete user")
	@ResourceLock("DB")
	void deleteUser() throws IOException, InterruptedException {
		UiUser user1 = new UiUser("user1", true, "pass1");
		user1.setLevel(UiUser.LEVEL_EXECUTEREADWRITE);
		UiUser user2 = new UiUser("user2", false, "pass2");
		user2.setLevel(UiUser.LEVEL_READONLY);
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(user1);
			session.persist(user2);
			session.getTransaction().commit();
		}
		HttpResponse<JsonNode> response = apiClient.delete(
			"/users/%d".formatted(user1.getId()));
		Assertions.assertEquals(
			Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
			"Not getting 204 response for user deletion");
		HttpResponse<JsonNode> listResponse = apiClient.get("/users");
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), listResponse.statusCode(),
			"Not getting 200 response for user listing");
		Assertions.assertEquals(1, listResponse.body().size(),
			"User list doesn't have 1 element");
		JsonNode userNode = listResponse.body().iterator().next();
		Assertions.assertEquals(
			JsonNodeFactory.instance.objectNode()
				.put("id", user2.getId())
				.put("local", user2.isLocal())
				.put("username", user2.getUsername())
				.put("level", Long.valueOf(user2.getLevel())),
			userNode,
			"Retrieved user doesn't match expected object");
	}

	@Test
	@DisplayName("Update user")
	@ResourceLock("DB")
	void updateUser() throws IOException, InterruptedException {
		UiUser user = new UiUser("user1", true, "pass1");
		user.setLevel(UiUser.LEVEL_EXECUTEREADWRITE);
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(user);
			session.getTransaction().commit();
		}
		UiUser targetUser = new UiUser("user2", true, "pass1");
		targetUser.setLevel(UiUser.LEVEL_READONLY);
		targetUser.setHashedPassword(user.getHashedPassword());
		targetUser.setId(user.getId());
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("local", targetUser.isLocal())
			.put("username", targetUser.getUsername())
			.put("level", Long.valueOf(targetUser.getLevel()));
		HttpResponse<JsonNode> response = apiClient.put(
			"/users/%d".formatted(user.getId()), data);
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), response.statusCode(),
			"Not getting 200 response for user update");
		try (Session session = Database.getSession()) {
			UiUser dbUser = session.byId(UiUser.class)
				.load(targetUser.getId());
			Assertions.assertEquals(targetUser.getName(), dbUser.getName(),
				"User not updated as expected");
			Assertions.assertEquals(targetUser.getLevel(), dbUser.getLevel(),
				"User not updated as expected");
		}
	}

	@Test
	@DisplayName("Update remote user")
	@ResourceLock("DB")
	void updateRemoteUser() throws IOException, InterruptedException {
		UiUser user = new UiUser("user1", false, "pass1");
		user.setLevel(UiUser.LEVEL_OPERATOR);
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(user);
			session.getTransaction().commit();
		}
		UiUser targetUser = new UiUser("user2", true, "pass1");
		targetUser.setLevel(UiUser.LEVEL_OPERATOR);
		targetUser.setHashedPassword(user.getHashedPassword());
		targetUser.setId(user.getId());
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("local", true)
			.put("username", targetUser.getUsername())
			.put("level", Long.valueOf(targetUser.getLevel()));
		{
			HttpResponse<JsonNode> response = apiClient.put(
				"/users/%d".formatted(user.getId()), data);
			Assertions.assertEquals(
				Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
				"Not getting 400 response for erroneous user update");
		}
		data.put("password", "pass2");
		{
			HttpResponse<JsonNode> response = apiClient.put(
				"/users/%d".formatted(user.getId()), data);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for user update");
		}
		try (Session session = Database.getSession()) {
			UiUser dbUser = session.byId(UiUser.class)
				.load(targetUser.getId());
			Assertions.assertEquals(targetUser.getName(), dbUser.getName(),
				"User not updated as expected");
			Assertions.assertEquals(targetUser.getLevel(), dbUser.getLevel(),
				"User not updated as expected");
			Assertions.assertEquals(targetUser.isLocal(), dbUser.isLocal(),
				"User not updated as expected");
		}
	}
}
