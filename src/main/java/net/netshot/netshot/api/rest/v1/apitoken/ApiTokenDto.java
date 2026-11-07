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
package net.netshot.netshot.api.rest.v1.apitoken;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;

/**
 * An API token. The token value is never written (only Netshot's hash of it is stored): it is given once, in the
 * answer to the creation ({@link ApiTokenCreatedDto}).
 *
 * @param id the ID
 * @param url the path of the API token
 * @param display the description (or a name made of the ID if there is none)
 * @param changeDate the date of the creation of the token (it can't change)
 * @param description the description
 * @param level the permission level
 * @param role the name of the highest role the level gives
 */
@Schema(name = "ApiToken", description = "API token (the value of the token is not part of it)")
public record ApiTokenDto(
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long id,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String url,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String display,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Instant changeDate,
	@Brief String description,
	@Brief int level,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY, nullable = true) String role) {
}
