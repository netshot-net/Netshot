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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.UserRepository;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.api.NetshotApiClient;
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

	private final Transactions transactions = new Transactions();

	private final UserRepository repository = new UserRepository();

	@AfterEach
	void cleanUpData() {
		this.transactions.runDsl(dsl -> dsl.deleteFrom(Tables.USER).execute());
	}

	private UiUser store(UiUser user) {
		return user.withId(this.transactions.writeDsl(dsl -> this.repository.add(dsl, user)));
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
		UiUser user1 = UiUser.withPassword("user1", true, UiUser.LEVEL_EXECUTEREADWRITE, "pass1");
		UiUser user2 = UiUser.withPassword("user2", false, UiUser.LEVEL_READONLY, "pass2");
		user1 = this.store(user1);
		user2 = this.store(user2);
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
					.put("id", user1.id())
					.put("local", user1.local())
					.put("username", user1.username())
					.put("level", Long.valueOf(user1.level())),
				userNode1,
				"Retrieved user doesn't match expected object");
			JsonNode userNode2 = userNodeIt.next();
			Assertions.assertEquals(
				JsonNodeFactory.instance.objectNode()
					.put("id", user2.id())
					.put("local", user2.local())
					.put("username", user2.username())
					.put("level", Long.valueOf(user2.level())),
				userNode2,
				"Retrieved user doesn't match expected object");
		}
	}


	@Test
	@DisplayName("Create user")
	@ResourceLock("DB")
	void createUser() throws IOException, InterruptedException {
		String password = "userpass";
		UiUser user = UiUser.withPassword("newuser", true, UiUser.LEVEL_EXECUTEREADWRITE, password);
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("username", user.username())
			.put("password", password)
			.put("level", Long.valueOf(user.level()))
			.put("local", user.local());
		HttpResponse<JsonNode> response = apiClient.post("/users", data);
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for created user");

		{
			UiUser newUser = this.transactions.readDsl(dsl -> this.repository.findByUsername(dsl, user.username()));
			Assertions.assertEquals(user.username(), newUser.username(), "User not created as expected");
			Assertions.assertEquals(user.level(), newUser.level(), "User not created as expected");
			Assertions.assertEquals(user.local(), newUser.local(), "User not created as expected");
		}
	}

	@Test
	@DisplayName("Delete user")
	@ResourceLock("DB")
	void deleteUser() throws IOException, InterruptedException {
		UiUser user1 = UiUser.withPassword("user1", true, UiUser.LEVEL_EXECUTEREADWRITE, "pass1");
		UiUser user2 = UiUser.withPassword("user2", false, UiUser.LEVEL_READONLY, "pass2");
		user1 = this.store(user1);
		user2 = this.store(user2);
		HttpResponse<JsonNode> response = apiClient.delete(
			"/users/%d".formatted(user1.id()));
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
				.put("id", user2.id())
				.put("local", user2.local())
				.put("username", user2.username())
				.put("level", Long.valueOf(user2.level())),
			userNode,
			"Retrieved user doesn't match expected object");
	}

	@Test
	@DisplayName("Update user")
	@ResourceLock("DB")
	void updateUser() throws IOException, InterruptedException {
		UiUser user = UiUser.withPassword("user1", true, UiUser.LEVEL_EXECUTEREADWRITE, "pass1");
		user = this.store(user);
		UiUser targetUser = UiUser.withPassword("user2", true, UiUser.LEVEL_READONLY, "pass1")
			.withHashedPassword(user.hashedPassword()).withId(user.id());
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("local", targetUser.local())
			.put("username", targetUser.username())
			.put("level", Long.valueOf(targetUser.level()));
		HttpResponse<JsonNode> response = apiClient.put(
			"/users/%d".formatted(user.id()), data);
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), response.statusCode(),
			"Not getting 200 response for user update");
		{
			UiUser dbUser = this.transactions.readDsl(dsl -> this.repository.find(dsl, targetUser.id()));
			Assertions.assertEquals(targetUser.getName(), dbUser.getName(),
				"User not updated as expected");
			Assertions.assertEquals(targetUser.level(), dbUser.level(),
				"User not updated as expected");
		}
	}

	@Test
	@DisplayName("Update remote user")
	@ResourceLock("DB")
	void updateRemoteUser() throws IOException, InterruptedException {
		UiUser user = UiUser.withPassword("user1", false, UiUser.LEVEL_OPERATOR, "pass1");
		user = this.store(user);
		UiUser targetUser = UiUser.withPassword("user2", true, UiUser.LEVEL_OPERATOR, "pass1")
			.withHashedPassword(user.hashedPassword()).withId(user.id());
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("local", true)
			.put("username", targetUser.username())
			.put("level", Long.valueOf(targetUser.level()));
		{
			HttpResponse<JsonNode> response = apiClient.put(
				"/users/%d".formatted(user.id()), data);
			Assertions.assertEquals(
				Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
				"Not getting 400 response for erroneous user update");
		}
		data.put("password", "pass2");
		{
			HttpResponse<JsonNode> response = apiClient.put(
				"/users/%d".formatted(user.id()), data);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for user update");
		}
		{
			UiUser dbUser = this.transactions.readDsl(dsl -> this.repository.find(dsl, targetUser.id()));
			Assertions.assertEquals(targetUser.getName(), dbUser.getName(),
				"User not updated as expected");
			Assertions.assertEquals(targetUser.level(), dbUser.level(),
				"User not updated as expected");
			Assertions.assertEquals(targetUser.local(), dbUser.local(),
				"User not updated as expected");
		}
	}
}
