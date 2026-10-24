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

/**
 * The kinds of database connections, one pool each (see {@link ConnectionPools}).
 */
public enum PoolKind {
	/** Queries ({@link Transactions#read}), on the database of {@code netshot.db.readurl} if set. */
	READ_ONLY("ReadOnly"),
	/** Read-write transactions ({@link Transactions#write}). */
	READ_WRITE("ReadWrite"),
	/** Small dedicated pool for task dispatch and cluster bookkeeping ({@link Transactions#system}). */
	SYSTEM("System");

	private final String sourceName;

	PoolKind(String sourceName) {
		this.sourceName = sourceName;
	}

	/**
	 * Gets the name of the pool (as c3p0 data source).
	 *
	 * @return the name
	 */
	public String getSourceName() {
		return this.sourceName;
	}
}
