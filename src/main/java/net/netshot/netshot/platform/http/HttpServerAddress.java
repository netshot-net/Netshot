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
package net.netshot.netshot.platform.http;

import java.net.URI;
import java.net.URISyntaxException;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.Setting;

/**
 * Address of the embedded HTTP server, as configured by the netshot.http.* settings.
 * Readable from the configuration alone, so before the server is started.
 *
 * @param baseUri the base URI the server listens on
 * @param useSsl whether the server uses HTTPS
 */
public record HttpServerAddress(URI baseUri, boolean useSsl) {

	/** Whether the HTTP server uses HTTPS. */
	public static final Setting<Boolean> SSL_ENABLED = Setting.bool("netshot.http.ssl.enabled").defaultValue(true).restart();

	/** The base URL the server listens on (default: on localhost, port 8443 with HTTPS or 8080 without). */
	static final Setting<String> BASE_URL = Setting.text("netshot.http.baseurl").restart();

	/** The TCP port overriding the one of the base URL (0 to keep it). */
	static final Setting<Integer> BASE_PORT = Setting.integer("netshot.http.baseport").defaultValue(0).range(0, 65535).restart();

	/**
	 * Read the HTTP server address from the configuration.
	 * @return the configured HTTP server address
	 */
	public static HttpServerAddress fromConfig() {
		Config config = Netshot.config().current();
		boolean useSsl = config.get(SSL_ENABLED);
		String baseUrl = config.get(BASE_URL);
		if (baseUrl == null) {
			baseUrl = useSsl ? "http://localhost:8443" : "http://localhost:8080";
		}
		return of(baseUrl, config.get(BASE_PORT), useSsl);
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
