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
package net.netshot.netshot.device.script.helper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.crypto.Argon2idHash;
import net.netshot.netshot.crypto.Hash;
import net.netshot.netshot.crypto.Sha2BasedHash;
import net.netshot.netshot.device.Config;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.NetworkAddress;
import net.netshot.netshot.device.access.Cli;
import net.netshot.netshot.device.access.Ssh;
import net.netshot.netshot.device.attribute.AttributeDefinition;
import net.netshot.netshot.device.attribute.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.device.attribute.AttributeDefinition.AttributeType;
import net.netshot.netshot.device.collector.Collector;
import net.netshot.netshot.device.collector.SshServer;
import net.netshot.netshot.device.collector.SshServer.SessionLogBuffer;
import net.netshot.netshot.device.collector.TransferProtocol;
import net.netshot.netshot.device.collector.UploadTicket;
import net.netshot.netshot.utils.PasswordGenerator;
import net.netshot.netshot.device.attribute.ConfigBinaryAttribute;
import net.netshot.netshot.device.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.device.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.device.attribute.ConfigNumericAttribute;
import net.netshot.netshot.device.attribute.ConfigTextAttribute;
import net.netshot.netshot.work.TaskContext;

/**
 * Class used to control a device config from JavaScript.
 */
@Slf4j
public final class JsConfigHelper implements UploadTicket.Owner {

	/**
	 * Uploaded file metadata.
	 */
	private class UploadedFile {
		private long id;
		private Path path;
		private String name;
		private long size;

		UploadedFile(long id, Path path) {
			this.id = id;
			this.path = path;
			this.name = path.getFileName().toString();
			this.size = path.toFile().length();
		}

		public long getId() {
			return this.id;
		}

		public Path getPath() {
			return this.path;
		}

		public String getName() {
			return this.name;
		}

		public long getSize() {
			return this.size;
		}
	}

	private class ConfigUploadTicket implements UploadTicket {

		private long id;
		private Set<TransferProtocol> protocols;
		private String username;
		private String password;
		private Hash passwordHash;
		private NetworkAddress expectedSourceIp;
		private Path rootPath;
		private Map<Long, UploadedFile> uploadedFiles = new HashMap<>();
		private boolean sessionCompleted = false;
		boolean valid = true;

		ConfigUploadTicket(long id, Set<TransferProtocol> protocols,
				String username, String password, NetworkAddress expectedSourceIp,
				Path rootPath) {
			this.id = id;
			this.protocols = protocols;
			this.username = username;
			this.password = password;
			this.passwordHash = new Argon2idHash(password);
			this.expectedSourceIp = expectedSourceIp;
			this.rootPath = rootPath;
		}

		@Override
		public Owner getOwner() {
			return JsConfigHelper.this;
		}

		@Override
		public Set<TransferProtocol> getAllowedProtocols() {
			return this.protocols;
		}

		@Override
		public String getUsername() {
			return this.username;
		}

		@Override
		public boolean checkPassword(NetworkAddress source, String givenPassword) {
			if (this.expectedSourceIp != null && Objects.equals(this.expectedSourceIp, source)) {
				log.warn("Error during snapshot while requesting upload ticket: "
					+ "invalid source IP {} vs {}.", source, this.expectedSourceIp);
				taskContext.error("Error while requesting upload ticket: "
					+ "invalid source IP {} vs {}.", source, this.expectedSourceIp);
				return false;
			}
			return this.passwordHash.check(givenPassword);
		}

		@Override
		public Path getRootPath() {
			return this.rootPath;
		}

		@Override
		public boolean isValid() {
			return this.valid;
		}

		private void clearTextPassword() {
			this.password = null;
		}

		@Override
		public boolean onFileWritten(Path filePath) {
			synchronized (this) {
				if (!this.valid) {
					log.debug("File written callback on {} while ticket is not valid anymore", filePath);
					return false;
				}
				// Use next available ID (size + 1 ensures unique incrementing IDs)
				long fileId = this.uploadedFiles.size() + 1;
				UploadedFile file = new UploadedFile(fileId, filePath);
				this.uploadedFiles.put(fileId, file);
				log.debug("File uploaded via ticket {}: {} (id: {}, size: {})",
					this.id, filePath, fileId, file.getSize());
				taskContext.debug("File uploaded via ticket {}: {} (id: {}, size: {})",
					this.id, filePath, fileId, file.getSize());
				return true;
			}
		}

