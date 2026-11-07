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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import net.netshot.netshot.testing.UnitTest;

/**
 * The flattening of a list into a table, and its CSV and Excel forms.
 */
@UnitTest
@DisplayName("List export table")
class ExportTableTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static JsonNode json(String text) throws IOException {
		return JSON.readTree(text);
	}

	private static String csv(ExportTable table) throws IOException {
		StringWriter writer = new StringWriter();
		CsvListWriter.write(table, writer);
		return writer.toString();
	}

	@Test
	@DisplayName("Columns: nested objects dotted, references by display, lists joined, union of the rows")
	void flatten() throws Exception {
		ExportTable table = ExportTable.of(json("""
			[{"id": 1, "name": "a", "domain": {"id": 3, "url": "/x", "display": "Paris", "name": "Paris"},
				"owner": {"id": 4, "name": "bob"}, "tags": [{"id": 1, "url": "/t/1", "display": "core"}, {"id": 2, "url": "/t/2", "display": "edge"}],
				"numbers": [1, 2], "options": [{"k": "v"}], "empty": [], "none": null, "flag": true},
				{"id": 2, "name": "b", "extra": "x"}]"""));
		Assertions.assertEquals(List.of("id", "name", "domain", "owner.id", "owner.name", "tags", "numbers", "options", "empty",
			"none", "flag", "extra"), table.columns());
		List<JsonNode> first = table.rows().get(0);
		Assertions.assertEquals("Paris", first.get(2).asText());
		Assertions.assertEquals("core;edge", first.get(5).asText());
		Assertions.assertEquals("1;2", first.get(6).asText());
		Assertions.assertEquals("{\"k\":\"v\"}", first.get(7).asText());
		Assertions.assertNull(first.get(8));
		Assertions.assertTrue(first.get(10).isBoolean());
		List<JsonNode> second = table.rows().get(1);
		Assertions.assertEquals(12, second.size());
		Assertions.assertNull(second.get(2));
		Assertions.assertEquals("x", second.get(11).asText());
	}

	@Test
	@DisplayName("A row which looks like a reference is not collapsed")
	void rowIsNotCollapsed() throws Exception {
		ExportTable table = ExportTable.of(json("[{\"id\": 1, \"url\": \"/a\", \"display\": \"A\"}]"));
		Assertions.assertEquals(List.of("id", "url", "display"), table.columns());
	}

	@Test
	@DisplayName("CSV: RFC 4180 quoting, CRLF, header, empty cells")
	void csvQuoting() throws Exception {
		ExportTable table = ExportTable.of(json("""
			[{"a": "plain", "b": "with, comma", "c": "say \\"hi\\"", "d": "two\\nlines", "e": null, "f": 12, "g": false},
				{"a": "x", "b": "", "c": "r\\rr"}]"""));
		Assertions.assertEquals("a,b,c,d,e,f,g\r\nplain,\"with, comma\",\"say \"\"hi\"\"\",\"two\nlines\",,12,false\r\nx,,\"r\rr\",,,,\r\n", csv(table));
		Assertions.assertEquals("", csv(ExportTable.of(json("[]"))));
		Assertions.assertEquals("\"a,b\"", ExportTable.csvField(TextNode.valueOf("a,b")));
	}

	@Test
	@DisplayName("CSV: texts which a spreadsheet would run as a formula get a leading quote")
	void csvInjection() {
		Assertions.assertEquals("'=1+1", ExportTable.csvField(TextNode.valueOf("=1+1")));
		Assertions.assertEquals("'@SUM(A1)", ExportTable.csvField(TextNode.valueOf("@SUM(A1)")));
		Assertions.assertEquals("'-2", ExportTable.csvField(TextNode.valueOf("-2")));
		Assertions.assertEquals("a=b", ExportTable.csvField(TextNode.valueOf("a=b")));
		Assertions.assertEquals("-2", ExportTable.csvField(JSON.getNodeFactory().numberNode(-2)));
	}

	@Test
	@DisplayName("Instants are recognised")
	void instants() {
		Assertions.assertTrue(ExportTable.isInstant("2026-10-30T12:13:14Z"));
		Assertions.assertTrue(ExportTable.isInstant("2026-10-30T12:13:14.123456Z"));
		Assertions.assertFalse(ExportTable.isInstant("2026-10-30"));
		Assertions.assertFalse(ExportTable.isInstant("2026-13-30T12:13:14Z"));
		Assertions.assertFalse(ExportTable.isInstant("router"));
	}

	@Test
	@DisplayName("Excel: typed cells, header, long texts truncated")
	void xlsx() throws Exception {
		ExportTable table = ExportTable.of(json("""
			[{"name": "a", "count": 5, "ok": true, "when": "2026-10-30T12:13:14Z", "ratio": 1.5, "empty": null}]"""));
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		XlsxListWriter.write(table, "my/list", output);
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
			var sheet = workbook.getSheetAt(0);
			Assertions.assertEquals("my list", sheet.getSheetName());
			Assertions.assertEquals("count", sheet.getRow(0).getCell(1).getStringCellValue());
			var row = sheet.getRow(1);
			Assertions.assertEquals(CellType.STRING, row.getCell(0).getCellType());
			Assertions.assertEquals(CellType.NUMERIC, row.getCell(1).getCellType());
			Assertions.assertEquals(5, row.getCell(1).getNumericCellValue());
			Assertions.assertEquals(CellType.BOOLEAN, row.getCell(2).getCellType());
			Assertions.assertEquals(CellType.NUMERIC, row.getCell(3).getCellType());
			Assertions.assertEquals(2026, row.getCell(3).getLocalDateTimeCellValue().getYear());
			Assertions.assertEquals(12, row.getCell(3).getLocalDateTimeCellValue().getHour());
			Assertions.assertEquals(1.5, row.getCell(4).getNumericCellValue());
		}
		ByteArrayOutputStream big = new ByteArrayOutputStream();
		XlsxListWriter.write(ExportTable.of(json("[{\"t\": \"" + "x".repeat(40000) + "\"}]")), "big", big);
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(big.toByteArray()))) {
			Assertions.assertEquals(ExportTable.MAX_TEXT_LENGTH, workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue().length());
		}
	}

	@Test
	@DisplayName("Negotiation: the internal parameter is removed from the query, the file is named after the resource")
	void negotiation() {
		Assertions.assertEquals("a=1&b=2", ExportNegotiation.withoutMarker("a=1&_export=csv&b=2&_export"));
		Assertions.assertEquals("", ExportNegotiation.withoutMarker("_export=xlsx"));
		Assertions.assertEquals("devices", ExportFilter.resourceName("devices"));
		Assertions.assertEquals("software-rules", ExportFilter.resourceName("compliance/software-rules/"));
		Assertions.assertEquals("export", ExportFilter.resourceName(""));
		Assertions.assertEquals("a_b", ExportFilter.resourceName("x/a b"));
	}
}
