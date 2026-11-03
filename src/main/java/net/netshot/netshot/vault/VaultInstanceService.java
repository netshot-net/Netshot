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

import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.QuerySchema;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;

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
			return this.transactions.read(dsl -> this.repository.findAll(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the Vault instances.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the Vault instances");
		}
	}

	/**
	 * Gets what the Vault instance lists can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public static QuerySchema listSchema() {
		return VaultInstanceRepository.LIST.schema();
	}

	/**
	 * Lists the Vault instances matching a query.
	 *
	 * @param query the query
	 * @return the Vault instances of the window, with the total number of matching ones if the query asks for it
	 * @throws NetshotException if a filter is invalid, or the Vault instances can't be read
	 */
	public ListResult<VaultInstance> list(ListQuery query) {
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.repository.findAll(dsl, query),
				query.count() ? this.repository.count(dsl, query) : null));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the Vault instances.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the Vault instances");
		}
	}

	/**
	 * Gets a Vault instance.
	 *
	 * @param id the Vault instance ID
	 * @return the Vault instance
	 * @throws NetshotException if there is no such Vault instance, or it can't be read
	 */
	public VaultInstance get(long id) {
		VaultInstance vaultInstance;
		try {
			vaultInstance = this.transactions.read(dsl -> this.repository.find(dsl, id));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the Vault instance {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the Vault instance");
		}
		if (vaultInstance == null) {
			throw new NetshotException(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, "The Vault instance doesn't exist.");
		}
		return vaultInstance;
	}

	/**
	 * Builds a Vault instance from a definition, over an existing one (whose secrets are kept unless the
	 * definition changes them), and checks that it is complete.
	 *
	 * @param existing the existing Vault instance, or null for a new one
	 * @param definition the definition
	 * @return the Vault instance (to store)
	 * @throws NetshotException if the definition is invalid
	 */
	private static HashicorpVaultKv2Instance assemble(HashicorpVaultKv2Instance existing, VaultInstanceDefinition definition) {
		String name = definition.name() == null ? "" : definition.name().trim();
		if (name.isEmpty()) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, "Invalid name for the Vault instance");
		}
		HashicorpVaultKv2Instance base = existing != null ? existing : HashicorpVaultKv2Instance.builder(name).build();
		HashicorpVaultKv2Instance instance = base.toBuilder()
			.name(name)
			.baseUrl(definition.baseUrl())
			.namespace(definition.namespace())
			.httpsCaTrustMode(definition.httpsCaTrustMode() == null ? HttpsCaTrustMode.SYSTEM_TRUSTSTORE : definition.httpsCaTrustMode())
			.httpsCustomCaCertificate(definition.httpsCustomCaCertificate())
			.kvMountPath(definition.kvMountPath() == null ? "secret" : definition.kvMountPath())
			.authMethod(definition.authMethod())
			.appRoleMountPath(definition.appRoleMountPath() == null ? "approle" : definition.appRoleMountPath())
			.appRoleId(definition.appRoleId())
			.appRoleSecretId(definition.appRoleSecretId().applyTo(new VaultableSecret(base.appRoleSecretId(), null, null)).localValue())
			.jwtMountPath(definition.jwtMountPath() == null ? "jwt" : definition.jwtMountPath())
			.jwtIdpTokenEndpoint(definition.jwtIdpTokenEndpoint())
			.jwtClientId(definition.jwtClientId())
			.jwtClientSecret(definition.jwtClientSecret().applyTo(new VaultableSecret(base.jwtClientSecret(), null, null)).localValue())
			.jwtVaultRole(definition.jwtVaultRole())
			.jwtScope(definition.jwtScope())
			.build();
		if (instance.baseUrl() == null || !instance.baseUrl().matches("(?i)^https?://[^/\\s]+.*$")) {
			throw NetshotException.invalidParameter("baseUrl", "The base URL must be an http or https URL.");
		}
		if (instance.httpsCaTrustMode() == HttpsCaTrustMode.CUSTOM_CA
			&& (instance.httpsCustomCaCertificate() == null || instance.httpsCustomCaCertificate().isBlank())) {
			throw NetshotException.invalidParameter("httpsCustomCaCertificate", "A CA certificate is required in custom CA mode.");
		}
		if (instance.authMethod() == null) {
			throw NetshotException.invalidParameter("authMethod", "The authentication method is required.");
		}
		if (instance.authMethod() == AuthMethod.APPROLE) {
			requireValue("appRoleId", instance.appRoleId());
			requireValue("appRoleSecretId", instance.appRoleSecretId());
		}
		else {
			requireValue("jwtIdpTokenEndpoint", instance.jwtIdpTokenEndpoint());
			requireValue("jwtClientId", instance.jwtClientId());
			requireValue("jwtClientSecret", instance.jwtClientSecret());
			requireValue("jwtVaultRole", instance.jwtVaultRole());
		}
		return instance;
	}

	private static void requireValue(String property, String value) {
		if (value == null || value.isEmpty()) {
			throw NetshotException.invalidParameter(property, "The property '%s' is required with this authentication method.".formatted(property));
		}
	}

	/**
	 * Adds a Vault instance (v1 API: the definition is checked).
	 *
	 * @param definition the definition
	 * @return the new Vault instance
	 * @throws NetshotException if the definition is invalid, the name is already used, or the instance can't be stored
	 */
	public VaultInstance create(VaultInstanceDefinition definition) {
		return this.create(assemble(null, definition));
	}

	/**
	 * Replaces the definition of a Vault instance, if it is still at a given version. The secrets the definition
	 * leaves unchanged are kept.
	 *
	 * @param id the Vault instance ID
	 * @param definition the new definition
	 * @param expectedVersion the version the instance must be at, or null for any
	 * @return the edited Vault instance
	 * @throws NetshotException if the instance doesn't exist or isn't at the expected version, the definition is
	 *         invalid, the name is already used, or the instance can't be stored
	 */
	public VaultInstance update(long id, VaultInstanceDefinition definition, Long expectedVersion) {
		VaultInstance vaultInstance;
		try {
			vaultInstance = this.transactions.write(dsl -> {
				VaultInstance existing = this.repository.find(dsl, id);
				if (!(existing instanceof HashicorpVaultKv2Instance hcInstance)) {
					throw new NetshotException(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, "The Vault instance doesn't exist.");
				}
				if (expectedVersion != null && hcInstance.version() != expectedVersion) {
					throw NetshotException.versionMismatch("Vault instance", expectedVersion);
				}
				VaultInstance saved = this.repository.save(dsl, assemble(hcInstance, definition));
				if (saved == null) {
					throw NetshotException.versionMismatch("Vault instance", expectedVersion == null ? hcInstance.version() : expectedVersion);
				}
				return saved;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to save the Vault instance {}.", id, e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been edited", vaultInstance);
		this.changeNotifier.changed(id);
		return vaultInstance;
	}

	/**
	 * Deletes a Vault instance, if it is at a given version.
	 *
	 * @param id the Vault instance ID
	 * @param expectedVersion the version the instance must be at, or null for any
	 * @throws NetshotException if the instance doesn't exist, isn't at the expected version, is referenced by
	 *         credential fields, or can't be deleted
	 */
	public void delete(long id, Long expectedVersion) {
		try {
			this.transactions.run(dsl -> {
				if (!this.repository.remove(dsl, id, expectedVersion)) {
					if (expectedVersion != null && this.repository.find(dsl, id) != null) {
						throw NetshotException.versionMismatch("Vault instance", expectedVersion);
					}
					throw new NetshotException(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, "The Vault instance doesn't exist.");
				}
			});
		}
		catch (DataAccessException e) {
			if (DatabaseErrors.isForeignKeyViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_VAULT_INSTANCE_IN_USE,
					"Unable to delete the Vault instance, it is referenced by one or more credential fields.");
			}
			log.error("Unable to delete the Vault instance {}", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the Vault instance");
		}
		AAA_LOG.info("Vault instance of ID {} has been deleted.", id);
		this.changeNotifier.changed(id);
	}

	/**
	 * Tests the connection and authentication to a saved Vault instance.
	 *
	 * @param id the Vault instance ID
	 * @return the outcome of the login
	 * @throws NetshotException if the instance doesn't exist
	 */
	public VaultConnectionTestResult test(long id) {
		if (!(this.get(id) instanceof HashicorpVaultKv2Instance hcInstance)) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE, "Unsupported Vault instance type");
		}
		return this.tryLogin(hcInstance);
	}

	/**
	 * Tests the connection and authentication to a Vault instance which isn't saved (nothing is stored).
	 *
	 * @param definition the definition (its secrets must be given)
	 * @return the outcome of the login
	 * @throws NetshotException if the definition is invalid
	 */
	public VaultConnectionTestResult test(VaultInstanceDefinition definition) {
		return this.tryLogin(assemble(null, definition));
	}

	private VaultConnectionTestResult tryLogin(HashicorpVaultKv2Instance instance) {
		try {
			this.connectionTester.test(instance);
			AAA_LOG.info("Test connection to Vault instance '{}' succeeded.", instance.name());
			return new VaultConnectionTestResult(true, null);
		}
		catch (VaultException e) {
			AAA_LOG.warn("Test connection to Vault instance '{}' failed: {}", instance.name(), e.getMessage());
			return new VaultConnectionTestResult(false, e.getMessage());
		}
	}

	/**
	 * Adds a Vault instance.
	 *
	 * @param vaultInstance the Vault instance to add (its ID is ignored)
	 * @return the new Vault instance
	 * @throws NetshotException if the name is blank or already used, or the Vault instance can't be stored
	 */
	public VaultInstance create(VaultInstance vaultInstance) {
		if (vaultInstance.name() == null || "".equals(vaultInstance.name().trim())) {
			log.error("Invalid Vault instance name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, "Invalid name for the Vault instance");
		}
		VaultInstance created;
		try {
			created = this.transactions.write(dsl -> this.repository.add(dsl, vaultInstance));
		}
		catch (DataAccessException e) {
			log.error("Can't add the Vault instance.", e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been created.", created);
		return created;
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
			vaultInstance = this.transactions.write(dsl -> {
				VaultInstance existing = this.repository.find(dsl, id);
				if (existing == null || !existing.getClass().equals(incoming.getClass())) {
					// A Vault instance of another type is not found, as with the former query by type
					log.error("Unable to find the Vault instance {}.", id);
					throw new NetshotException(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, "Unable to find the Vault instance.");
				}
				if (incoming.name() == null || "".equals(incoming.name().trim())) {
					log.error("Invalid Vault instance name.");
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE_NAME, "Invalid name for the Vault instance");
				}
				VaultInstance edited = switch (existing) {
					case HashicorpVaultKv2Instance hcInstance -> {
						HashicorpVaultKv2Instance rsHcInstance = (HashicorpVaultKv2Instance) incoming;
						yield hcInstance.toBuilder()
							.name(rsHcInstance.name())
							.baseUrl(rsHcInstance.baseUrl())
							.namespace(rsHcInstance.namespace())
							.httpsCaTrustMode(rsHcInstance.httpsCaTrustMode())
							.httpsCustomCaCertificate(rsHcInstance.httpsCustomCaCertificate())
							.kvMountPath(rsHcInstance.kvMountPath())
							.authMethod(rsHcInstance.authMethod())
							.appRoleMountPath(rsHcInstance.appRoleMountPath())
							.appRoleId(rsHcInstance.appRoleId())
							.appRoleSecretId(rsHcInstance.appRoleSecretId() != null ? rsHcInstance.appRoleSecretId() : hcInstance.appRoleSecretId())
							.jwtMountPath(rsHcInstance.jwtMountPath())
							.jwtIdpTokenEndpoint(rsHcInstance.jwtIdpTokenEndpoint())
							.jwtClientId(rsHcInstance.jwtClientId())
							.jwtClientSecret(rsHcInstance.jwtClientSecret() != null ? rsHcInstance.jwtClientSecret() : hcInstance.jwtClientSecret())
							.jwtVaultRole(rsHcInstance.jwtVaultRole())
							.jwtScope(rsHcInstance.jwtScope())
							.build();
					}
				};
				VaultInstance saved = this.repository.save(dsl, edited);
				if (saved == null) {
					throw new NetshotException(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, "Unable to find the Vault instance.");
				}
				return saved;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to save the Vault instance {}.", id, e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been edited", vaultInstance);
		this.changeNotifier.changed(id);
		return vaultInstance;
	}

	private static NetshotException saveFailure(DataAccessException e) {
		if (DatabaseErrors.isUniqueViolation(e)) {
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
			deleted = this.transactions.write(dsl -> {
				if (!this.repository.remove(dsl, id)) {
					log.info("No such Vault instance of ID {}", id);
					return false;
				}
				return true;
			});
		}
		catch (RuntimeException e) {
			log.error("Unable to delete the Vault instance {}", id, e);
			if (e instanceof NetshotException) {
				throw e;
			}
			if (e instanceof DataAccessException dae && DatabaseErrors.isForeignKeyViolation(dae)) {
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
			hcInstance = this.transactions.read(dsl -> {
				VaultInstance existing = this.repository.find(dsl, id);
				if (!(existing instanceof HashicorpVaultKv2Instance existingHc)) {
					throw new NetshotException(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, "Unable to find the Vault instance.");
				}
				return rsHcInstance.toBuilder()
					.id(0)
					.version(0)
					.changeDate(null)
					.appRoleSecretId(rsHcInstance.appRoleSecretId() != null ? rsHcInstance.appRoleSecretId() : existingHc.appRoleSecretId())
					.jwtClientSecret(rsHcInstance.jwtClientSecret() != null ? rsHcInstance.jwtClientSecret() : existingHc.jwtClientSecret())
					.build();
			});
		}
		try {
			this.connectionTester.test(hcInstance);
			AAA_LOG.info("Test connection to Vault instance '{}' succeeded.", hcInstance.name());
		}
		catch (VaultException e) {
			AAA_LOG.warn("Test connection to Vault instance '{}' failed: {}", hcInstance.name(), e.getMessage());
			throw new NetshotException(ErrorCode.NETSHOT_VAULT_CONNECTION_FAILED,
				"Vault connection test failed: " + e.getMessage());
		}
	}
}
