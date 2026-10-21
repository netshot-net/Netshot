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
package net.netshot.netshot.aaa.auth;

import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.aaa.user.PasswordPolicy;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.aaa.user.UserCreation;
import net.netshot.netshot.aaa.user.UserRepository;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestProperty;

/**
 * Tests of the authentication of the users who log in (local password, expired password, remote
 * methods), against a real database and a fake set of remote authentication methods. The HTTP
 * session handling is the resource's: it is covered by the end-to-end tests of {@code RestServerTest}.
 */
@DatabaseTest
@TestProperty(name = "netshot.aaa.passwordpolicy.mintotalchars", value = "8")
@TestProperty(name = "netshot.aaa.passwordpolicy.minnumericalchars", value = "1")
@TestProperty(name = "netshot.aaa.passwordpolicy.maxduration", value = "30")
@TestInstance(Lifecycle.PER_CLASS)
class LoginServiceTest {

	private static final String PASSWORD = "Passw0rd-one";
	private static final String ADDRESS = "192.0.2.10";

	/** Fake remote authentication: the users it knows by method, and a log of the calls. */
	private static final class FakeRemote implements RemoteAuthenticator {
		private final List<String> calls = new ArrayList<>();
		private UiUser codeUser;
		private UiUser radiusUser;
		private UiUser tacacsUser;

		void reset() {
			this.calls.clear();
			this.codeUser = null;
			this.radiusUser = null;
			this.tacacsUser = null;
		}

		@Override
		public UiUser authenticateWithCode(String authorizationCode, URL redirectUrl) {
			this.calls.add("oidc:" + authorizationCode + ":" + redirectUrl);
			return this.codeUser;
		}

		@Override
		public UiUser authenticateWithRadius(String username, String password, String remoteAddress) {
			this.calls.add("radius:" + username + ":" + password + ":" + remoteAddress);
			return this.radiusUser;
		}

		@Override
		public UiUser authenticateWithTacacs(String username, String password, String remoteAddress) {
			this.calls.add("tacacs:" + username + ":" + password + ":" + remoteAddress);
			return this.tacacsUser;
		}
	}

