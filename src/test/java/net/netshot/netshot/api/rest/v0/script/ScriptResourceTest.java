/**
 * Copyright 2013-present Netshot
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
package net.netshot.netshot.api.rest.v0.script;

import java.io.IOException;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.api.NetshotApiClient;
import net.netshot.netshot.userscript.DeviceJsScript;
import net.netshot.netshot.userscript.ScriptRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ApiTest
@DisplayName("Script tests")
@ResourceLock("DB")
public class ScriptResourceTest {

	/** API client of the current test, authenticated with the admin token. */
	private NetshotApiClient apiClient;

	@BeforeEach
	void createApiClient(NetshotApiClient client) {
		this.apiClient = client;
	}

	private final String testScriptContent =
		"function run(cli, device) {\n"
		+ "   cli.macro(\"configure\");\n"
		+ "   cli.command(\"no ip domain-lookup\")\n"
		+ "   cli.macro(\"end\");\n"
		+ "   cli.macro(\"save\");\n"
		+ "}";

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	private DeviceDriver getTestDriver() {
		return TestDrivers.registry().getDriverByName("CiscoIOS12");
	}

	@AfterEach
	void cleanUpData() {
		TestWiring.get().transactions().run(dsl -> dsl.deleteFrom(Tables.DEVICE_JS_SCRIPT).execute());
	}

	@Test
	@DisplayName("Add script")
	@ResourceLock("DB")
	void addScript() throws IOException, InterruptedException {
		ObjectNode data = JsonNodeFactory.instance.objectNode()
			.put("name", "Test Script")
			.put("deviceDriver", this.getTestDriver().getName())
			.put("script", testScriptContent);
		{
			HttpResponse<JsonNode> response = apiClient.post("/scripts",
				data.deepCopy().put("deviceDriver", "NonExistingDriver"));
			Assertions.assertEquals(
				Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
				"Not getting 400 response for invalid device driver");
		}
		{
			HttpResponse<JsonNode> response = apiClient.post("/scripts",
				data.deepCopy().put("name", ""));
			Assertions.assertEquals(
				Response.Status.BAD_REQUEST.getStatusCode(), response.statusCode(),
				"Not getting 400 response for invalid script name");
		}
		{
			HttpResponse<JsonNode> response = apiClient.post("/scripts", data);
			Assertions.assertEquals(
				Response.Status.CREATED.getStatusCode(), response.statusCode(),
				"Not getting 201 response for created script");
		}
		{
			DeviceJsScript newScript = TestWiring.get().transactions().read(dsl -> new ScriptRepository().findAll(dsl, Page.ALL)).stream()
				.filter(s -> "Test Script".equals(s.name())).findFirst().orElse(null);
			Assertions.assertNotNull(newScript, "Script not created as expected");
			Assertions.assertEquals(this.getTestDriver().getName(), newScript.deviceDriver(),
				"Script not created as expected");
			Assertions.assertEquals(testScriptContent, newScript.script(),
				"Script not created as expected");
		}
		{
			// Duplicate script name in the same folder
			HttpResponse<JsonNode> response = apiClient.post("/scripts", data);
			Assertions.assertEquals(
				Response.Status.CONFLICT.getStatusCode(), response.statusCode(),
				"Not getting 400 response for duplicated script name");
		}
		{
			// Same script name but different folder is allowed
			HttpResponse<JsonNode> response = apiClient.post("/scripts",
				data.deepCopy().put("folder", "Other folder"));
			Assertions.assertEquals(
				Response.Status.CREATED.getStatusCode(), response.statusCode(),
				"Not getting 201 response for created script with same name in another folder");
		}
	}

	@Test
	@DisplayName("Get script")
	@ResourceLock("DB")
	void getScript() throws IOException, InterruptedException {
		DeviceJsScript stored = DeviceJsScript.of(
			"Test Script", this.getTestDriver().getName(), testScriptContent, "author1");
		DeviceJsScript script = stored.withId(TestWiring.get().transactions().write(dsl -> new ScriptRepository().add(dsl, stored)));
		HttpResponse<JsonNode> response = apiClient.get("/scripts/%d".formatted(script.id()));
		Assertions.assertEquals(
			Response.Status.OK.getStatusCode(), response.statusCode(),
			"Not getting 200 response for script");
		JsonNode scriptNode = response.body();
		Assertions.assertEquals(script.name(), scriptNode.get("name").asText(),
			"Retrieved script doesn't match expected object");
		Assertions.assertEquals(script.deviceDriver(), scriptNode.get("deviceDriver").asText(),
			"Retrieved script doesn't match expected object");
		Assertions.assertEquals(script.script(), scriptNode.get("script").asText(),
			"Retrieved script doesn't match expected object");
		Assertions.assertEquals(script.author(), scriptNode.get("author").asText(),
			"Retrieved script doesn't match expected object");
	}

	@Test
	@DisplayName("Delete script")
	@ResourceLock("DB")
	void deleteScript() throws IOException, InterruptedException {
		DeviceJsScript stored = DeviceJsScript.of(
			"Test Script", this.getTestDriver().getName(), testScriptContent, "author1");
		DeviceJsScript script = stored.withId(TestWiring.get().transactions().write(dsl -> new ScriptRepository().add(dsl, stored)));
		{
			HttpResponse<JsonNode> response = apiClient.delete(
				"/scripts/%d".formatted(TestServer.UNKNOWN_ID));
			Assertions.assertEquals(
				Response.Status.NOT_FOUND.getStatusCode(), response.statusCode(),
				"Not getting 404 response for unknown script deletion");
		}
		{
			HttpResponse<JsonNode> response = apiClient.delete(
				"/scripts/%d".formatted(script.id()));
			Assertions.assertEquals(
				Response.Status.NO_CONTENT.getStatusCode(), response.statusCode(),
				"Not getting 204 response for script deletion");
		}
		{
			DeviceJsScript deletedScript = TestWiring.get().transactions().read(dsl -> new ScriptRepository().find(dsl, script.id()));
			Assertions.assertNull(deletedScript, "Script not deleted as expected");
		}
	}

}
