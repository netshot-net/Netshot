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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Function;

import org.hibernate.Session;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.NetworkAddress;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Jooq;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.report.ReportExportSink.ExportedAddress;
import net.netshot.netshot.report.ReportExportSink.ExportedAttribute;
import net.netshot.netshot.report.ReportExportSink.ExportedDevice;
import net.netshot.netshot.report.ReportExportSink.ExportedInterface;
import net.netshot.netshot.report.ReportExportSink.ExportedMembership;
import net.netshot.netshot.report.ReportExportSink.ExportedModule;

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
	private <T> T readDsl(String logMessage, Function<DSLContext, T> work) {
		try {
			return this.transactions.readDsl(work);
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
		return this.readDsl("Unable to get the stats.", dsl -> {
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
		return this.readDsl("Unable to get the stats.",
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
		return this.readDsl("Unable to get the stats.", dsl -> new HardwareSupportStats(
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
		return this.readDsl("Unable to get the stats.",
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
		return this.readDsl("Unable to get the devices.",
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
		return this.readDsl("Unable to get the devices.",
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
		return this.readDsl("Unable to get the devices.", dsl -> this.repository.findDevicesByEndOfSupport(dsl,
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
		return this.readDsl("Unable to get the devices.",
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
		return this.readDsl("Unable to get the devices.",
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
		this.transactions.read(session -> {
			sink.summary(this.selectionNames(session, domains, true), this.selectionNames(session, groups, false));
			this.exportDevices(session, selection, sink);
			if (selection.interfaces()) {
				this.exportInterfaces(session, selection, sink);
			}
			if (selection.inventory()) {
				this.exportInventory(session, selection, sink);
			}
			if (selection.compliance()) {
				log.debug("Exporting compliance data");
				List<PolicyRuleDevice> checkResults = this.repository.findPolicyRuleDevices(Jooq.dsl(session), domains, groups,
					new HashSet<Long>(), new HashSet<>(List.of(CheckResult.ResultOption.CONFORMING,
						CheckResult.ResultOption.NONCONFORMING, CheckResult.ResultOption.EXEMPTED)));
				sink.complianceStart();
				for (PolicyRuleDevice checkResult : checkResults) {
					sink.complianceResult(checkResult);
				}
				session.clear();
			}
			if (selection.deviceGroups()) {
				this.exportGroups(session, selection, sink);
			}
			if (selection.deviceDriverAttributes()) {
				this.exportAttributes(session, selection, sink);
			}
			return null;
		});
	}

	private List<String> selectionNames(Session session, Set<Long> ids, boolean domain) {
		if (ids.isEmpty()) {
			return null;
		}
		List<String> names = new ArrayList<>();
		if (domain) {
			for (Domain deviceDomain : this.repository.findDomains(session, ids)) {
				names.add(String.format("%s (%d)", deviceDomain.getName(), deviceDomain.getId()));
			}
		}
		else {
			for (DeviceGroup group : this.repository.findDeviceGroups(session, ids)) {
				names.add(String.format("%s (%d)", group.getName(), group.getId()));
			}
		}
		return names;
	}

	private void exportDevices(Session session, ReportExportSelection selection, ReportExportSink sink) {
		sink.devicesStart(selection.compliance(), selection.locations());
		for (int n = 0; true; n += EXPORT_PAGE_SIZE) {
			List<Device> devices = this.repository.findDevicesToExport(session, selection.domains(), selection.groups(),
				new Page(n, EXPORT_PAGE_SIZE));
			if (devices.size() == 0) {
				break;
			}
			for (Device device : devices) {
				sink.device(new ExportedDevice(device.getId(), device.getName(), device.getMgmtAddress(),
					device.getMgmtDomain().getName(), device.getNetworkClass(), device.getFamily(),
					device.getCreatedDate(), device.getChangeDate(), device.getSoftwareVersion(),
					device.getSoftwareLevel(), device.getEosDate(), device.getEolDate(), device.getLocation(),
					device.getContact()));
			}
		}
	}

	private void exportInterfaces(Session session, ReportExportSelection selection, ReportExportSink sink) {
		log.debug("Exporting interface data");
		List<NetworkInterface> networkInterfaces = this.repository.findInterfacesToExport(session,
			selection.domains(), selection.groups());
		sink.interfacesStart();
		for (NetworkInterface networkInterface : networkInterfaces) {
			Device device = networkInterface.getDevice();
			List<ExportedAddress> addresses = new ArrayList<>();
			for (NetworkAddress address : networkInterface.getIpAddresses()) {
				addresses.add(new ExportedAddress(address.getIp(), address.getPrefixLength(), address.getAddressUsage()));
			}
			sink.networkInterface(new ExportedInterface(device.getId(), device.getName(),
				networkInterface.getVirtualDevice(), networkInterface.getInterfaceName(),
				networkInterface.getDescription(), networkInterface.getVrfInstance(), networkInterface.getMacAddress(),
				networkInterface.isEnabled(), networkInterface.isLevel3(), addresses));
		}
		session.clear();
	}

	private void exportInventory(Session session, ReportExportSelection selection, ReportExportSink sink) {
		log.debug("Exporting device inventory");
		sink.inventoryStart(selection.inventoryHistory());
		for (int n = 0; true; n += EXPORT_PAGE_SIZE) {
			List<Module> modules = this.repository.findModulesToExport(session, selection.inventoryHistory(),
				selection.domains(), selection.groups(), new Page(n, EXPORT_PAGE_SIZE));
			if (modules.size() == 0) {
				break;
			}
			for (Module module : modules) {
				Device device = module.getDevice();
				sink.module(new ExportedModule(device.getId(), device.getName(), module.getSlot(),
					module.getPartNumber(), module.getSerialNumber(), module.getFirstSeenDate(),
					module.getLastSeenDate(), module.isRemoved()));
			}
			session.clear();
		}
	}

	private void exportGroups(Session session, ReportExportSelection selection, ReportExportSink sink) {
		{
			log.debug("Exporting group data");
			List<GroupConfigComplianceStat> groupConfigComplianceStats = this.repository
				.findGroupConfigComplianceStats(Jooq.dsl(session), selection.domains(), selection.groups(), new HashSet<>());
			sink.groupsStart(selection.compliance());
			for (GroupConfigComplianceStat stat : groupConfigComplianceStats) {
				sink.group(stat);
			}
			session.clear();
		}
		{
			log.debug("Exporting group memberships");
			sink.membershipsStart();
			for (int n = 0; true; n += EXPORT_PAGE_SIZE) {
				List<Device> devices = this.repository.findDevicesWithGroupsToExport(session, selection.domains(),
					selection.groups(), new Page(n, EXPORT_PAGE_SIZE));
				if (devices.size() == 0) {
					break;
				}
				for (Device device : devices) {
					for (DeviceGroup group : device.getOwnerGroups()) {
						if (group.isHiddenFromReports()) {
							continue;
						}
						sink.membership(new ExportedMembership(device.getId(), device.getName(), group.getId(),
							group.getName()));
					}
				}
				session.clear();
			}
		}
	}

	private void exportAttributes(Session session, ReportExportSelection selection, ReportExportSink sink) {
		log.debug("Exporting driver-specific device attributes");
		sink.attributesStart();
		for (int n = 0; true; n += EXPORT_PAGE_SIZE) {
			List<DeviceAttribute> attributes = this.repository.findDeviceAttributesToExport(session,
				selection.domains(), selection.groups(), new Page(n, EXPORT_PAGE_SIZE));
			if (attributes.size() == 0) {
				break;
			}
			for (DeviceAttribute attribute : attributes) {
				try {
					String value = attribute.getData().toString();
					Device device = attribute.getDevice();
					DeviceDriver driver = this.driverRegistry.forDevice(device);
					AttributeDefinition definition = driver.getAttributeDefinition(AttributeLevel.DEVICE, attribute.getName());
					sink.attribute(new ExportedAttribute(device.getId(), device.getName(),
						definition == null ? null : definition.getTitle(), value));
				}
				catch (Exception e) {
					log.warn("Error while exporting attribute (ID {}, name {})... skipping",
						attribute.getId(), attribute.getName(), e);
				}
			}
			session.clear();
		}
	}
}
