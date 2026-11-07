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
package net.netshot.netshot.platform.database;

import org.jooq.exception.DataAccessException;

import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

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

	/**
	 * Tells whether a statement failed because of an invalid regular expression given to a {@code ~} operator
	 * (a regex filter of a list query), from the SQL state of the failure ({@code 2201B}).
	 *
	 * @param e the exception thrown by jOOQ
	 * @return true if a regular expression was invalid
	 */
	public static boolean isInvalidRegularExpression(DataAccessException e) {
		return "2201B".equals(e.sqlState());
	}

	/**
	 * Translates the failure of a list query: an invalid regular expression of a filter is the client's error,
	 * anything else a database access error.
	 *
	 * @param e the exception thrown by jOOQ
	 * @param message the message of a database access error
	 * @return the exception to throw
	 */
	public static NetshotException listFailure(DataAccessException e, String message) {
		if (isInvalidRegularExpression(e)) {
			return new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
				"Invalid regular expression: " + e.getCause().getMessage());
		}
		return new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, message, e);
	}
}
