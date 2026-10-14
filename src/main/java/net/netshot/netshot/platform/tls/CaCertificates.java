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
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Collection;

import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Helpers for the custom CA certificates users can configure to trust a HTTPS server
 * (device accesses, Web hooks).
 */
public final class CaCertificates {

	private CaCertificates() {
		// Static helpers only
	}

	/**
	 * Validates that a posted custom CA block parses as at least one X.509 certificate.
	 * @param pem the PEM-encoded certificate(s)
	 * @param context what the certificate belongs to, e.g. "access 'https'" or "the webhook" (for the error message)
	 * @throws NetshotException if the block is malformed or empty
	 */
	public static void validateCustomCa(String pem, String context) {
		try {
			CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
			Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(
				new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
			if (certificates.isEmpty()) {
				throw new CertificateException("No certificate found.");
			}
		}
		catch (Exception e) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
				"Invalid custom CA certificate for " + context + ": " + e.getMessage());
		}
	}
}
