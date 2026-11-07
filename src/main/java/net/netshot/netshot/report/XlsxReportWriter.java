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
package net.netshot.netshot.report;

import java.util.Date;
import java.util.List;

import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import net.netshot.netshot.inventory.compliance.ConformanceLevel;

/**
 * Writes the data export as a spreadsheet: a sheet per section, a title row (bold, frozen, with
 * an auto filter) and a row per item. The workbook is a streaming one, so the rows already
 * written are flushed to disk and the memory use doesn't depend on the number of rows.
 *
 * <p>The sections are written in the order the {@code ...Start} calls come, which is the order
 * of the sheets.</p>
 */
public class XlsxReportWriter implements ReportExportSink {

	private static final int WINDOW_SIZE = 100;

	private final SXSSFWorkbook workBook;
	private final String version;
	private final String userName;
	private final CellStyle datetimeCellStyle;
	private final CellStyle dateCellStyle;
	private final CellStyle titleCellStyle;
	private final CellStyle percentCellStyle;

	/** The sheet being written, and the index of its last row. */
	private Sheet sheet;
	private int y;
	/** The options of the section being written. */
	private boolean compliance;
	private boolean locations;
	private boolean history;

	/**
	 * Instantiates the writer.
	 *
	 * @param workBook the workbook to write to (closed by the caller)
	 * @param version the Netshot version, shown in the summary
	 * @param userName the name of the user exporting the data, shown in the summary
	 */
	public XlsxReportWriter(SXSSFWorkbook workBook, String version, String userName) {
		this.workBook = workBook;
		this.version = version;
		this.userName = userName;
		CreationHelper createHelper = workBook.getCreationHelper();
		this.datetimeCellStyle = workBook.createCellStyle();
		this.datetimeCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
		this.datetimeCellStyle.setAlignment(HorizontalAlignment.LEFT);
		this.dateCellStyle = workBook.createCellStyle();
		this.dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));
		this.titleCellStyle = workBook.createCellStyle();
		Font titleFont = workBook.createFont();
		titleFont.setBold(true);
		this.titleCellStyle.setFont(titleFont);
		this.percentCellStyle = workBook.createCellStyle();
		this.percentCellStyle.setDataFormat(workBook.createDataFormat()
			.getFormat(BuiltinFormats.getBuiltinFormat(0x09)));
	}

	@Override
	public void summary(List<String> domains, List<String> groups) {
		Row row;
		Cell cell;
		Sheet summarySheet = this.workBook.createSheet("Summary");
		summarySheet.setColumnWidth(0, 5000);
		summarySheet.setColumnWidth(1, 8000);

		int summaryY = -1;
		row = summarySheet.createRow(++summaryY);
		row.createCell(0).setCellValue("Netshot version");
		row.getCell(0).setCellStyle(this.titleCellStyle);
		row.createCell(1).setCellValue(this.version);
		row = summarySheet.createRow(++summaryY);
		row.createCell(0).setCellValue("Exported by");
		row.getCell(0).setCellStyle(this.titleCellStyle);
		row.createCell(1).setCellValue(this.userName);
		row = summarySheet.createRow(++summaryY);
		row.createCell(0).setCellValue("Date and time");
		row.getCell(0).setCellStyle(this.titleCellStyle);
		cell = row.createCell(1);
		cell.setCellValue(new Date());
		cell.setCellStyle(this.datetimeCellStyle);

		row = summarySheet.createRow(++summaryY);
		row.createCell(0).setCellValue("Selected Domain");
		row.getCell(0).setCellStyle(this.titleCellStyle);
		row.createCell(1).setCellValue(domains == null ? "Any" : String.join(", ", domains));
		row = summarySheet.createRow(++summaryY);
		row.createCell(0).setCellValue("Selected Group");
		row.getCell(0).setCellStyle(this.titleCellStyle);
		row.createCell(1).setCellValue(groups == null ? "Any" : String.join(", ", groups));
		summarySheet.setDefaultColumnStyle(0, this.titleCellStyle);
	}

	/**
	 * Starts a sheet: creates it and the title row. The caller adds the titles, then calls
	 * {@link #endTitles(Row, int, boolean)}.
	 *
	 * @param name the name of the sheet
	 * @return the title row
	 */
	private Row startSheet(String name) {
		this.sheet = this.workBook.createSheet(name);
		((SXSSFSheet) this.sheet).setRandomAccessWindowSize(WINDOW_SIZE);
		this.y = -1;
		return this.sheet.createRow(++this.y);
	}

	/**
	 * Adds a title cell, with the width of its column.
	 *
	 * @param row the title row
	 * @param x the column
	 * @param title the title
	 * @param width the width of the column
	 */
	private void title(Row row, int x, String title, int width) {
		row.createCell(x).setCellValue(title);
		row.getCell(x).setCellStyle(this.titleCellStyle);
		this.sheet.setColumnWidth(x, width);
	}

	/**
	 * Ends the title row: freezes it and adds the filter (and styles the row if asked).
	 *
	 * @param row the title row
	 * @param lastX the last column
	 * @param rowStyle whether to style the whole row as a title
	 */
	private void endTitles(Row row, int lastX, boolean rowStyle) {
		if (rowStyle) {
			row.setRowStyle(this.titleCellStyle);
		}
		this.sheet.createFreezePane(0, this.y + 1);
		this.sheet.setAutoFilter(new CellRangeAddress(0, this.y, 0, lastX));
	}

	@Override
	public void devicesStart(boolean exportCompliance, boolean exportLocations) {
		this.compliance = exportCompliance;
		this.locations = exportLocations;
		Row row = this.startSheet("Devices");
		int x = -1;
		this.title(row, ++x, "ID", 2200);
		this.title(row, ++x, "Name", 5000);
		this.title(row, ++x, "Management IP", 4000);
		this.title(row, ++x, "Domain", 4000);
		this.title(row, ++x, "Network Class", 4000);
		this.title(row, ++x, "Family", 5000);
		this.title(row, ++x, "Creation", 4200);
		this.title(row, ++x, "Last Change", 4200);
		this.title(row, ++x, "Software", 5000);
		if (exportCompliance) {
			this.title(row, ++x, "Software Level", 3500);
			this.title(row, ++x, "End of Sale Date", 4200);
			this.title(row, ++x, "End Of Life Date", 4200);
		}
		if (exportLocations) {
			this.title(row, ++x, "Location", 5000);
			this.title(row, ++x, "Contact", 5000);
		}
		this.endTitles(row, x, true);
	}

	@Override
	public void device(ExportedDevice device) {
		Cell cell;
		int x = -1;
		Row row = this.sheet.createRow(++this.y);
		row.createCell(++x).setCellValue(device.id());
		row.createCell(++x).setCellValue(device.name());
		row.createCell(++x).setCellValue(device.mgmtAddress());
		row.createCell(++x).setCellValue(device.domainName());
		row.createCell(++x).setCellValue(device.networkClass().toString());
		row.createCell(++x).setCellValue(device.family());
		cell = row.createCell(++x);
		cell.setCellValue(device.createdDate());
		cell.setCellStyle(this.datetimeCellStyle);
		cell = row.createCell(++x);
		cell.setCellValue(device.changeDate());
		cell.setCellStyle(this.datetimeCellStyle);
		row.createCell(++x).setCellValue(device.softwareVersion());
		if (this.compliance) {
			row.createCell(++x).setCellValue((device.softwareLevel() == null
				? ConformanceLevel.UNKNOWN : device.softwareLevel()).toString());
			cell = row.createCell(++x);
			if (device.eosDate() != null) {
				cell.setCellValue(device.eosDate());
				cell.setCellStyle(this.dateCellStyle);
			}
			cell = row.createCell(++x);
			if (device.eolDate() != null) {
				cell.setCellValue(device.eolDate());
				cell.setCellStyle(this.dateCellStyle);
			}
		}
		if (this.locations) {
			row.createCell(++x).setCellValue(device.location());
			row.createCell(++x).setCellValue(device.contact());
		}
	}

	@Override
	public void interfacesStart() {
		Row row = this.startSheet("Interfaces");
		int x = -1;
		this.title(row, ++x, "Device ID", 2200);
		this.title(row, ++x, "Device Name", 5000);
		this.title(row, ++x, "Virtual Device", 5000);
		this.title(row, ++x, "Name", 5000);
		this.title(row, ++x, "Description", 7000);
		this.title(row, ++x, "VRF", 5000);
		this.title(row, ++x, "MAC Address", 4000);
		this.title(row, ++x, "Enabled", 2000);
		this.title(row, ++x, "Level 3", 2000);
		this.title(row, ++x, "IP Address", 4000);
		this.title(row, ++x, "Length", 2000);
		this.title(row, ++x, "Usage", 4000);
		this.endTitles(row, x, true);
	}

	@Override
	public void networkInterface(ExportedInterface networkInterface) {
		Row row;
		if (networkInterface.addresses().isEmpty()) {
			row = this.sheet.createRow(++this.y);
			this.interfaceCells(row, networkInterface);
			row.createCell(9).setCellValue("");
			row.createCell(10).setCellValue("");
			row.createCell(11).setCellValue("");
		}
		for (ExportedAddress address : networkInterface.addresses()) {
			row = this.sheet.createRow(++this.y);
			this.interfaceCells(row, networkInterface);
			int x = 8;
			row.createCell(++x).setCellValue(address.ip());
			row.createCell(++x).setCellValue(address.prefixLength());
			row.createCell(++x).setCellValue(address.usage() == null ? "" : address.usage().toString());
		}
	}

	/**
	 * Writes the nine first cells of an interface row.
	 *
	 * @param row the row
	 * @param networkInterface the interface
	 */
	private void interfaceCells(Row row, ExportedInterface networkInterface) {
		int x = -1;
		row.createCell(++x).setCellValue(networkInterface.deviceId());
		row.createCell(++x).setCellValue(networkInterface.deviceName());
		row.createCell(++x).setCellValue(networkInterface.virtualDevice());
		row.createCell(++x).setCellValue(networkInterface.name());
		row.createCell(++x).setCellValue(networkInterface.description());
		row.createCell(++x).setCellValue(networkInterface.vrf());
		row.createCell(++x).setCellValue(networkInterface.macAddress());
		row.createCell(++x).setCellValue(networkInterface.enabled());
		row.createCell(++x).setCellValue(networkInterface.level3());
	}

	@Override
	public void inventoryStart(boolean exportHistory) {
		this.history = exportHistory;
		Row row = this.startSheet("Inventory");
		int x = -1;
		this.title(row, ++x, "Device ID", 2200);
		this.title(row, ++x, "Device Name", 5000);
		this.title(row, ++x, "Slot", 5000);
		this.title(row, ++x, "Part Number", 5000);
		this.title(row, ++x, "Serial Number", 4000);
		row.setRowStyle(this.titleCellStyle);
		if (exportHistory) {
			this.title(row, ++x, "First seen", 4200);
			this.title(row, ++x, "Last seen", 4200);
			this.title(row, ++x, "Removed", 4200);
		}
		this.endTitles(row, x, false);
	}

	@Override
	public void module(ExportedModule module) {
		Cell cell;
		int x = -1;
		Row row = this.sheet.createRow(++this.y);
		row.createCell(++x).setCellValue(module.deviceId());
		row.createCell(++x).setCellValue(module.deviceName());
		row.createCell(++x).setCellValue(module.slot());
		row.createCell(++x).setCellValue(module.partNumber());
		row.createCell(++x).setCellValue(module.serialNumber());
		if (this.history) {
			cell = row.createCell(++x);
			cell.setCellValue(module.firstSeenDate());
			cell.setCellStyle(this.datetimeCellStyle);
			cell = row.createCell(++x);
			cell.setCellValue(module.lastSeenDate());
			cell.setCellStyle(this.datetimeCellStyle);
			cell = row.createCell(++x, CellType.BOOLEAN);
			cell.setCellValue(module.removed());
		}
	}

	@Override
	public void complianceStart() {
		Row row = this.startSheet("Configuration Compliance");
		int x = -1;
		this.title(row, ++x, "Device ID", 2200);
		this.title(row, ++x, "Device Name", 5000);
		this.title(row, ++x, "Policy", 5000);
		this.title(row, ++x, "Rule", 8000);
		this.title(row, ++x, "Check Date", 4200);
		this.title(row, ++x, "Result", 4000);
		this.endTitles(row, x, true);
	}

	@Override
	public void complianceResult(PolicyRuleDevice checkResult) {
		int x = -1;
		Row row = this.sheet.createRow(++this.y);
		row.createCell(++x).setCellValue(checkResult.device().id());
		row.createCell(++x).setCellValue(checkResult.device().name());
		row.createCell(++x).setCellValue(checkResult.policyName());
		row.createCell(++x).setCellValue(checkResult.ruleName());
		row.createCell(++x).setCellValue(checkResult.checkDate());
		row.getCell(x).setCellStyle(this.datetimeCellStyle);
		row.createCell(++x).setCellValue(checkResult.result().toString());
	}

	@Override
	public void groupsStart(boolean exportCompliance) {
		this.compliance = exportCompliance;
		Row row = this.startSheet("Device Groups");
		int x = -1;
		this.title(row, ++x, "Group ID", 2200);
		this.title(row, ++x, "Group Name", 5000);
		this.title(row, ++x, "Folder", 8000);
		this.title(row, ++x, "Device Count", 4000);
		if (exportCompliance) {
			this.title(row, ++x, "Config Compliant Count", 6000);
			this.title(row, ++x, "Config Compliance", 5000);
		}
		this.endTitles(row, x, false);
	}

	@Override
	public void group(GroupConfigComplianceStat stat) {
		Row row = this.sheet.createRow(++this.y);
		int x = -1;
		row.createCell(++x).setCellValue(stat.groupId());
		row.createCell(++x).setCellValue(stat.groupName());
		row.createCell(++x).setCellValue(stat.groupFolder());
		row.createCell(++x).setCellValue(stat.deviceCount());
		if (this.compliance) {
			row.createCell(++x).setCellValue(stat.compliantDeviceCount());
			row.createCell(++x).setCellFormula(String.format("%s%d / %s%d",
				CellReference.convertNumToColString(x - 1), this.y + 1,
				CellReference.convertNumToColString(x - 2), this.y + 1));
			row.getCell(x).setCellStyle(this.percentCellStyle);
		}
	}

	@Override
	public void membershipsStart() {
		Row row = this.startSheet("Group Memberships");
		int x = -1;
		this.title(row, ++x, "Device ID", 2200);
		this.title(row, ++x, "Device Name", 5000);
		this.title(row, ++x, "Group ID", 2200);
		this.title(row, ++x, "Group Name", 5000);
		this.endTitles(row, x, false);
	}

	@Override
	public void membership(ExportedMembership membership) {
		Row row = this.sheet.createRow(++this.y);
		int x = -1;
		row.createCell(++x).setCellValue(membership.deviceId());
		row.createCell(++x).setCellValue(membership.deviceName());
		row.createCell(++x).setCellValue(membership.groupId());
		row.createCell(++x).setCellValue(membership.groupName());
	}

	@Override
	public void attributesStart() {
		Row row = this.startSheet("Device Attributes");
		int x = -1;
		this.title(row, ++x, "Device ID", 2200);
		this.title(row, ++x, "Device Name", 5000);
		this.title(row, ++x, "Attribute Name", 5000);
		this.title(row, ++x, "Attribute Value", 7000);
		this.endTitles(row, x, true);
	}

	@Override
	public void attribute(ExportedAttribute attribute) {
		int x = -1;
		Row row = this.sheet.createRow(++this.y);
		row.createCell(++x).setCellValue(attribute.deviceId());
		row.createCell(++x).setCellValue(attribute.deviceName());
		Cell titleCell = row.createCell(++x);
		if (attribute.title() == null) {
			// The driver doesn't define the attribute: the row stops there, with an empty title
			return;
		}
		titleCell.setCellValue(attribute.title());
		row.createCell(++x).setCellValue(attribute.value());
	}
}
