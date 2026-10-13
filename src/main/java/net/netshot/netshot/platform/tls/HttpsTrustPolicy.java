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
package net.netshot.netshot.platform.tls;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Collection;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * Builds a {@link SSLContext} (and, where relevant, a {@link HostnameVerifier})
 * for an outgoing HTTPS connection, driven by a {@link HttpsCaTrustMode}
 * (trust-any/system truststore/custom CA). Shared by every part of Netshot
 * that opens a HTTPS connection with a configurable trust policy (device
 * HTTPS access, Web hooks, ...).
 * <p>
 * {@code TRUST_ANY} disables certificate verification in every respect,
 * hostname included - there is no way to accept any certificate while still
 * checking that its CN/SAN matches the target host, since that check alone
 * offers no real protection against an active attacker (who can simply
 * present a certificate with a matching CN/SAN). {@code SYSTEM_TRUSTSTORE}
 * and {@code CUSTOM_CA} always verify the hostname.
 */
public final class HttpsTrustPolicy {

	private HttpsTrustPolicy() {
	}

	/**
	 * Builds the trust managers reflecting the given CA trust mode.
	 * @param caTrustMode the CA trust mode (defaults to {@code SYSTEM_TRUSTSTORE} if null)
	 * @param customCaCertificate the PEM-encoded trust anchor certificate(s), used when
	 *        {@code caTrustMode} is {@code CUSTOM_CA}
	 * @return the trust managers to pass to {@link SSLContext#init}, or null to fall back to the
	 *         JVM's default (system truststore) trust managers
	 * @throws GeneralSecurityException if the custom CA certificate(s) cannot be loaded
	 * @throws IOException if the in-memory trust {@link KeyStore} cannot be initialized
	 */
	public static TrustManager[] buildTrustManagers(HttpsCaTrustMode caTrustMode, String customCaCertificate)
			throws GeneralSecurityException, IOException {
		HttpsCaTrustMode mode = caTrustMode == null ? HttpsCaTrustMode.SYSTEM_TRUSTSTORE : caTrustMode;
		switch (mode) {
			case TRUST_ANY:
				return new TrustManager[] { new InsecureTrustManager() };
			case CUSTOM_CA:
				if (customCaCertificate == null || customCaCertificate.isBlank()) {
					throw new CertificateException(
						"No custom CA certificate configured, despite CUSTOM_CA trust mode.");
				}
				KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
				trustStore.load(null, null);
				CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
				Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(
					new ByteArrayInputStream(customCaCertificate.getBytes(StandardCharsets.UTF_8)));
				if (certificates.isEmpty()) {
					throw new CertificateException("No certificate found in the configured custom CA.");
				}
				int i = 0;
				for (Certificate certificate : certificates) {
					trustStore.setCertificateEntry("ca" + (i++), certificate);
				}
				TrustManagerFactory trustManagerFactory =
					TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				trustManagerFactory.init(trustStore);
				return trustManagerFactory.getTrustManagers();
			case SYSTEM_TRUSTSTORE:
			default:
				// null trust managers => SSLContext.init falls back to the JVM's default (system) ones
				return null;
		}
	}

	/**
	 * Builds a TLS {@link SSLContext} reflecting the given CA trust mode.
	 * @param caTrustMode the CA trust mode (defaults to {@code SYSTEM_TRUSTSTORE} if null)
	 * @param customCaCertificate the PEM-encoded trust anchor certificate(s), used when
	 *        {@code caTrustMode} is {@code CUSTOM_CA}
	 * @return the SSL context
	 * @throws GeneralSecurityException if the custom CA certificate(s) cannot be loaded, or the
	 *         context cannot be initialized
	 * @throws IOException if the in-memory trust {@link KeyStore} cannot be initialized
	 */
	public static SSLContext buildSslContext(HttpsCaTrustMode caTrustMode, String customCaCertificate)
			throws GeneralSecurityException, IOException {
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, buildTrustManagers(caTrustMode, customCaCertificate), new SecureRandom());
		return sslContext;
	}

	/**
	 * Builds the hostname verifier reflecting the given CA trust mode.
	 * @param caTrustMode the CA trust mode (defaults to {@code SYSTEM_TRUSTSTORE} if null)
	 * @return an {@link InsecureHostnameVerifier} (accepts any hostname) when {@code caTrustMode}
	 *         is {@code TRUST_ANY}, or null otherwise to fall back to the JAX-RS client's default
	 *         (real) hostname verification
	 */
	public static HostnameVerifier buildHostnameVerifier(HttpsCaTrustMode caTrustMode) {
		HttpsCaTrustMode mode = caTrustMode == null ? HttpsCaTrustMode.SYSTEM_TRUSTSTORE : caTrustMode;
		return mode == HttpsCaTrustMode.TRUST_ANY ? new InsecureHostnameVerifier() : null;
	}
}
