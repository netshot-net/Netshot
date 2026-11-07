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
package net.netshot.netshot.api.rest.v0.report;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import jakarta.ws.rs.core.MediaType;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.testing.ApiTest;
import net.netshot.netshot.testing.TestDevices;
import net.netshot.netshot.testing.TestDrivers;
import net.netshot.netshot.testing.api.NetshotApiClient;

@ApiTest
@DisplayName("Report tests")
@ResourceLock("DB")
public class ReportResourceTest {

	/** API client of the current test, authenticated with the admin token. */
	private NetshotApiClient apiClient;

	@BeforeEach
	void createApiClient(NetshotApiClient client) {
		this.apiClient = client;
	}

	private String testDomainName = "Domain 1";
	private Domain testDomain;
	private List<Device> testDevices;

	private void createTestDomain() throws IOException {
		this.testDomain = TestDevices.addDomain(Domain.builder().name(this.testDomainName).description("Test Domain for devices")
			.server4Address(new Network4Address("10.1.1.1")).build());
	}

	private void createTestDevices() {
		this.testDevices = new ArrayList<>();
		for (int i = 0; i < 1000; i++) {
			this.testDevices.add(TestDevices.addFakeCiscoIos(this.testDomain, i));
		}
	}

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@AfterEach
	void cleanUpData() {
		TestDevices.deleteAll();
	}

	@Test
	@DisplayName("Export data test")
	@ResourceLock("DB")
	void exportDataTest() throws IOException, InterruptedException {
		this.createTestDomain();
		this.createTestDevices();
		apiClient.setMediaType(MediaType.WILDCARD_TYPE);
		{
			InputStream xlsStream = apiClient.download("/reports/export?format=xlsx").body();
			try (XSSFWorkbook wb = new XSSFWorkbook(xlsStream)) {
				Assertions.assertEquals(wb.getNumberOfSheets(), 2);
				XSSFSheet deviceSheet = wb.getSheet("Devices");
				Assertions.assertEquals(this.testDevices.size(), deviceSheet.getLastRowNum(),
					"Excel report doesn't have the expected number of lines in Devices sheet");
			}
		}
		{
			InputStream xlsStream = apiClient.download("/reports/export?interfaces=true").body();
			try (XSSFWorkbook wb = new XSSFWorkbook(xlsStream)) {
				Assertions.assertEquals(wb.getNumberOfSheets(), 3);
				XSSFSheet deviceSheet = wb.getSheet("Devices");
				Assertions.assertEquals(deviceSheet.getLastRowNum(), this.testDevices.size(),
					"Excel report doesn't have the expected number of lines in Devices sheet");
				XSSFSheet intfSheet = wb.getSheet("Interfaces");
				Assertions.assertEquals(this.testDevices.size() * 4, intfSheet.getLastRowNum(),
					"Excel report doesn't have the expected number of lines in Interfaces sheet");
			}
		}
		{
			InputStream xlsStream = apiClient.download("/reports/export?inventory=true").body();
			try (XSSFWorkbook wb = new XSSFWorkbook(xlsStream)) {
				Assertions.assertEquals(wb.getNumberOfSheets(), 3);
				XSSFSheet deviceSheet = wb.getSheet("Devices");
				Assertions.assertEquals(deviceSheet.getLastRowNum(), this.testDevices.size(),
					"Excel report doesn't have the expected number of lines in Devices sheet");
				XSSFSheet intfSheet = wb.getSheet("Inventory");
				Assertions.assertEquals(this.testDevices.size() * 2, intfSheet.getLastRowNum(),
					"Excel report doesn't have the expected number of lines in Inventory sheet");
			}
		}
	}

}
