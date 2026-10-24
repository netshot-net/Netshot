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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.LongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;

/**
 * Central, process-wide manager for Vault-backed secret resolution:
 * <ul>
 * <li>caches Vault client tokens (per Vault instance) and resolved secret
 * data (per Vault instance + path), each with its own freshness check;</li>
 * <li>coalesces concurrent logins/reads for the same instance/path into a
 * single in-flight HTTP call, so a bulk task touching many devices that
 * share one Vault-backed credential set doesn't hammer Vault;</li>
 * <li>is invalidated - locally and, via {@link net.netshot.netshot.cluster.ClusterManager},
 * cluster-wide - whenever a Vault instance's configuration changes.</li>
 * </ul>
 * Never falls back to a stale value on failure: {@link #resolve} throws
 * {@link VaultException} if Vault can't be reached and there is no valid
 * cached entry, and callers must treat that as a hard connection failure.
 */
@Slf4j
public final class VaultManager {

	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/**
	 * Settings/config for this class, shared with {@link VaultClient}.
	 */
	public static final class Settings {

		/** Margin (ms) before a cached client token's expiry at which it's proactively renewed. */
		@Getter
		private int tokenRenewMarginMs;

		/** How long (ms) a resolved secret's JSON data is cached for. */
		@Getter
		private int secretCacheTtlMs;

		/** HTTP connect timeout (ms) for calls to a Vault instance. */
		@Getter
		private int httpConnectTimeoutMs;

		/** HTTP read timeout (ms) for calls to a Vault instance. */
		@Getter
		private int httpReadTimeoutMs;

		/**
		 * Load settings from config.
		 */
		private void load() {
			this.tokenRenewMarginMs = Netshot.getConfig("netshot.vault.token.renewmarginms", 30000, 0, Integer.MAX_VALUE);
			log.debug("The Vault client token renew margin is {}ms", this.tokenRenewMarginMs);

			this.secretCacheTtlMs = Netshot.getConfig("netshot.vault.secret.cachettlms", 60000, 0, Integer.MAX_VALUE);
			log.debug("The Vault secret cache TTL is {}ms", this.secretCacheTtlMs);

			this.httpConnectTimeoutMs = Netshot.getConfig("netshot.vault.http.connecttimeoutms", 5000, 100, Integer.MAX_VALUE);
			log.debug("The Vault HTTP connect timeout is {}ms", this.httpConnectTimeoutMs);

			this.httpReadTimeoutMs = Netshot.getConfig("netshot.vault.http.readtimeoutms", 10000, 100, Integer.MAX_VALUE);
			log.debug("The Vault HTTP read timeout is {}ms", this.httpReadTimeoutMs);
		}
	}

	/** Settings for this class. */
	public static final Settings SETTINGS = new Settings();

	/**
	 * Initialize some additional static variables from global configuration.
	 */
	public static void loadConfig() {
		VaultManager.SETTINGS.load();
	}

	private record SecretCacheKey(long vaultInstanceId, String path) {
	}

	private record CachedSecretData(JsonNode data, long expiresAt) {
		boolean isValid() {
			return System.currentTimeMillis() < this.expiresAt;
		}
	}

	/**
	 * The Vault instances, by ID, read from the database on first use (dropped by {@link #invalidate}).
	 */
	private static final ConcurrentHashMap<Long, VaultInstance> INSTANCE_CACHE = new ConcurrentHashMap<>();

	/** Reads a Vault instance from the database (null if it doesn't exist). */
	private static volatile LongFunction<VaultInstance> instanceLoader =
		id -> new Transactions().read(dsl -> new VaultInstanceRepository().find(dsl, id));

		private static final ConcurrentHashMap<Long, VaultToken> TOKEN_CACHE = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Long, CompletableFuture<VaultToken>> LOGIN_IN_FLIGHT = new ConcurrentHashMap<>();

	private static final ConcurrentHashMap<SecretCacheKey, CachedSecretData> SECRET_CACHE = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<SecretCacheKey, CompletableFuture<JsonNode>> READ_IN_FLIGHT =
		new ConcurrentHashMap<>();

