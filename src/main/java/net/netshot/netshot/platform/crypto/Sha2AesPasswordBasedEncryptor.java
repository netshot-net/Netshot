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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import lombok.extern.slf4j.Slf4j;

/**
 * SHA2/AES-based encryption/decryption util class.
 */
@Slf4j
public final class Sha2AesPasswordBasedEncryptor extends PasswordBasedEncryptor {

	private static final String DEFAULT_ALGORITHM = "PBEWithHmacSHA256AndAES_256";
	private static final Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;
	private static final Charset ENCRYPTED_CHARSET = StandardCharsets.US_ASCII;

	private static final int DEFAULT_SALT_SIZE = 8;
	private static final int DEFAULT_IV_SIZE = 16;
	private static final int DEFAULT_KEY_ITERATIONS = 1000;


	private int saltSize = DEFAULT_SALT_SIZE;
	private int ivSize = DEFAULT_IV_SIZE;
	private int keyIterations = DEFAULT_KEY_ITERATIONS;

	public Sha2AesPasswordBasedEncryptor(String password) {
		super(password);
	}

	private byte[] generateSalt() {
		SecureRandom secureRandom = new SecureRandom();
		byte[] salt = new byte[this.saltSize];
		secureRandom.nextBytes(salt);
		return salt;
	}

	private byte[] generateIv() {
		SecureRandom secureRandom = new SecureRandom();
		byte[] iv = new byte[this.ivSize];
		secureRandom.nextBytes(iv);
		return iv;
	}

	@Override
	public String encrypt(String input) {
		return this.encrypt(input, this.generateSalt(), this.generateIv());
	}

	public String encrypt(String input, byte[] salt, byte[] iv) {
		if (input == null) {
			return null;
		}

		try {
			final byte[] inputBytes = input.getBytes(MESSAGE_CHARSET);
			Cipher cipher = Cipher.getInstance(DEFAULT_ALGORITHM);
			SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(DEFAULT_ALGORITHM);

			PBEKeySpec keySpec = new PBEKeySpec(this.password.toCharArray());
			SecretKey key = keyFactory.generateSecret(keySpec);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			PBEParameterSpec paramSpec = new PBEParameterSpec(salt, this.keyIterations, ivSpec);

			cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
			byte[] encrypted = cipher.doFinal(inputBytes);
			byte[] saltedEncrypted = new byte[salt.length + iv.length + encrypted.length];
			System.arraycopy(salt, 0, saltedEncrypted, 0, salt.length);
			System.arraycopy(iv, 0, saltedEncrypted, salt.length, iv.length);
			System.arraycopy(encrypted, 0, saltedEncrypted, salt.length + iv.length, encrypted.length);
			return new String(Base64.getEncoder().encode(saltedEncrypted), ENCRYPTED_CHARSET);
		}
		catch (Exception e) {
			log.error("Unable to encrypt data with given arguments", e);
			throw new IllegalArgumentException("Unable to encrypt data with given arguments", e);
		}
	}

	public String decrypt(byte[] inputBytes, byte[] salt, byte[] iv) {

		try {
			Cipher cipher = Cipher.getInstance(DEFAULT_ALGORITHM);
			SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(DEFAULT_ALGORITHM);

			PBEKeySpec keySpec = new PBEKeySpec(this.password.toCharArray());
			SecretKey key = keyFactory.generateSecret(keySpec);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			PBEParameterSpec paramSpec = new PBEParameterSpec(salt, this.keyIterations, ivSpec);

			cipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
			byte[] decrypted = cipher.doFinal(inputBytes);
			return new String(decrypted, MESSAGE_CHARSET);
		}
		catch (Exception e) {
			log.error("Unable to decrypt data with given arguments", e);
			throw new IllegalArgumentException("Unable to decrypt data with given arguments", e);
		}
	}

	@Override
	public String decrypt(String input) {
		if (input == null) {
			return null;
		}
		byte[] inputBytes = Base64.getDecoder().decode(input.getBytes(ENCRYPTED_CHARSET));
		byte[] salt = new byte[this.saltSize];
		byte[] iv = new byte[this.ivSize];
		System.arraycopy(inputBytes, 0, salt, 0, this.saltSize);
		System.arraycopy(inputBytes, this.saltSize, iv, 0, this.ivSize);
		inputBytes = Arrays.copyOfRange(inputBytes, this.saltSize + this.ivSize, inputBytes.length);
		return this.decrypt(inputBytes, salt, iv);
	}

}
