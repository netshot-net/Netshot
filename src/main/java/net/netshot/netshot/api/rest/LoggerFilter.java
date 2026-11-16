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

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.augur.tacacs.TacacsException;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.aaa.auth.Tacacs;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Config;

/**
 * Filter to log requests.
 */
@Slf4j
public class LoggerFilter implements ContainerResponseFilter {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/** Whether the client address is read from the X-Forwarded-For header (behind a reverse proxy). */
	static final Setting<Boolean> TRUST_X_FORWARDED_FOR = Setting.bool("netshot.http.trustxforwardedfor").defaultValue(false).live();

	@Context
	private HttpServletRequest httpRequest;

	/** The TACACS+ authentication (accounting of the requests). */
	private final Tacacs tacacs;
	private final ConfigStore config;

	/**
	 * Creates the filter.
	 *
	 * @param tacacs the TACACS+ authentication (accounting of the requests)
	 * @param config the configuration store
	 */
	@Inject
	public LoggerFilter(Tacacs tacacs, ConfigStore config) {
		this.tacacs = tacacs;
		this.config = config;
	}

	/**
	 * Guess the client IP address based on X-Forwarded-For header (if present).
	 * @param request = the http request
	 * @param config the configuration (whether the X-Forwarded-For header is trusted)
	 * @return the probable client IP address
	 */
	public static String getClientAddress(HttpServletRequest request, Config config) {
		String address = null;
		if (config.get(TRUST_X_FORWARDED_FOR)) {
			String forwardedFor = request.getHeader("X-Forwarded-For");
			if (forwardedFor != null) {
				String[] addresses = forwardedFor.split(",");
				address = addresses[0].trim();
			}
		}
		if (address == null) {
			address = request.getRemoteAddr();
		}
		return address;
	}

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
		throws IOException {
		User user = null;
		try {
			user = (User) requestContext.getSecurityContext().getUserPrincipal();
		}
		catch (Exception e) {
			//
		}
		String method = requestContext.getMethod().toUpperCase();
		String remoteAddr = LoggerFilter.getClientAddress(this.httpRequest, this.config.current());
		if ("GET".equals(method)) {
			AAA_LOG.debug("Request from {} ({}) - {} - \"{} {}\" - {}.", remoteAddr,
				requestContext.getHeaderString(HttpHeaders.USER_AGENT), user == null ? "<none>" : user.username(),
				requestContext.getMethod(), requestContext.getUriInfo().getRequestUri(), responseContext.getStatus());
		}
		else {
			AAA_LOG.info("Request from {} ({}) - {} - \"{} {}\" - {}.", remoteAddr,
				requestContext.getHeaderString(HttpHeaders.USER_AGENT), user == null ? "<none>" : user.username(),
				requestContext.getMethod(), requestContext.getUriInfo().getRequestUri(), responseContext.getStatus());
			try {
				this.tacacs.account(
					requestContext.getMethod(),
					requestContext.getUriInfo().getRequestUri().getPath(),
					user == null ? "<none>" : user.username(),
					Integer.toString(responseContext.getStatus()),
					remoteAddr);
			}
			catch (TacacsException e) {
				log.warn("Unable to send accounting message to TACACS+ server", e);
			}
		}
	}
}
