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
 * A rule checking the devices with a JavaScript script.
 *
 * @param id the ID (0 for a rule not stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the date of the last change
 * @param name the name
 * @param enabled true if the rule is checked
 * @param policyId the ID of the policy
 * @param script the script
 */
@Builder(toBuilder = true)
public record JavaScriptRule(long id, int version, Instant changeDate, String name, boolean enabled, long policyId, String script) implements Rule {

	/** The script of a new rule. */
	public static final String TEMPLATE = ""
		+ "/*\n"
		+ " * Script template - to be customized.\n"
		+ " */\n"
		+ "function check(device) {\n"
		+ "    //const config = device.get(\"runningConfig\");\n"
		+ "    //const name = device.get(\"name\");\n"
		+ "    return CONFORMING;\n"
		+ "    //return NONCONFORMING;\n"
		+ "    //return NOTAPPLICABLE;\n"
		+ "}\n";

	/**
	 * Builds a new (disabled) rule, with the script template.
	 *
	 * @param name the name
	 * @param policyId the ID of the policy
	 * @return the rule
	 */
	public static JavaScriptRule of(String name, long policyId) {
		return new JavaScriptRule(0, 0, null, name, false, policyId, TEMPLATE);
	}

	@Override
	public JavaScriptRule withId(long newId) {
		return new JavaScriptRule(newId, this.version, this.changeDate, this.name, this.enabled, this.policyId, this.script);
	}

	@Override
	public String toString() {
		return "Compliance JavaScript-based rule " + id + " (name '" + name + "')";
	}
}
