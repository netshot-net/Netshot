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
package net.netshot.netshot.credentials;

import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.domain.DomainRepository;
import net.netshot.netshot.platform.database.DatabaseErrors;
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

}
