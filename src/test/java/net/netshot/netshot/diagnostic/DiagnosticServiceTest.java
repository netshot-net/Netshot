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

import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.testing.DatabaseTest;

/**
 * Tests of the diagnostic use cases, against a real database: error codes and their order,
 * the specific checks of each kind of diagnostic, update semantics, deletion with the results.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
class DiagnosticServiceTest {

	private final AtomicInteger counter = new AtomicInteger();
	private DiagnosticService service;
	private Transactions transactions;
	private long groupId;
	private long otherGroupId;
	private long deviceId;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = new Transactions();
		this.service = new DiagnosticService(this.transactions, new DiagnosticRepository());
		this.groupId = this.transactions.write(session -> {
			StaticDeviceGroup group = new StaticDeviceGroup("Group");
			session.persist(group);
			return group.getId();
		});
		this.otherGroupId = this.transactions.write(session -> {
			StaticDeviceGroup group = new StaticDeviceGroup("Other group");
			session.persist(group);
			return group.getId();
		});
		this.deviceId = this.transactions.write(session -> {
			Domain domain = new Domain("Lab", "Test domain", null, null);
			session.persist(domain);
			Device device = new Device("CiscoIOS12", "10.31.0.1", domain, "seed");
			device.setName("router-a");
			session.persist(device);
			return device.getId();
		});
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
		return this.transactions.read(session -> session.createQuery("select count(d) from Diagnostic d", Long.class).uniqueResult());
	}

	private long countResults(long diagnosticId) {
		return this.transactions.read(session -> session
			.createQuery("select count(dr) from DiagnosticResult dr where dr.diagnostic.id = :id", Long.class)
			.setParameter("id", diagnosticId).uniqueResult());
	}

	private void addResult(long diagnosticId) {
		this.transactions.run(session -> session.persist(new DiagnosticTextResult(session.get(Device.class, this.deviceId),
			session.get(Diagnostic.class, diagnosticId), "value")));
	}

	@Test
	@DisplayName("create() stores each kind of diagnostic with its properties, trims the name, and the result can be read once detached")
	void createKinds() {
		Diagnostic javaScript = this.service.create(js("  " + this.name("JS") + " ", this.groupId, "function diagnose() {}"));
		JavaScriptDiagnostic jsDiagnostic = Assertions.assertInstanceOf(JavaScriptDiagnostic.class, javaScript);
		Assertions.assertFalse(jsDiagnostic.getName().startsWith(" "));
		Assertions.assertEquals("function diagnose() {}", jsDiagnostic.getScript());
		Assertions.assertEquals(AttributeType.TEXT, jsDiagnostic.getResultType());
		Assertions.assertTrue(jsDiagnostic.isEnabled());
		Assertions.assertEquals(this.groupId, jsDiagnostic.getTargetGroup().getId());

		Diagnostic python = this.service.create(python(this.name("Python"), -1, "def diagnose(): pass"));
		PythonDiagnostic pythonDiagnostic = Assertions.assertInstanceOf(PythonDiagnostic.class, python);
		Assertions.assertFalse(pythonDiagnostic.isEnabled());
		Assertions.assertNull(pythonDiagnostic.getTargetGroup(), "-1 is no group");
		Assertions.assertEquals(AttributeType.NUMERIC, pythonDiagnostic.getResultType());

		Diagnostic simple = this.service.create(simple(this.name("Simple"), this.otherGroupId, "enable", "show clock"));
		SimpleDiagnostic simpleDiagnostic = Assertions.assertInstanceOf(SimpleDiagnostic.class, simple);
		Assertions.assertEquals("CiscoIOS12", simpleDiagnostic.getDeviceDriver());
		Assertions.assertEquals("enable", simpleDiagnostic.getCliMode());
		Assertions.assertEquals("show clock", simpleDiagnostic.getCommand());
		Assertions.assertEquals("(.*)", simpleDiagnostic.getModifierPattern());
		Assertions.assertEquals("$1", simpleDiagnostic.getModifierReplacement());
		Assertions.assertEquals("Cisco IOS and IOS-XE", TestDrivers.registry().getDriverByName(simpleDiagnostic.getDeviceDriver()).getDescription());

		Diagnostic read = this.service.get(simple.getId());
		Assertions.assertEquals(this.otherGroupId, read.getTargetGroup().getId(), "the group is loaded by get()");
		Diagnostic listed = this.service.list(Page.ALL).stream().filter(d -> d.getId() == javaScript.getId()).findFirst().orElseThrow();
		Assertions.assertEquals("Group", listed.getTargetGroup().getName(), "the group is loaded by list()");
		Assertions.assertTrue(this.service.list(Page.ALL).size() >= 3);
		Assertions.assertEquals(1, this.service.list(new Page(0, 1)).size());
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
		assertError(ErrorCode.NETSHOT_INVALID_GROUP, () -> this.service.update(created.getId(), js(created.getName(), 99999999L, "x")));
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
		Diagnostic updated = this.service.update(javaScript.getId(),
			new DiagnosticDefinition("JavaScriptDiagnostic", " Renamed " + javaScript.getId() + " ", -1, false, "BINARY", "new", "ignored",
				"ignored", "ignored", "ignored", "ignored"));
		JavaScriptDiagnostic jsDiagnostic = Assertions.assertInstanceOf(JavaScriptDiagnostic.class, updated);
		Assertions.assertEquals("Renamed " + javaScript.getId(), jsDiagnostic.getName());
		Assertions.assertEquals("new", jsDiagnostic.getScript());
		Assertions.assertFalse(jsDiagnostic.isEnabled());
		Assertions.assertEquals(AttributeType.BINARY, jsDiagnostic.getResultType());
		Assertions.assertNull(jsDiagnostic.getTargetGroup());

		Diagnostic python = this.service.create(python(this.name("Python to update"), -1, "old"));
		PythonDiagnostic pythonDiagnostic = Assertions.assertInstanceOf(PythonDiagnostic.class, this.service.update(python.getId(),
			python(python.getName(), this.groupId, "new python")));
		Assertions.assertEquals("new python", pythonDiagnostic.getScript());
		Assertions.assertEquals(this.groupId, pythonDiagnostic.getTargetGroup().getId());

		Diagnostic simple = this.service.create(simple(this.name("Simple to update"), -1, "enable", "show clock"));
		SimpleDiagnostic simpleDiagnostic = Assertions.assertInstanceOf(SimpleDiagnostic.class, this.service.update(simple.getId(),
			new DiagnosticDefinition("SimpleDiagnostic", simple.getName(), this.otherGroupId, true, "TEXT", "ignored", "Other", "exec",
				"show version", null, null)));
		Assertions.assertEquals("Other", simpleDiagnostic.getDeviceDriver());
		Assertions.assertEquals("exec", simpleDiagnostic.getCliMode());
		Assertions.assertEquals("show version", simpleDiagnostic.getCommand());
		Assertions.assertNull(simpleDiagnostic.getModifierPattern());
		Assertions.assertEquals(this.otherGroupId, this.service.get(simple.getId()).getTargetGroup().getId());
	}

	@Test
	@DisplayName("update() reports an unknown diagnostic before checking the properties, then the name, the result type, "
		+ "the kind, and the specific properties")
	void updateErrors() {
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.update(99999999L, js(" ", -1, "x")));
		Diagnostic javaScript = this.service.create(js(this.name("JS errors"), this.groupId, "script"));
		long id = javaScript.getId();
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_NAME, () -> this.service.update(id, js(" ", -1, "x")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC_NAME, () -> this.service.update(id, js("a\"b", -1, "x")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.update(id,
			new DiagnosticDefinition("JavaScriptDiagnostic", "x", -1, true, "NOPE", "x", null, null, null, null, null)));
		// The kind of the diagnostic can't change
		assertError(ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC, () -> this.service.update(id, python("x", -1, "x")));
		assertError(ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC, () -> this.service.update(id, simple("x", -1, "enable", "show")));
		assertError(ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC, () -> this.service.update(id,
			new DiagnosticDefinition("NoSuchDiagnostic", "x", -1, true, "TEXT", "x", null, null, null, null, null)));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.update(id, js("x", -1, "  ")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.update(id, js("x", -1, null)));

		Diagnostic python = this.service.create(python(this.name("Python errors"), -1, "script"));
		assertError(ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC, () -> this.service.update(python.getId(), js("x", -1, "x")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.update(python.getId(), python("x", -1, "")));

		Diagnostic simple = this.service.create(simple(this.name("Simple errors"), -1, "enable", "show clock"));
		assertError(ErrorCode.NETSHOT_INCOMPATIBLE_DIAGNOSTIC, () -> this.service.update(simple.getId(), js("x", -1, "x")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.update(simple.getId(), simple("x", -1, "", "show")));
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.update(simple.getId(), simple("x", -1, "enable", null)));

		// None of the failed updates changed anything
		JavaScriptDiagnostic reread = Assertions.assertInstanceOf(JavaScriptDiagnostic.class, this.service.get(id));
		Assertions.assertEquals(javaScript.getName(), reread.getName());
		Assertions.assertEquals("script", reread.getScript());
		Assertions.assertEquals(this.groupId, reread.getTargetGroup().getId());
		Assertions.assertEquals(AttributeType.TEXT, reread.getResultType());
		Assertions.assertEquals("show clock", Assertions.assertInstanceOf(SimpleDiagnostic.class, this.service.get(simple.getId())).getCommand());
	}

	@Test
	@DisplayName("update() refuses a name already used by another diagnostic")
	void updateDuplicate() {
		Diagnostic first = this.service.create(js(this.name("First"), -1, "x"));
		Diagnostic second = this.service.create(js(this.name("Second"), -1, "x"));
		assertError(ErrorCode.NETSHOT_DUPLICATE_DIAGNOSTIC, () -> this.service.update(second.getId(), js(first.getName(), -1, "x")));
		Assertions.assertEquals(second.getName(), this.service.get(second.getId()).getName());
	}

	@Test
	@DisplayName("update() deletes the results when the target group changes, unless there was no group before")
	void updateGroupResults() {
		Diagnostic diagnostic = this.service.create(js(this.name("Results"), this.groupId, "x"));
		this.addResult(diagnostic.getId());
		// Same group: the results stay
		this.service.update(diagnostic.getId(), js(diagnostic.getName(), this.groupId, "y"));
		Assertions.assertEquals(1, this.countResults(diagnostic.getId()));
		// Other group: the results go
		this.service.update(diagnostic.getId(), js(diagnostic.getName(), this.otherGroupId, "y"));
		Assertions.assertEquals(0, this.countResults(diagnostic.getId()));

		// Without group, the results stay when a group is set
		Diagnostic noGroup = this.service.create(js(this.name("No group"), -1, "x"));
		this.addResult(noGroup.getId());
		this.service.update(noGroup.getId(), js(noGroup.getName(), this.groupId, "x"));
		Assertions.assertEquals(1, this.countResults(noGroup.getId()));
		// Removing the group deletes them
		this.service.update(noGroup.getId(), js(noGroup.getName(), -1, "x"));
		Assertions.assertEquals(0, this.countResults(noGroup.getId()));
	}

	@Test
	@DisplayName("a failed update() after the group change leaves the results alone")
	void updateFailureKeepsResults() {
		Diagnostic diagnostic = this.service.create(js(this.name("Failed"), this.groupId, "x"));
		this.addResult(diagnostic.getId());
		assertError(ErrorCode.NETSHOT_INVALID_DIAGNOSTIC, () -> this.service.update(diagnostic.getId(), js(diagnostic.getName(), this.otherGroupId, " ")));
		Assertions.assertEquals(1, this.countResults(diagnostic.getId()), "the deletion was rolled back");
		Assertions.assertEquals(this.groupId, this.service.get(diagnostic.getId()).getTargetGroup().getId());
	}

	@Test
	@DisplayName("delete() tells whether the diagnostic existed, and removes its results")
	void delete() {
		Diagnostic diagnostic = this.service.create(simple(this.name("To delete"), this.groupId, "enable", "show clock"));
		this.addResult(diagnostic.getId());
		Assertions.assertEquals(1, this.countResults(diagnostic.getId()));
		Assertions.assertTrue(this.service.delete(diagnostic.getId()));
		Assertions.assertFalse(this.service.delete(diagnostic.getId()));
		Assertions.assertEquals(0, this.countResults(diagnostic.getId()));
		assertError(ErrorCode.NETSHOT_DIAGNOSTIC_NOT_FOUND, () -> this.service.get(diagnostic.getId()));
		Assertions.assertFalse(this.service.delete(99999999L));
	}

	@Test
	@DisplayName("listResults() returns the results of a device with their diagnostic, applies the page, and accepts an unknown device")
	void listResults() {
		Diagnostic first = this.service.create(js(this.name("Result 1"), -1, "x"));
		Diagnostic second = this.service.create(js(this.name("Result 2"), -1, "x"));
		this.addResult(first.getId());
		this.addResult(second.getId());
		List<DiagnosticResult> results = this.service.listResults(this.deviceId, Page.ALL);
		Assertions.assertTrue(results.size() >= 2);
		DiagnosticResult result = results.stream().filter(r -> r.getDiagnosticId() == first.getId()).findFirst().orElseThrow();
		Assertions.assertEquals(first.getName(), result.getDiagnosticName(), "the diagnostic is loaded with the result");
		Assertions.assertEquals("value", Assertions.assertInstanceOf(DiagnosticTextResult.class, result).getText());
		Assertions.assertEquals(1, this.service.listResults(this.deviceId, new Page(0, 1)).size());
		Assertions.assertTrue(this.service.listResults(99999999L, Page.ALL).isEmpty());
	}
}
