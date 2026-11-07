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
package net.netshot.netshot.api.rest.v1.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import jakarta.servlet.http.HttpServletRequest;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;

/**
 * Export of the lists as CSV or Excel: what a client asked for and what a resource can answer.
 *
 * <p>A client asks for an export with the {@code Accept} header of a list request ({@code text/csv} or the
 * Excel media type). {@link ExportNegotiation} notes the format and lets the request go on as a JSON one, so
 * the resources and their query parameters are the same for every format: the list is built as usual, then
 * written as a table by {@link CsvListWriter} or {@link XlsxListWriter}. A resource only has to use this class
 * when it can answer an export better than the generic table, as the device list does with a workbook of
 * several sheets.</p>
 */
public final class ListExport {

	/** The request attribute holding the {@link ExportFormat} asked for. */
	static final String FORMAT = ListExport.class.getName() + ".format";

	/** The request attribute holding the {@link Body} replacing the generic table. */
	static final String BODY = ListExport.class.getName() + ".body";

	/**
	 * The content of an export, when a resource writes it itself instead of the generic table of the list.
	 */
	@FunctionalInterface
	public interface Body {

		/**
		 * Writes the export.
		 *
		 * @param output the stream to write to
		 * @throws IOException if the stream can't be written
		 */
		void writeTo(OutputStream output) throws IOException;
	}

	private ListExport() {
	}

	/**
	 * Gets the export format the client asked for.
	 *
	 * @param request the current request
	 * @return the format, or null if the client didn't ask for an export
	 */
	public static ExportFormat format(HttpServletRequest request) {
		return request.getAttribute(FORMAT) instanceof ExportFormat format ? format : null;
	}

	/**
	 * Replaces the generic table of the list with an export written by the resource (only the Excel format
	 * has such a replacement for now). The body is called when the response is written, and the entity returned
	 * by the resource is then only used for its headers (the table is not written).
	 *
	 * @param request the current request
	 * @param body the export
	 */
	public static void write(HttpServletRequest request, Body body) {
		request.setAttribute(BODY, body);
	}

	/**
	 * Makes the table of a list: the items are written as JSON, the selection of the request (view, fields, omit,
	 * expand) is applied, and the result is flattened.
	 *
	 * @param request the current request
	 * @param list the list
	 * @return the table
	 */
	static ExportTable table(HttpServletRequest request, ListResponse<?> list) {
		Selection selection = V1Response.selectionOf(request, list);
		if (selection == null) {
			return ExportTable.of(V1Mappers.json().valueToTree(list.results()));
		}
		// The tree of the selected properties only
		JsonMapper mapper = V1Mappers.json();
		try (TokenBuffer buffer = new TokenBuffer(mapper, false)) {
			selection.write(buffer, mapper.getSerializerProviderInstance(), list.results());
			return ExportTable.of(mapper.readTree(buffer.asParser()));
		}
		catch (IOException e) {
			throw new UncheckedIOException("Unable to write the exported items", e);
		}
	}

	/**
	 * Gets the export written by the resource.
	 *
	 * @param request the current request
	 * @return the body, or null if the generic table is to be written
	 */
	static Body body(HttpServletRequest request) {
		return request.getAttribute(BODY) instanceof Body body ? body : null;
	}
}
