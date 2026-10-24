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

import java.util.List;

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

/**
 * Tests of the API token use cases, against a real database.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
class ApiTokenServiceTest {

	private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz012345";
	private static final String OTHER_TOKEN = "ZyXwVuTsRqPoNmLkJiHgFeDcBa987654";

	private final ApiTokenRepository repository = new ApiTokenRepository();
	private Transactions transactions;
	private ApiTokenService service;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = new Transactions();
		this.service = new ApiTokenService(this.transactions, this.repository);
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private int tokenCounter;

	/**
	 * A new valid token value, different from all the others of the class.
	 * @return the token value
	 */
	private String newToken() {
		return "Gen%029d".formatted(this.tokenCounter++);
	}

	private ApiToken reload(long id) {
		return this.transactions.read(dsl -> this.repository.find(dsl, id));
	}

	@Test
	@DisplayName("Authenticate: a known token gives its API token (value trimmed), an unknown one gives null")
	void authenticate() {
		String authToken = "Q1w2E3r4T5y6U7i8O9p0AaSsDdFfGgHh";
		ApiToken created = this.service.create("Auth", authToken, 10);
		ApiToken found = this.service.authenticate("  " + authToken + " ");
		Assertions.assertNotNull(found);
		Assertions.assertEquals(created.id(), found.id());
		Assertions.assertEquals(10, found.level());
		Assertions.assertNull(this.service.authenticate("00000000000000000000000000000000"));
	}

	@Test
	@DisplayName("Create: only the hash of the token is stored, the description is trimmed")
	void create() {
		ApiToken created = this.service.create("  Monitoring  ", TOKEN, 50);
		Assertions.assertTrue(created.id() > 0);
		ApiToken stored = this.reload(created.id());
		Assertions.assertEquals("Monitoring", stored.description());
		Assertions.assertEquals(50, stored.level());
		Assertions.assertEquals(ApiToken.hashToken(TOKEN), stored.hashedToken());
		Assertions.assertNotEquals(TOKEN, stored.hashedToken());
	}

	@Test
	@DisplayName("Create: a missing or blank description is stored as empty")
	void createBlankDescription() {
		Assertions.assertEquals("", this.reload(this.service.create(null, OTHER_TOKEN, 10).id()).description());
		Assertions.assertEquals("", this.reload(this.service.create("   ", this.newToken(), 10).id()).description());
	}

	@Test
	@DisplayName("Create: the token must be 32 alphanumeric characters")
	void createInvalidToken() {
		int before = this.service.list(Page.ALL).size();
		for (String invalid : new String[] {null, "", "short", TOKEN + "x", TOKEN.substring(1), "!" + TOKEN.substring(1), TOKEN.substring(1) + " "}) {
			NetshotException error = assertError(ErrorCode.NETSHOT_INVALID_API_TOKEN_FORMAT, () -> this.service.create("d", invalid, 10));
			Assertions.assertEquals("Invalid token format.", error.getMessage());
		}
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
	}

	@Test
	@DisplayName("Create: a token value already registered is refused, and nothing is stored")
	void createDuplicate() {
		String token = this.newToken();
		ApiToken first = this.service.create("one", token, 10);
		int before = this.service.list(Page.ALL).size();
		assertError(ErrorCode.NETSHOT_DUPLICATE_API_TOKEN, () -> this.service.create("two", token, 1000));
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
		Assertions.assertEquals(first.id(), this.service.authenticate(token).id());
	}

	@Test
	@DisplayName("List: the tokens can be paged")
	void list() {
		for (int i = 0; i < 3; i++) {
			this.service.create("list" + i, this.newToken(), 10);
		}
		List<ApiToken> all = this.service.list(Page.ALL);
		Assertions.assertTrue(all.size() >= 3);
		Assertions.assertEquals(2, this.service.list(new Page(0, 2)).size());
	}

	@Test
	@DisplayName("Delete: true when deleted, false when there is no such token")
	void delete() {
		ApiToken created = this.service.create("to delete", this.newToken(), 10);
		Assertions.assertTrue(this.service.delete(created.id()));
		Assertions.assertNull(this.reload(created.id()));
		Assertions.assertFalse(this.service.delete(created.id()));
		Assertions.assertFalse(this.service.delete(99_999_999L));
	}
}
