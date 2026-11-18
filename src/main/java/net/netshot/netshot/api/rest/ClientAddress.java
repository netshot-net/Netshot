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
package net.netshot.netshot.api.rest;

import jakarta.servlet.http.HttpServletRequest;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;

/**
 * Gives the address of the client of a request: the address the request comes from, or the first address of the
 * X-Forwarded-For header behind a trusted reverse proxy ({@code netshot.http.trustxforwardedfor}). Injected in the
 * REST layer (logs, authentication, audit of the logins); built by the composition root.
 */
public final class ClientAddress {

	/** Whether the client address is read from the X-Forwarded-For header (behind a reverse proxy). */
	static final Setting<Boolean> TRUST_X_FORWARDED_FOR = Setting.bool("netshot.http.trustxforwardedfor").defaultValue(false).live();

	/** Whether the X-Forwarded-For header is trusted, from the configuration. */
	private final ConfigStore.Binding<Boolean> trustForwardedFor;

	/**
	 * Creates the client address resolver.
	 *
	 * @param config the configuration store (whether the X-Forwarded-For header is trusted)
	 */
	public ClientAddress(ConfigStore config) {
		this.trustForwardedFor = config.bind(current -> current.get(TRUST_X_FORWARDED_FOR));
	}

	/**
	 * Whether the X-Forwarded-For header is trusted, i.e. Netshot is behind a reverse proxy.
	 *
	 * @return true behind a trusted reverse proxy
	 */
	public boolean trustsForwardedFor() {
		return this.trustForwardedFor.get();
	}

	/**
	 * Gets the probable address of the client of a request.
	 *
	 * @param request the HTTP request
	 * @return the client address
	 */
	public String of(HttpServletRequest request) {
		if (this.trustForwardedFor.get()) {
			String forwardedFor = request.getHeader("X-Forwarded-For");
			if (forwardedFor != null) {
				return forwardedFor.split(",")[0].trim();
			}
		}
		return request.getRemoteAddr();
	}
}
