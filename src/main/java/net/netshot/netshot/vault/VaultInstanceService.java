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

import java.util.List;
import java.util.function.Function;

import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.QuerySchema;
import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.platform.secret.Secret;
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
			.appRoleSecretId(Secret.localValue(definition.appRoleSecretId().applyTo(base.appRoleSecretId())))
			.jwtMountPath(definition.jwtMountPath() == null ? "jwt" : definition.jwtMountPath())
			.jwtIdpTokenEndpoint(definition.jwtIdpTokenEndpoint())
			.jwtClientId(definition.jwtClientId())
			.jwtClientSecret(Secret.localValue(definition.jwtClientSecret().applyTo(base.jwtClientSecret())))
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

	private static void requireValue(String property, LocalSecret value) {
		requireValue(property, value == null ? null : value.reveal());
	}

	/**
	 * Adds a Vault instance.
	 *
	 * @param definition the definition
	 * @return the new Vault instance
	 * @throws NetshotException if the definition is invalid, the name is already used, or the instance can't be stored
	 */
	public VaultInstance create(VaultInstanceDefinition definition) {
		HashicorpVaultKv2Instance vaultInstance = assemble(null, definition);
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
	 * Replaces the definition of a Vault instance, if it is still at a given version: the new definition is
	 * computed from the instance as it is, which can't change meanwhile (to apply a patch). The secrets the
	 * definition leaves unchanged are kept.
	 *
	 * @param id the Vault instance ID
	 * @param change gives the new definition from the current instance
	 * @param expectedVersion the version the instance must be at, or null for any
	 * @return the edited Vault instance
	 * @throws NetshotException if the instance doesn't exist or isn't at the expected version, the definition is
	 *         invalid, the name is already used, or the instance can't be stored
	 */
	public VaultInstance update(long id, Function<VaultInstance, VaultInstanceDefinition> change, Long expectedVersion) {
		VaultInstance vaultInstance;
		try {
			vaultInstance = this.transactions.write(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				if (version == null || !(this.repository.find(dsl, id) instanceof HashicorpVaultKv2Instance hcInstance)) {
					throw new NetshotException(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, "The Vault instance doesn't exist.");
				}
				Versions.check("Vault instance", version, expectedVersion);
				return this.repository.save(dsl, assemble(hcInstance, change.apply(hcInstance)));
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
				Integer version = this.repository.lock(dsl, id);
				if (version == null) {
					throw new NetshotException(ErrorCode.NETSHOT_VAULT_INSTANCE_NOT_FOUND, "The Vault instance doesn't exist.");
				}
				Versions.check("Vault instance", version, expectedVersion);
				this.repository.remove(dsl, id);
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
	 * Tests the connection and authentication to a Vault instance as defined, without storing anything: a new one, or
	 * an existing one as it would be changed (the secrets the definition leaves unchanged are the stored ones).
	 *
	 * @param existingId the ID of the existing Vault instance the definition changes, or null for a new one
	 * @param definition the definition
	 * @return the outcome of the login
	 * @throws NetshotException if the existing Vault instance doesn't exist, or the definition is invalid
	 */
	public VaultConnectionTestResult test(Long existingId, VaultInstanceDefinition definition) {
		HashicorpVaultKv2Instance existing = null;
		if (existingId != null) {
			if (!(this.get(existingId) instanceof HashicorpVaultKv2Instance hcInstance)) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE, "Unsupported Vault instance type");
			}
			existing = hcInstance;
		}
		return this.tryLogin(assemble(existing, definition));
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

	private static NetshotException saveFailure(DataAccessException e) {
		if (DatabaseErrors.isUniqueViolation(e)) {
			return new NetshotException(ErrorCode.NETSHOT_DUPLICATE_VAULT_INSTANCE, "A Vault instance with this name already exists.");
		}
		return new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the Vault instance");
	}

}