		@Override
		public void onSessionStarted(SessionLogBuffer logBuffer) {
			log.info("Upload session started for ticket {}", this.id);
			taskContext.info("Upload session started for ticket {}", this.id);
			// Indent session logs
			StringBuffer logs = new StringBuffer();
			for (String line : logBuffer.getLogs().split("\n")) {
				logs.append("  ").append(line).append("\n");
			}
			taskContext.trace("Session logs for ticket {}:\n{}", this.id, logs);
		}

		@Override
		public void onSessionStopped() {
			synchronized (this) {
				this.sessionCompleted = true;
				int fileCount = this.uploadedFiles.size();
				log.info("Upload session stopped for ticket {}, {} file(s) uploaded",
					this.id, fileCount);
				taskContext.info("Upload session stopped for ticket {}, {} file(s) uploaded",
					this.id, fileCount);
				this.notifyAll();
			}
		}

		public Map<Long, UploadedFile> getUploadedFiles() {
			synchronized (this) {
				return new HashMap<>(this.uploadedFiles);
			}
		}

		public UploadedFile getUploadedFile(long fileId) {
			synchronized (this) {
				return this.uploadedFiles.get(fileId);
			}
		}

		public boolean awaitCompletion(long timeout) throws IOException {
			synchronized (this) {
				if (!this.sessionCompleted) {
					try {
						this.wait(timeout);
					}
					catch (InterruptedException e) {
						throw new IOException(e);
					}
				}
				return this.sessionCompleted;
			}
		}

		public void cleanUp() {
			synchronized (this) {
				this.valid = false;
				log.debug("Removing temporary upload directory {}", this.getRootPath());
				try {
					FileUtils.deleteDirectory(this.getRootPath().toFile());
				}
				catch (IOException e) {
					log.warn("Error while removing upload directory {}", this.getRootPath(), e);
				}
			}
			SshServer.getServer().clearUploadTickets(JsConfigHelper.this);
		}
		
	}

	private final Device device;
	private Config config;
	private Config lastConfig;
	private TaskContext taskContext;
	private JsCliHelper defaultCliHelper;

	/** Upload tickets of this config helper context. */
	private Map<Long, ConfigUploadTicket> uploadTickets = new HashMap<>();

	public JsConfigHelper(Device device, Config config, Config lastConfig,
		JsCliHelper defaultCliHelper, TaskContext taskContext) {
		this.device = device;
		this.config = config;
		this.lastConfig = lastConfig;
		this.defaultCliHelper = defaultCliHelper;
		this.taskContext = taskContext;
	}

	/**
	 * To be called when the helper needs to release its resources or files.
	 */
	public void release() {
		for (ConfigUploadTicket ticket : this.uploadTickets.values()) {
			ticket.cleanUp();
		}
	}

	@Export
	public void set(String key, Value value) {
		if (value == null) {
			return;
		}
		try {
			if ("author".equals(key)) {
				config.setAuthor(value.asString());
				taskContext.trace("Setting config author to '{}'.", value.asString());
			}
			else {
				DeviceDriver driver = device.getDeviceDriver();
				for (AttributeDefinition attribute : driver.getAttributes()) {
					if (attribute.getLevel().equals(AttributeLevel.CONFIG) && attribute.getName().equals(key)) {
						switch (attribute.getType()) {
							case BINARY:
								config.addAttribute(new ConfigBinaryAttribute(config, key, value.asBoolean()));
								taskContext.trace("Setting config attribute '{}' to {}.", key, value.asBoolean());
								break;
							case NUMERIC:
								config.addAttribute(new ConfigNumericAttribute(config, key, value.asDouble()));
								taskContext.trace("Setting config attribute '{}' to {}.", key, value.asDouble());
								break;
							case LONGTEXT:
								config.addAttribute(new ConfigLongTextAttribute(config, key, value.asString()));
								taskContext.trace("Setting config attribute '{}' to '{}'.", key, value.asString());
								break;
							case TEXT:
								config.addAttribute(new ConfigTextAttribute(config, key, value.asString()));
								taskContext.trace("Setting config attribute '{}' to '{}'.", key, value.asString());
								break;
							default:
						}
						break;
					}
				}
			}
		}
		catch (Exception e) {
			log.warn("Error during snapshot while setting config attribute key '{}'.", key, e);
		}
	}

