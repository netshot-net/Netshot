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

import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.domain.DomainRepository;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

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
			return this.transactions.readDsl(dsl -> this.repository.findGlobal(dsl, page));
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
			created = this.transactions.writeDsl(dsl -> {
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
			credentialSet = this.transactions.writeDsl(dsl -> {
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
			deleted = this.transactions.writeDsl(dsl -> {
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
