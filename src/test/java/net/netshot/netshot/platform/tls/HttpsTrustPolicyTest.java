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
package net.netshot.netshot.platform.tls;

import java.net.URI;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.testing.TestTls;
import net.netshot.netshot.testing.TestTls.Identity;
import net.netshot.netshot.testing.UnitTest;

/**
 * The HTTPS trust policies, on real TLS connections to local servers: a Jersey client is built from the policy
 * as the device HTTP accesses, the web hooks and the Vault client build theirs.
 */
@UnitTest
@DisplayName("HTTPS trust policies")
@TestInstance(Lifecycle.PER_CLASS)
class HttpsTrustPolicyTest {

	private final Identity authority = TestTls.authority("Test CA");
	private final Identity otherAuthority = TestTls.authority("Other CA");

	/** Server whose certificate is signed by the test CA, for localhost and 127.0.0.1. */
	private TestTls.Server signed;

	/** Server whose certificate is signed by the test CA, for another host name. */
	private TestTls.Server otherHost;

	/** Server with a self-signed certificate. */
	private TestTls.Server selfSigned;

	@BeforeAll
	void startServers() {
		this.signed = new TestTls.Server(TestTls.server(this.authority, "localhost", "127.0.0.1"), "{}");
		this.otherHost = new TestTls.Server(TestTls.server(this.authority, "device.example.net"), "{}");
		this.selfSigned = new TestTls.Server(TestTls.selfSigned("localhost", "127.0.0.1"), "{}");
	}

	@AfterAll
	void stopServers() {
		this.signed.close();
		this.otherHost.close();
		this.selfSigned.close();
	}

	/**
	 * Calls a server with a client following a trust policy.
	 *
	 * @param mode the CA trust mode
	 * @param customCa the custom CA certificate(s), in PEM format
	 * @param url the URL to call
	 * @return null if the call succeeded, the reason of the failure otherwise
	 */
	private static Throwable call(HttpsCaTrustMode mode, String customCa, URI url) throws Exception {
		ClientBuilder builder = ClientBuilder.newBuilder().sslContext(HttpsTrustPolicy.buildSslContext(mode, customCa));
		HostnameVerifier hostnameVerifier = HttpsTrustPolicy.buildHostnameVerifier(mode);
		if (hostnameVerifier != null) {
			builder.hostnameVerifier(hostnameVerifier);
		}
		try (Client client = builder.build(); Response response = client.target(url).request().get()) {
			Assertions.assertEquals(200, response.getStatus());
			return null;
		}
		catch (ProcessingException e) {
			return e.getCause();
		}
	}

	private static void assertRefused(Throwable failure, String what) {
		Assertions.assertNotNull(failure, what + ": accepted");
		Assertions.assertInstanceOf(SSLException.class, failure, what);
	}

	@Test
	@DisplayName("Trust any: unknown CA, self-signed certificate and host name mismatch are accepted")
	void trustAny() throws Exception {
		Assertions.assertNull(call(HttpsCaTrustMode.TRUST_ANY, null, this.signed.url("localhost")));
		Assertions.assertNull(call(HttpsCaTrustMode.TRUST_ANY, null, this.selfSigned.url("127.0.0.1")));
		Assertions.assertNull(call(HttpsCaTrustMode.TRUST_ANY, null, this.otherHost.url("localhost")));
	}

	@Test
	@DisplayName("System trust store (also with no mode): a certificate of an unknown CA is refused")
	void systemTrustStore() throws Exception {
		assertRefused(call(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, null, this.signed.url("localhost")), "Unknown CA");
		assertRefused(call(null, null, this.signed.url("localhost")), "Unknown CA, no mode");
		assertRefused(call(HttpsCaTrustMode.SYSTEM_TRUSTSTORE, null, this.selfSigned.url("localhost")), "Self-signed");
	}

	@Test
	@DisplayName("Custom CA: a certificate signed by the CA is accepted, by host name and by IP address")
	void customCa() throws Exception {
		Assertions.assertNull(call(HttpsCaTrustMode.CUSTOM_CA, this.authority.pem(), this.signed.url("localhost")));
		Assertions.assertNull(call(HttpsCaTrustMode.CUSTOM_CA, this.authority.pem(), this.signed.url("127.0.0.1")));
	}

	@Test
	@DisplayName("Custom CA: a bundle holding the CA among others is accepted")
	void customCaBundle() throws Exception {
		String bundle = TestTls.pem(this.otherAuthority.certificate(), this.authority.certificate());
		Assertions.assertNull(call(HttpsCaTrustMode.CUSTOM_CA, bundle, this.signed.url("localhost")));
	}

	@Test
	@DisplayName("Custom CA: a certificate of another CA, or a self-signed one, is refused")
	void customCaOtherAuthority() throws Exception {
		assertRefused(call(HttpsCaTrustMode.CUSTOM_CA, this.otherAuthority.pem(), this.signed.url("localhost")), "Other CA");
		assertRefused(call(HttpsCaTrustMode.CUSTOM_CA, this.authority.pem(), this.selfSigned.url("localhost")), "Self-signed");
	}

	@Test
	@DisplayName("Custom CA: a certificate for another host name is refused")
	void customCaHostMismatch() throws Exception {
		Throwable failure = call(HttpsCaTrustMode.CUSTOM_CA, this.authority.pem(), this.otherHost.url("localhost"));
		assertRefused(failure, "Host name mismatch");
		Assertions.assertFalse(failure instanceof SSLHandshakeException && failure.getMessage().contains("PKIX"),
			"Refused for the CA rather than the host name: " + failure);
	}

	@Test
	@DisplayName("Custom CA: none, an invalid one or a PEM without certificate can't build a policy")
	void invalidCustomCa() {
		for (String pem : new String[] { null, "  ", "not a certificate",
			"-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n" }) {
			Assertions.assertThrows(CertificateException.class, () -> HttpsTrustPolicy.buildSslContext(HttpsCaTrustMode.CUSTOM_CA, pem),
				String.valueOf(pem));
		}
	}

	@Test
	@DisplayName("The validation of a custom CA accepts a certificate or a bundle, and refuses anything else")
	void validateCustomCa() {
		CaCertificates.validateCustomCa(this.authority.pem(), "test");
		CaCertificates.validateCustomCa(TestTls.pem(this.authority.certificate(), this.otherAuthority.certificate()), "test");
		for (String pem : new String[] { "", "not a certificate", "-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n" }) {
			NetshotException e = Assertions.assertThrows(NetshotException.class, () -> CaCertificates.validateCustomCa(pem, "the hook"));
			Assertions.assertEquals(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, e.getErrorCode());
			Assertions.assertTrue(e.getMessage().startsWith("Invalid custom CA certificate for the hook: "), e.getMessage());
		}
	}
}
