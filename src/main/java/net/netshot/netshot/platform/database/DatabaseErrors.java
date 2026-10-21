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

import org.hibernate.HibernateException;
import org.jooq.exception.DataAccessException;

/**
 * Helpers to tell what a failed database operation was about.
 */
public final class DatabaseErrors {

	private DatabaseErrors() {
		// Static helpers only
	}

	/**
	 * Tells whether the cause of a failure (typically the SQL exception behind a constraint
	 * violation) mentions some text, like "uplicate" for a duplicate key or the name of a column
	 * for a foreign key violation. This is how the credential set, Vault instance and hook
	 * use cases have always recognized the errors they report: it is not the same as
	 * {@link Database#isDuplicateException}, which also takes any constraint violation for a duplicate.
	 *
	 * @param e the failure
	 * @param text the text to look for in the message of its direct cause
	 * @return true if the failure has a cause, and its message contains the text
	 */
	public static boolean causeMentions(HibernateException e, String text) {
		Throwable cause = e.getCause();
		return cause != null && cause.getMessage() != null && cause.getMessage().contains(text);
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
}
