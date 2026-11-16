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
package net.netshot.netshot.platform.database;

import org.jooq.impl.AbstractConverter;

import net.netshot.netshot.platform.crypto.PasswordBasedEncryptor;
import net.netshot.netshot.platform.crypto.Sha2AesPasswordBasedEncryptor;
import net.netshot.netshot.platform.secret.LocalSecret;

/**
 * jOOQ converter of the encrypted columns (secrets of the credential sets and Vault instances):
 * the database holds the value encrypted with the DB encryption password (the format
 * written by the former Hibernate converter, so the existing values are read).
 *
 * <p>A value is read without being decrypted: the {@link LocalSecret} decrypts it when it is revealed, at the
 * moment it is used. A value which was read is written back as it was read (it isn't encrypted again).</p>
 *
 * <p>The password is given by the database access when it starts ({@link #init(String)}), not read when the
 * generated table classes are loaded (which may happen before the configuration is).</p>
 */
public class LocalSecretConverter extends AbstractConverter<String, LocalSecret> {

	private static final long serialVersionUID = 1L;

	public LocalSecretConverter() {
		super(String.class, LocalSecret.class);
	}

	/** The encryptor (set by {@link #init(String)} when the database access starts). */
	private static volatile PasswordBasedEncryptor encryptor;

	/**
	 * Sets the password of the encryption, when the database access starts (the generated table classes build the
	 * converter themselves: it can't receive it otherwise).
	 *
	 * @param password the encryption password
	 */
	public static synchronized void init(String password) {
		encryptor = new Sha2AesPasswordBasedEncryptor(password);
	}

	private static PasswordBasedEncryptor encryptor() {
		PasswordBasedEncryptor current = encryptor;
		if (current == null) {
			throw new IllegalStateException("The encryption of the secrets is not initialized");
		}
		return current;
	}

	@Override
	public LocalSecret from(String databaseObject) {
		return databaseObject == null ? null : LocalSecret.encrypted(databaseObject, encryptor());
	}

	@Override
	public String to(LocalSecret userObject) {
		return userObject == null ? null : userObject.encrypt(encryptor());
	}
}
