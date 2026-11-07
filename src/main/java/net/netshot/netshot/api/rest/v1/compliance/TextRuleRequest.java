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
package net.netshot.netshot.api.rest.v1.compliance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.RefId;

/**
 * The definition of a text rule.
 *
 * @param name the name
 * @param enabled whether the rule is checked (false if absent)
 * @param policy the ID of the policy (required to create a rule, can't change)
 * @param field the field to match the text in
 * @param context the context (the blocks of the field to look in, as a regular expression)
 * @param text the text to look for
 * @param regExp whether the text is a regular expression (false if absent)
 * @param invert whether to invert the result (false if absent)
 * @param anyBlock whether one block of the context matching is enough (false if absent)
 * @param matchAll whether the text must match all the content (false if absent)
 * @param normalize whether to normalize the white spaces of the field first (false if absent)
 * @param deviceDriver the name of the driver the rule applies to (any if absent)
 */
@Schema(name = "TextRuleRequest", description = "Definition of a text compliance rule")
@ReadOnlyFrom(TextRuleDto.class)
@JsonTypeName("TEXT")
public record TextRuleRequest(
	@JsonProperty(required = true) String name,
	boolean enabled,
	RefId policy,
	String field,
	String context,
	String text,
	boolean regExp,
	boolean invert,
	boolean anyBlock,
	boolean matchAll,
	boolean normalize,
	String deviceDriver) implements RuleRequest {
}
