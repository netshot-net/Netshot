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
package net.netshot.netshot.api.hook;

import java.util.LinkedHashSet;
import java.util.Map;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.hook.WebHook.Action;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.testing.TestTls;
import net.netshot.netshot.testing.TestTls.Identity;
import net.netshot.netshot.testing.UnitTest;

/**
 * The web hook sender on HTTPS targets: the CA trust policy of the hook applies to the connection (the
 * policies themselves are tested by {@code platform.tls.HttpsTrustPolicyTest}).
 */
@UnitTest
@DisplayName("Web hook sender over HTTPS")
class WebHookSenderTest {

	private static WebHook hook(TestTls.Server server, HttpsCaTrustMode mode, String customCa) {
		return new WebHook(1, 0, null, "TLS hook", true, new LinkedHashSet<>(), Action.POST_JSON,
			server.url("localhost").toString(), mode, customCa);
	}

	@Test
	@DisplayName("The trust policy of the hook applies: system trust store refuses the unknown CA, the custom CA is accepted")
	void trustPolicy() throws Exception {
		Identity authority = TestTls.authority("Hook CA");
		try (TestTls.Server server = new TestTls.Server(TestTls.server(authority, "localhost"), "{}")) {
			WebHookSender sender = new WebHookSender();
			Exception e = Assertions.assertThrows(Exception.class,
				() -> sender.send(hook(server, HttpsCaTrustMode.SYSTEM_TRUSTSTORE, null), Map.of("event", "test")));
			Assertions.assertTrue(hasSslCause(e), "Not refused by TLS: " + e);

			Assertions.assertEquals("HTTP response code 200",
				sender.send(hook(server, HttpsCaTrustMode.CUSTOM_CA, authority.pem()), Map.of("event", "test")));
			Assertions.assertEquals("HTTP response code 200",
				sender.send(hook(server, HttpsCaTrustMode.TRUST_ANY, null), Map.of("event", "test")));
		}
	}

	private static boolean hasSslCause(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SSLException) {
				return true;
			}
		}
		return false;
	}
}
