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
package net.netshot.netshot.api.rest.v1.report;

import java.io.ByteArrayInputStream;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.databind.JsonNode;

import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.compliance.CheckResult.ResultOption;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestCompliance;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.TestServer;
import net.netshot.netshot.testing.TestTasks;
import net.netshot.netshot.testing.TestWiring;
import net.netshot.netshot.testing.UtcTimeZoneExtension;
import net.netshot.netshot.testing.api.V1Client;
import net.netshot.netshot.testing.api.V1Client.Answer;
import net.netshot.netshot.testing.api.V1Endpoints;

/**
 * The reports of the v1 API, over HTTP.
 */
@ApiTest
@ExtendWith(UtcTimeZoneExtension.class)
@V1Endpoints({
	"GET /reports/access-failure-devices",
	"GET /reports/config-compliance-device-statuses",
	"GET /reports/group-config-compliance-stats",
	"GET /reports/group-config-non-compliant-devices",
	"GET /reports/group-devices-by-software-level",
	"GET /reports/group-software-compliance-stats",
	"GET /reports/hardware-support-devices",
	"GET /reports/hardware-support-stats",
	"GET /reports/changes-by-day",
	"GET /reports/export"
})
@DisplayName("v1 reports")
@ResourceLock("DB")
public class V1ReportResourceTest {

	private static final long DAY = 24L * 3600 * 1000;
	/** 2040-01-01T00:00:00Z. */
	private static final Instant EOS = Instant.ofEpochMilli(2_208_988_800_000L);
	/** 2041-01-01T00:00:00Z. */
	private static final Instant EOL = Instant.ofEpochMilli(2_240_524_800_000L);
	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private V1Client reader;
	private long domainA;
	private long domainB;
	private Device dev1;
	private Device dev2;
	private Device dev3;
	private long group1;
	private long policy;
	private long rule1;

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@BeforeEach
	void setUp(TestServer server) throws Exception {
		this.reader = new V1Client(server, UiUser.LEVEL_READONLY);
		TestWiring.get().transactions().run(dsl -> {
			dsl.execute("truncate table task cascade");
			dsl.deleteFrom(Tables.RULE).execute();
			dsl.deleteFrom(Tables.POLICY).execute();
		});
		TestDevices.deleteAll();
		Domain a = TestDevices.addDomain("Report A", "A");
		Domain b = TestDevices.addDomain("Report B", "B");
		this.domainA = a.id();
		this.domainB = b.id();
		this.dev1 = this.device("rep-1", a, Device.Status.INPRODUCTION, ConformanceLevel.GOLD, "1.0", EOS, null);
		this.dev2 = this.device("rep-2", b, Device.Status.INPRODUCTION, ConformanceLevel.SILVER, "2.0", EOS, EOL);
		this.dev3 = this.device("rep-3", a, Device.Status.DISABLED, ConformanceLevel.GOLD, "3.0", EOS, EOL);
		this.device("rep-4", a, Device.Status.INPRODUCTION, ConformanceLevel.BRONZE, "4.0", null, null);
		this.group1 = TestDevices.addStaticGroup("Report group", this.dev1, this.dev2, this.dev3).id();
		this.policy = TestCompliance.policy("Report policy", this.group1).id();
		TextRule text1 = TestCompliance.rule(TextRule.builder("Report rule 1", this.policy).enabled(true).build());
		TextRule text2 = TestCompliance.rule(TextRule.builder("Report rule 2", this.policy).enabled(true).build());
		this.rule1 = text1.id();
		TestCompliance.checkResults(
			CheckResult.of(text1.id(), this.dev1.id(), ResultOption.NONCONFORMING, "bad"),
			CheckResult.of(text2.id(), this.dev1.id(), ResultOption.CONFORMING, "ok"),
			CheckResult.of(text1.id(), this.dev2.id(), ResultOption.CONFORMING, "ok"),
			CheckResult.of(text2.id(), this.dev2.id(), ResultOption.EXEMPTED, "exempted"),
			CheckResult.of(text1.id(), this.dev3.id(), ResultOption.NONCONFORMING, "disabled device"));
		long now = System.currentTimeMillis();
		this.snapshot(this.dev1, Task.Status.SUCCESS, now - 10 * DAY);
		this.snapshot(this.dev1, Task.Status.FAILURE, now - DAY);
		this.snapshot(this.dev2, Task.Status.SUCCESS, now - DAY);
		TestDevices.addOldConfig(this.dev1.id(), Config.builder().author("").changeDate(Instant.ofEpochMilli(now)).build());
		TestDevices.addOldConfig(this.dev1.id(), Config.builder().author("").changeDate(Instant.ofEpochMilli(now - 3 * DAY)).build());
	}

