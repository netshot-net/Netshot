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
package net.netshot.netshot.aaa.user;

import java.util.List;

import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The use cases on the API tokens: list, create and delete them. Only the hash of a token is
 * stored: the clear value is given by the administrator at creation and never read back.
 */
@Slf4j
public class ApiTokenService {

	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final ApiTokenRepository repository;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the API token queries
	 */
	public ApiTokenService(Transactions transactions, ApiTokenRepository repository) {
		this.transactions = transactions;
		this.repository = repository;
	}

	/**
	 * Lists the API tokens.
	 *
	 * @param page the window on the result
	 * @return the API tokens
	 */
	public List<ApiToken> list(Page page) {
		try {
			return this.transactions.readDsl(dsl -> this.repository.list(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to retrieve the API tokens.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to retrieve the API tokens.");
		}
	}

	/**
	 * Authenticates a request: looks for the API token matching a token value.
	 *
	 * @param token the clear token value, as received
	 * @return the API token, or null if the value is unknown or the database is not reachable
	 */
	public ApiToken authenticate(String token) {
		String hash = ApiToken.hashToken(token.trim());
		try {
			// Read-write pool, as before: a token just created must work even with a lagging read database
			ApiToken apiToken = this.transactions.writeDsl(dsl -> this.repository.findByHash(dsl, hash));
			if (apiToken == null) {
				AAA_LOG.warn("Invalid API token received.");
			}
			else {
				AAA_LOG.info("Successful API token usage {}.", apiToken);
			}
			return apiToken;
		}
		catch (DataAccessException e) {
			log.error("Database error while looking for API token", e);
			return null;
		}
	}

	/**
	 * Creates an API token. A token value can be registered only once (it identifies the token).
	 *
	 * @param description the description (a blank one is stored as empty)
	 * @param token the clear token value (32 alphanumeric characters)
	 * @param level the authorization level
	 * @return the new API token
	 * @throws NetshotException if the token has an invalid format, or is already registered
	 */
	public ApiToken create(String description, String token, int level) {
		if (description == null || description.trim().isEmpty()) {
			description = "";
		}
		description = description.trim();

		if (!ApiToken.isValidToken(token)) {
			log.warn("The passed token is not valid");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_API_TOKEN_FORMAT, "Invalid token format.");
		}

		ApiToken valid = ApiToken.of(description, token, level);

		ApiToken apiToken;
		try {
			apiToken = valid.withId(this.transactions.writeDsl(dsl -> {
				if (this.repository.existsByHash(dsl, valid.hashedToken())) {
					log.warn("The passed token is already registered");
					throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_API_TOKEN, "This token is already registered.");
				}
				return this.repository.add(dsl, valid);
			}));
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new API token.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the API token to the database");
		}
		AAA_LOG.info("{} has been created", apiToken);
		return apiToken;
	}

	/**
	 * Deletes an API token.
	 *
	 * @param id the ID of the API token
	 * @return true if the API token has been deleted, false if there is no such token
	 */
	public boolean delete(long id) {
		ApiToken apiToken;
		try {
			apiToken = this.transactions.writeDsl(dsl -> {
				ApiToken existing = this.repository.find(dsl, id);
				if (existing == null) {
					log.info("No such API token of ID {}", id);
					return null;
				}
				this.repository.remove(dsl, existing.id());
				return existing;
			});
		}
		catch (DataAccessException e) {
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the API token.");
		}
		if (apiToken == null) {
			return false;
		}
		AAA_LOG.info("API token of ID {} has been deleted", apiToken);
		return true;
	}
}
