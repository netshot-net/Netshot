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
import net.netshot.netshot.api.rest.v1.RefId;

/**
 * The definition of a compliance rule, to create one ({@code POST}) or replace one ({@code PUT}, and the result of a
 * {@code PATCH}), of the kind given by its {@code type} property. The read-only properties of the representation
 * ({@link RuleDto}) are ignored. The kind of an existing rule can't change.
 */
@Schema(name = "RuleRequest", description = "Definition of a compliance rule",
	oneOf = {TextRuleRequest.class, JavaScriptRuleRequest.class, PythonRuleRequest.class}, discriminatorProperty = "type")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = TextRuleRequest.class, name = "TEXT"),
	@JsonSubTypes.Type(value = JavaScriptRuleRequest.class, name = "JAVASCRIPT"),
	@JsonSubTypes.Type(value = PythonRuleRequest.class, name = "PYTHON")})
public sealed interface RuleRequest permits TextRuleRequest, JavaScriptRuleRequest, PythonRuleRequest {

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * Tells whether the rule is enabled.
	 *
	 * @return true if enabled
	 */
	boolean enabled();

	/**
	 * Gets the ID of the policy.
	 *
	 * @return the policy ID, or null if not given
	 */
	RefId policy();
}
