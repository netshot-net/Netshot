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

import org.hibernate.HibernateException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The use cases of the Vault instances: list, add, edit, delete and test the connection.
 *
 * <p>The inputs of {@link #create}, {@link #update} and {@link #testConnection} are
 * <em>transient</em> Vault instances holding what the client sent (the API mapper builds them).
 * The secrets the client left out or masked are {@code null} in them, which means "unchanged".
 * The Vault instances it returns are detached (they have no association).</p>
 *
 * <p>Once an edit or a deletion is committed, the {@link VaultInstanceChangeNotifier} is told, so that
 * the cached tokens and secrets are dropped on this node and on the other nodes of the cluster.</p>
 */
@Slf4j
public class VaultInstanceService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final VaultInstanceRepository repository;
	private final VaultInstanceChangeNotifier changeNotifier;
	private final VaultConnectionTester connectionTester;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the Vault instance repository
	 * @param changeNotifier told when a Vault instance has been edited or deleted
	 * @param connectionTester tries a login to a Vault server
	 */
	public VaultInstanceService(Transactions transactions, VaultInstanceRepository repository,
			VaultInstanceChangeNotifier changeNotifier, VaultConnectionTester connectionTester) {
		this.transactions = transactions;
		this.repository = repository;
		this.changeNotifier = changeNotifier;
		this.connectionTester = connectionTester;
	}

	/**
	 * Lists the Vault instances.
	 *
	 * @param page the window on the result
	 * @return the Vault instances
	 * @throws NetshotException if the Vault instances can't be read
	 */
	public List<VaultInstance> list(Page page) {
		try {
			return this.transactions.read(session -> this.repository.findAll(session, page));
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the Vault instances.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the Vault instances");
		}
	}

	/**
	 * Adds a Vault instance.
	 *
	 * @param vaultInstance the Vault instance to add (it becomes the persistent one)
	 * @return the new Vault instance
	 * @throws NetshotException if the name is blank or already used, or the Vault instance can't be stored
	 */
	public VaultInstance create(VaultInstance vaultInstance) {
		if (vaultInstance.getName() == null || "".equals(vaultInstance.getName().trim())) {
			log.error("Invalid Vault instance name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, "Invalid name for the Vault instance");
		}
		try {
			this.transactions.run(session -> {
				this.repository.add(session, vaultInstance);
				this.repository.refresh(session, vaultInstance);
			});
		}
		catch (HibernateException e) {
			log.error("Can't add the Vault instance.", e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been created.", vaultInstance);
		return vaultInstance;
	}

	/**
	 * Edits a Vault instance. The secrets which are {@code null} in the new definition are kept.
	 *
	 * @param id the Vault instance ID
	 * @param incoming the new definition, of the same kind as the existing Vault instance
	 * @return the edited Vault instance
	 * @throws NetshotException if the Vault instance doesn't exist or is of another kind, the name
	 *         is blank or already used, or the Vault instance can't be stored
	 */
	public VaultInstance update(long id, VaultInstance incoming) {
		VaultInstance vaultInstance;
		try {
			vaultInstance = this.transactions.write(session -> {
				VaultInstance existing = this.repository.find(session, incoming.getClass(), id);
				if (existing == null) {
					log.error("Unable to find the Vault instance {}.", id);
					throw new NetshotException(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, "Unable to find the Vault instance.");
				}
				if (!existing.getClass().equals(incoming.getClass())) {
					log.error("Wrong posted Vault instance type for Vault instance {}.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE,
						"The posted Vault instance type doesn't match the existing one.");
				}
				existing.setName(incoming.getName());
				if (existing.getName() == null || "".equals(existing.getName().trim())) {
					log.error("Invalid Vault instance name.");
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, "Invalid name for the Vault instance");
				}
				existing.setBaseUrl(incoming.getBaseUrl());
				existing.setNamespace(incoming.getNamespace());
				existing.setHttpsCaTrustMode(incoming.getHttpsCaTrustMode());
				existing.setHttpsCustomCaCertificate(incoming.getHttpsCustomCaCertificate());
				if (existing instanceof HashicorpVaultKv2Instance hcInstance
						&& incoming instanceof HashicorpVaultKv2Instance rsHcInstance) {
					hcInstance.setKvMountPath(rsHcInstance.getKvMountPath());
					hcInstance.setAuthMethod(rsHcInstance.getAuthMethod());
					hcInstance.setAppRoleMountPath(rsHcInstance.getAppRoleMountPath());
					hcInstance.setAppRoleId(rsHcInstance.getAppRoleId());
					if (rsHcInstance.getAppRoleSecretId() != null) {
						hcInstance.setAppRoleSecretId(rsHcInstance.getAppRoleSecretId());
					}
					hcInstance.setJwtMountPath(rsHcInstance.getJwtMountPath());
					hcInstance.setJwtIdpTokenEndpoint(rsHcInstance.getJwtIdpTokenEndpoint());
					hcInstance.setJwtClientId(rsHcInstance.getJwtClientId());
					if (rsHcInstance.getJwtClientSecret() != null) {
						hcInstance.setJwtClientSecret(rsHcInstance.getJwtClientSecret());
					}
					hcInstance.setJwtVaultRole(rsHcInstance.getJwtVaultRole());
					hcInstance.setJwtScope(rsHcInstance.getJwtScope());
				}
				this.repository.save(session, existing);
				return existing;
			});
		}
		catch (HibernateException e) {
			log.error("Unable to save the Vault instance {}.", id, e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been edited", vaultInstance);
		this.changeNotifier.changed(id);
		return vaultInstance;
	}

	private static NetshotException saveFailure(HibernateException e) {
		if (DatabaseErrors.causeMentions(e, "uplicate")) {
			return new NetshotException(ErrorCode.NETSHOT_DUPLICATE_VAULT_INSTANCE, "A Vault instance with this name already exists.");
		}
		return new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the Vault instance");
	}

	/**
	 * Deletes a Vault instance.
	 *
	 * @param id the Vault instance ID
	 * @return true if the Vault instance has been deleted, false if there was no such Vault instance
	 * @throws NetshotException if the Vault instance is referenced by credential fields, or can't be deleted
	 */
	public boolean delete(long id) {
		boolean deleted;
		try {
			deleted = this.transactions.write(session -> {
				VaultInstance vaultInstance = this.repository.find(session, id);
				if (vaultInstance == null) {
					log.info("No such Vault instance of ID {}", id);
					return false;
				}
				this.repository.remove(session, vaultInstance);
				return true;
			});
		}
		catch (RuntimeException e) {
			log.error("Unable to delete the Vault instance {}", id, e);
			if (e instanceof NetshotException) {
				throw e;
			}
			if (e instanceof ConstraintViolationException) {
				throw new NetshotException(ErrorCode.NETSHOT_VAULT_INSTANCE_IN_USE,
					"Unable to delete the Vault instance, it is referenced by one or more credential fields.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the Vault instance");
		}
		if (deleted) {
			AAA_LOG.info("Vault instance of ID {} has been deleted.", id);
			this.changeNotifier.changed(id);
		}
		return deleted;
	}

	/**
	 * Tests the connection and authentication to a Vault instance, without persisting anything.
	 * It works both for a new, not yet saved instance (no {@code id}) and for re-testing an existing
	 * one without having to enter the unchanged secrets again (the secrets which are {@code null}
	 * in the definition fall back to the values currently stored for that ID).
	 *
	 * @param id the ID of the existing Vault instance being tested, or null when testing a new one
	 * @param incoming the Vault instance configuration to test
	 * @throws NetshotException if the kind of Vault instance is not supported, the existing Vault
	 *         instance doesn't exist, or the login fails
	 */
	public void testConnection(Long id, VaultInstance incoming) {
		if (!(incoming instanceof HashicorpVaultKv2Instance rsHcInstance)) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE, "Unsupported Vault instance type");
		}
		HashicorpVaultKv2Instance hcInstance = rsHcInstance;
		if (id != null) {
			// Overlay onto the persisted secrets, so re-testing an already-saved
			// instance without touching its masked secret field(s) still works.
			hcInstance = this.transactions.read(session -> {
				VaultInstance existing = this.repository.find(session, id);
				if (!(existing instanceof HashicorpVaultKv2Instance existingHc)) {
					throw new NetshotException(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, "Unable to find the Vault instance.");
				}
				HashicorpVaultKv2Instance overlaid = new HashicorpVaultKv2Instance(rsHcInstance.getName());
				overlaid.setBaseUrl(rsHcInstance.getBaseUrl());
				overlaid.setNamespace(rsHcInstance.getNamespace());
				overlaid.setHttpsCaTrustMode(rsHcInstance.getHttpsCaTrustMode());
				overlaid.setHttpsCustomCaCertificate(rsHcInstance.getHttpsCustomCaCertificate());
				overlaid.setKvMountPath(rsHcInstance.getKvMountPath());
				overlaid.setAuthMethod(rsHcInstance.getAuthMethod());
				overlaid.setAppRoleMountPath(rsHcInstance.getAppRoleMountPath());
				overlaid.setAppRoleId(rsHcInstance.getAppRoleId());
				overlaid.setAppRoleSecretId(rsHcInstance.getAppRoleSecretId() != null
					? rsHcInstance.getAppRoleSecretId() : existingHc.getAppRoleSecretId());
				overlaid.setJwtMountPath(rsHcInstance.getJwtMountPath());
				overlaid.setJwtIdpTokenEndpoint(rsHcInstance.getJwtIdpTokenEndpoint());
				overlaid.setJwtClientId(rsHcInstance.getJwtClientId());
				overlaid.setJwtClientSecret(rsHcInstance.getJwtClientSecret() != null
					? rsHcInstance.getJwtClientSecret() : existingHc.getJwtClientSecret());
				overlaid.setJwtVaultRole(rsHcInstance.getJwtVaultRole());
				overlaid.setJwtScope(rsHcInstance.getJwtScope());
				return overlaid;
			});
		}
		try {
			this.connectionTester.test(hcInstance);
			AAA_LOG.info("Test connection to Vault instance '{}' succeeded.", hcInstance.getName());
		}
		catch (VaultException e) {
			AAA_LOG.warn("Test connection to Vault instance '{}' failed: {}", hcInstance.getName(), e.getMessage());
			throw new NetshotException(ErrorCode.NETSHOT_VAULT_CONNECTION_FAILED,
				"Vault connection test failed: " + e.getMessage());
		}
	}
}
