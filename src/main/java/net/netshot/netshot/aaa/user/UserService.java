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

import org.hibernate.HibernateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.PasswordPolicy.PasswordPolicyException;
import net.netshot.netshot.aaa.user.UiUser.WrongPasswordException;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

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
			return this.transactions.read(session -> this.repository.list(session, page));
		}
		catch (HibernateException e) {
			log.error("Unable to retrieve the users.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to retrieve the users.");
		}
	}

	/**
	 * Creates a user. The password of a local user is not checked against the password policy.
	 *
	 * @param creation the user to create
	 * @return the new user
	 * @throws NetshotException if the name is empty, a local user has no password, the name is taken
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

		UiUser user = new UiUser(username, creation.local(), password);
		user.setLevel(creation.level());

		try {
			this.transactions.run(session -> this.repository.persist(session, user));
		}
		catch (HibernateException e) {
			log.error("Error while saving the new user.", e);
			if (Database.isDuplicateException(e)) {
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
			user = this.transactions.write(session -> {
				UiUser existing = this.repository.find(session, id);
				if (existing == null) {
					log.error("Unable to find the user {} to be edited.", id);
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_USER, "Unable to find this user.");
				}
				if (existing.getUsername().equals(currentUsername)) {
					throw new NetshotException(ErrorCode.NETSHOT_LOCKED_USER, "Cannot edit the user who is making the request.");
				}

				String username = update.username();
				if (username == null || username.trim().isEmpty()) {
					log.warn("User posted an empty user name.");
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_USER_NAME, "Invalid user name.");
				}
				username = username.trim();
				existing.setUsername(username);

				existing.setLevel(update.level());
				if (update.local()) {
					if (update.password() == null) {
						if (!existing.isLocal()) {
							log.error("The password cannot be empty for user {}.", id);
							throw new NetshotException(ErrorCode.NETSHOT_INVALID_PASSWORD, "You must set a password.");
						}
					}
					else {
						try {
							existing.setPassword(update.password(), PasswordPolicy.getMainPolicy());
						}
						catch (PasswordPolicyException e) {
							log.error("New user password doesn't comply with password policy.", e);
							throw new NetshotException(ErrorCode.NETSHOT_INVALID_PASSWORD, e.getMessage());
						}
					}
				}
				else {
					existing.setHashedPassword("");
				}
				existing.setLocal(update.local());
				return existing;
			});
		}
		catch (HibernateException e) {
			log.error("Unable to save the user {}.", id, e);
			if (Database.isDuplicateException(e)) {
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
			user = this.transactions.write(session -> {
				UiUser existing = this.repository.find(session, id);
				if (existing == null) {
					log.info("No such user of ID {}", id);
					return null;
				}
				if (existing.getUsername().equals(currentUsername)) {
					throw new NetshotException(ErrorCode.NETSHOT_LOCKED_USER, "Cannot delete the user who is making the request.");
				}
				this.repository.remove(session, existing);
				return existing;
			});
		}
		catch (HibernateException e) {
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the user.");
		}
		if (user == null) {
			return false;
		}
		AAA_LOG.info("User {} of ID {} has been deleted", user.getUsername(), user.getId());
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
			user = this.transactions.write(session -> {
				UiUser existing = this.repository.findByUsername(session, currentUsername);
				if (existing == null || !existing.getUsername().equals(username) || !existing.isLocal()) {
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

				try {
					existing.setPassword(newPassword, PasswordPolicy.getMainPolicy());
				}
				catch (PasswordPolicyException e) {
					log.error("New user password doesn't comply with password policy.", e);
					throw new NetshotException(ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY, e.getMessage());
				}
				this.repository.persist(session, existing);
				return existing;
			});
		}
		catch (HibernateException e) {
			log.error("Unable to retrieve the user {}.", username, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to retrieve the user.");
		}
		AAA_LOG.warn("Password successfully changed by user {} for user {}.", currentUsername, username);
		return user;
	}
}
