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

import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Transactions;

/**
 * The composition root: builds the long-lived objects of the application
 * (transaction support, then the repositories and services) and holds them.
 *
 * <p>{@link #init()} initializes the database access and builds the wiring;
 * it must be called once the database schema is up to date, and before
 * anything that uses a service (the REST application, the task manager...).
 * It is the single entry point for the server startup as well as for the
 * tests which need a working database.</p>
 *
 * <p>The REST resources receive the objects of the wiring through dependency
 * injection (the web application registers them into Jersey). Code which is
 * not created by an injection container (Quartz jobs, cluster code, entities)
 * can only reach them through {@link #get()}.</p>
 */
public final class Wiring {

	private static volatile Wiring instance;

	private final Transactions transactions;

	private Wiring(Transactions transactions) {
		this.transactions = transactions;
	}

	/**
	 * Initializes the database access and builds the wiring.
	 *
	 * @return the new wiring
	 */
	public static synchronized Wiring init() {
		Database.init(EntityModel.build());
		instance = new Wiring(new Transactions());
		return instance;
	}

	/**
	 * Gets the wiring, for the code which cannot receive its dependencies by injection.
	 *
	 * <p><b>Transitional:</b> a static lookup is a service locator, which hides the dependencies
	 * of its callers. It only exists for the code not yet created by an injection
	 * container (Quartz jobs, cluster code), and is meant to disappear as such code gets
	 * its dependencies explicitly. Do not use it from new code which can be injected.</p>
	 *
	 * @return the wiring
	 * @throws IllegalStateException if {@link #init()} has not been called yet
	 */
	public static Wiring get() {
		Wiring wiring = instance;
		if (wiring == null) {
			throw new IllegalStateException("The wiring has not been initialized");
		}
		return wiring;
	}

	/**
	 * Gets the transaction support.
	 *
	 * @return the transactions
	 */
	public Transactions transactions() {
		return this.transactions;
	}
}