	/**
	 * Compute a custom hash based on the inputs.
	 * @param inputs = the list of strings to hash
	 */
	@Export
	public void computeCustomHash(String[] inputs) {
		Sha2BasedHash digester = new Sha2BasedHash();
		digester.setSalt(null);
		digester.setIterations(1);
		digester.setAlgorithm("SHA-256");
		digester.digest(inputs);
		String hash = digester.toHashString();
		log.debug("Computed custom hash is {}", hash);
		config.setCustomHash(hash);
	}

	@Export
	public String getCustomHash() {
		return config.getCustomHash();
	}

	@Export
	public String getLastCustomHash() {
		return lastConfig.getCustomHash();
	}

	private String checkFileSha256(Path filePath, String expected) throws IOException {
		try (InputStream is = Files.newInputStream(filePath)) {
			String computed = DigestUtils.sha256Hex(is).toLowerCase();
			if (!expected.equalsIgnoreCase(computed)) {
				log.warn("Invalid computed SHA256 hash for received file: {} vs {}", computed, expected);
				throw new IllegalArgumentException(
					"Invalid computed SHA256 hash for received file: %s vs %s".formatted(computed, expected));
			}
			taskContext.debug("Checksum of downloaded file was successfully verified (%s).".formatted(computed));
			return computed;
		}
	}

	private String checkFileMd5(Path filePath, String expected) throws IOException {
		try (InputStream is = Files.newInputStream(filePath)) {
			String computed = DigestUtils.md5Hex(is).toLowerCase();
			if (!expected.equalsIgnoreCase(computed)) {
				log.warn("Invalid computed MD5 hash for received file: {} vs {}", computed, expected);
				throw new IllegalArgumentException(
					"Invalid computed MD5 hash for received file: %s vs %s".formatted(computed, expected));
			}
			taskContext.debug("Checksum of downloaded file was successfully verified (%s).".formatted(computed));
			return computed;
		}
	}

	/**
	 * Compute hash (digest) of given downloaded file and compare to passed checkum.
	 * @param filePath = the file path to digest
	 * @param expected = the expected checkum (hex-based), either MD5 or SHA256
	 * @throws IOException
	 */
	private void checkFileSum(Path filePath, String expected) throws IOException {
		if (expected == null) {
			log.info("Skipping verification of downloaded file (no checksum was provided).");
			taskContext.debug("Skipping verification of downloaded file (no checksum provided).");
			return;
		}
		if (expected.length() == 32) {
			checkFileMd5(filePath, expected);
		}
		else if (expected.length() == 64) {
			checkFileSha256(filePath, expected);
		}
		else {
			log.warn(
				"Invalid or unsupported passed file checksum length ({} characters)",
				expected.length());
			throw new IllegalArgumentException(
				"Invalid or unsupported passed file checksum length (%d characters).".formatted(expected.length()));
		}
	}

	/**
	 * Normalize the storage file name.
	 * @param requestedStoreName the requested store name
	 * @param remoteFileName the remote file name
	 * @return the normalized store name
	 */
	private String normalizeStoreName(String requestedStoreName, String remoteFileName) {
		String storeName = requestedStoreName;
		if (storeName != null) {
			storeName = storeName.trim();
			if ("".equals(storeName)) {
				storeName = null;
			}
		}
		if (storeName == null) {
			try {
				storeName = Paths.get(remoteFileName).getFileName().toString();
				storeName = storeName.trim();
				storeName = storeName.replaceAll("[^0-9_a-zA-Z\\(\\)\\%\\-\\.]", "");
			}
			catch (Exception e) {
				// Go on
			}
		}
		if (storeName != null) {
			storeName = storeName.trim();
			if ("".equals(storeName)) {
				storeName = null;
			}
		}
		return storeName;
	}

