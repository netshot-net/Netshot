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
package net.netshot.netshot.driver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.testing.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ConfigBinaryFileAttribute}'s pending-to-final storage
 * lifecycle: filenames before/after the owning config is "persisted"
 * (simulated here via setId(), without a real database).
 */
@UnitTest
@DisplayName("ConfigBinaryFileAttribute storage lifecycle test")
public class ConfigBinaryFileAttributeStorageTest {

	@BeforeEach
	void setUp() throws IOException {
		Path storagePath = Files.createTempDirectory("netshot-test-binary-storage-");
		Properties props = new Properties();
		props.setProperty("netshot.snapshots.binary.path", storagePath.toString());
		TestConfig.init(props);
		ConfigBinaryFileAttribute.loadConfig();
	}

	private ConfigBinaryFileAttribute newPersistedAttribute(long deviceId, long configId) {
		Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
		Device device = new Device("HttpDownloadTestDriver", null, domain, "test");
		device.setId(deviceId);
		Config config = new Config(device);
		config.setId(configId);
		ConfigBinaryFileAttribute attribute = new ConfigBinaryFileAttribute(config, "backupArchive", "backup.tar.gz");
		attribute.setId(1); // Simulate a persisted attribute row
		return attribute;
	}

	@Test
	@DisplayName("finalizeStorage() renames the pending file to its final name once the config has a real id")
	void finalizeStorageRenamesPendingToFinal() throws IOException {
		ConfigBinaryFileAttribute attribute = newPersistedAttribute(12, 483);
		Path pendingPath = attribute.getPendingFilePath();
		Files.write(pendingPath, "hello".getBytes());

		attribute.finalizeStorage();

		Path expectedFinalPath = pendingPath.getParent()
			.resolve("12_cfg483_backupArchive_%s.data".formatted(attribute.getUid()));
		Assertions.assertFalse(Files.exists(pendingPath), "The pending file should have been renamed away");
		Assertions.assertTrue(Files.exists(expectedFinalPath), "The final file should now exist");
		Assertions.assertEquals(expectedFinalPath, attribute.getFilePath());
	}

	@Test
	@DisplayName("finalizeStorage() is a no-op when called again after already finalizing")
	void finalizeStorageIsIdempotent() throws IOException {
		ConfigBinaryFileAttribute attribute = newPersistedAttribute(12, 483);
		Path pendingPath = attribute.getPendingFilePath();
		Files.write(pendingPath, "hello".getBytes());

		attribute.finalizeStorage();
		Path finalPath = attribute.getFilePath();

		Assertions.assertDoesNotThrow(attribute::finalizeStorage);
		Assertions.assertEquals(finalPath, attribute.getFilePath());
		Assertions.assertTrue(Files.exists(finalPath));
	}

	@Test
	@DisplayName("getFilePath() falls back to the pending path when the file hasn't been finalized yet")
	void getFilePathFallsBackToPendingWhenNotYetFinalized() throws IOException {
		// Simulates a persisted attribute whose finalizeStorage() never ran
		// (e.g. process crash between commit() and finalizeStorage()), or
		// whose rename attempt failed - either way, nothing exists yet at
		// the final path, only at the pending one.
		ConfigBinaryFileAttribute attribute = newPersistedAttribute(12, 483);
		Path pendingPath = attribute.getPendingFilePath();
		Files.write(pendingPath, "hello".getBytes());

		Assertions.assertEquals(pendingPath, attribute.getFilePath(),
			"getFilePath() should resolve to the pending path when the final one doesn't exist yet");
	}

	@Test
	@DisplayName("getFilePath() falls back to the legacy <uid>.data name for a pre-feature row")
	void getFilePathFallsBackToLegacyName() throws IOException {
		ConfigBinaryFileAttribute attribute = newPersistedAttribute(12, 483);
		Path legacyPath = attribute.getPendingFilePath().getParent()
			.resolve("%s.data".formatted(attribute.getUid()));
		Files.write(legacyPath, "hello".getBytes());

		Assertions.assertEquals(legacyPath, attribute.getFilePath(),
			"With neither the final nor pending file present, getFilePath() should fall back to the legacy name");
	}
}
