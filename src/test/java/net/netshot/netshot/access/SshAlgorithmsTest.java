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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.kex.KexProposalOption;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import net.netshot.netshot.driver.protocol.SshConfig;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.TestConnectors;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * The SSH client proposes the algorithms of the configuration of the access, and only them, from the first
 * key exchange of the session.
 */
@SimulatorTest
@DisplayName("SSH algorithms of an access")
public class SshAlgorithmsTest {

	@Container
	private static final GenericContainer<?> CONTAINER = DeviceSimulators.ssh("Cisco_IOS");

	/**
	 * Records the client proposal of each key exchange of the shared SSH client of the tests while some work runs.
	 *
	 * @param work the work
	 * @return the client proposals
	 * @throws Exception if the work fails
	 */
	private static List<Map<KexProposalOption, String>> clientProposals(ThrowingRunnable work) throws Exception {
		SshClient client = TestConnectors.sshClient();
		List<Map<KexProposalOption, String>> proposals = Collections.synchronizedList(new ArrayList<>());
		SessionListener listener = new SessionListener() {
			@Override
			public void sessionNegotiationEnd(Session session, Map<KexProposalOption, String> clientProposal,
				Map<KexProposalOption, String> serverProposal, Map<KexProposalOption, String> negotiatedOptions,
				Throwable reason) {
				proposals.add(Map.copyOf(clientProposal));
			}
		};
		client.addSessionListener(listener);
		try {
			work.run();
		}
		finally {
			client.removeSessionListener(listener);
		}
		return proposals;
	}

	/** Some work which can throw. */
	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	/**
	 * Builds the configuration of the access: one cipher and one MAC.
	 *
	 * @return the configuration
	 */
	private static SshConfig restrictedConfig() {
		SshConfig config = new SshConfig(Ssh.settings());
		config.setCiphers(new String[] { "aes128-ctr" });
		config.setMacs(new String[] { "hmac-sha2-256" });
		return config;
	}

	private static void assertRestricted(List<Map<KexProposalOption, String>> proposals, int expectedCount) {
		Assertions.assertEquals(expectedCount, proposals.size(), "One (initial) key exchange per connection");
		for (Map<KexProposalOption, String> proposal : proposals) {
			Assertions.assertEquals("aes128-ctr", proposal.get(KexProposalOption.C2SENC), "Proposed ciphers " + proposal);
			Assertions.assertEquals("aes128-ctr", proposal.get(KexProposalOption.S2CENC), "Proposed ciphers " + proposal);
			Assertions.assertEquals("hmac-sha2-256", proposal.get(KexProposalOption.C2SMAC), "Proposed MACs " + proposal);
		}
	}

	@Test
	@DisplayName("A connection proposes the configured algorithms only")
	void connectionProposesTheConfiguredAlgorithms() throws Exception {
		List<Map<KexProposalOption, String>> proposals = clientProposals(() -> {
			for (int i = 0; i < 5; i++) {
				Ssh ssh = new Ssh(TestConnectors.sshClient(), CONTAINER.getHost(), CONTAINER.getMappedPort(22), "admin", "admin",
					new FakeTaskContext());
				SshConfig config = new SshConfig();
				config.setCiphers(new String[] { "aes128-ctr" });
				config.setMacs(new String[] { "hmac-sha2-256" });
				ssh.applySshConfig(config);
				ssh.connect(false);
				ssh.disconnect();
			}
		});
		assertRestricted(proposals, 5);
	}

	@Test
	@DisplayName("The algorithms are set before the first key exchange, however late the caller gets the session")
	void algorithmsAreSetBeforeTheFirstKeyExchange() throws Exception {
		List<Map<KexProposalOption, String>> proposals = clientProposals(() -> {
			// As Ssh.connect opens the session, with a caller stalled after the connection (a loaded server)
			AttributeRepository context = AttributeRepository.ofKeyValuePair(Ssh.SSH_CONFIG_ATTRIBUTE, restrictedConfig());
			try (ClientSession session = TestConnectors.sshClient()
				.connect("admin", CONTAINER.getHost(), CONTAINER.getMappedPort(22), context, null)
				.verify(Duration.ofSeconds(10)).getSession()) {
				Thread.sleep(500);
				session.addPasswordIdentity("admin");
				session.auth().verify(Duration.ofSeconds(10));
			}
		});
		assertRestricted(proposals, 1);
	}
}
