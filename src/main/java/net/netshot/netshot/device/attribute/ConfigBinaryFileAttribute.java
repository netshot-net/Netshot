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
package net.netshot.netshot.device.attribute;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.device.Config;
import net.netshot.netshot.rest.RestViews.DefaultView;

@Slf4j
@Entity
@DiscriminatorValue("F")
public final class ConfigBinaryFileAttribute extends ConfigAttribute {


	/**
	 * Settings/config for the current class.
	 */
	public static final class Settings {
		/** Where the binary files are stored. */
		@Getter
		private Path storageFolderPath;

		/**
		 * Load settings from config.
		 */
		private void load() {
			String folder = Netshot.getConfig("netshot.snapshots.binary.path",
					"/var/local/netshot");
			try {
				this.storageFolderPath = Path.of(folder).normalize();
				if (!Files.isDirectory(this.storageFolderPath)) {
					log.error("Storage path '{}' for binary files is not a folder. Binary files won't be saved!",
						this.storageFolderPath);
					this.storageFolderPath = null;
				}
			}
			catch (InvalidPathException e) {
				log.error(
					"Invalid configured storage path '{}' for binary files. Binary files won't be saved!",
					folder, e);
				this.storageFolderPath = null;
			}
		}
	}

	/** Settings for this class. */
	public static final Settings SETTINGS = new Settings();

	/**
	 * Load the main policy from configuration.
	 */
	public static void loadConfig() {
		ConfigBinaryFileAttribute.SETTINGS.load();
	}

	/**
	 * Create a temporary folder for binary file attributes based on given prefix.
	 * @param prefix the prefix for the temporary folder
	 * @return the path to the temporary folder
	 * @throws IOException if the folder cannot be created
	 */
	public static Path makeTempFolder(String prefix) throws IOException {
		if (ConfigBinaryFileAttribute.SETTINGS.storageFolderPath == null) {
			throw new IllegalStateException(
				"Cannot get a folder path to save temporary binary file attribute. "
				+ "Is netshot.snapshots.binary.path defined?");
		}
		Path folder = Files.createTempDirectory(ConfigBinaryFileAttribute.SETTINGS.storageFolderPath, prefix);
		folder.toFile().deleteOnExit();
		return folder;
	}

	/** Unique ID generated before saving. */
	@Getter(onMethod = @__({
		@Column(unique = true)
	}))
	@Setter
	private String uid;

	/** Original name of the file. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String originalName;

	/** File size (in bytes). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private long fileSize;

	/** File checksum (SHA256). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String checksum;

	protected ConfigBinaryFileAttribute() {
	}

	public ConfigBinaryFileAttribute(Config config, String name, String originalName) {
		super(config, name);
		this.uid = UUID.randomUUID().toString();
		this.originalName = originalName;
	}

	/**
	 * Format the visible/final on-disk name for this attribute, given a config ID.
	 * @param configId the ID of the owning config (0 while still pending/transient)
	 * @return the formatted file name (no folder, no dot prefix)
	 */
	private String finalName(long configId) {
		return "%d_cfg%d_%s_%s.data".formatted(
			this.getConfig().getDevice().getId(), configId, this.getName(), this.getUid());
	}

	/**
	 * Get the full filename where to store data of the configuration attribute.
	 * @return a File object, where to store data of the attribute
	 * @throws IllegalAccessException
	 */
	@Transient
	public Path getFilePath() {
		if (ConfigBinaryFileAttribute.SETTINGS.storageFolderPath == null) {
			throw new IllegalStateException(
				"Cannot get a file path to save binary file attribute. "
				+ "Is netshot.snapshots.binary.path defined?");
		}
		if (this.getId() == 0) {
			// Not yet persisted (mid-snapshot): the owning config has no DB id yet.
			return this.getPendingFilePath();
		}
		Path finalPath = ConfigBinaryFileAttribute.SETTINGS.storageFolderPath
			.resolve(this.finalName(this.getConfig().getId()))
			.normalize();
		if (Files.exists(finalPath)) {
			return finalPath;
		}
		Path pendingPath = this.getPendingFilePath();
		if (Files.exists(pendingPath)) {
			// Persisted, but not renamed yet (e.g. crash between commit and finalizeStorage()).
			return pendingPath;
		}
		// Pre-feature (legacy) row, or the file is genuinely missing.
		return ConfigBinaryFileAttribute.SETTINGS.storageFolderPath
			.resolve("%s.data".formatted(this.getUid()))
			.normalize();
	}

	/**
	 * Get the path where this attribute's data lives while its owning config is
	 * still transient (not yet persisted), dot-prefixed so it stays hidden from
	 * a plain directory listing. This is also where SCP/SFTP/HTTP downloads and
	 * device-pushed ticket uploads write the file directly.
	 * @return the pending file path
	 */
	@Transient
	public Path getPendingFilePath() {
		if (ConfigBinaryFileAttribute.SETTINGS.storageFolderPath == null) {
			throw new IllegalStateException(
				"Cannot get a file path to save binary file attribute. "
				+ "Is netshot.snapshots.binary.path defined?");
		}
		return ConfigBinaryFileAttribute.SETTINGS.storageFolderPath
			.resolve(".tmp.%s".formatted(this.finalName(0)))
			.normalize();
	}

	/**
	 * Rename this attribute's data file from its pending name to its final name,
	 * now that the owning config has been persisted and has a real ID. Safe to
	 * call more than once (a no-op once already finalized).
	 */
	public void finalizeStorage() {
		Path pendingPath = this.getPendingFilePath();
		if (!Files.exists(pendingPath)) {
			// Already finalized in an earlier run, or nothing was ever written.
			return;
		}
		Path finalPath = ConfigBinaryFileAttribute.SETTINGS.storageFolderPath
			.resolve(this.finalName(this.getConfig().getId()))
			.normalize();
		try {
			Files.move(pendingPath, finalPath);
		}
		catch (IOException e) {
			log.warn("Unable to rename binary file attribute {} to its final storage name.", this.getUid(), e);
		}
	}

	@Override
	@Transient
	public String getAsText() {
		return "";
	}

	@Override
	@Transient
	public Object getData() {
		return null;
	}

	@Override
	public boolean valueEquals(ConfigAttribute obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ConfigBinaryFileAttribute)) {
			return false;
		}
		ConfigBinaryFileAttribute other = (ConfigBinaryFileAttribute) obj;
		if (this.fileSize != other.fileSize) {
			return false;
		}
		if (originalName == null) {
			if (other.originalName != null) {
				return false;
			}
		}
		else if (!this.originalName.equals(other.originalName)) {
			return false;
		}
		if (this.uid == null) {
			if (other.uid != null) {
				return false;
			}
		}
		else if (!this.uid.equals(other.uid)) {
			return false;
		}
		return true;
	}

}
