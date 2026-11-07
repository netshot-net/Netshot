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
package net.netshot.netshot;

import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.attribute.AttributeDefinition.AttributeType;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class SimpleDiagnosticTest {

	@Test
	@DisplayName("A modifier without replacement removes the matched text")
	void modifierWithoutReplacement() {
		Device device = FakeDeviceFactory.getFakeCiscoIosDevice();
		SimpleDiagnostic diagnostic = new SimpleDiagnostic("Simple", true, null, AttributeType.TEXT,
			"CiscoIOS12", "exec", "show something", "^value=", null);
		DiagnosticResult result = diagnostic.makeResult(device, Value.asValue("value=42"));
		Assertions.assertInstanceOf(DiagnosticTextResult.class, result);
		Assertions.assertEquals("42", ((DiagnosticTextResult) result).getText());
	}

}
