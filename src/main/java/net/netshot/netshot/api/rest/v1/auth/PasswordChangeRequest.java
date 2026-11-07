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
package net.netshot.netshot.api.rest.v1.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The change of the password of the current (local) user.
 *
 * @param currentPassword the current password
 * @param newPassword the new password, which must comply with the password policy
 */
@Schema(name = "PasswordChangeRequest", description = "Change of the password of the current user")
public record PasswordChangeRequest(
	@JsonProperty(required = true) @Schema(format = "password") String currentPassword,
	@JsonProperty(required = true) @Schema(format = "password") String newPassword) {
}
