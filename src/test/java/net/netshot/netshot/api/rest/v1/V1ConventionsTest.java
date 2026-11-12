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
package net.netshot.netshot.api.rest.v1;

import java.time.Instant;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import jakarta.ws.rs.core.MultivaluedHashMap;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.api.rest.v1.domain.DomainRequest;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.QuerySchema;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.testing.UnitTest;

/**
 * The conventions of the v1 API which don't need a server: status of the error codes, merge patch, dates.
 */
@UnitTest
@DisplayName("v1 conventions")
class V1ConventionsTest {

	@Test
	@DisplayName("Only a version mismatch is a 412 in v1")
	void statuses() {
		for (ErrorCode code : ErrorCode.values()) {
			int status = V1Status.of(code);
			Assertions.assertTrue(status >= 400 && status < 600, code.name());
			Assertions.assertEquals(code == ErrorCode.NETSHOT_VERSION_MISMATCH, status == 412, code.name());
		}
		Assertions.assertEquals(409, V1Status.of(ErrorCode.NETSHOT_DOMAIN_IN_USE));
		Assertions.assertEquals("urn:netshot:error:domain-in-use", V1Status.type(ErrorCode.NETSHOT_DOMAIN_IN_USE));
		Assertions.assertEquals("Domain in use", V1Status.title(ErrorCode.NETSHOT_DOMAIN_IN_USE));
	}

	@Test
	@DisplayName("Merge patch (RFC 7396)")
	void mergePatch() throws Exception {
		JsonNode target = V1Mappers.json().readTree("{\"a\": \"b\", \"c\": {\"d\": \"e\", \"f\": \"g\"}, \"l\": [1, 2]}");
		JsonNode patch = V1Mappers.json().readTree("{\"a\": \"z\", \"c\": {\"f\": null}, \"l\": [3], \"n\": 1}");
		Assertions.assertEquals(V1Mappers.json().readTree("{\"a\": \"z\", \"c\": {\"d\": \"e\", \"f\": null}, \"l\": [3], \"n\": 1}"),
			MergePatch.merge(target, patch));
	}

	@Test
	@DisplayName("Dates: ISO-8601 UTC with milliseconds, read with any offset")
	void dates() throws Exception {
		Assertions.assertEquals("\"2026-10-30T09:12:44.000Z\"", V1Mappers.json().writeValueAsString(Instant.parse("2026-10-30T09:12:44Z")));
		Assertions.assertEquals(Instant.parse("2026-10-30T07:12:44Z"), V1Mappers.json().readValue("\"2026-10-30T09:12:44+02:00\"", Instant.class));
		Assertions.assertThrows(Exception.class, () -> V1Mappers.json().readValue("\"2026-10-30T09:12:44\"", Instant.class));
	}

	@Test
	@DisplayName("The read-only properties of the representation are ignored in a request, other unknown ones are refused")
	void readOnlyProperties() throws Exception {
		String json = """
			{"id": 3, "url": "/api/v1/domains/3", "display": "Paris", "version": 4, "changeDate": "2026-10-30T09:12:44.000Z",
			"name": "Paris", "description": "DC", "server4Address": "10.0.1.1", "server6Address": null}""";
		Assertions.assertEquals(new DomainRequest("Paris", "DC", "10.0.1.1"), V1Mappers.json().readValue(json, DomainRequest.class));
		String xml = "<domain><id>3</id><url>/api/v1/domains/3</url><version>4</version><name>Paris</name>"
			+ "<server4Address>10.0.1.1</server4Address></domain>";
		Assertions.assertEquals(new DomainRequest("Paris", null, "10.0.1.1"), V1Mappers.xml().readValue(xml, DomainRequest.class));
		Assertions.assertEquals(new DomainRequest("Paris", null, "10.0.1.1"),
			V1Mappers.yaml().readValue("id: 3\nversion: 4\nname: Paris\nserver4Address: 10.0.1.1\n", DomainRequest.class));
		UnrecognizedPropertyException unknown = Assertions.assertThrows(UnrecognizedPropertyException.class,
			() -> V1Mappers.json().readValue("{\"name\": \"Paris\", \"server4Address\": \"10.0.1.1\", \"color\": \"red\"}",
				DomainRequest.class));
		Assertions.assertEquals("color", unknown.getPropertyName());
	}

	@Test
	@DisplayName("The page size has no maximum by default (limit=0: no page), a configured maximum is enforced")
	void pageSizeMaximum() {
		QuerySchema schema = new QuerySchema(java.util.Map.of(), java.util.List.of());
		MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
		params.putSingle(ListParams.LIMIT, "100000");
		try {
			TestConfig.init(new Properties());
			Assertions.assertNull(ListParams.settings().maxPageSize());
			Assertions.assertEquals(100000, ListParams.parse(params, schema).page().limit());
			params.putSingle(ListParams.LIMIT, "0");
			Assertions.assertNull(ListParams.parse(params, schema).page().limit(), "limit=0: all the items");
			params.remove(ListParams.LIMIT);
			Assertions.assertEquals(100, ListParams.parse(params, schema).page().limit());

			Properties config = new Properties();
			config.setProperty("netshot.http.api.pagesize.max", "50");
			TestConfig.init(config);
			Assertions.assertEquals(50, ListParams.parse(params, schema).page().limit(), "The default is capped to the maximum");
			params.putSingle(ListParams.LIMIT, "51");
			NetshotException tooBig = Assertions.assertThrows(NetshotException.class, () -> ListParams.parse(params, schema));
			Assertions.assertEquals(ListParams.LIMIT, tooBig.getTarget());
			params.putSingle(ListParams.LIMIT, "0");
			NetshotException all = Assertions.assertThrows(NetshotException.class, () -> ListParams.parse(params, schema), "No limit=0 with a maximum");
			Assertions.assertEquals(ListParams.LIMIT, all.getTarget());
		}
		finally {
			TestConfig.init(new Properties());
		}
	}
}
