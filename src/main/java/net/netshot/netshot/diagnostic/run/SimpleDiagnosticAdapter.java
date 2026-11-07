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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.diagnostic.SimpleDiagnostic.JsSimpleDiagnostic;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticTextResult;

/**
 * Adapts the simple (CLI command) diagnostics.
 */
public class SimpleDiagnosticAdapter implements DiagnosticAdapter<SimpleDiagnostic> {

	@Override
	public Class<SimpleDiagnostic> diagnosticType() {
		return SimpleDiagnostic.class;
	}

	@Override
	public Value toJsObject(SimpleDiagnostic diagnostic, Device device, Context context) {
		if (!device.driver().equals(diagnostic.deviceDriver())) {
			return null;
		}
		return Value.asValue(new JsSimpleDiagnostic(diagnostic.cliMode(), diagnostic.command()));
	}

	@Override
	public DiagnosticResult makeResult(SimpleDiagnostic diagnostic, Value value) {
		String newValue = value.asString();
		if (diagnostic.modifierPattern() != null) {
			// No replacement (possible through the v0 API) removes the matched text
			String replacement = diagnostic.modifierReplacement() == null ? "" : diagnostic.modifierReplacement();
			newValue = newValue.replaceAll(diagnostic.modifierPattern(), replacement);
		}
		switch (diagnostic.resultType()) {
			case LONGTEXT:
				return DiagnosticLongTextResult.of(diagnostic.id(), diagnostic.name(), newValue);
			case TEXT:
				return DiagnosticTextResult.of(diagnostic.id(), diagnostic.name(), newValue);
			case NUMERIC:
				return DiagnosticNumericResult.of(diagnostic.id(), diagnostic.name(), Double.parseDouble(newValue));
			case BINARY:
				boolean booleanValue = !"".equals(newValue) && !"false".equals(newValue.toLowerCase()) && !"no".equals(newValue.toLowerCase());
				return DiagnosticBinaryResult.of(diagnostic.id(), diagnostic.name(), booleanValue);
			default:
				return null;
		}
	}

}
