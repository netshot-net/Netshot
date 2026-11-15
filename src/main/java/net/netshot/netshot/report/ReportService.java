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
package net.netshot.netshot.report;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jooq.DSLContext;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.report.ReportExportSink.ExportedAttribute;
import net.netshot.netshot.report.ReportExportSink.ExportedDevice;
import net.netshot.netshot.report.ReportExportSink.ExportedInterface;
import net.netshot.netshot.report.ReportExportSink.ExportedMembership;
import net.netshot.netshot.report.ReportExportSink.ExportedModule;
import net.netshot.netshot.report.ReportRepository.AttributeToExport;

/**
 * The report use cases: statistics on the configuration changes, the compliance and the hardware
 * support, lists of the devices behind these statistics, and the data export.
 *
 * <p>All of them are read-only. Every query failure is reported as
 * {@link ErrorCode#NETSHOT_DATABASE_ACCESS_ERROR}, except for the export, which lets any
 * exception through.</p>
 */
@Slf4j
public class ReportService {

	/** The size of the pages read by the export. */
	private static final int EXPORT_PAGE_SIZE = 1000;

	private final Transactions transactions;
	private final ReportRepository repository;
	private final DriverRegistry driverRegistry;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the report repository
	 * @param driverRegistry the loaded device drivers
	 */
	public ReportService(Transactions transactions, ReportRepository repository, DriverRegistry driverRegistry) {
		this.transactions = transactions;
		this.repository = repository;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Exports the devices and optionally their interfaces, inventory, compliance, groups and
	 * attributes. The data is read in one read-only transaction and pushed to the sink as it is
	 * read, page by page; any exception, from the queries or from the sink, goes through.
	 *
	 * @param selection what to export
	 * @param sink the receiver of the data
	 */
	public void export(ReportExportSelection selection, ReportExportSink sink) {
		Set<Long> domains = selection.domains();
		Set<Long> groups = selection.groups();
		Set<Long> devices = selection.devices();
		this.transactions.read(dsl -> {
			sink.summary(domains.isEmpty() ? null : this.repository.findDomainNames(dsl, domains),
				groups.isEmpty() ? null : this.repository.findGroupNames(dsl, groups));
			this.exportDevices(dsl, selection, sink);
			if (selection.interfaces()) {
				log.debug("Exporting interface data");
				sink.interfacesStart();
				for (ExportedInterface networkInterface : this.repository.findInterfacesToExport(dsl, domains, groups, devices)) {
					sink.networkInterface(networkInterface);
				}
			}
			if (selection.inventory()) {
				this.exportInventory(dsl, selection, sink);
			}
			if (selection.compliance()) {
				log.debug("Exporting compliance data");
				List<PolicyRuleDevice> checkResults = this.repository.findPolicyRuleDevices(dsl, domains, groups, devices,
					new HashSet<Long>(), new HashSet<>(List.of(CheckResult.ResultOption.CONFORMING,
						CheckResult.ResultOption.NONCONFORMING, CheckResult.ResultOption.EXEMPTED)));
				sink.complianceStart();
				for (PolicyRuleDevice checkResult : checkResults) {
					sink.complianceResult(checkResult);
				}
			}
			if (selection.deviceGroups()) {
				log.debug("Exporting group data");
				List<GroupConfigComplianceStat> groupConfigComplianceStats = this.repository
					.findGroupConfigComplianceStats(dsl, domains, groups, devices, new HashSet<>());
				sink.groupsStart(selection.compliance());
				for (GroupConfigComplianceStat stat : groupConfigComplianceStats) {
					sink.group(stat);
				}
				log.debug("Exporting group memberships");
				sink.membershipsStart();
				for (ExportedMembership membership : this.repository.findMembershipsToExport(dsl, domains, groups, devices)) {
					sink.membership(membership);
				}
			}
			if (selection.deviceDriverAttributes()) {
				this.exportAttributes(dsl, selection, sink);
			}
			return null;
		});
	}

	private void exportDevices(DSLContext dsl, ReportExportSelection selection, ReportExportSink sink) {
		sink.devicesStart(selection.compliance(), selection.locations());
		for (int n = 0; true; n += EXPORT_PAGE_SIZE) {
			List<ExportedDevice> devices = this.repository.findDevicesToExport(dsl, selection.domains(), selection.groups(),
				selection.devices(), new Page(n, EXPORT_PAGE_SIZE));
			if (devices.size() == 0) {
				break;
			}
			for (ExportedDevice device : devices) {
				sink.device(device);
			}
		}
	}

	private void exportInventory(DSLContext dsl, ReportExportSelection selection, ReportExportSink sink) {
		log.debug("Exporting device inventory");
		sink.inventoryStart(selection.inventoryHistory());
		for (int n = 0; true; n += EXPORT_PAGE_SIZE) {
			List<ExportedModule> modules = this.repository.findModulesToExport(dsl, selection.inventoryHistory(),
				selection.domains(), selection.groups(), selection.devices(), new Page(n, EXPORT_PAGE_SIZE));
			if (modules.size() == 0) {
				break;
			}
			for (ExportedModule module : modules) {
				sink.module(module);
			}
		}
	}

	private void exportAttributes(DSLContext dsl, ReportExportSelection selection, ReportExportSink sink) {
		log.debug("Exporting driver-specific device attributes");
		sink.attributesStart();
		for (int n = 0; true; n += EXPORT_PAGE_SIZE) {
			List<AttributeToExport> attributes = this.repository.findDeviceAttributesToExport(dsl,
				selection.domains(), selection.groups(), selection.devices(), new Page(n, EXPORT_PAGE_SIZE));
			if (attributes.size() == 0) {
				break;
			}
			for (AttributeToExport attribute : attributes) {
				try {
					String value = attribute.value().toString();
					DeviceDriver driver = this.driverRegistry.forDevice(attribute.deviceId(), attribute.driver());
					AttributeDefinition definition = driver.getAttributeDefinition(AttributeLevel.DEVICE, attribute.name());
					sink.attribute(new ExportedAttribute(attribute.deviceId(), attribute.deviceName(),
						definition == null ? null : definition.getTitle(), value));
				}
				catch (Exception e) {
					log.warn("Error while exporting attribute (device {}, name {})... skipping",
						attribute.deviceId(), attribute.name(), e);
				}
			}
		}
	}
}
