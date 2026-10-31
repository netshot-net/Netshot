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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.RefId;

/**
 * The definition of a JavaScript rule.
 *
 * @param name the name
 * @param enabled whether the rule is checked (false if absent)
 * @param policy the ID of the policy (required to create a rule, can't change)
 * @param script the script (the template script if absent)
 */
@Schema(name = "JavaScriptRuleRequest", description = "Definition of a JavaScript compliance rule")
@ReadOnlyFrom(JavaScriptRuleDto.class)
@JsonTypeName("JAVASCRIPT")
public record JavaScriptRuleRequest(
	@JsonProperty(required = true) String name,
	boolean enabled,
	RefId policy,
	String script) implements RuleRequest {
}
