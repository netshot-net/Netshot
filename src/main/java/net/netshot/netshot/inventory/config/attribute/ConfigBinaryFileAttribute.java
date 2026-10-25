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
package net.netshot.netshot.inventory.config.attribute;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;

/**
 * A configuration attribute holding a binary file, stored in the storage folder (not in the
 * database). The name of the stored file is made of the IDs of the device and of the
 * configuration: while the configuration isn't stored yet, the file has a pending name.
 *
 * @param name the name of the attribute
 * @param uid the unique ID of the file
 * @param originalName the original name of the file
 * @param fileSize the size of the file, in bytes
 * @param checksum the checksum of the file ({@code sha256:<hex>})
 */
@Slf4j
public record ConfigBinaryFileAttribute(
	String name,
	String uid,
	String originalName,
	long fileSize,
	String checksum) implements ConfigAttribute {

	/**
	 * The settings of the binary file storage.
	 *
	 * @param storageFolderPath where the binary files are stored (null if not usable)
	 */
	public record Settings(Path storageFolderPath) {

		/** Where the binary files are stored. */
		static final Setting<String> BINARY_PATH = Setting.text("netshot.snapshots.binary.path")
			.defaultValue("/var/local/netshot").live();

		/**
		 * Reads the settings.
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			String folder = config.get(BINARY_PATH);
			try {
				Path storageFolderPath = Path.of(folder).normalize();
				if (!Files.isDirectory(storageFolderPath)) {
					log.error("Storage path '{}' for binary files is not a folder. Binary files won't be saved!",
						storageFolderPath);
					return new Settings(null);
				}
				return new Settings(storageFolderPath);
			}
			catch (InvalidPathException e) {
				log.error(
					"Invalid configured storage path '{}' for binary files. Binary files won't be saved!",
					folder, e);
				return new Settings(null);
			}
		}
	}

	/** The settings, from the configuration. */
	private static final ConfigStore.Binding<Settings> SETTINGS = Netshot.config().bind(Settings::from);

	/**
	 * Gets the settings of the binary file storage.
	 * @return the settings
	 */
	public static Settings settings() {
		return SETTINGS.get();
	}

	/**
	 * Create a temporary folder, within the storage folder.
	 *
	 * @param prefix prefix for the folder name
	 * @return the created folder
	 * @throws IOException if the folder can't be created
	 */
	public static Path makeTempFolder(String prefix) throws IOException {
		Path storageFolderPath = ConfigBinaryFileAttribute.settings().storageFolderPath();
		if (storageFolderPath == null) {
			throw new IllegalStateException(
				"Cannot get a folder path to save temporary binary file attribute. "
				+ "Is netshot.snapshots.binary.path defined?");
		}
		Path folder = Files.createTempDirectory(storageFolderPath, prefix);
		folder.toFile().deleteOnExit();
		return folder;
	}

	/**
	 * Builds a new binary file attribute (with a new unique ID), its file still to be stored.
	 *
	 * @param name the name of the attribute
	 * @param originalName the original name of the file
	 * @return the attribute
	 */
	public static ConfigBinaryFileAttribute create(String name, String originalName) {
		return new ConfigBinaryFileAttribute(name, UUID.randomUUID().toString(), originalName, 0, null);
	}

	/**
	 * Gets the same attribute, with the size and checksum of its file.
	 *
	 * @param newFileSize the size of the file
	 * @param newChecksum the checksum of the file
	 * @return the attribute
	 */
	public ConfigBinaryFileAttribute withFile(long newFileSize, String newChecksum) {
		return new ConfigBinaryFileAttribute(this.name, this.uid, this.originalName, newFileSize, newChecksum);
	}

	private String finalName(long deviceId, long configId) {
		return "%d_cfg%d_%s_%s.data".formatted(deviceId, configId, this.name, this.uid);
	}

	private static Path storageFolder() {
		Path storageFolderPath = ConfigBinaryFileAttribute.settings().storageFolderPath();
		if (storageFolderPath == null) {
			throw new IllegalStateException(
				"Cannot get a file path to save binary file attribute. "
				+ "Is netshot.snapshots.binary.path defined?");
		}
		return storageFolderPath;
	}

	/**
	 * Gets the path of the file. For historical reasons, a file may be found under its
	 * pending name, or under its unique ID only.
	 *
	 * @param deviceId the ID of the device
	 * @param configId the ID of the configuration, or 0 if it isn't stored yet
	 * @return the path of the file
	 */
	public Path getFilePath(long deviceId, long configId) {
		Path folder = storageFolder();
		if (configId == 0) {
			return this.getPendingFilePath(deviceId);
		}
		Path finalPath = folder.resolve(this.finalName(deviceId, configId)).normalize();
		if (Files.exists(finalPath)) {
			return finalPath;
		}
		Path pendingPath = this.getPendingFilePath(deviceId);
		if (Files.exists(pendingPath)) {
			return pendingPath;
		}
		return folder.resolve("%s.data".formatted(this.uid)).normalize();
	}

	/**
	 * Gets the path of the file while its configuration isn't stored yet.
	 *
	 * @param deviceId the ID of the device
	 * @return the pending path of the file
	 */
	public Path getPendingFilePath(long deviceId) {
		return storageFolder().resolve(".tmp.%s".formatted(this.finalName(deviceId, 0))).normalize();
	}

	/**
	 * Gives the file its final name, once its configuration is stored.
	 *
	 * @param deviceId the ID of the device
	 * @param configId the ID of the configuration
	 */
	public void finalizeStorage(long deviceId, long configId) {
		Path pendingPath = this.getPendingFilePath(deviceId);
		if (!Files.exists(pendingPath)) {
			return;
		}
		Path finalPath = storageFolder().resolve(this.finalName(deviceId, configId)).normalize();
		try {
			Files.move(pendingPath, finalPath);
		}
		catch (IOException e) {
			log.warn("Unable to rename binary file attribute {} to its final storage name.", this.uid, e);
		}
	}

	@Override
	public String getAsText() {
		return "";
	}

	@Override
	public Object getData() {
		return null;
	}

	@Override
	public boolean valueEquals(ConfigAttribute other) {
		return other instanceof ConfigBinaryFileAttribute o && this.fileSize == o.fileSize
			&& Objects.equals(this.originalName, o.originalName) && Objects.equals(this.uid, o.uid);
	}
}
