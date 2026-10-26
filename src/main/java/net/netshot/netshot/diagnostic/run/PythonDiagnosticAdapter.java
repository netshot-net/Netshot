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
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import net.netshot.netshot.diagnostic.PythonDiagnostic;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;
import net.netshot.netshot.platform.script.ScriptToolbox;

/**
 * Adapts the Python diagnostics.
 */
public class PythonDiagnosticAdapter implements DiagnosticAdapter<PythonDiagnostic> {

	@Override
	public Class<PythonDiagnostic> diagnosticType() {
		return PythonDiagnostic.class;
	}

	@Override
	public Value toJsObject(PythonDiagnostic diagnostic, Device device, Context context) throws ScriptException {
		Source diagSource = Source.newBuilder("python", diagnostic.script(),
			"_diag%d.py".formatted(diagnostic.id())).buildLiteral();
		ScriptToolbox.loadPython(context);
		context.eval(diagSource);
		Value diagnose = context.getBindings("python").getMember("diagnose");
		if (!diagnose.canExecute()) {
			throw new ScriptException(String.format("Unable to find 'diagnose' function in '%s' Python diagnostic", diagnostic.name()));
		}
		return diagnose;
	}

	@Override
	public DiagnosticResult makeResult(PythonDiagnostic diagnostic, Value value) {
		return DiagnosticResults.make(diagnostic, value);
	}

}
