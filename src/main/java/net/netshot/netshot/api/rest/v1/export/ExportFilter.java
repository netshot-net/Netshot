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

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Completes the negotiation of {@link ExportNegotiation}: once the resource method is matched, answers 406 if
 * it isn't a list (an export is only a way to write a list); once the list is built, sets the media type of
 * the response, the file name ({@code Content-Disposition}) and the total number of items
 * ({@code X-Total-Count}), and refuses an export which is bigger than the limit of
 * {@link ListParams#exportMaxRows()}.
 */
public class ExportFilter implements ContainerRequestFilter, ContainerResponseFilter {

	/** The header telling how many items match the list (when counted: count=true). */
	static final String TOTAL_COUNT = "X-Total-Count";

	@Context
	private HttpServletRequest httpRequest;

	@Context
	private ResourceInfo resourceInfo;

	@Override
	public void filter(ContainerRequestContext requestContext) {
		if (ListExport.format(this.httpRequest) == null) {
			return;
		}
		if (this.resourceInfo.getResourceMethod() == null || this.resourceInfo.getResourceMethod().getReturnType() != ListResponse.class) {
			throw new NotAcceptableException("Only the lists can be exported as CSV or Excel.");
		}
	}

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
		ExportFormat format = ListExport.format(this.httpRequest);
		if (format == null || responseContext.getStatus() != Response.Status.OK.getStatusCode()
			|| !(responseContext.getEntity() instanceof ListResponse<?> list)) {
			return;
		}
		if (ListExport.body(this.httpRequest) == null && list.results().size() > ListParams.exportMaxRows()) {
			throw NetshotException.invalidParameter(ListParams.LIMIT,
				"The export can't have more than %d rows: filter the list or use limit and offset".formatted(ListParams.exportMaxRows()));
		}
		responseContext.setEntity(list, responseContext.getEntityAnnotations(), mediaType(format));
		responseContext.getHeaders().putSingle(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s-%s.%s\"".formatted(
			resourceName(requestContext.getUriInfo().getPath()), new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()), format.extension()));
		if (list.count() != null) {
			responseContext.getHeaders().putSingle(TOTAL_COUNT, list.count());
		}
	}

	private static MediaType mediaType(ExportFormat format) {
		MediaType mediaType = MediaType.valueOf(format.mediaType());
		return format == ExportFormat.CSV ? mediaType.withCharset(StandardCharsets.UTF_8.name()) : mediaType;
	}

	/**
	 * Gets the name of the exported resource: the last element of the path of the list.
	 *
	 * @param path the path of the request (under the API root)
	 * @return the name, safe in a file name
	 */
	static String resourceName(String path) {
		String name = "export";
		for (String segment : path.split("/")) {
			if (!segment.isEmpty()) {
				name = segment;
			}
		}
		String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
		return safe.isEmpty() ? "export" : safe;
	}
}
