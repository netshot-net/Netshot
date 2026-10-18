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
package net.netshot.netshot.inventory.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.hibernate.Session;
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
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigNumericAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Tests of the configuration use cases, against a real database.
 */
@TestInstance(Lifecycle.PER_CLASS)
class ConfigServiceTest extends WithDatabaseTest {

	private static final long D2020 = 1_577_880_000_000L;
	private static final long D2021 = 1_609_502_400_000L;
	private static final long D2022 = 1_641_038_400_000L;

	private Transactions transactions;
	private ConfigService service;
	private long domainId;
	private long groupId;
	private long router;
	private long other;
	private long snmp;
	private long first;
	private long second;
	private long third;
	private long otherConfig;
	private long snmpConfig;
	private Path binaryFolder;

	@BeforeAll
	void initDb() throws Exception {
		Properties config = getFreshDatabaseConfig("configservicetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		this.binaryFolder = Files.createTempDirectory("netshot-config-service-");
		config.setProperty("netshot.snapshots.binary.path", this.binaryFolder.toString());
		TestConfig.init(config);
		Netshot.loadModuleConfigs();
		TestDrivers.reload();
		Database.update();
		Wiring.init(TestDrivers.registry());
		this.transactions = new Transactions();
		this.service = new ConfigService(this.transactions, new ConfigRepository(), TestDrivers.registry());
		this.transactions.run(session -> {
			Domain domain = new Domain("Lab", "Test domain", null, null);
			session.persist(domain);
			this.domainId = domain.getId();
			Device routerDevice = device(session, "CiscoIOS12", "router-a", "10.40.0.1", domain);
			this.router = routerDevice.getId();
			Device otherDevice = device(session, "CiscoIOS12", "router-b", "10.40.0.2", null);
			this.other = otherDevice.getId();
			Device snmpDevice = device(session, "GenericSNMP", "snmp-c", "10.40.0.3", domain);
			this.snmp = snmpDevice.getId();
			this.first = addConfig(session, routerDevice, D2020, "tester", "hostname a\n!\ninterface Gi0/0\n ip address 10.0.0.1\n!\nend\n", false).getId();
			this.second = addConfig(session, routerDevice, D2021, "tester", "hostname a\n!\ninterface Gi0/0\n ip address 10.0.0.1\n shutdown\n!\nend\n", true).getId();
			this.third = addConfig(session, routerDevice, D2022, "snapshot", "hostname a\n!\nend\n", true).getId();
			this.otherConfig = addConfig(session, otherDevice, D2021, "tester", "hostname b\n", false).getId();
			this.snmpConfig = addConfig(session, snmpDevice, D2021, "tester", "x\n", false).getId();
			StaticDeviceGroup group = new StaticDeviceGroup("Routers");
			group.updateCachedDevices(List.of(routerDevice));
			session.persist(group);
			this.groupId = group.getId();
		});
	}

	private static Device device(Session session, String driver, String name, String address, Domain domain) {
		Device device = new Device(driver, address, domain, "seed");
		device.setName(name);
		session.persist(device);
		return device;
	}

	private static Config addConfig(Session session, Device device, long date, String author, String runningConfig, boolean full) {
		Config config = new Config(device);
		config.setAuthor(author);
		config.setChangeDate(new Date(date));
		device.getConfigs().add(config);
		session.persist(config);
		ConfigLongTextAttribute running = new ConfigLongTextAttribute(config, "runningConfig", runningConfig);
		config.addAttribute(running);
		session.persist(running);
		ConfigTextAttribute version = new ConfigTextAttribute(config, "iosVersion", "16.1.6");
		config.addAttribute(version);
		session.persist(version);
		if (full) {
			ConfigNumericAttribute size = new ConfigNumericAttribute(config, "configSize", 1234.5);
			config.addAttribute(size);
			session.persist(size);
			ConfigBinaryAttribute saved = new ConfigBinaryAttribute(config, "configSaved", true);
			config.addAttribute(saved);
			session.persist(saved);
			ConfigBinaryFileAttribute file = new ConfigBinaryFileAttribute(config, "backupFile", "backup.tar.gz");
			file.setFileSize(5);
			config.addAttribute(file);
			session.persist(file);
			try {
				Files.writeString(file.getFilePath(), "bytes");
			}
			catch (java.io.IOException e) {
				throw new IllegalStateException(e);
			}
		}
		return config;
	}

	private static NetshotException assertError(ErrorCode code, Executable executable) {
		NetshotException exception = Assertions.assertThrows(NetshotException.class, executable);
		Assertions.assertEquals(code, exception.getErrorCode(), exception.getMessage());
		return exception;
	}

	private static ConfigFilter noFilter() {
		return new ConfigFilter(null, null, Set.of(), Set.of());
	}

	// ------------------------------------------------------------------
	// lists
	// ------------------------------------------------------------------

	@Test
	@DisplayName("listByDevice() returns the configurations of the device, the most recent first, with usable attributes")
	void listByDevice() {
		List<Config> configs = this.service.listByDevice(this.router, Page.ALL);
		Assertions.assertEquals(List.of(this.third, this.second, this.first), configs.stream().map(Config::getId).toList());
		Assertions.assertEquals(5, configs.get(0).getAttributes().size(), "attributes can be read without a session");
		Assertions.assertEquals(2, configs.get(2).getAttributes().size());
		Assertions.assertEquals(List.of(this.second), this.service.listByDevice(this.router, new Page(1, 1)).stream().map(Config::getId).toList());
		Assertions.assertEquals(List.of(), this.service.listByDevice(99999999L, Page.ALL));
	}

	@Test
	@DisplayName("listChanges() filters on the dates, the domains and the groups")
	void listChanges() {
		List<ConfigSummary> all = this.service.listChanges(noFilter(), Page.ALL);
		Assertions.assertEquals(5, all.size());
		ConfigSummary summary = all.stream().filter(s -> s.id() == this.third).findFirst().orElseThrow();
		Assertions.assertEquals("router-a", summary.deviceName());
		Assertions.assertEquals(this.router, summary.deviceId());
		Assertions.assertEquals("snapshot", summary.author());
		Assertions.assertEquals(new Date(D2022), summary.changeDate());

		Assertions.assertEquals(Set.of(this.second, this.third, this.otherConfig, this.snmpConfig),
			ids(this.service.listChanges(new ConfigFilter(new Date(D2021), null, Set.of(), Set.of()), Page.ALL)), "after is inclusive");
		Assertions.assertEquals(Set.of(this.first),
			ids(this.service.listChanges(new ConfigFilter(null, new Date(D2021), Set.of(), Set.of()), Page.ALL)), "before is exclusive");
		Assertions.assertEquals(Set.of(this.first, this.second, this.third, this.snmpConfig),
			ids(this.service.listChanges(new ConfigFilter(null, null, Set.of(this.domainId), Set.of()), Page.ALL)));
		Assertions.assertEquals(Set.of(this.first, this.second, this.third),
			ids(this.service.listChanges(new ConfigFilter(null, null, Set.of(), Set.of(this.groupId)), Page.ALL)));
		Assertions.assertEquals(1, this.service.listChanges(noFilter(), new Page(0, 1)).size());
	}

	private static Set<Long> ids(List<ConfigSummary> summaries) {
		return summaries.stream().map(ConfigSummary::id).collect(java.util.stream.Collectors.toSet());
	}

	// ------------------------------------------------------------------
	// items
	// ------------------------------------------------------------------

	@Test
	@DisplayName("getItem() gives a long text as UTF-8 bytes, named after the device and the date")
	void textItem() {
		ConfigItem item = this.service.getItem(this.first, "runningConfig");
		byte[] expected = "hostname a\n!\ninterface Gi0/0\n ip address 10.0.0.1\n!\nend\n".getBytes(StandardCharsets.UTF_8);
		Assertions.assertArrayEquals(expected, item.text());
		Assertions.assertNull(item.file());
		Assertions.assertEquals(expected.length, item.length());
		String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date(D2020));
		Assertions.assertEquals("router-a_" + date + "_runningConfig.cfg", item.fileName());
	}

