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
package net.netshot.netshot.device.script.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.proxy.ProxyObject;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.device.DeviceDriver.AccessDefinition;
import net.netshot.netshot.device.access.AccessAuthenticationException;
import net.netshot.netshot.device.access.AccessManager;
import net.netshot.netshot.device.access.AccessManager.Resolution;
import net.netshot.netshot.device.access.Client;
import net.netshot.netshot.device.access.Http;
import net.netshot.netshot.device.access.Http.HttpDownloadResult;
import net.netshot.netshot.device.access.Http.HttpResult;
import net.netshot.netshot.device.access.InvalidCredentialsException;
import net.netshot.netshot.device.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.device.credentials.DeviceCredentialSet;
import net.netshot.netshot.device.credentials.DeviceHttpAccount;
import net.netshot.netshot.work.TaskContext;

/**
 * This class is used to pass HTTP client control to JavaScript.
 * <p>
 * Connection is lazy, like the CLI/SNMP helpers. HTTP has no separate
 * transport-level auth handshake though, so credential validity can only be
 * judged from the response of an actual request: a 401/403 on the <em>first</em>
 * request made through a given client is treated as "this credential didn't
 * work" (mirroring the CLI/SNMP "first use" fallback scope) - later requests
 * on an already-resolved client just return whatever status the server gives.
 */
@Slf4j
public class JsHttpHelper {

	private final AccessManager accessManager;
	private final Resolution resolution;
	private final boolean autoTryCredentials;
	private final TaskContext taskContext;
	private final String basePath;
	/** May be null (e.g. in a "run"/diagnostic script context, which has no config to write to). */
	private final JsConfigHelper configHelper;

	private Http http;
	private DeviceHttpAccount account;
	private AccessDefinition accessDef;
	private boolean firstRequestDone = false;

	/**
	 * Instantiate a new JsHttpHelper object.
	 * @param accessManager the access manager (shared across all clients of this task attempt)
	 * @param accessDefs the ordered list of HTTP accesses to try
	 * @param autoTryCredentials whether to transparently loop through all candidate
	 *        credential sets (true) or stop at the first failure (false)
	 * @param basePath an extra base path to prepend to every request (from {@code client.create("http", {basePath})})
	 * @param taskContext The task context
	 * @param configHelper the current script's config helper (may be null), needed by
	 *        {@link #download(String, String, String, Map, Map, Map, String, String, String)}
	 *        to land a downloaded file into a {@code BinaryFile} config attribute
	 */
	public JsHttpHelper(AccessManager accessManager, List<AccessDefinition> accessDefs,
			boolean autoTryCredentials, String basePath, TaskContext taskContext, JsConfigHelper configHelper) {
		this.accessManager = accessManager;
		this.autoTryCredentials = autoTryCredentials;
		this.basePath = basePath;
		this.taskContext = taskContext;
		this.configHelper = configHelper;
		this.resolution = accessManager.newResolution(accessDefs, this::buildClient);
	}

	private Client buildClient(AccessDefinition candidateAccessDef, DeviceCredentialSet credentialSet) throws IOException {
		Http.HttpConfig httpConfig = candidateAccessDef.getHttpConfig();
		String host = this.accessManager.resolveHost(candidateAccessDef);
		int port = this.accessManager.resolvePort(candidateAccessDef);
		this.taskContext.debug("Trying access '{}' ({}) using credentials '{}', at {}:{}.",
			candidateAccessDef.getName(), candidateAccessDef.getProtocol(), credentialSet.getName(), host, port);
		Http httpClient = new Http(host, port, httpConfig.isTls(), this.taskContext);
		this.accessManager.applyHttpsTrustPolicy(candidateAccessDef, httpClient);
		return httpClient;
	}

	private void ensureResolved() throws IOException {
		if (this.http != null) {
			return;
		}
		Client client = this.resolution.ensureResolved(this.autoTryCredentials);
		this.http = (Http) client;
		this.account = (DeviceHttpAccount) this.resolution.getCurrentCredentialSet();
		this.accessDef = this.resolution.getCurrentAccessDef();
	}

	private boolean isAuthStatus(int status) {
		return status == 401 || status == 403;
	}

