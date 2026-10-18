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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@DisplayName("Encryption and hashing basic tests")
public class CryptoTest {

	@Test
	@DisplayName("MD5/DES-based decryption test")
	public void md5DesDecryptTest() {
		String plain = "admin";
		String encrypted = "ACdTtNiLgWBoGs3qq+mbNA==";
		PasswordBasedEncryptor encryptor = new Md5DesPasswordBasedEncryptor("netshot");
		String decrypted = encryptor.decrypt(encrypted);
		Assertions.assertEquals(plain, decrypted, "The decrypted message is not correct");
	}

	@Test
	@DisplayName("MD5/DES-based encryption test")
	public void md5DesEncryptTest() {
		String plain = "admin";
		String expected = "ACdTtNiLgWBoGs3qq+mbNA==";
		byte[] salt = new byte[] { 0x00, 0x27, 0x53, (byte) 0xb4, (byte) 0xd8, (byte) 0x8b, (byte) 0x81, 0x60 };
		Md5DesPasswordBasedEncryptor encryptor = new Md5DesPasswordBasedEncryptor("netshot");
		String encrypted = encryptor.encrypt(plain, salt);
		Assertions.assertEquals(expected, encrypted, "The encrypted message is not correct");
	}

	@Test
	@DisplayName("MD5/DES-based encryption then decryption test")
	public void md5DesEncryptDecryptTest() {
		String plain = "somesecretdata";
		PasswordBasedEncryptor encryptor = new Md5DesPasswordBasedEncryptor("netshot");
		String encrypted = encryptor.encrypt(plain);
		String decrypted = encryptor.decrypt(encrypted);
		Assertions.assertEquals(plain, decrypted, "The final message is not equal to initial one");
	}

	@Test
	@DisplayName("SHA2/AES-based decryption test")
	public void sha2AesDecryptTest() {
		String plain = "admin";
		String encrypted = "AAkFZdFiJCIIVngREmchB39WJQgeZnh7FvpkmYjpeVEWusV3R9V2iA==";
		PasswordBasedEncryptor encryptor = new Sha2AesPasswordBasedEncryptor("netshot");
		String decrypted = encryptor.decrypt(encrypted);
		Assertions.assertEquals(plain, decrypted, "The decrypted message is not correct");
	}

	@Test
	@DisplayName("SHA2/AES-based encryption test")
	public void sha2AesEncryptTest() {
		String plain = "admin";
		String expected = "AAkFZdFiJCIIVngREmchB39WJQgeZnh7FvpkmYjpeVEWusV3R9V2iA==";
		byte[] salt = new byte[] { 0x00, 0x09, 0x05, 0x65, (byte) 0xd1, 0x62, 0x24, 0x22 };
		byte[] iv = new byte[] {
			0x08, 0x56, 0x78, 0x11, 0x12, 0x67, 0x21, 0x07,
			0x7f, 0x56, 0x25, 0x08, 0x1e, 0x66, 0x78, 0x7b
		};
		Sha2AesPasswordBasedEncryptor encryptor = new Sha2AesPasswordBasedEncryptor("netshot");
		String encrypted = encryptor.encrypt(plain, salt, iv);
		Assertions.assertEquals(expected, encrypted, "The encrypted message is not correct");
	}

	@Test
	@DisplayName("SHA2/AES-based encryption then decryption test")
	public void sha2AesEncryptDecryptTest() {
		String plain = "somesecretdata";
		PasswordBasedEncryptor encryptor = new Sha2AesPasswordBasedEncryptor("netshot");
		String encrypted = encryptor.encrypt(plain);
		String decrypted = encryptor.decrypt(encrypted);
		Assertions.assertEquals(plain, decrypted, "The final message is not equal to initial one");
	}

	@Test
	@DisplayName("MD5-based hash check test")
	public void md5HashCheckTest() throws InvalidClassException {
		String sensitive = "netshot";
		String hashString = "7htrot2BNjUV/g57h/HJ/C1N0Fqrj+QQ";
		Hash hash = Hash.fromHashString(hashString);
		Assertions.assertTrue(hash.check(sensitive), "The hash was not properly validated");
	}

	@Test
	@DisplayName("MD5-based hash compute test")
	public void md5HashComputeTest() throws InvalidClassException {
		String sensitive = "netshot";
		Md5BasedHash hash = new Md5BasedHash();
		hash.setSalt(new byte[] {
			(byte) 0xee, 0x1b, 0x6b, (byte) 0xa2, (byte) 0xdd, (byte) 0x81, 0x36, 0x35
		});
		hash.digest(sensitive);
		Assertions.assertEquals(
			"$1$t=1000$7htrot2BNjU$Ff4Oe4fxyfwtTdBaq4/kEA",
			hash.toHashString(),
			"The hash was not properly computed");
		Assertions.assertTrue(hash.check(sensitive), "The hash was not properly validated");
	}