	@Test
	@DisplayName("getItem() gives a binary file as a file, named after the device, the date and the original name")
	void fileItem() {
		ConfigItem item = this.service.getItem(this.second, "backupFile");
		Assertions.assertNull(item.text());
		File file = item.file();
		Assertions.assertTrue(file.isFile());
		Assertions.assertEquals(5, item.length());
		String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date(D2021));
		Assertions.assertEquals("router-a_" + date + "_backupFile_backup.tar.gz", item.fileName());
	}

	@Test
	@DisplayName("getItem() reports an unknown configuration, an unknown item and an item which can't be downloaded")
	void itemErrors() {
		ConfigItemException unknown = Assertions.assertThrows(ConfigItemException.class, () -> this.service.getItem(99999999L, "runningConfig"));
		Assertions.assertEquals(ConfigItemException.Reason.CONFIG_NOT_FOUND, unknown.getReason());
		Assertions.assertEquals("Unable to find the configuration set", unknown.getMessage());
		ConfigItemException noItem = Assertions.assertThrows(ConfigItemException.class, () -> this.service.getItem(this.first, "nope"));
		Assertions.assertEquals(ConfigItemException.Reason.INVALID_ITEM, noItem.getReason());
		Assertions.assertEquals("Invalid configuration item 'nope'", noItem.getMessage());
		ConfigItemException text = Assertions.assertThrows(ConfigItemException.class, () -> this.service.getItem(this.first, "iosVersion"));
		Assertions.assertEquals(ConfigItemException.Reason.INVALID_ITEM, text.getReason(), "a short text can't be downloaded");
	}

	// ------------------------------------------------------------------
	// diff
	// ------------------------------------------------------------------

	@Test
	@DisplayName("diff() computes the differences of the comparable items, with their context")
	void diff() {
		ConfigDiff diff = this.service.diff(this.first, this.second, true, false);
		Assertions.assertEquals(this.first, diff.originalConfigId());
		Assertions.assertEquals(this.second, diff.revisedConfigId());
		Assertions.assertEquals(new Date(D2020), diff.originalDate());
		Assertions.assertEquals(new Date(D2021), diff.revisedDate());
		Assertions.assertNull(diff.originalConfig());
		Assertions.assertNull(diff.revisedConfig());
		List<ConfigDelta> deltas = diff.deltas().get("Running configuration");
		Assertions.assertEquals(1, deltas.size());
		ConfigDelta delta = deltas.get(0);
		Assertions.assertEquals(ConfigDelta.Type.INSERT, delta.type());
		Assertions.assertEquals(List.of(" shutdown"), delta.revisedLines());
		Assertions.assertEquals(4, delta.originalPosition());
		Assertions.assertEquals(List.of("hostname a", "!", "interface Gi0/0", " ip address 10.0.0.1").subList(1, 4), delta.preContext());
		Assertions.assertEquals(List.of(), delta.hierarchy().stream().map(ConfigDelta.LineWithPosition::line).toList());
		Assertions.assertFalse(diff.deltas().containsKey("IOS version"), "unchanged items have no delta");
	}

	@Test
	@DisplayName("diff() leaves the deltas out on demand, and gives the configurations with their attributes on demand")
	void diffOptions() {
		ConfigDiff bare = this.service.diff(this.first, this.second, false, false);
		Assertions.assertNull(bare.deltas());
		Assertions.assertNull(bare.originalConfig());
		ConfigDiff full = this.service.diff(this.second, this.third, false, true);
		Assertions.assertNull(full.deltas());
		Assertions.assertEquals(5, full.originalConfig().getAttributes().size(), "attributes can be read without a session");
		Assertions.assertEquals(this.third, full.revisedConfig().getId());
	}

	@Test
	@DisplayName("diff() takes the previous configuration of the device when the first ID is 0")
	void diffPrevious() {
		ConfigDiff diff = this.service.diff(0, this.third, true, false);
		Assertions.assertEquals(this.second, diff.originalConfigId());
		ConfigDiff beforeAll = this.service.diff(0, this.first, true, true);
		Assertions.assertEquals(0, beforeAll.originalConfigId(), "an empty configuration stands for the missing one");
		Assertions.assertNull(beforeAll.originalDate());
		Assertions.assertEquals(0, beforeAll.originalConfig().getAttributes().size());
		Assertions.assertEquals(ConfigDelta.Type.CHANGE, beforeAll.deltas().get("Running configuration").get(0).type());
	}

	@Test
	@DisplayName("diff() reports unknown configurations and configurations of different drivers")
	void diffErrors() {
		assertError(ErrorCode.NETSHOT_CONFIG_NOT_FOUND, () -> this.service.diff(this.first, 99999999L, true, false));
		assertError(ErrorCode.NETSHOT_CONFIG_NOT_FOUND, () -> this.service.diff(99999999L, this.first, true, false));
		assertError(ErrorCode.NETSHOT_CONFIG_NOT_FOUND, () -> this.service.diff(0, 99999999L, true, false));
		assertError(ErrorCode.NETSHOT_INCOMPATIBLE_CONFIGS, () -> this.service.diff(this.first, this.snmpConfig, true, false));
		// Another device, same driver
		Assertions.assertEquals(this.otherConfig, this.service.diff(this.first, this.otherConfig, false, false).revisedConfigId());
	}
}
