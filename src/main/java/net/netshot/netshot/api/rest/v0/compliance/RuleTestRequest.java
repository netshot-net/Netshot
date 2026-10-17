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
package net.netshot.netshot.api.rest.v0.compliance;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;


import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * The request to test a compliance rule against a device: the properties of a rule (see
 * {@link RuleRequest}, whose shape this repeats since a record can't extend another one) and the
 * device to check.
 *
 * @param id not used
 * @param name not used
 * @param type the kind of rule: {@code TextRule}, {@code JavaScriptRule} or {@code PythonRule}
 * @param script the script (JavaScript and Python rules)
 * @param policy not used
 * @param enabled not used
 * @param exemptions not used
 * @param text the text to look for (text rules)
 * @param regExp whether the text is a regular expression (text rules)
 * @param context the regular expression of the context (text rules)
 * @param driver the name of the device driver the rule applies to (text rules)
 * @param field the configuration attribute to search in (text rules)
 * @param anyBlock whether one matching block is enough (text rules)
 * @param matchAll whether all the lines have to match (text rules)
 * @param invert whether to invert the result (text rules)
 * @param normalize whether to normalize the text before searching (text rules)
 * @param device the ID of the device to check the rule against
 */
// Transitional, see NewDeviceRequest
@XmlRootElement(name = "rsRuleTest")
public record RuleTestRequest(
	long id,
	String name,
	String type,
	String script,
	long policy,
	boolean enabled,
	Map<Long, Date> exemptions,
	String text,
	Boolean regExp,
	String context,
	String driver,
	String field,
	Boolean anyBlock,
	Boolean matchAll,
	Boolean invert,
	Boolean normalize,
	long device) {

	/**
	 * Applies the default values.
	 */
	public RuleTestRequest {
		if (type == null) {
			type = "";
		}
		if (exemptions == null) {
			exemptions = new HashMap<>();
		}
	}
}
