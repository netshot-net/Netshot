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
package net.netshot.netshot.inventory.config.attribute;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.config.ConfigStore;

/**
 * The storage of the files of the binary file attributes, in the folder of the configuration
 * ({@code netshot.snapshots.binary.path}). The name of a stored file is made of the IDs of the device and of the
 * configuration: while the configuration isn't stored yet, the file has a pending name.
 */
@Slf4j
public final class BinaryFileStorage {

	/** The settings, from the configuration. */
	private final ConfigStore.Binding<ConfigBinaryFileAttribute.Settings> settings;

	/**
	 * Binds the storage to the configuration (once: a binding lives as long as the configuration store).
	 *
	 * @param config the configuration store
	 */
	public BinaryFileStorage(ConfigStore config) {
		this.settings = config.bind(ConfigBinaryFileAttribute.Settings::from);
	}

	/**
	 * Gets the storage folder.
	 *
	 * @return the folder, or null if it isn't usable
	 */
	public Path folderOrNull() {
		return this.settings.get().storageFolderPath();
	}

	private Path folder() {
		Path storageFolderPath = this.folderOrNull();
		if (storageFolderPath == null) {
			throw new IllegalStateException(
				"Cannot get a file path to save binary file attribute. "
				+ "Is netshot.snapshots.binary.path defined?");
		}
		return storageFolderPath;
	}

	/**
	 * Create a temporary folder, within the storage folder.
	 *
	 * @param prefix prefix for the folder name
	 * @return the created folder
	 * @throws IOException if the folder can't be created
	 */
	public Path makeTempFolder(String prefix) throws IOException {
		Path storageFolderPath = this.folderOrNull();
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
	 * Gets the path of the file of an attribute. For historical reasons, a file may be found under its
	 * pending name, or under its unique ID only.
	 *
	 * @param attribute the attribute
	 * @param deviceId the ID of the device
	 * @param configId the ID of the configuration, or 0 if it isn't stored yet
	 * @return the path of the file
	 */
	public Path filePath(ConfigBinaryFileAttribute attribute, long deviceId, long configId) {
		Path folder = this.folder();
		if (configId == 0) {
			return this.pendingFilePath(attribute, deviceId);
		}
		Path finalPath = folder.resolve(attribute.finalName(deviceId, configId)).normalize();
		if (Files.exists(finalPath)) {
			return finalPath;
		}
		Path pendingPath = this.pendingFilePath(attribute, deviceId);
		if (Files.exists(pendingPath)) {
			return pendingPath;
		}
		return folder.resolve("%s.data".formatted(attribute.uid())).normalize();
	}

	/**
	 * Gets the path of the file of an attribute while its configuration isn't stored yet.
	 *
	 * @param attribute the attribute
	 * @param deviceId the ID of the device
	 * @return the pending path of the file
	 */
	public Path pendingFilePath(ConfigBinaryFileAttribute attribute, long deviceId) {
		return this.folder().resolve(".tmp.%s".formatted(attribute.finalName(deviceId, 0))).normalize();
	}

	/**
	 * Gives the file of an attribute its final name, once its configuration is stored.
	 *
	 * @param attribute the attribute
	 * @param deviceId the ID of the device
	 * @param configId the ID of the configuration
	 */
	public void finalizeStorage(ConfigBinaryFileAttribute attribute, long deviceId, long configId) {
		Path pendingPath = this.pendingFilePath(attribute, deviceId);
		if (!Files.exists(pendingPath)) {
			return;
		}
		Path finalPath = this.folder().resolve(attribute.finalName(deviceId, configId)).normalize();
		try {
			Files.move(pendingPath, finalPath);
		}
		catch (IOException e) {
			log.warn("Unable to rename binary file attribute {} to its final storage name.", attribute.uid(), e);
		}
	}
}
