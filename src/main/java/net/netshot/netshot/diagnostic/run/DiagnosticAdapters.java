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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.hibernate.Hibernate;

import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.inventory.device.Device;

/**
 * The registry of the diagnostic adapters, by type of diagnostic. It refuses to be built if
 * a type of diagnostic has no adapter.
 */
public final class DiagnosticAdapters {

	private final Map<Class<? extends Diagnostic>, DiagnosticAdapter<? extends Diagnostic>> adapters = new HashMap<>();

	/**
	 * Builds the registry.
	 *
	 * @param adapters the adapters, one per type of diagnostic
	 * @throws IllegalStateException if two adapters handle the same type, or if a type of diagnostic has no adapter
	 */
	public DiagnosticAdapters(List<DiagnosticAdapter<? extends Diagnostic>> adapters) {
		for (DiagnosticAdapter<? extends Diagnostic> adapter : adapters) {
			if (this.adapters.put(adapter.diagnosticType(), adapter) != null) {
				throw new IllegalStateException("Several adapters for the diagnostic type " + adapter.diagnosticType().getSimpleName());
			}
		}
		for (Class<? extends Diagnostic> diagnosticClass : Diagnostic.getDiagnosticClasses()) {
			if (!this.adapters.containsKey(diagnosticClass)) {
				throw new IllegalStateException("No adapter for the diagnostic type " + diagnosticClass.getSimpleName());
			}
		}
	}

	/**
	 * Builds the registry with the adapters of all the diagnostic types.
	 *
	 * @return the registry
	 */
	public static DiagnosticAdapters standard() {
		return new DiagnosticAdapters(List.of(
			new SimpleDiagnosticAdapter(),
			new JavaScriptDiagnosticAdapter(),
			new PythonDiagnosticAdapter()));
	}

	@SuppressWarnings("unchecked")
	private <D extends Diagnostic> DiagnosticAdapter<D> forDiagnostic(D diagnostic) {
		Class<?> diagnosticClass = diagnostic.getClass();
		DiagnosticAdapter<? extends Diagnostic> adapter = this.adapters.get(diagnosticClass);
		if (adapter == null) {
			throw new IllegalStateException("No adapter for the diagnostic type " + diagnosticClass.getSimpleName());
		}
		return (DiagnosticAdapter<D>) adapter;
	}

	/**
	 * Gets the object describing the diagnostic, to hand to the device script.
	 *
	 * @param diagnostic the diagnostic (a proxy is accepted)
	 * @param device the device
	 * @param context the script context
	 * @return the script object, or null if the diagnostic doesn't apply to this device
	 * @throws ScriptException if the diagnostic can't be prepared
	 * @throws IllegalStateException if the type of the diagnostic has no adapter
	 */
	public Value toJsObject(Diagnostic diagnostic, Device device, Context context) throws ScriptException {
		Diagnostic real = (Diagnostic) Hibernate.unproxy(diagnostic);
		return this.forDiagnostic(real).toJsObject(real, device, context);
	}

	/**
	 * Builds a diagnostic result.
	 *
	 * @param diagnostic the diagnostic (a proxy is accepted)
	 * @param device the device
	 * @param value the diagnostic result, as returned by the script
	 * @return the diagnostic result of the right type
	 * @throws IllegalStateException if the type of the diagnostic has no adapter
	 */
	public DiagnosticResult makeResult(Diagnostic diagnostic, Device device, Value value) {
		Diagnostic real = (Diagnostic) Hibernate.unproxy(diagnostic);
		return this.forDiagnostic(real).makeResult(real, device, value);
	}

}
