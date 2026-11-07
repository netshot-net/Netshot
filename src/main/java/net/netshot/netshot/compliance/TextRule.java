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
package net.netshot.netshot.compliance;

import java.time.Instant;

import lombok.Builder;

/**
 * A rule matching a text in a field of the devices (configuration or device attribute).
 *
 * @param id the ID (0 for a rule not stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the date of the last change
 * @param name the name
 * @param enabled true if the rule is checked
 * @param policyId the ID of the policy
 * @param field the field to match the text in
 * @param context the context (the blocks of the field to look in, as a regular expression)
 * @param text the text to look for (without carriage return)
 * @param regExp true if the text is a regular expression
 * @param invert true to invert the result
 * @param anyBlock true if one block of the context matching is enough
 * @param matchAll true if the text must match all the content (instead of a part)
 * @param normalize true to normalize the white spaces of the field first
 * @param deviceDriver the name of the driver the rule applies to (null for any)
 */
@Builder(builderMethodName = "defaults", toBuilder = true)
public record TextRule(long id, int version, Instant changeDate, String name, boolean enabled, long policyId, String field, String context, String text,
	boolean regExp, boolean invert, boolean anyBlock, boolean matchAll, boolean normalize, String deviceDriver) implements Rule {

	/**
	 * Canonical constructor: the carriage returns of the text are removed.
	 */
	public TextRule {
		if (text != null) {
			text = text.replace("\r", "");
		}
	}

	/**
	 * Starts building a rule.
	 *
	 * @param name the name
	 * @param policyId the ID of the policy
	 * @return the builder
	 */
	public static TextRuleBuilder builder(String name, long policyId) {
		return defaults().name(name).policyId(policyId);
	}

	@Override
	public TextRule withId(long newId) {
		return this.toBuilder().id(newId).build();
	}

	@Override
	public String toString() {
		return "Compliance text-based rule " + id + " (name '" + name + "')";
	}
}
