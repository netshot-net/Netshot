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
package net.netshot.netshot.platform.secret;

import java.util.Objects;

import net.netshot.netshot.platform.crypto.PasswordBasedEncryptor;

/**
 * A value held by Netshot. When it is read from an encrypted column, it keeps the encrypted text, and is only
 * decrypted when {@link #reveal() revealed}, at the moment it is used (connecting to a device, logging into Vault):
 * reading and listing objects doesn't decrypt their secrets. A new value is held in clear text until it is stored.
 * The clear text is never cached, and never written by {@link #toString()}.
 */
public final class LocalSecret implements Secret {

	/** The value in clear text (a new value, or one of a column which isn't encrypted), or null. */
	private final String clearText;

	/** The value as stored (encrypted), or null. */
	private final String cipherText;

	/** The encryptor of the stored value, or null. */
	private final PasswordBasedEncryptor encryptor;

	private LocalSecret(String clearText, String cipherText, PasswordBasedEncryptor encryptor) {
		this.clearText = clearText;
		this.cipherText = cipherText;
		this.encryptor = encryptor;
	}

	/**
	 * Gets a value from its clear text.
	 *
	 * @param clearText the value
	 * @return the local secret
	 */
	public static LocalSecret of(String clearText) {
		return new LocalSecret(Objects.requireNonNull(clearText), null, null);
	}

	/**
	 * Gets a value from its encrypted text, as stored, without decrypting it.
	 *
	 * @param cipherText the encrypted value
	 * @param encryptor the encryptor to decrypt it with, when it is revealed
	 * @return the local secret
	 */
	public static LocalSecret encrypted(String cipherText, PasswordBasedEncryptor encryptor) {
		return new LocalSecret(null, Objects.requireNonNull(cipherText), Objects.requireNonNull(encryptor));
	}

	/**
	 * Gets the value in clear text (decrypting it if it was read encrypted).
	 *
	 * @return the value
	 */
	public String reveal() {
		return this.clearText != null ? this.clearText : this.encryptor.decrypt(this.cipherText);
	}

	/**
	 * Gets the value as it is to be stored in an encrypted column: the encrypted text it was read with (it is
	 * not encrypted again), or the clear text encrypted.
	 *
	 * @param withEncryptor the encryptor to encrypt a new value with
	 * @return the encrypted value
	 */
	public String encrypt(PasswordBasedEncryptor withEncryptor) {
		return this.cipherText != null ? this.cipherText : withEncryptor.encrypt(this.clearText);
	}

	/**
	 * Compares the values in clear text (which decrypts them: for tests mostly).
	 */
	@Override
	public boolean equals(Object other) {
		return other instanceof LocalSecret o && (this == o || this.reveal().equals(o.reveal()));
	}

	@Override
	public int hashCode() {
		return this.reveal().hashCode();
	}

	@Override
	public String toString() {
		return "LocalSecret[***]";
	}
}
