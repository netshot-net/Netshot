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
package net.netshot.netshot.platform.crypto;

import java.io.InvalidClassException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;

/**
 * SHA2-based hash.
 * By default, SHA256, 1000 iterations and 8-byte salt.
 */
public final class Sha2BasedHash extends Hash {

	private static final Charset HASH_CHARSET = StandardCharsets.US_ASCII;
	private static final Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;


	private static final int TYPE5_TYPE = 5;
	private static final String TYPE5_ALGORITHM = "SHA-256";
	private static final int TYPE5_HASH_LENGTH = 32;

	private static final int TYPE6_TYPE = 6;
	private static final String TYPE6_ALGORITHM = "SHA-512";
	private static final int TYPE6_HASH_LENGTH = 64;

	private static final int DEFAULT_SALT_SIZE = 8;
	private static final int DEFAULT_ITERATIONS = 1000;
	private static final String DEFAULT_ALGORITHM = TYPE5_ALGORITHM;

	private static final int MIN_ITERATIONS = 1;
	private static final int MAX_ITERATIONS = 1 << 24;

	private static final Pattern HASHSTRING_PATTERN = Pattern.compile(
		"^\\$(?<version>5|6)(\\$t=(?<iterations>[0-9]+))?(\\$(?<salt>[-A-Za-z0-9+/]+))?\\$(?<hash>[-A-Za-z0-9+/]+)$");

	public static Sha2BasedHash fromHashString(String hashString) throws InvalidClassException {
		if (hashString == null) {
			return null;
		}
		final Matcher matcher = HASHSTRING_PATTERN.matcher(hashString);
		if (!matcher.matches()) {
			throw new InvalidClassException("Cannot parse SHA2 hash string");
		}
		Sha2BasedHash sha2Hash = new Sha2BasedHash();
		final String saltString = matcher.group("salt");
		if (saltString == null) {
			sha2Hash.setSalt(null);
		}
		else {
			try {
				sha2Hash.setSalt(Base64.getDecoder().decode(saltString.getBytes(HASH_CHARSET)));
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Salt component cannot be decoded", e);
			}
		}
		try {
			sha2Hash.hash = Base64.getDecoder().decode(matcher.group("hash").getBytes(HASH_CHARSET));
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Hash component cannot be decoded", e);
		}
		int version = Integer.parseInt(matcher.group("version"));
		if (version == TYPE5_TYPE) {
			sha2Hash.algorithm = TYPE5_ALGORITHM;
			if (sha2Hash.hash.length != TYPE5_HASH_LENGTH) {
				throw new IllegalArgumentException("Invalid length for hash component");
			}
		}
		else if (version == TYPE6_TYPE) {
			sha2Hash.algorithm = TYPE6_ALGORITHM;
			if (sha2Hash.hash.length != TYPE6_HASH_LENGTH) {
				throw new IllegalArgumentException("Invalid length for hash component");
			}
		}
		else {
			throw new IllegalArgumentException("Unknown SHA2 version/algorithm");
		}
		final String iterationString = matcher.group("iterations");
		if (iterationString != null) {
			sha2Hash.iterations = Integer.parseInt(iterationString);
			if (sha2Hash.iterations < MIN_ITERATIONS || sha2Hash.iterations > MAX_ITERATIONS) {
				throw new IllegalArgumentException("Invalid iteration component value");
			}
		}
		return sha2Hash;
	}

	public static Sha2BasedHash fromRawHashString(String hashString) throws InvalidClassException {
		if (hashString == null) {
			return null;
		}
		Sha2BasedHash sha2Hash = new Sha2BasedHash();
		sha2Hash.setSalt(null);
		sha2Hash.hash = Base64.getDecoder().decode(hashString.getBytes(HASH_CHARSET));
		if (sha2Hash.hash.length == TYPE5_HASH_LENGTH) {
			sha2Hash.algorithm = TYPE5_ALGORITHM;
		}
		else if (sha2Hash.hash.length == TYPE6_HASH_LENGTH) {
			sha2Hash.algorithm = TYPE6_ALGORITHM;
		}
		else {
			throw new InvalidClassException("Invalid hash size for SHA2");
		}
		return sha2Hash;
	}

	private int saltSize = DEFAULT_SALT_SIZE;

	@Getter
	private byte[] salt;

	@Getter
	private byte[] hash;

	@Getter
	@Setter
	private int iterations = DEFAULT_ITERATIONS;

	@Getter
	@Setter
	private String algorithm = DEFAULT_ALGORITHM;

	public Sha2BasedHash() {

	}

	public Sha2BasedHash(String input) {
		this.digest(input);
	}

	private void generateSalt() {
		if (this.saltSize == 0) {
			this.salt = null;
			return;
		}
		SecureRandom secureRandom = new SecureRandom();
		this.salt = new byte[this.saltSize];
		secureRandom.nextBytes(this.salt);
	}

	@Override
	public void digest(String[] inputs) {
		if (inputs == null || inputs.length == 0) {
			this.hash = null;
			return;
		}
		if (this.salt == null) {
			this.generateSalt();
		}

		MessageDigest md;
		try {
			md = MessageDigest.getInstance(this.algorithm);
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Lack of SHA2 support in the JVM", e);
		}
		md.reset();
		if (this.salt != null) {
			md.update(this.salt);
		}
		for (String input : inputs) {
			if (input == null) {
				continue;
			}
			byte[] inputBytes = input.getBytes(MESSAGE_CHARSET);
			md.update(inputBytes);
		}
		byte[] digest = md.digest();
		for (int i = 1; i < this.iterations; i++) {
			md.reset();
			digest = md.digest(digest);
		}
		this.hash = digest;
	}

	@Override
	public String toHashString() {
		if (this.hash == null) {
			return null;
		}
		int version = TYPE5_TYPE;
		if (TYPE6_ALGORITHM.equals(this.algorithm)) {
			version = TYPE6_TYPE;
		}
		StringBuffer sb = new StringBuffer()
			.append("$").append(version);
		if (this.iterations != 1) {
			sb.append("$").append(String.format("t=%d", this.iterations));
		}
		if (this.salt != null) {
			sb.append("$").append(
				new String(Base64.getEncoder().withoutPadding().encode(this.salt),
					HASH_CHARSET));
		}
		sb.append("$").append(
			new String(Base64.getEncoder().withoutPadding().encode(this.hash),
				HASH_CHARSET));
		return sb.toString();
	}

	@Override
	public boolean check(String input) {
		if (input == null) {
			return this.hash == null;
		}
		Sha2BasedHash other = new Sha2BasedHash();
		other.salt = this.salt == null ? null : Arrays.copyOf(this.salt, this.salt.length);
		other.saltSize = this.saltSize;
		other.iterations = this.iterations;
		other.algorithm = this.algorithm;
		other.digest(input);
		return Arrays.equals(other.hash, this.hash);
	}

	public void setSalt(byte[] salt) {
		this.salt = salt;
		this.saltSize = salt == null ? 0 : salt.length;
	}

}
