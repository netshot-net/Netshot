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
package net.netshot.netshot.driver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.testing.UnitTest;

/**
 * Tests for {@link ConfigBinaryFileAttribute}'s pending-to-final storage
 * lifecycle: filenames before/after the owning config is "persisted"
 * (given here as IDs, without a real database).
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
	}

	/** The ID of the device of the attribute. */
	private static final long DEVICE_ID = 12;

	/** The ID of the configuration of the attribute, once it is stored. */
	private static final long CONFIG_ID = 483;

	private static ConfigBinaryFileAttribute newAttribute() {
		return ConfigBinaryFileAttribute.create("backupArchive", "backup.tar.gz");
	}

	@Test
	@DisplayName("finalizeStorage() renames the pending file to its final name once the config has a real id")
	void finalizeStorageRenamesPendingToFinal() throws IOException {
		ConfigBinaryFileAttribute attribute = newAttribute();
		Path pendingPath = attribute.getPendingFilePath(DEVICE_ID);
		Files.write(pendingPath, "hello".getBytes());

		attribute.finalizeStorage(DEVICE_ID, CONFIG_ID);

		Path expectedFinalPath = pendingPath.getParent()
			.resolve("12_cfg483_backupArchive_%s.data".formatted(attribute.uid()));
		Assertions.assertFalse(Files.exists(pendingPath), "The pending file should have been renamed away");
		Assertions.assertTrue(Files.exists(expectedFinalPath), "The final file should now exist");
		Assertions.assertEquals(expectedFinalPath, attribute.getFilePath(DEVICE_ID, CONFIG_ID));
	}

	@Test
	@DisplayName("finalizeStorage() is a no-op when called again after already finalizing")
	void finalizeStorageIsIdempotent() throws IOException {
		ConfigBinaryFileAttribute attribute = newAttribute();
		Path pendingPath = attribute.getPendingFilePath(DEVICE_ID);
		Files.write(pendingPath, "hello".getBytes());

		attribute.finalizeStorage(DEVICE_ID, CONFIG_ID);
		Path finalPath = attribute.getFilePath(DEVICE_ID, CONFIG_ID);

		Assertions.assertDoesNotThrow(() -> attribute.finalizeStorage(DEVICE_ID, CONFIG_ID));
		Assertions.assertEquals(finalPath, attribute.getFilePath(DEVICE_ID, CONFIG_ID));
		Assertions.assertTrue(Files.exists(finalPath));
	}

	@Test
	@DisplayName("getFilePath() falls back to the pending path when the file hasn't been finalized yet")
	void getFilePathFallsBackToPendingWhenNotYetFinalized() throws IOException {
		// Simulates a persisted attribute whose finalizeStorage() never ran
		// (e.g. process crash between commit() and finalizeStorage()), or
		// whose rename attempt failed - either way, nothing exists yet at
		// the final path, only at the pending one.
		ConfigBinaryFileAttribute attribute = newAttribute();
		Path pendingPath = attribute.getPendingFilePath(DEVICE_ID);
		Files.write(pendingPath, "hello".getBytes());

		Assertions.assertEquals(pendingPath, attribute.getFilePath(DEVICE_ID, CONFIG_ID),
			"getFilePath() should resolve to the pending path when the final one doesn't exist yet");
	}

	@Test
	@DisplayName("getFilePath() falls back to the legacy <uid>.data name for a pre-feature row")
	void getFilePathFallsBackToLegacyName() throws IOException {
		ConfigBinaryFileAttribute attribute = newAttribute();
		Path legacyPath = attribute.getPendingFilePath(DEVICE_ID).getParent()
			.resolve("%s.data".formatted(attribute.uid()));
		Files.write(legacyPath, "hello".getBytes());

		Assertions.assertEquals(legacyPath, attribute.getFilePath(DEVICE_ID, CONFIG_ID),
			"With neither the final nor pending file present, getFilePath() should fall back to the legacy name");
	}
}
