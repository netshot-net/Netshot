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
package net.netshot.netshot.diagnostic;

import net.netshot.netshot.driver.AttributeDefinition.AttributeType;

/**
 * A diagnostic: a command or a script run on the devices (of a group), whose result is stored
 * with the device.
 */
public sealed interface Diagnostic permits SimpleDiagnostic, JavaScriptDiagnostic, PythonDiagnostic {

	/**
	 * Gets the ID.
	 *
	 * @return the ID (0 for a diagnostic not stored yet)
	 */
	long id();

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * Tells whether the diagnostic is run.
	 *
	 * @return true if enabled
	 */
	boolean enabled();

	/**
	 * Gets the ID of the group of devices the diagnostic is run on.
	 *
	 * @return the group ID, or null for no group
	 */
	Long targetGroupId();

	/**
	 * Gets the type of result.
	 *
	 * @return the type of result
	 */
	AttributeType resultType();

	/**
	 * Gets a copy of the diagnostic with another ID.
	 *
	 * @param id the ID
	 * @return the diagnostic
	 */
	Diagnostic withId(long id);

	/**
	 * Describes the diagnostic, for the logs.
	 *
	 * @param diagnostic the diagnostic
	 * @return the description
	 */
	static String describe(Diagnostic diagnostic) {
		return "Diagnostic " + diagnostic.id() + " (name '" + diagnostic.name() + "', type "
			+ diagnostic.getClass().getSimpleName() + ")";
	}
}
