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
package net.netshot.netshot.inventory.device;

import java.util.Date;
import java.util.List;

import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.testing.UnitTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnitTest
@DisplayName("Device access replace test")
public class DeviceAccessReplaceTest {

	private Device fakeDevice() {
		Domain domain = new Domain("Test domain", "Fake domain for tests", null, null);
		return new Device("CiscoIOS12", null, domain, "test");
	}

	@Test
	@DisplayName("replaceAccesses copies the SSH/HTTPS trust fields onto the managed access")
	void replaceAccessesCopiesTrustFields() {
		Device device = fakeDevice();
		DeviceAccess sshIncoming = new DeviceAccess(device, "ssh");
		sshIncoming.setSshHostKeyVerification(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN);
		sshIncoming.setSshTrustedHostKeys("ssh-ed25519 AAAAKEY");
		DeviceAccess httpsIncoming = new DeviceAccess(device, "https");
		httpsIncoming.setHttpsCaTrustMode(HttpsCaTrustMode.CUSTOM_CA);
		httpsIncoming.setHttpsCustomCaCertificate("-----BEGIN CERTIFICATE-----FAKE-----END CERTIFICATE-----");

		device.replaceAccesses(List.of(sshIncoming, httpsIncoming));

		DeviceAccess sshAccess = device.getDeviceAccess("ssh");
		Assertions.assertEquals(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN, sshAccess.getSshHostKeyVerification());
		Assertions.assertEquals("ssh-ed25519 AAAAKEY", sshAccess.getSshTrustedHostKeys());
		Assertions.assertNotNull(sshAccess.getSshTrustedHostKeysSince(),
			"Setting a trusted key for the first time should stamp 'since'");

		DeviceAccess httpsAccess = device.getDeviceAccess("https");
		Assertions.assertEquals(HttpsCaTrustMode.CUSTOM_CA, httpsAccess.getHttpsCaTrustMode());
		Assertions.assertEquals("-----BEGIN CERTIFICATE-----FAKE-----END CERTIFICATE-----",
			httpsAccess.getHttpsCustomCaCertificate());
	}

	@Test
	@DisplayName("replaceAccesses actually clears the trusted host keys when resubmitted empty (regression test)")
	void replaceAccessesClearsTrustedHostKeys() {
		Device device = fakeDevice();
		DeviceAccess initial = new DeviceAccess(device, "ssh");
		initial.setSshHostKeyVerification(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN);
		initial.setSshTrustedHostKeys("ssh-ed25519 AAAAKEY");
		device.replaceAccesses(List.of(initial));
		Assertions.assertEquals("ssh-ed25519 AAAAKEY", device.getDeviceAccess("ssh").getSshTrustedHostKeys());
		Assertions.assertNotNull(device.getDeviceAccess("ssh").getSshTrustedHostKeysSince());

		// Re-submit with the field cleared, as the "clear" button in the device edit form does.
		DeviceAccess cleared = new DeviceAccess(device, "ssh");
		cleared.setSshHostKeyVerification(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN);
		cleared.setSshTrustedHostKeys(null);
		device.replaceAccesses(List.of(cleared));

		DeviceAccess sshAccess = device.getDeviceAccess("ssh");
		Assertions.assertNull(sshAccess.getSshTrustedHostKeys(), "The trusted host keys should actually be cleared");
		Assertions.assertNull(sshAccess.getSshTrustedHostKeysSince(),
			"The 'since' timestamp should be cleared along with the keys");
	}

	@Test
	@DisplayName("replaceAccesses does not bump sshTrustedHostKeysSince when resubmitted unchanged")
	void replaceAccessesKeepsSinceWhenUnchanged() throws InterruptedException {
		Device device = fakeDevice();
		DeviceAccess initial = new DeviceAccess(device, "ssh");
		initial.setSshTrustedHostKeys("ssh-ed25519 AAAAKEY");
		device.replaceAccesses(List.of(initial));
		Date firstSince = device.getDeviceAccess("ssh").getSshTrustedHostKeysSince();
		Assertions.assertNotNull(firstSince);

		Thread.sleep(5);

		DeviceAccess resubmitted = new DeviceAccess(device, "ssh");
		resubmitted.setSshTrustedHostKeys("ssh-ed25519 AAAAKEY");
		device.replaceAccesses(List.of(resubmitted));

		Assertions.assertEquals(firstSince, device.getDeviceAccess("ssh").getSshTrustedHostKeysSince(),
			"Resubmitting the same value should not touch the 'since' timestamp");
	}
}
