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
package net.netshot.netshot.api.rest.v1;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * What a v1 resource tells about its response beside the entity it returns (so that the resource methods keep
 * a typed return value, which is what the OpenAPI document describes): the status (201, 202, 204), the
 * {@code Location} of a created object, the {@code ETag} (the version of the object), and the {@link Selection}
 * to apply. They are attached to the request, and applied by this class as a response filter, which also
 * answers {@code 304 Not Modified} to a {@code GET} whose {@code If-None-Match} matches the ETag.
 *
 * <p>The ETag is the version of the object ({@code "3"}): it changes with every change of the object, whatever
 * the format and the selection of the representation. {@code If-Match} on a {@code PUT}, {@code PATCH} or
 * {@code DELETE} is read by {@link #ifMatch(HttpHeaders)} and checked by the service.</p>
 */
public class V1Response implements ContainerResponseFilter {

	private static final String STATUS = V1Response.class.getName() + ".status";
	private static final String LOCATION = V1Response.class.getName() + ".location";
	private static final String ETAG = V1Response.class.getName() + ".etag";
	private static final String NO_STORE = V1Response.class.getName() + ".noStore";

	@Context
	private HttpServletRequest httpRequest;

	/**
	 * Sets the status of the response.
	 *
	 * @param request the current request
	 * @param status the status
	 */
	public static void status(HttpServletRequest request, Response.Status status) {
		request.setAttribute(STATUS, status.getStatusCode());
	}

	/**
	 * Answers {@code 201 Created} with the location of the new object.
	 *
	 * @param request the current request
	 * @param location the path of the new object (from {@link V1Links})
	 */
	public static void created(HttpServletRequest request, String location) {
		status(request, Response.Status.CREATED);
		request.setAttribute(LOCATION, location);
	}

	/**
	 * Answers {@code 202 Accepted} with the location of the task doing the work.
	 *
	 * @param request the current request
	 * @param location the path of the task
	 */
	public static void accepted(HttpServletRequest request, String location) {
		status(request, Response.Status.ACCEPTED);
		request.setAttribute(LOCATION, location);
	}

	/**
	 * Sets the ETag of the response, from the version of the object.
	 *
	 * @param request the current request
	 * @param version the version of the object
	 */
	public static void etag(HttpServletRequest request, long version) {
		request.setAttribute(ETAG, Long.toString(version));
	}

	/**
	 * Forbids any cache to store the response ({@code Cache-Control: no-store}), for the answers holding a secret
	 * (the value of a new API token) or opening a session.
	 *
	 * @param request the current request
	 */
	public static void noStore(HttpServletRequest request) {
		request.setAttribute(NO_STORE, Boolean.TRUE);
	}

	/**
	 * Attaches the selection to apply to the response.
	 *
	 * @param request the current request
	 * @param selection the selection
	 * @param type the class of the representation the selection applies to (of the items, for a list)
	 */
	public static void select(HttpServletRequest request, Selection selection, Class<?> type) {
		request.setAttribute(Selection.ATTRIBUTE, new Attached(selection, type));
	}

	/**
	 * A selection with the class it applies to.
	 *
	 * @param selection the selection
	 * @param type the class of the representation (of the items, for a list)
	 */
	private record Attached(Selection selection, Class<?> type) {
	}

	/**
	 * Gets the selection to apply to a response entity.
	 *
	 * @param request the current request
	 * @param entity the response entity
	 * @return the selection, or null if none applies to this entity (another type, an error...)
	 */
	public static Selection selectionOf(HttpServletRequest request, Object entity) {
		if (request == null || !(request.getAttribute(Selection.ATTRIBUTE) instanceof Attached attached)) {
			return null;
		}
		if (entity instanceof ListResponse<?> list) {
			List<?> results = list.results();
			return results.isEmpty() || attached.type().isInstance(results.get(0)) ? attached.selection() : null;
		}
		return attached.type().isInstance(entity) ? attached.selection() : null;
	}

	/**
	 * Reads the version a {@code PUT}, {@code PATCH} or {@code DELETE} is conditioned on.
	 *
	 * @param headers the request headers
	 * @return the version given by {@code If-Match}, or null if there is none (or {@code *})
	 * @throws NetshotException if the header is invalid
	 */
	public static Long ifMatch(HttpHeaders headers) {
		List<String> values = headers.getRequestHeader(HttpHeaders.IF_MATCH);
		if (values == null || values.isEmpty()) {
			return null;
		}
		String value = String.join(",", values).trim();
		if ("*".equals(value)) {
			return null;
		}
		if (value.matches("\"\\d{1,18}\"")) {
			return Long.parseLong(value.substring(1, value.length() - 1));
		}
		throw NetshotException.invalidParameter(HttpHeaders.IF_MATCH,
			"Invalid If-Match header: a single strong ETag (such as \"3\") is expected");
	}

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
		if (Boolean.TRUE.equals(this.httpRequest.getAttribute(NO_STORE))) {
			responseContext.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, "no-store");
		}
		if (responseContext.getStatus() >= 300) {
			return;
		}
		if (this.httpRequest.getAttribute(STATUS) instanceof Integer status) {
			responseContext.setStatus(status);
		}
		if (this.httpRequest.getAttribute(LOCATION) instanceof String location) {
			responseContext.getHeaders().putSingle(HttpHeaders.LOCATION, location);
		}
		if (this.httpRequest.getAttribute(ETAG) instanceof String etag) {
			EntityTag tag = new EntityTag(etag);
			responseContext.getHeaders().putSingle(HttpHeaders.ETAG, tag);
			String ifNoneMatch = requestContext.getHeaderString(HttpHeaders.IF_NONE_MATCH);
			if ("GET".equals(requestContext.getMethod()) && ifNoneMatch != null
				&& (ifNoneMatch.trim().equals("*") || List.of(ifNoneMatch.split(",")).stream()
					.map(String::trim).map(t -> t.replaceFirst("^W/", "")).anyMatch(t -> t.equals("\"" + etag + "\"")))) {
				responseContext.setStatus(Response.Status.NOT_MODIFIED.getStatusCode());
				responseContext.setEntity(null);
			}
		}
	}
}
