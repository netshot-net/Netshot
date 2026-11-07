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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
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
	 * Reads in a read-only transaction, reporting a query failure as a database access error.
	 *
	 * @param <T> the type of the result
	 * @param logMessage the message logged on failure
	 * @param work the reads
	 * @return the result
	 */
	private <T> T read(String logMessage, Function<DSLContext, T> work) {
		try {
			return this.transactions.read(work);
		}
		catch (DataAccessException e) {
			log.error(logMessage, e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to get the stats");
		}
	}

	/**
	 * Counts the configuration changes of each of the last 7 days.
	 *
	 * @param jsTimeZone the name of the time zone to base the days on (the server one if it can't be read)
	 * @return the counts, oldest day first
	 */
	public List<DailyChangeCount> getLast7DaysChanges(String jsTimeZone) {
		TimeZone timeZone = TimeZone.getDefault();
		try {
			timeZone = TimeZone.getTimeZone(jsTimeZone);
		}
		catch (Exception e) {
			log.warn("Unable to parse timezone '{}'", jsTimeZone);
		}
		Calendar today = Calendar.getInstance(timeZone);
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		return this.read("Unable to get the stats.", dsl -> {
			List<DailyChangeCount> stats = new ArrayList<>();
			for (int d = 7; d > 0; d--) {
				Calendar dayStart = (Calendar) today.clone();
				Calendar dayEnd = (Calendar) today.clone();
				dayStart.add(Calendar.DATE, -d + 1);
				dayEnd.add(Calendar.DATE, -d + 2);
				Long changeCount = this.repository.countConfigChanges(dsl, dayStart.getTime(), dayEnd.getTime());
				stats.add(new DailyChangeCount(changeCount == null ? 0 : changeCount, dayStart.getTime()));
			}
			return stats;
		});
	}

	/**
	 * Gets the configuration compliance of the device groups.
	 *
	 * @param domains the domains to filter the devices on (empty: any)
	 * @param deviceGroups the groups to report (empty: any)
	 * @param policies the policies to take into account (empty: any)
	 * @return the statistics
	 */
	public List<GroupConfigComplianceStat> getGroupConfigComplianceStats(Set<Long> domains, Set<Long> deviceGroups,
		Set<Long> policies) {
		return this.read("Unable to get the stats.",
			dsl -> this.repository.findGroupConfigComplianceStats(dsl, domains, deviceGroups, policies));
	}

	/**
	 * Gets the hardware support status of the devices.
	 *
	 * @param domains the domains to filter the devices on (empty: any)
	 * @param deviceGroups the groups to filter the devices on (empty: any)
	 * @return the device counts by end-of-sale and end-of-life dates
	 */
	public HardwareSupportStats getHardwareSupportStats(Set<Long> domains, Set<Long> deviceGroups) {
		return this.read("Unable to get the stats.", dsl -> new HardwareSupportStats(
			this.repository.findHardwareSupportCounts(dsl, EndOfSupport.SALE, domains, deviceGroups),
			this.repository.findHardwareSupportCounts(dsl, EndOfSupport.LIFE, domains, deviceGroups)));
	}

	/**
	 * Gets the software compliance of the device groups.
	 *
	 * @param domains the domains to filter the devices on (empty: any)
	 * @return the statistics
	 */
	public List<GroupSoftwareComplianceStat> getGroupSoftwareComplianceStats(Set<Long> domains) {
		return this.read("Unable to get the stats.",
			dsl -> this.repository.findGroupSoftwareComplianceStats(dsl, domains));
	}

	/**
	 * Gets the configuration compliance results of the devices.
	 *
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @param policies the policies to filter on (empty: any)
	 * @param results the results to filter on (empty: any)
	 * @return the results
	 */
	public List<PolicyRuleDevice> getConfigComplianceDeviceStatuses(Set<Long> domains, Set<Long> groups,
		Set<Long> policies, Set<CheckResult.ResultOption> results) {
		return this.read("Unable to get the devices.",
			dsl -> this.repository.findPolicyRuleDevices(dsl, domains, groups, policies, results));
	}

	/**
	 * Gets the non conforming rules of the devices of a group.
	 *
	 * @param groupId the group ID
	 * @param domains the domains to filter on (empty: any)
	 * @param policies the policies to filter on (empty: any)
	 * @return the non conforming results
	 */
	public List<PolicyRuleDevice> getGroupConfigNonCompliantDevices(Long groupId, Set<Long> domains,
		Set<Long> policies) {
		return this.read("Unable to get the devices.",
			dsl -> this.repository.findNonCompliantDevices(dsl, groupId, domains, policies));
	}

	/**
	 * Gets the devices reaching a hardware support milestone on a given day.
	 *
	 * @param type "eos" (end of sale) or "eol" (end of life)
	 * @param date the day (milliseconds since 1970), or 0 for the devices without date
	 * @param domains the domains to filter on (empty: any)
	 * @param deviceGroups the groups to filter on (empty: any)
	 * @return the devices
	 * @throws NetshotException if the type is not valid
	 */
	public List<DeviceSummary> getHardwareSupportDevices(String type, long date, Set<Long> domains,
		Set<Long> deviceGroups) {
		EndOfSupport milestone = EndOfSupport.fromCode(type);
		if (milestone == null) {
			log.warn("Invalid requested EoX type.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, "Invalid type, expected eos or eol.");
		}
		Date eoxDate = new Date(date);
		return this.read("Unable to get the devices.", dsl -> this.repository.findDevicesByEndOfSupport(dsl,
			milestone, date == 0 ? null : eoxDate, domains, deviceGroups));
	}

	/**
	 * Gets the devices of a group at a software compliance level.
	 *
	 * @param groupId the group ID
	 * @param level the level, case insensitive (unknown if it is not a valid level)
	 * @param domains the domains to filter on (empty: any)
	 * @return the devices
	 */
	public List<SoftwareLevelDevice> getGroupDevicesBySoftwareLevel(Long groupId, String level, Set<Long> domains) {
		log.debug("Group {} devices by software level {}.", groupId, level);
		ConformanceLevel filterLevel = ConformanceLevel.UNKNOWN;
		for (ConformanceLevel l : ConformanceLevel.values()) {
			if (l.toString().equalsIgnoreCase(level)) {
				filterLevel = l;
				break;
			}
		}
		ConformanceLevel conformanceLevel = filterLevel;
		return this.read("Unable to get the devices.",
			dsl -> this.repository.findDevicesBySoftwareLevel(dsl, groupId, conformanceLevel, domains));
	}

	/**
	 * Gets the devices without successful snapshot over a number of days.
	 *
	 * @param days the number of days (3 if it is missing or lower than 1)
	 * @param domains the domains to filter on (empty: any)
	 * @param page the window on the result
	 * @return the devices, the most recent success first
	 */
	public List<AccessFailureDevice> getAccessFailureDevices(Integer days, Set<Long> domains, Page page) {
		Integer period = days;
		if (period == null || period < 1) {
			log.warn("Invalid number of days {} to find the unreachable devices, using 3.", period);
			period = 3;
		}
		Calendar when = Calendar.getInstance();
		when.add(Calendar.DATE, -period);
		return this.read("Unable to get the devices.",
			dsl -> this.repository.findAccessFailureDevices(dsl, when.getTime(), domains, page));
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
