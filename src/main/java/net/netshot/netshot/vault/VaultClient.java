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
package net.netshot.netshot.vault;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ClientCredentialsGrant;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.tls.HttpsTrustPolicy;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;

/**
 * Low-level HTTP client for a HashiCorp Vault KV v2 instance: AppRole/JWT
 * login, and reading a KV v2 secret path. Stateless - callers (VaultManager)
 * own token/secret caching.
 */
@Slf4j
final class VaultClient {

	private static final ObjectMapper JSON = new ObjectMapper();

	private VaultClient() {
	}

	private static Client buildHttpClient(VaultInstance instance, VaultManager.Settings settings) throws VaultException {
		try {
			log.trace("Building HTTP client for Vault instance '{}' (base URL '{}').",
				instance.name(), instance.baseUrl());
			ClientBuilder clientBuilder = ClientBuilder.newBuilder();
			if (instance.baseUrl() != null && instance.baseUrl().startsWith("https")) {
				SSLContext sslContext = HttpsTrustPolicy.buildSslContext(
					instance.httpsCaTrustMode(), instance.httpsCustomCaCertificate());
				clientBuilder.sslContext(sslContext);
				HostnameVerifier hostnameVerifier = HttpsTrustPolicy.buildHostnameVerifier(instance.httpsCaTrustMode());
				if (hostnameVerifier != null) {
					clientBuilder.hostnameVerifier(hostnameVerifier);
				}
			}
			clientBuilder.connectTimeout(
				settings.httpConnectTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
			clientBuilder.readTimeout(
				settings.httpReadTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
			return clientBuilder.build();
		}
		catch (GeneralSecurityException | java.io.IOException e) {
			throw new VaultException(
				"Unable to initialize the HTTPS trust policy for Vault instance '%s': %s"
					.formatted(instance.name(), e.getMessage()), e);
		}
	}

	/**
	 * Logs in to the given Vault instance, using its configured auth method.
	 * @param instance the Vault instance
	 * @param settings the settings of the Vault client (HTTP timeouts)
	 * @return the resulting client token
	 * @throws VaultException in case of login failure
	 */
	static VaultToken login(HashicorpVaultKv2Instance instance, VaultManager.Settings settings) throws VaultException {
		log.trace("Logging in to Vault instance '{}' using {} auth.", instance.name(), instance.authMethod());
		if (instance.authMethod() == AuthMethod.JWT) {
			String jwt = obtainJwtFromIdp(instance);
			return loginWithPayload(instance, settings, "%s/login".formatted(instance.jwtMountPath()), Map.of(
				"role", instance.jwtVaultRole(),
				"jwt", jwt
			));
		}
		else {
			return loginWithPayload(instance, settings, "%s/login".formatted(instance.appRoleMountPath()), Map.of(
				"role_id", instance.appRoleId(),
				"secret_id", instance.appRoleSecretId()
			));
		}
	}

	private static String obtainJwtFromIdp(HashicorpVaultKv2Instance instance) throws VaultException {
		try {
			log.trace("Obtaining a JWT from IdP token endpoint '{}' for Vault instance '{}'.",
				instance.jwtIdpTokenEndpoint(), instance.name());
			ClientAuthentication clientAuth = new ClientSecretBasic(
				new ClientID(instance.jwtClientId()), new Secret(instance.jwtClientSecret()));
			AuthorizationGrant grant = new ClientCredentialsGrant();
			TokenRequest.Builder builder = new TokenRequest.Builder(
				new URI(instance.jwtIdpTokenEndpoint()), clientAuth, grant);
			if (instance.jwtScope() != null && !instance.jwtScope().isBlank()) {
				builder.scope(new Scope(instance.jwtScope()));
			}
			HTTPResponse httpResponse = builder.build().toHTTPRequest().send();
			TokenResponse tokenResponse = TokenResponse.parse(httpResponse);
			if (!tokenResponse.indicatesSuccess()) {
				TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
				throw new VaultException("Failed to obtain JWT from IdP for Vault instance '%s': %s"
					.formatted(instance.name(), errorResponse.getErrorObject().getDescription()));
			}
			AccessTokenResponse successResponse = tokenResponse.toSuccessResponse();
			log.trace("Successfully obtained a JWT from the IdP for Vault instance '{}'.", instance.name());
			return successResponse.getTokens().getAccessToken().getValue();
		}
		catch (VaultException e) {
			throw e;
		}
		catch (Exception e) {
			throw new VaultException("Error while obtaining JWT from IdP for Vault instance '%s': %s"
				.formatted(instance.name(), e.getMessage()), e);
		}
	}

	private static VaultToken loginWithPayload(HashicorpVaultKv2Instance instance, VaultManager.Settings settings, String authPath,
			Map<String, String> payload) throws VaultException {
		Client client = buildHttpClient(instance, settings);
		try {
			String uri = "%s/v1/auth/%s".formatted(stripTrailingSlash(instance.baseUrl()), authPath);
			log.trace("POSTing Vault login request to '{}'.", uri);
			jakarta.ws.rs.client.Invocation.Builder request = client.target(uri).request(MediaType.APPLICATION_JSON);
			if (instance.namespace() != null && !instance.namespace().isBlank()) {
				request = request.header("X-Vault-Namespace", instance.namespace());
			}
			String body = JSON.writeValueAsString(payload);
			Response response = request.post(Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
			String responseBody = response.readEntity(String.class);
			if (response.getStatus() != 200) {
				throw new VaultException("Vault login failed for instance '%s' (HTTP %d): %s"
					.formatted(instance.name(), response.getStatus(), responseBody));
			}
			JsonNode root = JSON.readTree(responseBody);
			JsonNode auth = root.path("auth");
			String clientToken = auth.path("client_token").asText(null);
			if (clientToken == null) {
				throw new VaultException(
					"Vault login response for instance '%s' didn't include a client token"
						.formatted(instance.name()));
			}
			long leaseSeconds = auth.path("lease_duration").asLong(0);
			long expiresAt = System.currentTimeMillis() + Math.max(leaseSeconds, 1) * 1000;
			log.trace("Vault login for instance '{}' succeeded, token lease {}s.", instance.name(), leaseSeconds);
			return new VaultToken(clientToken, expiresAt);
		}
		catch (VaultException e) {
			throw e;
		}
		catch (Exception e) {
			throw new VaultException("Error while logging in to Vault instance '%s': %s"
				.formatted(instance.name(), e.getMessage()), e);
		}
		finally {
			client.close();
		}
	}

	/**
	 * Reads a KV v2 secret path, returning its full JSON data node.
	 * @param instance the Vault instance
	 * @param path the KV v2 secret path (relative to the mount)
	 * @param clientToken the Vault client token to authenticate with
	 * @param settings the settings of the Vault client (HTTP timeouts)
	 * @return the secret's data node
	 * @throws VaultException in case of read failure
	 */
	static JsonNode readKv2(HashicorpVaultKv2Instance instance, String path, String clientToken, VaultManager.Settings settings)
			throws VaultException {
		Client client = buildHttpClient(instance, settings);
		try {
			String mount = instance.kvMountPath() == null || instance.kvMountPath().isBlank()
				? "secret" : instance.kvMountPath();
			String uri = "%s/v1/%s/data/%s".formatted(
				stripTrailingSlash(instance.baseUrl()), stripSlashes(mount), stripSlashes(path));
			log.trace("GETting Vault secret data from '{}'.", uri);
			jakarta.ws.rs.client.Invocation.Builder request = client.target(uri).request(MediaType.APPLICATION_JSON)
				.header("X-Vault-Token", clientToken);
			if (instance.namespace() != null && !instance.namespace().isBlank()) {
				request = request.header("X-Vault-Namespace", instance.namespace());
			}
			Response response = request.get();
			String responseBody = response.readEntity(String.class);
			if (response.getStatus() != 200) {
				throw new VaultException("Vault read failed for instance '%s' path '%s' (HTTP %d): %s"
					.formatted(instance.name(), path, response.getStatus(), responseBody));
			}
			JsonNode root = JSON.readTree(responseBody);
			log.trace("Vault read of path '{}' on instance '{}' succeeded.", path, instance.name());
			return root.path("data").path("data");
		}
		catch (VaultException e) {
			throw e;
		}
		catch (Exception e) {
			throw new VaultException("Error while reading Vault path '%s' on instance '%s': %s"
				.formatted(path, instance.name(), e.getMessage()), e);
		}
		finally {
			client.close();
		}
	}

	private static String stripTrailingSlash(String s) {
		return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
	}

	private static String stripSlashes(String s) {
		String result = s;
		if (result.startsWith("/")) {
			result = result.substring(1);
		}
		if (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}
}