	/**
	 * Download a binary file from the device, using SCP.
	 * @param attribute the config attribute definition
	 * @param protocol the transfer protocol (SFTP or SCP)
	 * @param remoteFileName the file (including full path) to download from the device
	 * @param storeFileName the file name to store (null to use remoreFileName)
	 * @param newSession use a new SSH session to download the file
	 * @param expectedHash if not null verify the hash of the received file (SHA256 or MD5)
	 * @throws Exception if an error occurs during the download
	 */
	public void download(AttributeDefinition attribute, TransferProtocol protocol, String remoteFileName, String storeFileName,
		boolean newSession, String expectedHash) throws Exception {

		if (!AttributeType.BINARYFILE.equals(attribute.getType())) {
			log.warn("Error during snapshot: can't use download method on attribute '{}'"
				+ " (not a binary file attribute).", attribute.getName());
			taskContext.error("Can't use download method on attribute '{}'"
				+ " (not a binary file attribute).", attribute.getName());
			throw new IllegalArgumentException(
				"Can't use SCP/SFTP download method on non-binary-file attribute");
		}

		if (remoteFileName == null) {
			log.warn("Error during snapshot, remote file name is missing in download request (for attribute '{}').", attribute.getName());
			taskContext.error("Remote file name is missing in download request (for attribute '{}').", attribute.getName());
			throw new IllegalArgumentException("Remote file name to download is missing in download request");
		}
		String storeName = this.normalizeStoreName(storeFileName, remoteFileName);
		if (storeName == null) {
			log.warn("Error during snapshot, unable to generate a store file name in download request (for attribute '{}').", attribute.getName());
			taskContext.error("Unable to generate a store file name in download request (for attribute '{}').", attribute.getName());
			throw new IllegalArgumentException("Unable to generate a proper store file name in download request");
		}

		if (TransferProtocol.SCP.equals(protocol) || TransferProtocol.SFTP.equals(protocol)) {
			if (defaultCliHelper == null) {
				throw new IllegalArgumentException("Can't use SCP/SFTP download method as no CLI access exists in the current context.");
			}

			Cli cli = defaultCliHelper.getUnderlyingCli();
			if (cli instanceof Ssh sshCli) {
				try {
					ConfigBinaryFileAttribute fileAttribute = new ConfigBinaryFileAttribute(config, attribute.getName(), storeName);
					Path tempPath = fileAttribute.getTempFilePath();
					tempPath.toFile().deleteOnExit();
					log.trace("Temporary file path for download is {}", tempPath);
					try {
						if (TransferProtocol.SCP.equals(protocol)) {
							sshCli.scpDownload(remoteFileName, tempPath, newSession);
						}
						else if (TransferProtocol.SFTP.equals(protocol)) {
							sshCli.sftpDownload(remoteFileName, tempPath, newSession);
						}
					}
					catch (IOException e) {
						log.debug("Removing temporary file path {}", tempPath);
						Files.deleteIfExists(tempPath);
						throw e;
					}
					this.finalizeBinaryFile(fileAttribute, tempPath, expectedHash);
					return;
				}
				catch (Exception e) {
					log.warn(
						"Error during snapshot while downloading file for attribute key '{}'.",
						attribute.getName());
					taskContext.error("Error while downloading file for attribute key {}: {}",
						attribute.getName(), e.getMessage());
					throw e;
				}
			}

			// Not SSH-based CLI session
			log.warn("Error during snapshot: can't use SCP/SFTP method with non-SSH CLI access (for attribute '{}').", attribute.getName());
			taskContext.error("Can't use SCP/SFTP method with non-SSH CLI access (for attribute '{}').", attribute.getName());
			throw new IllegalArgumentException("Can't use SCP/SFTP method with non-SSH CLI access.");
		}

		log.warn("Error during snapshot: unsupported download protocol '{}' (for attribute '{}').", protocol, attribute.getName());
		taskContext.error("Unsupported download protocol '{}' (for attribute '{}').", protocol, attribute.getName());
		throw new IllegalArgumentException("Unsupported download protocol.");
	}

