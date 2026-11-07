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
package net.netshot.netshot.hook;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.hook.HookTrigger.TriggerType;
import net.netshot.netshot.hook.WebHook.Action;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.TestWiring;

/**
 * Tests of the hook use cases, against a real database: error codes and their order, the
 * validation of the Web hooks, the triggers, and what an update returns.
 */
@DatabaseTest
@TestInstance(Lifecycle.PER_CLASS)
class HookServiceTest {

	private Transactions transactions;
	private HookService service;
	private int counter;

	@BeforeAll
	void initDb() throws Exception {
		this.transactions = TestWiring.get().transactions();
		this.service = new HookService(this.transactions, new HookRepository());
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private String nextName() {
		return "Hook " + (this.counter++);
	}

	private static HookTrigger trigger(String item) {
		return new HookTrigger(TriggerType.POST_TASK, item);
	}

	private static WebHook webHook(String name, String url, Action action, String... triggerItems) {
		Set<HookTrigger> triggers = new HashSet<>();
		for (String item : triggerItems) {
			triggers.add(trigger(item));
		}
		return new WebHook(0, 0, null, name, true, triggers, action, url, HttpsCaTrustMode.SYSTEM_TRUSTSTORE, null);
	}

	private static WebHook withCa(WebHook hook, HttpsCaTrustMode mode, String certificate) {
		return new WebHook(hook.id(), hook.version(), hook.changeDate(), hook.name(), hook.enabled(), hook.triggers(), hook.action(), hook.url(), mode, certificate);
	}

	private WebHook valid(String... triggerItems) {
		return webHook(this.nextName(), "https://example.net/hook", Action.POST_JSON, triggerItems);
	}

	private WebHook stored(long id) {
		return (WebHook) this.transactions.read(dsl -> new HookRepository().find(dsl, id));
	}

	private static Set<String> items(Hook hook) {
		Set<String> items = new HashSet<>();
		hook.triggers().forEach(trigger -> items.add(trigger.item()));
		return items;
	}

	@Test
	@DisplayName("create() stores the Web hook with its triggers, and defaults the CA trust mode")
	void create() {
		WebHook hook = this.valid("TakeSnapshotTask", "CheckComplianceTask").withHttpsCaTrustMode(null);
		WebHook created = (WebHook) this.service.create(hook);
		Assertions.assertTrue(created.id() > 0);
		WebHook reloaded = this.stored(created.id());
		Assertions.assertEquals(Set.of("TakeSnapshotTask", "CheckComplianceTask"), items(reloaded));
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, reloaded.httpsCaTrustMode());
		Assertions.assertTrue(reloaded.enabled());
		Assertions.assertEquals(Action.POST_JSON, reloaded.action());
	}

	@Test
	@DisplayName("create() accepts a hook without triggers or with a null set of triggers")
	void createWithoutTriggers() {
		WebHook none = this.valid();
		Assertions.assertTrue(this.service.create(none).id() > 0);
		WebHook nullSet = this.valid().withTriggers(null);
		Hook created = this.service.create(nullSet);
		Assertions.assertTrue(created.id() > 0);
		Assertions.assertTrue(this.stored(created.id()).triggers().isEmpty());
	}

