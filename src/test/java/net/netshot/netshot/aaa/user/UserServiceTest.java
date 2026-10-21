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
package net.netshot.netshot.aaa.user;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.aaa.user.UiUser.WrongPasswordException;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestProperty;

/**
 * Tests of the user use cases (administration and change of the own password), against a real
 * database. The password policy is: at least 8 characters including 1 digit, 2 previous passwords
 * remembered, 30 days of validity.
 */
@DatabaseTest
@TestProperty(name = "netshot.aaa.passwordpolicy.mintotalchars", value = "8")
@TestProperty(name = "netshot.aaa.passwordpolicy.minnumericalchars", value = "1")
@TestProperty(name = "netshot.aaa.passwordpolicy.maxhistory", value = "2")
@TestProperty(name = "netshot.aaa.passwordpolicy.maxduration", value = "30")
@TestInstance(Lifecycle.PER_CLASS)
class UserServiceTest {

	private static final String ADMIN = "the-admin";
	private static final String PASSWORD = "Passw0rd-one";

	private final AtomicInteger counter = new AtomicInteger();
	private final UserRepository repository = new UserRepository();
	private Transactions transactions;
	private UserService service;

	@BeforeAll
	void initDb() throws Exception {
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
		return this.transactions.readDsl(dsl -> this.repository.find(dsl, id));
	}

	private UiUser reload(String name) {
		return this.transactions.readDsl(dsl -> this.repository.findByUsername(dsl, name));
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
		Assertions.assertTrue(local.id() > 0);
		Assertions.assertEquals(name, local.username(), "The name is trimmed");
		Assertions.assertTrue(local.local());
		Assertions.assertEquals(User.LEVEL_READWRITE, local.level());
		UiUser stored = this.reload(local.id());
		Assertions.assertNotEquals(PASSWORD, stored.hashedPassword());
		assertPassword(stored, PASSWORD);
		Assertions.assertThrows(WrongPasswordException.class, () -> assertPassword(stored, "other"));
		Assertions.assertNotNull(stored.lastPasswordChangeDate());

		String remoteName = this.nextName();
		UiUser remote = this.create(remoteName, false, "ignored", User.LEVEL_OPERATOR);
		UiUser storedRemote = this.reload(remote.id());
		Assertions.assertFalse(storedRemote.local());
		assertPassword(storedRemote, "");
		Assertions.assertThrows(WrongPasswordException.class, () -> assertPassword(storedRemote, "ignored"));
	}

	@Test
	@DisplayName("The password policy is applied when an administrator creates a local user, not for a remote user")
	void createAppliesPolicy() throws Exception {
		String name = this.nextName();
		assertError(ErrorCode.NETSHOT_INVALID_PASSWORD, () -> this.create(name, true, "x", User.LEVEL_READONLY));
		// Nothing was stored: the name is still free
		UiUser user = this.create(name, true, PASSWORD, User.LEVEL_READONLY);
		assertPassword(this.reload(user.id()), PASSWORD);
		this.create(this.nextName(), false, "x", User.LEVEL_READONLY);
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
		Assertions.assertNotNull(all.get(0).username());
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
		UiUser updated = this.service.update(user.id(), ADMIN, new UserUpdate(" " + newName + " ", true, "N3w-password", 500));
		Assertions.assertEquals(newName, updated.username());
		UiUser stored = this.reload(user.id());
		Assertions.assertEquals(newName, stored.username());
		Assertions.assertEquals(500, stored.level());
		assertPassword(stored, "N3w-password");
		Assertions.assertEquals(List.of(user.hashedPassword()), stored.oldHashedPasswords(), "The previous hash is kept in the history");
	}

	@Test
	@DisplayName("Update: a null password keeps the current one of a local user")
	void updateKeepsPassword() throws Exception {
		UiUser user = this.create(this.nextName(), true, PASSWORD, 10);
		this.service.update(user.id(), ADMIN, new UserUpdate(user.username(), true, null, 100));
		UiUser stored = this.reload(user.id());
		Assertions.assertEquals(100, stored.level());
		assertPassword(stored, PASSWORD);
	}

	@Test
	@DisplayName("Update: turning a user into a remote one clears its password")
	void updateToRemote() throws Exception {
		UiUser user = this.create(this.nextName(), true, PASSWORD, 10);
		this.service.update(user.id(), ADMIN, new UserUpdate(user.username(), false, PASSWORD, 10));
		UiUser stored = this.reload(user.id());
		Assertions.assertFalse(stored.local());
		Assertions.assertEquals("", stored.hashedPassword());
	}

	@Test
	@DisplayName("Update: a remote user turned into a local one needs a password, which must comply with the policy")
	void updateToLocal() throws Exception {
		UiUser user = this.create(this.nextName(), false, null, 10);
		String name = user.username();
		NetshotException missing = assertError(ErrorCode.NETSHOT_INVALID_PASSWORD,
			() -> this.service.update(user.id(), ADMIN, new UserUpdate("renamed", true, null, 500)));
		Assertions.assertEquals("You must set a password.", missing.getMessage());
		NetshotException weak = assertError(ErrorCode.NETSHOT_INVALID_PASSWORD,
			() -> this.service.update(user.id(), ADMIN, new UserUpdate("renamed", true, "short", 500)));
		Assertions.assertTrue(weak.getMessage().startsWith("The password doesn't match the defined policy"), weak.getMessage());
		UiUser stored = this.reload(user.id());
		Assertions.assertEquals(name, stored.username(), "A failed update changes nothing");
		Assertions.assertEquals(10, stored.level());
		Assertions.assertFalse(stored.local());
		this.service.update(user.id(), ADMIN, new UserUpdate(name, true, PASSWORD, 10));
		Assertions.assertTrue(this.reload(user.id()).local());
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
			() -> this.service.update(self.id(), self.username(), new UserUpdate(" ", false, null, 10)));
		Assertions.assertEquals("Cannot edit the user who is making the request.", locked.getMessage());