	/**
	 * Verifies the checksum (if any), fills in the size/checksum of a
	 * {@link ConfigBinaryFileAttribute} and moves the already-downloaded file
	 * at {@code tempPath} into its final storage location, then attaches it
	 * to the current config. Shared tail of every "land a downloaded file
	 * into a BinaryFile config attribute" path - SCP/SFTP download above, and
	 * {@link net.netshot.netshot.device.script.helper.JsHttpHelper#download}
	 * for HTTP downloads (same package, calls this directly - there is no
	 * JS-facing "commit" step, since unlike an upload ticket's {@code commitUpload}
	 * (which accepts a file the *device* pushed on its own schedule), a
	 * download's temp file is produced synchronously by the same call that
	 * consumes it).
	 * @param fileAttribute the (not yet attached) binary file attribute to fill in
	 * @param tempPath the already-downloaded file to verify/move into place
	 * @param expectedHash if not null, the expected checksum (MD5 or SHA256) of the downloaded file
	 * @throws Exception if the checksum doesn't match, or the file can't be moved
	 */
	void finalizeBinaryFile(ConfigBinaryFileAttribute fileAttribute, Path tempPath, String expectedHash) throws Exception {
		Path targetPath = fileAttribute.getFilePath().toAbsolutePath();
		try {
			this.checkFileSum(tempPath, expectedHash);
			fileAttribute.setFileSize(tempPath.toFile().length());
			try (InputStream is = new FileInputStream(tempPath.toFile())) {
				String cs = "sha256:" + DigestUtils.sha256Hex(is).toLowerCase();
				log.trace("Computed SHA256 for the received file is {}", cs);
				fileAttribute.setChecksum(cs);
			}
			Files.move(tempPath, targetPath);
			config.addAttribute(fileAttribute);
		}
		catch (Exception e) {
			log.debug("Removing temporary file path {}", tempPath);
			Files.deleteIfExists(tempPath);
			throw e;
		}
	}

	/**
	 * Resolves and validates the (BinaryFile) config attribute a download is
	 * about to be stored into, and allocates the temporary file path it
	 * should be downloaded to - the HTTP counterpart to how the SCP/SFTP
	 * {@link #download} path above prepares its own {@code fileAttribute}/
	 * {@code tempPath} pair before handing them to {@code sshCli.scpDownload}/
	 * {@code sftpDownload}. Package-private: called directly (no JS
	 * involved) by {@link net.netshot.netshot.device.script.helper.JsHttpHelper#download}.
	 * @param key the name of the config attribute
	 * @param storeFileName the file name to store (null to derive one from {@code fallbackRemoteName})
	 * @param fallbackRemoteName used to derive a store file name when {@code storeFileName} is null
	 *        (typically the request path/URL that's about to be downloaded)
	 * @return the (not yet attached) binary file attribute, whose {@link ConfigBinaryFileAttribute#getTempFilePath()}
	 *         is where the caller should download the file to before calling {@link #finalizeBinaryFile}
	 * @throws Exception if the device driver can't be resolved, the attribute is unknown/not a BinaryFile,
	 *         or no store file name could be derived
	 */
	ConfigBinaryFileAttribute prepareBinaryFileDownload(String key, String storeFileName, String fallbackRemoteName) throws Exception {
		DeviceDriver driver = this.device.getDeviceDriver();
		AttributeDefinition attribute = driver.getAttributeDefinition(AttributeLevel.CONFIG, key);
		if (attribute == null) {
			log.warn("Error during snapshot, requested attribute key '{}' doesn't exist.", key);
			taskContext.error("Requested attribute key '{}' doesn't exist.", key);
			throw new IllegalArgumentException("Unknown attribute key");
		}
		if (!AttributeType.BINARYFILE.equals(attribute.getType())) {
			log.warn("Error during snapshot: can't download into attribute '{}' (not a binary file attribute).", attribute.getName());
			taskContext.error("Can't download into attribute '{}' (not a binary file attribute).", attribute.getName());
			throw new IllegalArgumentException("Can't use download method on non-binary-file attribute");
		}
		String storeName = this.normalizeStoreName(storeFileName, fallbackRemoteName);
		if (storeName == null) {
			log.warn("Error during snapshot, unable to generate a store file name for attribute '{}'.", attribute.getName());
			taskContext.error("Unable to generate a store file name for attribute '{}'.", attribute.getName());
			throw new IllegalArgumentException("Unable to generate a proper store file name");
		}
		return new ConfigBinaryFileAttribute(config, attribute.getName(), storeName);
	}

