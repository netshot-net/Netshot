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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * The export of the lists of the v1 API as CSV and Excel, over HTTP: the same list (filters, search, selection) in
 * another format, with or without paging, and the workbook of the devices.
 */
@ApiTest
@V1Endpoints({
	"GET /devices",
	"GET /devices/{id}",
	"GET /groups",
	"GET /domains"
})
@DisplayName("v1 list export")
@ResourceLock("DB")
public class V1ListExportTest {

	private static final String CSV = "text/csv";
	private static final String XLSX = ExportFormat.APPLICATION_XLSX;

	private V1Client admin;
	private Device router1;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.admin = new V1Client(server, UiUser.LEVEL_ADMIN);
		TestDevices.deleteAll();
		Domain paris = TestDevices.addDomain(Domain.builder().name("Paris-" + System.nanoTime()).description("Paris DC")
			.server4Address(new Network4Address("10.1.1.1")).build());
		this.router1 = TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(paris, null, 1).toBuilder()
			.comments("Line 1, \"quoted\"\nLine 2").tags(Set.of("core", "edge")).location("Paris 1").build());
		TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(paris, null, 2).toBuilder().tags(Set.of("core")).location("Paris 2").build());
		TestDevices.add(FakeDeviceFactory.getFakeCiscoIosDevice(paris, null, 3).toBuilder().status(Device.Status.DISABLED).build());
		TestDevices.addStaticGroup("Core-" + System.nanoTime(), this.router1);
	}

	private HttpResponse<byte[]> export(String path, String type) throws Exception {
		return this.admin.download(path, "Accept", type);
	}

	private static String text(HttpResponse<byte[]> response) {
		return new String(response.body(), StandardCharsets.UTF_8);
	}

	private static List<String> lines(HttpResponse<byte[]> response) {
		return List.of(text(response).split("\r\n"));
	}

	private static XSSFWorkbook workbook(HttpResponse<byte[]> response) throws IOException {
		return new XSSFWorkbook(new ByteArrayInputStream(response.body()));
	}

	private static List<String> column(Sheet sheet, int index) {
		List<String> values = new ArrayList<>();
		for (int y = 1; y <= sheet.getLastRowNum(); y++) {
			values.add(sheet.getRow(y).getCell(index).getStringCellValue());
		}
		return values;
	}

	@Test
	@DisplayName("CSV: the list with its filters, a header row, the media type and the file name")
	void csvDevices() throws Exception {
		HttpResponse<byte[]> response = this.export("/devices?status=INPRODUCTION&fields=name,status,mgmtDomain", CSV);
		Assertions.assertEquals(200, response.statusCode(), text(response));
		Assertions.assertEquals("text/csv;charset=UTF-8", response.headers().firstValue("Content-Type").orElse(null).replace("; ", ";"));
		Assertions.assertTrue(response.headers().firstValue("Content-Disposition").orElse("")
			.matches("attachment; filename=\"devices-\\d{8}-\\d{6}\\.csv\""), response.headers().toString());
		Assertions.assertEquals("2", response.headers().firstValue("X-Total-Count").orElse(null));
		List<String> lines = lines(response);
		Assertions.assertEquals(3, lines.size(), text(response));
		Assertions.assertEquals("id,name,status,mgmtDomain", lines.get(0));
		Assertions.assertTrue(lines.get(1).endsWith(",router00001,INPRODUCTION,Paris-" + lines.get(1).split("Paris-")[1]), lines.get(1));
		Assertions.assertTrue(lines.get(2).contains(",router00002,INPRODUCTION,"), lines.get(2));
	}

	@Test
	@DisplayName("CSV: sort, search, and the escaping of commas, quotes and line breaks")
	void csvEscaping() throws Exception {
		HttpResponse<byte[]> response = this.export("/devices?fields=name,comments,tags&sort=-name&q=router0000", CSV);
		Assertions.assertEquals(200, response.statusCode(), text(response));
		String body = text(response);
		Assertions.assertTrue(body.startsWith("id,name,comments,tags\r\n"), body);
		Assertions.assertTrue(body.indexOf("router00003") < body.indexOf("router00001"), body);
		Assertions.assertTrue(body.contains(",\"Line 1, \"\"quoted\"\"\nLine 2\",core;edge\r\n"), body);

		Assertions.assertEquals(List.of("router00001"), List.of(text(this.export("/devices?fields=name&name=router00001", CSV)).split("\r\n"))
			.subList(1, 2).stream().map(line -> line.split(",")[1]).toList());
	}

	@Test
	@DisplayName("CSV: the default brief view, references by their display, full view")
	void csvViews() throws Exception {
		List<String> brief = lines(this.export("/devices?name=router00001", CSV));
		Assertions.assertEquals("id,url,display,name,mgmtAddress,status,family,driver,driverDescription,networkClass,softwareLevel,compliant,"
			+ "endOfLife,endOfSale", brief.get(0));
		List<String> full = lines(this.export("/devices?name=router00001&view=full&omit=attributes,accesses,ownerGroups,options", CSV));
		Assertions.assertTrue(List.of(full.get(0).split(",")).contains("mgmtDomain"), full.get(0));
		Assertions.assertFalse(full.get(1).contains("{"), full.get(1));
		List<String> nested = lines(this.export("/devices?name=router00001&fields=name,mgmtDomain.description", CSV));
		Assertions.assertEquals("id,name,mgmtDomain.id,mgmtDomain.description", nested.get(0));
		Assertions.assertTrue(nested.get(1).endsWith(",Paris DC"), nested.get(1));
	}

	@Test
	@DisplayName("Export: all the rows without paging, unless the limit is explicit")
	void paging() throws Exception {
		Assertions.assertEquals(4, lines(this.export("/devices", CSV)).size());
		HttpResponse<byte[]> limited = this.export("/devices?limit=2&offset=1&fields=name", CSV);
		Assertions.assertEquals(List.of("id,name"), List.of(lines(limited).get(0)));
		Assertions.assertEquals(3, lines(limited).size());
		Assertions.assertTrue(lines(limited).get(1).endsWith("router00002"), text(limited));
		Assertions.assertEquals("3", limited.headers().firstValue("X-Total-Count").orElse(null));
		Assertions.assertEquals(400, this.export("/devices?limit=0", CSV).statusCode());
		// The JSON list is paged as usual
		Assertions.assertEquals(100, this.admin.get("/devices").json().get("limit").asInt());
	}

	@Test
	@DisplayName("Excel: one sheet with a header, typed cells, all rows; other lists are exported too")
	void xlsx() throws Exception {
		HttpResponse<byte[]> response = this.export("/devices?view=full&omit=attributes,accesses,ownerGroups,options&sort=name", XLSX);
		Assertions.assertEquals(200, response.statusCode());
		Assertions.assertTrue(response.headers().firstValue("Content-Disposition").orElse("")
			.matches("attachment; filename=\"devices-\\d{8}-\\d{6}\\.xlsx\""));
		try (XSSFWorkbook workbook = workbook(response)) {
			Assertions.assertEquals(1, workbook.getNumberOfSheets());
			Sheet sheet = workbook.getSheetAt(0);
			Assertions.assertEquals("devices", sheet.getSheetName());
			Row header = sheet.getRow(0);
			List<String> columns = new ArrayList<>();
			header.forEach(cell -> columns.add(cell.getStringCellValue()));
			Assertions.assertEquals(3, sheet.getLastRowNum());
			Assertions.assertEquals(List.of("router00001", "router00002", "router00003"), column(sheet, columns.indexOf("name")));
			Cell id = sheet.getRow(1).getCell(columns.indexOf("id"));
			Assertions.assertEquals(CellType.NUMERIC, id.getCellType());
			Assertions.assertEquals(this.router1.id(), (long) id.getNumericCellValue());
			Cell created = sheet.getRow(1).getCell(columns.indexOf("createdDate"));
			Assertions.assertEquals(CellType.NUMERIC, created.getCellType());
			Assertions.assertNotNull(created.getLocalDateTimeCellValue());
			Assertions.assertEquals("Line 1, \"quoted\"\nLine 2", sheet.getRow(1).getCell(columns.indexOf("comments")).getStringCellValue());
			Assertions.assertEquals("core;edge", sheet.getRow(1).getCell(columns.indexOf("tags")).getStringCellValue());
		}
		HttpResponse<byte[]> groups = this.export("/groups", XLSX);
		Assertions.assertEquals(200, groups.statusCode());
		try (XSSFWorkbook workbook = workbook(groups)) {
			Assertions.assertEquals("groups", workbook.getSheetAt(0).getSheetName());
			Assertions.assertTrue(workbook.getSheetAt(0).getLastRowNum() >= 1);
		}
	}

	@Test
	@DisplayName("Other lists: groups and domains as CSV")
	void otherLists() throws Exception {
		List<String> groups = lines(this.export("/groups?fields=name,type", CSV));
		Assertions.assertEquals("id,name,type", groups.get(0));
		Assertions.assertTrue(groups.stream().anyMatch(line -> line.contains(",Core-") && line.endsWith(",STATIC")), groups.toString());
		List<String> domains = lines(this.export("/domains?fields=name", CSV));
		Assertions.assertEquals("id,name", domains.get(0));
		Assertions.assertTrue(domains.get(1).contains("Paris-"));
	}

	@Test
	@DisplayName("Device workbook: the sheets of include, for the matching devices only")
	void deviceWorkbook() throws Exception {
		HttpResponse<byte[]> response = this.export("/devices?status=INPRODUCTION&tag=edge"
			+ "&include=interfaces,inventory,locations,compliance,groups,driverAttributes", XLSX);
		Assertions.assertEquals(200, response.statusCode(), text(response));
		Assertions.assertTrue(response.headers().firstValue("Content-Disposition").orElse("").contains("devices-"));
		try (XSSFWorkbook workbook = workbook(response)) {
			List<String> names = new ArrayList<>();
			workbook.forEach(sheet -> names.add(sheet.getSheetName()));
			Assertions.assertEquals("Summary", names.get(0));
			Assertions.assertEquals("Devices", names.get(1));
			Assertions.assertTrue(names.containsAll(List.of("Interfaces", "Inventory", "Configuration Compliance", "Device Groups",
				"Group Memberships")), names.toString());
			Sheet devices = workbook.getSheet("Devices");
			Assertions.assertEquals(1, devices.getLastRowNum());
			Assertions.assertEquals("router00001", devices.getRow(1).getCell(1).getStringCellValue());
			Sheet interfaces = workbook.getSheet("Interfaces");
			Assertions.assertTrue(interfaces.getLastRowNum() >= 1);
			for (int y = 1; y <= interfaces.getLastRowNum(); y++) {
				Assertions.assertEquals("router00001", interfaces.getRow(y).getCell(1).getStringCellValue());
			}
		}
		try (XSSFWorkbook workbook = workbook(this.export("/devices?query=%5BName%5D%20is%20%22router00002%22&include=inventoryHistory", XLSX))) {
			Assertions.assertNotNull(workbook.getSheet("Inventory"));
			Assertions.assertNull(workbook.getSheet("Interfaces"));
			Assertions.assertEquals(1, workbook.getSheet("Devices").getLastRowNum());
		}
		try (XSSFWorkbook workbook = workbook(this.export("/devices?name=nothing&include=", XLSX))) {
			Assertions.assertEquals(0, workbook.getSheet("Devices").getLastRowNum());
		}
	}

	@Test
	@DisplayName("Errors: include without the Excel format, unknown sheet, unsupported or not allowed formats")
	void errors() throws Exception {
		Answer json = this.admin.get("/devices?include=interfaces");
		Assertions.assertEquals(400, json.status(), json.toString());
		Assertions.assertEquals("include", json.json().get("errors").get(0).get("field").asText());
		HttpResponse<byte[]> csv = this.export("/devices?include=interfaces", CSV);
		Assertions.assertEquals(400, csv.statusCode());
		Assertions.assertEquals(400, this.export("/devices?include=nothing", XLSX).statusCode());
		Assertions.assertEquals(400, this.export("/devices?status=BROKEN", CSV).statusCode());

		Assertions.assertEquals(406, this.admin.get("/devices", "Accept", "text/plain").status());
		Assertions.assertEquals(406, this.admin.get("/devices", "Accept", "application/pdf").status());
		// Not a list
		Assertions.assertEquals(406, this.export("/devices/" + this.router1.id(), CSV).statusCode());
		// Preferring JSON, or any type, never gives an export
		Assertions.assertEquals(200, this.admin.get("/devices", "Accept", "application/json, text/csv;q=0.5").status());
		Answer any = this.admin.get("/devices", "Accept", "*/*");
		Assertions.assertEquals(200, any.status());
		Assertions.assertEquals(3, any.json().get("count").asInt());
		// The internal parameter can't be given by a client
		Answer forged = this.admin.get("/devices?_export=csv");
		Assertions.assertEquals(200, forged.status(), forged.toString());
		Assertions.assertEquals(100, forged.json().get("limit").asInt());
	}
}
