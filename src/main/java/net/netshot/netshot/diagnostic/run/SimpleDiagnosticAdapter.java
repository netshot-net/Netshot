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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import net.netshot.netshot.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.diagnostic.SimpleDiagnostic.JsSimpleDiagnostic;
import net.netshot.netshot.inventory.device.Device;

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
		if (!device.getDriver().equals(diagnostic.getDeviceDriver())) {
			return null;
		}
		return Value.asValue(new JsSimpleDiagnostic(diagnostic.getCliMode(), diagnostic.getCommand()));
	}

	@Override
	public DiagnosticResult makeResult(SimpleDiagnostic diagnostic, Device device, Value value) {
		String newValue = value.asString();
		if (diagnostic.getModifierPattern() != null) {
			newValue = newValue.replaceAll(diagnostic.getModifierPattern(), diagnostic.getModifierReplacement());
		}
		switch (diagnostic.getResultType()) {
			case LONGTEXT:
				return new DiagnosticLongTextResult(device, diagnostic, newValue);
			case TEXT:
				return new DiagnosticTextResult(device, diagnostic, newValue);
			case NUMERIC:
				return new DiagnosticNumericResult(device, diagnostic, Double.parseDouble(newValue));
			case BINARY:
				boolean booleanValue = !"".equals(newValue) && !"false".equals(newValue.toLowerCase()) && !"no".equals(newValue.toLowerCase());
				return new DiagnosticBinaryResult(device, diagnostic, booleanValue);
			default:
				return null;
		}
	}

}
