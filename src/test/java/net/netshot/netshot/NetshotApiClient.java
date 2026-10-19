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
package net.netshot.netshot;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.Setter;

/**
 * Simple wrapper for Netshot REST API.
 */
public class NetshotApiClient {

	/**
	 * Simple exception to catch unexpected response and attach it.
	 */
	public static class WrongApiResponseException extends IOException {
		private static final long serialVersionUID = 1L;

		@Getter
		private HttpResponse<?> response;

		public WrongApiResponseException(String message, HttpResponse<?> response) {
			super(message);
			this.response = response;
		}
	}

	protected static final String SESSION_COOKIE_NAME = "NetshotSessionID";

	@Getter
	@Setter
	private URI apiUrl;

	@Getter
	@Setter
	private String apiToken;

	@Getter
	@Setter
	private String username;

	@Getter
	@Setter
	private String password;

	/** To change the password on login. */
	@Getter
	@Setter
	private String newPassword;

	@Getter
	@Setter
	private String authorizationCode;

	@Getter
	@Setter
	private String redirectUri;

	private ObjectMapper objectMapper;

	@Getter
	private MediaType mediaType = MediaType.APPLICATION_JSON_TYPE;

	private HttpClient httpClient;

	private CookieManager cookieManager;

	private NetshotApiClient(String apiUrl) throws URISyntaxException {
		this.cookieManager = new CookieManager();
		this.httpClient = HttpClient.newBuilder().cookieHandler(this.cookieManager).build();
		this.apiUrl = new URI(apiUrl);
	}

	public NetshotApiClient(String apiUrl, String apiToken) throws URISyntaxException {
		this(apiUrl);
		this.apiToken = apiToken;
	}

	public NetshotApiClient(String apiUrl, String username, String password) throws URISyntaxException {
		this(apiUrl);
		this.username = username;
		this.password = password;
	}

	public void setLogin(String username, String password) {
		this.username = username;
		this.password = password;
		this.apiToken = null;
	}

	public void setOidcCodeLogin(String authorizationCode, String redirectUri) {
		this.authorizationCode = authorizationCode;
		this.redirectUri = redirectUri;
		this.apiToken = null;
	}

	private HttpRequest.BodyPublisher jsonNodePublisher(JsonNode jsonNode) throws JsonProcessingException {
		return HttpRequest.BodyPublishers.ofString(
			this.getObjectMapper().writeValueAsString(jsonNode));
	}

