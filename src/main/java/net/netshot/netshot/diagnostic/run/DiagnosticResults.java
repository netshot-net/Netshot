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

import org.graalvm.polyglot.Value;

import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.inventory.device.Device;

/**
 * Builds the diagnostic results from the values returned by the scripts.
 */
final class DiagnosticResults {

	private DiagnosticResults() {
	}

	/**
	 * Build a diagnostic result.
	 * @param diagnostic the diagnostic
	 * @param device the device
	 * @param value the diagnostic result
	 * @return the diagnostic result of the right type
	 */
	static DiagnosticResult make(Diagnostic diagnostic, Device device, Value value) {
		switch (diagnostic.getResultType()) {
			case LONGTEXT:
				return new DiagnosticLongTextResult(device, diagnostic, value.asString());
			case TEXT:
				return new DiagnosticTextResult(device, diagnostic, value.asString());
			case NUMERIC:
				return new DiagnosticNumericResult(device, diagnostic, value.asDouble());
			case BINARY:
				return new DiagnosticBinaryResult(device, diagnostic, value.asBoolean());
			default:
				return null;
		}
	}

}
