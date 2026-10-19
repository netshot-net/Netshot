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
package net.netshot.netshot.api.rest.v0.hook;

import java.io.IOException;
import java.net.http.HttpResponse;

import org.hibernate.Session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.hook.Hook;
import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
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
@DisplayName("Webhook API Tests")
public class HookResourceTest {

	/** API client of the current test, authenticated with the admin token. */
	private NetshotApiClient apiClient;

	@BeforeEach
	void createApiClient(NetshotApiClient client) {
		this.apiClient = client;
	}

	// A real, parseable self-signed certificate (reused from DeviceDriverTest) -
	// only ever used here to exercise the CUSTOM_CA PEM-parsing validation, its
	// identity/expiry are irrelevant.
	private static final String VALID_CUSTOM_CA_CERTIFICATE =
		"-----BEGIN CERTIFICATE-----\n"
		+ "MIID8TCCAtmgAwIBAgIUU0V+Vgqhs6fsS4MX6dbNYFn2Nk4wDQYJKoZIhvcNAQEL\n"
		+ "BQAwgYcxCzAJBgNVBAYTAkZSMQwwCgYDVQQIDANJZEYxDjAMBgNVBAcMBVBhcmlz\n"
		+ "MRAwDgYDVQQKDAdOZXRzaG90MQ0wCwYDVQQLDARUZXN0MRUwEwYDVQQDDAxBc3lu\n"
		+ "Y09TIHRlc3QxIjAgBgkqhkiG9w0BCQEWE2NvbnRhY3RAbmV0c2hvdC5uZXQwHhcN\n"
		+ "MjUwOTIwMTI1MjA5WhcNMjYwOTIwMTI1MjA5WjCBhzELMAkGA1UEBhMCRlIxDDAK\n"
		+ "BgNVBAgMA0lkRjEOMAwGA1UEBwwFUGFyaXMxEDAOBgNVBAoMB05ldHNob3QxDTAL\n"
		+ "BgNVBAsMBFRlc3QxFTATBgNVBAMMDEFzeW5jT1MgdGVzdDEiMCAGCSqGSIb3DQEJ\n"
		+ "ARYTY29udGFjdEBuZXRzaG90Lm5ldDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC\n"
		+ "AQoCggEBANNRfOo37uan0pRKrfSVpefgwbZwZ/J5MRtaeZcBqHvompZA8FTz6Yp1\n"
		+ "amNuKehdKXae/gYCc83cNTwS7IBivz30iZt4/1VewIwKiZEVbK4DbsGQioseb3vO\n"
		+ "gJoot7FzGrkoKnHn9n9cZmOA2zWiKE7SqVztg1MXcKnZhz5QE1mIG4Abz8dAYnM7\n"
		+ "yRQ7DuDl9L7ESFQA8NcsML+zZ1q8kpQz82Oq10lolbnMolHCJx8jjAYnnMG/tK4I\n"
		+ "Q6gUFPxS0gNsgoKnOe6OYMX7Z06hU5sibVc6jF+wCnVZLjEMuOhpCvLcZmbaYTmg\n"
		+ "Cy7KpGF6ILyKKG83NlOYttHJBSA3eP8CAwEAAaNTMFEwHQYDVR0OBBYEFMABqRHf\n"
		+ "1AxhRgx/dCyPTbbIkJYjMB8GA1UdIwQYMBaAFMABqRHf1AxhRgx/dCyPTbbIkJYj\n"
		+ "MA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBADQ5fEVeNuw8RaFf\n"
		+ "hv97+xQfej39CSDCI9foSSFr6LL624S4wyiSwWKrtg9lH+mxozg5q2NXUW+qWKPR\n"
		+ "NDuOBY9SNVwkEoLqsdXbOIncuvwXBJ+5+/Md/IVjH5O88Gb6fEczhb0snBqp9+8v\n"
		+ "jD75NoACvUucKL4J5A8M54mw+wZvRn64RhwUhigqQETRilUUsVAJITRdXvXtyYK0\n"
		+ "Jtb1Bt8xVvOHzMi/n6I8yKdjuIrDiec4+XGaXK9DqNeZc4znTVXkx3Sr1HLs3BT4\n"
		+ "SlvJ+y6SluxPcAoSFBGy4uC8DhJiWXpPaCDWPY1IE7aBiwMFCzdQrczWnp6vOKIa\n"
		+ "Ahhsx70=\n"
		+ "-----END CERTIFICATE-----\n";