	@Test
	@DisplayName("SHA256-based (no salt) raw hash check test")
	public void sha256NoSaltRawHashCheckTest() throws InvalidClassException {
		String sensitive = "HzpCNm3PTgp7mxGNKZqNFrRUBjD6wbaR";
		String hashString = "ehn2Pt13CGKsMESmWGkCGA+5p7W9WC6cgFxoW8BGKXw=";
		Hash hash = Sha2BasedHash.fromRawHashString(hashString);
		Assertions.assertTrue(hash.check(sensitive), "The hash was not properly validated");
	}

	@Test
	@DisplayName("SHA256-based (no salt) hash check test")
	public void sha256NoSaltHashCheckTest() throws InvalidClassException {
		String sensitive = "HzpCNm3PTgp7mxGNKZqNFrRUBjD6wbaR";
		String hashString = "$5$t=1000$ehn2Pt13CGKsMESmWGkCGA+5p7W9WC6cgFxoW8BGKXw";
		Hash hash = Hash.fromHashString(hashString);
		Assertions.assertTrue(hash.check(sensitive), "The hash was not properly validated");
	}

	@Test
	@DisplayName("SHA256-based hash compute test")
	public void sha256HashComputeTest() throws InvalidClassException {
		String sensitive = "HzpCNm3PTgp7mxGNKZqNFrRUBjD6wbaR";
		Sha2BasedHash hash = new Sha2BasedHash();
		hash.setSalt(null);
		hash.digest(sensitive);
		Assertions.assertEquals(
			"$5$t=1000$ehn2Pt13CGKsMESmWGkCGA+5p7W9WC6cgFxoW8BGKXw",
			hash.toHashString(),
			"The hash was not properly computed");
		Assertions.assertTrue(hash.check(sensitive), "The hash was not properly validated");
	}

	@Test
	@DisplayName("Argon2id valid check test")
	public void argon2idValidCheckTest() throws InvalidClassException {
		String sensitive = "netshot1";
		String hashString = "$argon2id$v=19$m=9216,t=4,p=1$VUFuaDdSZ3FTd3FHQ1J5MQ$DZio5sjMQZygXu9iIaN66w";
		Hash hash = Hash.fromHashString(hashString);
		Assertions.assertTrue(hash.check(sensitive), "The hash was not properly validated");
	}

	@Test
	@DisplayName("Argon2id invalid check test")
	public void argon2idInvalidCheckTest() throws InvalidClassException {
		String sensitive = "netshot1";
		String hashString = "$argon2id$v=19$m=9216,t=4,p=1$VUFuaDdSZ3FTd3FHQ1J5MQ$DZio5sjMQZygXu9iIAN66w";
		Hash hash = Hash.fromHashString(hashString);
		Assertions.assertFalse(hash.check(sensitive), "The hash was validated while it shouldn't");
	}

	@Test
	@DisplayName("Argon2id compute and check test")
	public void argon2idComputeCheckTest() {
		String sensitive = "Netshot101$";
		Hash hash = new Argon2idHash(sensitive);
		Assertions.assertTrue(hash.check(sensitive), "The hash was not properly computed or validated");
	}

	@Test
	@DisplayName("Argon2id compute test")
	public void argon2idComputeTest() {
		String sensitive = "netshot";
		Argon2idHash hash = new Argon2idHash();
		hash.setSalt(new byte[] {
			0x48, 0x43, 0x6f, 0x4c, 0x41, 0x5a, 0x4d, 0x4f,
			0x4b, 0x73, 0x35, 0x76, 0x6f, 0x77, 0x53, 0x48
		});
		hash.digest(sensitive);
		Assertions.assertEquals(
			"$argon2id$v=19$m=9216,t=4,p=2$SENvTEFaTU9LczV2b3dTSA$UIzpqtaVWULgdrGGJlN1EQ",
			hash.toHashString(),
			"The hash was not properly computed");
	}

	@Test
	@DisplayName("PasswordGenerator default generation test")
	public void passwordGeneratorDefaultTest() {
		String password = PasswordGenerator.generateDefault();
		Assertions.assertNotNull(password, "Password should not be null");
		Assertions.assertEquals(16, password.length(), "Default password length should be 16");
		Assertions.assertTrue(containsUppercase(password), "Password should contain uppercase letters");
		Assertions.assertTrue(containsLowercase(password), "Password should contain lowercase letters");
		Assertions.assertTrue(containsDigit(password), "Password should contain digits");
	}

	@Test
	@DisplayName("PasswordGenerator custom length test")
	public void passwordGeneratorCustomLengthTest() {
		int expectedLength = 24;
		String password = PasswordGenerator.builder()
			.length(expectedLength)
			.build()
			.generate();
		Assertions.assertEquals(expectedLength, password.length(), "Password length should match requested length");
	}

