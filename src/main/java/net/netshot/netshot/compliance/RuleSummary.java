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

/**
 * A compliance rule with minimal details (projection of the list of rules).
 *
 * @param id the rule ID
 * @param name the name
 * @param type the kind of rule: the simple class name of the rule
 * @param enabled whether the rule is enabled
 * @param policyId the ID of the policy owning the rule
 */
public record RuleSummary(long id, String name, String type, boolean enabled, long policyId) {

	/**
	 * Instantiates a summary from the class of the rule (as the query provides it).
	 *
	 * @param id the rule ID
	 * @param name the name
	 * @param type the class of the rule
	 * @param enabled whether the rule is enabled
	 * @param policyId the ID of the policy owning the rule
	 */
	public RuleSummary(long id, String name, Class<? extends Rule> type, boolean enabled, long policyId) {
		this(id, name, type.getSimpleName(), enabled, policyId);
	}
}
