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
package net.netshot.netshot.aaa.user;

import java.io.InvalidClassException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.aaa.user.PasswordPolicy.PasswordPolicyException;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.crypto.Argon2idHash;
import net.netshot.netshot.platform.crypto.Hash;

/**
 * A Netshot user: a local one (with a password stored in Netshot), or a remote one (authenticated
 * by RADIUS, TACACS+ or OIDC, possibly with a local entry to override its level).
 *
 * @param id the ID (0 for a user not stored, such as a remote user without local entry)
 * @param version the version, incremented on each change
 * @param changeDate the date of the last change
 * @param username the user name
 * @param local true for a local user
 * @param level the authorization level
 * @param hashedPassword the hash of the password
 * @param oldHashedPasswords the hashes of the previous passwords, most recent first
 * @param lastPasswordChangeDate when the password was last changed (null if unknown)
 */
@Slf4j
public record UiUser(long id, int version, Instant changeDate, String username, boolean local, int level, String hashedPassword,
	List<String> oldHashedPasswords, Instant lastPasswordChangeDate) implements User {

	/**
	 * The settings of the users.
	 *
	 * @param maxIdleTime the max idle time of a user session (in seconds)
	 */
	public record Settings(int maxIdleTime) {

		/** The max idle time of a user session (in seconds). */
		static final Setting<Integer> MAX_IDLE_TIME = Setting.integer("netshot.aaa.maxidletime")
			.defaultValue(1800).range(5, Integer.MAX_VALUE).live();

		/**
		 * Reads the settings.
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			return new Settings(config.get(MAX_IDLE_TIME));
		}
	}

	/**
	 * Exception to be thrown in case of password check failure.
	 */
	public static class WrongPasswordException extends Exception {
		private static final long serialVersionUID = 1L;

		public WrongPasswordException(String message) {
			super(message);
		}
	}

	/** The settings, from the configuration. */
	private static final ConfigStore.Binding<Settings> SETTINGS = Netshot.config().bind(Settings::from);

	/**
	 * Gets the settings of the users.
	 * @return the settings
	 */
	public static Settings settings() {
		return SETTINGS.get();
	}

	/**
	 * Hash the password for future storage.
	 *
	 * @param password the plaintext password
	 * @return the hashed password
	 */
	private static String hash(String password) {
		Hash hash = new Argon2idHash(password);
		return hash.toHashString();
	}

	/**
	 * Builds a user with a password (not checked against the password policy), not stored yet.
	 *
	 * @param username the user name
	 * @param local true for a local user
	 * @param level the authorization level
	 * @param password the clear password
	 * @return the user
	 */
	public static UiUser withPassword(String username, boolean local, int level, String password) {
		return new UiUser(0, 0, null, username, local, level, null, List.of(), null).withPassword(password);
	}

	/**
	 * Builds a remote user, as authenticated by a remote server.
	 *
	 * @param username the user name
	 * @param level the authorization level
	 * @return the user
	 */
	public static UiUser remote(String username, int level) {
		return new UiUser(0, 0, null, username, false, level, null, List.of(), null);
	}

	/**
	 * Canonical constructor: the password history is copied, none means an empty one.
	 */
	public UiUser {
		oldHashedPasswords = oldHashedPasswords == null ? List.of() : List.copyOf(oldHashedPasswords);
	}

	/**
	 * Gets a copy of the user with another ID.
	 *
	 * @param newId the ID
	 * @return the user
	 */
	public UiUser withId(long newId) {
		return new UiUser(newId, this.version, this.changeDate, this.username, this.local, this.level, this.hashedPassword, this.oldHashedPasswords, this.lastPasswordChangeDate);
	}

	/**
	 * Gets a copy of the user with another level.
	 *
	 * @param newLevel the level
	 * @return the user
	 */
	public UiUser withLevel(int newLevel) {
		return new UiUser(this.id, this.version, this.changeDate, this.username, this.local, newLevel, this.hashedPassword, this.oldHashedPasswords, this.lastPasswordChangeDate);
	}

	/**
	 * Gets a copy of the user with new settings, keeping the password.
	 *
	 * @param newUsername the user name
	 * @param newLocal true for a local user
	 * @param newLevel the level
	 * @return the user
	 */
	public UiUser withSettings(String newUsername, boolean newLocal, int newLevel) {
		return new UiUser(this.id, this.version, this.changeDate, newUsername, newLocal, newLevel, this.hashedPassword, this.oldHashedPasswords, this.lastPasswordChangeDate);
	}

	/**
	 * Gets a copy of the user with another password hash (the history and change date are kept).
	 *
	 * @param newHashedPassword the hash
	 * @return the user
	 */
	public UiUser withHashedPassword(String newHashedPassword) {
		return new UiUser(this.id, this.version, this.changeDate, this.username, this.local, this.level, newHashedPassword, this.oldHashedPasswords, this.lastPasswordChangeDate);
	}

	/**
	 * Gets a copy of the user with another password change date.
	 *
	 * @param date the date
	 * @return the user
	 */
	public UiUser withLastPasswordChangeDate(Instant date) {
		return new UiUser(this.id, this.version, this.changeDate, this.username, this.local, this.level, this.hashedPassword, this.oldHashedPasswords, date);
	}

	/**
	 * Gets a copy of the user with a new password, changed now (the history is kept as is).
	 *
	 * @param password the new password
	 * @return the user
	 */
	public UiUser withPassword(String password) {
		return new UiUser(this.id, this.version, this.changeDate, this.username, this.local, this.level, UiUser.hash(password), this.oldHashedPasswords, Instant.now());
	}

	/**
	 * Gets a copy of the user with a new password which complies with the given policy:
	 * the current password goes to the history.
	 *
	 * @param password the new password
	 * @param policy the policy to check
	 * @return the user
	 * @throws PasswordPolicyException if the password doesn't comply with the policy
	 */
	public UiUser withPassword(String password, PasswordPolicy policy) throws PasswordPolicyException {
		for (Map.Entry<PasswordPolicy.CharMatch, Integer> e : policy.getMinCharMatchCounts().entrySet()) {
			if (e.getKey().countMatches(password) < e.getValue()) {
				throw new PasswordPolicyException(String.format(
					"The password doesn't match the defined policy: %s", e.getKey().getDescription()));
			}
		}
		int size = Math.min(policy.getMaxHistory(), this.oldHashedPasswords.size());
		for (int i = 0; i < size; i++) {
			try {
				Hash oldHash = Hash.fromHashString(this.oldHashedPasswords.get(i));
				if (oldHash.check(password)) {
					throw new PasswordPolicyException("Password was already used for this account");
				}
			}
			catch (PasswordPolicyException e) {
				throw e;
			}
			catch (Exception e) {
				log.error("Error while computing/checking user password hash vs old hash", e);
			}
		}
		List<String> history = new ArrayList<>(this.oldHashedPasswords);
		if (this.hashedPassword != null) {
			history.addFirst(this.hashedPassword);
		}
		while (history.size() > policy.getMaxHistory()) {
			history.removeLast();
		}
		return new UiUser(this.id, this.version, this.changeDate, this.username, this.local, this.level, UiUser.hash(password), history, Instant.now());
	}

	/**
	 * Check password against user hash, and against policy.
	 *
	 * @param password the password
	 * @param policy The policy to check
	 * @throws WrongPasswordException 
	 * @throws PasswordPolicyException 
	 */
	public void checkPassword(String password, PasswordPolicy policy)
		throws WrongPasswordException, PasswordPolicyException {
		try {
			Hash hash = Hash.fromHashString(this.hashedPassword);
			if (!hash.check(password)) {
				throw new WrongPasswordException("Wrong password");
			}
		}
		catch (InvalidClassException e) {
			log.error("Error while reading/checking password hash", e);
			throw new WrongPasswordException("Unable to check password");
		}
		if (policy == null) {
			return;
		}
		if (this.lastPasswordChangeDate == null) {
			return;
		}
		int days = policy.getMaxDuration();
		if (days == 0) {
			return;
		}
		if (this.lastPasswordChangeDate.isBefore(Instant.now().minus(days, ChronoUnit.DAYS))) {
			throw new PasswordPolicyException("Password has expired, it must be changed");
		}
	}

	/*(non-Javadoc)
	 * @see java.security.Principal#getName()
	 */
	@Override
	public String getName() {
		return username;
	}

	@Override
	public String toString() {
		return "User " + id + " (username '" + username + "', level " + level + (local ? "" : ", remote user") + ")";
	}

}
