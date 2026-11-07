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
package net.netshot.netshot.inventory.compliance;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

/**
 * The result of the check of a device against a compliance rule.
 *
 * @param ruleId the ID of the rule
 * @param deviceId the ID of the device
 * @param checkDate when the check was made
 * @param comment the comment of the rule (abbreviated to fit in the database)
 * @param result the result
 */
public record CheckResult(long ruleId, long deviceId, Instant checkDate, String comment, ResultOption result) {

	/**
	 * The Enum ResultOption.
	 */
	public enum ResultOption {

		/** The device is confirming to the rule. */
		CONFORMING(0),

		/** The device is exempted from this rule. */
		EXEMPTED(4),

		/** The rule is invalid. */
		INVALIDRULE(3),

		/** The rule is disabled. */
		DISABLED(5),

		/** The device isn't conforming to the rule. */
		NONCONFORMING(1),

		/** The rule is not applicable to this device. */
		NOTAPPLICABLE(2);

		/** The value. */
		@Getter
		private final int value;

		/**
		 * Instantiates a new result option.
		 *
		 * @param value the value
		 */
		ResultOption(int value) {
			this.value = value;
		}
	}

	/** The maximum length of a comment. */
	public static final int MAX_COMMENT_LENGTH = 9900;

	/**
	 * Canonical constructor: the comment is abbreviated to fit in the database.
	 */
	public CheckResult {
		comment = StringUtils.abbreviate(comment, MAX_COMMENT_LENGTH);
	}

	/**
	 * Builds a result checked now, without comment.
	 *
	 * @param ruleId the ID of the rule
	 * @param deviceId the ID of the device
	 * @param result the result
	 * @return the check result
	 */
	public static CheckResult of(long ruleId, long deviceId, ResultOption result) {
		return new CheckResult(ruleId, deviceId, Instant.now(), "", result);
	}

	/**
	 * Builds a result checked now.
	 *
	 * @param ruleId the ID of the rule
	 * @param deviceId the ID of the device
	 * @param result the result
	 * @param comment the comment
	 * @return the check result
	 */
	public static CheckResult of(long ruleId, long deviceId, ResultOption result, String comment) {
		return new CheckResult(ruleId, deviceId, Instant.now(), comment, result);
	}
}
