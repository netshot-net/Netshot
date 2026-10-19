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

import org.hibernate.Session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.WithRestServerTest;
import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.platform.database.Database;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@DisplayName("API token management API Tests")
public class ApiTokenResourceTest extends WithRestServerTest {

	@Test
	@DisplayName("List tokens")
	@ResourceLock("DB")
	void listTokens() throws IOException, InterruptedException {
		// Tokens already created like before all tests
		HttpResponse<JsonNode> response = apiClient.get("/apitokens");
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), response.statusCode(),
			"Not getting 200 response for token list");
		Assertions.assertEquals(API_TOKENS.size(), response.body().size(),
			"User list doesn't have as many elements as API_TOKENS");
	}


	@Test
	@DisplayName("Create token")
	@ResourceLock("DB")
	void createToken() throws IOException, InterruptedException {
		String tokenString = "dZLV0zCn5gmbUJIebRHBxM4QjIAoNruK";
		ApiToken token = new ApiToken(
			"Test API token",
			tokenString,
			UiUser.LEVEL_READWRITE
		);
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("description", token.getDescription())
			.put("token", tokenString)
			.put("level", token.getLevel());
		HttpResponse<JsonNode> response = apiClient.post("/apitokens", data);
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for created API token");

		try (Session session = Database.getSession()) {
			ApiToken newToken = session
				.createQuery("from ApiToken t where t.description = :description", ApiToken.class)
				.setParameter("description", token.getDescription())
				.uniqueResult();
			Assertions.assertEquals(token.getLevel(), newToken.getLevel(),
				"Token not created as expected");
		}
	}

	@Test
	@DisplayName("Delete token")
	@ResourceLock("DB")
	void deleteToken() throws IOException, InterruptedException {
		ApiToken token1 = new ApiToken(
			"Temp token", "dZLV0zCn5gmbUJIebRHBxM4QjIAoNruK", UiUser.LEVEL_OPERATOR);
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(token1);
			session.getTransaction().commit();
		}
		HttpResponse<JsonNode> response = apiClient.delete(
			"/apitokens/%d".formatted(token1.getId()));
		Assertions.assertEquals(
			Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
			"Not getting 204 response for token deletion");
		HttpResponse<JsonNode> listResponse = apiClient.get("/apitokens");
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), listResponse.statusCode(),
			"Not getting 200 response for token listing");
		Assertions.assertEquals(API_TOKENS.size(), listResponse.body().size(),
			"User list doesn't have the right number of elements");
	}
}
