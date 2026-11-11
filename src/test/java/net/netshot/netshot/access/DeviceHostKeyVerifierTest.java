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

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.inventory.device.DeviceAccess.SshHostKeyVerification;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.fake.FakeTaskContext;

/**
 * The decisions of the SSH host key verifier, for each verification mode and set of trusted keys (the
 * connections themselves are tested by {@link SshHostKeyTest}).
 */
@UnitTest
@DisplayName("SSH host key verifier")
class DeviceHostKeyVerifierTest {

	private static final PublicKey RSA = generate(KeyPairProvider.SSH_RSA, 2048);
	private static final PublicKey OTHER_RSA = generate(KeyPairProvider.SSH_RSA, 2048);
	private static final PublicKey ECDSA = generate(KeyPairProvider.ECDSA_SHA2_NISTP256, 256);
	private static final PublicKey ED25519 = generate(KeyPairProvider.SSH_ED25519, 256);

	private static PublicKey generate(String type, int size) {
		try {
			KeyPair pair = KeyUtils.generateKeyPair(type, size);
			return pair.getPublic();
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Gets the line of a key, as the trusted keys of an access hold it.
	 *
	 * @param key the public key
	 * @return the line, "<algorithm> <base64 data>"
	 */
	private static String line(PublicKey key) {
		return PublicKeyEntry.toString(key);
	}

	private static boolean verify(DeviceHostKeyVerifier verifier, PublicKey key) {
		return verifier.verifyServerKey(null, null, key);
	}

	@Test
	@DisplayName("Trust any: every key is accepted, none is learned")
	void trustAny() {
		DeviceHostKeyVerifier verifier = new DeviceHostKeyVerifier(SshHostKeyVerification.TRUST_ANY, line(RSA), null);
		Assertions.assertTrue(verify(verifier, OTHER_RSA));
		Assertions.assertTrue(verify(verifier, ECDSA));
		Assertions.assertNull(verifier.getLearnedKeysUpdate());
		Assertions.assertNull(verifier.getLastRejectionReason());
	}

	@Test
	@DisplayName("Trust known, no key trusted yet: the first key is accepted and learned, for each algorithm")
	void learnsFirstKey() {
		for (PublicKey key : new PublicKey[] { RSA, ECDSA, ED25519 }) {
			DeviceHostKeyVerifier verifier = new DeviceHostKeyVerifier(SshHostKeyVerification.TRUST_KNOWN, null, null);
			Assertions.assertTrue(verify(verifier, key), KeyUtils.getKeyType(key));
			Assertions.assertEquals(line(key), verifier.getLearnedKeysUpdate(), KeyUtils.getKeyType(key));
		}
		DeviceHostKeyVerifier blank = new DeviceHostKeyVerifier(SshHostKeyVerification.TRUST_KNOWN, "  \n# comment only\n", null);
		Assertions.assertTrue(verify(blank, RSA));
		Assertions.assertEquals(line(RSA), blank.getLearnedKeysUpdate());
	}

	@Test
	@DisplayName("No mode: trust known")
	void defaultMode() {
		DeviceHostKeyVerifier verifier = new DeviceHostKeyVerifier(null, line(RSA), null);
		Assertions.assertFalse(verify(verifier, OTHER_RSA));
		Assertions.assertTrue(verify(new DeviceHostKeyVerifier(null, line(RSA), null), RSA));
	}

	@Test
	@DisplayName("Trust known: the trusted key of the algorithm is accepted, and nothing new is learned")
	void acceptsTrustedKey() {
		String trusted = "# Lab router\n\n" + line(ECDSA) + " router1\r\n" + line(RSA) + " old comment\n";
		DeviceHostKeyVerifier verifier = new DeviceHostKeyVerifier(SshHostKeyVerification.TRUST_KNOWN, trusted, null);
		Assertions.assertTrue(verify(verifier, RSA));
		Assertions.assertTrue(verify(verifier, ECDSA));
		Assertions.assertNull(verifier.getLearnedKeysUpdate());
	}

	@Test
	@DisplayName("Trust known: another key of a trusted algorithm is refused, with its fingerprint in the task log")
	void refusesChangedKey() {
		FakeTaskContext taskContext = new FakeTaskContext();
		DeviceHostKeyVerifier verifier = new DeviceHostKeyVerifier(SshHostKeyVerification.TRUST_KNOWN, line(RSA), taskContext);
		Assertions.assertFalse(verify(verifier, OTHER_RSA));
		Assertions.assertNull(verifier.getLearnedKeysUpdate());
		String reason = verifier.getLastRejectionReason();
		Assertions.assertTrue(reason.contains("does not match"), reason);
		Assertions.assertTrue(reason.contains(KeyUtils.getFingerPrint(OTHER_RSA)), reason);
		Assertions.assertTrue(taskContext.getLog().contains("[ERROR] " + reason), taskContext.getLog());
	}

	@Test
	@DisplayName("Trust known: a key of another algorithm than the trusted ones is refused, not learned")
	void refusesNewAlgorithm() {
		DeviceHostKeyVerifier verifier = new DeviceHostKeyVerifier(SshHostKeyVerification.TRUST_KNOWN, line(RSA), null);
		Assertions.assertFalse(verify(verifier, ECDSA));
		Assertions.assertNull(verifier.getLearnedKeysUpdate());
		Assertions.assertTrue(verifier.getLastRejectionReason().contains("No trusted SSH host key configured for algorithm"),
			verifier.getLastRejectionReason());
	}

	@Test
	@DisplayName("Trust known: lines which are not keys are ignored")
	void ignoresInvalidLines() {
		DeviceHostKeyVerifier verifier = new DeviceHostKeyVerifier(SshHostKeyVerification.TRUST_KNOWN, "garbage\n   \n", null);
		Assertions.assertTrue(verify(verifier, RSA), "Nothing valid is trusted: the first key is learned");
		Assertions.assertEquals(line(RSA), verifier.getLearnedKeysUpdate());
	}
}
