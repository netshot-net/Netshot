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
package net.netshot.netshot.hook;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.WithDatabaseTest;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.hook.HookTrigger.TriggerType;
import net.netshot.netshot.hook.WebHook.Action;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * Tests of the hook use cases, against a real database: error codes and their order, the
 * validation of the Web hooks, the triggers, and what an update returns.
 */
@TestInstance(Lifecycle.PER_CLASS)
class HookServiceTest extends WithDatabaseTest {

	private Transactions transactions;
	private HookService service;
	private int counter;

	@BeforeAll
	void initDb() throws Exception {
		Properties config = getFreshDatabaseConfig("hookservicetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		TestConfig.init(config);
		Netshot.loadModuleConfigs();
		TestDrivers.reload();
		Database.update();
		Wiring.init(TestDrivers.registry());
		this.transactions = new Transactions();
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
		HookTrigger trigger = new HookTrigger();
		trigger.setType(TriggerType.POST_TASK);
		trigger.setItem(item);
		return trigger;
	}

	private static WebHook webHook(String name, String url, Action action, String... triggerItems) {
		WebHook hook = new WebHook();
		hook.setName(name);
		hook.setUrl(url);
		hook.setAction(action);
		Set<HookTrigger> triggers = new HashSet<>();
		for (String item : triggerItems) {
			triggers.add(trigger(item));
		}
		hook.setTriggers(triggers);
		return hook;
	}

	private WebHook valid(String... triggerItems) {
		return webHook(this.nextName(), "https://example.net/hook", Action.POST_JSON, triggerItems);
	}

	private WebHook stored(long id) {
		return (WebHook) this.transactions.read(session -> {
			Hook hook = session.get(Hook.class, id);
			hook.getTriggers().size();
			return hook;
		});
	}

	private static Set<String> items(Hook hook) {
		Set<String> items = new HashSet<>();
		hook.getTriggers().forEach(trigger -> items.add(trigger.getItem()));
		return items;
	}

	@Test
	@DisplayName("create() stores the Web hook with its triggers, and defaults the CA trust mode")
	void create() {
		WebHook hook = this.valid("TakeSnapshotTask", "CheckComplianceTask");
		hook.setHttpsCaTrustMode(null);
		WebHook created = (WebHook) this.service.create(hook);
		Assertions.assertTrue(created.getId() > 0);
		WebHook reloaded = this.stored(created.getId());
		Assertions.assertEquals(Set.of("TakeSnapshotTask", "CheckComplianceTask"), items(reloaded));
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, reloaded.getHttpsCaTrustMode());
		Assertions.assertTrue(reloaded.isEnabled());
		Assertions.assertEquals(Action.POST_JSON, reloaded.getAction());
	}