	/**
	 * Download a binary file from the device - to be called from JS.
	 * @param key the name of the config attribute
	 * @param method "scp" or "sftp"
	 * @param remoteFileName the file (including full path) to download from the device
	 * @param storeFileName the file name to store (null to use remoreFileName)
	 * @param newSession use a new SSH session to download the file
	 * @param expectedHash if not null verify the hash of the received file (SHA256 or MD5)
	 */
	@Export
	public void download(String key, String method, String remoteFileName, String storeFileName,
		boolean newSession, String expectedHash) throws Exception {

		TransferProtocol protocol = null;
		if (method != null) {
			protocol = TransferProtocol.valueOf(method.toUpperCase());
		}

		if (protocol == null) {
			log.warn("Error during snapshot, invalid download method '{}'.", method);
			taskContext.error("Invalid download method '{}'.", method);
			throw new IllegalArgumentException("Invalid download method");
		}

		DeviceDriver driver = this.device.getDeviceDriver();
		AttributeDefinition attribute = driver.getAttributeDefinition(AttributeLevel.CONFIG, key);
		if (attribute == null) {
			log.warn("Error during snapshot, requested attribute key '{}' doesn't exist.", key);
			taskContext.error("Requested attribute key '{}' doesn't exist.", key);
			throw new IllegalArgumentException("Unknown attribute key");
		}

		this.download(attribute, protocol, remoteFileName, storeFileName, newSession, expectedHash);
	}


	/**
	 * Request an upload ticket (SCP/SFTP).
	 * @param protocols the allowed transfer protocols
	 * @param sourceIp the expected source IP address
	 * @return the upload ticket
	 * @throws IOException if an error occurs
	 */
	public ConfigUploadTicket requestUpload(Set<TransferProtocol> protocols,
		NetworkAddress sourceIp) throws IOException {

		if (!SshServer.isRunning()) {
			log.warn("Error during snapshot while requesting upload ticket: "
				+ "the embedded SSH server is not running.");
			taskContext.error("Error while requesting upload ticket for key: "
				+ "the embedded SSH server is not running.");
			throw new IOException("The SSH server is not running.");
		}
		for (TransferProtocol protocol : protocols) {
			if (!SshServer.isRunning(protocol)) {
				log.warn("Error during snapshot while requesting upload ticket: "
					+ "the transfer protocol {} is not enabled.", protocol);
				taskContext.error("Error while requesting upload ticket: "
					+ "the transfer protocol {} is not enabled.", protocol);
				throw new IOException("The SSH server is not running.");
			}
		}

		long ticketId = this.uploadTickets.size() + 1;

		final String username = this.taskContext.getIdentifier()
			.replace("Task", "")
			.replace("_", "")
			.toLowerCase()
			+ "t%d".formatted(ticketId);
		String password = PasswordGenerator.generateAlphanumeric(24);

		Path rootPath = ConfigBinaryFileAttribute.makeTempFolder(username);

		ConfigUploadTicket ticket = new ConfigUploadTicket(ticketId,
			protocols, username, password, sourceIp, rootPath);
		this.uploadTickets.put(ticketId, ticket);

		SshServer.getServer().registerUploadTicket(ticket);
		return ticket;
	}