	@Test
	@DisplayName("PasswordGenerator alphanumeric only test")
	public void passwordGeneratorAlphanumericTest() {
		String password = PasswordGenerator.generateAlphanumeric(20);
		Assertions.assertEquals(20, password.length(), "Password length should be 20");
		Pattern alphanumeric = Pattern.compile("^[A-Za-z0-9]+$");
		Assertions.assertTrue(alphanumeric.matcher(password).matches(),
			"Password should contain only alphanumeric characters");
	}

	@Test
	@DisplayName("PasswordGenerator with special characters test")
	public void passwordGeneratorWithSpecialCharsTest() {
		String password = PasswordGenerator.builder()
			.length(20)
			.includeSpecialChars(true)
			.build()
			.generate();
		Assertions.assertEquals(20, password.length(), "Password length should be 20");
		// Generate multiple passwords to increase chance of getting special chars
		boolean hasSpecial = false;
		for (int i = 0; i < 10; i++) {
			String pwd = PasswordGenerator.builder()
				.length(20)
				.includeSpecialChars(true)
				.build()
				.generate();
			if (containsSpecialChar(pwd)) {
				hasSpecial = true;
				break;
			}
		}
		Assertions.assertTrue(hasSpecial, "At least one password should contain special characters");
	}

	@Test
	@DisplayName("PasswordGenerator exclude ambiguous characters test")
	public void passwordGeneratorExcludeAmbiguousTest() {
		String password = PasswordGenerator.builder()
			.length(50)
			.excludeAmbiguous(true)
			.build()
			.generate();
		Assertions.assertFalse(password.contains("0"), "Password should not contain '0'");
		Assertions.assertFalse(password.contains("O"), "Password should not contain 'O'");
		Assertions.assertFalse(password.contains("1"), "Password should not contain '1'");
		Assertions.assertFalse(password.contains("l"), "Password should not contain 'l'");
	}

	@Test
	@DisplayName("PasswordGenerator custom character set test")
	public void passwordGeneratorCustomCharSetTest() {
		String customChars = "ABC123";
		String password = PasswordGenerator.builder()
			.length(20)
			.customCharSet(customChars)
			.build()
			.generate();
		Assertions.assertEquals(20, password.length(), "Password length should be 20");
		for (char c : password.toCharArray()) {
			Assertions.assertTrue(customChars.indexOf(c) >= 0,
				"Password should only contain characters from custom set: " + c);
		}
	}

	@Test
	@DisplayName("PasswordGenerator uniqueness test")
	public void passwordGeneratorUniquenessTest() {
		Set<String> passwords = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			passwords.add(PasswordGenerator.generateDefault());
		}
		Assertions.assertEquals(100, passwords.size(), "All generated passwords should be unique");
	}

	@Test
	@DisplayName("PasswordGenerator reusable instance test")
	public void passwordGeneratorReusableInstanceTest() {
		PasswordGenerator generator = PasswordGenerator.builder()
			.length(12)
			.includeUppercase(true)
			.includeLowercase(true)
			.includeDigits(true)
			.includeSpecialChars(false)
			.build();

		String pwd1 = generator.generate();
		String pwd2 = generator.generate();

		Assertions.assertEquals(12, pwd1.length(), "First password should have length 12");
		Assertions.assertEquals(12, pwd2.length(), "Second password should have length 12");
		Assertions.assertNotEquals(pwd1, pwd2, "Two passwords from same generator should be different");
	}

	@Test
	@DisplayName("PasswordGenerator minimum length validation test")
	public void passwordGeneratorMinimumLengthTest() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			PasswordGenerator.builder().length(0).build();
		}, "Should throw exception for length 0");

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			PasswordGenerator.builder().length(-1).build();
		}, "Should throw exception for negative length");
	}

	@Test
	@DisplayName("PasswordGenerator no character sets enabled test")
	public void passwordGeneratorNoCharSetsTest() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			PasswordGenerator.builder()
				.includeUppercase(false)
				.includeLowercase(false)
				.includeDigits(false)
				.includeSpecialChars(false)
				.build()
				.generate();
		}, "Should throw exception when no character sets are enabled");
	}

	// Helper methods for password validation
	private boolean containsUppercase(String str) {
		return str.chars().anyMatch(Character::isUpperCase);
	}

	private boolean containsLowercase(String str) {
		return str.chars().anyMatch(Character::isLowerCase);
	}

	private boolean containsDigit(String str) {
		return str.chars().anyMatch(Character::isDigit);
	}

	private boolean containsSpecialChar(String str) {
		String specialChars = "!@#$%^&*()-_=+[]{}|;:,.<>?";
		return str.chars().anyMatch(c -> specialChars.indexOf(c) >= 0);
	}
}