	private HttpResponse.BodyHandler<JsonNode> jsonNodeHandler() {
		return new HttpResponse.BodyHandler<JsonNode>() {
			@Override
			public BodySubscriber<JsonNode> apply(ResponseInfo responseInfo) {
				HttpResponse.BodySubscriber<String> upstream = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
				return HttpResponse.BodySubscribers.mapping(upstream, (String body) -> {
					try {
						ObjectMapper objectMapper = getObjectMapper();
						return objectMapper.readTree(body);
					}
					catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
			}
		};
	}

	public ObjectMapper getObjectMapper() {
		if (this.objectMapper == null) {
			if (this.mediaType.equals(MediaType.APPLICATION_XML_TYPE)) {
				this.objectMapper = new XmlMapper();
			}
			else {
				this.objectMapper = new JsonMapper();
			}
			this.objectMapper.enable(DeserializationFeature.USE_LONG_FOR_INTS);
		}
		return this.objectMapper;
	}

	public void setMediaType(MediaType mediaType) {
		this.mediaType = mediaType;
		this.objectMapper = null;
	}

	public void login() throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder();
		builder.version(HttpClient.Version.HTTP_1_1);
		builder.uri(URI.create(this.apiUrl + "/user"));
		builder.header("Accept", this.mediaType.toString());
		builder.header("Content-Type", this.mediaType.toString());
		ObjectNode payload = JsonNodeFactory.instance.objectNode();
		if (this.username != null) {
			payload.put("username", this.username);
			payload.put("password", this.password);
			if (this.newPassword != null) {
				payload.put("newPassword", this.newPassword);
			}
		}
		else if (this.authorizationCode != null) {
			payload.put("authorizationCode", this.authorizationCode);
			payload.put("redirectUri", this.redirectUri);
		}
		builder.POST(this.jsonNodePublisher(payload));
		HttpRequest request = builder.build();
		HttpResponse<JsonNode> response =
			this.httpClient.send(request, this.jsonNodeHandler());
		if (response.statusCode() != Response.Status.OK.getStatusCode()) {
			throw new WrongApiResponseException("Netshot REST API login failed", response);
		}
	}

	public HttpCookie getSessionCookie() {
		for (HttpCookie cookie : this.cookieManager.getCookieStore().get(this.apiUrl)) {
			if (cookie.getName().equals(SESSION_COOKIE_NAME)) {
				return cookie;
			}
		}
		return null;
	}

	public void addSessionCookie(HttpCookie cookie) {
		this.cookieManager.getCookieStore().add(this.apiUrl, cookie);
	}

	public void addSessionCookie(String value) {
		HttpCookie cookie = new HttpCookie(SESSION_COOKIE_NAME, value);
		this.addSessionCookie(cookie);
	}

	public void logout() throws IOException, InterruptedException {
		if (this.getSessionCookie() == null) {
			return;
		}
		HttpRequest.Builder builder = HttpRequest.newBuilder();
		builder.version(HttpClient.Version.HTTP_1_1);
		builder.uri(URI.create(this.apiUrl + "/user/0"));
		builder.DELETE();
		HttpRequest request = builder.build();
		HttpResponse<JsonNode> response =
			this.httpClient.send(request, this.jsonNodeHandler());
		if (response.statusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
			throw new RuntimeException("Netshot REST API logout failed");
		}
	}

	protected HttpRequest.Builder initRequest(String path) throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder();
		builder.version(HttpClient.Version.HTTP_1_1);
		if (this.apiToken != null) {
			builder.header("X-Netshot-API-Token", this.apiToken);
		}
		else if ((this.username != null && this.password != null)
			|| this.authorizationCode != null) {
			if (this.getSessionCookie() == null) {
				this.login();
			}
		}
		builder.header("Accept", this.mediaType.toString());
		builder.uri(URI.create(this.apiUrl + path));
		return builder;
	}

	public HttpResponse<JsonNode> get(String path) throws IOException, InterruptedException {
		HttpRequest request = initRequest(path).GET().build();
		HttpResponse<JsonNode> response =
			this.httpClient.send(request, this.jsonNodeHandler());
		return response;
	}

	public HttpResponse<Path> download(String path, Path localPath) throws IOException, InterruptedException {
		HttpRequest request = initRequest(path).GET().build();
		return this.httpClient.send(request, BodyHandlers.ofFile(localPath));
	}

	public HttpResponse<InputStream> download(String path) throws IOException, InterruptedException {
		HttpRequest request = initRequest(path).GET().build();
		return this.httpClient.send(request, BodyHandlers.ofInputStream());
	}

	public HttpResponse<JsonNode> post(String path, JsonNode data) throws IOException, InterruptedException {
		HttpRequest request = initRequest(path)
			.header("Content-Type", this.mediaType.toString())
			.POST(this.jsonNodePublisher(data))
			.build();
		HttpResponse<JsonNode> response =
			this.httpClient.send(request, this.jsonNodeHandler());
		return response;
	}

	public HttpResponse<JsonNode> put(String path, JsonNode data) throws IOException, InterruptedException {
		HttpRequest request = initRequest(path)
			.header("Content-Type", this.mediaType.toString())
			.PUT(this.jsonNodePublisher(data))
			.build();
		HttpResponse<JsonNode> response =
			this.httpClient.send(request, this.jsonNodeHandler());
		return response;
	}

	public HttpResponse<JsonNode> delete(String path) throws IOException, InterruptedException {
		HttpRequest request = initRequest(path).DELETE().build();
		HttpResponse<JsonNode> response =
			this.httpClient.send(request, this.jsonNodeHandler());
		return response;
	}
}
