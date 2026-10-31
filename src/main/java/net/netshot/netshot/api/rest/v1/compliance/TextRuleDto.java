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
package net.netshot.netshot.api.rest.v1.compliance;

import com.fasterxml.jackson.annotation.JsonTypeName;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.api.rest.v1.Ref;

/**
 * A rule matching a text in a field of the devices (configuration or device attribute).
 *
 * @param id the ID
 * @param url the path of the rule
 * @param display the display name
 * @param type the kind of rule
 * @param name the name
 * @param enabled whether the rule is checked
 * @param policy the policy of the rule
 * @param field the field to match the text in
 * @param context the context (the blocks of the field to look in, as a regular expression)
 * @param text the text to look for
 * @param regExp whether the text is a regular expression
 * @param invert whether the result is inverted
 * @param anyBlock whether one block of the context matching is enough
 * @param matchAll whether the text must match all the content (instead of a part)
 * @param normalize whether the white spaces of the field are normalized first
 * @param deviceDriver the name of the driver the rule applies to (null for any)
 */
@Schema(name = "TextRule", description = "Compliance rule matching a text")
@JsonTypeName("TEXT")
public record TextRuleDto(
	long id,
	String url,
	String display,
	@Brief String type,
	@Brief String name,
	@Brief boolean enabled,
	@Brief @Ref PolicyDto policy,
	String field,
	String context,
	String text,
	boolean regExp,
	boolean invert,
	boolean anyBlock,
	boolean matchAll,
	boolean normalize,
	String deviceDriver) implements RuleDto {
}
