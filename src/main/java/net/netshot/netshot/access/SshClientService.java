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
package net.netshot.netshot.access;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.config.keys.ClientIdentityLoader;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.client.session.SessionFactory;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.core.CoreModuleProperties;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.lifecycle.Service;

/**
 * The SSH client shared by the SSH accesses to the devices ({@link Ssh}): built with the settings of
 * the configuration (applied again on each change), started and stopped with the server.
 */
public final class SshClientService implements Service {

	/** The client. */
	private final SshClient client;

	/** The number of sessions opened since the start. */
	private final AtomicLong openedSessions = new AtomicLong();

	/** The number of sessions currently open. */
	private final AtomicInteger openSessions = new AtomicInteger();

	/**
	 * Builds the client (not started).
	 *
	 * @param config the configuration store, whose changes are applied to the client
	 */
	public SshClientService(ConfigStore config) {
		this.client = SshClient.setUpDefaultClient();
		this.client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
		this.client.setFilePasswordProvider(FilePasswordProvider.EMPTY);
		this.client.setClientIdentityLoader(ClientIdentityLoader.DEFAULT);
		this.client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
		this.client.setSessionFactory(new SessionFactory(this.client) {
			@Override
			protected ClientSessionImpl doCreateSession(IoSession ioSession) throws Exception {
				return new Ssh.NetshotClientSession(getClient(), ioSession);
			}
		});

		CoreModuleProperties.CLIENT_IDENTIFICATION.set(this.client, "NETSHOT-%s".formatted(Netshot.VERSION));
		// Cisco IOS at least doesn't like receiving the identification message from client before sending its own
		CoreModuleProperties.SEND_IMMEDIATE_IDENTIFICATION.set(this.client, false);
		CoreModuleProperties.SEND_IMMEDIATE_KEXINIT.set(this.client, false);
		// Fallback when a connection is established without a per-access verifier
		// (e.g. tests) - real connections always supply one, see Ssh.connect(boolean).
		this.client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
		this.client.addSessionListener(new SessionListener() {
			@Override
			public void sessionCreated(org.apache.sshd.common.session.Session session) {
				SshClientService.this.openedSessions.incrementAndGet();
				SshClientService.this.openSessions.incrementAndGet();
			}

			@Override
			public void sessionClosed(org.apache.sshd.common.session.Session session) {
				SshClientService.this.openSessions.decrementAndGet();
			}

			@Override
			public void sessionEstablished(org.apache.sshd.common.session.Session session) {
				if (!(session instanceof ClientSession clientSession)) {
					return;
				}
				Object rawContext = session.getIoSession().getAttribute(AttributeRepository.class);
				ServerKeyVerifier verifier = (rawContext instanceof AttributeRepository context)
					? context.getAttribute(Ssh.HOST_KEY_VERIFIER_ATTRIBUTE)
					: null;
				if (verifier != null) {
					clientSession.setServerKeyVerifier(verifier);
				}
			}
		});
		this.applySettings();
		// Applied again on each configuration change
		config.addListener(_ -> this.applySettings());
	}

	/**
	 * Applies the settings to the client.
	 */
	private void applySettings() {
		Ssh.Settings settings = Ssh.settings();
		CoreModuleProperties.NIO2_READ_TIMEOUT.set(this.client, Duration.ofMillis(settings.receiveTimeout()));
		CoreModuleProperties.REKEY_TIME_LIMIT.set(this.client, Duration.ofMillis(settings.rekeyTimeLimit()));
		CoreModuleProperties.REKEY_BYTES_LIMIT.set(this.client, settings.rekeyDataLimit());
		CoreModuleProperties.REKEY_BLOCKS_LIMIT.set(this.client, settings.rekeyBlocksLimit());
		CoreModuleProperties.REKEY_PACKETS_LIMIT.set(this.client, settings.rekeyPacketsLimit());
	}

	/**
	 * Gets the client.
	 *
	 * @return the SSH client (usable once the service is started)
	 */
	public SshClient client() {
		return this.client;
	}

	@Override
	public String name() {
		return "SSH client";
	}

	@Override
	public void start() {
		this.client.start();
	}

	@Override
	public void stop() {
		this.client.stop();
	}

	@Override
	public boolean isHealthy() {
		return this.client.isStarted();
	}

	@Override
	public Map<String, Object> stats() {
		return Map.of("openedSessions", this.openedSessions.get(), "openSessions", this.openSessions.get());
	}
}
