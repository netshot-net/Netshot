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
package net.netshot.netshot.device.access;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.ClientConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.credentials.DeviceHttpAccount;
import net.netshot.netshot.utils.HttpsCaTrustMode;
import net.netshot.netshot.utils.HttpsTrustPolicy;
import net.netshot.netshot.vault.VaultException;
import net.netshot.netshot.vault.VaultManager;
import net.netshot.netshot.vault.VaultableSecret;
import net.netshot.netshot.work.TaskContext;

/**
 * An HTTP(S) client to access a device REST/HTTP API.
 * <p>
 * TLS trust is driven by the access's configured {@link HttpsCaTrustMode}
 * (trust-any/system truststore/custom CA) plus an independent hostname
 * verification toggle - see {@link #applyTrustPolicy}.
 */
@Slf4j
public class Http implements Client {

	public static final int DEFAULT_PORT = 443;

	/**
	 * Declarative HTTP authentication scheme for an access, inspired by
	 * OpenAPI's {@code securitySchemes} object.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static final class AuthScheme {
		/** "http", "apiKey", "cookie", "oauth2" or "openIdConnect". */
		@Getter(onMethod = @__({ @XmlElement }))
		@Setter
		private String type;

		/** For type "http": "basic" or "bearer". */
		@Getter(onMethod = @__({ @XmlElement }))
		@Setter
		private String scheme;

		/** For type "apiKey": "header", "query" or "cookie". */
		@Getter(onMethod = @__({ @XmlElement }))
		@Setter
		private String in;

		/** For type "apiKey": the header/query/cookie name. */
		@Getter(onMethod = @__({ @XmlElement }))
		@Setter
		private String name;

		/** For type "cookie": the HTTP method used to log in ("POST" or "PUT"). */
		@Getter(onMethod = @__({ @XmlElement }))
		@Setter
		private String method;

		/** For type "cookie": the login request path (relative to the access's base path). */
		@Getter(onMethod = @__({ @XmlElement }))
		@Setter
		private String path;

		/** For type "cookie": the login request body, with {@code $$NetshotUsername$$}/
		 * {@code $$NetshotPassword$$} placeholders substituted from the resolved credential. */
		@Getter(onMethod = @__({ @XmlElement }))
		@Setter
		private Map<String, Object> data;

		/** For type "cookie": "json" or "form" - how {@link #data} is encoded in the login request body. */
		@Getter(onMethod = @__({ @XmlElement }))
		@Setter
		private String contentType;

