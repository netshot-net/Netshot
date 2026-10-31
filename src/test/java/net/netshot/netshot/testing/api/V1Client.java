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
package net.netshot.netshot.testing.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.netshot.netshot.testing.TestServer;

/**
 * A plain HTTP client of the v1 API for the tests: any method, headers and body, with the raw answer.
 */
public final class V1Client {

	private static final ObjectMapper JSON = new ObjectMapper();

	/** The requests sent by all the clients ("METHOD /path", without the query), checked by {@link V1EndpointsExtension}. */
	private static final List<String> REQUESTS = new ArrayList<>();

	private final HttpClient client = HttpClient.newHttpClient();
	private final String baseUrl;
	private final Map<String, String> defaultHeaders = new LinkedHashMap<>();

	/**
	 * An answer.
	 *
	 * @param status the status
	 * @param headers the headers
	 * @param body the body
	 */
	public record Answer(int status, java.net.http.HttpHeaders headers, String body) {

		/**
		 * Reads the JSON body.
		 *
		 * @return the body as a JSON tree
		 */
		public JsonNode json() {
			try {
				return JSON.readTree(this.body);
			}
			catch (IOException e) {
				throw new IllegalStateException("Not JSON: " + this.body, e);
			}
		}

		/**
		 * Gets a header.
		 *
		 * @param name the name of the header
		 * @return the first value, or null
		 */
		public String header(String name) {
			return this.headers.firstValue(name).orElse(null);
		}

		@Override
		public String toString() {
			return this.status + " " + this.body;
		}
	}

	/**
	 * Creates a client authenticated with an API token of a level.
	 *
	 * @param server the test server
	 * @param level the permission level, or null for no authentication
	 */
	public V1Client(TestServer server, Integer level) {
		this.baseUrl = server.apiUrl() + "/v1";
		if (level != null) {
			this.defaultHeaders.put("X-Netshot-API-Token", TestServer.API_TOKENS.get(level));
		}
		this.defaultHeaders.put("Accept", "application/json");
	}

	/**
	 * Sends a request.
	 *
	 * @param method the HTTP method
	 * @param path the path under {@code /api/v1}, with the query
	 * @param body the body (JSON unless a Content-Type is given), or null
	 * @param headers the headers (name, value, name, value...)
	 * @return the answer
	 * @throws IOException on transport error
	 * @throws InterruptedException if interrupted
	 */
	public Answer send(String method, String path, String body, String... headers) throws IOException, InterruptedException {
		Map<String, String> all = new LinkedHashMap<>(this.defaultHeaders);
		if (body != null) {
			all.put("Content-Type", "application/json");
		}
		for (int i = 0; i + 1 < headers.length; i += 2) {
			all.put(headers[i], headers[i + 1]);
		}
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(this.baseUrl + path))
			.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		all.forEach((name, value) -> {
			if (value != null) {
				builder.header(name, value);
			}
		});
		synchronized (REQUESTS) {
			REQUESTS.add(method + " " + URI.create(this.baseUrl + path).getRawPath().substring(URI.create(this.baseUrl).getRawPath().length()));
		}
		HttpResponse<String> response = this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		return new Answer(response.statusCode(), response.headers(), response.body());
	}

	/**
	 * Sends a GET.
	 *
	 * @param path the path and query
	 * @param headers the headers
	 * @return the answer
	 * @throws IOException on transport error
	 * @throws InterruptedException if interrupted
	 */
	public Answer get(String path, String... headers) throws IOException, InterruptedException {
		return this.send("GET", path, null, headers);
	}

	/**
	 * Sends a POST of a JSON body.
	 *
	 * @param path the path
	 * @param body the body
	 * @param headers the headers
	 * @return the answer
	 * @throws IOException on transport error
	 * @throws InterruptedException if interrupted
	 */
	public Answer post(String path, String body, String... headers) throws IOException, InterruptedException {
		return this.send("POST", path, body, headers);
	}

	/**
	 * Sends a PUT of a JSON body.
	 *
	 * @param path the path
	 * @param body the body
	 * @param headers the headers
	 * @return the answer
	 * @throws IOException on transport error
	 * @throws InterruptedException if interrupted
	 */
	public Answer put(String path, String body, String... headers) throws IOException, InterruptedException {
		return this.send("PUT", path, body, headers);
	}

	/**
	 * Sends a PATCH of a merge patch.
	 *
	 * @param path the path
	 * @param body the patch
	 * @param headers the headers
	 * @return the answer
	 * @throws IOException on transport error
	 * @throws InterruptedException if interrupted
	 */
	public Answer patch(String path, String body, String... headers) throws IOException, InterruptedException {
		String[] all = new String[headers.length + 2];
		all[0] = "Content-Type";
		all[1] = "application/merge-patch+json";
		System.arraycopy(headers, 0, all, 2, headers.length);
		return this.send("PATCH", path, body, all);
	}

	/**
	 * Sends a DELETE.
	 *
	 * @param path the path
	 * @param headers the headers
	 * @return the answer
	 * @throws IOException on transport error
	 * @throws InterruptedException if interrupted
	 */
	public Answer delete(String path, String... headers) throws IOException, InterruptedException {
		return this.send("DELETE", path, null, headers);
	}

	/**
	 * Gets the requests sent so far by all the clients.
	 *
	 * @return a copy of the requests ("METHOD /path" under {@code /api/v1}, without the query), in order
	 */
	static List<String> requests() {
		synchronized (REQUESTS) {
			return List.copyOf(REQUESTS);
		}
	}
}
