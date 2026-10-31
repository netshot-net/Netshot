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
 * A rule checking the devices with a JavaScript script.
 *
 * @param id the ID
 * @param url the path of the rule
 * @param display the display name
 * @param type the kind of rule
 * @param name the name
 * @param enabled whether the rule is checked
 * @param policy the policy of the rule
 * @param script the script
 */
@Schema(name = "JavaScriptRule", description = "Compliance rule checked by a JavaScript script")
@JsonTypeName("JAVASCRIPT")
public record JavaScriptRuleDto(
	long id,
	String url,
	String display,
	@Brief String type,
	@Brief String name,
	@Brief boolean enabled,
	@Brief @Ref PolicyDto policy,
	String script) implements RuleDto {
}
