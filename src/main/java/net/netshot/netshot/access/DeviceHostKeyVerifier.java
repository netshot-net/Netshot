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

import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.device.DeviceAccess.SshHostKeyVerification;
import net.netshot.netshot.platform.log.TaskContext;

/**
 * Per-access SSH server host key verifier, implementing the two
 * {@link SshHostKeyVerification} modes:
 * <ul>
 * <li>{@code TRUST_ANY} - always accepts (the default).</li>
 * <li>{@code TRUST_KNOWN} - accepts only a presented key matching the
 * trusted entry on record for its algorithm (known_hosts-style, one entry
 * per algorithm), <em>except</em> when nothing at all is on record yet, in
 * which case the presented key is learned and stored instead of being
 * rejected. Once at least one entry exists, a key for a not-yet-seen
 * algorithm is rejected rather than silently learned in addition to it -
 * only the very first key, on an otherwise empty record, is ever learned
 * automatically.</li>
 * </ul>
 * <p>
 * This is invoked by the SSH library on its own I/O thread, not the task's
 * worker thread, so it does not change the {@link DeviceAccess} of the device
 * itself. Instead, a learned key is only recorded on {@link #learnedKeysUpdate},
 * for the caller to pick up and persist afterwards, back on its own thread,
 * once {@code connect()} returns (see {@code AccessManager}).
 */
@Slf4j
public class DeviceHostKeyVerifier implements ServerKeyVerifier {

	private final SshHostKeyVerification mode;
	private final String storedKeys;
	private final TaskContext taskContext;

	/** Set when a new host key has been learned and needs to be persisted by the caller. */
	@Getter
	private volatile String learnedKeysUpdate;

	/** Set with a human-readable reason whenever {@link #verifyServerKey} returns false. */
	@Getter
	private volatile String lastRejectionReason;

	/**
	 * Instantiates a new verifier for one connection attempt.
	 * @param mode the configured verification mode (defaults to {@code TRUST_ANY} if null)
	 * @param storedKeys the currently trusted host keys for this access, known_hosts-style
	 *        ("{@code <algorithm> <base64-key>}" per line, no hostname prefix), or null/empty if none yet
	 * @param taskContext the current task context, for logging (may be null)
	 */
	public DeviceHostKeyVerifier(SshHostKeyVerification mode, String storedKeys, TaskContext taskContext) {
		this.mode = mode == null ? SshHostKeyVerification.TRUST_ANY : mode;
		this.storedKeys = storedKeys;
		this.taskContext = taskContext;
	}

	@Override
	public boolean verifyServerKey(ClientSession session, SocketAddress remoteAddress, PublicKey serverKey) {
		if (this.mode == SshHostKeyVerification.TRUST_ANY) {
			return true;
		}

		String algorithm = KeyUtils.getKeyType(serverKey);
		String fingerprint = KeyUtils.getFingerPrint(serverKey);
		String encodedEntry;
		try {
			encodedEntry = PublicKeyEntry.toString(serverKey);
		}
		catch (IllegalArgumentException e) {
			this.lastRejectionReason = "Unable to encode the presented SSH host key: " + e.getMessage();
			log.warn(this.lastRejectionReason, e);
			return false;
		}
		String presentedKeyData = encodedEntry.substring(algorithm.length() + 1);

		List<String[]> knownEntries = DeviceHostKeyVerifier.parseKnownEntries(this.storedKeys);
		for (String[] entry : knownEntries) {
			if (!entry[0].equals(algorithm)) {
				continue;
			}
			if (entry[1].equals(presentedKeyData)) {
				this.trace("SSH host key for algorithm '{}' (fingerprint {}) matches the trusted key on record.",
					algorithm, fingerprint);
				return true;
			}
			this.lastRejectionReason = String.format(
				"The presented SSH host key (algorithm '%s', fingerprint %s) does not match the key trusted "
					+ "so far for this algorithm. If this change is expected (e.g. device reinstall), "
					+ "the trusted key must be explicitly reset in the device's access configuration.",
				algorithm, fingerprint);
			this.error(this.lastRejectionReason);
			return false;
		}

		if (!knownEntries.isEmpty()) {
			// At least one other key is already trusted for this access - a not-yet-seen
			// algorithm is never silently trusted alongside it, only an initially empty
			// record gets auto-populated (see below).
			this.lastRejectionReason = String.format(
				"No trusted SSH host key configured for algorithm '%s' (fingerprint %s).", algorithm, fingerprint);
			this.error(this.lastRejectionReason);
			return false;
		}

		// Nothing trusted at all yet: learn and store this first key.
		this.learnedKeysUpdate = algorithm + " " + presentedKeyData;
		// Not yet persisted at this point (this runs on the SSH library's own I/O thread,
		// before the connect() call even returns) - the definitive "accepted and saved"
		// task log message is emitted by the caller once it's actually been saved, see
		// AccessManager.recordLearnedSshHostKey.
		this.trace("New SSH host key accepted for algorithm '{}' (fingerprint {}), to be saved.",
			algorithm, fingerprint);
		return true;
	}

	/**
	 * Parses a known_hosts-style block of trusted host key entries.
	 * @param text one "{@code <algorithm> <base64-key>}" entry per line (blank lines
	 *        and lines starting with "#" are ignored)
	 * @return the parsed entries, each as a {@code [algorithm, base64Key]} pair
	 */
	private static List<String[]> parseKnownEntries(String text) {
		if (text == null || text.isBlank()) {
			return Collections.emptyList();
		}
		List<String[]> entries = new ArrayList<>();
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			String[] parts = trimmed.split("\\s+");
			if (parts.length < 2) {
				continue;
			}
			entries.add(new String[] { parts[0], parts[1] });
		}
		return entries;
	}

	private void trace(String format, Object... args) {
		log.trace(format, args);
		if (this.taskContext != null) {
			this.taskContext.trace(format, args);
		}
	}

	private void error(String message) {
		log.warn(message);
		if (this.taskContext != null) {
			this.taskContext.error(message);
		}
	}

}
