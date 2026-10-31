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

import java.security.SecureRandom;
import java.util.regex.Pattern;

import net.netshot.netshot.platform.crypto.Sha2BasedHash;

/**
 * An API token: the value itself is not stored, only its hash.
 *
 * @param id the ID (0 for a token not stored yet)
 * @param description the description
 * @param hashedToken the hash of the token value
 * @param level the authorization level
 */
public record ApiToken(long id, String description, String hashedToken, int level) implements User {

	public static String hashToken(String token) {
		Sha2BasedHash hash = new Sha2BasedHash();
		hash.setSalt(null); // No salt
		hash.setIterations(1000);
		hash.digest(token);
		return hash.toHashString();
	}

	protected static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9]{32}$");

	/** The characters of a generated token. */
	private static final String TOKEN_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

	private static final SecureRandom RANDOM = new SecureRandom();

	/**
	 * Generates a random token value (32 alphanumeric characters, from a cryptographically strong generator).
	 *
	 * @return the clear token value
	 */
	public static String generateToken() {
		StringBuilder token = new StringBuilder(32);
		for (int i = 0; i < 32; i++) {
			token.append(TOKEN_CHARACTERS.charAt(RANDOM.nextInt(TOKEN_CHARACTERS.length())));
		}
		return token.toString();
	}

	/**
	 * Checks whether the given text is a valid token.
	 * @param token the token
	 * @return true if the given token is of valid format
	 */
	public static boolean isValidToken(String token) {
		return token != null && TOKEN_PATTERN.matcher(token).matches();
	}

	/**
	 * Builds an API token from its clear value, not stored yet.
	 *
	 * @param description the description
	 * @param token the clear token value
	 * @param level the authorization level
	 * @return the API token
	 */
	public static ApiToken of(String description, String token, int level) {
		return new ApiToken(0, description, hashToken(token), level);
	}

	/**
	 * Gets a copy of the API token with another ID.
	 *
	 * @param newId the ID
	 * @return the API token
	 */
	public ApiToken withId(long newId) {
		return new ApiToken(newId, this.description, this.hashedToken, this.level);
	}

	/*(non-Javadoc)
	 * @see java.security.Principal#getName()
	 */
	@Override
	public String getName() {
		return String.format("API Token [%d: %s]", this.id, this.description);
	}

	@Override
	public String username() {
		return getName();
	}

	@Override
	public String toString() {
		return "ApiToken [description=" + description + ", id=" + id + ", level=" + level + "]";
	}

}
