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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.testing.UnitTest;

/**
 * Tests the iterated MD5 and SHA-2 hashes (the former password hashes, still read for the existing users,
 * and the API token hashes): the expected values are computed independently (Python hashlib: hash of the salt
 * and the UTF-8 password, then hashed again iterations - 1 times).
 */
@UnitTest
@DisplayName("Iterated MD5 and SHA-2 hashes")
class IteratedHashTest {

	private static final String PASSWORD = "Netshot101!";

	private static final byte[] SALT = { 1, 2, 3, 4, 5, 6, 7, 8 };

	@Nested
	@DisplayName("MD5")
	class Md5 {

		@Test
		@DisplayName("Computes and writes a single iteration (no iteration field)")
		void singleIteration() {
			Md5BasedHash hash = new Md5BasedHash();
			hash.setSalt(SALT.clone());
			hash.setIterations(1);
			hash.digest(PASSWORD);
			Assertions.assertEquals("$1$AQIDBAUGBwg$eVOc+t1KFueJ1z1AJKqOHw", hash.toHashString());
		}

		@Test
		@DisplayName("Reads and checks a hash without salt")
		void noSalt() throws InvalidClassException {
			Hash hash = Hash.fromHashString("$1$t=1000$BFmnna+hqDpa7pjs8Z9GAw");
			Assertions.assertInstanceOf(Md5BasedHash.class, hash);
			Assertions.assertNull(((Md5BasedHash) hash).getSalt());
			Assertions.assertEquals(1000, ((Md5BasedHash) hash).getIterations());
			Assertions.assertTrue(hash.check(PASSWORD));
			Assertions.assertFalse(hash.check("netshot101!"));
			Assertions.assertFalse(hash.check(null));
		}

		@Test
		@DisplayName("Reads a raw (Jasypt) hash: salt and hash in one Base64 value, 1000 iterations")
		void raw() throws InvalidClassException {
			Hash hash = Hash.fromHashString("AQIDBAUGBwh9m39ReDZa/qSSzAassekt");
			Assertions.assertInstanceOf(Md5BasedHash.class, hash);
			Assertions.assertArrayEquals(SALT, ((Md5BasedHash) hash).getSalt());
			Assertions.assertTrue(hash.check(PASSWORD));
			Assertions.assertFalse(hash.check("other"));
		}

		@Test
		@DisplayName("Generates a new 8-byte salt for each hash")
		void generatedSalt() throws InvalidClassException {
			Md5BasedHash first = new Md5BasedHash(PASSWORD);
			Md5BasedHash second = new Md5BasedHash(PASSWORD);
			Assertions.assertEquals(8, first.getSalt().length);
			Assertions.assertNotEquals(first.toHashString(), second.toHashString());
			Assertions.assertTrue(Hash.fromHashString(first.toHashString()).check(PASSWORD));
		}

		@Test
		@DisplayName("Refuses the malformed hash strings")
		void malformed() {
			Assertions.assertNull(assertDoesNotThrowInvalidClass(() -> Md5BasedHash.fromHashString(null)));
			Assertions.assertThrows(InvalidClassException.class, () -> Md5BasedHash.fromHashString("$5$abc"));
			// Base64 of a 15-byte hash
			Assertions.assertThrows(IllegalArgumentException.class, () -> Md5BasedHash.fromHashString("$1$AAAAAAAAAAAAAAAAAAAA"));
			Assertions.assertThrows(IllegalArgumentException.class, () -> Md5BasedHash.fromHashString("$1$t=0$BFmnna+hqDpa7pjs8Z9GAw"));
			Assertions.assertThrows(IllegalArgumentException.class, () -> Md5BasedHash.fromHashString("$1$t=16777216$BFmnna+hqDpa7pjs8Z9GAw"));
			// The pattern accepts '-', the Base64 decoder doesn't
			Assertions.assertThrows(IllegalArgumentException.class, () -> Md5BasedHash.fromHashString("$1$ab-c$BFmnna+hqDpa7pjs8Z9GAw"));
			Assertions.assertThrows(IllegalArgumentException.class, () -> Md5BasedHash.fromHashString("$1$BFmnna-hqDpa7pjs8Z9GAw"));
			Assertions.assertThrows(InvalidClassException.class, () -> Md5BasedHash.fromRawHashString("AAAA"));
			Assertions.assertNull(assertDoesNotThrowInvalidClass(() -> Md5BasedHash.fromRawHashString(null)));
		}
	}

	@Nested
	@DisplayName("SHA-2")
	class Sha2 {

		@Test
		@DisplayName("Computes and writes SHA-256 with a single iteration")
		void sha256SingleIteration() {
			Sha2BasedHash hash = new Sha2BasedHash();
			hash.setSalt(SALT.clone());
			hash.setIterations(1);
			hash.digest(PASSWORD);
			Assertions.assertEquals("$5$AQIDBAUGBwg$1v2pFKTzo6Lk3K6GCbS3KvmMGG301xJYGUxtI7tKzBo", hash.toHashString());
		}

