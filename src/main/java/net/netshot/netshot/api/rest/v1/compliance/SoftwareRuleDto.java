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

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;

/**
 * A software compliance rule: the devices matching it get its conformance level. The rules are checked in order, the
 * first match wins.
 *
 * @param id the ID
 * @param url the path of the rule
 * @param display the display name
 * @param version the version, incremented on each change (the ETag)
 * @param changeDate the date of the last change
 * @param priority the position of the rule among the software rules, from 1 (checked first)
 * @param level the conformance level of the matching devices
 * @param group the group the rule applies to (null for all the devices)
 * @param driver the name of the driver of the devices (null for any)
 * @param family the family of the devices (empty for any)
 * @param familyRegExp whether the family is a regular expression
 * @param softwareVersion the software version of the devices (empty for any)
 * @param softwareVersionRegExp whether the software version is a regular expression
 * @param partNumber the part number of a module of the devices (empty for any)
 * @param partNumberRegExp whether the part number is a regular expression
 */
@Schema(name = "SoftwareRule", description = "Software compliance rule")
public record SoftwareRuleDto(
	long id,
	String url,
	String display,
	long version,
	Instant changeDate,
	@Brief int priority,
	@Brief ConformanceLevel level,
	@Brief @Ref GroupDto group,
	@Brief String driver,
	@Brief String family,
	@Brief boolean familyRegExp,
	@Brief String softwareVersion,
	@Brief boolean softwareVersionRegExp,
	@Brief String partNumber,
	@Brief boolean partNumberRegExp) {
}
