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
package net.netshot.netshot.database;

public enum TenantIdentifier {
	READ_ONLY("ReadOnly"),
	READ_WRITE("ReadWrite"),
	/** Small dedicated pool for task dispatch and cluster bookkeeping (see Database.getSystemSession). */
	SYSTEM("System");

	private final String sourceName;

	TenantIdentifier(String sourceName) {
		this.sourceName = sourceName;
	}

	public String getSourceName() {
		return this.sourceName;
	}
}
