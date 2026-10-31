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
import net.netshot.netshot.aaa.user.PasswordPolicy.PasswordPolicyException;
import net.netshot.netshot.aaa.user.UiUser.WrongPasswordException;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.QuerySchema;

/**
 * The use cases on the users: list, create, edit and delete them (administrators), and
 * change the own password of the current user.
 *
 * <p>The service knows nothing of the HTTP session: the name of the user making the request is
 * given by the caller.</p>
 */
@Slf4j
public class UserService {

	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final UserRepository repository;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the user queries
	 */
	public UserService(Transactions transactions, UserRepository repository) {
		this.transactions = transactions;
		this.repository = repository;
	}

	/**
	 * Lists the users.
	 *
	 * @param page the window on the result
	 * @return the users
	 */
	public List<UiUser> list(Page page) {
		try {
			return this.transactions.read(dsl -> this.repository.list(dsl, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to retrieve the users.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to retrieve the users.");
		}
	}

	/**
	 * Gets what the user lists can be filtered and sorted on.
	 *
	 * @return the schema of the list queries
	 */
	public QuerySchema listSchema() {
		return UserRepository.LIST.schema();
	}

	/**
	 * Lists the users matching a query.
	 *
	 * @param query the query
	 * @return the users of the window, with the total number of matching users if the query asks for it
	 * @throws NetshotException if a filter is invalid, or the users can't be read
	 */
	public ListResult<UiUser> list(ListQuery query) {
		try {
			return this.transactions.read(dsl -> new ListResult<>(this.repository.findAll(dsl, query),
				query.count() ? this.repository.count(dsl, query) : null));
		}
		catch (DataAccessException e) {
			log.error("Unable to retrieve the users.", e);
			throw DatabaseErrors.listFailure(e, "Unable to retrieve the users.");
		}
	}

	/**
	 * Gets a user.
	 *
	 * @param id the user ID
	 * @return the user
	 * @throws NetshotException if there is no such user, or it can't be read
	 */
	public UiUser get(long id) {
		UiUser user;
		try {
			user = this.transactions.read(dsl -> this.repository.find(dsl, id));
		}
		catch (DataAccessException e) {
			log.error("Unable to retrieve the user {}.", id, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to retrieve the user.");
		}
		if (user == null) {
			throw new NetshotException(ErrorCode.NETSHOT_USER_NOT_FOUND, "The user doesn't exist.");
		}
		return user;
	}

	/**
	 * Creates a user. The password of a local user must comply with the password policy.
	 *
	 * @param creation the user to create
	 * @return the new user
	 * @throws NetshotException if the name is empty, a local user has no password or one which doesn't
	 *         comply with the password policy, the name is taken
	 */
	public UiUser create(UserCreation creation) {
		String username = creation.username();
		if (username == null || username.trim().isEmpty()) {
			log.warn("User posted an empty user name.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_USER_NAME, "Invalid user name.");
		}
		username = username.trim();

		String password = creation.password();
		if (creation.local()) {
			if (password == null || "".equals(password)) {
				log.warn("User tries to create a local account without password.");
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_PASSWORD, "Please set a password.");
			}
		}
		else {
			password = "";
		}

		UiUser valid;
		if (creation.local()) {
			try {
				valid = new UiUser(0, username, true, creation.level(), null, List.of(), null)
					.withPassword(password, PasswordPolicy.getMainPolicy());
			}
			catch (PasswordPolicyException e) {
				log.warn("New user password doesn't comply with password policy.", e);
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_PASSWORD, e.getMessage());
			}
		}
		else {
			valid = UiUser.withPassword(username, false, creation.level(), password);
		}

		UiUser user;
		try {
			user = valid.withId(this.transactions.write(dsl -> this.repository.add(dsl, valid)));
		}
		catch (DataAccessException e) {
			log.error("Error while saving the new user.", e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_USER, "A user with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the user to the database");
		}
		AAA_LOG.info("{} has been created", user);
		return user;
	}

	/**
	 * Edits a user. The user making the request cannot edit itself.
	 *
	 * @param id the ID of the user to edit
	 * @param currentUsername the name of the user making the request
	 * @param update the new settings
	 * @return the user
	 * @throws NetshotException if the user doesn't exist, is the current one, or the new settings are invalid
	 */
	public UiUser update(long id, String currentUsername, UserUpdate update) {
		UiUser user;
		try {
			user = this.transactions.write(dsl -> {
				UiUser existing = this.repository.find(dsl, id);
				if (existing == null) {
					log.error("Unable to find the user {} to be edited.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_USER, "Unable to find this user.");
				}
				if (existing.username().equals(currentUsername)) {
					throw new NetshotException(ErrorCode.NETSHOT_LOCKED_USER, "Cannot edit the user who is making the request.");
				}

				String username = update.username();
				if (username == null || username.trim().isEmpty()) {
					log.warn("User posted an empty user name.");
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_USER_NAME, "Invalid user name.");
				}
				username = username.trim();

				UiUser edited = existing.withSettings(username, update.local(), update.level());
				if (update.local()) {
					if (update.password() == null) {
						if (!existing.local()) {
							log.error("The password cannot be empty for user {}.", id);
							throw new NetshotException(ErrorCode.NETSHOT_INVALID_PASSWORD, "You must set a password.");
						}
					}
					else {
						try {
							edited = edited.withPassword(update.password(), PasswordPolicy.getMainPolicy());
						}
						catch (PasswordPolicyException e) {
							log.error("New user password doesn't comply with password policy.", e);
							throw new NetshotException(ErrorCode.NETSHOT_INVALID_PASSWORD, e.getMessage());
						}
					}
				}
				else {
					edited = edited.withHashedPassword("");
				}
				this.repository.save(dsl, edited);
				return edited;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to save the user {}.", id, e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_USER, "A user with this name already exists.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the user.");
		}
		AAA_LOG.info("{} has been edited", user);
		return user;
	}

	/**
	 * Deletes a user. The user making the request cannot delete itself.
	 *
	 * @param id the ID of the user to delete
	 * @param currentUsername the name of the user making the request
	 * @return true if the user has been deleted, false if there is no such user
	 * @throws NetshotException if the user is the current one
	 */
	public boolean delete(long id, String currentUsername) {
		UiUser user;
		try {
			user = this.transactions.write(dsl -> {
				UiUser existing = this.repository.find(dsl, id);
				if (existing == null) {
					log.info("No such user of ID {}", id);
					return null;
				}
				if (existing.username().equals(currentUsername)) {
					throw new NetshotException(ErrorCode.NETSHOT_LOCKED_USER, "Cannot delete the user who is making the request.");
				}
				this.repository.remove(dsl, existing.id());
				return existing;
			});
		}
		catch (DataAccessException e) {
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the user.");
		}
		if (user == null) {
			return false;
		}
		AAA_LOG.info("User {} of ID {} has been deleted", user.username(), user.id());
		return true;
	}

	/**
	 * Changes the password of the current user, who must be a local user. The current password
	 * is checked (an expired password is accepted), the new one must comply with the password policy.
	 *
	 * @param currentUsername the name of the user making the request
	 * @param username the name of the user the request is for (must be the current user)
	 * @param password the current clear password
	 * @param newPassword the new clear password
	 * @return the user
	 * @throws NetshotException if the user is invalid, the current password wrong, or the new one invalid
	 */
	public UiUser changeOwnPassword(String currentUsername, String username, String password, String newPassword) {
		AAA_LOG.warn("Password change request via REST by user {} for user {}.", currentUsername, username);
		UiUser user;
		try {
			user = this.transactions.write(dsl -> {
				UiUser existing = this.repository.findByUsername(dsl, currentUsername);
				if (existing == null || !existing.username().equals(username) || !existing.local()) {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_USER, "Invalid user.");
				}

				try {
					existing.checkPassword(password, PasswordPolicy.getMainPolicy());
				}
				catch (WrongPasswordException e) {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_PASSWORD, "Invalid current password.");
				}
				catch (PasswordPolicyException e) {
					// Ignore to let user update the password
				}

				if (newPassword == null || "".equals(newPassword)) {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_PASSWORD, "The password cannot be empty.");
				}

				UiUser changed;
				try {
					changed = existing.withPassword(newPassword, PasswordPolicy.getMainPolicy());
				}
				catch (PasswordPolicyException e) {
					log.error("New user password doesn't comply with password policy.", e);
					throw new NetshotException(ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY, e.getMessage());
				}
				this.repository.save(dsl, changed);
				return changed;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to retrieve the user {}.", username, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to retrieve the user.");
		}
		AAA_LOG.warn("Password successfully changed by user {} for user {}.", currentUsername, username);
		return user;
	}
}
