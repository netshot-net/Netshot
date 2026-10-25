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

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.session.SessionListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import net.netshot.netshot.TestConfig;
import net.netshot.netshot.access.Ssh;
import net.netshot.netshot.task.engine.TaskContext;
import net.netshot.netshot.testing.DeviceSimulators;
import net.netshot.netshot.testing.SimulatorTest;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * Exploration/reproduction tests for the NetScaler SDX SSH rekeying bug: a driver can ask to
 * disable/tighten SSH rekeying via {@code CLI.ssh.config.rekey} ({@link Ssh.SshConfig}'s
 * {@code rekeyTimeLimit}/{@code rekeyDataLimit}/{@code rekeyPacketsLimit}), but as of this
 * writing that per-connection override is applied too late to matter for those three limits -
 * see {@code Ssh.connect(boolean)}: {@code CoreModuleProperties.REKEY_*.set(this.session, ...)}
 * runs only after {@code Ssh.client.connect(...).verify(...)} returns, while
 * {@code AbstractSession.refreshConfiguration()} - which is what actually captures
 * {@code maxRekeyInterval}/{@code maxRekeyBytes}/{@code maxRekyPackets} into the fields
 * {@code isRekeyRequired()} checks - runs once, synchronously, from inside the session
 * constructor, strictly before that point. Only the *global* {@code Ssh.client}-wide default
 * (set once via {@code Ssh.loadConfig()}, before any session exists) is visible in time.
 * <p>
 * These tests don't rely on any specific vendor driver, or on transferring enough data to
 * naturally cross a byte-based rekey threshold (impractical against the real OpenSSH-backed
 * simulator's constrained CLI, which only ever offers a tiny driver-emulation shell). Instead
 * they use a deliberately tiny (but not absurdly so - see below) global rekey <em>data</em>
 * limit and observe how many {@link SessionListener.Event#KeyEstablished} session events fire: 1 means only
 * the initial key exchange happened, 2+ means at least one rekey was also triggered.
 * <p>
 * A tiny <em>time</em>-based limit was tried first and discarded: at 1ms, the limit is crossed
 * again within a millisecond of each rekey completing, so the session enters a permanent rekey
 * storm that can starve the auth exchange entirely (observed as an auth timeout in practice).
 * A byte limit doesn't have this failure mode, because {@code inBytesCount}/{@code
 * outBytesCount} reset to 0 on every key exchange (including a rekey) - so once one rekey
 * completes, it takes another full threshold's worth of fresh traffic to trigger the next one,
 * and a bare authenticated connection with no channel/commands never generates that much more.
 */
@SimulatorTest
@DisplayName("SSH rekey behavior")
public class RekeyTest {

	TaskContext taskContext = new FakeTaskContext();

	@Container
	private static final GenericContainer<?> CONTAINER = DeviceSimulators.ssh("Cisco_IOS");

	/** 2000 bytes: small enough to trigger reliably right after connecting, but - as found
	 *  while writing this test - NOT so small that the rekey lands *during* the auth exchange
	 *  itself. A first attempt at 300 bytes did exactly that: it landed right after
	 *  SSH_MSG_SERVICE_ACCEPT, before the password SSH_MSG_USERAUTH_REQUEST - against this real
	 *  OpenSSH server (strict KEX negotiated), the server replied SSH_MSG_UNIMPLEMENTED to the
	 *  resulting mid-auth SSH_MSG_KEXINIT and the connection just hung until the 30s auth
	 *  timeout - a genuine protocol-level breakage, and itself a striking illustration of how
	 *  badly a live session can react to an unwelcome rekey (not unlike the NetScaler SDX
	 *  symptom this is all about). 2000 bytes reliably lands the rekey after auth succeeds
	 *  instead, which is also more representative of the production case (a rekey firing well
	 *  into an established SFTP transfer, not while still authenticating). Unlike an equivalent
	 *  time-based limit, a byte limit is also self-limiting - see this class's javadoc for why
	 *  that avoids a permanent rekey storm. */
	private static final String TINY_REKEY_DATA_LIMIT = "900";

	/** Saved so the tiny global rekey data limit set below can be restored afterwards -
	 *  {@code Ssh.client} is shared process-wide, so leaving it in place would affect every
	 *  other test in this class/JVM run. */
	private static String originalRekeyDataLimit;

	@BeforeAll
	static void forceTinyGlobalRekeyDataLimit() {
		Properties config = TestConfig.properties();
		originalRekeyDataLimit = config.getProperty("netshot.cli.ssh.rekeydatalimit");
		config.setProperty("netshot.cli.ssh.rekeydatalimit", TINY_REKEY_DATA_LIMIT);
		TestConfig.init(config);
		Ssh.loadConfig();
	}

	@AfterAll
	static void restoreGlobalRekeyDataLimit() {
		Properties config = TestConfig.properties();
		if (originalRekeyDataLimit == null) {
			config.remove("netshot.cli.ssh.rekeydatalimit");
		}
		else {
			config.setProperty("netshot.cli.ssh.rekeydatalimit", originalRekeyDataLimit);
		}
		TestConfig.init(config);
		Ssh.loadConfig();
	}

	/**
	 * Loggers bumped to DEBUG only for the duration of this nested class, so the rekey
	 * decision logic (isRekeyDataSizeExceeded/requestNewKeysExchange/setOutputEncoding's
	 * "blocks limit" line, etc.) is visible in test output without turning on SSH DEBUG
	 * logging - and its channel/IO noise - for every other test in this file. Set
	 * programmatically (rather than in logback-test.xml) precisely so it's scoped to this
	 * class's own lifecycle instead of the whole test run.
	 * <p>
	 * Two names, not one: that decision logic lives in {@code AbstractSession}, but MINA
	 * SSHD's {@code AbstractLoggingBean} binds its {@code log} field via {@code
	 * LoggerFactory.getLogger(getClass())} - the RUNTIME class - so a logger named after
	 * {@code AbstractSession} itself would never match anything actually logged. The runtime
	 * class is {@code org.apache.sshd.client.session.ClientSessionImpl} without the {@code
	 * refreshConfiguration()} fix in place, or {@code Ssh$NetshotClientSession} with it -
	 * covering both keeps this working whether or not that fix is currently applied/stashed.
	 */
	private static final String[] REKEY_DEBUG_LOGGER_NAMES = {
		"org.apache.sshd.client.session",
		"net.netshot.netshot.access.Ssh$NetshotClientSession",
	};

	/** Saved so the loggers above can be restored to their prior level afterwards. */
	private static Level[] originalLoggerLevels;

	@BeforeAll
	static void enableRekeyDebugLogging() {
		originalLoggerLevels = new Level[REKEY_DEBUG_LOGGER_NAMES.length];
		for (int i = 0; i < REKEY_DEBUG_LOGGER_NAMES.length; i++) {
			Logger logger = (Logger) LoggerFactory.getLogger(REKEY_DEBUG_LOGGER_NAMES[i]);
			originalLoggerLevels[i] = logger.getLevel();
			logger.setLevel(Level.DEBUG);
		}
	}

	@AfterAll
	static void restoreRekeyDebugLogging() {
		for (int i = 0; i < REKEY_DEBUG_LOGGER_NAMES.length; i++) {
			((Logger) LoggerFactory.getLogger(REKEY_DEBUG_LOGGER_NAMES[i])).setLevel(originalLoggerLevels[i]);
		}
	}

	/**
	 * Connects (auth only, no shell channel needed) and counts how many
	 * {@link SessionListener.Event#KeyEstablished} events fire on the shared {@code Ssh.client} for the
	 * duration of the connection. {@code Ssh.client} has no public accessor, so it's reached
	 * the same way this test class already reaches {@code SnapshotDeviceScript#run}: reflection.
	 * @param ssh the SSH access to connect with
	 * @return the number of KeyEstablished events
	 */
	private static int connectAndCountKeyEstablishedEvents(Ssh ssh) throws Exception {
		Field clientField = Ssh.class.getDeclaredField("client");
		clientField.setAccessible(true);
		SshClient client = (SshClient) clientField.get(null);

		AtomicInteger keyEstablishedCount = new AtomicInteger(0);
		SessionListener listener = new SessionListener() {
			@Override
			public void sessionEvent(org.apache.sshd.common.session.Session session, Event event) {
				if (event == Event.KeyEstablished) {
					keyEstablishedCount.incrementAndGet();
				}
			}
		};
		client.addSessionListener(listener);
		try {
			// openChannel=true: a plain auth-only connection generates no further traffic
			// once auth succeeds, so there'd be no later message to notice the crossed
			// threshold on. Opening the channel (PTY + shell request, plus whatever banner
			// the simulated device prints back) reliably supplies that.
			ssh.connect(true);
			// Give a triggered rekey (requested as soon as the tiny limit is crossed, but
			// only counted once negotiation actually completes) time to finish.
			Thread.sleep(1000);
		}
		finally {
			ssh.disconnect();
			client.removeSessionListener(listener);
		}
		return keyEstablishedCount.get();
	}

	@Test
	@DisplayName("Tiny global rekey data limit alone forces a rekey (sanity check)")
	void globalTinyLimitForcesRekey() throws Exception {
		Ssh ssh = new Ssh(CONTAINER.getHost(), CONTAINER.getMappedPort(22), "admin", "admin", this.taskContext);
		int keyEstablishedCount = connectAndCountKeyEstablishedEvents(ssh);
		Assertions.assertTrue(keyEstablishedCount >= 2,
			"Expected at least one rekey (2+ KeyEstablished events) with the "
				+ TINY_REKEY_DATA_LIMIT + "-byte global rekey data limit and no per-connection "
				+ "override, got " + keyEstablishedCount
				+ " - the harness itself may not be detecting/forcing rekeys correctly");
	}

	@Test
	@DisplayName("Driver-level rekey.dataLimit=0 override should disable the forced rekey")
	void driverOverrideShouldDisableRekey() throws Exception {
		Ssh ssh = new Ssh(CONTAINER.getHost(), CONTAINER.getMappedPort(22), "admin", "admin", this.taskContext);
		// Exactly what DeviceDriver.java does for a driver's `CLI.ssh.config.rekey.dataLimit: 0`
		// (see Citrix_NetscalerSDX.js): only this one field is set, everything else stays
		// unset/null so applySshConfig() leaves it alone.
		Ssh.SshConfig disableRekey = new Ssh.SshConfig(false);
		disableRekey.setRekeyDataLimit(0L);
		ssh.applySshConfig(disableRekey);

		int keyEstablishedCount = connectAndCountKeyEstablishedEvents(ssh);
		Assertions.assertEquals(1, keyEstablishedCount,
			"Expected no rekey (1 KeyEstablished event, the initial exchange) now that the driver "
				+ "explicitly disabled it via rekey.dataLimit=0, got " + keyEstablishedCount
				+ " - the per-connection override did not take effect (see this class's javadoc)");
	}
}
