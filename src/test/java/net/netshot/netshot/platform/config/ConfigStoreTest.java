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
package net.netshot.netshot.platform.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Configuration store")
public class ConfigStoreTest {

	/** Settings declared as in the code, to build a catalog from this class. */
	static final class Declared {
		static final Setting<Integer> PORT = Setting.integer("netshot.test.port").defaultValue(514).restart();
		static final Setting<Integer> TIMEOUT = Setting.integer("netshot.test.timeout").defaultValue(5).live();
		static final Setting<String> SECRET = Setting.text("netshot.test.serverN.secret").secret().live();
		/** Not a setting (not static). */
		final Setting<String> notStatic = Setting.text("netshot.test.notstatic").live();
	}

	@TempDir
	Path tempDir;

	private static final SettingCatalog CATALOG = SettingCatalog.fromClasses(List.of(Declared.class));

	private static Config config(Map<String, String> file) {
		Properties properties = new Properties();
		properties.putAll(file);
		return Config.of(properties, Map.of());
	}

	@Test
	@DisplayName("Catalog from the static final fields")
	void catalogFromClasses() {
		Assertions.assertEquals(3, CATALOG.settings().size());
		Assertions.assertSame(Declared.TIMEOUT, CATALOG.find("netshot.test.timeout"));
		Assertions.assertSame(Declared.SECRET, CATALOG.find("netshot.test.server3.secret"));
		Assertions.assertNull(CATALOG.find("netshot.test.notstatic"));
	}

	@Test
	@DisplayName("A failed load or reload keeps the current configuration")
	void failedReloadKeepsCurrent() {
		ConfigStore store = new ConfigStore(config(Map.of("netshot.test.timeout", "7")));
		Config initial = store.current();
		Assertions.assertFalse(store.reload(() -> {
			throw new ConfigException("No file");
		}));
		Assertions.assertSame(initial, store.current());
		Assertions.assertFalse(store.load(() -> Config.load(List.of(this.tempDir.resolve("missing.conf").toString()))));
		Assertions.assertSame(initial, store.current());
	}

	@Test
	@DisplayName("A reload with an unreadable secret file of a known secret keeps the current configuration")
	void unreadableSecretKeepsCurrent() throws Exception {
		ConfigStore store = new ConfigStore(config(Map.of()));
		store.setCatalog(CATALOG);
		Config initial = store.current();
		Config withMissingSecret = config(Map.of("netshot.test.server1.secret.file", this.tempDir.resolve("missing").toString()));
		Assertions.assertFalse(store.reload(() -> withMissingSecret));
		Assertions.assertSame(initial, store.current());

		Path secretFile = this.tempDir.resolve("secret");
		Files.writeString(secretFile, "s3cret", StandardCharsets.UTF_8);
		Config withSecret = config(Map.of("netshot.test.server1.secret.file", secretFile.toString()));
		Assertions.assertTrue(store.reload(() -> withSecret));
		Assertions.assertEquals("s3cret", store.current().get(Declared.SECRET.at(1)));
	}

	@Test
	@DisplayName("Bindings follow the current configuration, listeners are called on change")
	void bindingsAndListeners() {
		ConfigStore store = new ConfigStore(config(Map.of("netshot.test.timeout", "7")));
		AtomicInteger mappings = new AtomicInteger();
		ConfigStore.Binding<Integer> timeout = store.bind(c -> {
			mappings.incrementAndGet();
			return c.get(Declared.TIMEOUT);
		});
		List<Config> notified = new ArrayList<>();
		store.addListener(notified::add);
		Assertions.assertEquals(7, timeout.get());
		Assertions.assertEquals(7, timeout.get());
		Assertions.assertEquals(1, mappings.get(), "Mapped once per snapshot");

		Config next = config(Map.of("netshot.test.timeout", "9"));
		Assertions.assertTrue(store.reload(() -> next));
		Assertions.assertEquals(2, mappings.get(), "Mapped again, eagerly, on reload");
		Assertions.assertEquals(9, timeout.get());
		Assertions.assertEquals(List.of(next), notified);
	}

	@Test
	@DisplayName("Changed settings which need a restart")
	void changedRestartKeys() {
		Config previous = config(Map.of("netshot.test.port", "514", "netshot.test.timeout", "5"));
		Assertions.assertEquals(Set.of(), CATALOG.changedRestartKeys(previous, previous));
		Assertions.assertEquals(Set.of(), CATALOG.changedRestartKeys(previous,
			config(Map.of("netshot.test.port", "514", "netshot.test.timeout", "6"))), "Live setting");
		Assertions.assertEquals(Set.of("netshot.test.port"), CATALOG.changedRestartKeys(previous,
			config(Map.of("netshot.test.port", "1514", "netshot.test.timeout", "5"))));
		Assertions.assertEquals(Set.of("netshot.test.port"), CATALOG.changedRestartKeys(previous,
			config(Map.of("netshot.test.timeout", "5"))), "Removed");
	}

	@Test
	@DisplayName("Unknown configuration items")
	void unknownKeys() {
		Properties properties = new Properties();
		properties.putAll(Map.of(
			"netshot.test.port", "514",
			"netshot.test.prot", "514",
			"netshot.test.server2.secret.file", "/run/secrets/x",
			"netshot.test.timeout.file", "/tmp/x",
			"other.item", "x"));
		Config config = Config.of(properties, Map.of("NETSHOT_TEST_TIMEOUT", "3", "NETSHOT_TEST_TIEMOUT", "3", "PATH", "/bin"));
		Assertions.assertEquals(Set.of("netshot.test.prot", "netshot.test.timeout.file", "netshot.test.tiemout"),
			CATALOG.unknownKeys(config));
		Assertions.assertEquals(Set.of(), SettingCatalog.EMPTY.unknownKeys(config), "Nothing is checked without catalog");
	}
}
