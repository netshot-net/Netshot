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
package net.netshot.netshot.platform.http;

import java.net.URI;
import java.net.URISyntaxException;

import net.netshot.netshot.Netshot;

/**
 * Address of the embedded HTTP server, as configured by the netshot.http.* settings.
 * Readable from the configuration alone, so before the server is started.
 *
 * @param baseUri the base URI the server listens on
 * @param useSsl whether the server uses HTTPS
 */
public record HttpServerAddress(URI baseUri, boolean useSsl) {

	/**
	 * Read the HTTP server address from the configuration.
	 * @return the configured HTTP server address
	 */
	public static HttpServerAddress fromConfig() {
		boolean useSsl = Netshot.getConfig("netshot.http.ssl.enabled", true);
		String baseUrl = Netshot.getConfig("netshot.http.baseurl", useSsl ? "http://localhost:8443" : "http://localhost:8080");
		int basePort = Netshot.getConfig("netshot.http.baseport", 0, 0, 65535);
		return of(baseUrl, basePort, useSsl);
	}

	/**
	 * Build the HTTP server address.
	 * @param baseUrl the base URL
	 * @param basePort the TCP port overriding the one of the base URL, 0 to keep it
	 * @param useSsl whether the server uses HTTPS
	 * @return the HTTP server address
	 */
	public static HttpServerAddress of(String baseUrl, int basePort, boolean useSsl) {
		URI uri = URI.create(baseUrl);
		if (basePort != 0) {
			try {
				uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), basePort,
					uri.getPath(), uri.getQuery(), uri.getFragment());
			}
			catch (URISyntaxException e) {
				throw new IllegalArgumentException("Invalid HTTP base URL " + baseUrl, e);
			}
		}
		return new HttpServerAddress(uri, useSsl);
	}

	/**
	 * Get the TCP port of the server, defaulting to the standard port of the protocol.
	 * @return the TCP port
	 */
	public int port() {
		int port = this.baseUri.getPort();
		if (port != -1 && port != 0) {
			return port;
		}
		return this.useSsl ? 443 : 80;
	}
}
