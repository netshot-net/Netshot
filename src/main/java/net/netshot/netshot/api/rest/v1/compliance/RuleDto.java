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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A compliance rule, of one of the kinds given by its {@code type} property: {@code TEXT} (a text looked for in the
 * configuration or an attribute of the device), {@code JAVASCRIPT} or {@code PYTHON} (a script checking the device).
 */
@Schema(name = "Rule", description = "Compliance rule", oneOf = {TextRuleDto.class, JavaScriptRuleDto.class, PythonRuleDto.class},
	discriminatorProperty = "type")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = TextRuleDto.class, name = "TEXT"),
	@JsonSubTypes.Type(value = JavaScriptRuleDto.class, name = "JAVASCRIPT"),
	@JsonSubTypes.Type(value = PythonRuleDto.class, name = "PYTHON")})
public sealed interface RuleDto permits TextRuleDto, JavaScriptRuleDto, PythonRuleDto {
}
