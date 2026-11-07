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
package net.netshot.netshot.api.rest.v0;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * Filter to set the custom status code.
 */
public class ResponseCodeFilter implements ContainerResponseFilter {
	/** Request property name to embed the suggested HTTP response code. */
	public static final String SUGGESTED_RESPONSE_CODE = "net.netshot.netshot.rest.SuggestedResponseCode";

	@Context
	private HttpServletRequest httpRequest;

	/**
	 * Attaches a suggested return code to the request, so that this filter can in turn change
	 * the status of the response, e.g. 201 or 204 instead of the default 200.
	 * @param request the current request
	 * @param status the status to answer with
	 */
	public static void suggest(HttpServletRequest request, Response.Status status) {
		request.setAttribute(SUGGESTED_RESPONSE_CODE, status);
	}

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
		Response.Status status = (Response.Status) httpRequest.getAttribute(ResponseCodeFilter.SUGGESTED_RESPONSE_CODE);
		if (status != null) {
			responseContext.setStatus(status.getStatusCode());
		}
	}
}
