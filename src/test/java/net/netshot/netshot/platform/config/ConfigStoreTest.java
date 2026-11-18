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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Configuration store")
class ConfigStoreTest {

	/** Settings declared as in the code, to build a catalog from this class. */
	static final class Declared {
		static final Setting<Integer> PORT = Setting.integer("netshot.test.port").defaultValue(514).restart();
		static final Setting<Integer> TIMEOUT = Setting.integer("netshot.test.timeout").defaultValue(5).live();
		static final Setting<String> SECRET = Setting.text("netshot.test.serverN.secret").secret().live();
		static final Setting<Boolean> ENABLED = Setting.bool("netshot.test.enabled")
			.alias("netshot.test.disabled", disabled -> !disabled).defaultValue(true).live();
		static final Setting<String> DRIVER_CLASS = Setting.text("netshot.test.driverclass").alias("netshot.test.driver_class").restart();
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
		Assertions.assertEquals(5, CATALOG.settings().size());
		Assertions.assertSame(Declared.TIMEOUT, CATALOG.find("netshot.test.timeout"));
		Assertions.assertSame(Declared.SECRET, CATALOG.find("netshot.test.server3.secret"));
		Assertions.assertNull(CATALOG.find("netshot.test.notstatic"));
	}

	/**
	 * A store whose source gives the configuration set in the test.
	 *
	 * @param source the configuration (or the failure) the source gives
	 * @return the store, loaded from the source
	 */
	private static ConfigStore store(AtomicReference<ConfigStore.Loader> source) throws ConfigException {
		ConfigStore store = new ConfigStore(() -> source.get().load());
		store.load();
		return store;
	}

	@Test
	@DisplayName("The configuration is loaded, then reloaded, from the source of the store")
	void loadsFromItsSource() throws Exception {
		Path file = this.tempDir.resolve("netshot.conf");
		Files.writeString(file, "netshot.test.timeout = 7\n", StandardCharsets.UTF_8);
		ConfigStore store = ConfigStore.ofFiles(List.of(this.tempDir.resolve("missing.conf").toString(), file.toString()));
		Assertions.assertNull(store.current().get("netshot.test.timeout"), "Empty until loaded");
		store.load();
		Assertions.assertEquals("7", store.current().get("netshot.test.timeout"));
		Files.writeString(file, "netshot.test.timeout = 9\n", StandardCharsets.UTF_8);
		Assertions.assertTrue(store.reload());
		Assertions.assertEquals("9", store.current().get("netshot.test.timeout"));
	}

	@Test
	@DisplayName("The environment overrides the file at each load, a reload only brings the changes of the file")
	void environmentOverridesOnReload() throws Exception {
		Path file = this.tempDir.resolve("netshot.conf");
		Files.writeString(file, "netshot.test.timeout = 7\nnetshot.test.port = 514\n", StandardCharsets.UTF_8);
		ConfigStore store = ConfigStore.ofFiles(List.of(file.toString()), Map.of("NETSHOT_TEST_TIMEOUT", "3"));
		store.load();
		Assertions.assertEquals(3, store.current().get(Declared.TIMEOUT), "Overridden by the environment");
		Assertions.assertEquals(514, store.current().get(Declared.PORT));
		Assertions.assertEquals(file.toString(), store.current().fileName());
		Files.writeString(file, "netshot.test.timeout = 9\nnetshot.test.port = 1514\n", StandardCharsets.UTF_8);
		Assertions.assertTrue(store.reload());
		Assertions.assertEquals(3, store.current().get(Declared.TIMEOUT), "Still overridden by the environment");
		Assertions.assertEquals(1514, store.current().get(Declared.PORT), "Changed in the file");
	}

	@Test
	@DisplayName("A secret file named by an environment variable is read again on reload")
	void environmentSecretFileOnReload() throws Exception {
		Path file = this.tempDir.resolve("netshot.conf");
		Files.writeString(file, "netshot.test.port = 514\n", StandardCharsets.UTF_8);
		Path secretFile = this.tempDir.resolve("secret");
		Files.writeString(secretFile, "first\n", StandardCharsets.UTF_8);
		Setting<String> secret = Declared.SECRET.at(1);
		ConfigStore store = ConfigStore.ofFiles(List.of(file.toString()),
			Map.of(Config.environmentName(secret.key()) + "_FILE", secretFile.toString()));
		store.load();
		Assertions.assertEquals("first", store.current().get(secret));
		Files.writeString(secretFile, "second\n", StandardCharsets.UTF_8);
		Assertions.assertTrue(store.reload());
		Assertions.assertEquals("second", store.current().get(secret), "The rotated secret is read");
	}

	@Test
	@DisplayName("An empty first file fails the load, the next files aren't tried")
	void emptyFirstFile() throws Exception {
		Path empty = this.tempDir.resolve("empty.conf");
		Files.writeString(empty, "# Nothing\n", StandardCharsets.UTF_8);
		Path file = this.tempDir.resolve("netshot.conf");
		Files.writeString(file, "netshot.test.port = 514\n", StandardCharsets.UTF_8);
		ConfigStore store = ConfigStore.ofFiles(List.of(empty.toString(), file.toString()), Map.of());
		Assertions.assertThrows(ConfigException.class, store::load);
		Assertions.assertNull(store.current().fileName(), "Still empty");
	}

