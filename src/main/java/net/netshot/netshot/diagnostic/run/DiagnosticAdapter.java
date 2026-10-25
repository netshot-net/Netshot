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
package net.netshot.netshot.diagnostic.run;

import javax.script.ScriptException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;

/**
 * Adapts the diagnostics of one type to the device script which runs them: how a diagnostic
 * is handed to the script, and how the value it returns becomes a diagnostic result.
 *
 * @param <D> the type of diagnostic adapted
 */
public interface DiagnosticAdapter<D extends Diagnostic> {

	/**
	 * Gets the type of diagnostic this adapter handles.
	 *
	 * @return the diagnostic type
	 */
	Class<D> diagnosticType();

	/**
	 * Gets the object describing the diagnostic, to hand to the device script.
	 *
	 * @param diagnostic the diagnostic
	 * @param device the device
	 * @param context the script context
	 * @return the script object, or null if the diagnostic doesn't apply to this device
	 * @throws ScriptException if the diagnostic can't be prepared
	 */
	Value toJsObject(D diagnostic, Device device, Context context) throws ScriptException;

	/**
	 * Builds a diagnostic result.
	 *
	 * @param diagnostic the diagnostic
	 * @param value the diagnostic result, as returned by the script
	 * @return the diagnostic result of the right type
	 */
	DiagnosticResult makeResult(D diagnostic, Value value);

}
