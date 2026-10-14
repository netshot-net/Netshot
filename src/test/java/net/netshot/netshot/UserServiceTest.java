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

import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.aaa.user.PasswordPolicy;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.UiUser.WrongPasswordException;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.aaa.user.UserCreation;
import net.netshot.netshot.aaa.user.UserRepository;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.aaa.user.UserUpdate;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Tests of the user use cases (administration and change of the own password), against a real
 * database. The password policy is: at least 8 characters including 1 digit, 2 previous passwords
 * remembered, 30 days of validity.
 */
@TestInstance(Lifecycle.PER_CLASS)
class UserServiceTest extends WithDatabaseTest {

	private static final String ADMIN = "the-admin";
	private static final String PASSWORD = "Passw0rd-one";

	private final AtomicInteger counter = new AtomicInteger();
	private final UserRepository repository = new UserRepository();
	private Transactions transactions;
	private UserService service;

	@BeforeAll
	void initDb() throws Exception {
		Properties config = getFreshDatabaseConfig("userservicetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		config.setProperty("netshot.aaa.passwordpolicy.mintotalchars", "8");
		config.setProperty("netshot.aaa.passwordpolicy.minnumericalchars", "1");
		config.setProperty("netshot.aaa.passwordpolicy.maxhistory", "2");
		config.setProperty("netshot.aaa.passwordpolicy.maxduration", "30");
		Netshot.initConfig(config);
		Netshot.loadModuleConfigs();
		DeviceDriver.refreshDrivers();
		Database.update();
		Wiring.init();
		this.transactions = new Transactions();
		this.service = new UserService(this.transactions, this.repository);
	}

	private String nextName() {
		return "user" + this.counter.getAndIncrement();
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private UiUser create(String name, boolean local, String password, int level) {
		return this.service.create(new UserCreation(name, local, password, level));
	}

	private UiUser reload(long id) {
		return this.transactions.read(session -> this.repository.find(session, id));
	}

	private UiUser reload(String name) {
		return this.transactions.read(session -> this.repository.findByUsername(session, name));
	}

	private static void assertPassword(UiUser user, String password) throws Exception {
		user.checkPassword(password, null);
	}

	// ------------------------------------------------------------------
	// Create
	// ------------------------------------------------------------------

	@Test
	@DisplayName("A local user is created with a hashed password, a remote one with an empty password")
	void create() throws Exception {
		String name = this.nextName();
		UiUser local = this.create("  " + name + "  ", true, PASSWORD, User.LEVEL_READWRITE);
		Assertions.assertTrue(local.getId() > 0);
		Assertions.assertEquals(name, local.getUsername(), "The name is trimmed");
		Assertions.assertTrue(local.isLocal());
		Assertions.assertEquals(User.LEVEL_READWRITE, local.getLevel());
		UiUser stored = this.reload(local.getId());
		Assertions.assertNotEquals(PASSWORD, stored.getHashedPassword());
		assertPassword(stored, PASSWORD);
		Assertions.assertThrows(WrongPasswordException.class, () -> assertPassword(stored, "other"));
		Assertions.assertNotNull(stored.getLastPasswordChangeDate());

		String remoteName = this.nextName();
		UiUser remote = this.create(remoteName, false, "ignored", User.LEVEL_OPERATOR);
		UiUser storedRemote = this.reload(remote.getId());
		Assertions.assertFalse(storedRemote.isLocal());
		assertPassword(storedRemote, "");
		Assertions.assertThrows(WrongPasswordException.class, () -> assertPassword(storedRemote, "ignored"));
	}

	@Test
	@DisplayName("Known quirk: the password policy is not applied when an administrator creates a user")
	void createIgnoresPolicy() throws Exception {
		UiUser user = this.create(this.nextName(), true, "x", User.LEVEL_READONLY);
		assertPassword(this.reload(user.getId()), "x");
	}

	@Test
	@DisplayName("Create: blank name, then missing password of a local user, then duplicate name")
	void createErrors() {
		int before = this.service.list(Page.ALL).size();
		assertError(ErrorCode.NETSHOT_INVALID_USER_NAME, () -> this.create(null, true, PASSWORD, 10));
		assertError(ErrorCode.NETSHOT_INVALID_USER_NAME, () -> this.create("   ", true, PASSWORD, 10));
		// The name is checked before the password
		assertError(ErrorCode.NETSHOT_INVALID_USER_NAME, () -> this.create("", true, null, 10));
		assertError(ErrorCode.NETSHOT_INVALID_PASSWORD, () -> this.create(this.nextName(), true, null, 10));
		assertError(ErrorCode.NETSHOT_INVALID_PASSWORD, () -> this.create(this.nextName(), true, "", 10));
		String name = this.nextName();
		this.create(name, false, null, 10);
		NetshotException duplicate = assertError(ErrorCode.NETSHOT_DUPLICATE_USER, () -> this.create(" " + name, true, PASSWORD, 10));
		Assertions.assertEquals("A user with this name already exists.", duplicate.getMessage());
		Assertions.assertEquals(before + 1, this.service.list(Page.ALL).size(), "Failed creations leave nothing behind");
	}

	// ------------------------------------------------------------------
	// List
	// ------------------------------------------------------------------

	@Test
	@DisplayName("List: users are returned detached and can be paged")
	void list() {
		for (int i = 0; i < 3; i++) {
			this.create(this.nextName(), false, null, 10);
		}
		List<UiUser> all = this.service.list(Page.ALL);
		Assertions.assertTrue(all.size() >= 3);
		Assertions.assertNotNull(all.get(0).getUsername());
		Assertions.assertEquals(2, this.service.list(new Page(0, 2)).size());
		Assertions.assertEquals(all.size() - 1, this.service.list(new Page(1, null)).size());
	}

	// ------------------------------------------------------------------
	// Update
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Update: name (trimmed), level and password are changed")
	void update() throws Exception {
		UiUser user = this.create(this.nextName(), true, PASSWORD, 10);
		String newName = this.nextName();
		UiUser updated = this.service.update(user.getId(), ADMIN, new UserUpdate(" " + newName + " ", true, "N3w-password", 500));
		Assertions.assertEquals(newName, updated.getUsername());
		UiUser stored = this.reload(user.getId());
		Assertions.assertEquals(newName, stored.getUsername());
		Assertions.assertEquals(500, stored.getLevel());
		assertPassword(stored, "N3w-password");
		Assertions.assertEquals(List.of(user.getHashedPassword()), stored.getOldHashedPasswords(), "The previous hash is kept in the history");
	}

	@Test
	@DisplayName("Update: a null password keeps the current one of a local user")
	void updateKeepsPassword() throws Exception {
		UiUser user = this.create(this.nextName(), true, PASSWORD, 10);
		this.service.update(user.getId(), ADMIN, new UserUpdate(user.getUsername(), true, null, 100));
		UiUser stored = this.reload(user.getId());
		Assertions.assertEquals(100, stored.getLevel());
		assertPassword(stored, PASSWORD);
	}

	@Test
	@DisplayName("Update: turning a user into a remote one clears its password")
	void updateToRemote() throws Exception {
		UiUser user = this.create(this.nextName(), true, PASSWORD, 10);
		this.service.update(user.getId(), ADMIN, new UserUpdate(user.getUsername(), false, PASSWORD, 10));
		UiUser stored = this.reload(user.getId());
		Assertions.assertFalse(stored.isLocal());
		Assertions.assertEquals("", stored.getHashedPassword());
	}

	@Test
	@DisplayName("Update: a remote user turned into a local one needs a password, which must comply with the policy")
	void updateToLocal() throws Exception {
		UiUser user = this.create(this.nextName(), false, null, 10);
		String name = user.getUsername();
		NetshotException missing = assertError(ErrorCode.NETSHOT_INVALID_PASSWORD,
			() -> this.service.update(user.getId(), ADMIN, new UserUpdate("renamed", true, null, 500)));
		Assertions.assertEquals("You must set a password.", missing.getMessage());
		NetshotException weak = assertError(ErrorCode.NETSHOT_INVALID_PASSWORD,
			() -> this.service.update(user.getId(), ADMIN, new UserUpdate("renamed", true, "short", 500)));
		Assertions.assertTrue(weak.getMessage().startsWith("The password doesn't match the defined policy"), weak.getMessage());
		UiUser stored = this.reload(user.getId());
		Assertions.assertEquals(name, stored.getUsername(), "A failed update changes nothing");
		Assertions.assertEquals(10, stored.getLevel());
		Assertions.assertFalse(stored.isLocal());
		this.service.update(user.getId(), ADMIN, new UserUpdate(name, true, PASSWORD, 10));
		Assertions.assertTrue(this.reload(user.getId()).isLocal());
	}

	@Test
	@DisplayName("Update: unknown user, then the user making the request, then the name, in that order")
	void updateErrors() {
		NetshotException unknown = assertError(ErrorCode.NETSHOT_INVALID_USER,
			() -> this.service.update(99_999_999L, ADMIN, new UserUpdate("x", false, null, 10)));
		Assertions.assertEquals("Unable to find this user.", unknown.getMessage());

		UiUser self = this.create(this.nextName(), false, null, 1000);
		// The self check comes before the validation of the name
		NetshotException locked = assertError(ErrorCode.NETSHOT_LOCKED_USER,
			() -> this.service.update(self.getId(), self.getUsername(), new UserUpdate(" ", false, null, 10)));
		Assertions.assertEquals("Cannot edit the user who is making the request.", locked.getMessage());

		UiUser other = this.create(this.nextName(), false, null, 10);
		assertError(ErrorCode.NETSHOT_INVALID_USER_NAME, () -> this.service.update(other.getId(), ADMIN, new UserUpdate(null, false, null, 10)));
		assertError(ErrorCode.NETSHOT_INVALID_USER_NAME, () -> this.service.update(other.getId(), ADMIN, new UserUpdate("  ", false, null, 10)));
		Assertions.assertEquals(10, this.reload(other.getId()).getLevel());
	}

	@Test
	@DisplayName("Update: a name already used is a duplicate and changes nothing")
	void updateDuplicate() {
		UiUser first = this.create(this.nextName(), false, null, 10);
		UiUser second = this.create(this.nextName(), false, null, 10);
		assertError(ErrorCode.NETSHOT_DUPLICATE_USER,
			() -> this.service.update(second.getId(), ADMIN, new UserUpdate(first.getUsername(), false, null, 1000)));
		UiUser stored = this.reload(second.getId());
		Assertions.assertEquals(second.getUsername(), stored.getUsername());
		Assertions.assertEquals(10, stored.getLevel());
	}

	// ------------------------------------------------------------------
	// Delete
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Delete: false for an unknown user, an error for the user making the request, true otherwise")
	void delete() {
		Assertions.assertFalse(this.service.delete(99_999_999L, ADMIN));
		UiUser self = this.create(this.nextName(), false, null, 1000);
		NetshotException locked = assertError(ErrorCode.NETSHOT_LOCKED_USER, () -> this.service.delete(self.getId(), self.getUsername()));
		Assertions.assertEquals("Cannot delete the user who is making the request.", locked.getMessage());
		Assertions.assertNotNull(this.reload(self.getId()));
		UiUser other = this.create(this.nextName(), false, null, 10);
		Assertions.assertTrue(this.service.delete(other.getId(), self.getUsername()));
		Assertions.assertNull(this.reload(other.getId()));
		Assertions.assertFalse(this.service.delete(other.getId(), self.getUsername()), "Already deleted");
	}

	// ------------------------------------------------------------------
	// Change of the own password
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Own password: changed when the current one is right and the new one complies with the policy")
	void changeOwnPassword() throws Exception {
		UiUser user = this.create(this.nextName(), true, PASSWORD, 10);
		String name = user.getUsername();
		UiUser changed = this.service.changeOwnPassword(name, name, PASSWORD, "An0ther-password");
		Assertions.assertEquals(name, changed.getUsername());
		UiUser stored = this.reload(name);
		assertPassword(stored, "An0ther-password");
		Assertions.assertEquals(List.of(user.getHashedPassword()), stored.getOldHashedPasswords());
	}

	@Test
	@DisplayName("Own password: only the local user making the request, whose name must be repeated")
	void changeOwnPasswordInvalidUser() {
		UiUser user = this.create(this.nextName(), true, PASSWORD, 10);
		UiUser remote = this.create(this.nextName(), false, null, 10);
		NetshotException other = assertError(ErrorCode.NETSHOT_INVALID_USER,
			() -> this.service.changeOwnPassword(user.getUsername(), remote.getUsername(), PASSWORD, "An0ther-password"));
		Assertions.assertEquals("Invalid user.", other.getMessage());
		assertError(ErrorCode.NETSHOT_INVALID_USER, () -> this.service.changeOwnPassword(user.getUsername(), null, PASSWORD, "An0ther-password"));
		assertError(ErrorCode.NETSHOT_INVALID_USER, () -> this.service.changeOwnPassword(remote.getUsername(), remote.getUsername(), "", "An0ther-password"));
		// An API token (or any principal without a user record)
		assertError(ErrorCode.NETSHOT_INVALID_USER, () -> this.service.changeOwnPassword("API Token [1: x]", "API Token [1: x]", PASSWORD, "An0ther-password"));
	}

	@Test
	@DisplayName("Own password: wrong current password, empty or non compliant new password, reused password")
	void changeOwnPasswordErrors() throws Exception {
		UiUser user = this.create(this.nextName(), true, PASSWORD, 10);
		String name = user.getUsername();
		NetshotException wrong = assertError(ErrorCode.NETSHOT_INVALID_PASSWORD,
			() -> this.service.changeOwnPassword(name, name, "wrong", "An0ther-password"));
		Assertions.assertEquals("Invalid current password.", wrong.getMessage());
		// The current password is checked before the new one
		assertError(ErrorCode.NETSHOT_INVALID_PASSWORD, () -> this.service.changeOwnPassword(name, name, "wrong", null));
		NetshotException empty = assertError(ErrorCode.NETSHOT_INVALID_PASSWORD, () -> this.service.changeOwnPassword(name, name, PASSWORD, ""));
		Assertions.assertEquals("The password cannot be empty.", empty.getMessage());
		assertError(ErrorCode.NETSHOT_INVALID_PASSWORD, () -> this.service.changeOwnPassword(name, name, PASSWORD, null));
		assertError(ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY, () -> this.service.changeOwnPassword(name, name, PASSWORD, "short1"));
		assertError(ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY, () -> this.service.changeOwnPassword(name, name, PASSWORD, "no-digit-here"));
		assertPassword(this.reload(name), PASSWORD);
		this.service.changeOwnPassword(name, name, PASSWORD, "Second-pass1");
		NetshotException reused = assertError(ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY,
			() -> this.service.changeOwnPassword(name, name, "Second-pass1", PASSWORD));
		Assertions.assertEquals("Password was already used for this account", reused.getMessage());
		assertPassword(this.reload(name), "Second-pass1");
	}

	@Test
	@DisplayName("Own password: an expired password can still be changed")
	void changeExpiredPassword() throws Exception {
		UiUser user = this.create(this.nextName(), true, PASSWORD, 10);
		String name = user.getUsername();
		this.transactions.run(session -> {
			UiUser stored = this.repository.findByUsername(session, name);
			Calendar old = Calendar.getInstance();
			old.add(Calendar.DATE, -60);
			stored.setLastPasswordChangeDate(old.getTime());
		});
		Assertions.assertThrows(PasswordPolicy.PasswordPolicyException.class, () -> this.reload(name).checkPassword(PASSWORD, PasswordPolicy.getMainPolicy()));
		this.service.changeOwnPassword(name, name, PASSWORD, "Fresh-passw0rd");
		this.reload(name).checkPassword("Fresh-passw0rd", PasswordPolicy.getMainPolicy());
	}
}
