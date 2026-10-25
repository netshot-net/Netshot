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

import java.util.List;


import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;

/**
 * Background daemon that proactively refreshes Vault client tokens shortly
 * before they expire, so device-connection threads usually hit an
 * already-warm {@link VaultManager} token cache instead of blocking on a
 * login round-trip. Purely a performance optimization - {@link VaultManager#getToken}
 * itself also lazily re-logs in on demand, which remains the correctness
 * backstop if this daemon is slow to catch an expiry.
 * <p>
 * Modeled after {@code Oidc.IdPDiscoveryDaemon}, the codebase's existing
 * pattern for this kind of lightweight periodic background refresh.
 */
@Slf4j
final class VaultTokenRefreshDaemon extends Thread {

	/** Interval (seconds) between two refreshes of the tokens. */
	static final Setting<Integer> POLL_INTERVAL = Setting.integer("netshot.vault.token.refreshpollintervalseconds")
		.defaultValue(60).range(5, Integer.MAX_VALUE).live();

	private static VaultTokenRefreshDaemon instance;

	private volatile boolean stopping = false;

	private VaultTokenRefreshDaemon() {
		this.setName("NetshotVaultTokenRefresh");
		this.setDaemon(true);
	}

	static synchronized void launch() {
		if (VaultTokenRefreshDaemon.instance == null) {
			VaultTokenRefreshDaemon.instance = new VaultTokenRefreshDaemon();
			VaultTokenRefreshDaemon.instance.start();
		}
	}

	@Override
	public void run() {
		log.info("Starting Vault token refresh daemon");
		while (!this.stopping) {
			int pollIntervalSeconds = Netshot.config().current().get(POLL_INTERVAL);
			try {
				this.refreshAll();
			}
			catch (Exception e) {
				log.error("Error while refreshing Vault tokens", e);
			}
			try {
				synchronized (this) {
					this.wait(pollIntervalSeconds * 1000L);
				}
			}
			catch (InterruptedException e) {
				break;
			}
		}
		log.info("End of Vault token refresh daemon");
	}

	private void refreshAll() {
		// The instances are read first: no transaction is held during the logins
		List<VaultInstance> instances = new Transactions().read(dsl -> new VaultInstanceRepository().findAll(dsl, Page.ALL));
		for (VaultInstance candidate : instances) {
			if (!(candidate instanceof HashicorpVaultKv2Instance vaultInstance)) {
				continue;
			}
			try {
				VaultManager.getToken(vaultInstance);
			}
			catch (VaultException e) {
				log.warn("Unable to proactively refresh the token for Vault instance '{}': {}",
					vaultInstance.name(), e.getMessage());
			}
		}
	}
}
