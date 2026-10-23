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
package net.netshot.netshot.boot;

import java.util.List;

import net.netshot.netshot.platform.database.DatabaseModel;

/**
 * The persistent model of the application, as Hibernate knows it: no entity is left (all the
 * data is read and written with jOOQ), Hibernate only provides the sessions (connections and
 * transactions) until it is removed.
 */
public final class EntityModel {

	/**
	 * Builds the persistent model: no entity, no attribute converter.
	 *
	 * @return the model, to be given to {@code Database.init()}
	 */
	public static DatabaseModel build() {
		return new DatabaseModel(List.of(), List.of());
	}

	private EntityModel() {
	}
}
