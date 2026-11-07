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
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.group.GroupDto;

/**
 * A hardware compliance rule: the end-of-sale and end-of-life dates of the modules matching it.
 *
 * @param id the ID
 * @param url the path of the rule
 * @param display the display name
 * @param version the version, incremented on each change (the ETag)
 * @param changeDate the date of the last change
 * @param partNumber the part number of the modules
 * @param endOfSale the end-of-sale date of the matching modules
 * @param endOfLife the end-of-life date of the matching modules
 * @param group the group the rule applies to (null for all the devices)
 * @param driver the name of the driver of the devices (null for any)
 * @param family the family of the devices (empty for any)
 * @param familyRegExp whether the family is a regular expression
 * @param partNumberRegExp whether the part number is a regular expression
 */
@Schema(name = "HardwareRule", description = "Hardware compliance rule")
public record HardwareRuleDto(
	long id,
	String url,
	String display,
	long version,
	Instant changeDate,
	@Brief String partNumber,
	@Brief Instant endOfSale,
	@Brief Instant endOfLife,
	@Brief @Ref GroupDto group,
	@Brief String driver,
	@Brief String family,
	@Brief boolean familyRegExp,
	@Brief boolean partNumberRegExp) {
}
