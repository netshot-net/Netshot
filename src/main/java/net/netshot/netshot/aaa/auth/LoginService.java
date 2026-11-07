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
package net.netshot.netshot.aaa.auth;

import java.net.MalformedURLException;
import java.net.URI;

import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.PasswordPolicy;
import net.netshot.netshot.aaa.user.PasswordPolicy.PasswordPolicyException;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.UiUser.WrongPasswordException;
import net.netshot.netshot.aaa.user.UserRepository;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The authentication of the users who log in: local password (with the change of an expired
 * password), then the remote methods (OIDC, RADIUS, TACACS+).
 *
 * <p>The service only decides whether the credentials are good and who the user is. Creating,
 * renewing or dropping the HTTP session is up to the caller: it must drop the existing session
 * after every attempt which did not end with an exception, and only create a new one for a non-null
 * result.</p>
 */
@Slf4j
public class LoginService {

	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final UserRepository repository;
	private final RemoteAuthenticator remote;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the user queries
	 * @param remote the remote authentication methods
	 */
	public LoginService(Transactions transactions, UserRepository repository, RemoteAuthenticator remote) {
		this.transactions = transactions;
		this.repository = repository;
		this.remote = remote;
	}

	/**
	 * Authenticates the bearer of a JWT access token issued by the OIDC IdP. As for an OIDC login, a user
	 * configured as local can't be authenticated this way, and the level of a remote user configured in
	 * Netshot is the configured one.
	 *
	 * @param token the token
	 * @param remoteAddress the address of the client (for the audit log)
	 * @return the authenticated user, or null if the token is invalid or the user has an insufficient level
	 * @throws NetshotException if the database can't be accessed
	 */
	public UiUser authenticateBearer(String token, String remoteAddress) {
		UiUser remoteUser = this.remote.authenticateWithBearerToken(token);
		if (remoteUser == null) {
			AAA_LOG.warn("JWT bearer authentication failure from {}.", remoteAddress);
			return null;
		}
		String username = remoteUser.username();
		UiUser user;
		try {
			user = this.transactions.read(dsl -> this.repository.findByUsername(dsl, username));
		}
		catch (DataAccessException e) {
			log.error("Unable to retrieve the user {}.", username, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to retrieve the user.");
		}
		if (user != null && user.local()) {
			AAA_LOG.warn("JWT bearer token of user {} from {} refused: the user is configured as Netshot local.",
				username, remoteAddress);
			return null;
		}
		if (user != null) {
			remoteUser = remoteUser.withLevel(user.level());
		}
		if (remoteUser.level() <= 0) {
			AAA_LOG.info("User {} was authenticated by a JWT bearer token but has unsufficient permission (level {})",
				username, remoteUser.level());
			return null;
		}
		AAA_LOG.info("Successful JWT bearer authentication of user {} from {} (level {}).", username, remoteAddress, remoteUser.level());
		return remoteUser;
	}

	/**
	 * Authenticates a user.
	 *
	 * @param attempt the credentials presented
	 * @return the authenticated user, or null if the authentication failed or the user has an
	 *         insufficient level (level 0 or lower)
	 * @throws NetshotException if the password of the user has expired and no new one is given, if the
	 *         new password doesn't comply with the policy, or if the database can't be accessed
	 */
	public UiUser authenticate(LoginAttempt attempt) {
		AAA_LOG.info("REST authentication request, username {}.", attempt.username());

		String remoteAddress = attempt.remoteAddress();
		UiUser user = null;
		UiUser remoteUser = null;
		String username = attempt.username();

		if (username == null && attempt.authorizationCode() != null && attempt.redirectUri() != null) {
			try {
				remoteUser = this.remote.authenticateWithCode(
					attempt.authorizationCode(),
					URI.create(attempt.redirectUri()).toURL());
				if (remoteUser != null) {
					username = remoteUser.username();
				}
			}
			catch (MalformedURLException | IllegalArgumentException e) {
				// IllegalArgumentException: invalid URI syntax, or a relative URI
				log.error("Error while extracting client request URL", e);
			}
		}

		if (username != null) {
			String name = username;
			try {
				user = this.transactions.read(dsl -> this.repository.findByUsername(dsl, name));
			}
			catch (DataAccessException e) {
				log.error("Unable to retrieve the user {}.", username, e);
				throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to retrieve the user.");
			}
		}

		if (user != null && user.local()) {
			try {
				if (remoteUser != null) {
					// Remote login (via OIDC) while a corresponding user configured as local exists
					throw new WrongPasswordException("Remote login attempt with username which is configured as Netshot local");
				}
				try {
					user.checkPassword(attempt.password(), PasswordPolicy.getMainPolicy());
					AAA_LOG.info(
						"Local authentication success for user {} from {}.", username, remoteAddress);
				}
				catch (PasswordPolicyException e) {
					if (attempt.newPassword() == null) {
						throw e;
					}
					AAA_LOG.info(
						"User {} authenticated from {}, password has to be changed.", username, remoteAddress);
					// If new password, proceed with password change
				}
				if (attempt.newPassword() != null) {
					this.changePassword(user.id(), attempt.newPassword());
					AAA_LOG.info(
						"User {} changed its password.", username, remoteAddress);
				}
			}
			catch (WrongPasswordException e) {
				AAA_LOG.warn(
					"Local authentication failure for user {} from {}.",
					username, remoteAddress);
				user = null;
			}
			catch (PasswordPolicyException e) {
				AAA_LOG.warn(
					"Password of user {} is expired, it must be changed.", username);
				user = null;
				throw new NetshotException(ErrorCode.NETSHOT_EXPIRED_PASSWORD, "Password has expired, it must be changed.");
			}
		}
		else if (username != null) {
			if (remoteUser == null) {
				remoteUser = this.remote.authenticateWithRadius(username, attempt.password(), remoteAddress);
			}
			if (remoteUser == null) {
				remoteUser = this.remote.authenticateWithTacacs(username, attempt.password(), remoteAddress);
			}
			if (remoteUser == null) {
				AAA_LOG.warn("Remote authentication failure for user {} from {}.",
					username, remoteAddress);
			}
			else {
				AAA_LOG.info("Remote authentication success for user {} from {}.",
					username, remoteAddress);
				if (user != null) {
					remoteUser = remoteUser.withLevel(user.level());
					AAA_LOG.info("Level permission for user {} is locally overriden: {}.",
						username, user.level());
				}
			}
			user = remoteUser;
		}

		if (user != null && user.level() <= 0) {
			AAA_LOG.info(
				"User {} was successfully authenticated but has unsufficient permission (level {})",
				user.username(), user.level());
			return null;
		}
		return user;
	}

	/**
	 * Sets the new password of a user who just authenticated.
	 *
	 * @param userId the ID of the user
	 * @param newPassword the new clear password
	 * @throws NetshotException if the password doesn't comply with the policy or the user can't be saved
	 */
	private void changePassword(long userId, String newPassword) {
		try {
			this.transactions.run(dsl -> {
				this.repository.lock(dsl, userId);
				UiUser user = this.repository.find(dsl, userId);
				try {
					this.repository.save(dsl, user.withPassword(newPassword, PasswordPolicy.getMainPolicy()));
				}
				catch (PasswordPolicyException e) {
					log.error("New user password doesn't comply with password policy.", e);
					throw new NetshotException(ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY, e.getMessage());
				}
			});
		}
		catch (DataAccessException e) {
			log.error("Error while updating a user.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to update the user in the database");
		}
	}
}
