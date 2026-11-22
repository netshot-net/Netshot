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
package net.netshot.netshot.access.collector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Principal;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.sshd.common.AttributeRepository.AttributeKey;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.ECCurves;
import org.apache.sshd.common.compression.BuiltinCompressions;
import org.apache.sshd.common.config.keys.BuiltinIdentities;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.file.root.RootedFileSystemProvider;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.io.IoUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.scp.common.ScpSourceStreamResolver;
import org.apache.sshd.scp.common.ScpTargetStreamResolver;
import org.apache.sshd.scp.common.helpers.DefaultScpFileOpener;
import org.apache.sshd.scp.common.helpers.ScpTimestampCommandDetails;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.ServerBuilder;
import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.keyboard.InteractiveChallenge;
import org.apache.sshd.server.auth.keyboard.KeyboardInteractiveAuthenticator;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.forward.RejectAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.subsystem.SubsystemFactory;
import org.apache.sshd.sftp.SftpModuleProperties;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.server.DirectoryHandle;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.lifecycle.Service;
import net.netshot.netshot.platform.net.NetworkAddress;
import net.netshot.netshot.platform.runtime.BuildInfo;

import org.apache.sshd.common.kex.KexProposalOption;

/**
 * SSH server to receive files via SFTP/SCP.
 */
@Slf4j
public final class SshServer implements Service {

	/** The name of the service (also while disabled, when it isn't built). */
	public static final String NAME = "SSH/SCP/SFTP server";

	/** The AAA logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/** Whether the SSH (SFTP/SCP) server is enabled (the server is built only then). */
	public static final Setting<Boolean> ENABLED = Setting.bool("netshot.sshserver.enabled").defaultValue(true).restart();

