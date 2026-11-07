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
package net.netshot.netshot.compliance;

import java.time.Instant;

/**
 * A rule defines a number of constraints that a device should comply with.
 */
public sealed interface Rule permits TextRule, JavaScriptRule, PythonRule {

	/**
	 * Gets the ID.
	 *
	 * @return the ID (0 for a rule not stored yet)
	 */
	long id();

	/**
	 * Gets the version of the rule, incremented on each change (of the rule or of its exemptions).
	 *
	 * @return the version
	 */
	int version();

	/**
	 * Gets the date of the last change of the rule (or of its exemptions).
	 *
	 * @return the date
	 */
	Instant changeDate();

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * Tells whether the rule is checked.
	 *
	 * @return true if enabled
	 */
	boolean enabled();

	/**
	 * Gets the ID of the policy of the rule.
	 *
	 * @return the policy ID
	 */
	long policyId();

	/**
	 * Gets a copy of the rule with another ID.
	 *
	 * @param id the ID
	 * @return the rule
	 */
	Rule withId(long id);

	/**
	 * Gets the name of the type of rule, as the REST API publishes it.
	 *
	 * @return the name of the type
	 */
	default String typeName() {
		return this.getClass().getSimpleName();
	}
}
