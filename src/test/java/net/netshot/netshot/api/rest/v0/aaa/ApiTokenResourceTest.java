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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.ApiTokenRepository;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.NetshotApiClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ApiTest
@DisplayName("API token management API Tests")
public class ApiTokenResourceTest {

	/** API client of the current test, authenticated with the admin token. */
	private NetshotApiClient apiClient;

	private final Transactions transactions = new Transactions();

	private final ApiTokenRepository repository = new ApiTokenRepository();

	@BeforeEach
	void createApiClient(NetshotApiClient client) {
		this.apiClient = client;
	}

	@Test
	@DisplayName("List tokens")
	@ResourceLock("DB")
	void listTokens() throws IOException, InterruptedException {
		// Tokens already created like before all tests
		HttpResponse<JsonNode> response = apiClient.get("/apitokens");
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), response.statusCode(),
			"Not getting 200 response for token list");
		Assertions.assertEquals(TestServer.API_TOKENS.size(), response.body().size(),
			"User list doesn't have as many elements as TestServer.API_TOKENS");
	}


	@Test
	@DisplayName("Create token")
	@ResourceLock("DB")
	void createToken() throws IOException, InterruptedException {
		String tokenString = "dZLV0zCn5gmbUJIebRHBxM4QjIAoNruK";
		ApiToken token = ApiToken.of(
			"Test API token",
			tokenString,
			UiUser.LEVEL_READWRITE
		);
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("description", token.description())
			.put("token", tokenString)
			.put("level", token.level());
		HttpResponse<JsonNode> response = apiClient.post("/apitokens", data);
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for created API token");

		{
			ApiToken newToken = this.transactions.readDsl(dsl -> this.repository.findByHash(dsl, token.hashedToken()));
			Assertions.assertEquals(token.level(), newToken.level(),
				"Token not created as expected");
		}
	}

	@Test
	@DisplayName("Delete token")
	@ResourceLock("DB")
	void deleteToken() throws IOException, InterruptedException {
		ApiToken temp = ApiToken.of(
			"Temp token", "dZLV0zCn5gmbUJIebRHBxM4QjIAoNruK", UiUser.LEVEL_OPERATOR);
		ApiToken token1 = temp.withId(this.transactions.writeDsl(dsl -> this.repository.add(dsl, temp)));
		HttpResponse<JsonNode> response = apiClient.delete(
			"/apitokens/%d".formatted(token1.id()));
		Assertions.assertEquals(
			Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
			"Not getting 204 response for token deletion");
		HttpResponse<JsonNode> listResponse = apiClient.get("/apitokens");
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), listResponse.statusCode(),
			"Not getting 200 response for token listing");
		Assertions.assertEquals(TestServer.API_TOKENS.size(), listResponse.body().size(),
			"User list doesn't have the right number of elements");
	}
}