	/**
	 * The settings of the embedded SSH server (applied when it starts).
	 *
	 * @param enabledProtocols the enabled transfer protocols
	 * @param tcpPort the listening TCP port
	 * @param externalTcpPort the external TCP port (can be different than tcpPort if port translation is used)
	 * @param listenHost the listen address (e.g. 0.0.0.0 or 127.0.0.1)
	 * @param kexAlgorithms the key exchange algorithms
	 * @param hostKeyAlgorithms the host key algorithms
	 * @param ciphers the ciphers
	 * @param macs the MACs
	 * @param compressionAlgorithms the compression algorithms
	 * @param hostKeyPath the path to store the host keys
	 * @param maxConcurrentSessions the max number of concurrent SSH sessions
	 */
	public record Settings(List<TransferProtocol> enabledProtocols, int tcpPort, int externalTcpPort,
		InetAddress listenHost, List<String> kexAlgorithms, List<String> hostKeyAlgorithms, List<String> ciphers,
		List<String> macs, List<String> compressionAlgorithms, Path hostKeyPath, int maxConcurrentSessions) {

		private static final int DEFAULT_SSH_SERVER_PORT = 2022;

		private static final List<String> DEFAULT_SSH_KEX_ALGORITHMS = List.of(
			BuiltinDHFactories.Constants.DIFFIE_HELLMAN_GROUP18_SHA512,
			BuiltinDHFactories.Constants.DIFFIE_HELLMAN_GROUP16_SHA512,
			BuiltinDHFactories.Constants.DIFFIE_HELLMAN_GROUP14_SHA256,
			BuiltinDHFactories.Constants.DIFFIE_HELLMAN_GROUP_EXCHANGE_SHA256
		);

		private static final List<String> DEFAULT_SSH_HOST_KEY_ALGORITHMS = List.of(
			KeyPairProvider.SSH_ED25519,
			KeyPairProvider.ECDSA_SHA2_NISTP521,
			KeyPairProvider.ECDSA_SHA2_NISTP256,
			KeyUtils.RSA_SHA256_KEY_TYPE_ALIAS,
			KeyUtils.RSA_SHA512_KEY_TYPE_ALIAS,
			KeyPairProvider.SSH_RSA,
			KeyPairProvider.SSH_DSS
		);

		private static final List<String> DEFAULT_SSH_CIPHERS = List.of(
			BuiltinCiphers.Constants.CC20P1305_OPENSSH,
			BuiltinCiphers.Constants.AES256_GCM,
			BuiltinCiphers.Constants.AES128_GCM,
			BuiltinCiphers.Constants.AES256_CTR,
			BuiltinCiphers.Constants.AES256_CBC,
			BuiltinCiphers.Constants.AES192_CTR,
			BuiltinCiphers.Constants.AES192_CBC,
			BuiltinCiphers.Constants.AES128_CTR,
			BuiltinCiphers.Constants.AES128_CBC
		);

		private static final List<String> DEFAULT_SSH_MACS = List.of(
			BuiltinMacs.Constants.ETM_HMAC_SHA2_512,
			BuiltinMacs.Constants.ETM_HMAC_SHA2_256,
			BuiltinMacs.Constants.HMAC_SHA2_256,
			BuiltinMacs.Constants.HMAC_SHA2_512
		);

		private static final List<String> DEFAULT_SSH_COMPRESSION_ALGORITHMS = List.of(
			BuiltinCompressions.Constants.NONE,
			BuiltinCompressions.Constants.ZLIB
		);

		/** Whether SFTP is enabled. */
		static final Setting<Boolean> SFTP_ENABLED = Setting.bool("netshot.sshserver.sftp.enabled").defaultValue(true).restart();

		/** Whether SCP is enabled. */
		static final Setting<Boolean> SCP_ENABLED = Setting.bool("netshot.sshserver.scp.enabled").defaultValue(true).restart();

		/** Listening TCP port. */
		static final Setting<Integer> PORT = Setting.integer("netshot.sshserver.port")
			.defaultValue(DEFAULT_SSH_SERVER_PORT).range(1, 65535).restart();

		/** External TCP port (default: the listening port). */
		static final Setting<Integer> EXTERNAL_PORT = Setting.integer("netshot.sshserver.externalport").range(1, 65535).restart();

		/** Listen address. */
		static final Setting<String> LISTEN_ADDRESS = Setting.text("netshot.sshserver.listenaddress").defaultValue("0.0.0.0").restart();

		/** Path to store the host keys. */
		static final Setting<String> HOST_KEY_PATH = Setting.text("netshot.sshserver.hostkeypath").restart();

		/** Key exchange algorithms. */
		static final Setting<List<String>> KEX_ALGORITHMS = Setting.list("netshot.sshserver.kexalgorithms", ", *")
			.defaultValue(DEFAULT_SSH_KEX_ALGORITHMS).restart();

		/** Host key algorithms. */
		static final Setting<List<String>> HOST_KEY_ALGORITHMS = Setting.list("netshot.sshserver.hostkeyalgorithms", ", *")
			.defaultValue(DEFAULT_SSH_HOST_KEY_ALGORITHMS).restart();

		/** Ciphers. */
		static final Setting<List<String>> CIPHERS = Setting.list("netshot.sshserver.ciphers", ", *")
			.defaultValue(DEFAULT_SSH_CIPHERS).restart();

		/** MACs. */
		static final Setting<List<String>> MACS = Setting.list("netshot.sshserver.macs", ", *")
			.defaultValue(DEFAULT_SSH_MACS).restart();

		/** Compression algorithms. */
		static final Setting<List<String>> COMPRESSION_ALGORITHMS = Setting.list("netshot.sshserver.compressionalgorithms", ", *")
			.defaultValue(DEFAULT_SSH_COMPRESSION_ALGORITHMS).restart();

		/** Max concurrent SSH sessions. */
		static final Setting<Integer> MAX_CONCURRENT_SESSIONS = Setting.integer("netshot.sshserver.maxconcurrentsessions")
			.defaultValue(10).range(1, 1000).restart();

		/**
		 * Reads the settings.
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			List<TransferProtocol> enabledProtocols = new ArrayList<>();
			if (config.get(SFTP_ENABLED)) {
				enabledProtocols.add(TransferProtocol.SFTP);
			}
			if (config.get(SCP_ENABLED)) {
				enabledProtocols.add(TransferProtocol.SCP);
			}
			int tcpPort = config.get(PORT);
			Integer externalTcpPort = config.get(EXTERNAL_PORT);
			String listenAddress = config.get(LISTEN_ADDRESS);
			InetAddress listenHost;
			try {
				listenHost = InetAddress.getByName(listenAddress);
			}
			catch (UnknownHostException e) {
				log.warn("Unable to parse IP address '{}' for SSH server listen address, using loopback instead",
					listenAddress, e);
				listenHost = InetAddress.getLoopbackAddress();
			}
			Path hostKeyPath;
			String hostKeyPathString = config.get(HOST_KEY_PATH);
			if (hostKeyPathString == null) {
				// No configuration set, use system temp directory and warn
				hostKeyPath = Paths.get(System.getProperty("java.io.tmpdir"));
				log.warn("Embedded SSH server host keys will be stored in system temp directory: {}. "
					+ "Set netshot.sshserver.hostkeypath configuration to a persistent location to avoid "
					+ "host key regeneration.", hostKeyPath);
			}
			else {
				hostKeyPath = Paths.get(hostKeyPathString);
				log.info("SSH server host keys will be stored in: {}", hostKeyPath);
			}
			return new Settings(List.copyOf(enabledProtocols), tcpPort,
				externalTcpPort == null ? tcpPort : externalTcpPort, listenHost,
				config.get(KEX_ALGORITHMS), config.get(HOST_KEY_ALGORITHMS), config.get(CIPHERS), config.get(MACS),
				config.get(COMPRESSION_ALGORITHMS), hostKeyPath, config.get(MAX_CONCURRENT_SESSIONS));
		}

		public boolean isProtocolEnabled(TransferProtocol protocol) {
			return this.enabledProtocols.contains(protocol);
		}
	}

	/** Upload Ticket attribute to be attached to SSH sessions. */
	public static final AttributeKey<UploadTicket> UPLOAD_TICKET = new AttributeKey<>();

	/** Session log buffer attribute to be attached to SSH sessions. */
	public static final AttributeKey<SessionLogBuffer> SESSION_LOG_BUFFER = new AttributeKey<>();

	/**
	 * Buffer to collect session logs for later retrieval by upload ticket.
	 */
	public static final class SessionLogBuffer {
		private final StringBuilder buffer = new StringBuilder();
		private final String remoteAddress;

		public SessionLogBuffer(String address) {
			this.remoteAddress = address;
		}

		public void trace(String message, Object... params) {
			log(Level.TRACE, message, params);
		}

		public void debug(String message, Object... params) {
			log(Level.DEBUG, message, params);
		}

		public void info(String message, Object... params) {
			log(Level.INFO, message, params);
		}

		public void warn(String message, Object... params) {
			log(Level.WARN, message, params);
		}

		public void error(String message, Object... params) {
			log(Level.ERROR, message, params);
		}

		private void log(Level level, String message, Object... params) {
			synchronized (buffer) {
				buffer
					.append(Instant.now())
					.append(" [").append(level).append("] ")
					.append("[").append(remoteAddress).append("] ")
					.append(MessageFormatter.arrayFormat(message, params).getMessage())
					.append("\n");
			}
		}

		public String getLogs() {
			synchronized (buffer) {
				return buffer.toString();
			}
		}

		public void clear() {
			synchronized (buffer) {
				buffer.setLength(0);
			}
		}

		/**
		 * Gets the logs collected since the last call, and clears them (so that the logs of a session given to its
		 * ticket after a rejected login attempt aren't given again with the next attempt).
		 *
		 * @return the logs
		 */
		public String takeLogs() {
			synchronized (buffer) {
				String logs = buffer.toString();
				buffer.setLength(0);
				return logs;
			}
		}
	}

	/** The configuration store. */
	private final ConfigStore config;

	/** The number of accepted logins (valid upload ticket). */
	private final AtomicLong acceptedLogins = new AtomicLong();

	/** The number of rejected logins. */
	private final AtomicLong rejectedLogins = new AtomicLong();

	/** The number of registered upload tickets. */
	private final AtomicLong registeredTickets = new AtomicLong();

	/** The settings, read when the server starts (a change needs a restart); null before. */
	private volatile Settings settings;

	/**
	 * Creates the SSH server.
	 *
	 * @param config the configuration store
	 */
	public SshServer(ConfigStore config) {
		this.config = config;
	}

	/**
	 * Gets the settings of the SSH server: the ones it was started with.
	 * @return the settings, null if it wasn't started
	 */
	public Settings settings() {
		return this.settings;
	}

	@Override
	public String id() {
		return "ssh-server";
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public synchronized void start() throws IOException {
		this.settings = Settings.from(this.config.current());
		this.startServer();
	}

	@Override
	public synchronized void stop() {
		this.stopServer();
	}

	@Override
	public boolean isHealthy() {
		return this.isRunning();
	}

	@Override
	public Map<String, Object> stats() {
		Map<String, Object> stats = new LinkedHashMap<>();
		Settings current = this.settings;
		stats.put("port", current == null ? 0 : current.tcpPort());
		stats.put("listening", this.sshd != null);
		stats.put("sessions", this.sshd == null ? 0 : this.sshd.getActiveSessions().size());
		stats.put("tickets", this.tickets.size());
		stats.put("registeredTickets", this.registeredTickets.get());
		stats.put("acceptedLogins", this.acceptedLogins.get());
		stats.put("rejectedLogins", this.rejectedLogins.get());
		return stats;
	}

	/**
	 * Whether the server is running (listening).
	 * @return true if running
	 */
	public boolean isRunning() {
		return this.sshd != null;
	}

	/**
	 * Whether the server is running, with the given transfer protocol enabled.
	 * @param protocol the transfer protocol
	 * @return true if running with the protocol enabled
	 */
	public boolean isRunning(TransferProtocol protocol) {
		return this.isRunning() && this.settings.isProtocolEnabled(protocol);
	}

	/**
	 * Describes the options of a failed key exchange negotiation for which the client and the server propose no
	 * algorithm in common, with the algorithms proposed by each side. The negotiation stops at the first of them,
	 * which the failure reason names: this also gives the others.
	 *
	 * @param clientProposal the client proposal
	 * @param serverProposal the server proposal
	 * @return the description, e.g. "encryption algorithms (client to server) [client: 3des-cbc / server: aes128-ctr]; ..."
	 */
	static String noCommonAlgorithm(Map<KexProposalOption, String> clientProposal, Map<KexProposalOption, String> serverProposal) {
		List<String> options = new ArrayList<>();
		for (KexProposalOption option : KexProposalOption.VALUES) {
			if (KexProposalOption.LANGUAGE_PROPOSALS.contains(option)) {
				continue;
			}
			String client = clientProposal == null ? null : clientProposal.get(option);
			String server = serverProposal == null ? null : serverProposal.get(option);
			Set<String> common = new HashSet<>(algorithms(client));
			common.retainAll(algorithms(server));
			if (common.isEmpty()) {
				options.add("%s [client: %s / server: %s]".formatted(option.getDescription(), client, server));
			}
		}
		return options.isEmpty() ? "none" : String.join("; ", options);
	}

	private static List<String> algorithms(String proposal) {
		return proposal == null || proposal.isEmpty() ? List.of() : List.of(proposal.split(","));
	}

	/**
	 * Session listener to track session lifecycle and trigger ticket callbacks.
	 */
	private final class DeviceSessionListener implements SessionListener {
		@Override
		public void sessionCreated(Session session) {
			// Create log buffer for this session
			String remoteAddress = session.getRemoteAddress() == null ? "unknown"
				:
				session.getRemoteAddress().toString();
			SessionLogBuffer logBuffer = new SessionLogBuffer(remoteAddress);
			session.setAttribute(SESSION_LOG_BUFFER, logBuffer);
			logBuffer.debug("SSH session created from {}", remoteAddress);
		}

		@Override
		public void sessionNegotiationEnd(
				Session session,
				Map<KexProposalOption, String> clientProposal,
				Map<KexProposalOption, String> serverProposal,
				Map<KexProposalOption, String> negotiatedOptions,
				Throwable reason) {
			if (reason != null) {
				log.info("SSH protocol negotiation with {} ({}) failed: {}. No algorithm in common for: {}",
					session.getRemoteAddress(), session.getClientVersion(), reason.getMessage(),
					noCommonAlgorithm(clientProposal, serverProposal));
			}

			SessionLogBuffer logBuffer = session.getAttribute(SESSION_LOG_BUFFER);
			if (logBuffer == null) {
				return;
			}

			if (reason != null) {
				logBuffer.warn("SSH Protocol Negotiation ended with error: {}", reason.getMessage());
			}

			logBuffer.trace("SSH Protocol Negotiation {}:",
				reason == null ? "Results" : "Proposals (Failed)");

			// Log KEX algorithms
			String kexClient = clientProposal.get(KexProposalOption.ALGORITHMS);
			String kexServer = serverProposal.get(KexProposalOption.ALGORITHMS);
			String kexNegotiated = negotiatedOptions.get(KexProposalOption.ALGORITHMS);
			logBuffer.trace("  Key Exchange:");
			logBuffer.trace("    Client proposed: {}", kexClient);
			logBuffer.trace("    Server proposed: {}", kexServer);
			if (kexNegotiated != null) {
				logBuffer.trace("    Negotiated: {}", kexNegotiated);
			}

			// Log Server Host Key algorithms
			String hostKeyClient = clientProposal.get(KexProposalOption.SERVERKEYS);
			String hostKeyServer = serverProposal.get(KexProposalOption.SERVERKEYS);
			String hostKeyNegotiated = negotiatedOptions.get(KexProposalOption.SERVERKEYS);
			logBuffer.trace("  Server Host Key:");
			logBuffer.trace("    Client proposed: {}", hostKeyClient);
			logBuffer.trace("    Server proposed: {}", hostKeyServer);
			if (hostKeyNegotiated != null) {
				logBuffer.trace("    Negotiated: {}", hostKeyNegotiated);
			}

			// Log Cipher algorithms (client-to-server)
			String cipherC2SClient = clientProposal.get(KexProposalOption.C2SENC);
			String cipherC2SServer = serverProposal.get(KexProposalOption.C2SENC);
			String cipherC2SNegotiated = negotiatedOptions.get(KexProposalOption.C2SENC);
			logBuffer.trace("  Cipher (Client-to-Server):");
			logBuffer.trace("    Client proposed: {}", cipherC2SClient);
			logBuffer.trace("    Server proposed: {}", cipherC2SServer);
			if (cipherC2SNegotiated != null) {
				logBuffer.trace("    Negotiated: {}", cipherC2SNegotiated);
			}

			// Log Cipher algorithms (server-to-client)
			String cipherS2CClient = clientProposal.get(KexProposalOption.S2CENC);
			String cipherS2CServer = serverProposal.get(KexProposalOption.S2CENC);
			String cipherS2CNegotiated = negotiatedOptions.get(KexProposalOption.S2CENC);
			logBuffer.trace("  Cipher (Server-to-Client):");
			logBuffer.trace("    Client proposed: {}", cipherS2CClient);
			logBuffer.trace("    Server proposed: {}", cipherS2CServer);
			if (cipherS2CNegotiated != null) {
				logBuffer.trace("    Negotiated: {}", cipherS2CNegotiated);
			}

			// Log MAC algorithms (client-to-server)
			String macC2SClient = clientProposal.get(KexProposalOption.C2SMAC);
			String macC2SServer = serverProposal.get(KexProposalOption.C2SMAC);
			String macC2SNegotiated = negotiatedOptions.get(KexProposalOption.C2SMAC);
			logBuffer.trace("  MAC (Client-to-Server):");
			logBuffer.trace("    Client proposed: {}", macC2SClient);
			logBuffer.trace("    Server proposed: {}", macC2SServer);
			if (macC2SNegotiated != null) {
				logBuffer.trace("    Negotiated: {}", macC2SNegotiated);
			}

			// Log MAC algorithms (server-to-client)
			String macS2CClient = clientProposal.get(KexProposalOption.S2CMAC);
			String macS2CServer = serverProposal.get(KexProposalOption.S2CMAC);
			String macS2CNegotiated = negotiatedOptions.get(KexProposalOption.S2CMAC);
			logBuffer.trace("  MAC (Server-to-Client):");
			logBuffer.trace("    Client proposed: {}", macS2CClient);
			logBuffer.trace("    Server proposed: {}", macS2CServer);
			if (macS2CNegotiated != null) {
				logBuffer.trace("    Negotiated: {}", macS2CNegotiated);
			}

			// Log Compression algorithms (client-to-server)
			String compC2SClient = clientProposal.get(KexProposalOption.C2SCOMP);
			String compC2SServer = serverProposal.get(KexProposalOption.C2SCOMP);
			String compC2SNegotiated = negotiatedOptions.get(KexProposalOption.C2SCOMP);
			logBuffer.trace("  Compression (Client-to-Server):");
			logBuffer.trace("    Client proposed: {}", compC2SClient);
			logBuffer.trace("    Server proposed: {}", compC2SServer);
			if (compC2SNegotiated != null) {
				logBuffer.trace("    Negotiated: {}", compC2SNegotiated);
			}

			// Log Compression algorithms (server-to-client)
			String compS2CClient = clientProposal.get(KexProposalOption.S2CCOMP);
			String compS2CServer = serverProposal.get(KexProposalOption.S2CCOMP);
			String compS2CNegotiated = negotiatedOptions.get(KexProposalOption.S2CCOMP);
			logBuffer.trace("  Compression (Server-to-Client):");
			logBuffer.trace("    Client proposed: {}", compS2CClient);
			logBuffer.trace("    Server proposed: {}", compS2CServer);
			if (compS2CNegotiated != null) {
				logBuffer.trace("    Negotiated: {}", compS2CNegotiated);
			}
		}

		@Override
		public void sessionClosed(Session session) {
			UploadTicket ticket = session.getAttribute(UPLOAD_TICKET);
			if (ticket != null) {
				log.debug("Session closed for ticket {}", ticket.getUsername());
				ticket.onSessionStopped();
			}
		}
	}

	/**
	 * Authenticator to be called when a device tries to authenticate in keyboard interactive mode.
	 */
	private final class DeviceKeyboardInteractiveAuthenticator implements KeyboardInteractiveAuthenticator {

		@Override
		public InteractiveChallenge generateChallenge(ServerSession session, String username, String lang,
				String subMethods) throws Exception {
			InteractiveChallenge challenge = new InteractiveChallenge();
			challenge.addPrompt("Password: ", false);
			return challenge;
		}

		@Override
		public boolean authenticate(ServerSession session, String username, List<String> responses) throws Exception {
			if (responses.size() != 1) {
				log.warn("Error in embedded SSH server while authenticating incoming request: response size is not 1");
				return false;
			}
			final String password = responses.get(0);
			return SshServer.this.authenticate(session, username, password);
		}

	}

	/**
	 * Authenticator to be called when a device tries to authenticate with a password.
	 */
	private final class DevicePasswordAuthenticator implements PasswordAuthenticator {

		@Override
		public boolean authenticate(String username, String password, ServerSession session)
				throws PasswordChangeRequiredException, AsyncAuthException {
			return SshServer.this.authenticate(session, username, password);
		}
		
	}

	/**
	 * File opener to be called when a device tries to send or receive a file via SCP.
	 */
	private final class DeviceScpFileOpener extends DefaultScpFileOpener {

		/** Common error message for all download attempts. */
		private static final String FORBIDDEN_MESSAGE =
			"Forbidden. This server only accepts simple incoming configuration files.";

		/**
		 * Log a forbidden access attempt and throw an IOException.
		 * @param session the SSH session
		 * @param operation the operation that was attempted
		 * @throws IOException always thrown with FORBIDDEN_MESSAGE
		 */
		private void throwForbiddenException(Session session, String operation) throws IOException {
			log.warn("Forbidden SCP operation '{}' attempted in session {}", operation, session);
			throw new IOException(DeviceScpFileOpener.FORBIDDEN_MESSAGE);
		}

		@Override
		public Path resolveIncomingFilePath(Session session, Path localPath, String name, boolean preserve,
				Set<PosixFilePermission> permissions, ScpTimestampCommandDetails time) throws IOException {
			throwForbiddenException(session, "resolveIncomingFilePath");
			return null; // Unreachable
		}

		@Override
		public Iterable<Path> getMatchingFilesToSend(Session session, Path basedir, String pattern) throws IOException {
			throwForbiddenException(session, "getMatchingFilesToSend");
			return null; // Unreachable
		}

		@Override
		public boolean sendAsRegularFile(Session session, Path path, LinkOption... options) throws IOException {
			throwForbiddenException(session, "sendAsRegularFile");
			return false; // Unreachable
		}

		@Override
		public boolean sendAsDirectory(Session session, Path path, LinkOption... options) throws IOException {
			throwForbiddenException(session, "sendAsDirectory");
			return false; // Unreachable
		}

		@Override
		public DirectoryStream<Path> getLocalFolderChildren(Session session, Path path) throws IOException {
			throwForbiddenException(session, "getLocalFolderChildren");
			return null; // Unreachable
		}

		@Override
		public BasicFileAttributes getLocalBasicFileAttributes(Session session, Path path, LinkOption... options)
				throws IOException {
			throwForbiddenException(session, "getLocalBasicFileAttributes");
			return null; // Unreachable
		}

		@Override
		public Set<PosixFilePermission> getLocalFilePermissions(Session session, Path path, LinkOption... options)
				throws IOException {
			throwForbiddenException(session, "getLocalFilePermissions");
			return null; // Unreachable
		}

		@Override
		public Path resolveIncomingReceiveLocation(Session session, Path path, boolean recursive, boolean shouldBeDir,
				boolean preserve) throws IOException {
			return super.resolveIncomingReceiveLocation(session, path, recursive, shouldBeDir, preserve);
		}

		@Override
		public Path resolveOutgoingFilePath(Session session, Path localPath, LinkOption... options) throws IOException {
			throwForbiddenException(session, "resolveOutgoingFilePath");
			return null; // Unreachable
		}

		@Override
		public InputStream openRead(Session session, Path file, long size, Set<PosixFilePermission> permissions,
				OpenOption... options) throws IOException {
			throwForbiddenException(session, "openRead");
			return null; // Unreachable
		}

		@Override
		public void closeRead(Session session, Path file, long size, Set<PosixFilePermission> permissions,
				InputStream stream) throws IOException {
			throwForbiddenException(session, "closeRead");
		}

		@Override
		public ScpSourceStreamResolver createScpSourceStreamResolver(Session session, Path path) throws IOException {
			throwForbiddenException(session, "createScpSourceStreamResolver");
			return null; // Unreachable
		}

		@Override
		public OutputStream openWrite(Session session, Path file, long size, Set<PosixFilePermission> permissions,
				OpenOption... options) throws IOException {
			log.info("SSH/scp server open file request - session {}, path {}, permissions {}, options {}",
				session, file, permissions, options);
			return super.openWrite(session, file, size, permissions, options);
		}

		@Override
		public void closeWrite(Session session, Path file, long size, Set<PosixFilePermission> permissions, OutputStream os)
				throws IOException {
			log.debug("SSH/SCP server close write request: session {}, file {}, size {}", session, file, size);

			// Close the output stream first
			if (os != null) {
				try {
					os.close();
				}
				catch (IOException e) {
					log.warn("Error closing SCP output stream for file {}", file, e);
					throw e;
				}
			}

			// Trigger onFileWritten callback
			UploadTicket ticket = session.getAttribute(UPLOAD_TICKET);
			if (ticket != null) {
				log.info("File written via SCP in session {}: {}", session, file);
				if (!ticket.onFileWritten(file)) {
					Files.deleteIfExists(file);
				}
			}
		}

		@Override
		public ScpTargetStreamResolver createScpTargetStreamResolver(Session session, Path path) throws IOException {
			log.debug("Create SCP target stream resolver for session {}, path {}", session, path);
			return super.createScpTargetStreamResolver(session, path);
		}
	}

	/**
	 * File system accessor to be called when a device tries to interact with files via SFTP.
	 */
	private final class DeviceSftpFileSystemAccessor implements SftpFileSystemAccessor {

		/** Common error message for all download attempts. */
		private static final String FORBIDDEN_MESSAGE =
			"Forbidden. This server only accepts incoming simple configuration files.";

		/**
		 * Log a forbidden access attempt and throw an IOException.
		 * @param subsystem the SFTP subsystem
		 * @param operation the operation that was attempted
		 * @throws IOException always thrown with FORBIDDEN_MESSAGE
		 */
		private void throwForbiddenException(SftpSubsystemProxy subsystem, String operation) throws IOException {
			log.warn("Forbidden SFTP operation '{}' attempted in session {}", operation, subsystem.getSession());
			throw new IOException(FORBIDDEN_MESSAGE);
		}

		@Override
		public void applyExtensionFileAttributes(SftpSubsystemProxy subsystem, Path file, Map<String, byte[]> extensions,
				LinkOption... options) throws IOException {
			throwForbiddenException(subsystem, "applyExtensionFileAttributes");
		}

		@Override
		public void closeDirectory(SftpSubsystemProxy subsystem, DirectoryHandle dirHandle, Path dir, String handle,
				DirectoryStream<Path> ds) throws IOException {
			log.info("SSH/SFTP server close directory request - session {}, path {}",
				subsystem.getSession(), dir);
			SftpFileSystemAccessor.super.closeDirectory(subsystem, dirHandle, dir, handle, ds);
		}

		@Override
		public void closeFile(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle,
				Channel channel, Set<? extends OpenOption> options) throws IOException {
			log.info("SSH/SFTP server close file request - session {}, path {}, options {}",
				subsystem.getSession(), file, options);
			// Closes the channel of the file (complete before the ticket gets the file)
			SftpFileSystemAccessor.super.closeFile(subsystem, fileHandle, file, handle, channel, options);

			// Trigger onFileWritten callback if this was a write operation
			if (options.contains(java.nio.file.StandardOpenOption.WRITE)
					|| options.contains(java.nio.file.StandardOpenOption.CREATE)) {
				UploadTicket ticket = subsystem.getSession().getAttribute(UPLOAD_TICKET);
				if (ticket != null) {
					log.info("File written via SFTP in session {}: {}", subsystem.getSession(), file);
					if (!ticket.onFileWritten(file)) {
						log.warn("File {} received through session {} refused by its ticket (not valid anymore), deleted", file,
							subsystem.getSession());
						Files.deleteIfExists(file);
					}
				}
			}
		}

		@Override
		public void copyFile(SftpSubsystemProxy subsystem, Path src, Path dst, Collection<CopyOption> opts)
				throws IOException {
			throwForbiddenException(subsystem, "copyFile");
		}

		@Override
		public void createDirectory(SftpSubsystemProxy subsystem, Path path) throws IOException {
			throwForbiddenException(subsystem, "createDirectory");
		}

		@Override
		public void createLink(SftpSubsystemProxy subsystem, Path link, Path existing, boolean symLink) throws IOException {
			throwForbiddenException(subsystem, "createLink");
		}

		@Override
		public DirectoryStream<Path> openDirectory(SftpSubsystemProxy subsystem, DirectoryHandle dirHandle, Path dir,
				String handle, LinkOption... linkOptions) throws IOException {
			log.info("SSH/SFTP server open directory request - session {}, path {}, options {}",
				subsystem.getSession(), dir, linkOptions);
			return SftpFileSystemAccessor.super.openDirectory(subsystem, dirHandle, dir, handle, linkOptions);
		}

		@Override
		public SeekableByteChannel openFile(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle,
				Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
			log.info("SSH/SFTP server open file request - session {}, path {}, options {}, attrs {}",
				subsystem.getSession(), file, options, attrs);
			return SftpFileSystemAccessor.super.openFile(subsystem, fileHandle, file, handle, options, attrs);
		}

		@Override
		public void putRemoteFileName(SftpSubsystemProxy subsystem, Path path, Buffer buf, String name, boolean shortName)
				throws IOException {
			SftpFileSystemAccessor.super.putRemoteFileName(subsystem, path, buf, name, shortName);
		}

		@Override
		public Map<String, ?> readFileAttributes(SftpSubsystemProxy subsystem, Path file, String view,
				LinkOption... options) throws IOException {

			Map<String, ?> attributes = SftpFileSystemAccessor.super.readFileAttributes(subsystem, file, view, options);
			log.debug("SSH/SFTP server read file attribute request, session {}, path {}, options {} => attributes {}",
				subsystem.getSession(), file, options, attributes);
			return attributes;
		}

		@Override
		public void removeFile(SftpSubsystemProxy subsystem, Path path, boolean isDirectory) throws IOException {
			throwForbiddenException(subsystem, "removeFile");
		}

		@Override
		public void renameFile(SftpSubsystemProxy subsystem, Path oldPath, Path newPath, Collection<CopyOption> opts)
				throws IOException {
			throwForbiddenException(subsystem, "renameFile");
		}

		@Override
		public LinkOption[] resolveFileAccessLinkOptions(SftpSubsystemProxy subsystem, Path file, int cmd, String extension,
				boolean followLinks) throws IOException {
			LinkOption[] linkOptions = SftpFileSystemAccessor.super.resolveFileAccessLinkOptions(subsystem, file, cmd, extension, followLinks);
			log.debug("SSH/SFTP server resolve file access link options request, session {}, path {}, cmd {}, extension {}, followLinks {} => {}",
				subsystem.getSession(), file, cmd, extension, followLinks, linkOptions);
			return linkOptions;
		}

		@Override
		public UserPrincipal resolveFileOwner(SftpSubsystemProxy subsystem, Path file, UserPrincipal name)
				throws IOException {
			throwForbiddenException(subsystem, "resolveFileOwner");
			return null; // Unreachable
		}

		@Override
		public GroupPrincipal resolveGroupOwner(SftpSubsystemProxy subsystem, Path file, GroupPrincipal name)
				throws IOException {
			throwForbiddenException(subsystem, "resolveGroupOwner");
			return null; // Unreachable
		}

		@Override
		public String resolveLinkTarget(SftpSubsystemProxy subsystem, Path link) throws IOException {
			throwForbiddenException(subsystem, "resolveLinkTarget");
			return null; // Unreachable
		}

		@Override
		public NavigableMap<String, Object> resolveReportedFileAttributes(SftpSubsystemProxy subsystem, Path file,
				int flags, NavigableMap<String, Object> attrs, LinkOption... options) throws IOException {
			log.info("SSH/SFTP server resolve reported file attributes - session {}, path {}, flags {}, attrs {}",
				subsystem.getSession(), file, flags, attrs);
			return SftpFileSystemAccessor.super.resolveReportedFileAttributes(subsystem, file, flags, attrs, options);
		}

		@Override
		public void setFileAccessControl(SftpSubsystemProxy subsystem, Path file, List<AclEntry> acl, LinkOption... options)
				throws IOException {
			throwForbiddenException(subsystem, "setFileAccessControl");
		}

		/**
		 * Ignores the permissions given by the device (with a SETSTAT, or with the attributes of an OPEN), so that it
		 * can't make the received file (e.g. a configuration with secrets) readable by others; not refused, for the
		 * devices which give the permissions of the new file when opening it.
		 */
		@Override
		public void setFilePermissions(SftpSubsystemProxy subsystem, Path file, Set<PosixFilePermission> perms,
				LinkOption... options) throws IOException {
			log.info("Ignored SFTP permission change ({}) of {} in session {}", perms, file, subsystem.getSession());
		}

		/**
		 * Ignores the file times given by the device (e.g. {@code sftp put -p}, or {@code scp -p} over SFTP), which
		 * would otherwise report the whole upload as failed: the time of the received file doesn't matter. Any other
		 * attribute is refused.
		 */
		@Override
		public void setFileAttribute(SftpSubsystemProxy subsystem, Path file, String view, String attribute, Object value,
				LinkOption... options) throws IOException {
			if ("basic".equals(view) && (IoUtils.LASTMOD_TIME_VIEW_ATTR.equals(attribute) || IoUtils.LASTACC_TIME_VIEW_ATTR.equals(attribute)
					|| IoUtils.CREATE_TIME_VIEW_ATTR.equals(attribute))) {
				log.info("Ignored SFTP time change ({} = {}) of {} in session {}", attribute, value, file, subsystem.getSession());
				return;
			}
			throwForbiddenException(subsystem, "setFileAttribute");
		}

		@Override
		public void setFileOwner(SftpSubsystemProxy subsystem, Path file, Principal value, LinkOption... options)
				throws IOException {
			throwForbiddenException(subsystem, "setFileOwner");
		}

		@Override
		public void setGroupOwner(SftpSubsystemProxy subsystem, Path file, Principal value, LinkOption... options)
				throws IOException {
			throwForbiddenException(subsystem, "setGroupOwner");
		}

		/**
		 * Writes the data of a file opened by the device to the disk ({@code fsync@openssh.com}), as some clients do
		 * before closing the file.
		 */
		@Override
		public void syncFileData(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle,
				Channel channel) throws IOException {
			if (channel instanceof FileChannel fileChannel) {
				fileChannel.force(true);
			}
		}

		@Override
		public FileLock tryLock(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle,
				Channel channel, long position, long size, boolean shared) throws IOException {
			throwForbiddenException(subsystem, "tryLock");
			return null; // Unreachable
		}

	}

	/**
	 * File system factory to be used when a device wants to upload data via SCP/SFTP.
	 */
	private final class DeviceVirtualFileSystemFactory implements FileSystemFactory {
		@Override
		public Path getUserHomeDir(SessionContext session) throws IOException {
			UploadTicket ticket = session.getAttribute(UPLOAD_TICKET);
			if (ticket == null) {
				return null;
			}
			return ticket.getRootPath();
		}

		@Override
		public FileSystem createFileSystem(SessionContext session) throws IOException {
			Path dir = getUserHomeDir(session);
			if (dir == null) {
				throw new InvalidPathException(session.getUsername(), "Cannot resolve home directory");
			}
			return new RootedFileSystemProvider().newFileSystem(dir, Collections.emptyMap());
		}
	}

	/** The internal SSHd server. */
	private volatile org.apache.sshd.server.SshServer sshd = null;

	/** The upload tickets, to accept incoming upload requests. */
	private Map<String, UploadTicket> tickets = new ConcurrentHashMap<>();

	/**
	 * Get a path for storing a specific key algorithm.
	 * @param algorithm The key algorithm
	 * @param keySize The key size (0 = default)
	 * @return The path to use
	 */
	private Path getHostKeyPath(String algorithm, int keySize) {
		Path keyBasePath = this.settings.hostKeyPath();
		String central = algorithm.toLowerCase();
		if (keySize != 0) {
			central += Integer.toString(keySize);
		}
		String fileName = "netshot_ssh_host_%s.key".formatted(central);
		return keyBasePath.resolve(fileName);
	}

	/**
	 * Start the embedded SSH server.
	 */
	private void startServer() throws IOException {
		this.sshd = org.apache.sshd.server.SshServer.setUpDefaultServer();
		this.sshd.setPort(this.settings.tcpPort());
		this.sshd.setHost(this.settings.listenHost().getHostAddress());

		CoreModuleProperties.MAX_CONCURRENT_SESSIONS.set(this.sshd, this.settings.maxConcurrentSessions());
		CoreModuleProperties.SERVER_IDENTIFICATION.set(this.sshd, "NETSHOT-%s".formatted(BuildInfo.VERSION));
		// The default value causes an error on Cisco ISE SFTP client: "Outbound message too long 262169"
		SftpModuleProperties.MAX_WRITEDATA_PACKET_LENGTH.set(this.sshd, 255 * 1024);

		this.sshd.setKeyExchangeFactories(
			NamedFactory.setUpTransformedFactories(true,
				BuiltinDHFactories.parseDHFactoriesList(this.settings.kexAlgorithms()).getParsedFactories(),
				ServerBuilder.DH2KEX));
		this.sshd.setSignatureFactoriesNames(this.settings.hostKeyAlgorithms());
		this.sshd.setCipherFactoriesNames(this.settings.ciphers());
		this.sshd.setMacFactoriesNames(this.settings.macs());
		this.sshd.setCompressionFactoriesNames(this.settings.compressionAlgorithms());
		this.sshd.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);

		// Configure host key provider to generate keys for all supported algorithms
		// Each key type will be generated and stored in a separate file
		// Only generate keys that are configured in hostKeyAlgorithms
		// Map of SSH key type -> key provider for direct lookup during negotiation
		final Map<String, SimpleGeneratorHostKeyProvider> hostKeyProvidersByType = new HashMap<>();
		// Track which algorithms we've created providers for to avoid duplicate providers
		final Map<String, SimpleGeneratorHostKeyProvider> providersByAlgorithm = new HashMap<>();

		for (String hostKeyAlgorithm : this.settings.hostKeyAlgorithms()) {
			String algorithm = null;
			int keySize = 0;

			String canonicalType = KeyUtils.getCanonicalKeyType(hostKeyAlgorithm);
			ECCurves ecCurves = ECCurves.fromKeyType(hostKeyAlgorithm);
			if (ecCurves != null) {
				algorithm = BuiltinIdentities.ECDSA.getAlgorithm();
				keySize = ecCurves.getKeySize();
			}
			else if (KeyPairProvider.SSH_RSA.equals(canonicalType)) {
				algorithm = BuiltinIdentities.RSA.getAlgorithm();
			}
			else if (KeyPairProvider.SSH_ED25519.equals(canonicalType)) {
				algorithm = BuiltinIdentities.ED25119.getAlgorithm();
				keySize = 255; // Ed25519 requires keysize of 255
			}
			else if (KeyPairProvider.SSH_DSS.equals(canonicalType)) {
				algorithm = BuiltinIdentities.DSA.getAlgorithm();
			}
			else {
				log.warn("Unknown SSH host key algorithm '{}', ignoring", hostKeyAlgorithm);
				continue;
			}

			// Get or create provider for this algorithm
			String algoSize = "%s_%d".formatted(algorithm, keySize);
			SimpleGeneratorHostKeyProvider provider = providersByAlgorithm.get(algoSize);
			if (provider == null) {
				Path keyPath = this.getHostKeyPath(algorithm, keySize);
				provider = new SimpleGeneratorHostKeyProvider(keyPath);
				provider.setAlgorithm(algorithm);
				provider.setKeySize(keySize);
				providersByAlgorithm.put(algoSize, provider);
				log.debug("SSH server will use {} host key (stored in: {})", algorithm, keyPath);
			}
			// Map this key type to the provider
			hostKeyProvidersByType.put(hostKeyAlgorithm, provider);
		}

		// Combine all key providers into a single provider
		this.sshd.setKeyPairProvider(new KeyPairProvider() {
			@Override
			public Iterable<KeyPair> loadKeys(SessionContext session) {
				List<KeyPair> allKeys = new ArrayList<>();
				// Iterate over unique providers only
				for (SimpleGeneratorHostKeyProvider provider : providersByAlgorithm.values()) {
					Iterable<KeyPair> keys = provider.loadKeys(session);
					for (KeyPair key : keys) {
						allKeys.add(key);
					}
				}
				return allKeys;
			}
		});
		
		this.sshd.addSessionListener(new DeviceSessionListener());

		this.sshd.setPasswordAuthenticator(new DevicePasswordAuthenticator());
		this.sshd.setKeyboardInteractiveAuthenticator(new DeviceKeyboardInteractiveAuthenticator());
		this.sshd.setPublickeyAuthenticator(null);
		this.sshd.setHostBasedAuthenticator(null);
		this.sshd.setGSSAuthenticator(null);

		this.sshd.setShellFactory(null);
		if (this.settings.isProtocolEnabled(TransferProtocol.SCP)) {
			ScpCommandFactory scpCommandFactory = new ScpCommandFactory() {
				@Override
				public Command createCommand(ChannelSession channel, String command) throws IOException {
					if (this.isSupportedCommand(channel, command) && !isTicketProtocolAllowed(channel, TransferProtocol.SCP)) {
						return new RefusedTransfer("SCP is not allowed for this upload");
					}
					return super.createCommand(channel, command);
				}
			};
			scpCommandFactory.setScpFileOpener(new DeviceScpFileOpener());
			this.sshd.setCommandFactory(scpCommandFactory);
		}
		else {
			log.info("SCP is disabled by configuration.");
			this.sshd.setCommandFactory((channel, command) -> {
				log.warn("Command '{}' refused for session from {}: SCP is disabled", command, channel.getServerSession().getRemoteAddress());
				return new RefusedTransfer("SCP is disabled on this server");
			});
		}

		if (this.settings.isProtocolEnabled(TransferProtocol.SFTP)) {
			SftpSubsystemFactory sftpFactory = new SftpSubsystemFactory() {
				@Override
				public Command createSubsystem(ChannelSession channel) throws IOException {
					if (!isTicketProtocolAllowed(channel, TransferProtocol.SFTP)) {
						return new RefusedTransfer("SFTP is not allowed for this upload");
					}
					return super.createSubsystem(channel);
				}
			};
			sftpFactory.setFileSystemAccessor(new DeviceSftpFileSystemAccessor());
			this.sshd.setSubsystemFactories(Collections.singletonList(sftpFactory));
		}
		else {
			log.info("SFTP is disabled by configuration.");
			this.sshd.setSubsystemFactories(Collections.singletonList(new SubsystemFactory() {
				@Override
				public String getName() {
					return SftpConstants.SFTP_SUBSYSTEM_NAME;
				}

				@Override
				public Command createSubsystem(ChannelSession channel) {
					log.warn("SFTP refused for session from {}: SFTP is disabled", channel.getServerSession().getRemoteAddress());
					return new RefusedTransfer("SFTP is disabled on this server");
				}
			}));
		}
		this.sshd.setFileSystemFactory(new DeviceVirtualFileSystemFactory());

		// A failure (e.g. port in use) fails the start: logged by the service manager, which stops the server
		this.sshd.start();

	}

	/**
	 * Stop the server.
	 */
	private void stopServer() {
		if (this.sshd == null) {
			return;
		}
		try {
			this.sshd.close();
			this.sshd = null;
		}
		catch (IOException e) {
			log.warn("Cannot close embedded SSH server", e);
		}
	}

	/**
	 * Common authentication callback for SFTP and SCP requests (counted for the statistics).
	 * @param session the server session
	 * @param username the passed username
	 * @param password the passed password
	 * @return true for successful authentication
	 */
	private boolean authenticate(ServerSession session, String username, String password) {
		boolean accepted = this.checkCredentials(session, username, password);
		(accepted ? this.acceptedLogins : this.rejectedLogins).incrementAndGet();
		return accepted;
	}

	/**
	 * Checks the credentials of a SFTP or SCP request against the upload tickets.
	 * @param session the server session
	 * @param username the passed username
	 * @param password the passed password
	 * @return true for successful authentication
	 */
	private boolean checkCredentials(ServerSession session, String username, String password) {
		UploadTicket ticket = SshServer.this.tickets.get(username);
		if (ticket == null) {
			log.warn("Authentication failed for incoming request on embedded SSH server from {}: unknown ticket {}",
				session.getRemoteAddress(), username);
			AAA_LOG.warn("Authentication failed for incoming request on embedded SSH server from {}: unknown ticket {}",
				session.getRemoteAddress(), username);
			return false;
		}
		synchronized (ticket) {
			if (!ticket.isValid()) {
				log.warn("Authentication failed for incoming request on embedded SSH server from {}: invalid ticket {}",
					session.getRemoteAddress(), username);
				AAA_LOG.warn("Authentication failed for incoming request on embedded SSH server from {}: invalid ticket {}",
					session.getRemoteAddress(), username);
				return false;
			}
			NetworkAddress source = null;
			try {
				if (session.getRemoteAddress() instanceof InetSocketAddress iAddress) {
					source = NetworkAddress.getNetworkAddress(iAddress.getAddress());
				}
			}
			catch (UnknownHostException e) {
				// Ignore
			}
			if (!ticket.checkPassword(source, password)) {
				log.warn("Authentication failed for incoming request on embedded SSH server from {}: credentials rejected for ticket {}",
					session.getRemoteAddress(), username);
				AAA_LOG.warn("Authentication failed for incoming request on embedded SSH server from {}: credentials rejected for ticket {}",
					session.getRemoteAddress(), username);
				ticket.onSessionRejected(session.getAttribute(SESSION_LOG_BUFFER));
				return false;
			}
		}
		log.info("Authentication succeeded for incoming request on embedded SSH server from {}, for ticket {}",
			session.getRemoteAddress(), username);
		session.setAttribute(UPLOAD_TICKET, ticket);

		// Get the session log buffer and pass logs to the ticket
		SessionLogBuffer logBuffer = session.getAttribute(SESSION_LOG_BUFFER);
		ticket.onSessionStarted(logBuffer);
		return true;
	}

	/**
	 * Check whether the upload ticket of the session allows a transfer protocol (the ticket is told otherwise).
	 * @param channel the channel of the transfer
	 * @param protocol the transfer protocol
	 * @return true if the ticket allows the protocol
	 */
	private boolean isTicketProtocolAllowed(ChannelSession channel, TransferProtocol protocol) {
		ServerSession session = channel.getServerSession();
		UploadTicket ticket = session.getAttribute(UPLOAD_TICKET);
		if (ticket != null && ticket.getAllowedProtocols().contains(protocol)) {
			return true;
		}
		log.warn("{} transfer refused for session from {} with ticket {}: protocol not allowed by the ticket",
			protocol, session.getRemoteAddress(), session.getUsername());
		if (ticket != null) {
			ticket.onProtocolRefused(protocol);
		}
		return false;
	}

	/**
	 * Command replacing a refused transfer (protocol disabled, or not allowed by the ticket): it writes the error and exits
	 * with status 1, which closes the channel (a failed channel request alone would leave
	 * the clients which don't wait for the reply hanging).
	 */
	private static final class RefusedTransfer implements Command {
		private final String message;
		private OutputStream err;
		private ExitCallback exitCallback;

		RefusedTransfer(String message) {
			this.message = message;
		}

		@Override
		public void setInputStream(InputStream in) {
			// Not read
		}

		@Override
		public void setOutputStream(OutputStream out) {
			// Not written
		}

		@Override
		public void setErrorStream(OutputStream errorStream) {
			this.err = errorStream;
		}

		@Override
		public void setExitCallback(ExitCallback callback) {
			this.exitCallback = callback;
		}

		@Override
		public void start(ChannelSession channel, Environment env) throws IOException {
			try {
				this.err.write((this.message + "\n").getBytes(StandardCharsets.UTF_8));
				this.err.flush();
			}
			finally {
				this.exitCallback.onExit(1, this.message);
			}
		}

		@Override
		public void destroy(ChannelSession channel) {
			// Nothing to release
		}
	}

	/**
	 * Register a new upload ticket.
	 * @param ticket the ticket to register
	 * @throws IOException
	 */
	public void registerUploadTicket(UploadTicket ticket) throws IOException {
		this.registeredTickets.incrementAndGet();
		synchronized (ticket) {
			UploadTicket existing = this.tickets.putIfAbsent(ticket.getUsername(), ticket);
			if (existing != null) {
				throw new IOException(
					"Upload ticket '%s' already exists".formatted(ticket.getUsername()));
			}
		}
	}

	/**
	 * Clear upload ticket(s) of given owner.
	 * @param owner the owner
	 */
	public void clearUploadTickets(UploadTicket.Owner owner) {
		Iterator<Entry<String, UploadTicket>> ticketIt = this.tickets.entrySet().iterator();
		while (ticketIt.hasNext()) {
			UploadTicket ticket = ticketIt.next().getValue();
			synchronized (ticket) {
				if (Objects.equals(owner, ticket.getOwner())) {
					ticketIt.remove();
				}
			}
		}
	}

	/**
	 * Retrive the public host keys of the embedded SSH server.
	 * @return the public host keys
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	public Set<PublicKey> getPublicHostKeys() throws IOException, GeneralSecurityException {
		Set<PublicKey> keys = new HashSet<>();
		for (KeyPair kp : this.sshd.getKeyPairProvider().loadKeys(null)) {
			keys.add(kp.getPublic());
		}
		return keys;
	}
}
