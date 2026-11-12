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
package net.netshot.netshot.testing;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/**
 * TLS material made when the tests run (with BouncyCastle): certificate authorities, server certificates
 * signed by them or self-signed, and an HTTPS server on the loopback interface presenting one of them.
 */
public final class TestTls {

	private static final AtomicLong SERIAL = new AtomicLong(System.currentTimeMillis());

	private TestTls() {
		// Static helpers only
	}

	/**
	 * A certificate with its private key.
	 *
	 * @param keyPair the key pair
	 * @param certificate the certificate
	 */
	public record Identity(KeyPair keyPair, X509Certificate certificate) {

		/**
		 * Gets the certificate in PEM format, as a custom CA is configured.
		 *
		 * @return the PEM text
		 */
		public String pem() {
			return TestTls.pem(this.certificate);
		}
	}

	/**
	 * Builds a certificate authority (self-signed, allowed to sign certificates).
	 *
	 * @param name the common name of the authority
	 * @return the authority
	 */
	public static Identity authority(String name) {
		KeyPair keyPair = newKeyPair();
		return new Identity(keyPair, sign(new X500Name("CN=" + name), keyPair, keyPair, true, List.of()));
	}

	/**
	 * Builds a server certificate signed by an authority.
	 *
	 * @param issuer the authority
	 * @param hosts the host names and IP addresses of the server (subject alternative names)
	 * @return the server identity
	 */
	public static Identity server(Identity issuer, String... hosts) {
		KeyPair keyPair = newKeyPair();
		X500Name issuerName = new X500Name(issuer.certificate().getSubjectX500Principal().getName());
		X509Certificate certificate = sign(issuerName, issuer.keyPair(), keyPair, false, List.of(hosts));
		return new Identity(keyPair, certificate);
	}

	/**
	 * Builds a self-signed server certificate.
	 *
	 * @param hosts the host names and IP addresses of the server (subject alternative names)
	 * @return the server identity
	 */
	public static Identity selfSigned(String... hosts) {
		KeyPair keyPair = newKeyPair();
		return new Identity(keyPair, sign(new X500Name("CN=" + hosts[0]), keyPair, keyPair, false, List.of(hosts)));
	}

	/**
	 * Writes certificates in PEM format (a bundle when there are several).
	 *
	 * @param certificates the certificates
	 * @return the PEM text
	 */
	public static String pem(X509Certificate... certificates) {
		StringWriter text = new StringWriter();
		try (JcaPEMWriter writer = new JcaPEMWriter(text)) {
			for (X509Certificate certificate : certificates) {
				writer.writeObject(certificate);
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return text.toString();
	}

	private static KeyPair newKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
			generator.initialize(new ECGenParameterSpec("secp256r1"));
			return generator.generateKeyPair();
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private static X509Certificate sign(X500Name issuer, KeyPair issuerKeys, KeyPair subjectKeys, boolean authority, List<String> hosts) {
		try {
			X500Name subject = authority ? issuer : new X500Name("CN=" + hosts.get(0));
			Instant now = Instant.now();
			X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(SERIAL.incrementAndGet()),
				Date.from(now.minus(Duration.ofHours(1))), Date.from(now.plus(Duration.ofDays(1))), subject, subjectKeys.getPublic());
			builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(authority));
			if (authority) {
				builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
			}
			else {
				List<GeneralName> names = new ArrayList<>();
				for (String host : hosts) {
					names.add(new GeneralName(host.matches("[0-9.]+") ? GeneralName.iPAddress : GeneralName.dNSName, host));
				}
				builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names.toArray(GeneralName[]::new)));
			}
			return new JcaX509CertificateConverter().getCertificate(
				builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKeys.getPrivate())));
		}
		catch (IOException | GeneralSecurityException | OperatorCreationException e) {
			throw new IllegalStateException("Unable to build a test certificate", e);
		}
	}

	/**
	 * An HTTPS server on the loopback interface, answering 200 with a fixed JSON body to any request.
	 */
	public static final class Server implements AutoCloseable {

		private final HttpsServer httpsServer;

		/**
		 * Starts the server.
		 *
		 * @param identity the certificate (and key) the server presents
		 * @param body the JSON body of the answers
		 */
		public Server(Identity identity, String body) {
			try {
				KeyStore keyStore = KeyStore.getInstance("PKCS12");
				keyStore.load(null, null);
				char[] password = "test".toCharArray();
				keyStore.setKeyEntry("server", identity.keyPair().getPrivate(), password, new X509Certificate[] { identity.certificate() });
				KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				keyManagers.init(keyStore, password);
				SSLContext sslContext = SSLContext.getInstance("TLS");
				sslContext.init(keyManagers.getKeyManagers(), null, null);
				this.httpsServer = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
				this.httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
				byte[] answer = body.getBytes(StandardCharsets.UTF_8);
				this.httpsServer.createContext("/", exchange -> {
					exchange.getRequestBody().readAllBytes();
					exchange.getResponseHeaders().add("Content-Type", "application/json");
					exchange.sendResponseHeaders(200, answer.length);
					try (OutputStream out = exchange.getResponseBody()) {
						out.write(answer);
					}
				});
				this.httpsServer.start();
			}
			catch (IOException | GeneralSecurityException e) {
				throw new IllegalStateException("Unable to start the test HTTPS server", e);
			}
		}

		/**
		 * Gets the port of the server.
		 *
		 * @return the port
		 */
		public int port() {
			return this.httpsServer.getAddress().getPort();
		}

		/**
		 * Gets the URL of the server, with a given host name (which must resolve to the loopback interface).
		 *
		 * @param host the host name or address, such as localhost or 127.0.0.1
		 * @return the URL, such as https://localhost:12345/
		 */
		public URI url(String host) {
			return URI.create("https://%s:%d/".formatted(host, this.port()));
		}

		@Override
		public void close() {
			this.httpsServer.stop(0);
		}
	}
}
