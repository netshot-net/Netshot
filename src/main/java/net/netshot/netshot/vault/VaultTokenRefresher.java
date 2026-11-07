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
package net.netshot.netshot.vault;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.concurrent.NamedThreadFactory;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.lifecycle.Service;

/**
 * Service that proactively refreshes the Vault client tokens shortly before they expire, so that the
 * device-connection threads usually hit an already-warm {@link VaultManager} token cache instead of blocking
 * on a login round-trip. Purely a performance optimization - {@link VaultManager#getToken} itself also lazily
 * re-logs in on demand, which remains the correctness backstop if this refresher is slow to catch an expiry.
 */
@Slf4j
public final class VaultTokenRefresher implements Service {

	/** Interval (seconds) between two refreshes of the tokens (read again before each wait). */
	static final Setting<Integer> POLL_INTERVAL = Setting.integer("netshot.vault.token.refreshpollintervalseconds")
		.defaultValue(60).range(5, Integer.MAX_VALUE).live();

	/** The Vault client, whose tokens are refreshed. */
	private final VaultManager vaultManager;

	/** The configuration store. */
	private final ConfigStore config;

	/** The number of refresh rounds. */
	private final AtomicLong rounds = new AtomicLong();

	/** The number of tokens which couldn't be refreshed. */
	private final AtomicLong failures = new AtomicLong();

	/** The executor running the refreshes. */
	private ScheduledExecutorService executor;

	/** The transaction support. */
	private final Transactions transactions;

	/**
	 * Creates the refresher.
	 *
	 * @param transactions the transaction support, to read the Vault instances
	 * @param vaultManager the Vault client, whose tokens are refreshed
	 * @param config the configuration store
	 */
	public VaultTokenRefresher(Transactions transactions, VaultManager vaultManager, ConfigStore config) {
		this.transactions = transactions;
		this.vaultManager = vaultManager;
		this.config = config;
	}

	@Override
	public String name() {
		return "Vault token refresher";
	}

	@Override
	public synchronized void start() {
		this.executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("NetshotVaultTokenRefresh"));
		this.schedule(0);
	}

	@Override
	public synchronized void stop() throws InterruptedException {
		this.executor.shutdownNow();
		this.executor.awaitTermination(10, TimeUnit.SECONDS);
	}

	@Override
	public Map<String, Object> stats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("rounds", this.rounds.get());
		stats.put("refreshFailures", this.failures.get());
		stats.putAll(this.vaultManager.stats());
		return stats;
	}

	private void schedule(long delaySeconds) {
		try {
			this.executor.schedule(this::refresh, delaySeconds, TimeUnit.SECONDS);
		}
		catch (RejectedExecutionException e) {
			// Stopped in the meantime
		}
	}

	private void refresh() {
		try {
			this.refreshAll();
		}
		catch (RuntimeException e) {
			log.error("Error while refreshing Vault tokens", e);
		}
		finally {
			this.rounds.incrementAndGet();
			this.schedule(this.config.current().get(POLL_INTERVAL));
		}
	}

	private void refreshAll() {
		// The instances are read first: no transaction is held during the logins
		List<VaultInstance> instances = this.transactions.read(dsl -> new VaultInstanceRepository().findAll(dsl, Page.ALL));
		for (VaultInstance candidate : instances) {
			if (!(candidate instanceof HashicorpVaultKv2Instance vaultInstance)) {
				continue;
			}
			try {
				this.vaultManager.getToken(vaultInstance);
			}
			catch (VaultException e) {
				this.failures.incrementAndGet();
				log.warn("Unable to proactively refresh the token for Vault instance '{}': {}",
					vaultInstance.name(), e.getMessage());
			}
		}
	}
}
