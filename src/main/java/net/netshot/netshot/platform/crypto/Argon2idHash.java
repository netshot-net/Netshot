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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Argon2id-based hash.
 */
public final class Argon2idHash extends Hash {

	private static final Charset HASH_CHARSET = StandardCharsets.US_ASCII;
	private static final Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;

	private static final int DEFAULT_SALT_SIZE = 8;
	private static final int DEFAULT_HASH_SIZE = 16;
	private static final int DEFAULT_ITERATIONS = 4;
	private static final int DEFAULT_PARALLELISM = 2;
	private static final int DEFAULT_MEMORY = 9 * 1024;

	private static final int MIN_ITERATIONS = 1;
	private static final int MAX_ITERATIONS = 100000;
	private static final int MIN_PARALLELISM = 1;
	private static final int MAX_PARALLELISM = 2 ^ 24 - 1;
	private static final int MIN_MEMORY_PARALLELISM_FACTOR = 8;

	private static final Pattern HASHSTRING_PATTERN = Pattern.compile(
		"^\\$argon2id\\$v=(?<version>[0-9]+)\\$(?<params>[a-z=0-9,]+)(\\$(?<salt>[-A-Za-z0-9+/]+))?\\$(?<hash>[-A-Za-z0-9+/]+)$");
	private static final Pattern HASHSTRING_PARAM_PATTERN = Pattern.compile(
		"^(m=(?<memory>[0-9]+)|t=(?<iterations>[0-9+])|p=(?<parallelism>[0-9]+))$");

	public static Argon2idHash fromHashString(String hashString) throws InvalidClassException {
		if (hashString == null) {
			return null;
		}
		final Matcher matcher = HASHSTRING_PATTERN.matcher(hashString);
		if (!matcher.matches()) {
			throw new InvalidClassException("Cannot parse Argon2id hash string");
		}
		Argon2idHash a2Hash = new Argon2idHash();
		final int version = Integer.parseInt(matcher.group("version"));
		if (version != Argon2Parameters.ARGON2_VERSION_13) {
			throw new IllegalArgumentException(String.format("Unsupported Argon2 version %d", version));
		}
		final String saltString = matcher.group("salt");
		if (saltString == null) {
			a2Hash.setSalt(null);
		}
		else {
			try {
				a2Hash.setSalt(Base64.getDecoder().decode(saltString.getBytes(HASH_CHARSET)));
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Salt component cannot be decoded", e);
			}
		}
		try {
			a2Hash.hash = Base64.getDecoder().decode(matcher.group("hash"));
			a2Hash.hashSize = a2Hash.hash.length;
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Hash component cannot be decoded", e);
		}
		a2Hash.iterations = -1;
		a2Hash.memory = -1;
		a2Hash.parallelism = -1;
		final String params = matcher.group("params");
		for (String param : params.split(",")) {
			Matcher argon2ParamMatcher = HASHSTRING_PARAM_PATTERN.matcher(param);
			if (!argon2ParamMatcher.matches()) {
				throw new IllegalArgumentException(String.format("Invalid Argon2id param %s", param));
			}
			if (argon2ParamMatcher.group("memory") != null) {
				a2Hash.memory = Integer.parseInt(argon2ParamMatcher.group("memory"));
			}
			else if (argon2ParamMatcher.group("iterations") != null) {
				a2Hash.iterations = Integer.parseInt(argon2ParamMatcher.group("iterations"));
			}
			else if (argon2ParamMatcher.group("parallelism") != null) {
				a2Hash.parallelism = Integer.parseInt(argon2ParamMatcher.group("parallelism"));
			}
		}
		if (a2Hash.iterations < MIN_ITERATIONS || a2Hash.iterations > MAX_ITERATIONS) {
			throw new IllegalArgumentException("Invalid or missing Argon2id iterations param");
		}
		if (a2Hash.parallelism < MIN_PARALLELISM || a2Hash.parallelism > MAX_PARALLELISM) {
			throw new IllegalArgumentException("Invalid or missing parallelism param");
		}
		if (a2Hash.memory < MIN_MEMORY_PARALLELISM_FACTOR * a2Hash.parallelism || a2Hash.memory > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Invalid or missing memory param");
		}
		return a2Hash;
	}

	private int saltSize = DEFAULT_SALT_SIZE;

	@Getter
	private byte[] salt;

	private int hashSize = DEFAULT_HASH_SIZE;

	@Getter
	private byte[] hash;

	@Getter
	@Setter
	private int memory = DEFAULT_MEMORY;

	@Getter
	@Setter
	private int iterations = DEFAULT_ITERATIONS;

	@Getter
	@Setter
	private int parallelism = DEFAULT_PARALLELISM;

	public Argon2idHash() {

	}

	public Argon2idHash(String input) {
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

		Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
			.withVersion(Argon2Parameters.ARGON2_VERSION_13)
			.withIterations(this.iterations)
			.withMemoryAsKB(this.memory)
			.withParallelism(this.parallelism)
			.withSalt(this.salt)
			.build();

		Argon2BytesGenerator generator = new Argon2BytesGenerator();
		generator.init(parameters);
		StringBuffer sb = new StringBuffer();
		for (String input : inputs) {
			if (input != null) {
				sb.append(input);
			}
		}
		byte[] inputBytes = sb.toString().getBytes(MESSAGE_CHARSET);
		this.hash = new byte[this.hashSize];
		generator.generateBytes(inputBytes, this.hash);
	}

	@Override
	public String toHashString() {
		if (this.hash == null) {
			return null;
		}
		String encodedSalt = new String(Base64.getEncoder().withoutPadding().encode(this.salt), HASH_CHARSET);
		String encodedHash = new String(Base64.getEncoder().withoutPadding().encode(this.hash), HASH_CHARSET);
		return new StringBuffer()
			.append("$").append("argon2id")
			.append("$").append(String.format("v=%d", Argon2Parameters.ARGON2_VERSION_13))
			.append("$").append(String.format("m=%d,t=%d,p=%d", memory, iterations, parallelism))
			.append("$").append(encodedSalt)
			.append("$").append(encodedHash)
			.toString();
	}

	@Override
	public boolean check(String input) {
		if (input == null) {
			return this.hash == null;
		}
		Argon2idHash other = new Argon2idHash();
		other.hashSize = this.hashSize;
		other.salt = this.salt == null ? null : Arrays.copyOf(this.salt, this.salt.length);
		other.saltSize = this.saltSize;
		other.memory = this.memory;
		other.iterations = this.iterations;
		other.parallelism = this.parallelism;
		other.digest(input);
		return Arrays.equals(other.hash, this.hash);
	}

	public void setSalt(byte[] salt) {
		this.salt = salt;
		this.saltSize = salt == null ? 0 : salt.length;
	}

}
