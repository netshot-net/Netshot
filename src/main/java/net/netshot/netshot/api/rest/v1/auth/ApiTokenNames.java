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
package net.netshot.netshot.api.rest.v1.auth;

import net.netshot.netshot.aaa.user.ApiToken;

/**
 * Names the API tokens.
 */
public final class ApiTokenNames {

	private ApiTokenNames() {
	}

	/**
	 * Gets the display name of an API token: its description, or its ID if it has none.
	 *
	 * @param token the API token
	 * @return the display name
	 */
	public static String display(ApiToken token) {
		String description = token.description();
		return description == null || description.isBlank() ? "API token " + token.id() : description;
	}
}
