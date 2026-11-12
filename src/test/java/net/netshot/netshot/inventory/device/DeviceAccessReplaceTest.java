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
package net.netshot.netshot.inventory.device;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Device access replace test")
class DeviceAccessReplaceTest {

	private Device fakeDevice() {
		Domain domain = Domain.builder().name("Test domain").description("Fake domain for tests").build();
		return Device.create("CiscoIOS12", null, domain, "test");
	}

	// Gives the device the incoming accesses, as an edit does
	private static Device replace(Device device, DeviceAccess... incoming) {
		return device.toBuilder().accesses(Device.mergeAccesses(device.accesses(), List.of(incoming))).build();
	}

	@Test
	@DisplayName("mergeAccesses copies the SSH/HTTPS trust fields onto the managed access")
	void replaceAccessesCopiesTrustFields() {
		DeviceAccess sshIncoming = DeviceAccess.of("ssh").toBuilder()
			.sshHostKeyVerification(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN)
			.sshTrustedHostKeys("ssh-ed25519 AAAAKEY")
			.build();
		DeviceAccess httpsIncoming = DeviceAccess.of("https").toBuilder()
			.httpsCaTrustMode(HttpsCaTrustMode.CUSTOM_CA)
			.httpsCustomCaCertificate("-----BEGIN CERTIFICATE-----FAKE-----END CERTIFICATE-----")
			.build();

		Device device = replace(fakeDevice(), sshIncoming, httpsIncoming);

		DeviceAccess sshAccess = device.getDeviceAccess("ssh");
		Assertions.assertEquals(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN, sshAccess.sshHostKeyVerification());
		Assertions.assertEquals("ssh-ed25519 AAAAKEY", sshAccess.sshTrustedHostKeys());
		Assertions.assertNotNull(sshAccess.sshTrustedHostKeysSince(),
			"Setting a trusted key for the first time should stamp 'since'");

		DeviceAccess httpsAccess = device.getDeviceAccess("https");
		Assertions.assertEquals(HttpsCaTrustMode.CUSTOM_CA, httpsAccess.httpsCaTrustMode());
		Assertions.assertEquals("-----BEGIN CERTIFICATE-----FAKE-----END CERTIFICATE-----",
			httpsAccess.httpsCustomCaCertificate());
	}

	@Test
	@DisplayName("mergeAccesses actually clears the trusted host keys when resubmitted empty (regression test)")
	void replaceAccessesClearsTrustedHostKeys() {
		Device device = replace(fakeDevice(), DeviceAccess.of("ssh").toBuilder()
			.sshHostKeyVerification(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN)
			.sshTrustedHostKeys("ssh-ed25519 AAAAKEY")
			.build());
		Assertions.assertEquals("ssh-ed25519 AAAAKEY", device.getDeviceAccess("ssh").sshTrustedHostKeys());
		Assertions.assertNotNull(device.getDeviceAccess("ssh").sshTrustedHostKeysSince());

		// Re-submit with the field cleared, as the "clear" button in the device edit form does.
		device = replace(device, DeviceAccess.of("ssh").toBuilder()
			.sshHostKeyVerification(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN)
			.sshTrustedHostKeys(null)
			.build());

		DeviceAccess sshAccess = device.getDeviceAccess("ssh");
		Assertions.assertNull(sshAccess.sshTrustedHostKeys(), "The trusted host keys should actually be cleared");
		Assertions.assertNull(sshAccess.sshTrustedHostKeysSince(),
			"The 'since' timestamp should be cleared along with the keys");
	}

	@Test
	@DisplayName("mergeAccesses does not bump sshTrustedHostKeysSince when resubmitted unchanged")
	void replaceAccessesKeepsSinceWhenUnchanged() throws InterruptedException {
		Device device = replace(fakeDevice(), DeviceAccess.of("ssh").toBuilder().sshTrustedHostKeys("ssh-ed25519 AAAAKEY").build());
		Instant firstSince = device.getDeviceAccess("ssh").sshTrustedHostKeysSince();
		Assertions.assertNotNull(firstSince);

		Thread.sleep(5);

		device = replace(device, DeviceAccess.of("ssh").toBuilder().sshTrustedHostKeys("ssh-ed25519 AAAAKEY").build());

		Assertions.assertEquals(firstSince, device.getDeviceAccess("ssh").sshTrustedHostKeysSince(),
			"Resubmitting the same value should not touch the 'since' timestamp");
	}
}
