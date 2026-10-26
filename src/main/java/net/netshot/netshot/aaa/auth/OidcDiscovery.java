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
package net.netshot.netshot.aaa.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.concurrent.NamedThreadFactory;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.lifecycle.Service;

/**
 * Service fetching the metadata of the OIDC identity provider (IdP), then refreshing it periodically, which
 * enables the OIDC authentication ({@link Oidc}). Applies the configuration changes: a changed IdP or client
 * is fetched again immediately, and the OIDC authentication is disabled when it's not configured any more.
 */
@Slf4j
public final class OidcDiscovery implements Service {

	/**
	 * What is fetched, from the settings.
	 *
	 * @param endpoint the URL of the IdP
	 * @param clientId the client ID
	 * @param clientSecret the client secret
	 * @param retryInterval the interval (ms) before retrying after a failure
	 * @param refreshInterval the interval (ms) between two fetches
	 */
	private record Target(String endpoint, String clientId, String clientSecret, int retryInterval, int refreshInterval) {
	}

	/** The OIDC authentication, enabled by this service. */
	private final Oidc oidc;

	/** The configuration store. */
	private final ConfigStore config;

	/** Applies the configuration changes. */
	private final Consumer<Config> listener = _ -> this.apply();

	/** The number of successful fetches. */
	private final AtomicLong fetches = new AtomicLong();

	/** The number of failed fetches. */
	private final AtomicLong failures = new AtomicLong();

	/** The executor running the fetches. */
	private ScheduledExecutorService executor;

	/** What is currently fetched (null if OIDC isn't configured). */
	private Target target;

	/** The next fetch. */
	private ScheduledFuture<?> next;

	/**
	 * Creates the service.
	 *
	 * @param oidc the OIDC authentication, enabled by this service
	 * @param config the configuration store
	 */
	public OidcDiscovery(Oidc oidc, ConfigStore config) {
		this.oidc = oidc;
		this.config = config;
	}

	@Override
	public String name() {
		return "OIDC IdP discovery";
	}

	@Override
	public synchronized void start() {
		this.executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("NetshotOidcIdPDiscovery"));
		this.config.addListener(this.listener);
		this.apply();
	}

	@Override
	public void stop() throws InterruptedException {
		this.config.removeListener(this.listener);
		synchronized (this) {
			this.target = null;
			this.executor.shutdownNow();
		}
		this.executor.awaitTermination(10, TimeUnit.SECONDS);
		this.oidc.disable();
	}

	@Override
	public synchronized boolean isHealthy() {
		return this.target == null || this.oidc.isAvailable();
	}

	@Override
	public synchronized Map<String, Object> stats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("configured", this.target != null);
		stats.put("available", this.oidc.isAvailable());
		stats.put("fetches", this.fetches.get());
		stats.put("failures", this.failures.get());
		return stats;
	}

	private static Target targetOf(Oidc.Settings settings) {
		if (settings.endpoint() == null) {
			return null;
		}
		if (settings.clientId() == null) {
			log.warn("OIDC client id configuration is missing, OIDC won't be enabled");
			return null;
		}
		if (settings.clientSecret() == null) {
			log.warn("OIDC client secret configuration is missing, OIDC won't be enabled");
			return null;
		}
		return new Target(settings.endpoint(), settings.clientId(), settings.clientSecret(),
			settings.retryInterval(), settings.refreshInterval());
	}

	/**
	 * Applies the current settings.
	 */
	private synchronized void apply() {
		if (this.executor == null || this.executor.isShutdown()) {
			return;
		}
		Target newTarget = targetOf(this.oidc.settings());
		if (Objects.equals(newTarget, this.target)) {
			return;
		}
		if (this.next != null) {
			this.next.cancel(false);
		}
		this.target = newTarget;
		this.oidc.disable();
		if (newTarget == null) {
			log.info("OIDC is not configured.");
			return;
		}
		log.info("Starting the discovery of the OIDC IdP {}", newTarget.endpoint());
		this.oidc.setClient(newTarget.clientId(), newTarget.clientSecret());
		this.schedule(newTarget, 0);
	}

	private synchronized void schedule(Target fetched, long delay) {
		if (fetched != this.target) {
			// Changed in the meantime
			return;
		}
		try {
			this.next = this.executor.schedule(() -> this.fetch(fetched), delay, TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException e) {
			// Stopped in the meantime
		}
	}

	private void fetch(Target fetched) {
		long delay;
		try {
			OIDCProviderMetadata metadata = Oidc.fetchIdpMetadata(fetched.endpoint());
			synchronized (this) {
				if (fetched != this.target) {
					// Changed in the meantime, the new target is fetched
					return;
				}
				this.oidc.setIdpMetadata(metadata);
			}
			this.fetches.incrementAndGet();
			delay = fetched.refreshInterval();
		}
		catch (Exception e) {
			this.failures.incrementAndGet();
			log.error("Error while fetching/preparing OIDC configuration", e);
			delay = fetched.retryInterval();
		}
		log.trace("OIDC IdP discovery - pausing for {}ms", delay);
		this.schedule(fetched, delay);
	}
}
