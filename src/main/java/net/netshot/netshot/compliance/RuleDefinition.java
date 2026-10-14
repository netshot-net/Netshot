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

import java.util.Date;
import java.util.Map;

/**
 * The properties of a rule to create, to apply to an existing rule, or to test. Which
 * properties are used depends on the kind of rule; a null component of a kind-specific property
 * means "not provided" (the rule keeps its value, or the default one).
 *
 * @param type the kind of rule: the simple class name ({@code TextRule}, {@code JavaScriptRule}, {@code PythonRule})
 * @param name the name (null on an update to keep the current one)
 * @param policyId the ID of the policy owning the rule (only used on creation)
 * @param enabled whether the rule is enabled (ignored on creation, the rule starts disabled)
 * @param exemptions the device exemptions, as the expiration date by device ID (on an update, replaces all the exemptions)
 * @param script the script (JavaScript and Python rules)
 * @param text the text to look for (text rules)
 * @param regExp whether the text is a regular expression (text rules)
 * @param context the regular expression of the context, i.e. the configuration block(s) to search in (text rules)
 * @param driver the name of the device driver the rule applies to (text rules)
 * @param field the configuration attribute to search in (text rules)
 * @param anyBlock whether one matching block is enough (text rules)
 * @param matchAll whether all the lines have to match (text rules)
 * @param invert whether to invert the result (text rules)
 * @param normalize whether to normalize the text before searching (text rules)
 */
public record RuleDefinition(
	String type,
	String name,
	long policyId,
	boolean enabled,
	Map<Long, Date> exemptions,
	String script,
	String text,
	Boolean regExp,
	String context,
	String driver,
	String field,
	Boolean anyBlock,
	Boolean matchAll,
	Boolean invert,
	Boolean normalize) {
}
