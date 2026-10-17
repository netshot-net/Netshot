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
package net.netshot.netshot;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.quartz.Scheduler;
import org.quartz.impl.SchedulerRepository;

import jakarta.ws.rs.core.UriBuilder;
import net.netshot.netshot.api.rest.RestServer;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.engine.TaskManager;

/**
 * The XLSX export ({@code GET /reports/export}) with the combinations of options the
 * contract goldens don't reach. The workbook is dumped cell by cell (sheet names, column
 * widths, freeze panes, auto filters, values, number formats, bold titles, formulas) and
 * compared to the dumps stored under {@code src/test/resources/report-export/}, which were
 * produced by the former {@code RestService} implementation. Run with
 * {@code -Dnetshot.report.export.update=true} to (re)write them.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class ReportExportTest extends WithDatabaseTest {

	private static final String API_URL = TestNetwork.freeLocalApiUrl();
	private static final Path FIXTURES = Path.of("src", "test", "resources", "report-export");
	private static final String UPDATE_PROPERTY = "netshot.report.export.update";
	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
	/** Sheets whose row order is not defined by the queries. */
	private static final Set<String> UNORDERED_SHEETS = Set.of("Interfaces", "Configuration Compliance", "Device Groups", "Group Memberships");

	private final long startTime = System.currentTimeMillis();
	private TimeZone previousTimeZone;
	private Path binaryFolder;
	private HttpClient httpClient;
	private ApiContractSeed seed;

	@BeforeAll
	void bootAndSeed() throws Exception {
		this.previousTimeZone = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		Properties config = getFreshDatabaseConfig("reportexporttest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "INFO");
		config.setProperty("netshot.http.ssl.enabled", "false");
		URI uri = UriBuilder.fromUri(API_URL).replacePath("/").build();
		config.setProperty("netshot.http.baseurl", uri.toString());
		this.binaryFolder = Files.createTempDirectory("netshot-export-");
		config.setProperty("netshot.snapshots.binary.path", this.binaryFolder.toString());
		Netshot.initConfig(config);
		Netshot.loadModuleConfigs();
		TestDrivers.reload();
		Database.update();
		Wiring.init(TestDrivers.registry());
		TaskManager.init(Wiring.get().taskJobFactory());
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			scheduler.standby();
			scheduler.clear();
		}
		RestServer.init(Wiring.get().binder());
		Thread.sleep(1000);
		this.httpClient = HttpClient.newHttpClient();
		this.seed = new ApiContractSeed(API_URL, this.httpClient);
		this.seed.seed();
	}

	@AfterAll
	void shutdown() throws Exception {
		TimeZone.setDefault(this.previousTimeZone);
		if (this.binaryFolder != null) {
			try (Stream<Path> files = Files.walk(this.binaryFolder)) {
				files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
			}
		}
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			scheduler.clear();
			scheduler.start();
		}
	}

	@TestFactory
	List<DynamicTest> exports() {
		Map<String, Supplier<String>> variants = new LinkedHashMap<>();
		variants.put("none", () -> "");
		variants.put("interfaces", () -> "?interfaces=true");
		variants.put("inventory", () -> "?inventory=true");
		variants.put("inventory-history", () -> "?inventory=true&inventoryhistory=true");
		variants.put("locations", () -> "?locations=true");
		variants.put("compliance", () -> "?compliance=true");
		variants.put("groups", () -> "?groups=true");
		variants.put("groups-compliance", () -> "?groups=true&compliance=true");
		variants.put("attributes", () -> "?devicedriverattributes=true");
		variants.put("domain", () -> "?domain=%d&interfaces=true&compliance=true".formatted(this.seed.id("domain.lyon")));
		variants.put("group", () -> "?group=%d&groups=true&compliance=true&locations=true".formatted(this.seed.id("group.static")));
		variants.put("domain-group", () -> "?domain=%d&group=%d&inventory=true".formatted(this.seed.id("domain.paris"), this.seed.id("group.dynamic")));
		variants.put("everything", () -> "?interfaces=true&inventory=true&inventoryhistory=true&locations=true&compliance=true&groups=true"
			+ "&devicedriverattributes=true");
		boolean update = Boolean.getBoolean(UPDATE_PROPERTY);
		List<DynamicTest> tests = new ArrayList<>();
		for (Map.Entry<String, Supplier<String>> variant : variants.entrySet()) {
			tests.add(DynamicTest.dynamicTest(variant.getKey(), () -> {
				HttpResponse<byte[]> response = this.httpClient.send(HttpRequest.newBuilder()
					.version(HttpClient.Version.HTTP_1_1)
					.uri(URI.create(API_URL + "/reports/export" + variant.getValue().get()))
					.header("Accept", XLSX)
					.header("X-Netshot-API-Token", ApiContractSeed.ADMIN_TOKEN)
					.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
				Assertions.assertEquals(200, response.statusCode());
				Assertions.assertTrue(response.headers().firstValue("Content-Disposition").orElse("")
					.matches("attachment; filename=\"netshot-export_\\d{8}-\\d{6}\\.xlsx\""));
				Assertions.assertEquals(Integer.toString(response.body().length),
					response.headers().firstValue("Content-Length").orElse(null));
				String actual = this.dump(response.body());
				Path fixture = FIXTURES.resolve(variant.getKey() + ".txt");
				if (update) {
					Files.createDirectories(FIXTURES);
					Files.writeString(fixture, actual, StandardCharsets.UTF_8);
					return;
				}
				Assertions.assertTrue(Files.exists(fixture), "No fixture " + fixture);
				Assertions.assertEquals(Files.readString(fixture, StandardCharsets.UTF_8), actual);
			}));
		}
		return tests;
	}

	private String dump(byte[] raw) throws Exception {
		StringBuilder out = new StringBuilder();
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(raw))) {
			for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
				XSSFSheet sheet = workbook.getSheetAt(s);
				out.append("## Sheet ").append(sheet.getSheetName()).append('\n');
				int columns = 0;
				for (Row row : sheet) {
					columns = Math.max(columns, row.getLastCellNum());
				}
				StringBuilder widths = new StringBuilder("widths");
				for (int c = 0; c < columns; c++) {
					widths.append(' ').append(sheet.getColumnWidth(c));
				}
				out.append(widths).append('\n');
				out.append("freeze ").append(sheet.getPaneInformation() == null ? "none"
					: sheet.getPaneInformation().getHorizontalSplitTopRow() + "/" + sheet.getPaneInformation().getVerticalSplitLeftColumn()).append('\n');
				out.append("filter ").append(sheet.getCTWorksheet().isSetAutoFilter() ? sheet.getCTWorksheet().getAutoFilter().getRef() : "none").append('\n');
				out.append("column0 ").append(this.style((XSSFCellStyle) sheet.getColumnStyle(0))).append('\n');
				List<String> rows = new ArrayList<>();
				for (Row row : sheet) {
					List<String> cells = new ArrayList<>();
					boolean versionRow = false;
					for (Cell cell : row) {
						String value = this.value(cell);
						if (versionRow) {
							value = "'<version>'";
						}
						versionRow = cell.getColumnIndex() == 0 && "'Netshot version'".equals(value);
						cells.add(cell.getColumnIndex() + "=" + value + "{" + this.style((XSSFCellStyle) cell.getCellStyle()) + "}");
					}
					rows.add("[" + this.style((XSSFCellStyle) row.getRowStyle()) + "] " + String.join(" | ", cells));
				}
				if (UNORDERED_SHEETS.contains(sheet.getSheetName()) && rows.size() > 1) {
					rows.subList(1, rows.size()).sort(Comparator.naturalOrder());
				}
				rows.forEach(row -> out.append(row).append('\n'));
			}
		}
		return out.toString();
	}

	private String style(XSSFCellStyle style) {
		if (style == null) {
			return "-";
		}
		return style.getDataFormatString() + (style.getFont().getBold() ? ",bold" : "") + "," + style.getAlignment();
	}

	private String value(Cell cell) {
		CellType type = cell.getCellType();
		switch (type) {
			case NUMERIC:
				if (DateUtil.isCellDateFormatted(cell)) {
					long millis = cell.getDateCellValue().getTime();
					boolean now = millis >= this.startTime - 3_600_000L && millis <= this.startTime + 3_600_000L;
					return now ? "<now>" : "date:" + millis;
				}
				return Double.toString(cell.getNumericCellValue());
			case BOOLEAN:
				return Boolean.toString(cell.getBooleanCellValue());
			case STRING:
				return "'" + cell.getStringCellValue().replace("\n", "\\n") + "'";
			case BLANK:
				return "";
			case FORMULA:
				return "=" + cell.getCellFormula();
			default:
				return cell.toString();
		}
	}
}
