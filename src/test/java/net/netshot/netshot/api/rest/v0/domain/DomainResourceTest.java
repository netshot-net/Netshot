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
package net.netshot.netshot.api.rest.v0.domain;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Iterator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.domain.DomainRepository;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.NetshotApiClient;

@ApiTest
@DisplayName("Domain API Tests")
public class DomainResourceTest {

	/** API client of the current test, authenticated with the admin token. */
	private NetshotApiClient apiClient;

	@BeforeEach
	void createApiClient(NetshotApiClient client) {
		this.apiClient = client;
	}

	@AfterEach
	void cleanUpData() {
		TestDevices.deleteAll();
	}

	@Test
	@DisplayName("List domains")
	@ResourceLock("DB")
	void listDomains() throws IOException, InterruptedException {
		{
			HttpResponse<JsonNode> response = apiClient.get("/domains");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for initial list");
			Assertions.assertEquals(0, response.body().size(),
				"Domain list is not empty");
		}
		Domain domain1 = Domain.builder().name("Test1").description("Test Domain1 for listing").server4Address(new Network4Address("10.1.1.1")).build();
		Domain domain2 = Domain.builder().name("Test2").description("Test Domain2 for listing").server4Address(new Network4Address("10.1.2.1")).build();
		domain1 = TestDevices.addDomain(domain1);
		domain2 = TestDevices.addDomain(domain2);
		{
			HttpResponse<JsonNode> response = apiClient.get("/domains");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for domain list");
			Assertions.assertEquals(2, response.body().size(),
				"Domain list doesn't have 2 elements");
			Iterator<JsonNode> domainNodeIt = response.body().iterator();
			JsonNode domainNode1 = domainNodeIt.next();
			Assertions.assertEquals(
				JsonNodeFactory.instance.objectNode()
					.put("id", domain1.id())
					.put("name", domain1.name())
					.put("description", domain1.description())
					.put("ipAddress", domain1.server4Address().getIp()),
				domainNode1,
				"Retrieved domain doesn't match expected object");
		}
	}

	@Test
	@DisplayName("List domains with pagination")
	@ResourceLock("DB")
	void listPaginatedDomains() throws IOException, InterruptedException {
		{
			for (int i = 1; i <= 15; i++) {
				Domain domain = Domain.builder().name("Test%d".formatted(i)).description("Test Domain%d for pagination".formatted(i))
					.server4Address(new Network4Address("10.1.%d.1".formatted(i)))
					.build();
				TestDevices.addDomain(domain);
			}
		}
		{
			HttpResponse<JsonNode> response = apiClient.get("/domains?limit=10");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for domain list");
			Assertions.assertEquals(10, response.body().size(),
				"Domain list doesn't have 10 elements");
			JsonNode firstNode = response.body().iterator().next();
			Assertions.assertEquals("Test1", firstNode.get("name").asText());
		}
		{
			HttpResponse<JsonNode> response = apiClient.get("/domains?limit=10&offset=10");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for domain list");
			Assertions.assertEquals(5, response.body().size(),
				"Domain list doesn't have 5 elements");
			JsonNode firstNode = response.body().iterator().next();
			Assertions.assertEquals("Test11", firstNode.get("name").asText());
		}
	}


	@Test
	@DisplayName("Create domain")
	@ResourceLock("DB")
	void createDomain() throws IOException, InterruptedException {
		Domain domain = Domain.builder().name("Test").description("Test Domain for creation").server4Address(new Network4Address("10.1.1.1")).build();
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("name", domain.name())
			.put("description", domain.description())
			.put("ipAddress", domain.server4Address().getIp());
		HttpResponse<JsonNode> response = apiClient.post("/domains", data);
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for created domain");

		Domain newDomain = new Transactions().read(dsl -> new DomainRepository().findAll(dsl, Page.ALL)).stream()
			.filter(d -> d.name().equals(domain.name())).findFirst().orElseThrow();
		Assertions.assertEquals(domain.description(), newDomain.description(), "Domain not created as expected");
		Assertions.assertEquals(domain.server4Address(), newDomain.server4Address(), "Domain not created as expected");
	}

	@Test
	@DisplayName("Delete domain")
	@ResourceLock("DB")
	void deleteDomain() throws IOException, InterruptedException {
		Domain domain1 = Domain.builder().name("Test1").description("Test Domain1 for deletion").server4Address(new Network4Address("10.1.1.1")).build();
		Domain domain2 = Domain.builder().name("Test2").description("Test Domain2 for deletion").server4Address(new Network4Address("10.1.2.1")).build();
		domain1 = TestDevices.addDomain(domain1);
		domain2 = TestDevices.addDomain(domain2);
		{
			HttpResponse<JsonNode> response = apiClient.delete(
				"/domains/%d".formatted(TestServer.UNKNOWN_ID));
			Assertions.assertEquals(
				Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
				"Not getting 404 response for unknown domain deletion");
		}
		{
			HttpResponse<JsonNode> response = apiClient.delete(
				"/domains/%d".formatted(domain1.id()));
			Assertions.assertEquals(
				Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
				"Not getting 204 response for domain deletion");
		}
		HttpResponse<JsonNode> listResponse = apiClient.get("/domains");
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), listResponse.statusCode(),
			"Not getting 200 response for domain listing");
		Assertions.assertEquals(1, listResponse.body().size(),
			"Domain list doesn't have 1 element");
		JsonNode domainNode = listResponse.body().iterator().next();
		Assertions.assertEquals(
			JsonNodeFactory.instance.objectNode()
				.put("id", domain2.id())
				.put("name", domain2.name())
				.put("description", domain2.description())
				.put("ipAddress", domain2.server4Address().getIp()),
			domainNode,
			"Retrieved domain doesn't match expected object");
	}

	@Test
	@DisplayName("Update domain")
	@ResourceLock("DB")
	void updateDomain() throws IOException, InterruptedException {
		Domain domain = Domain.builder().name("Test1").description("Test Domain1 for update").server4Address(new Network4Address("10.1.1.1")).build();
		domain = TestDevices.addDomain(domain);
		Domain targetDomain = Domain.builder().name("Test2").description("Test Domain2 for update").server4Address(new Network4Address("10.1.2.1")).build();
		targetDomain = targetDomain.toBuilder().id(domain.id()).build();

		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("name", targetDomain.name())
			.put("description", targetDomain.description())
			.put("ipAddress", targetDomain.server4Address().getIp());
		{
			HttpResponse<JsonNode> response = apiClient.put(
				"/domains/%d".formatted(TestServer.UNKNOWN_ID), data);
			Assertions.assertEquals(
				Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
				"Not getting 404 response for unknown domain update");
		}
		{
			HttpResponse<JsonNode> response = apiClient.put(
				"/domains/%d".formatted(domain.id()), data);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for domain update");
		}
		long targetId = targetDomain.id();
		Domain dbDomain = new Transactions().read(dsl -> new DomainRepository().find(dsl, targetId));
		Assertions.assertEquals(targetDomain.name(), dbDomain.name(), "Domain not updated as expected");
		Assertions.assertEquals(targetDomain.description(), dbDomain.description(), "Domain not updated as expected");
		Assertions.assertEquals(targetDomain.server4Address(), dbDomain.server4Address(), "Domain not updated as expected");
	}
}
