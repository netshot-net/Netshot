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

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.aaa.auth.Radius;
import net.netshot.netshot.aaa.auth.Tacacs;
import net.netshot.netshot.aaa.user.PasswordPolicy;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.access.Ssh;
import net.netshot.netshot.access.Telnet;
import net.netshot.netshot.access.collector.Collector;
import net.netshot.netshot.devicescript.helper.PythonFileSystem;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.NetworkAddress;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.vault.VaultManager;

/**
 * Characterization of the configuration reload (what the HUP signal applies, through
 * {@link Netshot#loadModuleConfigs()}): the module settings are read again from the new configuration.
 */
@UnitTest
@DisplayName("Configuration reload")
public class ConfigReloadTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void resetConfig() {
		Netshot.initConfig(new Properties());
		Netshot.loadModuleConfigs();
	}

	private static void reload(Map<String, String> values) {
		Properties config = new Properties();
		config.putAll(values);
		Netshot.initConfig(config);
		Netshot.loadModuleConfigs();
	}

	@Test
	@DisplayName("Reload applies the new values of the module settings, then the defaults again")
	void reloadAppliesNewValuesThenDefaults() throws Exception {
		reload(Map.ofEntries(
			Map.entry("netshot.aaa.passwordpolicy.maxhistory", "7"),
			Map.entry("netshot.aaa.maxidletime", "600"),
			Map.entry("netshot.cli.ssh.connectiontimeout", "1234"),
			Map.entry("netshot.cli.ssh.ciphers", "aes256-ctr, aes128-ctr"),
			Map.entry("netshot.cli.telnet.receivetimeout", "4321"),
			Map.entry("netshot.cluster.domainipoverride", "10.0.0.1|192.168.0.1"),
			Map.entry("netshot.vault.http.readtimeoutms", "2222"),
			Map.entry("netshot.snapshots.binary.path", this.tempDir.toString()),
			Map.entry("netshot.python.virtualenv", this.tempDir.toString())));
		Assertions.assertEquals(7, PasswordPolicy.getMainPolicy().getMaxHistory());
		Assertions.assertEquals(600, UiUser.SETTINGS.getMaxIdleTime());
		Assertions.assertEquals(1234, Ssh.SETTINGS.getConnectionTimeout());
		Assertions.assertArrayEquals(new String[] { "aes256-ctr", "aes128-ctr" }, Ssh.SETTINGS.getCiphers());
		Assertions.assertEquals(4321, Telnet.SETTINGS.getReceiveTimeout());
		Assertions.assertEquals(NetworkAddress.getNetworkAddress("192.168.0.1"),
			Collector.SETTINGS.getClusterIpOverrides().get(NetworkAddress.getNetworkAddress("10.0.0.1")));
		Assertions.assertEquals(2222, VaultManager.SETTINGS.getHttpReadTimeoutMs());
		Assertions.assertEquals(this.tempDir.normalize(), ConfigBinaryFileAttribute.SETTINGS.getStorageFolderPath());
		Assertions.assertEquals(this.tempDir.toFile().getAbsolutePath(), PythonFileSystem.SETTINGS.getVenvFolder());

		reload(Map.of());
		Assertions.assertEquals(0, PasswordPolicy.getMainPolicy().getMaxHistory());
		Assertions.assertEquals(1800, UiUser.SETTINGS.getMaxIdleTime());
		Assertions.assertEquals(5000, Ssh.SETTINGS.getConnectionTimeout());
		Assertions.assertEquals(60000, Telnet.SETTINGS.getReceiveTimeout());
		Assertions.assertTrue(Collector.SETTINGS.getClusterIpOverrides().isEmpty());
		Assertions.assertEquals(10000, VaultManager.SETTINGS.getHttpReadTimeoutMs());
		Assertions.assertNull(PythonFileSystem.SETTINGS.getVenvFolder());
	}

	@Test
	@DisplayName("Reload adds and removes the RADIUS servers")
	void reloadRadiusServers() {
		reload(Map.of(
			"netshot.aaa.radius1.ip", "127.0.0.1",
			"netshot.aaa.radius1.secret", "secret"));
		Assertions.assertTrue(Radius.isAvailable());
		reload(Map.of());
		Assertions.assertFalse(Radius.isAvailable());
	}

	@Test
	@DisplayName("Reload adds and removes the TACACS+ servers")
	void reloadTacacsServers() {
		reload(Map.of(
			"netshot.aaa.tacacs1.ip", "127.0.0.1",
			"netshot.aaa.tacacs1.secret", "secret"));
		Assertions.assertTrue(Tacacs.isAvailable());
		reload(Map.of());
		Assertions.assertFalse(Tacacs.isAvailable());
	}

	@Test
	@DisplayName("Invalid values are replaced by the defaults")
	void invalidValuesUseDefaults() {
		reload(Map.of(
			"netshot.aaa.passwordpolicy.maxhistory", "seven",
			"netshot.aaa.maxidletime", "1",
			"netshot.cli.ssh.rekeydatalimit", "big"));
		Assertions.assertEquals(0, PasswordPolicy.getMainPolicy().getMaxHistory(), "Not a number");
		Assertions.assertEquals(1800, UiUser.SETTINGS.getMaxIdleTime(), "Out of range");
		Assertions.assertEquals(1024L * 1024L * 1024L, Ssh.SETTINGS.getRekeyDataLimit(), "Not a long");
	}

	@Test
	@DisplayName("Typed getters: parsing, ranges and defaults")
	void typedGetters() {
		Properties config = new Properties();
		config.setProperty("netshot.test.bool", "true");
		config.setProperty("netshot.test.badbool", "yes");
		config.setProperty("netshot.test.int", "42");
		config.setProperty("netshot.test.badint", "4x2");
		Netshot.initConfig(config);
		Assertions.assertTrue(Netshot.getConfig("netshot.test.bool", false));
		Assertions.assertFalse(Netshot.getConfig("netshot.test.badbool", false), "Only true/false are booleans");
		Assertions.assertTrue(Netshot.getConfig("netshot.test.missing", true));
		Assertions.assertEquals(42, Netshot.getConfig("netshot.test.int", 1));
		Assertions.assertEquals(1, Netshot.getConfig("netshot.test.badint", 1));
		Assertions.assertEquals(42, Netshot.getConfig("netshot.test.int", 1, 0, 100));
		Assertions.assertEquals(1, Netshot.getConfig("netshot.test.int", 1, 0, 10), "Out of range");
		Assertions.assertEquals("dflt", Netshot.getConfig("netshot.test.missing", "dflt"));
		Assertions.assertNull(Netshot.getConfig("netshot.test.missing"));
	}
}