	@AfterEach
	void cleanUpData() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session
				.createMutationQuery("delete from HookTrigger")
				.executeUpdate();
			session
				.createMutationQuery("delete from Hook")
				.executeUpdate();
			session.getTransaction().commit();
		}
	}

	private ObjectNode newWebhookData(String name, String url) {
		return JsonNodeFactory.instance.objectNode()
			.put("type", "Web")
			.put("name", name)
			.put("enabled", true)
			.put("action", WebHook.Action.POST_JSON.toString())
			.put("url", url);
	}

	private WebHook getWebHookByName(String name) {
		try (Session session = Database.getSession()) {
			return (WebHook) session
				.createQuery("from Hook h where h.name = :name", Hook.class)
				.setParameter("name", name)
				.uniqueResult();
		}
	}

	@Test
	@DisplayName("List webhooks")
	@ResourceLock("DB")
	void listWebhooks() throws IOException, InterruptedException {
		{
			HttpResponse<JsonNode> response = apiClient.get("/hooks");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for initial webhook list");
			Assertions.assertEquals(0, response.body().size(),
				"Webhook list is not empty");
		}
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(),
			apiClient.post("/hooks", newWebhookData("Webhook 1", "https://example.net/callback1")).statusCode(),
			"Not getting 201 response for created webhook 1");
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(),
			apiClient.post("/hooks", newWebhookData("Webhook 2", "https://example.net/callback2")).statusCode(),
			"Not getting 201 response for created webhook 2");
		{
			HttpResponse<JsonNode> response = apiClient.get("/hooks");
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for webhook list");
			Assertions.assertEquals(2, response.body().size(),
				"Webhook list doesn't have 2 elements");
		}
	}

	@Test
	@DisplayName("Create webhook applies default HTTPS trust policy")
	@ResourceLock("DB")
	void createWebhookDefaultsTrustPolicy() throws IOException, InterruptedException {
		HttpResponse<JsonNode> response = apiClient.post("/hooks",
			newWebhookData("Webhook default trust", "https://example.net/callback"));
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for created webhook");

		WebHook newHook = this.getWebHookByName("Webhook default trust");
		Assertions.assertNotNull(newHook, "Webhook not created as expected");
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, newHook.getHttpsCaTrustMode(),
			"Default HTTPS CA trust mode not applied");
		Assertions.assertNull(newHook.getHttpsCustomCaCertificate(),
			"Custom CA certificate should not be set by default");
	}

	@Test
	@DisplayName("Create webhook with explicit HTTPS trust policy")
	@ResourceLock("DB")
	void createWebhookExplicitTrustPolicy() throws IOException, InterruptedException {
		ObjectNode data = newWebhookData("Webhook explicit trust", "https://example.net/callback")
			.put("httpsCaTrustMode", HttpsCaTrustMode.TRUST_ANY.toString());
		HttpResponse<JsonNode> response = apiClient.post("/hooks", data);
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for created webhook");

		WebHook newHook = this.getWebHookByName("Webhook explicit trust");
		Assertions.assertNotNull(newHook, "Webhook not created as expected");
		Assertions.assertEquals(HttpsCaTrustMode.TRUST_ANY, newHook.getHttpsCaTrustMode());
	}

	@Test
	@DisplayName("Reject webhook with invalid target URL")
	@ResourceLock("DB")
	void rejectInvalidUrl() throws IOException, InterruptedException {
		HttpResponse<JsonNode> response = apiClient.post("/hooks",
			newWebhookData("Webhook bad URL", "not a url"));
		Assertions.assertEquals(
			Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
			"Not getting 400 response for an invalid webhook URL");
		Assertions.assertEquals(
			ErrorCode.NETSHOT_INVALID_HOOK_WEB_URL.getCode(),
			response.body().get("errorCode").asInt());
	}

	@Test
	@DisplayName("Reject webhook with missing action")
	@ResourceLock("DB")
	void rejectMissingAction() throws IOException, InterruptedException {
		ObjectNode data = newWebhookData("Webhook no action", "https://example.net/callback");
		data.remove("action");
		HttpResponse<JsonNode> response = apiClient.post("/hooks", data);
		Assertions.assertEquals(
			Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
			"Not getting 400 response for a webhook with no action");
		Assertions.assertEquals(
			ErrorCode.NETSHOT_INVALID_HOOK_WEB.getCode(),
			response.body().get("errorCode").asInt());
	}

	@Test
	@DisplayName("Reject CUSTOM_CA webhook with no certificate")
	@ResourceLock("DB")
	void rejectCustomCaWithoutCertificate() throws IOException, InterruptedException {
		ObjectNode data = newWebhookData("Webhook missing CA cert", "https://example.net/callback")
			.put("httpsCaTrustMode", HttpsCaTrustMode.CUSTOM_CA.toString());
		HttpResponse<JsonNode> response = apiClient.post("/hooks", data);
		Assertions.assertEquals(
			Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
			"Not getting 400 response for a CUSTOM_CA webhook with no certificate");
		Assertions.assertEquals(
			ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER.getCode(),
			response.body().get("errorCode").asInt());
	}

	@Test
	@DisplayName("Reject CUSTOM_CA webhook with an unparseable certificate")
	@ResourceLock("DB")
	void rejectCustomCaWithInvalidCertificate() throws IOException, InterruptedException {
		ObjectNode data = newWebhookData("Webhook bad CA cert", "https://example.net/callback")
			.put("httpsCaTrustMode", HttpsCaTrustMode.CUSTOM_CA.toString())
			.put("httpsCustomCaCertificate", "not a certificate");
		HttpResponse<JsonNode> response = apiClient.post("/hooks", data);
		Assertions.assertEquals(
			Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
			"Not getting 400 response for a CUSTOM_CA webhook with an unparseable certificate");
		Assertions.assertEquals(
			ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER.getCode(),
			response.body().get("errorCode").asInt());
	}

	@Test
	@DisplayName("Create CUSTOM_CA webhook with a valid certificate")
	@ResourceLock("DB")
	void createWebhookWithValidCustomCa() throws IOException, InterruptedException {
		ObjectNode data = newWebhookData("Webhook valid CA cert", "https://example.net/callback")
			.put("httpsCaTrustMode", HttpsCaTrustMode.CUSTOM_CA.toString())
			.put("httpsCustomCaCertificate", VALID_CUSTOM_CA_CERTIFICATE);
		HttpResponse<JsonNode> response = apiClient.post("/hooks", data);
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(), response.statusCode(),
			"Not getting 201 response for a CUSTOM_CA webhook with a valid certificate");

		WebHook newHook = this.getWebHookByName("Webhook valid CA cert");
		Assertions.assertNotNull(newHook, "Webhook not created as expected");
		Assertions.assertEquals(HttpsCaTrustMode.CUSTOM_CA, newHook.getHttpsCaTrustMode());
		Assertions.assertEquals(VALID_CUSTOM_CA_CERTIFICATE, newHook.getHttpsCustomCaCertificate());
	}

	@Test
	@DisplayName("Reject duplicate webhook name")
	@ResourceLock("DB")
	void rejectDuplicateName() throws IOException, InterruptedException {
		Assertions.assertEquals(
			Response.Status.CREATED.getStatusCode(),
			apiClient.post("/hooks", newWebhookData("Duplicate webhook", "https://example.net/callback1")).statusCode(),
			"Not getting 201 response for the first webhook");
		HttpResponse<JsonNode> response = apiClient.post("/hooks",
			newWebhookData("Duplicate webhook", "https://example.net/callback2"));
		Assertions.assertEquals(
			Response.Status.CONFLICT.getStatusCode(), response.statusCode(),
			"Not getting 409 response for a duplicate webhook name");
		Assertions.assertEquals(
			ErrorCode.NETSHOT_DUPLICATE_HOOK.getCode(),
			response.body().get("errorCode").asInt());
	}

	@Test
	@DisplayName("Update webhook")
	@ResourceLock("DB")
	void updateWebhook() throws IOException, InterruptedException {
		HttpResponse<JsonNode> createResponse = apiClient.post("/hooks",
			newWebhookData("Webhook to update", "https://example.net/callback"));
		long id = createResponse.body().get("id").asLong();

		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("type", "Web")
			.put("name", "Webhook updated")
			.put("enabled", false)
			.put("action", WebHook.Action.POST_XML.toString())
			.put("url", "https://example.net/updated")
			.put("httpsCaTrustMode", HttpsCaTrustMode.CUSTOM_CA.toString())
			.put("httpsCustomCaCertificate", VALID_CUSTOM_CA_CERTIFICATE);

		{
			HttpResponse<JsonNode> response = apiClient.put(
				"/hooks/%d".formatted(TestServer.UNKNOWN_ID), data);
			Assertions.assertEquals(
				Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
				"Not getting 400 response for an unknown webhook update");
		}
		{
			HttpResponse<JsonNode> response = apiClient.put(
				"/hooks/%d".formatted(id), data);
			Assertions.assertEquals(
				Response.Status.OK.getStatusCode(), response.statusCode(),
				"Not getting 200 response for webhook update");
		}

		WebHook updatedHook = this.getWebHookByName("Webhook updated");
		Assertions.assertNotNull(updatedHook, "Webhook not updated as expected");
		Assertions.assertFalse(updatedHook.isEnabled());
		Assertions.assertEquals(WebHook.Action.POST_XML, updatedHook.getAction());
		Assertions.assertEquals("https://example.net/updated", updatedHook.getUrl());
		Assertions.assertEquals(HttpsCaTrustMode.CUSTOM_CA, updatedHook.getHttpsCaTrustMode());
		Assertions.assertEquals(VALID_CUSTOM_CA_CERTIFICATE, updatedHook.getHttpsCustomCaCertificate());
	}

	@Test
	@DisplayName("Delete webhook")
	@ResourceLock("DB")
	void deleteWebhook() throws IOException, InterruptedException {
		HttpResponse<JsonNode> createResponse = apiClient.post("/hooks",
			newWebhookData("Webhook to delete", "https://example.net/callback"));
		long id = createResponse.body().get("id").asLong();

		{
			HttpResponse<JsonNode> response = apiClient.delete(
				"/hooks/%d".formatted(TestServer.UNKNOWN_ID));
			Assertions.assertEquals(
				Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
				"Not getting 404 response for unknown webhook deletion");
		}
		{
			HttpResponse<JsonNode> response = apiClient.delete("/hooks/%d".formatted(id));
			Assertions.assertEquals(
				Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
				"Not getting 204 response for webhook deletion");
		}

		Assertions.assertNull(this.getWebHookByName("Webhook to delete"),
			"Webhook not deleted as expected");
	}
}