		@Test
		@DisplayName("Hashes the UTF-8 bytes of the password")
		void utf8() throws InvalidClassException {
			Sha2BasedHash hash = new Sha2BasedHash();
			hash.setSalt(SALT.clone());
			hash.digest("M\u00f6tdep\u00e4sse\u20ac");
			Assertions.assertEquals("$5$t=1000$AQIDBAUGBwg$Bw+9/5i19m/hMQYMj9tRGopXjCoLI9+82Eek11+vvic", hash.toHashString());
		}

		@Test
		@DisplayName("Computes, writes, reads and checks SHA-512")
		void sha512() throws InvalidClassException {
			byte[] salt = new byte[16];
			for (int i = 0; i < salt.length; i++) {
				salt[i] = (byte) (16 + i);
			}
			String expected = "$6$t=5000$EBESExQVFhcYGRobHB0eHw$YMbqYxgvLfGtCFjsvzMoEmCO40vAdnfwtGSXq0s+fX7Lugxcg59lMJhW5ZdpfQy9zV5jAtxX5WgR1pGT5DrWIg";
			Sha2BasedHash hash = new Sha2BasedHash();
			hash.setAlgorithm("SHA-512");
			hash.setIterations(5000);
			hash.setSalt(salt);
			hash.digest(PASSWORD);
			Assertions.assertEquals(expected, hash.toHashString());

			Hash read = Hash.fromHashString(expected);
			Assertions.assertInstanceOf(Sha2BasedHash.class, read);
			Assertions.assertEquals("SHA-512", ((Sha2BasedHash) read).getAlgorithm());
			Assertions.assertEquals(5000, ((Sha2BasedHash) read).getIterations());
			Assertions.assertTrue(read.check(PASSWORD));
			Assertions.assertFalse(read.check("other"));
			Assertions.assertFalse(read.check(null));
		}

		@Test
		@DisplayName("Reads a raw SHA-512 hash (no salt, 1000 iterations)")
		void sha512Raw() throws InvalidClassException {
			Sha2BasedHash hash = Sha2BasedHash.fromRawHashString(
				"ZWdglSgQn3lGSr8Hm8M65R+4qn6h+H5E0W0KMKR8EjhdNr2J5FFI44aGkJrOEtMIgrA7A8w3cxACH/1CzxUQjQ==");
			Assertions.assertEquals("SHA-512", hash.getAlgorithm());
			Assertions.assertTrue(hash.check(PASSWORD));
		}

		@Test
		@DisplayName("Refuses the malformed hash strings")
		void malformed() {
			Assertions.assertNull(assertDoesNotThrowInvalidClass(() -> Sha2BasedHash.fromHashString(null)));
			Assertions.assertThrows(InvalidClassException.class, () -> Sha2BasedHash.fromHashString("$1$BFmnna+hqDpa7pjs8Z9GAw"));
			// A SHA-512 length under $5, a SHA-256 length under $6
			Assertions.assertThrows(IllegalArgumentException.class, () -> Sha2BasedHash.fromHashString(
				"$5$YMbqYxgvLfGtCFjsvzMoEmCO40vAdnfwtGSXq0s+fX7Lugxcg59lMJhW5ZdpfQy9zV5jAtxX5WgR1pGT5DrWIg"));
			Assertions.assertThrows(IllegalArgumentException.class, () -> Sha2BasedHash.fromHashString(
				"$6$1v2pFKTzo6Lk3K6GCbS3KvmMGG301xJYGUxtI7tKzBo"));
			Assertions.assertThrows(IllegalArgumentException.class, () -> Sha2BasedHash.fromHashString(
				"$5$t=0$1v2pFKTzo6Lk3K6GCbS3KvmMGG301xJYGUxtI7tKzBo"));
			Assertions.assertThrows(IllegalArgumentException.class, () -> Sha2BasedHash.fromHashString(
				"$5$a-b$1v2pFKTzo6Lk3K6GCbS3KvmMGG301xJYGUxtI7tKzBo"));
			Assertions.assertThrows(InvalidClassException.class, () -> Sha2BasedHash.fromRawHashString("AAAA"));
			Assertions.assertNull(assertDoesNotThrowInvalidClass(() -> Sha2BasedHash.fromRawHashString(null)));
		}
	}

	@Test
	@DisplayName("The hash of no password is no hash")
	void nullInput() {
		for (Hash hash : new Hash[] { new Md5BasedHash(), new Sha2BasedHash(), new Argon2idHash() }) {
			hash.digest((String) null);
			Assertions.assertNull(hash.toHashString(), hash.getClass().getSimpleName());
			Assertions.assertTrue(hash.check(null), hash.getClass().getSimpleName());
			Assertions.assertFalse(hash.check(""), hash.getClass().getSimpleName());
		}
	}

	@Test
	@DisplayName("Refuses a value in no known format")
	void unknownFormat() {
		// Too short for a raw MD5 hash
		Assertions.assertThrows(InvalidClassException.class, () -> Hash.fromHashString("AAAA"));
	}

	/**
	 * A parser call which may throw the checked InvalidClassException.
	 */
	@FunctionalInterface
	private interface Parser {
		Hash parse() throws InvalidClassException;
	}

	private static Hash assertDoesNotThrowInvalidClass(Parser parser) {
		try {
			return parser.parse();
		}
		catch (InvalidClassException e) {
			return Assertions.fail(e);
		}
	}
}