	private HttpResult doRequest(String method, String path, Map<String, String> headers,
			Map<String, String> query, Map<String, String> cookies, String body) throws IOException {
		String fullPath = this.buildFullPath(path);
		String upperMethod = method == null ? "GET" : method.toUpperCase();
		if (this.taskContext.isTracing()) {
			// Trace the request as built by the driver, i.e. before authentication
			// data (Authorization header, API key, session cookie...) is injected,
			// so that no secret ever gets written to the trace log.
			this.taskContext.trace("About to send the following HTTP request (before authentication is applied):");
			this.taskContext.trace("{} {}", upperMethod, fullPath);
			this.taskContext.trace("Headers: {}", headers);
			this.taskContext.trace("Query parameters: {}", query);
			this.taskContext.trace("Cookies: {}", cookies);
			if (body != null) {
				this.taskContext.trace("Body:");
				this.taskContext.trace(body);
			}
		}
		try {
			HttpResult result = this.http.request(method, fullPath, headers, query, cookies, body,
				this.accessDef.getHttpConfig(), this.account);
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("Received the following HTTP response:");
				this.taskContext.trace("Status: {}", result.getStatus());
				this.taskContext.trace("Headers: {}", result.getHeaders());
				this.taskContext.trace("Body:");
				this.taskContext.trace(result.getBody());
			}
			return result;
		}
		catch (IOException e) {
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("I/O exception: {}", e.getMessage());
			}
			throw e;
		}
	}

	/**
	 * Builds the full request path, prepending this client's basePath (from
	 * {@code client.create("http", {basePath})}), shared by {@link #doRequest}
	 * and {@link #doDownload}.
	 * @param path the request path to prepend the basePath to
	 * @return the full path, with basePath prepended if set
	 */
	private String buildFullPath(String path) {
		if (this.basePath == null || this.basePath.isEmpty()) {
			return path;
		}
		String base = this.basePath.startsWith("/") ? this.basePath : "/" + this.basePath;
		String p = (path == null) ? "" : (path.startsWith("/") ? path : "/" + path);
		return base + p;
	}

	/**
	 * Runs one attempt (a request or a download) and, on the very first
	 * attempt made through this client, applies the same "first use" 401/403
	 * credential-fallback logic for both: retries with the next candidate
	 * credential set (when {@code autoTryCredentials} is enabled) or fails
	 * outright, then pins the working credential set once confirmed. Shared
	 * by {@link #request} and {@link #download} so this logic - and its
	 * "first request only" semantics - lives in exactly one place.
	 * @param <T> the attempt's result type (HttpResult or HttpDownloadResult)
	 * @param attempt performs one request/download against the currently resolved client
	 * @param statusOf extracts the HTTP status from an attempt's result
	 * @return the result of the (possibly retried) attempt
	 * @throws IOException on connection failure, or if no credential worked
	 */
	private <T> T withCredentialRetry(IOSupplier<T> attempt, ToIntFunction<T> statusOf) throws IOException {
		this.ensureResolved();
		T result = attempt.get();
		if (!this.firstRequestDone) {
			this.firstRequestDone = true;
			if (this.isAuthStatus(statusOf.applyAsInt(result))) {
				if (this.autoTryCredentials) {
					while (this.isAuthStatus(statusOf.applyAsInt(result))) {
						if (!this.tryNextCredentials()) {
							break;
						}
						result = attempt.get();
					}
					if (this.isAuthStatus(statusOf.applyAsInt(result))) {
						throw new InvalidCredentialsException(
							"Couldn't find valid HTTP credentials (last status " + statusOf.applyAsInt(result) + ").");
					}
				}
				else {
					throw new AccessAuthenticationException(
						"Authentication failed for this access (HTTP status " + statusOf.applyAsInt(result) + ").");
				}
			}
			// The first attempt came back without an auth-status rejection (either
			// from the start, or after tryNextCredentials() found one that works),
			// so the credentials are confirmed valid.
			this.resolution.confirmCredentialWorks();
		}
		return result;
	}

	/**
	 * A {@link java.util.function.Supplier} allowed to throw {@link IOException}.
	 * @param <T> the type of result supplied
	 */
	@FunctionalInterface
	private interface IOSupplier<T> {
		T get() throws IOException;
	}

	/**
	 * Manually advances to the next candidate credential set (only relevant
	 * when {@code autoTryCredentials} is disabled for this client).
	 * @return true if a new client is now connected, false if exhausted
	 */
	@Export
	public boolean tryNextCredentials() {
		boolean success = this.resolution.tryNextCredentials();
		if (success) {
			this.http = (Http) this.resolution.getCurrentClient();
			this.account = (DeviceHttpAccount) this.resolution.getCurrentCredentialSet();
			this.accessDef = this.resolution.getCurrentAccessDef();
		}
		else {
			this.http = null;
			this.account = null;
			this.accessDef = null;
		}
		return success;
	}

	/**
	 * Pause the thread for the given number of milliseconds (e.g. while polling
	 * an asynchronous job on the remote HTTP API).
	 * @param millis The number of milliseconds to wait for
	 */
	@Export
	public void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
		}
	}

	/**
	 * Perform an HTTP request.
	 * @param method the HTTP method
	 * @param path the request path
	 * @param headers extra headers (may be null)
	 * @param query extra query parameters (may be null)
	 * @param cookies extra cookies (may be null)
	 * @param body the request body (may be null)
	 * @return a map with "status", "headers" and "body" entries
	 * @throws IOException on connection failure, or if no credential worked
	 */
	@Export
	public ProxyObject request(String method, String path, Map<String, String> headers,
			Map<String, String> query, Map<String, String> cookies, String body) throws IOException {
		HttpResult result = this.withCredentialRetry(
			() -> this.doRequest(method, path, headers, query, cookies, body),
			HttpResult::getStatus);
		Map<String, Object> jsResult = new HashMap<>();
		jsResult.put("status", result.getStatus());
		jsResult.put("headers", result.getHeaders());
		jsResult.put("body", result.getBody());
		return ProxyObject.fromMap(jsResult);
	}

	private HttpDownloadResult doDownload(String method, String path, Map<String, String> headers,
			Map<String, String> query, Map<String, String> cookies, String body, Path targetFile) throws IOException {
		String fullPath = this.buildFullPath(path);
		if (this.taskContext.isTracing()) {
			// Same rationale as doRequest(): traced before authentication is
			// applied, so no secret ever reaches the trace log.
			this.taskContext.trace("About to send the following HTTP download request (before authentication is applied):");
			this.taskContext.trace("{} {}", method == null ? "GET" : method.toUpperCase(), fullPath);
			this.taskContext.trace("Headers: {}", headers);
			this.taskContext.trace("Query parameters: {}", query);
			this.taskContext.trace("Cookies: {}", cookies);
		}
		try {
			HttpDownloadResult result = this.http.download(method, fullPath, headers, query, cookies, body,
				this.accessDef.getHttpConfig(), this.account, targetFile);
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("Received the following HTTP download response:");
				this.taskContext.trace("Status: {}", result.getStatus());
				this.taskContext.trace("Headers: {}", result.getHeaders());
				this.taskContext.trace("Downloaded {} byte(s) to a temporary file.", result.getSize());
			}
			return result;
		}
		catch (IOException e) {
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("I/O exception: {}", e.getMessage());
			}
			throw e;
		}
	}

	/**
	 * Perform an HTTP request and land its response body straight into a
	 * {@code BinaryFile} config attribute, byte for byte - the binary-safe
	 * counterpart to {@link #request}, whose response "body" is always a
	 * (charset-decoded) String and therefore lossy for arbitrary binary
	 * content such as a backup archive. Mirrors how the SSH/CLI side does
	 * this in one call ({@code config.download(key, "scp"|"sftp", ...)}
	 * driving {@code Ssh.scpDownload}/{@code sftpDownload}) - there is no
	 * separate "commit" step, since the only reason a downloaded file would
	 * ever need one is if some other party (not this call) produced it, which
	 * isn't the case here. The same "first use" credential fallback as
	 * {@link #request} applies.
	 * @param key the name of the (BinaryFile) config attribute to store the download into
	 * @param method the HTTP method (GET, POST, ...)
	 * @param path the request path (may be null)
	 * @param headers extra headers (may be null)
	 * @param query extra query parameters (may be null)
	 * @param cookies extra cookies (may be null)
	 * @param body the request body (may be null)
	 * @param storeFileName the file name to store (null to derive one from the request path)
	 * @param expectedHash if not null, the expected checksum (MD5 or SHA256) of the downloaded file
	 * @return a map with "status", "headers" and "size" entries
	 * @throws Exception on connection failure, if no credential worked, if there's no config
	 *         in the current script context, if the attribute is unknown/not a BinaryFile,
	 *         or if the checksum doesn't match
	 */
	@Export
	public ProxyObject download(String key, String method, String path, Map<String, String> headers,
			Map<String, String> query, Map<String, String> cookies, String body,
			String storeFileName, String expectedHash) throws Exception {
		if (this.configHelper == null) {
			throw new IllegalStateException(
				"No config is available in the current script context; can't store a downloaded file.");
		}
		ConfigBinaryFileAttribute fileAttribute = this.configHelper.prepareBinaryFileDownload(key, storeFileName, path);
		Path tempPath = fileAttribute.getTempFilePath();
		tempPath.toFile().deleteOnExit();
		HttpDownloadResult result;
		try {
			result = this.withCredentialRetry(
				() -> this.doDownload(method, path, headers, query, cookies, body, tempPath),
				HttpDownloadResult::getStatus);
		}
		catch (IOException e) {
			Files.deleteIfExists(tempPath);
			throw e;
		}
		this.configHelper.finalizeBinaryFile(fileAttribute, tempPath, expectedHash);
		Map<String, Object> jsResult = new HashMap<>();
		jsResult.put("status", result.getStatus());
		jsResult.put("headers", result.getHeaders());
		jsResult.put("size", result.getSize());
		return ProxyObject.fromMap(jsResult);
	}

}
