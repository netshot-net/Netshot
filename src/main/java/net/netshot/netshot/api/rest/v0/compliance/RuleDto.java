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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;


/**
 * A compliance rule, with the properties of its kind. The {@code type} property tells the kind
 * (the simple class name of the rule).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = RuleDto.Text.class, name = "TextRule"),
	@JsonSubTypes.Type(value = RuleDto.JavaScript.class, name = "JavaScriptRule"),
	@JsonSubTypes.Type(value = RuleDto.Python.class, name = "PythonRule"),
})
public sealed interface RuleDto {

	/**
	 * Gets the rule ID.
	 *
	 * @return the ID
	 */
	long id();

	/**
	 * Gets the name of the rule.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * Tells whether the rule is enabled.
	 *
	 * @return true if the rule is enabled
	 */
	boolean enabled();

	/**
	 * A rule searching a text in a configuration.
	 *
	 * @param anyBlock whether one matching block is enough
	 * @param context the regular expression of the context, i.e. the configuration block(s) to search in
	 * @param deviceDriver the name of the device driver the rule applies to
	 * @param deviceDriverDescription the description of the device driver
	 * @param enabled whether the rule is enabled
	 * @param field the configuration attribute to search in
	 * @param id the rule ID
	 * @param invert whether to invert the result
	 * @param matchAll whether all the lines have to match
	 * @param name the name
	 * @param normalize whether to normalize the text before searching
	 * @param regExp whether the text is a regular expression
	 * @param text the text to look for
	 */
	@JacksonXmlRootElement(localName = "TextRule")
	record Text(
		boolean anyBlock,
		String context,
		String deviceDriver,
		String deviceDriverDescription,
		boolean enabled,
		String field,
		long id,
		boolean invert,
		boolean matchAll,
		String name,
		boolean normalize,
		boolean regExp,
		String text) implements RuleDto {
	}

	/**
	 * A rule evaluated by a JavaScript script.
	 *
	 * @param enabled whether the rule is enabled
	 * @param id the rule ID
	 * @param name the name
	 * @param script the script
	 */
	@JacksonXmlRootElement(localName = "JavaScriptRule")
	record JavaScript(boolean enabled, long id, String name, String script) implements RuleDto {
	}

	/**
	 * A rule evaluated by a Python script.
	 *
	 * @param enabled whether the rule is enabled
	 * @param id the rule ID
	 * @param name the name
	 * @param script the script
	 */
	@JacksonXmlRootElement(localName = "PythonRule")
	record Python(boolean enabled, long id, String name, String script) implements RuleDto {
	}
}
