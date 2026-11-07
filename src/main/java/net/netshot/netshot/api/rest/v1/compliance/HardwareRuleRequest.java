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

import java.time.Instant;


import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.RefId;

/**
 * The definition of a hardware rule, to create one ({@code POST}) or replace one ({@code PUT}, and the result of a
 * {@code PATCH}). The read-only properties of the representation ({@link HardwareRuleDto}) are ignored.
 *
 * @param partNumber the part number of the modules (any if absent)
 * @param endOfSale the end-of-sale date of the matching modules (none if absent)
 * @param endOfLife the end-of-life date of the matching modules (none if absent)
 * @param group the ID of the group the rule applies to (all the devices if absent)
 * @param driver the name of the driver of the devices (any if absent)
 * @param family the family of the devices (any if absent)
 * @param familyRegExp whether the family is a regular expression (false if absent)
 * @param partNumberRegExp whether the part number is a regular expression (false if absent)
 */
@Schema(name = "HardwareRuleRequest", description = "Definition of a hardware compliance rule")
@ReadOnlyFrom(HardwareRuleDto.class)
public record HardwareRuleRequest(
	String partNumber,
	Instant endOfSale,
	Instant endOfLife,
	RefId group,
	String driver,
	String family,
	boolean familyRegExp,
	boolean partNumberRegExp) {
}
