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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;
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
public final class VaultManager implements SecretResolver {

	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/**
	 * The settings of the Vault client.
	 *
	 * @param tokenRenewMarginMs the margin (ms) before a cached client token's expiry at which it's proactively renewed
	 * @param secretCacheTtlMs how long (ms) a resolved secret's JSON data is cached for
	 * @param httpConnectTimeoutMs the HTTP connect timeout (ms) for calls to a Vault instance
	 * @param httpReadTimeoutMs the HTTP read timeout (ms) for calls to a Vault instance
	 */
	public record Settings(int tokenRenewMarginMs, int secretCacheTtlMs, int httpConnectTimeoutMs, int httpReadTimeoutMs) {

		/** Margin (ms) before a cached client token's expiry at which it's proactively renewed. */
		static final Setting<Integer> TOKEN_RENEW_MARGIN = Setting.integer("netshot.vault.token.renewmarginms")
			.defaultValue(30000).range(0, Integer.MAX_VALUE).live();

		/** How long (ms) a resolved secret's JSON data is cached for. */
		static final Setting<Integer> SECRET_CACHE_TTL = Setting.integer("netshot.vault.secret.cachettlms")
			.defaultValue(60000).range(0, Integer.MAX_VALUE).live();

		/** HTTP connect timeout (ms). */
		static final Setting<Integer> HTTP_CONNECT_TIMEOUT = Setting.integer("netshot.vault.http.connecttimeoutms")
			.defaultValue(5000).range(100, Integer.MAX_VALUE).live();

		/** HTTP read timeout (ms). */
		static final Setting<Integer> HTTP_READ_TIMEOUT = Setting.integer("netshot.vault.http.readtimeoutms")
			.defaultValue(10000).range(100, Integer.MAX_VALUE).live();

		/**
		 * Reads the settings.
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			Settings settings = new Settings(config.get(TOKEN_RENEW_MARGIN), config.get(SECRET_CACHE_TTL),
				config.get(HTTP_CONNECT_TIMEOUT), config.get(HTTP_READ_TIMEOUT));
			log.debug("The Vault client settings are {}", settings);
			return settings;
		}
	}

	/** The settings, from the configuration. */
	private final ConfigStore.Binding<Settings> settings;

	/**
	 * Gets the settings of the Vault client.
	 * @return the settings
	 */
	public Settings settings() {
		return this.settings.get();
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
	private final ConcurrentHashMap<Long, VaultInstance> instanceCache = new ConcurrentHashMap<>();

	/** Reads a Vault instance from the database (null if it doesn't exist). */
	private final LongFunction<VaultInstance> instanceLoader;

	private final ConcurrentHashMap<Long, VaultToken> tokenCache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, CompletableFuture<VaultToken>> loginInFlight = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<SecretCacheKey, CachedSecretData> secretCache = new ConcurrentHashMap<>();

	/** The number of logins to the Vault instances. */
	private final AtomicLong logins = new AtomicLong();

	/** The number of failed logins. */
	private final AtomicLong loginFailures = new AtomicLong();

	/** The number of reads of secrets from the Vault instances. */
	private final AtomicLong reads = new AtomicLong();

	/** The number of failed reads. */
	private final AtomicLong readFailures = new AtomicLong();

	/** The number of secrets taken from the cache. */
	private final AtomicLong cacheHits = new AtomicLong();
	private final ConcurrentHashMap<SecretCacheKey, CompletableFuture<JsonNode>> readInFlight =
		new ConcurrentHashMap<>();

	/**
	 * Creates the Vault client, reading the Vault instances from the database.
	 *
	 * @param config the configuration store, to read the settings from
	 * @param transactions the transaction support, to read the Vault instances
	 */
	public VaultManager(ConfigStore config, Transactions transactions) {
		this(config, id -> transactions.read(dsl -> new VaultInstanceRepository().find(dsl, id)));
	}

	/**
	 * Creates the Vault client.
	 *
	 * @param config the configuration store, to read the settings from
	 * @param instanceLoader reads a Vault instance, by ID (null if it doesn't exist)
	 */
	public VaultManager(ConfigStore config, LongFunction<VaultInstance> instanceLoader) {
		this.settings = config.bind(Settings::from);
		this.instanceLoader = instanceLoader;
	}

	/**
	 * Gets the statistics of the Vault client: logins, reads, cache.
	 *
	 * @return the statistics
	 */
	public Map<String, Object> stats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("logins", this.logins.get());
		stats.put("loginFailures", this.loginFailures.get());
		stats.put("reads", this.reads.get());
		stats.put("readFailures", this.readFailures.get());
		stats.put("cacheHits", this.cacheHits.get());
		stats.put("cachedTokens", this.tokenCache.size());
		stats.put("cachedSecrets", this.secretCache.size());
		stats.put("cachedInstances", this.instanceCache.size());
		return stats;
	}