	private VaultManager() {
	}

	/**
	 * Starts the background token-refresh warmer. Safe to call even if no
	 * Vault instance is configured.
	 */
	public static void init() {
		VaultTokenRefreshDaemon.launch();
	}

	/**
	 * Gets a Vault instance, from the cache or the database.
	 *
	 * @param id the ID of the Vault instance
	 * @return the instance, or null if there is no such instance
	 */
	static VaultInstance instance(long id) {
		VaultInstance cached = INSTANCE_CACHE.get(id);
		if (cached != null) {
			return cached;
		}
		VaultInstance instance = instanceLoader.apply(id);
		if (instance != null) {
			INSTANCE_CACHE.put(id, instance);
		}
		return instance;
	}

	/**
	 * Replaces the reader of the Vault instances (tests).
	 *
	 * @param loader the reader, by ID
	 */
	static void setInstanceLoader(LongFunction<VaultInstance> loader) {
		instanceLoader = loader;
		INSTANCE_CACHE.clear();
	}

	/**
	 * Resolves a field's actual value: the local value if it isn't
	 * Vault-backed, or the value read (possibly from cache) from Vault.
	 * @param secret the field to resolve
	 * @return the resolved value
	 * @throws VaultException if the field is Vault-backed and the value
	 *         couldn't be resolved (unreachable/auth failure/missing key) -
	 *         callers must treat this as a hard failure, there is no fallback
	 */
	public static String resolve(VaultableSecret secret) throws VaultException {
		if (!secret.isVaultBacked()) {
			log.trace("Resolving a non-Vault-backed field, returning its local value.");
			return secret.localValue();
		}
		VaultInstance instance = instance(secret.vaultInstanceId());
		if (!(instance instanceof HashicorpVaultKv2Instance hcInstance)) {
			throw new VaultException(
				"Vault-backed field references an unsupported or unresolved Vault instance");
		}
		if (secret.vaultPath() == null || secret.vaultPath().isBlank()) {
			throw new VaultException(
				"Vault-backed field on instance '%s' is missing a path".formatted(hcInstance.name()));
		}
		VaultKeyPath keyPath = VaultKeyPath.parse(secret.vaultPath());
		String token = getToken(hcInstance).clientToken();
		JsonNode data = getSecretData(hcInstance, keyPath.getKvPath(), token);
		JsonNode value = keyPath.resolve(data);
		if (value.isMissingNode()) {
			throw new VaultException("Key '%s' not found at Vault path '%s' on instance '%s'"
				.formatted(keyPath, keyPath.getKvPath(), hcInstance.name()));
		}
		AAA_LOG.info("Resolved a secret from Vault instance '{}', path '{}', key '{}'",
			hcInstance.name(), keyPath.getKvPath(), keyPath);
		return value.isTextual() ? value.asText() : value.toString();
	}

	/**
	 * Gets a valid (cached or freshly obtained) client token for the given
	 * Vault instance, coalescing concurrent callers into a single login.
	 * @param instance the Vault instance
	 * @return the client token
	 * @throws VaultException in case of login failure
	 */
	static VaultToken getToken(HashicorpVaultKv2Instance instance) throws VaultException {
		VaultToken cached = TOKEN_CACHE.get(instance.id());
		if (cached != null && cached.isValid(SETTINGS.getTokenRenewMarginMs())) {
			log.trace("Using cached Vault client token for instance '{}'.", instance.name());
			return cached;
		}
		log.trace("No valid cached Vault client token for instance '{}', logging in.", instance.name());
		CompletableFuture<VaultToken> myFuture = new CompletableFuture<>();
		CompletableFuture<VaultToken> raceWinner = LOGIN_IN_FLIGHT.putIfAbsent(instance.id(), myFuture);
		if (raceWinner == null) {
			// We're the one responsible for actually logging in; everyone else waits on myFuture.
			try {
				VaultToken token = VaultClient.login(instance);
				TOKEN_CACHE.put(instance.id(), token);
				AAA_LOG.info("Logged in to Vault instance '{}' (auth method {})",
					instance.name(), instance.authMethod());
				myFuture.complete(token);
				return token;
			}
			catch (VaultException e) {
				AAA_LOG.warn("Failed to log in to Vault instance '{}': {}", instance.name(), e.getMessage());
				myFuture.completeExceptionally(e);
				throw e;
			}
			finally {
				LOGIN_IN_FLIGHT.remove(instance.id(), myFuture);
			}
		}
		log.trace("A login to Vault instance '{}' is already in flight, waiting for it.", instance.name());
		return awaitFuture(raceWinner, "Vault login for instance '%s'".formatted(instance.name()));
	}