	@Test
	@DisplayName("A failed load or reload keeps the current configuration")
	void failedReloadKeepsCurrent() throws Exception {
		AtomicReference<ConfigStore.Loader> source = new AtomicReference<>(() -> config(Map.of("netshot.test.timeout", "7")));
		ConfigStore store = store(source);
		Config initial = store.current();
		source.set(() -> {
			throw new ConfigException("No file");
		});
		Assertions.assertFalse(store.reload());
		Assertions.assertSame(initial, store.current());
		ConfigException e = Assertions.assertThrows(ConfigException.class, store::load);
		Assertions.assertEquals("No file", e.getMessage());
		Assertions.assertSame(initial, store.current());
	}

	@Test
	@DisplayName("The loaded configuration is checked against the catalog set after the load")
	void checkAfterLoad() throws Exception {
		Path missing = this.tempDir.resolve("missing");
		ConfigStore store = new ConfigStore(config(Map.of("netshot.test.server1.secret.file", missing.toString())));
		ConfigException e = Assertions.assertThrows(ConfigException.class, () -> store.setCatalog(CATALOG));
		Assertions.assertTrue(e.getMessage().contains(missing.toString()), "The message tells the secret file: " + e.getMessage());
		Files.writeString(missing, "s3cret", StandardCharsets.UTF_8);
		store.setCatalog(CATALOG);
	}

	@Test
	@DisplayName("A fixed configuration is its own source")
	void fixedConfiguration() throws Exception {
		Config fixed = config(Map.of("netshot.test.timeout", "7"));
		ConfigStore store = new ConfigStore(fixed);
		store.replace(config(Map.of()));
		Assertions.assertTrue(store.reload());
		Assertions.assertSame(fixed, store.current());
	}

	@Test
	@DisplayName("A reload with an unreadable secret file of a known secret keeps the current configuration")
	void unreadableSecretKeepsCurrent() throws Exception {
		AtomicReference<ConfigStore.Loader> source = new AtomicReference<>(() -> config(Map.of()));
		ConfigStore store = store(source);
		store.setCatalog(CATALOG);
		Config initial = store.current();
		Config withMissingSecret = config(Map.of("netshot.test.server1.secret.file", this.tempDir.resolve("missing").toString()));
		source.set(() -> withMissingSecret);
		Assertions.assertFalse(store.reload());
		Assertions.assertSame(initial, store.current());

		Path secretFile = this.tempDir.resolve("secret");
		Files.writeString(secretFile, "s3cret", StandardCharsets.UTF_8);
		Config withSecret = config(Map.of("netshot.test.server1.secret.file", secretFile.toString()));
		source.set(() -> withSecret);
		Assertions.assertTrue(store.reload());
		Assertions.assertEquals("s3cret", store.current().get(Declared.SECRET.at(1)));
	}

	@Test
	@DisplayName("Bindings follow the current configuration, listeners are called on change")
	void bindingsAndListeners() throws Exception {
		AtomicReference<ConfigStore.Loader> source = new AtomicReference<>(() -> config(Map.of("netshot.test.timeout", "7")));
		ConfigStore store = store(source);
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
		source.set(() -> next);
		Assertions.assertTrue(store.reload());
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

	@Test
	@DisplayName("Legacy items: known (not reported as unknown), and listed with their new key")
	void legacyKeys() {
		Properties properties = new Properties();
		properties.putAll(Map.of("netshot.test.disabled", "true", "netshot.test.port", "514"));
		Config file = Config.of(properties, Map.of());
		Assertions.assertEquals(Map.of("netshot.test.disabled", "netshot.test.enabled"), CATALOG.legacyKeys(file));
		Assertions.assertEquals(Set.of(), CATALOG.unknownKeys(file));
		Config environment = Config.of(new Properties(), Map.of("NETSHOT_TEST_DISABLED", "true", "NETSHOT_TEST_DRIVER_CLASS", "x"));
		Assertions.assertEquals(Map.of("netshot.test.disabled", "netshot.test.enabled", "netshot.test.driver_class", "netshot.test.driverclass"),
			CATALOG.legacyKeys(environment));
		Assertions.assertEquals(Set.of(), CATALOG.unknownKeys(environment), "NETSHOT_TEST_DRIVER_CLASS is the legacy netshot.test.driver_class");
		Assertions.assertEquals(Map.of(), CATALOG.legacyKeys(config(Map.of("netshot.test.enabled", "false"))));
	}

	@Test
	@DisplayName("Concurrent reloads are run one at a time, listeners included")
	void reloadsAreSerialized() throws Exception {
		ConfigStore store = new ConfigStore(() -> Config.of(new Properties()));
		AtomicInteger running = new AtomicInteger();
		AtomicInteger overlaps = new AtomicInteger();
		store.addListener(_ -> {
			if (running.incrementAndGet() > 1) {
				overlaps.incrementAndGet();
			}
			try {
				Thread.sleep(50);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			running.decrementAndGet();
		});
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			Thread thread = Thread.ofVirtual().unstarted(store::reload);
			threads.add(thread);
			thread.start();
		}
		for (Thread thread : threads) {
			thread.join();
		}
		Assertions.assertEquals(0, overlaps.get());
	}
}