	@Test
	@DisplayName("create() checks the name, then the target URL, then the action, then the trust policy")
	void createValidationOrder() {
		assertError(ErrorCode.NETSHOT_INVALID_HOOK_NAME, () -> this.service.create(webHook(" ", "bad", null)));
		assertError(ErrorCode.NETSHOT_INVALID_HOOK_NAME, () -> this.service.create(webHook(null, "bad", null)));
		assertError(ErrorCode.NETSHOT_INVALID_HOOK_WEB_URL, () -> this.service.create(webHook(this.nextName(), "not a url", null)));
		assertError(ErrorCode.NETSHOT_INVALID_HOOK_WEB_URL, () -> this.service.create(webHook(this.nextName(), null, Action.POST_JSON)));
		assertError(ErrorCode.NETSHOT_INVALID_HOOK_WEB_URL, () -> this.service.create(webHook(this.nextName(), "ftp://example.net", Action.POST_JSON)));
		assertError(ErrorCode.NETSHOT_INVALID_HOOK_WEB, () -> this.service.create(webHook(this.nextName(), "http://example.net", null)));
		assertError(ErrorCode.NETSHOT_INVALID_HOOK_WEB_URL, () -> this.service.create(webHook(this.nextName(), "relative", Action.POST_JSON)));
		assertError(ErrorCode.NETSHOT_INVALID_HOOK_WEB_URL, () -> this.service.create(webHook(this.nextName(), "/path/only", Action.POST_JSON)));
		WebHook customCa = withCa(webHook(this.nextName(), "https://example.net", Action.POST_JSON), HttpsCaTrustMode.CUSTOM_CA, null);
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.create(customCa));
		WebHook badCa = withCa(customCa, HttpsCaTrustMode.CUSTOM_CA, "not a certificate");
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.create(badCa));
	}

	@Test
	@DisplayName("create() accepts a URL with credentials and keeps it as it is")
	void createUrlWithCredentials() {
		WebHook hook = webHook(this.nextName(), "https://user:secret@example.net/path?token=abc", Action.POST_XML);
		WebHook created = (WebHook) this.service.create(hook);
		Assertions.assertEquals("https://user:secret@example.net/path?token=abc", this.stored(created.id()).url());
	}

	@Test
	@DisplayName("create() refuses a name which is already used, and stores nothing")
	void createDuplicate() {
		WebHook first = (WebHook) this.service.create(this.valid());
		int before = this.service.list(Page.ALL).size();
		assertError(ErrorCode.NETSHOT_DUPLICATE_HOOK, () -> this.service.create(webHook(first.name(), "https://example.net/other", Action.POST_XML, "A")));
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
	}

	@Test
	@DisplayName("list() returns the hooks with their triggers, readable without a session, and honours the page")
	void list() {
		this.service.create(this.valid("A", "B"));
		this.service.create(this.valid("C"));
		List<Hook> all = this.service.list(Page.ALL);
		Assertions.assertTrue(all.size() >= 2);
		all.forEach(hook -> Assertions.assertNotNull(hook.triggers()));
		Assertions.assertEquals(1, this.service.list(new Page(0, 1)).size());
		Assertions.assertEquals(all.size() - 1, this.service.list(new Page(1, null)).size());
	}

	@Test
	@DisplayName("update() changes the Web hook and its triggers, keeping the triggers which stay")
	void update() {
		WebHook created = (WebHook) this.service.create(this.valid("A", "B"));
		WebHook renamed = withCa(webHook("Renamed " + created.name(), "http://example.net/new", Action.POST_YAML, "B", "C"),
			HttpsCaTrustMode.TRUST_ANY, null);
		WebHook incoming = new WebHook(0, 0, null, renamed.name(), false, renamed.triggers(), renamed.action(), renamed.url(),
			renamed.httpsCaTrustMode(), null);
		Hook updated = this.service.update(created.id(), incoming);
		Assertions.assertEquals(new WebHook(created.id(), updated.version(), updated.changeDate(), incoming.name(), incoming.enabled(),
			incoming.triggers(), incoming.action(), incoming.url(), incoming.httpsCaTrustMode(), incoming.httpsCustomCaCertificate()),
			updated, "the new definition is what is returned");
		Assertions.assertEquals(created.version() + 1, updated.version());
		Assertions.assertEquals(created.id(), updated.id());
		WebHook reloaded = this.stored(created.id());
		Assertions.assertEquals(incoming.name(), reloaded.name());
		Assertions.assertFalse(reloaded.enabled());
		Assertions.assertEquals("http://example.net/new", reloaded.url());
		Assertions.assertEquals(Action.POST_YAML, reloaded.action());
		Assertions.assertEquals(HttpsCaTrustMode.TRUST_ANY, reloaded.httpsCaTrustMode());
		Assertions.assertEquals(Set.of("B", "C"), items(reloaded));
	}

	@Test
	@DisplayName("update() defaults the CA trust mode, and ignores the ID of the definition")
	void updateDefaults() {
		WebHook created = (WebHook) this.service.create(this.valid());
		WebHook incoming = webHook(created.name(), "https://example.net/x", Action.POST_JSON).withId(99999999L).withHttpsCaTrustMode(null);
		Hook updated = this.service.update(created.id(), incoming);
		Assertions.assertEquals(created.id(), updated.id());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, ((WebHook) updated).httpsCaTrustMode());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, this.stored(created.id()).httpsCaTrustMode());
	}

	@Test
	@DisplayName("update() validates the definition before looking for the hook")
	void updateValidationOrder() {
		assertError(ErrorCode.NETSHOT_INVALID_HOOK_NAME, () -> this.service.update(99999999L, webHook("", "bad", null)));
		assertError(ErrorCode.NETSHOT_INVALID_HOOK_WEB_URL, () -> this.service.update(99999999L, webHook("x", "not a url", null)));
		assertError(ErrorCode.NETSHOT_INVALID_HOOK_WEB, () -> this.service.update(99999999L, webHook("x", "http://example.net", null)));
		assertError(ErrorCode.NETSHOT_INVALID_HOOK, () -> this.service.update(99999999L, webHook("x", "http://example.net", Action.POST_XML)));
	}

	@Test
	@DisplayName("update() refuses a name used by another hook, and leaves the hook unchanged")
	void updateDuplicate() {
		WebHook first = (WebHook) this.service.create(this.valid());
		WebHook second = (WebHook) this.service.create(this.valid("A"));
		assertError(ErrorCode.NETSHOT_DUPLICATE_HOOK,
			() -> this.service.update(second.id(), webHook(first.name(), "https://example.net/changed", Action.POST_XML, "Z")));
		WebHook reloaded = this.stored(second.id());
		Assertions.assertEquals(second.name(), reloaded.name());
		Assertions.assertEquals("https://example.net/hook", reloaded.url());
		Assertions.assertEquals(Set.of("A"), items(reloaded));
	}

	@Test
	@DisplayName("update() without a set of triggers removes all the triggers, as on creation")
	void updateNullTriggers() {
		WebHook created = (WebHook) this.service.create(this.valid("A"));
		WebHook incoming = webHook("Changed " + created.name(), "https://example.net/x", Action.POST_JSON).withTriggers(null);
		this.service.update(created.id(), incoming);
		WebHook reloaded = (WebHook) this.stored(created.id());
		Assertions.assertEquals("Changed " + created.name(), reloaded.name());
		Assertions.assertEquals(Set.of(), items(reloaded));
	}

	@Test
	@DisplayName("delete() removes the hook with its triggers, and says when there was none")
	void delete() {
		WebHook created = (WebHook) this.service.create(this.valid("A"));
		Assertions.assertTrue(this.service.delete(created.id()));
		Assertions.assertNull(this.stored(created.id()));
		Assertions.assertFalse(this.service.delete(created.id()));
		Assertions.assertFalse(this.service.delete(99999999L));
	}
}
