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

import org.hibernate.Session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.NetshotApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

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
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session
				.createMutationQuery("delete from Domain")
				.executeUpdate();
			session.getTransaction().commit();
		}
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
		Domain domain1 = new Domain(
			"Test1", "Test Domain1 for listing",
			new Network4Address("10.1.1.1"),
			null
		);
		Domain domain2 = new Domain(
			"Test2", "Test Domain2 for listing",
			new Network4Address("10.1.2.1"),
			null
		);
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(domain1);
			session.persist(domain2);
			session.getTransaction().commit();
		}
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
					.put("id", domain1.getId())
					.put("name", domain1.getName())
					.put("description", domain1.getDescription())
					.put("ipAddress", domain1.getServer4Address().getIp()),
				domainNode1,
				"Retrieved domain doesn't match expected object");
		}
	}

	@Test
	@DisplayName("List domains with pagination")
	@ResourceLock("DB")
	void listPaginatedDomains() throws IOException, InterruptedException {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			for (int i = 1; i <= 15; i++) {
				Domain domain = new Domain(
					"Test%d".formatted(i),
					"Test Domain%d for pagination".formatted(i),
					new Network4Address("10.1.%d.1".formatted(i)),
					null
				);
				session.persist(domain);
			}
			session.getTransaction().commit();
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
		Domain domain = new Domain(
			"Test", "Test Domain for creation",
			new Network4Address("10.1.1.1"),
			null
		);
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("name", domain.getName())
			.put("description", domain.getDescription())
			.put("ipAddress", domain.getServer4Address().getIp());
		HttpResponse<JsonNode> response = apiClient.post("/domains", data);
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for created domain");

		try (Session session = Database.getSession()) {
			Domain newDomain = session
				.createQuery("from Domain d where d.name = :name", Domain.class)
				.setParameter("name", domain.getName())
				.uniqueResult();
			domain.setId(newDomain.getId());
			Assertions.assertEquals(domain, newDomain, "Domain not created as expected");
		}
	}

	@Test
	@DisplayName("Delete domain")
	@ResourceLock("DB")
	void deleteDomain() throws IOException, InterruptedException {
		Domain domain1 = new Domain(
			"Test1", "Test Domain1 for deletion",
			new Network4Address("10.1.1.1"),
			null
		);
		Domain domain2 = new Domain(
			"Test2", "Test Domain2 for deletion",
			new Network4Address("10.1.2.1"),
			null
		);
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(domain1);
			session.persist(domain2);
			session.getTransaction().commit();
		}
		{
			HttpResponse<JsonNode> response = apiClient.delete(
				"/domains/%d".formatted(TestServer.UNKNOWN_ID));
			Assertions.assertEquals(
				Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
				"Not getting 404 response for unknown domain deletion");
		}
		{
			HttpResponse<JsonNode> response = apiClient.delete(
				"/domains/%d".formatted(domain1.getId()));
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
				.put("id", domain2.getId())
				.put("name", domain2.getName())
				.put("description", domain2.getDescription())
				.put("ipAddress", domain2.getServer4Address().getIp()),
			domainNode,
			"Retrieved domain doesn't match expected object");
	}

	@Test
	@DisplayName("Update domain")
	@ResourceLock("DB")
	void updateDomain() throws IOException, InterruptedException {
		Domain domain = new Domain(
			"Test1", "Test Domain1 for update",
			new Network4Address("10.1.1.1"),
			null
		);
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session.persist(domain);
			session.getTransaction().commit();
		}
		Domain targetDomain = new Domain(
			"Test2", "Test Domain2 for update",
			new Network4Address("10.1.2.1"),
			null
		);
		targetDomain.setId(domain.getId());

		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("name", targetDomain.getName())
			.put("description", targetDomain.getDescription())
			.put("ipAddress", targetDomain.getServer4Address().getIp());
		{
			HttpResponse<JsonNode> response = apiClient.put(
				"/domains/%d".formatted(TestServer.UNKNOWN_ID), data);
			Assertions.assertEquals(
				Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
				"Not getting 404 response for unknown domain update");
		}
		{
			HttpResponse<JsonNode> response = apiClient.put(
				"/domains/%d".formatted(domain.getId()), data);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for domain update");
		}
		try (Session session = Database.getSession()) {
			Domain dbDomain = session.byId(Domain.class)
				.load(targetDomain.getId());
			Assertions.assertEquals(
				targetDomain, dbDomain, "Domain not updated as expected");
		}
	}
}