	private static JsonNode getSecretData(HashicorpVaultKv2Instance instance, String path, String token)
			throws VaultException {
		SecretCacheKey key = new SecretCacheKey(instance.id(), path);
		CachedSecretData cached = SECRET_CACHE.get(key);
		if (cached != null && cached.isValid()) {
			log.trace("Using cached Vault secret data for instance '{}', path '{}'.", instance.name(), path);
			return cached.data();
		}
		log.trace("No valid cached Vault secret data for instance '{}', path '{}', reading from Vault.",
			instance.name(), path);
		CompletableFuture<JsonNode> myFuture = new CompletableFuture<>();
		CompletableFuture<JsonNode> raceWinner = READ_IN_FLIGHT.putIfAbsent(key, myFuture);
		if (raceWinner == null) {
			try {
				JsonNode data = VaultClient.readKv2(instance, path, token);
				SECRET_CACHE.put(key, new CachedSecretData(data, System.currentTimeMillis() + SETTINGS.getSecretCacheTtlMs()));
				myFuture.complete(data);
				return data;
			}
			catch (VaultException e) {
				myFuture.completeExceptionally(e);
				throw e;
			}
			finally {
				READ_IN_FLIGHT.remove(key, myFuture);
			}
		}
		log.trace("A read of Vault path '{}' on instance '{}' is already in flight, waiting for it.",
			path, instance.name());
		return awaitFuture(raceWinner, "Vault read of path '%s' on instance '%s'".formatted(path, instance.name()));
	}

	private static <T> T awaitFuture(CompletableFuture<T> future, String description) throws VaultException {
		try {
			return future.get();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new VaultException("Interrupted while waiting for " + description, e);
		}
		catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof VaultException ve) {
				throw ve;
			}
			throw new VaultException("Failed while waiting for " + description, cause);
		}
	}

	/**
	 * Attempts a fresh (non-cached) login, for the Admin UI's "Test connection"
	 * action. Does not touch the token cache.
	 * @param instance the Vault instance to test (possibly not yet persisted)
	 * @throws VaultException if the login fails
	 */
	public static void testConnection(HashicorpVaultKv2Instance instance) throws VaultException {
		if (instance.authMethod() == null) {
			throw new VaultException("No authentication method configured");
		}
		if (instance.authMethod() == AuthMethod.JWT
				&& (instance.jwtIdpTokenEndpoint() == null || instance.jwtClientId() == null
					|| instance.jwtVaultRole() == null)) {
			throw new VaultException("Incomplete JWT authentication configuration");
		}
		if (instance.authMethod() == AuthMethod.APPROLE
				&& (instance.appRoleId() == null || instance.appRoleSecretId() == null)) {
			throw new VaultException("Incomplete AppRole authentication configuration");
		}
		VaultClient.login(instance);
	}

	/**
	 * Invalidates all cached tokens/secrets for the given Vault instance -
	 * called locally right away when an admin edits/deletes it, and again on
	 * every cluster node on receipt of the corresponding broadcast (see
	 * {@link net.netshot.netshot.cluster.messages.VaultInstanceChangedMessage}).
	 * @param vaultInstanceId the ID of the Vault instance
	 */
	public static void invalidate(long vaultInstanceId) {
		INSTANCE_CACHE.remove(vaultInstanceId);
		TOKEN_CACHE.remove(vaultInstanceId);
		SECRET_CACHE.keySet().removeIf(key -> key.vaultInstanceId() == vaultInstanceId);
		AAA_LOG.info("Invalidated local Vault cache for instance ID {}", vaultInstanceId);
	}

}
