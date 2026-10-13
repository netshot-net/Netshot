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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Secure password generator.
 */
public final class PasswordGenerator {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	// Character sets for password generation
	private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
	private static final String DIGITS = "0123456789";
	private static final String SPECIAL = "!@#$%^&*()-_=+[]{}|;:,.<>?";

	// Configuration fields
	private final int length;
	private final boolean includeUppercase;
	private final boolean includeLowercase;
	private final boolean includeDigits;
	private final boolean includeSpecialChars;
	private final boolean excludeAmbiguous;
	private final String customCharSet;

	/**
	 * Private constructor - use builder() to create instances.
	 *
	 * @param builder the builder
	 */
	private PasswordGenerator(Builder builder) {
		this.length = builder.length;
		this.includeUppercase = builder.includeUppercase;
		this.includeLowercase = builder.includeLowercase;
		this.includeDigits = builder.includeDigits;
		this.includeSpecialChars = builder.includeSpecialChars;
		this.excludeAmbiguous = builder.excludeAmbiguous;
		this.customCharSet = builder.customCharSet;
	}

	/**
	 * Create a new builder instance.
	 *
	 * @return A new Builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Generate a password with default settings (16 chars, all character types).
	 *
	 * @return a randomly generated password
	 */
	public static String generateDefault() {
		return builder().build().generate();
	}

	/**
	 * Generate a secure alphanumeric-only password (no special characters).
	 *
	 * @param passwordLength the desired password length
	 * @return a randomly generated alphanumeric password
	 */
	public static String generateAlphanumeric(int passwordLength) {
		return builder()
				.length(passwordLength)
				.includeSpecialChars(false)
				.build()
				.generate();
	}

	/**
	 * Generate a password based on this generator's configuration.
	 *
	 * @return a randomly generated password
	 * @throws IllegalStateException if no character sets are enabled
	 */
	public String generate() {
		// Build character pool and required sets
		StringBuilder charPool = new StringBuilder();
		List<String> requiredSets = new ArrayList<>();

		if (customCharSet != null && !customCharSet.isEmpty()) {
			charPool.append(customCharSet);
		}
		else {
			if (includeUppercase) {
				String chars = excludeAmbiguous ? removeAmbiguous(UPPERCASE, "O") : UPPERCASE;
				charPool.append(chars);
				requiredSets.add(chars);
			}

			if (includeLowercase) {
				String chars = excludeAmbiguous ? removeAmbiguous(LOWERCASE, "l") : LOWERCASE;
				charPool.append(chars);
				requiredSets.add(chars);
			}

			if (includeDigits) {
				String chars = excludeAmbiguous ? removeAmbiguous(DIGITS, "01") : DIGITS;
				charPool.append(chars);
				requiredSets.add(chars);
			}

			if (includeSpecialChars) {
				charPool.append(SPECIAL);
				requiredSets.add(SPECIAL);
			}
		}

		if (charPool.length() == 0) {
			throw new IllegalStateException("No character sets enabled for password generation");
		}

		String pool = charPool.toString();
		List<Character> passwordChars = new ArrayList<>();

		// Add at least one character from each required set (if we have room)
		for (String requiredSet : requiredSets) {
			if (passwordChars.size() < length && requiredSet.length() > 0) {
				passwordChars.add(requiredSet.charAt(SECURE_RANDOM.nextInt(requiredSet.length())));
			}
		}

		// Fill remaining length with random characters from full pool
		for (int i = passwordChars.size(); i < length; i++) {
			passwordChars.add(pool.charAt(SECURE_RANDOM.nextInt(pool.length())));
		}

		// Shuffle to avoid predictable patterns
		Collections.shuffle(passwordChars, SECURE_RANDOM);

		// Convert to string
		StringBuilder password = new StringBuilder(length);
		for (char c : passwordChars) {
			password.append(c);
		}

		return password.toString();
	}

	/**
	 * Remove ambiguous characters from a character set.
	 *
	 * @param charSet the original character set
	 * @param ambiguous characters to remove
	 * @return the filtered character set
	 */
	private String removeAmbiguous(String charSet, String ambiguous) {
		StringBuilder result = new StringBuilder();
		for (char c : charSet.toCharArray()) {
			if (ambiguous.indexOf(c) == -1) {
				result.append(c);
			}
		}
		return result.toString();
	}

	/**
	 * Builder for PasswordGenerator.
	 */
	public static class Builder {
		private int length = 16;
		private boolean includeUppercase = true;
		private boolean includeLowercase = true;
		private boolean includeDigits = true;
		private boolean includeSpecialChars = true;
		private boolean excludeAmbiguous = false;
		private String customCharSet = null;

		/**
		 * Set the password length.
		 *
		 * @param passwordLength the desired password length (minimum 1)
		 * @return this builder
		 * @throws IllegalArgumentException if length is less than 1
		 */
		public Builder length(int passwordLength) {
			if (passwordLength < 1) {
				throw new IllegalArgumentException("Password length must be at least 1");
			}
			this.length = passwordLength;
			return this;
		}

		/**
		 * Include uppercase letters (A-Z).
		 *
		 * @param include whether to include uppercase letters
		 * @return this builder
		 */
		public Builder includeUppercase(boolean include) {
			this.includeUppercase = include;
			return this;
		}

		/**
		 * Include lowercase letters (a-z).
		 *
		 * @param include whether to include lowercase letters
		 * @return this builder
		 */
		public Builder includeLowercase(boolean include) {
			this.includeLowercase = include;
			return this;
		}

		/**
		 * Include digits (0-9).
		 *
		 * @param include whether to include digits
		 * @return this builder
		 */
		public Builder includeDigits(boolean include) {
			this.includeDigits = include;
			return this;
		}

		/**
		 * Include special characters (!@#$%^&*()-_=+[]{}|;:,.<>?).
		 *
		 * @param include whether to include special characters
		 * @return this builder
		 */
		public Builder includeSpecialChars(boolean include) {
			this.includeSpecialChars = include;
			return this;
		}

		/**
		 * Exclude ambiguous characters (0, O, 1, l) that look similar.
		 * Useful for passwords that will be manually typed.
		 *
		 * @param exclude whether to exclude ambiguous characters
		 * @return this builder
		 */
		public Builder excludeAmbiguous(boolean exclude) {
			this.excludeAmbiguous = exclude;
			return this;
		}

		/**
		 * Use a custom character set instead of the standard sets.
		 * When set, this overrides all include* settings.
		 *
		 * @param charSet the custom character set to use
		 * @return this builder
		 */
		public Builder customCharSet(String charSet) {
			this.customCharSet = charSet;
			return this;
		}

		/**
		 * Build the PasswordGenerator instance.
		 *
		 * @return a new PasswordGenerator
		 */
		public PasswordGenerator build() {
			return new PasswordGenerator(this);
		}
	}
}

