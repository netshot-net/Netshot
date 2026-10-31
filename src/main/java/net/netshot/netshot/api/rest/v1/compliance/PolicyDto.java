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

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.group.GroupDto;

/**
 * A compliance policy: a set of rules, checked on the devices of the target groups.
 *
 * @param id the ID
 * @param url the path of the policy
 * @param display the display name
 * @param name the name
 * @param targetGroups the groups whose devices the policy applies to
 */
@Schema(name = "Policy", description = "Compliance policy")
public record PolicyDto(
	long id,
	String url,
	String display,
	@Brief String name,
	@Ref List<GroupDto> targetGroups) {
}
