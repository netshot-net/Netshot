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

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.ApiTokenRepository;
import net.netshot.netshot.aaa.user.ApiTokenService;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Tests of the API token use cases, against a real database.
 */
@TestInstance(Lifecycle.PER_CLASS)
class ApiTokenServiceTest extends WithDatabaseTest {

	private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz012345";
	private static final String OTHER_TOKEN = "ZyXwVuTsRqPoNmLkJiHgFeDcBa987654";

	private final ApiTokenRepository repository = new ApiTokenRepository();
	private Transactions transactions;
	private ApiTokenService service;

	@BeforeAll
	void initDb() throws Exception {
		Properties config = getFreshDatabaseConfig("apitokenservicetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		Netshot.initConfig(config);
		Netshot.loadModuleConfigs();
		DeviceDriver.refreshDrivers();
		Database.update();
		Wiring.init();
		this.transactions = new Transactions();
		this.service = new ApiTokenService(this.transactions, this.repository);
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private ApiToken reload(long id) {
		return this.transactions.read(session -> this.repository.find(session, id));
	}

	@Test
	@DisplayName("Authenticate: a known token gives its API token (value trimmed), an unknown one gives null")
	void authenticate() {
		String authToken = "Q1w2E3r4T5y6U7i8O9p0AaSsDdFfGgHh";
		ApiToken created = this.service.create("Auth", authToken, 10);
		ApiToken found = this.service.authenticate("  " + authToken + " ");
		Assertions.assertNotNull(found);
		Assertions.assertEquals(created.getId(), found.getId());
		Assertions.assertEquals(10, found.getLevel());
		Assertions.assertNull(this.service.authenticate("00000000000000000000000000000000"));
		// A token value registered twice is ambiguous: the database error is logged and nobody is authenticated
		this.service.create("Auth again", authToken, 10);
		Assertions.assertNull(this.service.authenticate(authToken));
	}

	@Test
	@DisplayName("Create: only the hash of the token is stored, the description is trimmed")
	void create() {
		ApiToken created = this.service.create("  Monitoring  ", TOKEN, 50);
		Assertions.assertTrue(created.getId() > 0);
		ApiToken stored = this.reload(created.getId());
		Assertions.assertEquals("Monitoring", stored.getDescription());
		Assertions.assertEquals(50, stored.getLevel());
		Assertions.assertEquals(ApiToken.hashToken(TOKEN), stored.getHashedToken());
		Assertions.assertNotEquals(TOKEN, stored.getHashedToken());
	}

	@Test
	@DisplayName("Create: a missing or blank description is stored as empty")
	void createBlankDescription() {
		Assertions.assertEquals("", this.reload(this.service.create(null, OTHER_TOKEN, 10).getId()).getDescription());
		Assertions.assertEquals("", this.reload(this.service.create("   ", OTHER_TOKEN, 10).getId()).getDescription());
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
	@DisplayName("Known quirk: the same token can be registered several times, with any level")
	void createDuplicateAndAnyLevel() {
		ApiToken first = this.service.create("one", TOKEN, 10);
		ApiToken second = this.service.create("two", TOKEN, -5);
		Assertions.assertNotEquals(first.getId(), second.getId());
		Assertions.assertEquals(first.getHashedToken(), second.getHashedToken());
		Assertions.assertEquals(-5, this.reload(second.getId()).getLevel());
	}

	@Test
	@DisplayName("List: the tokens can be paged")
	void list() {
		for (int i = 0; i < 3; i++) {
			this.service.create("list" + i, TOKEN, 10);
		}
		List<ApiToken> all = this.service.list(Page.ALL);
		Assertions.assertTrue(all.size() >= 3);
		Assertions.assertEquals(2, this.service.list(new Page(0, 2)).size());
	}

	@Test
	@DisplayName("Delete: true when deleted, false when there is no such token")
	void delete() {
		ApiToken created = this.service.create("to delete", TOKEN, 10);
		Assertions.assertTrue(this.service.delete(created.getId()));
		Assertions.assertNull(this.reload(created.getId()));
		Assertions.assertFalse(this.service.delete(created.getId()));
		Assertions.assertFalse(this.service.delete(99_999_999L));
	}
}
