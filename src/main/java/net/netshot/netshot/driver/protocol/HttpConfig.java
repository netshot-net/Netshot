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
package net.netshot.netshot.driver.protocol;

import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.driver.DeviceDriver.DriverProtocol;

/**
 * HTTP-specific configuration of a driver access.
 */
public final class HttpConfig {
	/** Default TCP port for this access, when not overridden on the device. */
	@Getter
	@Setter
	private int defaultPort = DriverProtocol.HTTPS.defaultPort();

	/** Whether this access uses TLS ("https") or plain HTTP. */
	@Getter
	@Setter
	private boolean tls = true;

	/** Optional base path prepended to every request path. */
	@Getter
	@Setter
	private String basePath;

	/** Declared authentication scheme for this access (may be null = no auth). */
	@Getter
	@Setter
	private AuthScheme auth;

	public HttpConfig() {
	}
}