	@Export
	public ProxyObject requestUpload(String method, String sourceIp) throws Exception {

		Set<TransferProtocol> protocols =
			new HashSet<>(Arrays.asList(TransferProtocol.SCP, TransferProtocol.SFTP));
		if (method != null) {
			protocols = Collections.singleton(TransferProtocol.valueOf(method.toUpperCase()));
		}

		NetworkAddress sourceAddress = null;
		if (sourceIp != null) {
			try {
				sourceAddress = NetworkAddress.getNetworkAddress(sourceIp);
			}
			catch (Exception e) {
				log.warn("Error during snapshot, invalid source IP address '{}'.", sourceIp);
				taskContext.error("Invalid source IP address '{}'.", sourceIp);
				throw new IllegalArgumentException("Invalid source IP address");
			}
		}

		log.debug("Requesting upload ticket, protocols {}, source address {}",
			protocols, sourceAddress);
		this.taskContext.debug(
			"Requesting upload ticket, protocols {}, source address {}",
			protocols, sourceAddress);

		ConfigUploadTicket ticket = this.requestUpload(protocols, sourceAddress);

		Map<String, Object> hostKeys = new HashMap<>();
		for (PublicKey pk : SshServer.getServer().getPublicHostKeys()) {
			try {
				// Convert to OpenSSH format: "ssh-rsa AAAAB3NzaC1yc2E..."
				String opensshFormat = PublicKeyEntry.toString(pk);

				// Split to get key type and base64 data
				String[] parts = opensshFormat.split("\\s+", 2);
				if (parts.length >= 2) {
					hostKeys.put(parts[0], parts[1]);
				}
			}
			catch (Exception e) {
				log.warn("Unable to convert public key to OpenSSH format", e);
				taskContext.error("Unable to convert public key to OpenSSH format: {}", e.getMessage());
			}
		}

		final NetworkAddress host;
		try {
			host = Collector.getCollectorAddress(this.device.getMgmtDomain());
		}
		catch (Exception e) {
			log.warn("Unable to retrieve SSH server IP address for domain {}", this.device.getMgmtDomain(), e);
			taskContext.error("Unable to retrieve SSH server IP address for domain {}: {}",
				this.device.getMgmtDomain(), e.getMessage());
			throw e;
		}

		int port = SshServer.SETTINGS.getExternalTcpPort();

		log.debug("Upload ticket details: username '{}', password '{}...', host '{}:{}', hostkeys '{}'",
			ticket.username, ticket.password.substring(0, 1), host.getIp(), port, hostKeys);
		taskContext.debug("Upload ticket details: username '{}', password '{}...', host '{}:{}'",
			ticket.username, ticket.password.substring(0, 1), host.getIp(), port, hostKeys);

		Map<String, Object> result = new HashMap<>();
		result.put("id", ticket.id);
		result.put("username", ticket.username);
		result.put("password", ticket.password);
		result.put("host", host.getIp());
		result.put("port", port);
		result.put("hostkeys", ProxyObject.fromMap(hostKeys));
		ProxyObject proxy = ProxyObject.fromMap(result);

		// We don't need it anymore
		ticket.clearTextPassword();
		return proxy;
	}

	@Export
	public ProxyObject awaitUpload(long ticketId, long timeout) throws IOException {
		ConfigUploadTicket ticket = this.uploadTickets.get(ticketId);
		if (ticket == null) {
			log.warn("No such upload ticket {}", ticketId);
			this.taskContext.error("No such upload ticket {}", ticketId);
			return null;
		}
		log.debug("Waiting for upload ticket {} (timeout: {} ms)", ticketId, timeout);
		this.taskContext.info("Waiting for upload ticket {} (timeout: {} ms)", ticketId, timeout);

		boolean completed = ticket.awaitCompletion(timeout);

		if (!completed) {
			log.warn("Timeout while waiting for upload ticket {}", ticketId);
			this.taskContext.warn("Timeout while waiting for upload ticket {}", ticketId);
			throw new IOException("Timeout while waiting for upload ticket");
		}

		Map<Long, UploadedFile> uploadedFiles = ticket.getUploadedFiles();
		log.info("Upload ticket {} completed: {} file(s) uploaded", ticketId, uploadedFiles.size());
		this.taskContext.info("Upload ticket {} completed: {} file(s) uploaded", ticketId, uploadedFiles.size());

		// Convert uploaded files to list of objects for JavaScript
		List<Object> files = new ArrayList<>(uploadedFiles.size());
		for (UploadedFile file : uploadedFiles.values()) {
			Map<String, Object> fileInfo = new HashMap<>();
			fileInfo.put("id", file.getId());
			fileInfo.put("name", file.getName());
			fileInfo.put("size", file.getSize());
			files.add(ProxyObject.fromMap(fileInfo));
		}

		Map<String, Object> result = new HashMap<>();
		result.put("completed", completed);
		result.put("files", ProxyArray.fromList(files));
		result.put("fileCount", uploadedFiles.size());

		return ProxyObject.fromMap(result);
	}

