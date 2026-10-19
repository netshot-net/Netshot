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
package net.netshot.netshot.api.rest.v0.report;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.Session;

import jakarta.ws.rs.core.MediaType;
import net.netshot.netshot.FakeDeviceFactory;
import net.netshot.netshot.TestDrivers;
import net.netshot.netshot.WithRestServerTest;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@DisplayName("Report tests")
@ResourceLock("DB")
public class ReportResourceTest extends WithRestServerTest {

	private String testDomainName = "Domain 1";
	private Domain testDomain;
	private List<Device> testDevices;

	private void createTestDomain() throws IOException {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			this.testDomain = new Domain(
				this.testDomainName, "Test Domain for devices",
				new Network4Address("10.1.1.1"),
				null
			);
			session.persist(this.testDomain);
			session.getTransaction().commit();
		}
	}

	private void createTestDevices() {
		this.testDevices = new ArrayList<>();
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			for (int i = 0; i < 1000; i++) {
				Device device = FakeDeviceFactory.getFakeCiscoIosDevice(this.testDomain, null, i);
				this.testDevices.add(device);
				session.persist(device);
			}
			session.getTransaction().commit();
		}
	}

	@BeforeAll
	static void loadDrivers() throws Exception {
		TestDrivers.reload();
	}

	@AfterEach
	void cleanUpData() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session
				.createMutationQuery("delete from Device")
				.executeUpdate();
			session
				.createMutationQuery("delete from DeviceCredentialSet cs where cs.deviceSpecific is true")
				.executeUpdate();
			session
				.createMutationQuery("delete from Domain")
				.executeUpdate();
			session.getTransaction().commit();
		}
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
