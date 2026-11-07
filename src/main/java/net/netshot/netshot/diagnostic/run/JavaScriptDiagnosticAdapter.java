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
package net.netshot.netshot.diagnostic.run;

import javax.script.ScriptException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import net.netshot.netshot.diagnostic.JavaScriptDiagnostic;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;

/**
 * Adapts the JavaScript diagnostics.
 */
public class JavaScriptDiagnosticAdapter implements DiagnosticAdapter<JavaScriptDiagnostic> {

	@Override
	public Class<JavaScriptDiagnostic> diagnosticType() {
		return JavaScriptDiagnostic.class;
	}

	@Override
	public Value toJsObject(JavaScriptDiagnostic diagnostic, Device device, Context context) throws ScriptException {
		Source diagSource = Source.newBuilder("js", diagnostic.script(),
			"_diag%d.js".formatted(diagnostic.id())).buildLiteral();
		context.eval(diagSource);
		Value diagnose = context.getBindings("js").getMember("diagnose");
		if (!diagnose.canExecute()) {
			throw new ScriptException(String.format("Unable to find 'diagnose' function in '%s' JS diagnostic", diagnostic.name()));
		}
		return diagnose;
	}

	@Override
	public DiagnosticResult makeResult(JavaScriptDiagnostic diagnostic, Value value) {
		return DiagnosticResults.make(diagnostic, value);
	}

}
