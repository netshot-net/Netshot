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
package net.netshot.netshot.platform.database;

import org.jooq.exception.DataAccessException;

/**
 * Helpers to tell what a failed database operation was about.
 */
public final class DatabaseErrors {

	private DatabaseErrors() {
		// Static helpers only
	}

	/**
	 * Tells whether a jOOQ statement failed on a unique constraint (or primary key), from the
	 * SQL state of the failure ({@code 23505}), which doesn't depend on the language of the
	 * messages of the server.
	 *
	 * @param e the failure
	 * @return true for a unique violation
	 */
	public static boolean isUniqueViolation(DataAccessException e) {
		return "23505".equals(e.sqlState());
	}

	/**
	 * Tells whether a jOOQ statement failed on a foreign key (the row is still referenced, or
	 * references a missing one), from the SQL state of the failure ({@code 23503}).
	 *
	 * @param e the failure
	 * @return true for a foreign key violation
	 */
	public static boolean isForeignKeyViolation(DataAccessException e) {
		return "23503".equals(e.sqlState());
	}
}