		public AuthScheme() {
		}
	}

	/**
	 * Embedded class to represent HTTP-specific access configuration.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static final class HttpConfig {
		/** Default TCP port for this access, when not overridden on the device. */
		@Getter(onMethod = @__({ @XmlElement }))
		@Setter
		private int defaultPort = DEFAULT_PORT;

		/** Whether this access uses TLS ("https") or plain HTTP. */
		@Getter(onMethod = @__({ @XmlElement }))
		@Setter
		private boolean tls = true;

		/** Optional base path prepended to every request path. */
		@Getter(onMethod = @__({ @XmlElement }))
		@Setter
		private String basePath;

		/** Declared authentication scheme for this access (may be null = no auth). */
		@Getter(onMethod = @__({ @XmlElement }))
		@Setter
		private AuthScheme auth;

		public HttpConfig() {
		}
	}

	/**
	 * The result of an HTTP request, handed back to JS.
	 */
	public static final class HttpResult {
		@Getter
		private final int status;
		@Getter
		private final Map<String, String> headers;
		@Getter
		private final String body;

		public HttpResult(int status, Map<String, String> headers, String body) {
			this.status = status;
			this.headers = headers;
			this.body = body;
		}
	}

	/**
	 * The result of an HTTP download (response body streamed straight to a
	 * file instead of decoded as a {@link String}), handed back to JS.
	 */
	public static final class HttpDownloadResult {
		@Getter
		private final int status;
		@Getter
		private final Map<String, String> headers;
		@Getter
		private final long size;

		public HttpDownloadResult(int status, Map<String, String> headers, long size) {
			this.status = status;
			this.headers = headers;
			this.size = size;
		}
	}

	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	/** The target host (IPv4/IPv6 literal or FQDN) - DNS resolution is left to the
	 * underlying Jersey/JAX-RS client, so that TLS hostname/certificate validation
	 * matches the device's certificate (typically issued for its DNS name). */
	private final String host;
	private final int port;
	private final boolean tls;
	private final TaskContext taskContext;

	/** The underlying JAX-RS (Jersey) client - built lazily on first connect(). */
	private jakarta.ws.rs.client.Client jerseyClient;

	/**
	 * Session cookies obtained via a "cookie" auth login request, replayed on
	 * every subsequent request made through this same {@link Http} instance.
	 */
	private final Map<String, String> sessionCookies = new HashMap<>();

	/** Whether the "cookie" auth login request has already been attempted. */
	private boolean cookieSessionAttempted = false;

	/** CA trust mode for this access (defaults to the system truststore). */
	private HttpsCaTrustMode caTrustMode = HttpsCaTrustMode.SYSTEM_TRUSTSTORE;

	/** PEM-encoded trust anchor certificate(s), used when {@link #caTrustMode} is {@code CUSTOM_CA}. */
	private String customCaCertificate;

	/**
	 * Instantiates a new HTTP access.
	 * @param host the target host (IPv4/IPv6 literal or FQDN)
	 * @param port the target TCP port
	 * @param tls whether to use TLS (https) or not
	 * @param taskContext the current task context
	 */
	public Http(String host, int port, boolean tls, TaskContext taskContext) {
		this.host = host;
		this.port = port;
		this.tls = tls;
		this.taskContext = taskContext;
	}

	/**
	 * Applies the access's configured TLS trust policy, read from its {@link DeviceAccess} row.
	 * Only effective for a TLS ({@code https}) access - a no-op otherwise. Must be called before
	 * {@link #connect()}.
	 * @param trustMode the CA trust mode (defaults to {@code SYSTEM_TRUSTSTORE} if null)
	 * @param caCertificate the PEM-encoded trust anchor certificate(s), used when
	 *        {@code trustMode} is {@code CUSTOM_CA}
	 */
	public void applyTrustPolicy(HttpsCaTrustMode trustMode, String caCertificate) {
		this.caTrustMode = trustMode == null ? HttpsCaTrustMode.SYSTEM_TRUSTSTORE : trustMode;
		this.customCaCertificate = caCertificate;
	}

	@Override
	public void connect() throws IOException {
		if (this.jerseyClient != null) {
			return;
		}
		try {
			ClientConfig config = new ClientConfig();
			ClientBuilder builder = ClientBuilder.newBuilder().withConfig(config);
			if (this.tls) {
				SSLContext sslContext = HttpsTrustPolicy.buildSslContext(this.caTrustMode, this.customCaCertificate);
				builder.sslContext(sslContext);
				HostnameVerifier hostnameVerifier = HttpsTrustPolicy.buildHostnameVerifier(this.caTrustMode);
				if (hostnameVerifier != null) {
					builder.hostnameVerifier(hostnameVerifier);
				}
			}
			this.jerseyClient = builder.build();
		}
		catch (Exception e) {
			throw new IOException("Unable to initialize the HTTP client.", e);
		}
	}

	@Override
	public void disconnect() {
		if (this.jerseyClient != null) {
			this.jerseyClient.close();
			this.jerseyClient = null;
		}
	}

	/**
	 * Injects the authentication data (per the access's declared auth scheme)
	 * into the effective headers/query/cookies of the outgoing request.
	 * The credential itself never leaves this method.
	 * <p>
	 * The "type"/"scheme"/"in" values were already validated with exact case
	 * by {@code DeviceDriver.parseHttpAuthScheme}, so they are matched here
	 * with exact case too.
	 * @param httpConfig the access's declared HTTP configuration (base path, auth scheme)
	 * @param account the credential set to authenticate with
	 * @param headers the effective request headers, updated in place
	 * @param query the effective request query parameters, updated in place
	 * @param cookies the effective request cookies, updated in place
	 * @throws IOException if a "cookie" auth login request is needed and fails
	 */
	private void applyAuth(HttpConfig httpConfig, DeviceHttpAccount account,
			Map<String, String> headers, Map<String, String> query, Map<String, String> cookies) throws IOException {
		AuthScheme auth = httpConfig == null ? null : httpConfig.getAuth();
		if (auth == null || account == null || auth.getType() == null) {
			return;
		}
		// Resolved once here (local/Vault, per VaultableSecret) rather than read
		// piecemeal below, so a Vault-backed username/password is only fetched once per request.
		String username = resolveSecret(account.getUsernameSecret());
		String password = resolveSecret(account.getPasswordSecret());
		if ("http".equals(auth.getType())) {
			if ("bearer".equals(auth.getScheme())) {
				if (password != null) {
					headers.put("Authorization", "Bearer " + password);
				}
			}
			else {
				// Basic auth (default for type=http when scheme isn't "bearer")
				String user = username == null ? "" : username;
				String pass = password == null ? "" : password;
				String encoded = Base64.getEncoder()
					.encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
				headers.put("Authorization", "Basic " + encoded);
			}
		}
		else if ("apiKey".equals(auth.getType())) {
			String value = password;
			if (value == null) {
				return;
			}
			String keyName = auth.getName() == null ? "X-API-Key" : auth.getName();
			if ("query".equals(auth.getIn())) {
				query.put(keyName, value);
			}
			else if ("cookie".equals(auth.getIn())) {
				cookies.put(keyName, value);
			}
			else {
				headers.put(keyName, value);
			}
		}
		else if ("cookie".equals(auth.getType())) {
			Map<String, String> loginCookies = this.ensureCookieSession(httpConfig, auth, username, password);
			cookies.putAll(loginCookies);
		}
		// "oauth2" / "openIdConnect": schema is recognized and validated by
		// DeviceDriver, but the token fetch/cache/refresh flow against an IdP
		// is a deliberately deferred fast-follow, not implemented in Phase 1.
	}

	/**
	 * Resolves a credential field's actual value (local, or from Vault),
	 * wrapping a Vault failure as an {@link IOException} so it's handled the
	 * same way as any other request failure by callers.
	 * @param secret the vaultable secret to resolve
	 * @return the resolved value
	 */
	private static String resolveSecret(VaultableSecret secret) throws IOException {
		try {
			return VaultManager.resolve(secret);
		}
		catch (VaultException e) {
			throw new IOException("Unable to resolve a Vault-backed credential: " + e.getMessage(), e);
		}
	}

	/**
	 * Logs in (once per {@link Http} instance) against the "cookie" auth
	 * scheme's declared endpoint, and returns the session cookie(s) captured
	 * from its response - cached so later requests on this same instance
	 * replay them without logging in again.
	 * @param httpConfig the access's declared HTTP configuration (for the base path)
	 * @param auth the "cookie" auth scheme (method/path/data/contentType)
	 * @param username the resolved username to substitute into the login body
	 * @param password the resolved password to substitute into the login body
	 * @return the session cookies to replay on every request
	 * @throws IOException if the login request could not be sent, or came back with a non-2xx status
	 */
	private Map<String, String> ensureCookieSession(HttpConfig httpConfig, AuthScheme auth,
			String username, String password) throws IOException {
		if (this.cookieSessionAttempted) {
			return this.sessionCookies;
		}
		this.connect();
		Object substitutedData = substitutePlaceholders(auth.getData(), username, password);
		Entity<?> entity;
		if ("form".equals(auth.getContentType())) {
			entity = Entity.form(toForm(substitutedData));
		}
		else {
			String body;
			try {
				body = JSON_MAPPER.writeValueAsString(substitutedData == null ? Map.of() : substitutedData);
			}
			catch (Exception e) {
				throw new IOException("Unable to serialize the cookie-auth login body.", e);
			}
			entity = Entity.entity(body, MediaType.APPLICATION_JSON_TYPE);
		}
		URI uri = this.buildUri(auth.getPath(), httpConfig);
		String method = auth.getMethod() == null ? "POST" : auth.getMethod();
		if (this.taskContext.isTracing()) {
			// Trace the login request template (with the $$NetshotUsername$$/
			// $$NetshotPassword$$ placeholders still in place), never the
			// substituted body, so the credential itself never reaches the trace log.
			this.taskContext.trace("About to send the following cookie-auth login request (secrets not inserted):");
			this.taskContext.trace("{} {}", method, uri);
			this.taskContext.trace("{}", auth.getData() == null ? Map.of() : auth.getData());
		}
		Invocation.Builder invocationBuilder = this.buildInvocation(uri, Map.of(), Map.of(), Map.of(), true);
		Response response;
		try {
			response = invocationBuilder.method(method, entity);
		}
		catch (ProcessingException e) {
			log.warn("Cookie-auth login request to {} failed.", uri, e);
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("I/O exception: {}", e.getMessage());
			}
			throw new IOException("Cookie-auth login request failed: " + e.getMessage(), e);
		}
		try {
			int status = response.getStatus();
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("Received the following cookie-auth login response:");
				this.taskContext.trace("Status: {}", status);
			}
			if (status < 200 || status >= 300) {
				throw new IOException("Cookie-auth login failed (HTTP status " + status + ").");
			}
			for (NewCookie cookie : response.getCookies().values()) {
				this.sessionCookies.put(cookie.getName(), cookie.getValue());
			}
			if (this.taskContext.isTracing()) {
				// Cookie names only - the values are session credentials, replayed
				// on every later request, so they are kept out of the trace log.
				this.taskContext.trace("Captured {} session cookie(s): {}", this.sessionCookies.size(),
					this.sessionCookies.keySet());
			}
		}
		finally {
			response.close();
		}
		this.cookieSessionAttempted = true;
		return this.sessionCookies;
	}

	/**
	 * Recursively substitutes the {@code $$NetshotUsername$$}/{@code $$NetshotPassword$$}
	 * placeholders (see {@link DeviceDriver#PLACEHOLDER_USERNAME}/{@link DeviceDriver#PLACEHOLDER_PASSWORD})
	 * in a driver-declared "cookie" auth login body with the resolved credential's values.
	 * @param value the value to substitute into (String, Map, List, or any other plain object)
	 * @param username the resolved username to substitute
	 * @param password the resolved password to substitute
	 * @return the substituted value
	 */
	private static Object substitutePlaceholders(Object value, String username, String password) {
		if (value instanceof String s) {
			String result = s.replaceAll(Pattern.quote(DeviceDriver.PLACEHOLDER_USERNAME),
				Matcher.quoteReplacement(StringUtils.defaultString(username)));
			result = result.replaceAll(Pattern.quote(DeviceDriver.PLACEHOLDER_PASSWORD),
				Matcher.quoteReplacement(StringUtils.defaultString(password)));
			return result;
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> result = new HashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				result.put(String.valueOf(entry.getKey()), substitutePlaceholders(entry.getValue(), username, password));
			}
			return result;
		}
		if (value instanceof List<?> list) {
			List<Object> result = new ArrayList<>();
			for (Object item : list) {
				result.add(substitutePlaceholders(item, username, password));
			}
			return result;
		}
		return value;
	}

	/**
	 * Converts a flat map into a JAX-RS {@link Form}, for an
	 * {@code application/x-www-form-urlencoded} login request body.
	 * @param data the data to convert (expected to be a flat {@code Map<String, Object>})
	 * @return the form
	 */
	private static Form toForm(Object data) {
		Form form = new Form();
		if (data instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				form.param(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
			}
		}
		return form;
	}

	/**
	 * Builds the absolute request URI for a given path, prepending the access's base path.
	 * Uses {@link #host} (the originally configured IPv4/IPv6 literal or FQDN) as-is,
	 * leaving DNS resolution to the underlying Jersey/JAX-RS client, so TLS hostname/
	 * certificate validation matches the device's certificate.
	 * @param path the request path (may be null/empty)
	 * @param httpConfig the access's declared HTTP configuration (for the base path)
	 * @return the absolute URI
	 */
	private URI buildUri(String path, HttpConfig httpConfig) {
		StringBuilder pathBuilder = new StringBuilder();
		if (httpConfig != null && httpConfig.getBasePath() != null && !httpConfig.getBasePath().isEmpty()) {
			String base = httpConfig.getBasePath();
			pathBuilder.append(base.startsWith("/") ? base : "/" + base);
		}
		if (path != null && !path.isEmpty()) {
			pathBuilder.append(path.startsWith("/") ? path : "/" + path);
		}
		return UriBuilder.newInstance()
			.scheme(this.tls ? "https" : "http")
			.host(this.host)
			.port(this.port)
			.replacePath(pathBuilder.toString())
			.build();
	}

	/**
	 * Builds a Jersey invocation for a given URI, with query params/headers/cookies applied.
	 * @param uri the absolute request URI
	 * @param headers the request headers to apply. A "Content-Type" entry is skipped only when
	 *        {@code entityWillSetContentType} is true - i.e. when the caller is about to attach a
	 *        body via {@link Entity#entity(Object, String)}, which sets the Content-Type header
	 *        itself (a duplicate/conflicting header set here would fight with it). Otherwise (no
	 *        body - the common case for a GET), a driver-provided "Content-Type" is sent as a plain
	 *        header like any other: some servers unusually insist on seeing one even without a body,
	 *        and otherwise there would be no way for a driver to work around that.
	 * @param query the request query parameters to apply
	 * @param cookies the request cookies to apply
	 * @param entityWillSetContentType whether the caller is about to attach a body entity (which sets its own Content-Type)
	 * @return the configured invocation builder
	 */
	private Invocation.Builder buildInvocation(URI uri, Map<String, String> headers,
			Map<String, String> query, Map<String, String> cookies, boolean entityWillSetContentType) {
		WebTarget target = this.jerseyClient.target(uri);
		for (Map.Entry<String, String> entry : query.entrySet()) {
			target = target.queryParam(entry.getKey(), entry.getValue());
		}
		Invocation.Builder invocationBuilder = target.request();
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			if (entityWillSetContentType && "Content-Type".equalsIgnoreCase(entry.getKey())) {
				continue;
			}
			invocationBuilder = invocationBuilder.header(entry.getKey(), entry.getValue());
		}
		for (Map.Entry<String, String> entry : cookies.entrySet()) {
			invocationBuilder = invocationBuilder.cookie(entry.getKey(), entry.getValue());
		}
		return invocationBuilder;
	}

	/**
	 * Builds and sends the JAX-RS invocation shared by {@link #request} and
	 * {@link #download} (URI/auth/header/query/cookie preparation, method
	 * selection). The returned {@link Response} is not yet closed nor read -
	 * callers decide how to consume the body (as a {@link String} for
	 * {@link #request}, streamed to a file for {@link #download}).
	 * @param method the HTTP method (GET, POST, ...)
	 * @param path the request path (appended to the access's base path)
	 * @param headers extra request headers (driver-provided, may be null)
	 * @param query extra query parameters (driver-provided, may be null)
	 * @param cookies extra cookies (driver-provided, may be null)
	 * @param body the request body (null for none)
	 * @param httpConfig the access's declared HTTP configuration (base path, auth scheme)
	 * @param account the resolved credential set (may be null if the access has no auth)
	 * @return the not-yet-consumed response
	 * @throws IOException if the request could not be sent
	 */
	private Response sendRequest(String method, String path, Map<String, String> headers,
			Map<String, String> query, Map<String, String> cookies, String body,
			HttpConfig httpConfig, DeviceHttpAccount account) throws IOException {
		this.connect();

		URI uri = this.buildUri(path, httpConfig);

		Map<String, String> effectiveHeaders = new HashMap<>();
		if (headers != null) {
			effectiveHeaders.putAll(headers);
		}
		Map<String, String> effectiveQuery = new HashMap<>();
		if (query != null) {
			effectiveQuery.putAll(query);
		}
		Map<String, String> effectiveCookies = new HashMap<>();
		if (cookies != null) {
			effectiveCookies.putAll(cookies);
		}

		if (httpConfig != null) {
			this.applyAuth(httpConfig, account, effectiveHeaders, effectiveQuery, effectiveCookies);
		}

		String upperMethod = method == null ? "GET" : method.toUpperCase();
		boolean hasEntityBody = body != null && !"GET".equals(upperMethod) && !"HEAD".equals(upperMethod);

		Invocation.Builder invocationBuilder =
			this.buildInvocation(uri, effectiveHeaders, effectiveQuery, effectiveCookies, hasEntityBody);

		try {
			if (hasEntityBody) {
				String contentType = effectiveHeaders.entrySet().stream()
					.filter(e -> "Content-Type".equalsIgnoreCase(e.getKey()))
					.map(Map.Entry::getValue)
					.findFirst()
					.orElse(MediaType.APPLICATION_JSON);
				return invocationBuilder.method(upperMethod, Entity.entity(body, contentType));
			}
			return invocationBuilder.method(upperMethod);
		}
		catch (ProcessingException e) {
			log.warn("HTTP request to {} failed.", uri, e);
			throw new IOException("HTTP request failed: " + e.getMessage(), e);
		}
	}

	/**
	 * Perform an HTTP request, decoding the response body as a (charset-decoded)
	 * {@link String} - fine for JSON/XML/text APIs, but lossy for arbitrary
	 * binary content (see {@link #download}).
	 * @param method the HTTP method (GET, POST, ...)
	 * @param path the request path (appended to the access's base path)
	 * @param headers extra request headers (driver-provided, may be null)
	 * @param query extra query parameters (driver-provided, may be null)
	 * @param cookies extra cookies (driver-provided, may be null)
	 * @param body the request body (null for none)
	 * @param httpConfig the access's declared HTTP configuration (base path, auth scheme)
	 * @param account the resolved credential set (may be null if the access has no auth)
	 * @return the HTTP result (status, headers, body)
	 * @throws IOException if the request could not be sent/received
	 */
	public HttpResult request(String method, String path, Map<String, String> headers,
			Map<String, String> query, Map<String, String> cookies, String body,
			HttpConfig httpConfig, DeviceHttpAccount account) throws IOException {
		Response response = this.sendRequest(method, path, headers, query, cookies, body, httpConfig, account);
		int status = response.getStatus();
		Map<String, String> responseHeaders = new HashMap<>();
		response.getStringHeaders().forEach(
			(name, values) -> responseHeaders.put(name, String.join(", ", values)));
		String responseBody = response.hasEntity() ? response.readEntity(String.class) : "";
		response.close();
		return new HttpResult(status, responseHeaders, responseBody);
	}

	/**
	 * Perform an HTTP request and stream the response body straight to a file
	 * on disk, byte for byte - the binary-safe counterpart to {@link #request},
	 * for endpoints that return arbitrary (non-text) content such as a backup
	 * archive. Mirrors {@link Ssh#scpDownload}/{@link Ssh#sftpDownload}: pure
	 * transport, no checksum/attribute bookkeeping (that's the caller's job).
	 * @param method the HTTP method (GET, POST, ...)
	 * @param path the request path (appended to the access's base path)
	 * @param headers extra request headers (driver-provided, may be null)
	 * @param query extra query parameters (driver-provided, may be null)
	 * @param cookies extra cookies (driver-provided, may be null)
	 * @param body the request body (null for none)
	 * @param httpConfig the access's declared HTTP configuration (base path, auth scheme)
	 * @param account the resolved credential set (may be null if the access has no auth)
	 * @param targetFile where to write the response body (overwritten if it already exists)
	 * @return the HTTP download result (status, headers, downloaded size)
	 * @throws IOException if the request could not be sent/received, or the file could not be written
	 */
	public HttpDownloadResult download(String method, String path, Map<String, String> headers,
			Map<String, String> query, Map<String, String> cookies, String body,
			HttpConfig httpConfig, DeviceHttpAccount account, Path targetFile) throws IOException {
		Response response = this.sendRequest(method, path, headers, query, cookies, body, httpConfig, account);
		try {
			int status = response.getStatus();
			Map<String, String> responseHeaders = new HashMap<>();
			response.getStringHeaders().forEach(
				(name, values) -> responseHeaders.put(name, String.join(", ", values)));
			long size = 0;
			if (response.hasEntity()) {
				try (InputStream is = response.readEntity(InputStream.class)) {
					size = Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			else {
				Files.write(targetFile, new byte[0]);
			}
			return new HttpDownloadResult(status, responseHeaders, size);
		}
		finally {
			response.close();
		}
	}

}
