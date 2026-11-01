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
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import net.netshot.netshot.api.rest.v1.ListResponse;

/**
 * Writes a list as an Excel workbook: one sheet with a frozen, bold header row, then a row per item (see
 * {@link ExportTable} for the columns). Numbers, booleans and dates are typed cells (a date is in UTC, an Excel
 * date having no time zone). A resource can replace the generic sheet with its own workbook
 * ({@link ListExport#write}).
 */
@Produces(ExportFormat.APPLICATION_XLSX)
public class XlsxListWriter implements MessageBodyWriter<ListResponse<?>> {

	/** The number of rows kept in memory by the streaming workbook. */
	private static final int WINDOW_SIZE = 100;

	/** The widest column, in characters. */
	private static final int MAX_WIDTH = 60;

	@Context
	private HttpServletRequest request;

	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return ListResponse.class.isAssignableFrom(type) && ExportFormat.of(mediaType) == ExportFormat.XLSX;
	}

	@Override
	public void writeTo(ListResponse<?> list, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
		MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException {
		ListExport.Body body = ListExport.body(this.request);
		if (body != null) {
			body.writeTo(entityStream);
			return;
		}
		String name = ExportFilter.resourceName(this.request.getRequestURI());
		write(ListExport.table(this.request, list), name, entityStream);
	}

	/**
	 * Writes a table as a workbook.
	 *
	 * @param table the table
	 * @param sheetName the name of the sheet
	 * @param output where to write
	 * @throws IOException if the output fails
	 */
	static void write(ExportTable table, String sheetName, OutputStream output) throws IOException {
		SXSSFWorkbook workBook = new SXSSFWorkbook(WINDOW_SIZE);
		try {
			SXSSFSheet sheet = workBook.createSheet(WorkbookUtil.createSafeSheetName(sheetName));
			Font bold = workBook.createFont();
			bold.setBold(true);
			CellStyle titleStyle = workBook.createCellStyle();
			titleStyle.setFont(bold);
			CellStyle dateStyle = workBook.createCellStyle();
			dateStyle.setDataFormat(workBook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
			int[] widths = new int[table.columns().size()];
			Row header = sheet.createRow(0);
			for (int i = 0; i < widths.length; i++) {
				Cell cell = header.createCell(i);
				cell.setCellValue(table.columns().get(i));
				cell.setCellStyle(titleStyle);
				widths[i] = table.columns().get(i).length();
			}
			int y = 1;
			for (List<JsonNode> values : table.rows()) {
				Row row = sheet.createRow(y++);
				for (int i = 0; i < widths.length; i++) {
					int length = writeCell(row.createCell(i), values.get(i), dateStyle);
					widths[i] = Math.max(widths[i], length);
				}
			}
			for (int i = 0; i < widths.length; i++) {
				sheet.setColumnWidth(i, Math.min(MAX_WIDTH, Math.max(8, widths[i] + 2)) * 256);
			}
			if (widths.length > 0) {
				sheet.createFreezePane(0, 1);
				sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, widths.length - 1));
			}
			workBook.write(output);
		}
		finally {
			workBook.close();
		}
	}

	/**
	 * Fills a cell.
	 *
	 * @param cell the cell
	 * @param value the value (null for an empty cell)
	 * @param dateStyle the style of the dates
	 * @return the length of the text of the cell (to size the column)
	 */
	private static int writeCell(Cell cell, JsonNode value, CellStyle dateStyle) {
		if (value == null || value.isNull() || value.isMissingNode()) {
			return 0;
		}
		if (value.isBoolean()) {
			cell.setCellValue(value.booleanValue());
			return 5;
		}
		if (value.isNumber()) {
			cell.setCellValue(value.doubleValue());
			return value.asText().length();
		}
		String text = value.asText();
		if (ExportTable.isInstant(text)) {
			cell.setCellValue(LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC));
			cell.setCellStyle(dateStyle);
			return 19;
		}
		cell.setCellValue(text.length() > ExportTable.MAX_TEXT_LENGTH ? text.substring(0, ExportTable.MAX_TEXT_LENGTH) : text);
		return text.length();
	}
}
