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
package net.netshot.netshot.diagnostic;

/**
 * The properties of a diagnostic to create, or to replace on an existing diagnostic. Only the
 * components of the kind of diagnostic are used: the script for a JavaScript or Python one, the
 * driver, mode, command and modifier for a simple one.
 *
 * @param type the kind of diagnostic: SimpleDiagnostic, JavaScriptDiagnostic or PythonDiagnostic
 * @param name the name (must not be null)
 * @param targetGroup the ID of the group of devices the diagnostic applies to, -1 for none
 * @param enabled whether the diagnostic is enabled
 * @param resultType the name of the type of result
 * @param script the script (JavaScript or Python diagnostic)
 * @param deviceDriver the name of the device driver (simple diagnostic)
 * @param cliMode the CLI mode to run the command in (simple diagnostic)
 * @param command the CLI command (simple diagnostic)
 * @param modifierPattern the pattern to search in the result (simple diagnostic)
 * @param modifierReplacement the replacement of the pattern (simple diagnostic)
 */
public record DiagnosticDefinition(
	String type,
	String name,
	long targetGroup,
	boolean enabled,
	String resultType,
	String script,
	String deviceDriver,
	String cliMode,
	String command,
	String modifierPattern,
	String modifierReplacement) {
}