	@Test
	@DisplayName("create() accepts a hook without triggers or with a null set of triggers")
	void createWithoutTriggers() {
		WebHook none = this.valid();
		Assertions.assertTrue(this.service.create(none).getId() > 0);
		WebHook nullSet = this.valid();
		nullSet.setTriggers(null);
		Hook created = this.service.create(nullSet);
		Assertions.assertTrue(created.getId() > 0);
		Assertions.assertTrue(this.stored(created.getId()).getTriggers().isEmpty());
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
		WebHook customCa = webHook(this.nextName(), "https://example.net", Action.POST_JSON);
		customCa.setHttpsCaTrustMode(HttpsCaTrustMode.CUSTOM_CA);
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.create(customCa));
		customCa.setHttpsCustomCaCertificate("not a certificate");
		assertError(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, () -> this.service.create(customCa));
	}

	@Test
	@DisplayName("create() accepts a URL with credentials and keeps it as it is")
	void createUrlWithCredentials() {
		WebHook hook = webHook(this.nextName(), "https://user:secret@example.net/path?token=abc", Action.POST_XML);
		WebHook created = (WebHook) this.service.create(hook);
		Assertions.assertEquals("https://user:secret@example.net/path?token=abc", this.stored(created.getId()).getUrl());
	}

	@Test
	@DisplayName("create() refuses a name which is already used, and stores nothing")
	void createDuplicate() {
		WebHook first = (WebHook) this.service.create(this.valid());
		int before = this.service.list(Page.ALL).size();
		assertError(ErrorCode.NETSHOT_DUPLICATE_HOOK, () -> this.service.create(webHook(first.getName(), "https://example.net/other", Action.POST_XML, "A")));
		Assertions.assertEquals(before, this.service.list(Page.ALL).size());
	}

	@Test
	@DisplayName("list() returns the hooks with their triggers, readable without a session, and honours the page")
	void list() {
		this.service.create(this.valid("A", "B"));
		this.service.create(this.valid("C"));
		List<Hook> all = this.service.list(Page.ALL);
		Assertions.assertTrue(all.size() >= 2);
		all.forEach(hook -> hook.getTriggers().size());
		Assertions.assertEquals(1, this.service.list(new Page(0, 1)).size());
		Assertions.assertEquals(all.size() - 1, this.service.list(new Page(1, null)).size());
	}

	@Test
	@DisplayName("update() changes the Web hook and its triggers, keeping the triggers which stay")
	void update() {
		WebHook created = (WebHook) this.service.create(this.valid("A", "B"));
		WebHook incoming = webHook("Renamed " + created.getName(), "http://example.net/new", Action.POST_YAML, "B", "C");
		incoming.setEnabled(false);
		incoming.setHttpsCaTrustMode(HttpsCaTrustMode.TRUST_ANY);
		Hook updated = this.service.update(created.getId(), incoming);
		Assertions.assertSame(incoming, updated, "the new definition is what is returned");
		Assertions.assertEquals(created.getId(), updated.getId());
		WebHook reloaded = this.stored(created.getId());
		Assertions.assertEquals(incoming.getName(), reloaded.getName());
		Assertions.assertFalse(reloaded.isEnabled());
		Assertions.assertEquals("http://example.net/new", reloaded.getUrl());
		Assertions.assertEquals(Action.POST_YAML, reloaded.getAction());
		Assertions.assertEquals(HttpsCaTrustMode.TRUST_ANY, reloaded.getHttpsCaTrustMode());
		Assertions.assertEquals(Set.of("B", "C"), items(reloaded));
	}

	@Test
	@DisplayName("update() defaults the CA trust mode, and ignores the ID of the definition")
	void updateDefaults() {
		WebHook created = (WebHook) this.service.create(this.valid());
		WebHook incoming = webHook(created.getName(), "https://example.net/x", Action.POST_JSON);
		incoming.setId(99999999L);
		incoming.setHttpsCaTrustMode(null);
		Hook updated = this.service.update(created.getId(), incoming);
		Assertions.assertEquals(created.getId(), updated.getId());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, ((WebHook) updated).getHttpsCaTrustMode());
		Assertions.assertEquals(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, this.stored(created.getId()).getHttpsCaTrustMode());
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
			() -> this.service.update(second.getId(), webHook(first.getName(), "https://example.net/changed", Action.POST_XML, "Z")));
		WebHook reloaded = this.stored(second.getId());
		Assertions.assertEquals(second.getName(), reloaded.getName());
		Assertions.assertEquals("https://example.net/hook", reloaded.getUrl());
		Assertions.assertEquals(Set.of("A"), items(reloaded));
	}

	@Test
	@DisplayName("update() without a set of triggers removes all the triggers, as on creation")
	void updateNullTriggers() {
		WebHook created = (WebHook) this.service.create(this.valid("A"));
		WebHook incoming = webHook("Changed " + created.getName(), "https://example.net/x", Action.POST_JSON);
		incoming.setTriggers(null);
		this.service.update(created.getId(), incoming);
		WebHook reloaded = (WebHook) this.stored(created.getId());
		Assertions.assertEquals("Changed " + created.getName(), reloaded.getName());
		Assertions.assertEquals(Set.of(), items(reloaded));
	}

	@Test
	@DisplayName("delete() removes the hook with its triggers, and says when there was none")
	void delete() {
		WebHook created = (WebHook) this.service.create(this.valid("A"));
		Assertions.assertTrue(this.service.delete(created.getId()));
		Assertions.assertNull(this.transactions.read(session -> session.get(Hook.class, created.getId())));
		Assertions.assertFalse(this.service.delete(created.getId()));
		Assertions.assertFalse(this.service.delete(99999999L));
	}
}