		UiUser other = this.create(this.nextName(), false, null, 10);
		assertError(ErrorCode.NETSHOT_INVALID_USER_NAME, () -> this.service.update(other.id(), ADMIN, new UserUpdate(null, false, null, 10)));
		assertError(ErrorCode.NETSHOT_INVALID_USER_NAME, () -> this.service.update(other.id(), ADMIN, new UserUpdate("  ", false, null, 10)));
		Assertions.assertEquals(10, this.reload(other.id()).level());
	}

	@Test
	@DisplayName("Update: a name already used is a duplicate and changes nothing")
	void updateDuplicate() {
		UiUser first = this.create(this.nextName(), false, null, 10);
		UiUser second = this.create(this.nextName(), false, null, 10);
		assertError(ErrorCode.NETSHOT_DUPLICATE_USER,
			() -> this.service.update(second.id(), ADMIN, new UserUpdate(first.username(), false, null, 1000)));
		UiUser stored = this.reload(second.id());
		Assertions.assertEquals(second.username(), stored.username());
		Assertions.assertEquals(10, stored.level());
	}

	// ------------------------------------------------------------------
	// Delete
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Delete: false for an unknown user, an error for the user making the request, true otherwise")
	void delete() {
		Assertions.assertFalse(this.service.delete(99_999_999L, ADMIN));
		UiUser self = this.create(this.nextName(), false, null, 1000);
		NetshotException locked = assertError(ErrorCode.NETSHOT_LOCKED_USER, () -> this.service.delete(self.id(), self.username()));
		Assertions.assertEquals("Cannot delete the user who is making the request.", locked.getMessage());
		Assertions.assertNotNull(this.reload(self.id()));
		UiUser other = this.create(this.nextName(), false, null, 10);
		Assertions.assertTrue(this.service.delete(other.id(), self.username()));
		Assertions.assertNull(this.reload(other.id()));
		Assertions.assertFalse(this.service.delete(other.id(), self.username()), "Already deleted");
	}

	// ------------------------------------------------------------------
	// Change of the own password
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Own password: changed when the current one is right and the new one complies with the policy")
	void changeOwnPassword() throws Exception {
		UiUser user = this.create(this.nextName(), true, PASSWORD, 10);
		String name = user.username();
		UiUser changed = this.service.changeOwnPassword(name, name, PASSWORD, "An0ther-password");
		Assertions.assertEquals(name, changed.username());
		UiUser stored = this.reload(name);
		assertPassword(stored, "An0ther-password");
		Assertions.assertEquals(List.of(user.hashedPassword()), stored.oldHashedPasswords());
	}

	@Test
	@DisplayName("Own password: only the local user making the request, whose name must be repeated")
	void changeOwnPasswordInvalidUser() {
		UiUser user = this.create(this.nextName(), true, PASSWORD, 10);
		UiUser remote = this.create(this.nextName(), false, null, 10);
		NetshotException other = assertError(ErrorCode.NETSHOT_INVALID_USER,
			() -> this.service.changeOwnPassword(user.username(), remote.username(), PASSWORD, "An0ther-password"));
		Assertions.assertEquals("Invalid user.", other.getMessage());
		assertError(ErrorCode.NETSHOT_INVALID_USER, () -> this.service.changeOwnPassword(user.username(), null, PASSWORD, "An0ther-password"));
		assertError(ErrorCode.NETSHOT_INVALID_USER, () -> this.service.changeOwnPassword(remote.username(), remote.username(), "", "An0ther-password"));
		// An API token (or any principal without a user record)
		assertError(ErrorCode.NETSHOT_INVALID_USER, () -> this.service.changeOwnPassword("API Token [1: x]", "API Token [1: x]", PASSWORD, "An0ther-password"));
	}

	@Test
	@DisplayName("Own password: wrong current password, empty or non compliant new password, reused password")
	void changeOwnPasswordErrors() throws Exception {
		UiUser user = this.create(this.nextName(), true, PASSWORD, 10);
		String name = user.username();
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
		String name = user.username();
		this.transactions.runDsl(dsl -> this.repository.save(dsl,
			this.repository.findByUsername(dsl, name).withLastPasswordChangeDate(Instant.now().minus(60, ChronoUnit.DAYS))));
		Assertions.assertThrows(PasswordPolicy.PasswordPolicyException.class, () -> this.reload(name).checkPassword(PASSWORD, PasswordPolicy.getMainPolicy()));
		this.service.changeOwnPassword(name, name, PASSWORD, "Fresh-passw0rd");
		this.reload(name).checkPassword("Fresh-passw0rd", PasswordPolicy.getMainPolicy());
	}
}
