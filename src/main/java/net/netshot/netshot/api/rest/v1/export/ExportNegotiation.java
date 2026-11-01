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
package net.netshot.netshot.api.rest.v1.export;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import net.netshot.netshot.api.rest.v1.ListParams;

/**
 * Lets a list be exported without declaring the export media types on every list method: before the resource
 * method is matched, a {@code GET} whose {@code Accept} header prefers {@code text/csv} or the Excel media type
 * is turned into a JSON request (the format is kept as a request attribute, and as the internal
 * {@link ListParams#EXPORT} query parameter, which {@link ListParams} reads to lift the page limit).
 * {@link ExportFilter} then checks that the resource method is a list and sets the media type of the response.
 *
 * <p>Only the exact export media types select an export: {@code *}{@code /*} or {@code text/*} never do, and a
 * client which prefers JSON (or lists it first) gets JSON. A client-supplied {@link ListParams#EXPORT}
 * parameter is removed from every request.</p>
 */
@PreMatching
@Priority(Priorities.AUTHENTICATION - 1)
public class ExportNegotiation implements ContainerRequestFilter {

	@Context
	private HttpServletRequest httpRequest;

	@Override
	public void filter(ContainerRequestContext requestContext) {
		ExportFormat format = "GET".equals(requestContext.getMethod()) ? preferredFormat(requestContext) : null;
		URI uri = requestContext.getUriInfo().getRequestUri();
		String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
		String cleaned = withoutMarker(query);
		if (format == null && cleaned.equals(query)) {
			return;
		}
		List<String> parts = new ArrayList<>();
		if (!cleaned.isEmpty()) {
			parts.addAll(List.of(cleaned.split("&")));
		}
		if (format != null) {
			parts.add(ListParams.EXPORT + "=" + format.extension());
			requestContext.getHeaders().putSingle(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
			this.httpRequest.setAttribute(ListExport.FORMAT, format);
		}
		String text = uri.toString();
		int index = text.indexOf('?');
		String base = index < 0 ? text : text.substring(0, index);
		requestContext.setRequestUri(requestContext.getUriInfo().getBaseUri(),
			URI.create(parts.isEmpty() ? base : base + "?" + String.join("&", parts)));
	}

	/**
	 * Finds the format the client prefers, if it is an export one.
	 *
	 * @param requestContext the request
	 * @return the format of the first acceptable media type, or null if it isn't an export format
	 */
	private static ExportFormat preferredFormat(ContainerRequestContext requestContext) {
		for (MediaType accepted : requestContext.getAcceptableMediaTypes()) {
			String quality = accepted.getParameters().get("q");
			if (quality != null && quality.matches("0(\\.0*)?")) {
				continue;
			}
			return ExportFormat.of(accepted);
		}
		return null;
	}

	/**
	 * Removes the internal export parameter (and the empty parts) from a raw query.
	 *
	 * @param query the raw query
	 * @return the query without the parameter
	 */
	static String withoutMarker(String query) {
		List<String> kept = new ArrayList<>();
		for (String part : query.split("&")) {
			String name = part.contains("=") ? part.substring(0, part.indexOf('=')) : part;
			if (!part.isEmpty() && !name.equals(ListParams.EXPORT)) {
				kept.add(part);
			}
		}
		return String.join("&", kept);
	}
}
