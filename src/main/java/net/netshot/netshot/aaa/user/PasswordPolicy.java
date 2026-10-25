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
package net.netshot.netshot.aaa.user;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;

/**
 * Password policy.
 */
public class PasswordPolicy {

	/**
	 * Exception to be thrown in case of password policy check failure.
	 */
	public static class PasswordPolicyException extends Exception {
		private static final long serialVersionUID = 1L;

		public PasswordPolicyException(String message) {
			super(message);
		}
	}

	/**
	 * Match for specific characters in the password.
	 */
	public enum CharMatch {
		ANY("mintotalchars", "minimum total length", ".", 1),
		SPECIAL("minspecialchars", "minimum special characters count", "[!\"#$%&'()*+,-./:;<=>?@\\[\\]\\^_{}|~]", 0),
		NUMERICAL("minnumericalchars", "minimum numerical character count", "[0-9]", 0),
		LOWERCASE("minlowercasechars", "minimum lowercase character count", "[a-z]", 0),
		UPPERCASE("minuppercasechars", "minimum uppercase character count", "[A-Z]", 0);

		@Getter
		private String name;

		@Getter
		private String description;

		@Getter
		private Pattern pattern;

		@Getter
		private int defaultValue;

		CharMatch(String name, String description, String pattern, int defaultValue) {
			this.name = name;
			this.description = description;
			this.pattern = Pattern.compile(pattern);
			this.defaultValue = defaultValue;
		}

		/**
		 * Count the number of matches in the given target string.
		 * @param target Where to find characters
		 * @return the number of matches
		 */
		public long countMatches(String target) {
			Matcher m = this.pattern.matcher(target);
			return m.results().count();
		}
	}

	/** Max number of previous passwords which can't be used again. */
	private static final Setting<Integer> MAX_HISTORY = Setting.integer("netshot.aaa.passwordpolicy.maxhistory")
		.defaultValue(0).range(0, Integer.MAX_VALUE).live();

	/** Max duration of a password, in days. */
	private static final Setting<Integer> MAX_DURATION = Setting.integer("netshot.aaa.passwordpolicy.maxduration")
		.defaultValue(0).range(0, Integer.MAX_VALUE).live();

	/** Min total number of characters. */
	private static final Setting<Integer> MIN_TOTAL_CHARS = minCharsSetting(CharMatch.ANY);

	/** Min number of special characters. */
	private static final Setting<Integer> MIN_SPECIAL_CHARS = minCharsSetting(CharMatch.SPECIAL);

	/** Min number of numerical characters. */
	private static final Setting<Integer> MIN_NUMERICAL_CHARS = minCharsSetting(CharMatch.NUMERICAL);

	/** Min number of lowercase characters. */
	private static final Setting<Integer> MIN_LOWERCASE_CHARS = minCharsSetting(CharMatch.LOWERCASE);

	/** Min number of uppercase characters. */
	private static final Setting<Integer> MIN_UPPERCASE_CHARS = minCharsSetting(CharMatch.UPPERCASE);

	/** The main policy, from the configuration. */
	private static final ConfigStore.Binding<PasswordPolicy> MAIN_POLICY = Netshot.config().bind(PasswordPolicy::fromConfig);

	private static Setting<Integer> minCharsSetting(CharMatch match) {
		return Setting.integer("netshot.aaa.passwordpolicy." + match.getName())
			.defaultValue(match.getDefaultValue()).range(0, 1024).live();
	}

	/**
	 * Builds the main policy from the configuration.
	 * @param config the configuration
	 * @return the policy
	 */
	private static PasswordPolicy fromConfig(Config config) {
		PasswordPolicy policy = new PasswordPolicy();
		policy.maxHistory = config.get(MAX_HISTORY);
		policy.maxDuration = config.get(MAX_DURATION);
		Map<CharMatch, Setting<Integer>> minChars = Map.of(
			CharMatch.ANY, MIN_TOTAL_CHARS,
			CharMatch.SPECIAL, MIN_SPECIAL_CHARS,
			CharMatch.NUMERICAL, MIN_NUMERICAL_CHARS,
			CharMatch.LOWERCASE, MIN_LOWERCASE_CHARS,
			CharMatch.UPPERCASE, MIN_UPPERCASE_CHARS);
		for (CharMatch m : CharMatch.values()) {
			int min = config.get(minChars.get(m));
			if (min > 0) {
				policy.minCharMatchCounts.put(m, min);
			}
		}
		return policy;
	}

	/**
	 * Get the main password policy (as configured in Netshot config file).
	 * @return the main password policy
	 */
	public static PasswordPolicy getMainPolicy() {
		return MAIN_POLICY.get();
	}

	/** Max history count. */
	@Getter
	@Setter
	private int maxHistory;

	/** Max duration of the password, in days. */
	@Getter
	@Setter
	private int maxDuration;

	/** Min number of characters per character match. */
	@Getter
	@Setter
	private Map<CharMatch, Integer> minCharMatchCounts = new HashMap<>();
}
