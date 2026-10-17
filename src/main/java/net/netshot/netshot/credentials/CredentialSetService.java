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

import org.hibernate.HibernateException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The use cases of the global credential sets: list, add, edit and delete.
 *
 * <p>The inputs of {@link #create} and {@link #update} are <em>transient</em> credential sets
 * holding what the client sent (the API mapper builds them): they are validated, then either
 * persisted (create) or copied onto the persistent credential set (update). The secrets the client
 * left out or masked are {@code null} in them, which means "unchanged".</p>
 *
 * <p>The credential sets it returns are detached: the associations their representation reads
 * (domain, Vault instances) are eager.</p>
 */
@Slf4j
public class CredentialSetService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final CredentialSetRepository repository;

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
	 * @return the credential sets
	 * @throws NetshotException if the credential sets can't be read
	 */
	public List<DeviceCredentialSet> list(Page page) {
		try {
			return this.transactions.read(session -> this.repository.findGlobal(session, page));
		}
		catch (HibernateException e) {
			log.error("Unable to fetch the credentials.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the credentials");
		}
	}

	/**
	 * Adds a global credential set.
	 *
	 * @param credentialSet the credential set to add (it becomes the persistent one)
	 * @return the new credential set
	 * @throws NetshotException if the name is blank, the domain or a Vault instance doesn't exist,
	 *         the name is already used, or the credential set can't be stored
	 */
	public DeviceCredentialSet create(DeviceCredentialSet credentialSet) {
		if (credentialSet.getName() == null || "".equals(credentialSet.getName().trim())) {
			log.error("Invalid credential set name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, "Invalid name for the credential set");
		}
		try {
			this.transactions.run(session -> {
				if (credentialSet.getMgmtDomain() != null) {
					Domain domain = this.repository.findDomain(session, credentialSet.getMgmtDomain().getId());
					if (domain == null) {
						throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "Invalid domain");
					}
					credentialSet.setMgmtDomain(domain);
				}
				credentialSet.setDeviceSpecific(false);
				CredentialSets.applyVaultableFields(session, credentialSet, credentialSet);
				this.repository.add(session, credentialSet);
				this.repository.refresh(session, credentialSet);
			});
		}
		catch (HibernateException e) {
			log.error("Can't add the credentials.", e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been created.", credentialSet);
		return credentialSet;
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
			credentialSet = this.transactions.write(session -> {
				DeviceCredentialSet existing = this.repository.find(session, incoming.getClass(), id);
				if (existing == null) {
					log.error("Unable to find the credential set {}.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS, "Unable to find the credential set.");
				}
				if (!existing.getClass().equals(incoming.getClass())) {
					log.error("Wrong posted credential type for credential set {}.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE,
						"The posted credential type doesn't match the existing one.");
				}
				if (incoming.getMgmtDomain() == null) {
					existing.setMgmtDomain(null);
				}
				else {
					Domain domain = this.repository.findDomain(session, incoming.getMgmtDomain().getId());
					if (domain == null) {
						log.error("Unknown domain {} for credential set {}.", incoming.getMgmtDomain().getId(), id);
						throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "The domain doesn't exist.");
					}
					existing.setMgmtDomain(domain);
				}
				existing.setName(incoming.getName());
				if (existing.getName() == null || "".equals(existing.getName().trim())) {
					log.error("Invalid credential set name.");
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, "Invalid name for the credential set");
				}
				applyChanges(existing, incoming);
				// Applied last so that switching a field to Vault mode always wins over
				// the unconditional local-value copies above (e.g. setUsername(...)).
				CredentialSets.applyVaultableFields(session, existing, incoming);
				this.repository.save(session, existing);
				return existing;
			});
		}
		catch (HibernateException e) {
			log.error("Unable to save the credentials {}.", id, e);
			throw saveFailure(e);
		}
		AAA_LOG.info("{} has been edited", credentialSet);
		return credentialSet;
	}

	/**
	 * Copies the local values of a new definition onto the credential set. A secret which is
	 * {@code null} in the definition is left as it is.
	 *
	 * @param existing the persistent credential set
	 * @param incoming the new definition
	 */
	private static void applyChanges(DeviceCredentialSet existing, DeviceCredentialSet incoming) {
		if (existing instanceof DeviceCliAccount cliAccount
				&& incoming instanceof DeviceCliAccount rsCliAccount) {
			cliAccount.setUsername(rsCliAccount.getUsername());
			if (rsCliAccount.getPassword() != null) {
				cliAccount.setPassword(rsCliAccount.getPassword());
			}
			if (rsCliAccount.getSuperPassword() != null) {
				cliAccount.setSuperPassword(rsCliAccount.getSuperPassword());
			}
			if (cliAccount instanceof DeviceSshKeyAccount sshKeyAccount
					&& rsCliAccount instanceof DeviceSshKeyAccount rsSshKeyAccount) {
				if (rsSshKeyAccount.getPrivateKey() != null) {
					sshKeyAccount.setPrivateKey(rsSshKeyAccount.getPrivateKey());
				}
			}
		}
		else if (existing instanceof DeviceSnmpv3Community snmp3
				&& incoming instanceof DeviceSnmpv3Community rsSnmp3) {
			snmp3.setUsername(rsSnmp3.getUsername());
			snmp3.setAuthType(rsSnmp3.getAuthType());
			if (rsSnmp3.getAuthKey() != null) {
				snmp3.setAuthKey(rsSnmp3.getAuthKey());
			}
			snmp3.setPrivType(rsSnmp3.getPrivType());
			if (rsSnmp3.getPrivKey() != null) {
				snmp3.setPrivKey(rsSnmp3.getPrivKey());
			}
		}
		else if (existing instanceof DeviceSnmpCommunity snmpCommunity
				&& incoming instanceof DeviceSnmpCommunity rsSnmpCommunity) {
			String community = rsSnmpCommunity.getCommunity();
			if (community != null) {
				snmpCommunity.setCommunity(community);
			}
		}
		else if (existing instanceof DeviceHttpAccount httpAccount
				&& incoming instanceof DeviceHttpAccount rsHttpAccount) {
			httpAccount.setUsername(rsHttpAccount.getUsername());
			if (rsHttpAccount.getPassword() != null) {
				httpAccount.setPassword(rsHttpAccount.getPassword());
			}
		}
	}

	private static NetshotException saveFailure(HibernateException e) {
		if (DatabaseErrors.causeMentions(e, "uplicate")) {
			return new NetshotException(ErrorCode.NETSHOT_DUPLICATE_CREDENTIALS, "A credential set with this name already exists.");
		}
		else if (DatabaseErrors.causeMentions(e, "mgmt_domain")) {
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
			deleted = this.transactions.write(session -> {
				DeviceCredentialSet credentialSet = this.repository.find(session, id);
				if (credentialSet == null) {
					log.info("No such credential set of ID {}", id);
					return false;
				}
				if (credentialSet.isDeviceSpecific()) {
					throw new NetshotException(ErrorCode.NETSHOT_CREDENTIALS_IN_USE, "Can't delete a device-specific credential set.");
				}
				this.repository.remove(session, credentialSet);
				return true;
			});
		}
		catch (NetshotException e) {
			log.error("Unable to delete the credentials {}", id, e);
			throw e;
		}
		catch (RuntimeException e) {
			log.error("Unable to delete the credentials {}", id, e);
			if (e instanceof ConstraintViolationException) {
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
