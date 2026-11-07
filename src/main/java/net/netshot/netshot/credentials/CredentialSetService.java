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
package net.netshot.netshot.credentials;

import java.util.List;
import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.domain.DomainRepository;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.QuerySchema;

/**
 * The use cases of the global credential sets: list, add, edit and delete.
 *
 * <p>The inputs of {@link #create} and {@link #update} are <em>incoming</em> credential sets
 * holding what the client sent (the API mapper builds them): they are validated, then either
 * stored (create) or merged into the stored credential set (update). The secrets the client
 * left out or masked are {@code null} in them, which means "unchanged". Their domain is only a
 * reference (by ID).</p>
 */
@Slf4j
public class CredentialSetService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final CredentialSetRepository repository;
	private final DomainRepository domainRepository = new DomainRepository();

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the credential set repository
	 */
	public CredentialSetService(Transactions transactions, CredentialSetRepository repository) {
		this.transactions = transactions;
		this.repository = repository;
	}

	/**
	 * Lists the global credential sets.
	 *
	 * @param page the window on the result
	 * @return the credential sets, by ID
	 * @throws NetshotException if the credential sets can't be read
	 */
	public List<DeviceCredentialSet> list(Page page) {
		try {
			return this.transactions.read(dsl -> this.repository.findGlobal(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the credentials.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the credentials");
		}
	}

	/**
	 * Adds a global credential set.
	 *
	 * @param credentialSet the credential set to add (its ID is ignored)
	 * @return the new credential set
	 * @throws NetshotException if the name is blank, the domain or a Vault instance doesn't exist,
	 *         the name is already used, or the credential set can't be stored
	 */
	public DeviceCredentialSet create(DeviceCredentialSet credentialSet) {
		if (credentialSet.name() == null || "".equals(credentialSet.name().trim())) {
			log.error("Invalid credential set name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, "Invalid name for the credential set");
		}
		DeviceCredentialSet created;
		try {
			created = this.transactions.write(dsl -> {
				Domain domain = null;
				if (credentialSet.mgmtDomain() != null) {
					domain = this.domainRepository.find(dsl, credentialSet.mgmtDomain().id());
					if (domain == null) {
						throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "Invalid domain");
					}
				}
				// The ID is assigned by the database: a posted one is ignored
				DeviceCredentialSet global = credentialSet.withIdentity(0, credentialSet.name(), false).withMgmtDomain(domain);
				return this.repository.add(dsl, CredentialSets.resolveVaultableFields(dsl, global, global));
			});
		}
		catch (DataAccessException e) {
			log.error("Can't add the credentials.", e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been created.", created);
		return created;
	}

	/**
	 * Edits a global (or device specific) credential set. The secrets which are {@code null}
	 * in the new definition are kept.
	 *
	 * @param id the credential set ID
	 * @param incoming the new definition, of the same kind as the existing credential set
	 * @return the edited credential set
	 * @throws NetshotException if the credential set doesn't exist or is of another kind, the
	 *         name is blank or already used, a Vault instance doesn't exist, or the credential
	 *         set can't be stored
	 */
	public DeviceCredentialSet update(long id, DeviceCredentialSet incoming) {
		DeviceCredentialSet credentialSet;
		try {
			credentialSet = this.transactions.write(dsl -> {
				this.repository.lock(dsl, id);
				DeviceCredentialSet existing = this.repository.find(dsl, id);
				if (existing instanceof DeviceSshKeyAccount && incoming instanceof DeviceSshAccount) {
					// (The SSH key accounts used to be a kind of SSH accounts, found by the query by type)
					log.error("Wrong posted credential type for credential set {}.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE,
						"The posted credential type doesn't match the existing one.");
				}
				if (existing == null || !existing.getClass().equals(incoming.getClass())) {
					// A credential set of another type is not found, as with the former query by type
					log.error("Unable to find the credential set {}.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS, "Unable to find the credential set.");
				}
				Domain domain = null;
				if (incoming.mgmtDomain() != null) {
					domain = this.domainRepository.find(dsl, incoming.mgmtDomain().id());
					if (domain == null) {
						log.error("Unknown domain {} for credential set {}.", incoming.mgmtDomain().id(), id);
						throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "The domain doesn't exist.");
					}
				}
				if (incoming.name() == null || "".equals(incoming.name().trim())) {
					log.error("Invalid credential set name.");
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, "Invalid name for the credential set");
				}
				DeviceCredentialSet merged = CredentialSets.merge(existing, incoming, false)
					.withIdentity(existing.id(), incoming.name(), existing.deviceSpecific())
					.withMgmtDomain(domain);
				// Applied last so that switching a field to Vault mode always wins over
				// the unconditional local value copies of the merge (e.g. the username).
				DeviceCredentialSet saved = this.repository.save(dsl, CredentialSets.resolveVaultableFields(dsl, merged, incoming));
				if (saved == null) {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS, "Unable to find the credential set.");
				}
				return saved;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to save the credentials {}.", id, e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been edited", credentialSet);
		return credentialSet;
	}

	/**
	 * Gets what the credential set lists can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public static QuerySchema listSchema() {
		return CredentialSetRepository.LIST.schema();
	}

	/**
	 * Lists the credential sets matching a query (the global ones, unless the query filters on {@code deviceSpecific}).
	 *
	 * @param query the query
	 * @return the credential sets of the window, with the total number of matching ones if the query asks for it
	 * @throws NetshotException if a filter is invalid, or the credential sets can't be read
	 */
	public ListResult<DeviceCredentialSet> list(ListQuery query) {
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.repository.findAll(dsl, query),
				query.count() ? this.repository.count(dsl, query) : null));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the credentials.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the credentials");
		}
	}

	/**
	 * Gets a credential set, global or specific to a device access.
	 *
	 * @param id the credential set ID
	 * @return the credential set
	 * @throws NetshotException if there is no such credential set, or it can't be read
	 */
	public DeviceCredentialSet get(long id) {
		DeviceCredentialSet credentialSet;
		try {
			credentialSet = this.transactions.read(dsl -> this.repository.find(dsl, id));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the credential set {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the credential set");
		}
		if (credentialSet == null) {
			throw new NetshotException(ErrorCode.NETSHOT_CREDENTIALS_NOT_FOUND, "The credential set doesn't exist.");
		}
		return credentialSet;
	}

	private Domain findDomain(DSLContext dsl, Long domainId) {
		if (domainId == null) {
			return null;
		}
		Domain domain = this.domainRepository.find(dsl, domainId);
		if (domain == null) {
			throw NetshotException.invalidParameter("mgmtDomain", "The domain %d doesn't exist.".formatted(domainId));
		}
		return domain;
	}

	/**
	 * Adds a global credential set (v1 API: the definition is checked for completeness).
	 *
	 * @param definition the definition
	 * @return the new credential set
	 * @throws NetshotException if the definition is invalid or incomplete, the domain or a Vault instance doesn't
	 *         exist, the name is already used, or the credential set can't be stored
	 */
	public DeviceCredentialSet create(CredentialSetDefinition definition) {
		DeviceCredentialSet created;
		try {
			created = this.transactions.write(dsl -> {
				Domain domain = this.findDomain(dsl, definition.mgmtDomainId());
				return this.repository.add(dsl, CredentialSetAssembler.assemble(dsl, null, definition, domain));
			});
		}
		catch (DataAccessException e) {
			log.error("Can't add the credentials.", e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been created.", created);
		return created;
	}

	/**
	 * Replaces the definition of a global credential set, if it is still at a given version. The secrets the
	 * definition leaves unchanged are kept.
	 *
	 * @param id the credential set ID
	 * @param definition the new definition
	 * @param expectedVersion the version the credential set must be at, or null for any
	 * @return the edited credential set
	 * @throws NetshotException if the credential set doesn't exist, is specific to a device access, is of another
	 *         type, isn't at the expected version, the definition is invalid or incomplete, or it can't be stored
	 */
	public DeviceCredentialSet update(long id, CredentialSetDefinition definition, Long expectedVersion) {
		return this.update(id, _ -> definition, expectedVersion);
	}

	/**
	 * Replaces the definition of a global credential set, if it is still at a given version: the new definition is
	 * computed from the credential set as it is, which can't change meanwhile (to apply a patch). The secrets the
	 * definition leaves unchanged are kept.
	 *
	 * @param id the credential set ID
	 * @param change gives the new definition from the current credential set
	 * @param expectedVersion the version the credential set must be at, or null for any
	 * @return the edited credential set
	 * @throws NetshotException if the credential set doesn't exist, is specific to a device access, is of another
	 *         type, isn't at the expected version, the definition is invalid or incomplete, or it can't be stored
	 */
	public DeviceCredentialSet update(long id, Function<DeviceCredentialSet, CredentialSetDefinition> change, Long expectedVersion) {
		DeviceCredentialSet credentialSet;
		try {
			credentialSet = this.transactions.write(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				if (version == null) {
					throw new NetshotException(ErrorCode.NETSHOT_CREDENTIALS_NOT_FOUND, "The credential set doesn't exist.");
				}
				DeviceCredentialSet existing = this.repository.find(dsl, id);
				if (existing.deviceSpecific()) {
					throw new NetshotException(ErrorCode.NETSHOT_CREDENTIALS_IN_USE,
						"A device-specific credential set can only be changed with its device access.");
				}
				CredentialSetDefinition definition = change.apply(existing);
				if (CredentialSetDefinition.Kind.of(existing) != definition.kind()) {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE,
						"The type of a credential set can't change.");
				}
				Versions.check("credential set", version, expectedVersion);
				Domain domain = this.findDomain(dsl, definition.mgmtDomainId());
				return this.repository.save(dsl, CredentialSetAssembler.assemble(dsl, existing, definition, domain));
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to save the credentials {}.", id, e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been edited", credentialSet);
		return credentialSet;
	}

	/**
	 * Deletes a global credential set, if it is at a given version.
	 *
	 * @param id the credential set ID
	 * @param expectedVersion the version the credential set must be at, or null for any
	 * @throws NetshotException if the credential set doesn't exist or isn't at the expected version, is specific
	 *         to a device access or used by devices or tasks, or can't be deleted
	 */
	public void delete(long id, Long expectedVersion) {
		try {
			this.transactions.run(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				if (version == null) {
					throw new NetshotException(ErrorCode.NETSHOT_CREDENTIALS_NOT_FOUND, "The credential set doesn't exist.");
				}
				if (this.repository.find(dsl, id).deviceSpecific()) {
					throw new NetshotException(ErrorCode.NETSHOT_CREDENTIALS_IN_USE, "Can't delete a device-specific credential set.");
				}
				Versions.check("credential set", version, expectedVersion);
				this.repository.remove(dsl, id);
			});
		}
		catch (DataAccessException e) {
			if (DatabaseErrors.isForeignKeyViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_CREDENTIALS_IN_USE,
					"Unable to delete the credential set, there must be devices or tasks using it.");
			}
			log.error("Unable to delete the credentials {}", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the credential set");
		}
		AAA_LOG.info("Credential set of ID {} has been deleted.", id);
	}

	private static NetshotException saveFailure(DataAccessException e) {
		if (DatabaseErrors.isUniqueViolation(e)) {
			return new NetshotException(ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS, "A credential set with this name already exists.");
		}
		else if (DatabaseErrors.isForeignKeyViolation(e)) {
			return new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "The domain doesn't exist.");
		}
		return new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the credential set");
	}

	/**
	 * Deletes a global credential set.
	 *
	 * @param id the credential set ID
	 * @return true if the credential set has been deleted, false if there was no such credential set
	 * @throws NetshotException if the credential set is specific to a device access or used by
	 *         devices or tasks, or can't be deleted
	 */
	public boolean delete(long id) {
		boolean deleted;
		try {
			deleted = this.transactions.write(dsl -> {
				this.repository.lock(dsl, id);
				DeviceCredentialSet credentialSet = this.repository.find(dsl, id);
				if (credentialSet == null) {
					log.info("No such credential set of ID {}", id);
					return false;
				}
				if (credentialSet.deviceSpecific()) {
					throw new NetshotException(ErrorCode.NETSHOT_CREDENTIALS_IN_USE, "Can't delete a device-specific credential set.");
				}
				return this.repository.remove(dsl, id);
			});
		}
		catch (NetshotException e) {
			log.error("Unable to delete the credentials {}", id, e);
			throw e;
		}
		catch (RuntimeException e) {
			log.error("Unable to delete the credentials {}", id, e);
			if (e instanceof DataAccessException dae && DatabaseErrors.isForeignKeyViolation(dae)) {
				throw new NetshotException(ErrorCode.NETSHOT_CREDENTIALS_IN_USE,
					"Unable to delete the credential set, there must be devices or tasks using it.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the credential set");
		}
		if (deleted) {
			AAA_LOG.info("Credential set of ID {} has been deleted.", id);
		}
		return deleted;
	}
}
