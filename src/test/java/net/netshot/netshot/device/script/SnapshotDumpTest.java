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
package net.netshot.netshot.device.script;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.FakeDeviceFactory;
import net.netshot.netshot.FakeTaskContext;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.device.Config;
import net.netshot.netshot.device.Device;

@DisplayName("Snapshot configuration dump")
public class SnapshotDumpTest {

	@TempDir
	Path tempDir;

	Path dumpDir;

	@BeforeEach
	void setDumpFolder() throws Exception {
		this.dumpDir = Files.createDirectory(tempDir.resolve("dump"));
		Properties config = new Properties();
		config.setProperty("netshot.snapshots.dump", this.dumpDir.toString());
		TestConfig.init(config);
	}

	@AfterEach
	void resetConfig() {
		TestConfig.init(new Properties());
	}

	private void dump(String deviceName) {
		Device device = FakeDeviceFactory.getFakeCiscoIosDevice();
		device.setName(deviceName);
		new SnapshotDeviceScript(new FakeTaskContext()).dumpConfig(device, new Config(device));
	}

	@Test
	@DisplayName("Dump file is named after the device")
	void dumpFileName() {
		dump("router1");
		Assertions.assertTrue(Files.exists(dumpDir.resolve("router1")), "The dump file was not written");
	}

	@Test
	@DisplayName("Device name can't escape the dump folder")
	void dumpPathTraversal() throws Exception {
		dump("../escaped");
		dump("..");
		dump("sub/../../escaped2");
		Assertions.assertFalse(Files.exists(tempDir.resolve("escaped")), "The dump escaped the dump folder");
		Assertions.assertFalse(Files.exists(tempDir.resolve("escaped2")), "The dump escaped the dump folder");
		try (var files = Files.list(tempDir)) {
			Assertions.assertEquals(1, files.count(), "Unexpected file written outside of the dump folder");
		}
	}

	@Test
	@DisplayName("Path separators in device name are replaced")
	void dumpSeparators() {
		dump("sub/name");
		Assertions.assertTrue(Files.exists(dumpDir.resolve("sub_name")), "The dump file was not written as sub_name");
	}
}
