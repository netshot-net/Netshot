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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;

import net.netshot.netshot.platform.error.NetshotException;

/**
 * The versions of the objects which can be changed. Every change of an object increments its version (the ETag of
 * the v1 API) and sets its change date. A change locks the row of the object first ({@link #lock}): it then reads
 * the object, checks the version the caller expects ({@link #check}), and saves it, without any concurrent change
 * in between.
 */
public final class Versions {

	private Versions() {
	}

	/**
	 * Gets the date of a change being made (to the second, as stored).
	 *
	 * @return the current date
	 */
	public static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.SECONDS);
	}

	/**
	 * Locks the row of an object until the end of the transaction ({@code SELECT ... FOR UPDATE}): a concurrent
	 * change of the object waits for the transaction to end.
	 *
	 * @param dsl the jOOQ context
	 * @param table the table of the object
	 * @param idField the ID column of the table
	 * @param versionField the version column of the table
	 * @param id the ID of the object
	 * @return the current version of the object, or null if there is no such object
	 */
	public static Integer lock(DSLContext dsl, Table<?> table, Field<Long> idField, Field<Integer> versionField, long id) {
		return dsl.select(versionField).from(table).where(idField.eq(id)).forUpdate().fetchOne(versionField);
	}

	/**
	 * Checks that an object is at the version the caller expects.
	 *
	 * @param object the kind of object, for the message (e.g. "device")
	 * @param version the current version of the object
	 * @param expectedVersion the version the object must be at, or null for any
	 * @throws NetshotException (version mismatch) if the object isn't at the expected version
	 */
	public static void check(String object, long version, Long expectedVersion) {
		if (expectedVersion != null && version != expectedVersion) {
			throw NetshotException.versionMismatch(object, expectedVersion);
		}
	}
}