	private Device device(String name, Domain domain, Device.Status status, ConformanceLevel level, String version, Instant eos, Instant eol) {
		return TestDevices.add(Device.create("CiscoIOS12", "10.40.0." + (10 + name.charAt(name.length() - 1)), domain, "seed")
			.toBuilder()
			.name(name)
			.status(status)
			.softwareLevel(level)
			.softwareVersion(version)
			.eosDate(eos)
			.eolDate(eol)
			.build());
	}

	private void snapshot(Device device, Task.Status status, long date) {
		Instant when = Instant.ofEpochMilli(date);
		TestTasks.add(TakeSnapshotTask.of(device, "snapshot", "tester", true, false, false).toBuilder()
			.creationDate(when).changeDate(when).scheduleReference(when).executionDate(when).build(), status);
	}

	private JsonNode get(String path) throws Exception {
		Answer answer = this.reader.get(path);
		Assertions.assertEquals(200, answer.status(), path + " -> " + answer);
		return answer.json();
	}

	private static Set<String> deviceNames(JsonNode page, String deviceProperty) {
		Set<String> names = new TreeSet<>();
		page.get("results").forEach(item -> names.add((deviceProperty == null ? item : item.get(deviceProperty)).get("name").asText()));
		return names;
	}

	private static Set<String> keys(JsonNode object) {
		Set<String> keys = new TreeSet<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	private void assertBadRequest(String path) throws Exception {
		Answer answer = this.reader.get(path);
		Assertions.assertEquals(400, answer.status(), path + " -> " + answer);
	}

	@Test
	@DisplayName("Group configuration compliance: one row per group in an unpaginated envelope, filters")
	void groupConfigComplianceStats() throws Exception {
		JsonNode page = this.get("/reports/group-config-compliance-stats");
		Assertions.assertEquals(1, page.get("count").asInt());
		Assertions.assertEquals(0, page.get("offset").asInt());
		Assertions.assertTrue(page.get("next").isNull());
		JsonNode row = page.get("results").get(0);
		Assertions.assertEquals(Set.of("compliantDeviceCount", "deviceCount", "folder", "group"), keys(row));
		Assertions.assertEquals("Report group", row.get("group").get("display").asText());
		Assertions.assertEquals(this.group1, row.get("group").get("id").asLong());
		Assertions.assertEquals("/api/v1/groups/" + this.group1, row.get("group").get("url").asText());
		Assertions.assertEquals(2, row.get("deviceCount").asInt(), "the disabled device is not counted");
		Assertions.assertEquals(1, row.get("compliantDeviceCount").asInt());
		row = this.get("/reports/group-config-compliance-stats?domain=" + this.domainA).get("results").get(0);
		Assertions.assertEquals(1, row.get("deviceCount").asInt());
		Assertions.assertEquals(0, row.get("compliantDeviceCount").asInt());
		row = this.get("/reports/group-config-compliance-stats?group=" + this.group1 + "&policy=" + (this.policy + 1000)).get("results").get(0);
		Assertions.assertEquals(2, row.get("compliantDeviceCount").asInt());
		Assertions.assertEquals(0, this.get("/reports/group-config-compliance-stats?group=" + (this.group1 + 1000)).get("count").asInt());
		this.assertBadRequest("/reports/group-config-compliance-stats?domain=abc");
		this.assertBadRequest("/reports/group-config-compliance-stats?domain=1,,2");
		this.assertBadRequest("/reports/group-config-compliance-stats?bogus=1");
		this.assertBadRequest("/reports/group-config-compliance-stats?limit=1");
	}

	@Test
	@DisplayName("Group software compliance: counts by level")
	void groupSoftwareComplianceStats() throws Exception {
		JsonNode row = this.get("/reports/group-software-compliance-stats").get("results").get(0);
		Assertions.assertEquals(Set.of("bronzeDeviceCount", "deviceCount", "goldDeviceCount", "group", "silverDeviceCount"), keys(row));
		Assertions.assertEquals(1, row.get("goldDeviceCount").asInt(), "rep-3 is gold but disabled");
		Assertions.assertEquals(1, row.get("silverDeviceCount").asInt());
		Assertions.assertEquals(0, row.get("bronzeDeviceCount").asInt());
		Assertions.assertEquals(2, row.get("deviceCount").asInt());
		row = this.get("/reports/group-software-compliance-stats?domain=" + this.domainB).get("results").get(0);
		Assertions.assertEquals(0, row.get("goldDeviceCount").asInt());
		Assertions.assertEquals(1, row.get("deviceCount").asInt());
		this.assertBadRequest("/reports/group-software-compliance-stats?domain=x");
		this.assertBadRequest("/reports/group-software-compliance-stats?group=1");
	}

	@Test
	@DisplayName("Hardware support: statistics by milestone and date, and the devices behind a row")
	void hardwareSupport() throws Exception {
		JsonNode stats = this.get("/reports/hardware-support-stats");
		Assertions.assertEquals(4, stats.get("count").asInt());
		JsonNode saleRow = null;
		JsonNode saleWithoutDate = null;
		for (JsonNode row : stats.get("results")) {
			Assertions.assertEquals(Set.of("date", "deviceCount", "type"), keys(row));
			if ("EOS".equals(row.get("type").asText())) {
				if (row.get("date").isNull()) {
					saleWithoutDate = row;
				}
				else {
					saleRow = row;
				}
			}
		}
		Assertions.assertNotNull(saleRow);
		Assertions.assertEquals(2, saleRow.get("deviceCount").asInt(), "rep-1 and rep-2 (rep-3 is disabled)");
		Assertions.assertEquals(1, saleWithoutDate.get("deviceCount").asInt());
		Assertions.assertEquals(EOS, Instant.parse(saleRow.get("date").asText()));
		Assertions.assertEquals(2, this.get("/reports/hardware-support-stats?domain=" + this.domainB).get("count").asInt());
		Assertions.assertEquals(2, this.get("/reports/hardware-support-stats?group=" + this.group1).get("results").get(0).get("deviceCount").asInt());

		String date = saleRow.get("date").asText();
		JsonNode devices = this.get("/reports/hardware-support-devices?type=eos&date=" + date);
		Assertions.assertEquals(2, devices.get("count").asInt());
		Assertions.assertEquals(Set.of("rep-1", "rep-2"), deviceNames(devices, null));
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "mgmtAddress", "status"), keys(devices.get("results").get(0)));
		Assertions.assertEquals(Set.of("rep-2"), deviceNames(this.get("/reports/hardware-support-devices?type=EOL&date=" + EOL), null));
		Assertions.assertEquals(Set.of("rep-4"), deviceNames(this.get("/reports/hardware-support-devices?type=eos"), null));
		Assertions.assertEquals(Set.of("rep-1", "rep-4"), deviceNames(this.get("/reports/hardware-support-devices?type=eol&domain=" + this.domainA), null));
		Assertions.assertEquals(Set.of("rep-1"), deviceNames(this.get("/reports/hardware-support-devices?type=eol&group=" + this.group1), null));
		// Pagination
		JsonNode first = this.get("/reports/hardware-support-devices?type=eos&date=" + date + "&limit=1");
		Assertions.assertEquals(1, first.get("results").size());
		Assertions.assertEquals(2, first.get("count").asInt());
		Assertions.assertEquals(1, first.get("limit").asInt());
		Assertions.assertTrue(first.get("next").asText().contains("offset=1"), first.toString());
		JsonNode second = this.get("/reports/hardware-support-devices?type=eos&date=" + date + "&limit=1&offset=1");
		Assertions.assertTrue(second.get("next").isNull());
		Assertions.assertNotEquals(first.get("results").get(0).get("id").asLong(), second.get("results").get(0).get("id").asLong());
		Assertions.assertTrue(this.get("/reports/hardware-support-devices?type=eos&date=" + date + "&count=false").get("count").isNull());
		// Fields only a full device has
		JsonNode full = this.get("/reports/hardware-support-devices?type=eos&date=" + date + "&fields=name,softwareVersion");
		Assertions.assertEquals(Set.of("id", "name", "softwareVersion"), keys(full.get("results").get(0)));
		Assertions.assertFalse(full.get("results").get(0).get("softwareVersion").isNull());
		// Errors
		this.assertBadRequest("/reports/hardware-support-devices");
		this.assertBadRequest("/reports/hardware-support-devices?type=other");
		this.assertBadRequest("/reports/hardware-support-devices?type=eos&type=eol");
		this.assertBadRequest("/reports/hardware-support-devices?type=eos&date=yesterday");
		this.assertBadRequest("/reports/hardware-support-devices?type=eos&q=rep");
		this.assertBadRequest("/reports/hardware-support-devices?type=eos&sort=name");
		this.assertBadRequest("/reports/hardware-support-devices?type=eos&limit=0");
		this.assertBadRequest("/reports/hardware-support-devices?type=eos&unknown=1");
		this.assertBadRequest("/reports/hardware-support-stats?group=abc");
	}

	@Test
	@DisplayName("Changes by day: the last days, oldest first, in a time zone")
	void changesByDay() throws Exception {
		JsonNode page = this.get("/reports/changes-by-day?days=7&timeZone=UTC");
		Assertions.assertEquals(7, page.get("count").asInt());
		JsonNode rows = page.get("results");
		Assertions.assertEquals(Set.of("changeCount", "date"), keys(rows.get(0)));
		for (int i = 1; i < 7; i++) {
			Assertions.assertEquals(DAY, Instant.parse(rows.get(i).get("date").asText()).toEpochMilli()
				- Instant.parse(rows.get(i - 1).get("date").asText()).toEpochMilli());
		}
		Assertions.assertEquals(0, Instant.parse(rows.get(0).get("date").asText()).toEpochMilli() % DAY, "days start at midnight");
		Assertions.assertTrue(rows.get(6).get("changeCount").asInt() >= 1, "today's change");
		Assertions.assertTrue(rows.get(3).get("changeCount").asInt() >= 1, "the change of 3 days ago");
		Assertions.assertEquals(7, this.get("/reports/changes-by-day").get("results").size(), "7 days by default");
		Assertions.assertEquals(30, this.get("/reports/changes-by-day?days=30&timeZone=Europe/Paris").get("results").size());
		this.assertBadRequest("/reports/changes-by-day?days=0");
		this.assertBadRequest("/reports/changes-by-day?days=367");
		this.assertBadRequest("/reports/changes-by-day?days=many");
		this.assertBadRequest("/reports/changes-by-day?timeZone=No/Such_Zone");
		this.assertBadRequest("/reports/changes-by-day?tz=UTC");
	}

	@Test
	@DisplayName("Access failures: devices without recent success, with their last snapshots")
	void accessFailureDevices() throws Exception {
		JsonNode page = this.get("/reports/access-failure-devices");
		Assertions.assertEquals(2, page.get("count").asInt());
		List<String> names = new ArrayList<>();
		page.get("results").forEach(item -> names.add(item.get("device").get("name").asText()));
		Assertions.assertEquals(List.of("rep-1", "rep-4"), names, "rep-2 had a success yesterday, rep-3 is disabled");
		JsonNode rep1 = page.get("results").get(0);
		Assertions.assertEquals(Set.of("device", "lastFailure", "lastSuccess"), keys(rep1));
		Assertions.assertEquals(Set.of("id", "url", "display", "name", "mgmtAddress", "status"), keys(rep1.get("device")));
		Assertions.assertTrue(Instant.parse(rep1.get("lastSuccess").asText()).isBefore(Instant.parse(rep1.get("lastFailure").asText())));
		Assertions.assertTrue(page.get("results").get(1).get("lastSuccess").isNull());
		Assertions.assertEquals(Set.of("rep-4"), deviceNames(this.get("/reports/access-failure-devices?days=30"), "device"));
		Assertions.assertEquals(Set.of("rep-4"), deviceNames(this.get("/reports/access-failure-devices?domain=" + this.domainA + "&days=30"), "device"));
		Assertions.assertEquals(0, this.get("/reports/access-failure-devices?domain=" + this.domainB).get("count").asInt());
		JsonNode paged = this.get("/reports/access-failure-devices?limit=1&offset=1");
		Assertions.assertEquals(2, paged.get("count").asInt());
		Assertions.assertEquals("rep-4", paged.get("results").get(0).get("device").get("name").asText());
		JsonNode expanded = this.get("/reports/access-failure-devices?expand=device");
		Assertions.assertEquals("1.0", expanded.get("results").get(0).get("device").get("softwareVersion").asText());
		this.assertBadRequest("/reports/access-failure-devices?days=0");
		this.assertBadRequest("/reports/access-failure-devices?days=three");
		this.assertBadRequest("/reports/access-failure-devices?expand=lastSuccess");
	}

	@Test
	@DisplayName("Configuration compliance of devices: one row per rule, filters")
	void configComplianceDeviceStatuses() throws Exception {
		JsonNode page = this.get("/reports/config-compliance-device-statuses");
		Assertions.assertEquals(4, page.get("count").asInt(), "the disabled device is ignored");
		JsonNode row = null;
		for (JsonNode item : page.get("results")) {
			if (item.get("device").get("name").asText().equals("rep-1") && item.get("rule").get("id").asLong() == this.rule1) {
				row = item;
			}
		}
		Assertions.assertNotNull(row);
		Assertions.assertEquals(Set.of("checkDate", "device", "policy", "result", "rule"), keys(row));
		Assertions.assertEquals("NONCONFORMING", row.get("result").asText());
		Assertions.assertEquals("Report policy", row.get("policy").get("display").asText());
		Assertions.assertEquals("/api/v1/compliance/policies/" + this.policy, row.get("policy").get("url").asText());
		Assertions.assertEquals("Report rule 1", row.get("rule").get("display").asText());
		Assertions.assertEquals("/api/v1/compliance/rules/" + this.rule1, row.get("rule").get("url").asText());
		Assertions.assertFalse(row.get("device").has("softwareVersion"));
		Assertions.assertEquals(2, this.get("/reports/config-compliance-device-statuses?result=conforming").get("count").asInt());
		Assertions.assertEquals(3, this.get("/reports/config-compliance-device-statuses?result=CONFORMING,EXEMPTED").get("count").asInt());
		Assertions.assertEquals(3, this.get("/reports/config-compliance-device-statuses?result=CONFORMING&result=NONCONFORMING").get("count").asInt());
		Assertions.assertEquals(2, this.get("/reports/config-compliance-device-statuses?domain=" + this.domainA).get("count").asInt());
		Assertions.assertEquals(4, this.get("/reports/config-compliance-device-statuses?group=" + this.group1).get("count").asInt());
		Assertions.assertEquals(0, this.get("/reports/config-compliance-device-statuses?policy=" + (this.policy + 1000)).get("count").asInt());
		Assertions.assertEquals("1.0", this.get("/reports/config-compliance-device-statuses?domain=" + this.domainA + "&expand=device")
			.get("results").get(0).get("device").get("softwareVersion").asText());
		this.assertBadRequest("/reports/config-compliance-device-statuses?result=FINE");
		this.assertBadRequest("/reports/config-compliance-device-statuses?policy=a");
		this.assertBadRequest("/reports/config-compliance-device-statuses?foo=bar");
	}

	@Test
	@DisplayName("Non compliant devices of a group")
	void groupConfigNonCompliantDevices() throws Exception {
		JsonNode page = this.get("/reports/group-config-non-compliant-devices?group=" + this.group1);
		Assertions.assertEquals(1, page.get("count").asInt());
		JsonNode row = page.get("results").get(0);
		Assertions.assertEquals("rep-1", row.get("device").get("name").asText());
		Assertions.assertEquals("NONCONFORMING", row.get("result").asText());
		Assertions.assertEquals(0, this.get("/reports/group-config-non-compliant-devices?group=" + this.group1 + "&domain=" + this.domainB).get("count").asInt());
		Assertions.assertEquals(0, this.get("/reports/group-config-non-compliant-devices?group=" + this.group1 + "&policy=" + (this.policy + 1000)).get("count").asInt());
		Assertions.assertEquals(404, this.reader.get("/reports/group-config-non-compliant-devices?group=99999999").status());
		this.assertBadRequest("/reports/group-config-non-compliant-devices");
		this.assertBadRequest("/reports/group-config-non-compliant-devices?group=x");
		this.assertBadRequest("/reports/group-config-non-compliant-devices?group=" + this.group1 + "&group=" + this.group1);
	}

	@Test
	@DisplayName("Devices of a group by software level")
	void groupDevicesBySoftwareLevel() throws Exception {
		JsonNode page = this.get("/reports/group-devices-by-software-level?group=" + this.group1 + "&level=GoLd");
		Assertions.assertEquals(1, page.get("count").asInt(), "rep-3 is gold but disabled");
		JsonNode row = page.get("results").get(0);
		Assertions.assertEquals(Set.of("device", "softwareVersion"), keys(row));
		Assertions.assertEquals("rep-1", row.get("device").get("name").asText());
		Assertions.assertEquals("1.0", row.get("softwareVersion").asText());
		Assertions.assertEquals(1, this.get("/reports/group-devices-by-software-level?group=" + this.group1 + "&level=silver").get("count").asInt());
		Assertions.assertEquals(0, this.get("/reports/group-devices-by-software-level?group=" + this.group1 + "&level=silver&domain=" + this.domainA).get("count").asInt());
		Assertions.assertEquals(0, this.get("/reports/group-devices-by-software-level?group=" + this.group1 + "&level=unknown").get("count").asInt());
		Assertions.assertEquals(404, this.reader.get("/reports/group-devices-by-software-level?group=99999999&level=gold").status());
		this.assertBadRequest("/reports/group-devices-by-software-level?group=" + this.group1);
		this.assertBadRequest("/reports/group-devices-by-software-level?level=gold");
		this.assertBadRequest("/reports/group-devices-by-software-level?group=" + this.group1 + "&level=platinum");
	}

	@Test
	@DisplayName("Export: spreadsheet with the requested sections, strict parameters")
	void export() throws Exception {
		HttpResponse<byte[]> response = this.reader.download("/reports/export?include=interfaces,compliance&include=groups", "Accept", XLSX);
		Assertions.assertEquals(200, response.statusCode());
		Assertions.assertEquals(XLSX, response.headers().firstValue("Content-Type").orElse(null));
		Assertions.assertTrue(response.headers().firstValue("Content-Disposition").orElse("")
			.matches("attachment; filename=\"netshot-export_\\d{8}-\\d{6}\\.xlsx\""));
		Assertions.assertEquals(Integer.toString(response.body().length), response.headers().firstValue("Content-Length").orElse(null));
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.body()))) {
			List<String> sheets = new ArrayList<>();
			workbook.forEach(sheet -> sheets.add(sheet.getSheetName()));
			Assertions.assertEquals(List.of("Summary", "Devices", "Interfaces", "Configuration Compliance", "Device Groups", "Group Memberships"),
				sheets);
			XSSFSheet devices = workbook.getSheet("Devices");
			Assertions.assertEquals(4, devices.getLastRowNum(), "a title row and the four devices");
			Assertions.assertEquals(4, workbook.getSheet("Configuration Compliance").getLastRowNum());
		}
		// Only the devices of a domain, no optional section
		response = this.reader.download("/reports/export?domain=" + this.domainB + "&format=xlsx", "Accept", XLSX);
		Assertions.assertEquals(200, response.statusCode());
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.body()))) {
			Assertions.assertEquals(2, workbook.getNumberOfSheets());
			Assertions.assertEquals(1, workbook.getSheet("Devices").getLastRowNum());
		}
		for (String bad : List.of("include=sheet", "format=csv", "domain=a", "interfaces=true", "include=")) {
			response = this.reader.download("/reports/export?" + bad, "Accept", "*/*");
			Assertions.assertEquals(400, response.statusCode(), bad);
		}
	}
}
