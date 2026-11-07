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
package net.netshot.netshot.platform.crypto;

import java.io.InvalidClassException;


/**
 * Generic hash faciliy class, to compute and check digests.
 */
public abstract class Hash {

	/**
	 * Create specific hash object based on hash string.
	 * @param hashString hash string in Modular Crypt Format
	 * @return the hash object
	 * @throws InvalidClassException if the hash format was not recognized
	 */
	public static Hash fromHashString(String hashString) throws InvalidClassException {
		try {
			return Argon2idHash.fromHashString(hashString);
		}
		catch (InvalidClassException e) {
			// Try next option
		}
		try {
			return Md5BasedHash.fromHashString(hashString);
		}
		catch (InvalidClassException e) {
			// Try next option
		}
		try {
			return Sha2BasedHash.fromHashString(hashString);
		}
		catch (InvalidClassException e) {
			// Try next option
		}
		try {
			return Md5BasedHash.fromRawHashString(hashString);
		}
		catch (InvalidClassException e) {
			// Try next option
		}
		throw new InvalidClassException("Unknown hash string format");
	}

	/**
	 * Compute the hash of the passed string.
	 * @param input the data to hash
	 */
	public void digest(String input) {
		if (input == null) {
			this.digest(new String[] {});
		}
		this.digest(new String[] { input });
	}

	/**
	 * Compute the hash of the passed strings.
	 * @param inputs the data to hash
	 */
	public abstract void digest(String[] inputs);

	/**
	 * Produce the string representation of the hash in Modular Crypt Format.
	 * @return the string representation of the hash
	 */
	public abstract String toHashString();

	/**
	 * Check whether the passed input matches the current hash.
	 * @param input the data to digest
	 * @return true if the input validates the hash
	 */
	public abstract boolean check(String input);

}
