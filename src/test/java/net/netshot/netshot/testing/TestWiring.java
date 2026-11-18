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
package net.netshot.netshot.testing;

import net.netshot.netshot.boot.Application;
import net.netshot.netshot.boot.Wiring;

/**
 * The application built for the database tests (by {@link DatabaseExtension}), for the tests which use the
 * objects of its wiring. The main code has no such lookup: it receives its dependencies from the wiring.
 */
public final class TestWiring {

	private static volatile Application current;

	private TestWiring() {
	}

	/**
	 * Sets the application of the tests, closing the previous one (its services, e.g. the connection pools,
	 * point to the database of a previous test class).
	 *
	 * @param application the application
	 * @return the application
	 */
	public static synchronized Application set(Application application) {
		Application previous = current;
		if (previous != null && previous != application) {
			previous.close();
		}
		current = application;
		return application;
	}

	/**
	 * Closes the application of the tests (at the end of a test class: its services, e.g. the connection pools and
	 * the configuration listeners, mustn't outlive the class).
	 */
	public static synchronized void close() {
		Application previous = current;
		current = null;
		if (previous != null) {
			previous.close();
		}
	}

	/**
	 * Gets the application of the tests.
	 *
	 * @return the application
	 * @throws IllegalStateException if no application has been built
	 */
	public static Application application() {
		Application application = current;
		if (application == null) {
			throw new IllegalStateException("The application has not been initialized");
		}
		return application;
	}

	/**
	 * Gets the wiring of the application of the tests.
	 *
	 * @return the wiring
	 * @throws IllegalStateException if no application has been built
	 */
	public static Wiring get() {
		return application().wiring();
	}
}
