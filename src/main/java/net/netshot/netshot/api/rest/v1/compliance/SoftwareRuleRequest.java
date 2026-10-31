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

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.RefId;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;

/**
 * The definition of a software rule, to create one ({@code POST}) or replace one ({@code PUT}, and the result of a
 * {@code PATCH}). The read-only properties of the representation ({@link SoftwareRuleDto}) are ignored.
 *
 * @param priority the position of the rule among the software rules, from 1 (clamped to the number of rules): the
 *        rule is moved there and the other rules shift. Absent: the last position for a new rule, the current one
 *        for an existing rule
 * @param level the conformance level of the matching devices
 * @param group the ID of the group the rule applies to (all the devices if absent)
 * @param driver the name of the driver of the devices (any if absent)
 * @param family the family of the devices (any if absent)
 * @param familyRegExp whether the family is a regular expression (false if absent)
 * @param version the software version of the devices (any if absent)
 * @param versionRegExp whether the version is a regular expression (false if absent)
 * @param partNumber the part number of a module of the devices (any if absent)
 * @param partNumberRegExp whether the part number is a regular expression (false if absent)
 */
@Schema(name = "SoftwareRuleRequest", description = "Definition of a software compliance rule")
@ReadOnlyFrom(SoftwareRuleDto.class)
public record SoftwareRuleRequest(
	@Schema(minimum = "1") Integer priority,
	@JsonProperty(required = true) ConformanceLevel level,
	RefId group,
	String driver,
	String family,
	boolean familyRegExp,
	String version,
	boolean versionRegExp,
	String partNumber,
	boolean partNumberRegExp) {
}
