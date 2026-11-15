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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResultRepository;
import net.netshot.netshot.inventory.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestWiring;

/**
 * Tests of the diagnostic use cases, against a real database: error codes and their order,
 * the specific checks of each kind of diagnostic, update semantics, deletion with the results.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("Diagnostic service")
class DiagnosticServiceTest {

	private final AtomicInteger counter = new AtomicInteger();
	private DiagnosticService service;
	private Transactions transactions;
	private long groupId;
	private long otherGroupId;
	private long deviceId;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = TestWiring.get().transactions();
		this.service = new DiagnosticService(this.transactions, new DiagnosticRepository());
		this.groupId = TestDevices.addStaticGroup("Group").id();
		this.otherGroupId = TestDevices.addStaticGroup("Other group").id();
		Domain domain = TestDevices.addDomain("Lab", "Test domain");
		this.deviceId = TestDevices.add(Device.create("CiscoIOS12", "10.31.0.1", domain, "seed").toBuilder().name("router-a").build()).id();
	}

	private String name(String prefix) {
		return prefix + " " + this.counter.incrementAndGet();
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private static DiagnosticDefinition js(String name, long group, String script) {
		return new DiagnosticDefinition("JavaScriptDiagnostic", name, group, true, "TEXT", script, null, null, null, null, null);
	}

	private static DiagnosticDefinition python(String name, long group, String script) {
		return new DiagnosticDefinition("PythonDiagnostic", name, group, false, "NUMERIC", script, null, null, null, null, null);
	}

	private static DiagnosticDefinition simple(String name, long group, String cliMode, String command) {
		return new DiagnosticDefinition("SimpleDiagnostic", name, group, true, "LONGTEXT", null, "CiscoIOS12", cliMode, command, "(.*)",
			"$1");
	}

	private long countDiagnostics() {
		return this.transactions.read(dsl -> (long) dsl.fetchCount(Tables.DIAGNOSTIC));
	}

	private long countResults(long diagnosticId) {
		return this.transactions.read(dsl -> (long) dsl.fetchCount(Tables.DIAGNOSTIC_RESULT, Tables.DIAGNOSTIC_RESULT.DIAGNOSTIC.eq(diagnosticId)));
	}

	private long countLongTexts() {
		return this.transactions.read(dsl -> (long) dsl.fetchCount(Tables.LONG_TEXT_CONFIGURATION));
	}

	private void addLongTextResult(long diagnosticId) {
		this.transactions.run(dsl -> {
			Diagnostic diagnostic = new DiagnosticRepository().find(dsl, diagnosticId);
			new DiagnosticResultRepository().save(dsl, this.deviceId,
				List.of(DiagnosticLongTextResult.of(diagnostic.id(), diagnostic.name(), "long value")));
		});
	}

	private void addResult(long diagnosticId) {
		this.transactions.run(dsl -> {
			Diagnostic diagnostic = new DiagnosticRepository().find(dsl, diagnosticId);
			new DiagnosticResultRepository().save(dsl, this.deviceId,
				List.of(DiagnosticTextResult.of(diagnostic.id(), diagnostic.name(), "value")));
		});
	}

	private Diagnostic update(long id, DiagnosticDefinition definition) {
		return this.service.update(id, _ -> definition, null);
	}

	@Test
	@DisplayName("create() stores each kind of diagnostic with its properties, trims the name, and the result can be read once detached")
	void createKinds() {
		Diagnostic javaScript = this.service.create(js("  " + this.name("JS") + " ", this.groupId, "function diagnose() {}"));
		JavaScriptDiagnostic jsDiagnostic = Assertions.assertInstanceOf(JavaScriptDiagnostic.class, javaScript);
		Assertions.assertFalse(jsDiagnostic.name().startsWith(" "));
		Assertions.assertEquals("function diagnose() {}", jsDiagnostic.script());
		Assertions.assertEquals(AttributeType.TEXT, jsDiagnostic.resultType());
		Assertions.assertTrue(jsDiagnostic.enabled());
		Assertions.assertEquals(this.groupId, jsDiagnostic.targetGroupId());

		Diagnostic python = this.service.create(python(this.name("Python"), -1, "def diagnose(): pass"));
		PythonDiagnostic pythonDiagnostic = Assertions.assertInstanceOf(PythonDiagnostic.class, python);
		Assertions.assertFalse(pythonDiagnostic.enabled());
		Assertions.assertNull(pythonDiagnostic.targetGroupId(), "-1 is no group");
		Assertions.assertEquals(AttributeType.NUMERIC, pythonDiagnostic.resultType());

		Diagnostic simple = this.service.create(simple(this.name("Simple"), this.otherGroupId, "enable", "show clock"));
		SimpleDiagnostic simpleDiagnostic = Assertions.assertInstanceOf(SimpleDiagnostic.class, simple);
		Assertions.assertEquals("CiscoIOS12", simpleDiagnostic.deviceDriver());
		Assertions.assertEquals("enable", simpleDiagnostic.cliMode());
		Assertions.assertEquals("show clock", simpleDiagnostic.command());
		Assertions.assertEquals("(.*)", simpleDiagnostic.modifierPattern());
		Assertions.assertEquals("$1", simpleDiagnostic.modifierReplacement());
		Assertions.assertEquals("Cisco IOS and IOS-XE", TestDrivers.registry().getDriverByName(simpleDiagnostic.deviceDriver()).getDescription());

		Diagnostic read = this.service.get(simple.id());
		Assertions.assertEquals(this.otherGroupId, read.targetGroupId());
		Diagnostic listed = this.service.list(ListQuery.ALL).items().stream().filter(d -> d.id() == javaScript.id()).findFirst().orElseThrow();
		Assertions.assertEquals(this.groupId, listed.targetGroupId());
		Assertions.assertTrue(this.service.list(ListQuery.ALL).items().size() >= 3);
	}

	@Test
	@DisplayName("create() reports a blank or quoted name, then an invalid result type, then an invalid type (in this order)")
	void createCommonErrors() {
		long before = this.countDiagnostics();
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_NAME, () -> this.service.create(js("   ", -1, "x")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_NAME, () -> this.service.create(js("My \"diag\"", -1, "x")));
		DiagnosticDefinition badResult = new DiagnosticDefinition("NoSuchDiagnostic", "x", -1, true, "NOPE", null, null, null, null, null, null);
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.create(badResult));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.create(
			new DiagnosticDefinition("JavaScriptDiagnostic", this.name("No result type"), -1, true, null, "x", null, null, null, null, null)));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_TYPE, () -> this.service.create(
			new DiagnosticDefinition("NoSuchDiagnostic", this.name("Bad type"), -1, true, "TEXT", null, null, null, null, null, null)));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_TYPE, () -> this.service.create(
			new DiagnosticDefinition(null, this.name("No type"), -1, true, "TEXT", null, null, null, null, null, null)));
		Assertions.assertEquals(before, this.countDiagnostics(), "nothing was stored");
	}

	@Test
	@DisplayName("create() checks the script of a JavaScript or Python diagnostic")
	void createScriptErrors() {
		long before = this.countDiagnostics();
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.create(js(this.name("JS"), -1, null)));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.create(js(this.name("JS"), -1, "  ")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.create(python(this.name("Python"), -1, null)));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.create(python(this.name("Python"), -1, "")));
		Assertions.assertEquals(before, this.countDiagnostics());
	}

	@Test
	@DisplayName("create() checks the CLI mode, then the command, of a simple diagnostic")
	void createSimpleErrors() {
		long before = this.countDiagnostics();
		NetshotException noMode = assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC,
			() -> this.service.create(simple(this.name("Simple"), -1, null, null)));
		Assertions.assertEquals("The CLI mode must be provided.", noMode.getMessage());
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.create(simple(this.name("Simple"), -1, " ", "show clock")));
		NetshotException noCommand = assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC,
			() -> this.service.create(simple(this.name("Simple"), -1, "enable", " ")));
		Assertions.assertEquals("The command cannot be empty.", noCommand.getMessage());
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.create(simple(this.name("Simple"), -1, "enable", null)));
		Assertions.assertEquals(before, this.countDiagnostics());
	}

	@Test
	@DisplayName("create() refuses a duplicate name, and a group which doesn't exist")
	void createDuplicateAndUnknownGroup() {
		String name = this.name("Duplicate");
		this.service.create(js(name, -1, "x"));
		assertError(ErrorCode.NETSHOT_DUPLICATE_DIAGNOSTIC, () -> this.service.create(python(name, -1, "x")));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP, () -> this.service.create(js(this.name("Unknown group"), 99999999L, "x")));
		Diagnostic created = this.service.create(js(this.name("Known group"), -1, "x"));
		assertError(ErrorCode.NETSHOT_INVALID_GROUP, () -> this.update(created.id(), js(created.name(), 99999999L, "x")));
	}

	@Test
	@DisplayName("get() reports an unknown diagnostic")
	void getUnknown() {
		assertError(ErrorCode.NETSHOT_DIAGNOSTIC_NOT_FOUND, () -> this.service.get(99999999L));
	}

	@Test
	@DisplayName("update() replaces the properties of the kind of the diagnostic, trims the name")
	void updateKinds() {
		Diagnostic javaScript = this.service.create(js(this.name("JS to update"), this.groupId, "old"));
		Diagnostic updated = this.update(javaScript.id(),
			new DiagnosticDefinition("JavaScriptDiagnostic", " Renamed " + javaScript.id() + " ", -1, false, "BINARY", "new", "ignored",
				"ignored", "ignored", "ignored", "ignored"));
		JavaScriptDiagnostic jsDiagnostic = Assertions.assertInstanceOf(JavaScriptDiagnostic.class, updated);
		Assertions.assertEquals("Renamed " + javaScript.id(), jsDiagnostic.name());
		Assertions.assertEquals("new", jsDiagnostic.script());
		Assertions.assertFalse(jsDiagnostic.enabled());
		Assertions.assertEquals(AttributeType.BINARY, jsDiagnostic.resultType());
		Assertions.assertNull(jsDiagnostic.targetGroupId());

		Diagnostic python = this.service.create(python(this.name("Python to update"), -1, "old"));
		PythonDiagnostic pythonDiagnostic = Assertions.assertInstanceOf(PythonDiagnostic.class, this.update(python.id(),
			python(python.name(), this.groupId, "new python")));
		Assertions.assertEquals("new python", pythonDiagnostic.script());
		Assertions.assertEquals(this.groupId, pythonDiagnostic.targetGroupId());

		Diagnostic simple = this.service.create(simple(this.name("Simple to update"), -1, "enable", "show clock"));
		SimpleDiagnostic simpleDiagnostic = Assertions.assertInstanceOf(SimpleDiagnostic.class, this.update(simple.id(),
			new DiagnosticDefinition("SimpleDiagnostic", simple.name(), this.otherGroupId, true, "TEXT", "ignored", "Other", "exec",
				"show version", null, null)));
		Assertions.assertEquals("Other", simpleDiagnostic.deviceDriver());
		Assertions.assertEquals("exec", simpleDiagnostic.cliMode());
		Assertions.assertEquals("show version", simpleDiagnostic.command());
		Assertions.assertNull(simpleDiagnostic.modifierPattern());
		Assertions.assertEquals(this.otherGroupId, this.service.get(simple.id()).targetGroupId());
	}

	@Test
	@DisplayName("update() reports an unknown diagnostic before checking the properties, then the name, the result type, "
		+ "the kind, and the specific properties")
	void updateErrors() {
		assertError(ErrorCode.NETSHOT_DIAGNOSTIC_NOT_FOUND, () -> this.update(99999999L, js(" ", -1, "x")));
		Diagnostic javaScript = this.service.create(js(this.name("JS errors"), this.groupId, "script"));
		long id = javaScript.id();
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_NAME, () -> this.update(id, js(" ", -1, "x")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_NAME, () -> this.update(id, js("a\"b", -1, "x")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.update(id,
			new DiagnosticDefinition("JavaScriptDiagnostic", "x", -1, true, "NOPE", "x", null, null, null, null, null)));
		// The kind of the diagnostic can't change
		assertError(ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC, () -> this.update(id, python("x", -1, "x")));
		assertError(ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC, () -> this.update(id, simple("x", -1, "enable", "show")));
		assertError(ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC, () -> this.update(id,
			new DiagnosticDefinition("NoSuchDiagnostic", "x", -1, true, "TEXT", "x", null, null, null, null, null)));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.update(id, js("x", -1, "  ")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.update(id, js("x", -1, null)));

		Diagnostic python = this.service.create(python(this.name("Python errors"), -1, "script"));
		assertError(ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC, () -> this.update(python.id(), js("x", -1, "x")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.update(python.id(), python("x", -1, "")));

		Diagnostic simple = this.service.create(simple(this.name("Simple errors"), -1, "enable", "show clock"));
		assertError(ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC, () -> this.update(simple.id(), js("x", -1, "x")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.update(simple.id(), simple("x", -1, "", "show")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.update(simple.id(), simple("x", -1, "enable", null)));

		// None of the failed updates changed anything
		JavaScriptDiagnostic reread = Assertions.assertInstanceOf(JavaScriptDiagnostic.class, this.service.get(id));
		Assertions.assertEquals(javaScript.name(), reread.name());
		Assertions.assertEquals("script", reread.script());
		Assertions.assertEquals(this.groupId, reread.targetGroupId());
		Assertions.assertEquals(AttributeType.TEXT, reread.resultType());
		Assertions.assertEquals("show clock", Assertions.assertInstanceOf(SimpleDiagnostic.class, this.service.get(simple.id())).command());
	}

	@Test
	@DisplayName("update() refuses a name already used by another diagnostic")
	void updateDuplicate() {
		Diagnostic first = this.service.create(js(this.name("First"), -1, "x"));
		Diagnostic second = this.service.create(js(this.name("Second"), -1, "x"));
		assertError(ErrorCode.NETSHOT_DUPLICATE_DIAGNOSTIC, () -> this.update(second.id(), js(first.name(), -1, "x")));
		Assertions.assertEquals(second.name(), this.service.get(second.id()).name());
	}

	@Test
	@DisplayName("update() deletes the results when the target group changes, unless there was no group before")
	void updateGroupResults() {
		Diagnostic diagnostic = this.service.create(js(this.name("Results"), this.groupId, "x"));
		this.addResult(diagnostic.id());
		// Same group: the results stay
		this.update(diagnostic.id(), js(diagnostic.name(), this.groupId, "y"));
		Assertions.assertEquals(1, this.countResults(diagnostic.id()));
		// Other group: the results go
		this.update(diagnostic.id(), js(diagnostic.name(), this.otherGroupId, "y"));
		Assertions.assertEquals(0, this.countResults(diagnostic.id()));

		// Without group, the results stay when a group is set
		Diagnostic noGroup = this.service.create(js(this.name("No group"), -1, "x"));
		this.addResult(noGroup.id());
		this.update(noGroup.id(), js(noGroup.name(), this.groupId, "x"));
		Assertions.assertEquals(1, this.countResults(noGroup.id()));
		// Removing the group deletes them
		this.update(noGroup.id(), js(noGroup.name(), -1, "x"));
		Assertions.assertEquals(0, this.countResults(noGroup.id()));
	}

	@Test
	@DisplayName("a failed update() after the group change leaves the results alone")
	void updateFailureKeepsResults() {
		Diagnostic diagnostic = this.service.create(js(this.name("Failed"), this.groupId, "x"));
		this.addResult(diagnostic.id());
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.update(diagnostic.id(), js(diagnostic.name(), this.otherGroupId, " ")));
		Assertions.assertEquals(1, this.countResults(diagnostic.id()), "the deletion was rolled back");
		Assertions.assertEquals(this.groupId, this.service.get(diagnostic.id()).targetGroupId());
	}

	@Test
	@DisplayName("delete() removes the diagnostic with its results, and reports an unknown one")
	void delete() {
		Diagnostic diagnostic = this.service.create(simple(this.name("To delete"), this.groupId, "enable", "show clock"));
		this.addResult(diagnostic.id());
		Assertions.assertEquals(1, this.countResults(diagnostic.id()));
		this.service.delete(diagnostic.id(), null);
		assertError(ErrorCode.NETSHOT_DIAGNOSTIC_NOT_FOUND, () -> this.service.delete(diagnostic.id(), null));
		Assertions.assertEquals(0, this.countResults(diagnostic.id()));
		assertError(ErrorCode.NETSHOT_DIAGNOSTIC_NOT_FOUND, () -> this.service.get(diagnostic.id()));
		assertError(ErrorCode.NETSHOT_DIAGNOSTIC_NOT_FOUND, () -> this.service.delete(99999999L, null));
	}

	@Test
	@DisplayName("delete() and a target group change remove the long texts of the results")
	void deleteLongTextResults() {
		long longTexts = this.countLongTexts();
		Diagnostic diagnostic = this.service.create(simple(this.name("Long text"), this.groupId, "enable", "show run"));
		this.addLongTextResult(diagnostic.id());
		Assertions.assertEquals(longTexts + 1, this.countLongTexts());
		this.update(diagnostic.id(), simple(diagnostic.name(), this.otherGroupId, "enable", "show run"));
		Assertions.assertEquals(longTexts, this.countLongTexts(), "the long text of the result is deleted on a group change");

		this.addLongTextResult(diagnostic.id());
		Assertions.assertEquals(longTexts + 1, this.countLongTexts());
		this.service.delete(diagnostic.id(), null);
		Assertions.assertEquals(longTexts, this.countLongTexts(), "the long text of the result is deleted with the diagnostic");
	}

	@Test
	@DisplayName("listResults() returns the results of a device with their diagnostic, applies the page, and accepts an unknown device")
	void listResults() {
		Diagnostic first = this.service.create(js(this.name("Result 1"), -1, "x"));
		Diagnostic second = this.service.create(js(this.name("Result 2"), -1, "x"));
		this.addResult(first.id());
		this.addResult(second.id());
		List<DiagnosticResult> results = this.service.listResults(this.ofDevice(this.deviceId, Page.ALL)).items();
		Assertions.assertTrue(results.size() >= 2);
		DiagnosticResult result = results.stream().filter(r -> r.diagnosticId() == first.id()).findFirst().orElseThrow();
		Assertions.assertEquals(first.name(), result.diagnosticName(), "the diagnostic is loaded with the result");
		Assertions.assertEquals("value", Assertions.assertInstanceOf(DiagnosticTextResult.class, result).text());
		Assertions.assertEquals(1, this.service.listResults(this.ofDevice(this.deviceId, new Page(0, 1))).items().size());
		Assertions.assertTrue(this.service.listResults(this.ofDevice(99999999L, Page.ALL)).items().isEmpty());
	}

	private ListQuery ofDevice(long device, Page page) {
		return new ListQuery(List.of(new Criterion("device", Lookup.EXACT, List.of(device))), null, List.of(), page, false);
	}
}
