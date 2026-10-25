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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.JavaScriptDiagnostic;
import net.netshot.netshot.diagnostic.PythonDiagnostic;
import net.netshot.netshot.diagnostic.SimpleDiagnostic;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.testing.ScriptEngineTest;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * Tests of the registry of the diagnostic adapters.
 */
@ScriptEngineTest
public class DiagnosticAdaptersTest {

	@SuppressWarnings("unchecked")
	private static Set<Class<? extends Diagnostic>> concreteDiagnosticClasses() {
		Set<Class<? extends Diagnostic>> found = new HashSet<>();
		for (JavaClass javaClass : new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("net.netshot.netshot")) {
			if (javaClass.isAssignableTo(Diagnostic.class) && !javaClass.isEquivalentTo(Diagnostic.class)
				&& !javaClass.getModifiers().contains(JavaModifier.ABSTRACT)) {
				found.add((Class<? extends Diagnostic>) javaClass.reflect());
			}
		}
		return found;
	}

	@Test
	@DisplayName("Every concrete Diagnostic subclass has an adapter")
	void everyDiagnosticHasAnAdapter() throws Exception {
		Set<Class<? extends Diagnostic>> diagnosticClasses = concreteDiagnosticClasses();
		Assertions.assertFalse(diagnosticClasses.isEmpty(), "No diagnostic class found, the scan is broken");
		Assertions.assertEquals(Set.of(Diagnostic.class.getPermittedSubclasses()), new HashSet<Class<?>>(diagnosticClasses),
			"Diagnostic doesn't permit all the diagnostic types");
		DiagnosticAdapters adapters = DiagnosticAdapters.standard();
		for (Diagnostic diagnostic : List.<Diagnostic>of(
				new SimpleDiagnostic(0, "Simple", true, null, AttributeType.TEXT, null, null, null, null, null),
				new JavaScriptDiagnostic(0, "JS", true, null, AttributeType.TEXT, null),
				new PythonDiagnostic(0, "Python", true, null, AttributeType.TEXT, null))) {
			// Reaching the adapter is enough: no registered adapter raises an IllegalStateException
			try {
				adapters.makeResult(diagnostic, Value.asValue("x"));
			}
			catch (IllegalStateException e) {
				Assertions.fail("No adapter for " + diagnostic.getClass().getSimpleName(), e);
			}
			catch (RuntimeException e) {
				// The adapter was reached, and the incomplete diagnostic made it fail: fine
			}
		}
	}

	@Test
	@DisplayName("The registry refuses to be built without the adapter of a diagnostic type")
	void missingAdapter() {
		Assertions.assertThrows(IllegalStateException.class,
			() -> new DiagnosticAdapters(List.of(new SimpleDiagnosticAdapter())));
	}

	@Test
	@DisplayName("Simple diagnostic: not handed to the script for another driver, modifier applied to the result")
	void simpleDiagnostic() throws Exception {
		SimpleDiagnostic diagnostic = new SimpleDiagnostic(0, "Simple", true, null, AttributeType.NUMERIC,
			"CiscoIOS12", "exec", "show something", "^value=(\\d+)$", "$1");
		DiagnosticAdapters adapters = DiagnosticAdapters.standard();
		Device device = FakeDeviceFactory.getFakeCiscoIosDevice();
		Assertions.assertNotNull(adapters.toJsObject(diagnostic, device, null));
		SimpleDiagnostic other = new SimpleDiagnostic(0, "Simple", true, null, AttributeType.NUMERIC,
			"OtherDriver", "exec", "show something", "^value=(\\d+)$", "$1");
		Assertions.assertNull(adapters.toJsObject(other, device, null));

		DiagnosticResult result = adapters.makeResult(diagnostic, Value.asValue("value=42"));
		Assertions.assertInstanceOf(DiagnosticNumericResult.class, result);
		Assertions.assertEquals(42.0, ((DiagnosticNumericResult) result).number());

		SimpleDiagnostic text = new SimpleDiagnostic(0, "Simple", true, null, AttributeType.TEXT,
			"CiscoIOS12", "exec", "show something", "^value=(\\d+)$", "$1");
		result = adapters.makeResult(text, Value.asValue("value=42"));
		Assertions.assertInstanceOf(DiagnosticTextResult.class, result);
		Assertions.assertEquals("42", ((DiagnosticTextResult) result).text());
	}

}
