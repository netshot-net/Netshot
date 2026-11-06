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
package net.netshot.netshot.domain;

import java.net.UnknownHostException;
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
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.QuerySchema;

/**
 * The domain use cases: list, add, edit and delete the device domains.
 *
 * <p>The domains it returns are records: a domain has no association to load for its representation.</p>
 */
@Slf4j
public class DomainService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final DomainRepository repository;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the domain repository
	 */
	public DomainService(Transactions transactions, DomainRepository repository) {
		this.transactions = transactions;
		this.repository = repository;
	}

	/**
	 * Lists the domains.
	 *
	 * @param page the window on the result
	 * @return the domains, by ID
	 * @throws NetshotException if the domains can't be read
	 */
	public List<Domain> list(Page page) {
		try {
			return this.transactions.read(dsl -> this.repository.findAll(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the domains.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the domains");
		}
	}

	/**
	 * Gets what the domain lists can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public static QuerySchema listSchema() {
		return DomainRepository.LIST.schema();
	}

	/**
	 * Lists the domains matching a query.
	 *
	 * @param query the query
	 * @return the domains of the window, with the total number of matching domains if the query asks for it
	 * @throws NetshotException if a filter is invalid, or the domains can't be read
	 */
	public ListResult<Domain> list(ListQuery query) {
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.repository.findAll(dsl, query),
				query.count() ? this.repository.count(dsl, query) : null));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the domains.", e);
			throw DatabaseErrors.listFailure(e, "Unable to fetch the domains");
		}
	}

	/**
	 * Gets a domain.
	 *
	 * @param id the domain ID
	 * @return the domain
	 * @throws NetshotException if there is no such domain, or it can't be read
	 */
	public Domain get(long id) {
		Domain domain;
		try {
			domain = this.transactions.read(dsl -> this.repository.find(dsl, id));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the domain {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the domain");
		}
		if (domain == null) {
			throw new NetshotException(ErrorCode.NETSHOT_DOMAIN_NOT_FOUND, "The domain doesn't exist.");
		}
		return domain;
	}

	/**
	 * Adds a domain.
	 *
	 * @param definition the domain to add
	 * @return the new domain
	 * @throws NetshotException if the name or the address is invalid, the name is already used,
	 *         or the domain can't be stored
	 */
	public Domain create(DomainDefinition definition) {
		String name = definition.name() == null ? "" : definition.name().trim();
		if (name.isEmpty()) {
			log.warn("User posted an empty domain name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN_NAME, "Invalid domain name.");
		}
		String description = definition.description() == null ? "" : definition.description().trim();
		Domain definedDomain;
		try {
			Network4Address v4Address = parseServerAddress(definition.server4Address());
			Network6Address v6Address = new Network6Address("::");
			definedDomain = Domain.builder().name(name).description(description).server4Address(v4Address).server6Address(v6Address).build();
		}
		catch (UnknownHostException e) {
			log.warn("User posted an invalid IP address.");
			throw new NetshotException(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, "Malformed IP address");
		}
		Domain domain;
		try {
			domain = this.transactions.write(dsl -> this.repository.add(dsl, definedDomain));
		}
		catch (DataAccessException e) {
			log.error("Error while adding a domain.", e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_DOMAIN, "A domain with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the domain to the database");
		}
		AAA_LOG.info("{} has been created.", domain);
		return domain;
	}

	/**
	 * Edits a domain.
	 *
	 * @param id the domain ID
	 * @param definition the new definition of the domain
	 * @return the edited domain
	 * @throws NetshotException if the name or the address is invalid, the domain doesn't exist,
	 *         the name is already used, or the domain can't be stored
	 */
	public Domain update(long id, DomainDefinition definition) {
		return this.update(id, definition, null);
	}

	/**
	 * Edits a domain, if it is still at a given version.
	 *
	 * @param id the domain ID
	 * @param definition the new definition of the domain
	 * @param expectedVersion the version the domain must be at, or null for any
	 * @return the edited domain
	 * @throws NetshotException if the name or the address is invalid, the domain doesn't exist, isn't at the
	 *         expected version, the name is already used, or the domain can't be stored
	 */
	public Domain update(long id, DomainDefinition definition, Long expectedVersion) {
		Domain valid = validate(definition);
		return this.update(id, _ -> definition, expectedVersion, valid);
	}

	/**
	 * Edits a domain, if it is still at a given version: the new definition is computed from the domain as it is,
	 * which can't change meanwhile (to apply a patch).
	 *
	 * @param id the domain ID
	 * @param change gives the new definition from the current domain
	 * @param expectedVersion the version the domain must be at, or null for any
	 * @return the edited domain
	 * @throws NetshotException if the name or the address is invalid, the domain doesn't exist, isn't at the
	 *         expected version, the name is already used, or the domain can't be stored
	 */
	public Domain update(long id, Function<Domain, DomainDefinition> change, Long expectedVersion) {
		return this.update(id, change, expectedVersion, null);
	}

	// With the definition already validated (as a domain), if it is known
	private Domain update(long id, Function<Domain, DomainDefinition> change, Long expectedVersion, Domain validated) {
		Domain domain;
		try {
			domain = this.transactions.write(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				if (version == null) {
					log.error("The domain of ID {} doesn't exist.", id);
					throw new NetshotException(ErrorCode.NETSHOT_DOMAIN_NOT_FOUND, "The domain doesn't exist.");
				}
				Domain existing = this.repository.find(dsl, id);
				Domain valid = validated == null ? validate(change.apply(existing)) : validated;
				Versions.check("domain", version, expectedVersion);
				return this.repository.save(dsl, existing.toBuilder()
					.name(valid.name())
					.description(valid.description())
					.server4Address(valid.server4Address())
					.build());
			});
		}
		catch (NetshotException e) {
			log.error("Cannot edit the domain.", e);
			throw e;
		}
		catch (DataAccessException e) {
			log.error("Error while editing the domain.", e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_DOMAIN, "A domain with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the domain... is the name already in use?");
		}
		AAA_LOG.info("{} has been edited.", domain);
		return domain;
	}

	/**
	 * Deletes a domain.
	 *
	 * @param id the domain ID
	 * @throws NetshotException if the domain doesn't exist, is used by devices or credential sets,
	 *         or can't be deleted
	 */
	public void delete(long id) {
		this.delete(id, null);
	}

	/**
	 * Deletes a domain, if it is at a given version.
	 *
	 * @param id the domain ID
	 * @param expectedVersion the version the domain must be at, or null for any
	 * @throws NetshotException if the domain doesn't exist, isn't at the expected version, is used by devices or
	 *         credential sets, or can't be deleted
	 */
	public void delete(long id, Long expectedVersion) {
		try {
			this.transactions.run(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				if (version == null) {
					log.error("The domain of ID {} doesn't exist.", id);
					throw new NetshotException(ErrorCode.NETSHOT_DOMAIN_NOT_FOUND, "The domain doesn't exist.");
				}
				Versions.check("domain", version, expectedVersion);
				this.repository.remove(dsl, id);
			});
		}
		catch (DataAccessException e) {
			if (DatabaseErrors.isForeignKeyViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DOMAIN_IN_USE,
					"Unable to delete the domain, there must be devices or credential sets using it.");
			}
			log.error("Unable to delete the domain {}", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the domain");
		}
		AAA_LOG.info("Domain of ID {} has been deleted.", id);
	}

	/**
	 * Parses the IPv4 address of the server of a domain, which must be a normal unicast address.
	 *
	 * @param address the address, as posted
	 * @return the address
	 * @throws UnknownHostException if the address is malformed
	 * @throws NetshotException if there is no address, or it isn't a normal unicast one
	 */
	private static Network4Address parseServerAddress(String address) throws UnknownHostException {
		if (address == null) {
			log.warn("User posted no IP address.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, "Invalid IP address");
		}
		Network4Address v4Address = new Network4Address(address);
		if (!v4Address.isNormalUnicast()) {
			log.warn("User posted an invalid IP address.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, "Invalid IP address");
		}
		return v4Address;
	}

	/**
	 * Checks the definition of a domain, for an edition.
	 *
	 * @param definition the definition
	 * @return the name, description and IPv4 server address it gives (trimmed), as a domain
	 * @throws NetshotException if the name is empty or the address is invalid
	 */
	private static Domain validate(DomainDefinition definition) {
		String name = definition.name() == null ? "" : definition.name().trim();
		if (name.isEmpty()) {
			log.warn("User posted an invalid domain name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN_NAME, "Invalid domain name.");
		}
		String description = definition.description() == null ? "" : definition.description().trim();
		try {
			return Domain.builder().name(name).description(description).server4Address(parseServerAddress(definition.server4Address())).build();
		}
		catch (UnknownHostException e) {
			log.warn("User posted an invalid IP address.");
			throw new NetshotException(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, "Malformed IP address");
		}
	}
}
