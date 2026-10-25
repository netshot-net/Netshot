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
package net.netshot.netshot.platform.database;

import org.jooq.impl.AbstractConverter;

import net.netshot.netshot.platform.crypto.Sha2AesPasswordBasedEncryptor;

/**
 * jOOQ converter of the encrypted columns (secrets of the credential sets and Vault instances):
 * the database holds the value encrypted with the DB encryption password (the format
 * written by the former Hibernate converter, so the existing values are read).
 *
 * <p>The password is read on the first conversion, not when the generated table classes are
 * loaded (which may happen before the configuration is).</p>
 */
public class EncryptedStringConverter extends AbstractConverter<String, String> {

	private static final long serialVersionUID = 1L;

	public EncryptedStringConverter() {
		super(String.class, String.class);
	}

	/** Holder of the encryption password, read once, on first use (lazy, thread-safe class initialization). */
	private static final class Password {
		static final String VALUE = Database.getEncryptionPassword();
	}

	private static Sha2AesPasswordBasedEncryptor encryptor() {
		return new Sha2AesPasswordBasedEncryptor(Password.VALUE);
	}

	@Override
	public String from(String databaseObject) {
		return databaseObject == null ? null : encryptor().decrypt(databaseObject);
	}

	@Override
	public String to(String userObject) {
		return userObject == null ? null : encryptor().encrypt(userObject);
	}
}
