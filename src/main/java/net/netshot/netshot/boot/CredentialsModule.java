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
package net.netshot.netshot.boot;

import net.netshot.netshot.cluster.ClusterLink;
import net.netshot.netshot.credentials.CredentialSetService;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.vault.VaultInstanceService;
import net.netshot.netshot.vault.VaultManager;
import net.netshot.netshot.vault.VaultTokenRefresher;

/**
 * The credentials: the use cases of the credential sets and the Vault instances, and the refresher of the Vault tokens.
 *
 * @param credentialSetService the credential set use cases
 * @param vaultInstanceService the Vault instance use cases
 * @param vaultTokenRefresher the refresher of the Vault tokens (a service)
 */
public record CredentialsModule(
	CredentialSetService credentialSetService,
	VaultInstanceService vaultInstanceService,
	VaultTokenRefresher vaultTokenRefresher) {

	/**
	 * Builds the module.
	 *
	 * @param core the core module
	 * @param tasks the task module (the cluster, notified of the Vault instance changes)
	 * @return the module
	 */
	public static CredentialsModule create(CoreModule core, TaskModule tasks) {
		Transactions transactions = core.transactions();
		Repositories r = core.repositories();
		VaultManager vaultManager = core.vaultManager();
		ClusterLink cluster = tasks.cluster();
		return new CredentialsModule(
			new CredentialSetService(transactions, r.credentialSet(), r.domain(), r.credentialSetAssembler()),
			new VaultInstanceService(transactions, r.vaultInstance(),
				vaultInstanceId -> {
					vaultManager.invalidate(vaultInstanceId);
					cluster.requestVaultInstanceReload(vaultInstanceId);
				},
				vaultManager::testConnection),
			new VaultTokenRefresher(transactions, vaultManager, core.config(), r.vaultInstance()));
	}
}
