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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import net.netshot.netshot.api.rest.v1.ListResponse;

/**
 * Writes a list as CSV (RFC 4180): UTF-8 (without byte order mark), CRLF line ends, a header row, then a row per
 * item (see {@link ExportTable} for the columns). Dates are the ISO-8601 texts of the API.
 */
@Produces(ExportFormat.TEXT_CSV)
public class CsvListWriter implements MessageBodyWriter<ListResponse<?>> {

	@Context
	private HttpServletRequest request;

	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return ListResponse.class.isAssignableFrom(type) && ExportFormat.of(mediaType) == ExportFormat.CSV;
	}

	@Override
	public void writeTo(ListResponse<?> list, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
		MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException {
		ExportTable table = ListExport.table(this.request, list);
		Writer writer = new OutputStreamWriter(entityStream, StandardCharsets.UTF_8);
		write(table, writer);
		writer.flush();
	}

	/**
	 * Writes a table.
	 *
	 * @param table the table
	 * @param writer where to write
	 * @throws IOException if the writer fails
	 */
	static void write(ExportTable table, Writer writer) throws IOException {
		if (table.columns().isEmpty()) {
			return;
		}
		writer.write(table.columns().stream().map(name -> ExportTable.csvField(TextNode.valueOf(name))).collect(Collectors.joining(",")));
		writer.write("\r\n");
		for (List<JsonNode> row : table.rows()) {
			writer.write(row.stream().map(ExportTable::csvField).collect(Collectors.joining(",")));
			writer.write("\r\n");
		}
	}
}
