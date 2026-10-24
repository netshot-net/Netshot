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
package net.netshot.netshot.userscript;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestDrivers;

/**
 * Tests of the command script use cases, against a real database: validation (driver, name,
 * content, evaluation of the script), duplicates, listing without the content, deletion.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
class ScriptServiceTest {

	private static final String SCRIPT = "function run(cli, device) {\n  cli.command('show clock');\n}";

	private final AtomicInteger counter = new AtomicInteger();
	private ScriptService service;
	private Transactions transactions;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = new Transactions();
		this.service = new ScriptService(this.transactions, new ScriptRepository(), TestDrivers.registry());
	}

	private String name(String prefix) {
		return prefix + " " + this.counter.incrementAndGet();
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private ScriptCreation creation(String name) {
		return new ScriptCreation(name, "CiscoIOS12", SCRIPT, "tester", "Folder");
	}

	private long count() {
		return this.transactions.read(dsl -> (long) dsl.fetchCount(net.netshot.netshot.platform.database.schema.Tables.DEVICE_JS_SCRIPT));
	}

	@Test
	@DisplayName("create() stores the script as posted (name not trimmed), with its author, folder and extracted input definitions")
	void create() {
		String name = " " + this.name("Script") + " ";
		DeviceJsScript script = this.service.create(this.creation(name));
		Assertions.assertTrue(script.id() > 0);
		Assertions.assertEquals(name, script.name(), "the name is stored as posted");
		Assertions.assertEquals("CiscoIOS12", script.deviceDriver());
		Assertions.assertEquals("tester", script.author());
		Assertions.assertEquals("Folder", script.folder());
		Assertions.assertEquals(SCRIPT, script.script());
		Assertions.assertNotNull(script.userInputDefinitions(), "the definitions are extracted on creation");
		Assertions.assertTrue(script.userInputDefinitions().isEmpty());
		Assertions.assertEquals("Cisco IOS and IOS-XE", TestDrivers.registry().getDriverByName(script.deviceDriver()).getDescription());
		DeviceJsScript read = this.service.get(script.id());
		Assertions.assertEquals(name, read.name());
		Assertions.assertNull(read.userInputDefinitions(), "the definitions are never stored: known quirk, null once read back");
	}

	@Test
	@DisplayName("create() keeps a null folder as posted")
	void createNullFolder() {
		DeviceJsScript script = this.service.create(new ScriptCreation(this.name("No folder"), "CiscoIOS12", SCRIPT, "tester", null));
		Assertions.assertNull(script.folder());
	}

	@Test
	@DisplayName("validate() checks the script like create() but stores nothing")
	void validate() {
		long before = this.count();
		DeviceJsScript script = this.service.validate(this.creation(this.name("Validated")));
		Assertions.assertEquals(0, script.id());
		Assertions.assertNotNull(script.userInputDefinitions());
		Assertions.assertEquals(before, this.count());
		assertError(ErrorCode.NETSHOT_INVALID_SCRIPT, () -> this.service.validate(this.creation(" ")));
	}

	@Test
	@DisplayName("create() reports an unknown driver, a blank name, a missing content (in this order) and a script which can't be evaluated")
	void createErrors() {
		long before = this.count();
		NetshotException driver = assertError(ErrorCode.NETSHOT_INVALID_SCRIPT,
			() -> this.service.create(new ScriptCreation(null, "NoSuchDriver", null, "tester", "")));
		Assertions.assertEquals("Invalid driver name.", driver.getMessage());
		assertError(ErrorCode.NETSHOT_INVALID_SCRIPT, () -> this.service.create(new ScriptCreation(null, null, SCRIPT, "tester", "")));
		NetshotException blank = assertError(ErrorCode.NETSHOT_INVALID_SCRIPT,
			() -> this.service.create(new ScriptCreation("  ", "CiscoIOS12", null, "tester", "")));
		Assertions.assertEquals("Invalid script name.", blank.getMessage());
		NetshotException empty = assertError(ErrorCode.NETSHOT_INVALID_SCRIPT,
			() -> this.service.create(new ScriptCreation(this.name("Empty"), "CiscoIOS12", null, "tester", "")));
		Assertions.assertEquals("The script content can't be empty.", empty.getMessage());
		NetshotException syntax = assertError(ErrorCode.NETSHOT_INVALID_SCRIPT,
			() -> this.service.create(new ScriptCreation(this.name("Syntax"), "CiscoIOS12", "function run( {", "tester", "")));
		Assertions.assertTrue(syntax.getMessage().contains("SyntaxError"), syntax.getMessage());
		Assertions.assertEquals(before, this.count(), "nothing was stored");
	}

	@Test
	@DisplayName("create() refuses a duplicate name in the same folder, not in another folder")
	void createDuplicate() {
		String name = this.name("Duplicate");
		this.service.create(this.creation(name));
		assertError(ErrorCode.NETSHOT_DUPLICATE_SCRIPT, () -> this.service.create(this.creation(name)));
		this.service.create(new ScriptCreation(name, "CiscoIOS12", SCRIPT, "tester", "Other folder"));
	}

	@Test
	@DisplayName("get() returns null for an unknown script")
	void getUnknown() {
		Assertions.assertNull(this.service.get(99999999L));
	}

	@Test
	@DisplayName("list() returns the scripts without their content, applies the page, and does not blank the stored scripts")
	void list() {
		DeviceJsScript first = this.service.create(this.creation(this.name("Listed")));
		this.service.create(this.creation(this.name("Listed")));
		var listed = this.service.list(Page.ALL);
		Assertions.assertTrue(listed.size() >= 2);
		DeviceJsScript item = listed.stream().filter(s -> s.id() == first.id()).findFirst().orElseThrow();
		Assertions.assertNull(item.script());
		Assertions.assertEquals(first.name(), item.name());
		Assertions.assertEquals(1, this.service.list(new Page(0, 1)).size());
		Assertions.assertEquals(SCRIPT, this.service.get(first.id()).script(), "the stored script is untouched");
	}

	@Test
	@DisplayName("delete() removes the script, and reports an unknown script")
	void delete() {
		DeviceJsScript script = this.service.create(this.creation(this.name("To delete")));
		this.service.delete(script.id());
		Assertions.assertNull(this.service.get(script.id()));
		assertError(ErrorCode.NETSHOT_SCRIPT_NOT_FOUND, () -> this.service.delete(script.id()));
		assertError(ErrorCode.NETSHOT_SCRIPT_NOT_FOUND, () -> this.service.delete(99999999L));
	}
}