	/**
	 * Gets a Vault instance, from the cache or the database.
	 *
	 * @param id the ID of the Vault instance
	 * @return the instance, or null if there is no such instance
	 */
	VaultInstance instance(long id) {
		VaultInstance cached = this.instanceCache.get(id);
		if (cached != null) {
			return cached;
		}
		VaultInstance instance = this.instanceLoader.apply(id);
		if (instance != null) {
			this.instanceCache.put(id, instance);
		}
		return instance;
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
	@Override
	public String resolve(VaultableSecret secret) throws VaultException {
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
	VaultToken getToken(HashicorpVaultKv2Instance instance) throws VaultException {
		VaultToken cached = this.tokenCache.get(instance.id());
		if (cached != null && cached.isValid(this.settings().tokenRenewMarginMs())) {
			log.trace("Using cached Vault client token for instance '{}'.", instance.name());
			return cached;
		}
		log.trace("No valid cached Vault client token for instance '{}', logging in.", instance.name());
		CompletableFuture<VaultToken> myFuture = new CompletableFuture<>();
		CompletableFuture<VaultToken> raceWinner = this.loginInFlight.putIfAbsent(instance.id(), myFuture);
		if (raceWinner == null) {
			// We're the one responsible for actually logging in; everyone else waits on myFuture.
			try {
				this.logins.incrementAndGet();
				VaultToken token = VaultClient.login(instance, this.settings());
				this.tokenCache.put(instance.id(), token);
				AAA_LOG.info("Logged in to Vault instance '{}' (auth method {})",
					instance.name(), instance.authMethod());
				myFuture.complete(token);
				return token;
			}
			catch (VaultException e) {
				this.loginFailures.incrementAndGet();
				AAA_LOG.warn("Failed to log in to Vault instance '{}': {}", instance.name(), e.getMessage());
				myFuture.completeExceptionally(e);
				throw e;
			}
			finally {
				this.loginInFlight.remove(instance.id(), myFuture);
			}
		}
		log.trace("A login to Vault instance '{}' is already in flight, waiting for it.", instance.name());
		return awaitFuture(raceWinner, "Vault login for instance '%s'".formatted(instance.name()));
	}

	private JsonNode getSecretData(HashicorpVaultKv2Instance instance, String path, String token)
			throws VaultException {
		SecretCacheKey key = new SecretCacheKey(instance.id(), path);
		CachedSecretData cached = this.secretCache.get(key);
		if (cached != null && cached.isValid()) {
			log.trace("Using cached Vault secret data for instance '{}', path '{}'.", instance.name(), path);
			this.cacheHits.incrementAndGet();
			return cached.data();
		}
		log.trace("No valid cached Vault secret data for instance '{}', path '{}', reading from Vault.",
			instance.name(), path);
		CompletableFuture<JsonNode> myFuture = new CompletableFuture<>();
		CompletableFuture<JsonNode> raceWinner = this.readInFlight.putIfAbsent(key, myFuture);
		if (raceWinner == null) {
			try {
				this.reads.incrementAndGet();
				JsonNode data = VaultClient.readKv2(instance, path, token, this.settings());
				this.secretCache.put(key, new CachedSecretData(data, System.currentTimeMillis() + this.settings().secretCacheTtlMs()));
				myFuture.complete(data);
				return data;
			}
			catch (VaultException e) {
				this.readFailures.incrementAndGet();
				myFuture.completeExceptionally(e);
				throw e;
			}
			finally {
				this.readInFlight.remove(key, myFuture);
			}
		}
		log.trace("A read of Vault path '{}' on instance '{}' is already in flight, waiting for it.",
			path, instance.name());
		return awaitFuture(raceWinner, "Vault read of path '%s' on instance '%s'".formatted(path, instance.name()));
	}

	private <T> T awaitFuture(CompletableFuture<T> future, String description) throws VaultException {
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
	public void testConnection(HashicorpVaultKv2Instance instance) throws VaultException {
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
		VaultClient.login(instance, this.settings());
	}

	/**
	 * Invalidates all cached tokens/secrets for the given Vault instance -
	 * called locally right away when an admin edits/deletes it, and again on
	 * every cluster node on receipt of the corresponding broadcast (see
	 * {@link net.netshot.netshot.cluster.messages.VaultInstanceChangedMessage}).
	 * @param vaultInstanceId the ID of the Vault instance
	 */
	public void invalidate(long vaultInstanceId) {
		this.instanceCache.remove(vaultInstanceId);
		this.tokenCache.remove(vaultInstanceId);
		this.secretCache.keySet().removeIf(key -> key.vaultInstanceId() == vaultInstanceId);
		AAA_LOG.info("Invalidated local Vault cache for instance ID {}", vaultInstanceId);
	}

}