	private final AtomicInteger counter = new AtomicInteger();
	private final UserRepository repository = new UserRepository();
	private final FakeRemote remote = new FakeRemote();
	private Transactions transactions;
	private UserService users;
	private LoginService service;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = new Transactions();
		this.users = new UserService(this.transactions, this.repository);
		this.service = new LoginService(this.transactions, this.repository, this.remote);
	}

	@BeforeEach
	void resetRemote() {
		this.remote.reset();
	}

	private String nextName() {
		return "login" + this.counter.getAndIncrement();
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private UiUser createLocal(int level) {
		return this.users.create(new UserCreation(this.nextName(), true, PASSWORD, level));
	}

	private UiUser reload(String name) {
		return this.transactions.readDsl(dsl -> this.repository.findByUsername(dsl, name));
	}

	private static LoginAttempt password(String username, String password) {
		return new LoginAttempt(username, password, null, null, null, ADDRESS);
	}

	private void expire(String name) {
		this.transactions.runDsl(dsl -> this.repository.save(dsl,
			this.repository.findByUsername(dsl, name).withLastPasswordChangeDate(Instant.now().minus(60, ChronoUnit.DAYS))));
	}

	// ------------------------------------------------------------------
	// Local users
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Local user: the right password authenticates, the remote methods are not tried")
	void localSuccess() {
		UiUser user = this.createLocal(User.LEVEL_READWRITE);
		UiUser result = this.service.authenticate(password(user.username(), PASSWORD));
		Assertions.assertNotNull(result);
		Assertions.assertEquals(user.id(), result.id());
		Assertions.assertEquals(User.LEVEL_READWRITE, result.level());
		Assertions.assertEquals(List.of(), this.remote.calls);
	}

	@Test
	@DisplayName("Local user: a wrong or missing password authenticates nobody, and never falls back to the remote methods")
	void localFailure() {
		UiUser user = this.createLocal(User.LEVEL_READWRITE);
		Assertions.assertNull(this.service.authenticate(password(user.username(), "wrong")));
		Assertions.assertNull(this.service.authenticate(password(user.username(), "")));
		Assertions.assertEquals(List.of(), this.remote.calls);
	}

	@Test
	@DisplayName("Local user: a user without sufficient level is not authenticated")
	void localInsufficientLevel() {
		UiUser user = this.createLocal(0);
		Assertions.assertNull(this.service.authenticate(password(user.username(), PASSWORD)));
		UiUser negative = this.createLocal(-10);
		Assertions.assertNull(this.service.authenticate(password(negative.username(), PASSWORD)));
	}

	@Test
	@DisplayName("Local user: an expired password is an error, unless a new one is given")
	void localExpiredPassword() throws Exception {
		UiUser user = this.createLocal(User.LEVEL_READONLY);
		String name = user.username();
		this.expire(name);
		NetshotException expired = assertError(ErrorCode.NETSHOT_EXPIRED_PASSWORD, () -> this.service.authenticate(password(name, PASSWORD)));
		Assertions.assertEquals("Password has expired, it must be changed.", expired.getMessage());
		// A wrong password is still a plain failure
		Assertions.assertNull(this.service.authenticate(password(name, "wrong")));

		UiUser result = this.service.authenticate(new LoginAttempt(name, PASSWORD, "New-passw0rd", null, null, ADDRESS));
		Assertions.assertNotNull(result);
		this.reload(name).checkPassword("New-passw0rd", PasswordPolicy.getMainPolicy());
		Assertions.assertNotNull(this.service.authenticate(password(name, "New-passw0rd")));
	}

	@Test
	@DisplayName("Local user: the new password given at login must comply with the policy, and the old one stays")
	void localNewPasswordPolicy() throws Exception {
		UiUser user = this.createLocal(User.LEVEL_READONLY);
		String name = user.username();
		NetshotException failed = assertError(ErrorCode.NETSHOT_FAILED_PASSWORD_POLICY,
			() -> this.service.authenticate(new LoginAttempt(name, PASSWORD, "short", null, null, ADDRESS)));
		Assertions.assertTrue(failed.getMessage().startsWith("The password doesn't match the defined policy"), failed.getMessage());
		this.reload(name).checkPassword(PASSWORD, PasswordPolicy.getMainPolicy());
	}

	@Test
	@DisplayName("Local user: a new password is only taken after the current one is checked")
	void localNewPasswordWithWrongPassword() throws Exception {
		UiUser user = this.createLocal(User.LEVEL_READONLY);
		String name = user.username();
		Assertions.assertNull(this.service.authenticate(new LoginAttempt(name, "wrong", "New-passw0rd", null, null, ADDRESS)));
		this.reload(name).checkPassword(PASSWORD, PasswordPolicy.getMainPolicy());
	}

	@Test
	@DisplayName("Local user: an authorization code for a user who is local is a failure")
	void localUserWithCode() {
		UiUser user = this.createLocal(User.LEVEL_READONLY);
		this.remote.codeUser = UiUser.remote(user.username(), User.LEVEL_ADMIN);
		Assertions.assertNull(this.service.authenticate(new LoginAttempt(null, null, null, "code", "https://netshot.example/", ADDRESS)));
		Assertions.assertEquals(1, this.remote.calls.size());
	}

	// ------------------------------------------------------------------
	// Remote users
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Remote: RADIUS, then TACACS+ when RADIUS doesn't know the user")
	void remoteMethods() {
		String name = this.nextName();
		this.remote.tacacsUser = UiUser.remote(name, 100);
		UiUser result = this.service.authenticate(password(name, "secret"));
		Assertions.assertSame(this.remote.tacacsUser, result);
		Assertions.assertEquals(List.of("radius:" + name + ":secret:" + ADDRESS, "tacacs:" + name + ":secret:" + ADDRESS), this.remote.calls);

		this.remote.reset();
		this.remote.radiusUser = UiUser.remote(name, 50);
		this.remote.tacacsUser = UiUser.remote(name, 100);
		Assertions.assertSame(this.remote.radiusUser, this.service.authenticate(password(name, "secret")));
		Assertions.assertEquals(List.of("radius:" + name + ":secret:" + ADDRESS), this.remote.calls, "TACACS+ is not tried once RADIUS succeeded");
	}

	@Test
	@DisplayName("Remote: nobody authenticates, or without sufficient level")
	void remoteFailure() {
		String name = this.nextName();
		Assertions.assertNull(this.service.authenticate(password(name, "secret")));
		this.remote.radiusUser = UiUser.remote(name, 0);
		Assertions.assertNull(this.service.authenticate(password(name, "secret")));
	}

	@Test
	@DisplayName("Remote: a user known as non local keeps its local level, over the remote one")
	void remoteLevelOverride() {
		UiUser known = this.users.create(new UserCreation(this.nextName(), false, null, User.LEVEL_OPERATOR));
		this.remote.radiusUser = UiUser.remote(known.username(), User.LEVEL_ADMIN);
		UiUser result = this.service.authenticate(password(known.username(), "secret"));
		Assertions.assertEquals(this.remote.radiusUser.withLevel(User.LEVEL_OPERATOR), result);

		// A level of 0 set locally is a way to deny a remote user
		UiUser denied = this.users.create(new UserCreation(this.nextName(), false, null, 0));
		this.remote.radiusUser = UiUser.remote(denied.username(), User.LEVEL_ADMIN);
		Assertions.assertNull(this.service.authenticate(password(denied.username(), "secret")));
	}

	@Test
	@DisplayName("OIDC: the user name comes from the authorization code, and the remote password methods are not tried")
	void oidc() {
		String name = this.nextName();
		this.remote.codeUser = UiUser.remote(name, 100);
		UiUser result = this.service.authenticate(new LoginAttempt(null, "ignored", null, "the-code", "https://netshot.example/cb", ADDRESS));
		Assertions.assertSame(this.remote.codeUser, result);
		Assertions.assertEquals(List.of("oidc:the-code:https://netshot.example/cb"), this.remote.calls);
	}

	@Test
	@DisplayName("OIDC: a rejected code, a missing redirect URI, a malformed or relative one authenticate nobody")
	void oidcFailures() {
		this.remote.codeUser = null;
		Assertions.assertNull(this.service.authenticate(new LoginAttempt(null, null, null, "bad", "https://netshot.example/cb", ADDRESS)));
		Assertions.assertEquals(1, this.remote.calls.size());

		this.remote.reset();
		this.remote.codeUser = UiUser.remote("someone", 100);
		Assertions.assertNull(this.service.authenticate(new LoginAttempt(null, null, null, "code", null, ADDRESS)));
		Assertions.assertNull(this.service.authenticate(new LoginAttempt(null, null, null, null, "https://netshot.example/cb", ADDRESS)));
		Assertions.assertNull(this.service.authenticate(new LoginAttempt(null, null, null, "code", "foo://unknown-protocol", ADDRESS)));
		Assertions.assertNull(this.service.authenticate(new LoginAttempt(null, null, null, "code", "/relative/callback", ADDRESS)));
		Assertions.assertNull(this.service.authenticate(new LoginAttempt(null, null, null, "code", "https://bad uri", ADDRESS)));
		Assertions.assertEquals(List.of(), this.remote.calls, "The code is not exchanged without a valid redirect URL");
	}

	@Test
	@DisplayName("OIDC: the code is not looked at when a user name is given")
	void oidcCodeIgnoredWithUsername() {
		this.remote.codeUser = UiUser.remote("someone", 100);
		Assertions.assertNull(this.service.authenticate(new LoginAttempt("nobody", "pw", null, "code", "https://netshot.example/cb", ADDRESS)));
		Assertions.assertEquals(List.of("radius:nobody:pw:" + ADDRESS, "tacacs:nobody:pw:" + ADDRESS), this.remote.calls);
	}

	@Test
	@DisplayName("Without a user name nor a code, nobody authenticates and nothing is tried")
	void nothing() {
		Assertions.assertNull(this.service.authenticate(new LoginAttempt(null, "pw", "new", null, null, ADDRESS)));
		Assertions.assertEquals(List.of(), this.remote.calls);
	}
}