	@Export
	public void commitUpload(long ticketId, long fileId, String key, String storeName, String expectedHash) throws Exception {
		ConfigUploadTicket ticket = this.uploadTickets.get(ticketId);
		if (ticket == null) {
			log.warn("No such upload ticket {}", ticketId);
			this.taskContext.error("No such upload ticket {}", ticketId);
			throw new IllegalArgumentException("No such upload ticket");
		}

		UploadedFile uploadedFile = ticket.getUploadedFile(fileId);
		if (uploadedFile == null) {
			log.warn("No such file {} in upload ticket {}", fileId, ticketId);
			this.taskContext.error("No such file {} in upload ticket {}", fileId, ticketId);
			throw new IllegalArgumentException("No such file in upload ticket");
		}

		DeviceDriver driver = this.device.getDeviceDriver();
		AttributeDefinition attribute = driver.getAttributeDefinition(AttributeLevel.CONFIG, key);
		if (attribute == null) {
			log.warn("Error during snapshot, requested attribute key '{}' doesn't exist.", key);
			taskContext.error("Requested attribute key '{}' doesn't exist.", key);
			throw new IllegalArgumentException("Unknown attribute key");
		}

		if (!AttributeType.BINARYFILE.equals(attribute.getType())) {
			log.warn("Error during snapshot: can't use commitUpload method on attribute '{}'"
				+ " (not a binary file attribute).", attribute.getName());
			taskContext.error("Can't use commitUpload method on attribute '{}'"
				+ " (not a binary file attribute).", attribute.getName());
			throw new IllegalArgumentException(
				"Can't use commitUpload method on non-binary-file attribute");
		}

		String finalStoreName = this.normalizeStoreName(storeName, uploadedFile.getName());
		if (finalStoreName == null) {
			log.warn("Error during snapshot, unable to generate a store file name in commitUpload (for attribute '{}').", attribute.getName());
			taskContext.error("Unable to generate a store file name in commitUpload (for attribute '{}').", attribute.getName());
			throw new IllegalArgumentException("Unable to generate a proper store file name in commitUpload");
		}

		Path tempPath = uploadedFile.getPath();

		try {
			ConfigBinaryFileAttribute fileAttribute = new ConfigBinaryFileAttribute(config, attribute.getName(), finalStoreName);
			Path targetPath = fileAttribute.getFilePath().toAbsolutePath();

			// Verify checksum if provided
			this.checkFileSum(tempPath, expectedHash);

			fileAttribute.setFileSize(tempPath.toFile().length());
			try (InputStream is = new FileInputStream(tempPath.toFile())) {
				String cs = "sha256:" + DigestUtils.sha256Hex(is).toLowerCase();
				log.trace("Computed SHA256 for the uploaded file is {}", cs);
				fileAttribute.setChecksum(cs);
			}
			Files.move(tempPath, targetPath);
			config.addAttribute(fileAttribute);

			log.info("Committed uploaded file {} from ticket {} to attribute '{}' as '{}'",
				fileId, ticketId, key, finalStoreName);
			taskContext.info("Committed uploaded file {} from ticket {} to attribute '{}' as '{}'",
				fileId, ticketId, key, finalStoreName);
		}
		catch (Exception e) {
			log.warn("Error during snapshot while committing uploaded file for attribute key '{}'.",
				attribute.getName(), e);
			taskContext.error("Error while committing uploaded file for attribute key {}: {}",
				attribute.getName(), e.getMessage());
			throw e;
		}
	}
}
