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
package net.netshot.netshot.aaa.user;

import java.security.Principal;

import lombok.Getter;

/**
 * Netshot user with permission level: a user who logged in, or an API token.
 */
public sealed interface User extends Principal permits UiUser, ApiToken {

	/** Read-only authorization level. */
	int LEVEL_READONLY = 10;
	String ROLE_READONLY = "read-only";

	/** Operator authorization level. */
	int LEVEL_OPERATOR = 50;
	String ROLE_OPERATOR = "operator";

	/** Read-write authorization level. */
	int LEVEL_READWRITE = 100;
	String ROLE_READWRITE = "read-write";

	/** Read-write and command executer (on devices) level. */
	int LEVEL_EXECUTEREADWRITE = 500;
	String ROLE_EXECUTEREADWRITE = "execute-read-write";

	/** Admin authorization level. */
	int LEVEL_ADMIN = 1000;
	String ROLE_ADMIN = "admin";

	/** Role with name and level. */
	enum Role {
		READONLY(ROLE_READONLY, LEVEL_READONLY),
		OPERATOR(ROLE_OPERATOR, LEVEL_OPERATOR),
		READWRITE(ROLE_READWRITE, LEVEL_READWRITE),
		EXECUTEREADWRITE(ROLE_EXECUTEREADWRITE, LEVEL_EXECUTEREADWRITE),
		ADMIN(ROLE_ADMIN, LEVEL_ADMIN);

		/** Name of the role. */
		@Getter
		private String name;

		/** Numerical authorization level of the role.  */
		@Getter
		private int level;

		/**
		 * Private constructor.
		 * @param name = the role name
		 * @param level = the role level
		 */
		Role(String name, int level) {
			this.name = name;
			this.level = level;
		}
	}

	/**
	 * Gets the username.
	 *
	 * @return the username
	 */
	String username();

	/**
	 * Gets the level.
	 *
	 * @return the level
	 */
	int level();

}
