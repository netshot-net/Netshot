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
package net.netshot.netshot.testing;

import net.netshot.netshot.boot.Wiring;

/**
 * The wiring built for the database tests (by {@link DatabaseExtension}), for the tests which use the
 * objects it holds. The main code has no such lookup: it receives its dependencies from the wiring.
 */
public final class TestWiring {

	private static volatile Wiring current;

	private TestWiring() {
	}

	/**
	 * Sets the wiring of the tests.
	 *
	 * @param wiring the wiring
	 * @return the wiring
	 */
	public static Wiring set(Wiring wiring) {
		current = wiring;
		return wiring;
	}

	/**
	 * Gets the wiring of the tests.
	 *
	 * @return the wiring
	 * @throws IllegalStateException if no wiring has been built
	 */
	public static Wiring get() {
		Wiring wiring = current;
		if (wiring == null) {
			throw new IllegalStateException("The wiring has not been initialized");
		}
		return wiring;
	}
}
