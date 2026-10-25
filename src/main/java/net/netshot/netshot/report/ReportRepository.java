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

import static net.netshot.netshot.platform.database.schema.Tables.CHECK_RESULT;
import static net.netshot.netshot.platform.database.schema.Tables.CONFIG;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_ATTRIBUTE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP_CACHED_DEVICES;
import static net.netshot.netshot.platform.database.schema.Tables.DOMAIN;
import static net.netshot.netshot.platform.database.schema.Tables.LONG_TEXT_CONFIGURATION;
import static net.netshot.netshot.platform.database.schema.Tables.MODULE;
import static net.netshot.netshot.platform.database.schema.Tables.NETWORK_INTERFACE;
import static net.netshot.netshot.platform.database.schema.Tables.POLICY;
import static net.netshot.netshot.platform.database.schema.Tables.RULE;
import static net.netshot.netshot.platform.database.schema.Tables.TASK;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;

import net.netshot.netshot.compliance.CheckResult.ResultOption;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.net.NetworkAddress;
import net.netshot.netshot.report.ReportExportSink.ExportedAddress;
import net.netshot.netshot.report.ReportExportSink.ExportedDevice;
import net.netshot.netshot.report.ReportExportSink.ExportedInterface;
import net.netshot.netshot.report.ReportExportSink.ExportedMembership;
import net.netshot.netshot.report.ReportExportSink.ExportedModule;
import net.netshot.netshot.task.Task;

/**
 * The queries of the reports (read-only). Every method takes the jOOQ {@link DSLContext} first, none
 * opens a transaction.
 *
 * <p>The statistics, the device lists and the data export are projected into records
 * ({@link DeviceSummary} and the records of this package).</p>
 */
public class ReportRepository {

	/**
	 * Counts the configuration changes in a period.
	 *
	 * @param dsl the jOOQ context
	 * @param start the start of the period (included)
	 * @param end the end of the period (excluded)
	 * @return the number of changes
	 */
	public Long countConfigChanges(DSLContext dsl, Date start, Date end) {
		return dsl.selectCount().from(CONFIG)
			.where(CONFIG.CHANGE_DATE.ge(start.toInstant()).and(CONFIG.CHANGE_DATE.lt(end.toInstant())))
			.fetchOne(0, Long.class);
	}

	/**
	 * Finds the configuration compliance of the groups not hidden from the reports.
	 *
	 * @param dsl the jOOQ context
	 * @param domains the domains to count the devices of (empty: any)
	 * @param deviceGroups the groups to report (empty: any)
	 * @param policies the policies to take into account (empty: any)
	 * @return the statistics
	 */
	public List<GroupConfigComplianceStat> findGroupConfigComplianceStats(DSLContext dsl, Set<Long> domains,
		Set<Long> deviceGroups, Set<Long> policies) {
		var g = DEVICE_GROUP.as("g");
		var ccr = CHECK_RESULT.as("ccr");
		var r = RULE.as("r");
		Condition nonConforming = DSL.field(DSL.select(DSL.count(ccr.RESULT)).from(ccr).join(r).on(r.ID.eq(ccr.RULE))
			.where(ccr.DEVICE.eq(DEVICE.ID).and(policies.isEmpty() ? DSL.noCondition() : r.POLICY.in(policies))
				.and(ccr.RESULT.eq(ResultOption.NONCONFORMING.name())))).eq(0);
		return dsl.select(g.ID, g.NAME, g.FOLDER,
				groupDeviceCount(g, inDomains(domains).and(nonConforming)),
				groupDeviceCount(g, inDomains(domains)))
			.from(g)
			.where((deviceGroups.isEmpty() ? DSL.noCondition() : g.ID.in(deviceGroups)).and(DSL.condition("{0} <> true", g.HIDDEN_FROM_REPORTS)))
			.fetch(rec -> new GroupConfigComplianceStat(rec.value1(), rec.value2(), rec.value3(), rec.value4(), rec.value5()));
	}

	/**
	 * Counts the devices in production by date of a hardware support milestone.
	 *
	 * @param dsl the jOOQ context
	 * @param milestone the milestone
	 * @param domains the domains to count the devices of (empty: any)
	 * @param deviceGroups the groups to count the devices of (empty: any)
	 * @return the counts
	 */
	public List<HardwareSupportCount> findHardwareSupportCounts(DSLContext dsl, EndOfSupport milestone,
		Set<Long> domains, Set<Long> deviceGroups) {
		Field<Instant> date = milestoneField(milestone);
		return dsl.select(date, DSL.count(DEVICE.ID).cast(Long.class))
			.from(DEVICE)
			.where(DEVICE.STATUS.eq(Device.Status.INPRODUCTION.name()).and(inDomains(domains)).and(inGroups(deviceGroups)))
			.groupBy(date)
			.fetch(rec -> new HardwareSupportCount(toDate(rec.value1()), rec.value2()));
	}

	/**
	 * Finds the software compliance of the groups not hidden from the reports.
	 *
	 * @param dsl the jOOQ context
	 * @param domains the domains to count the devices of (empty: any)
	 * @return the statistics
	 */
	public List<GroupSoftwareComplianceStat> findGroupSoftwareComplianceStats(DSLContext dsl, Set<Long> domains) {
		var g = DEVICE_GROUP.as("g");
		return dsl.select(g.ID, g.NAME,
				groupDeviceCount(g, DEVICE.SOFTWARE_LEVEL.eq(ConformanceLevel.GOLD.name()).and(inDomains(domains))),
				groupDeviceCount(g, DEVICE.SOFTWARE_LEVEL.eq(ConformanceLevel.SILVER.name()).and(inDomains(domains))),
				groupDeviceCount(g, DEVICE.SOFTWARE_LEVEL.eq(ConformanceLevel.BRONZE.name()).and(inDomains(domains))),
				groupDeviceCount(g, inDomains(domains)))
			.from(g)
			.where(DSL.condition("{0} <> true", g.HIDDEN_FROM_REPORTS))
			.fetch(rec -> new GroupSoftwareComplianceStat(rec.value1(), rec.value2(), rec.value3(), rec.value4(), rec.value5(), rec.value6()));
	}

	/**
	 * Finds the compliance results of the devices in production.
	 *
	 * @param dsl the jOOQ context
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @param policies the policies to filter on (empty: any)
	 * @param results the results to filter on (empty: any)
	 * @return the results
	 */
	public List<PolicyRuleDevice> findPolicyRuleDevices(DSLContext dsl, Set<Long> domains, Set<Long> groups,
		Set<Long> policies, Set<ResultOption> results) {
		return dsl.select(policyRuleDeviceFields())
			.from(DEVICE)
			.join(CHECK_RESULT).on(CHECK_RESULT.DEVICE.eq(DEVICE.ID))
			.join(RULE).on(RULE.ID.eq(CHECK_RESULT.RULE))
			.join(POLICY).on(POLICY.ID.eq(RULE.POLICY))
			.where(DEVICE.STATUS.eq(Device.Status.INPRODUCTION.name())
				.and(inDomains(domains))
				.and(inGroups(groups))
				.and(policies.isEmpty() ? DSL.noCondition() : POLICY.ID.in(policies))
				.and(results.isEmpty() ? DSL.noCondition() : CHECK_RESULT.RESULT.in(results.stream().map(Enum::name).toList())))
			.orderBy(DEVICE.ID, POLICY.ID, RULE.ID)
			.fetch(ReportRepository::toPolicyRuleDevice);
	}

	/**
	 * Finds the non conforming rules of the devices in production of a group.
	 *
	 * @param dsl the jOOQ context
	 * @param groupId the group ID
	 * @param domains the domains to filter on (empty: any)
	 * @param policies the policies to filter on (empty: any)
	 * @return the non conforming results
	 */
	public List<PolicyRuleDevice> findNonCompliantDevices(DSLContext dsl, Long groupId, Set<Long> domains,
		Set<Long> policies) {
		return dsl.select(policyRuleDeviceFields())
			.from(DEVICE)
			.join(DEVICE_GROUP_CACHED_DEVICES).on(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(DEVICE.ID))
			.join(CHECK_RESULT).on(CHECK_RESULT.DEVICE.eq(DEVICE.ID))
			.join(RULE).on(RULE.ID.eq(CHECK_RESULT.RULE))
			.join(POLICY).on(POLICY.ID.eq(RULE.POLICY))
			.where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(groupId)
				.and(CHECK_RESULT.RESULT.eq(ResultOption.NONCONFORMING.name()))
				.and(DEVICE.STATUS.eq(Device.Status.INPRODUCTION.name()))
				.and(inDomains(domains))
				.and(policies.isEmpty() ? DSL.noCondition() : POLICY.ID.in(policies)))
			.orderBy(DEVICE.ID, POLICY.ID, RULE.ID)
			.fetch(ReportRepository::toPolicyRuleDevice);
	}

	/**
	 * Finds the devices in production whose hardware support milestone is on a given day (or has no date).
	 *
	 * @param dsl the jOOQ context
	 * @param milestone the milestone
	 * @param date the day, or null to find the devices without date for the milestone
	 * @param domains the domains to filter on (empty: any)
	 * @param deviceGroups the groups to filter on (empty: any)
	 * @return the devices
	 */
	public List<DeviceSummary> findDevicesByEndOfSupport(DSLContext dsl, EndOfSupport milestone, Date date,
		Set<Long> domains, Set<Long> deviceGroups) {
		Field<Instant> field = milestoneField(milestone);
		Condition condition = date == null
			? field.isNull()
			: DSL.condition("cast({0} as date) = cast({1} as date)", field, DSL.val(date.toInstant(), field.getDataType()));
		return dsl.select(DeviceSummary.FIELDS)
			.from(DEVICE)
			.where(condition.and(DEVICE.STATUS.eq(Device.Status.INPRODUCTION.name())).and(inDomains(domains)).and(inGroups(deviceGroups)))
			.fetch(DeviceSummary::fromRecord);
	}

	/**
	 * Finds the devices in production of a group at a given software compliance level.
	 *
	 * @param dsl the jOOQ context
	 * @param groupId the group ID
	 * @param level the software level
	 * @param domains the domains to filter on (empty: any)
	 * @return the devices
	 */
	public List<SoftwareLevelDevice> findDevicesBySoftwareLevel(DSLContext dsl, Long groupId, ConformanceLevel level,
		Set<Long> domains) {
		List<SelectField<?>> fields = new ArrayList<>(DeviceSummary.FIELDS);
		fields.add(DEVICE.SOFTWARE_VERSION);
		return dsl.select(fields)
			.from(DEVICE)
			.join(DEVICE_GROUP_CACHED_DEVICES).on(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(DEVICE.ID))
			.where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(groupId)
				.and(DEVICE.SOFTWARE_LEVEL.eq(level.name()))
				.and(DEVICE.STATUS.eq(Device.Status.INPRODUCTION.name()))
				.and(inDomains(domains)))
			.fetch(rec -> new SoftwareLevelDevice(DeviceSummary.fromRecord(rec), rec.get(DEVICE.SOFTWARE_VERSION)));
	}

	/**
	 * Finds the devices in production without successful snapshot since a date.
	 *
	 * @param dsl the jOOQ context
	 * @param when the date
	 * @param domains the domains to filter on (empty: any)
	 * @param page the window on the result
	 * @return the devices, the most recent success first
	 */
	public List<AccessFailureDevice> findAccessFailureDevices(DSLContext dsl, Date when, Set<Long> domains, Page page) {
		var t = TASK.as("t");
		Field<Instant> lastSuccess = DSL.max(DSL.when(t.STATUS.eq(Task.Status.SUCCESS.name()), t.EXECUTION_DATE));
		Field<Instant> lastFailure = DSL.max(DSL.when(t.STATUS.eq(Task.Status.FAILURE.name()), t.EXECUTION_DATE));
		List<SelectField<?>> fields = new ArrayList<>(DeviceSummary.FIELDS);
		fields.add(lastSuccess.as("last_success"));
		fields.add(lastFailure.as("last_failure"));
		return page.apply(dsl.select(fields)
				.from(DEVICE)
				.leftJoin(t).on(t.DEVICE.eq(DEVICE.ID).and(t.DTYPE.eq("TakeSnapshotTask")))
				.where(DEVICE.STATUS.eq(Device.Status.INPRODUCTION.name()).and(inDomains(domains)))
				.groupBy(DEVICE.ID, DEVICE.NAME, DEVICE.FAMILY, DEVICE.MGMT_ADDRESS, DEVICE.STATUS, DEVICE.DRIVER, DEVICE.EOL_DATE,
					DEVICE.EOS_DATE, DEVICE.SOFTWARE_LEVEL, DEVICE.NETWORK_CLASS)
				.having(lastSuccess.lt(when.toInstant()).or(lastSuccess.isNull()))
				.orderBy(lastSuccess.desc().nullsLast()))
			.fetch(rec -> new AccessFailureDevice(DeviceSummary.fromRecord(rec),
				toDate(rec.get("last_success", Instant.class)), toDate(rec.get("last_failure", Instant.class))));
	}

	// ---------------------------------------------------------------------
	// Data export
	// ---------------------------------------------------------------------

	/**
	 * The condition on the devices selected for an export.
	 *
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @return the condition on {@code DEVICE}
	 */
	private static Condition exportedDevices(Set<Long> domains, Set<Long> groups) {
		Condition condition = DSL.noCondition();
		if (domains.size() > 0) {
			condition = condition.and(DEVICE.MGMT_DOMAIN.in(domains));
		}
		if (groups.size() > 0) {
			condition = condition.and(DEVICE.ID.in(DSL.select(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES).from(DEVICE_GROUP_CACHED_DEVICES)
				.where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.in(groups))));
		}
		return condition;
	}

	/**
	 * Finds the names of domains, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param domainIds the IDs
	 * @return the domains, as "name (ID)" (the unknown IDs are ignored)
	 */
	public List<String> findDomainNames(DSLContext dsl, Set<Long> domainIds) {
		return dsl.select(DOMAIN.ID, DOMAIN.NAME).from(DOMAIN).where(DOMAIN.ID.in(domainIds)).orderBy(DOMAIN.ID)
			.fetch(r -> String.format("%s (%d)", r.value2(), r.value1()));
	}

	/**
	 * Finds the names of device groups, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param groupIds the IDs
	 * @return the groups, as "name (ID)" (the unknown IDs are ignored)
	 */
	public List<String> findGroupNames(DSLContext dsl, Set<Long> groupIds) {
		return dsl.select(DEVICE_GROUP.ID, DEVICE_GROUP.NAME).from(DEVICE_GROUP).where(DEVICE_GROUP.ID.in(groupIds))
			.orderBy(DEVICE_GROUP.ID)
			.fetch(r -> String.format("%s (%d)", r.value2(), r.value1()));
	}

	/**
	 * Finds a page of the devices to export, by name.
	 *
	 * @param dsl the jOOQ context
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @param page the window on the result
	 * @return the devices
	 */
	public List<ExportedDevice> findDevicesToExport(DSLContext dsl, Set<Long> domains, Set<Long> groups, Page page) {
		return page.apply(dsl.select(DEVICE.ID, DEVICE.NAME, DEVICE.MGMT_ADDRESS, DOMAIN.NAME, DEVICE.NETWORK_CLASS, DEVICE.FAMILY,
					DEVICE.CREATED_DATE, DEVICE.CHANGE_DATE, DEVICE.SOFTWARE_VERSION, DEVICE.SOFTWARE_LEVEL, DEVICE.EOS_DATE,
					DEVICE.EOL_DATE, DEVICE.LOCATION, DEVICE.CONTACT)
				.from(DEVICE)
				.leftJoin(DOMAIN).on(DOMAIN.ID.eq(DEVICE.MGMT_DOMAIN))
				.where(exportedDevices(domains, groups))
				.orderBy(DEVICE.NAME.asc(), DEVICE.ID.asc()))
			.fetch(r -> new ExportedDevice(r.get(DEVICE.ID), r.get(DEVICE.NAME), r.get(DEVICE.MGMT_ADDRESS), r.get(DOMAIN.NAME),
				r.get(DEVICE.NETWORK_CLASS) == null ? null : Device.NetworkClass.valueOf(r.get(DEVICE.NETWORK_CLASS)),
				r.get(DEVICE.FAMILY), toDate(r.get(DEVICE.CREATED_DATE)), toDate(r.get(DEVICE.CHANGE_DATE)), r.get(DEVICE.SOFTWARE_VERSION),
				r.get(DEVICE.SOFTWARE_LEVEL) == null ? null : ConformanceLevel.valueOf(r.get(DEVICE.SOFTWARE_LEVEL)),
				toDate(r.get(DEVICE.EOS_DATE)), toDate(r.get(DEVICE.EOL_DATE)), r.get(DEVICE.LOCATION), r.get(DEVICE.CONTACT)));
	}

	/**
	 * Finds all the interfaces to export, by device name.
	 *
	 * @param dsl the jOOQ context
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @return the interfaces
	 */
	public List<ExportedInterface> findInterfacesToExport(DSLContext dsl, Set<Long> domains, Set<Long> groups) {
		List<SelectField<?>> fields = new ArrayList<>(DeviceRepository.INTERFACE_FIELDS);
		fields.add(DEVICE.ID);
		fields.add(DEVICE.NAME);
		return dsl.select(fields)
			.from(NETWORK_INTERFACE)
			.join(DEVICE).on(DEVICE.ID.eq(NETWORK_INTERFACE.DEVICE))
			.where(exportedDevices(domains, groups))
			.orderBy(DEVICE.NAME.asc(), NETWORK_INTERFACE.ID.asc())
			.fetch(r -> {
				NetworkInterface networkInterface = DeviceRepository.toInterface(r);
				List<ExportedAddress> addresses = new ArrayList<>();
				for (NetworkAddress address : networkInterface.getIpAddresses()) {
					addresses.add(new ExportedAddress(address.getIp(), address.getPrefixLength(), address.getAddressUsage()));
				}
				return new ExportedInterface(r.get(DEVICE.ID), r.get(DEVICE.NAME),
					networkInterface.virtualDevice(), networkInterface.interfaceName(),
					networkInterface.description(), networkInterface.vrfInstance(), networkInterface.getMacAddress(),
					networkInterface.enabled(), networkInterface.level3(), addresses);
			});
	}

	/**
	 * Finds a page of the modules to export, by device name.
	 *
	 * @param dsl the jOOQ context
	 * @param history whether to include the removed modules
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @param page the window on the result
	 * @return the modules
	 */
	public List<ExportedModule> findModulesToExport(DSLContext dsl, boolean history, Set<Long> domains, Set<Long> groups, Page page) {
		Condition condition = exportedDevices(domains, groups);
		if (!history) {
			condition = condition.and(MODULE.REMOVED.isDistinctFrom(true));
		}
		return page.apply(dsl.select(DEVICE.ID, DEVICE.NAME, MODULE.SLOT, MODULE.PART_NUMBER, MODULE.SERIAL_NUMBER,
					MODULE.FIRST_SEEN_DATE, MODULE.LAST_SEEN_DATE, MODULE.REMOVED)
				.from(MODULE)
				.join(DEVICE).on(DEVICE.ID.eq(MODULE.DEVICE))
				.where(condition)
				.orderBy(DEVICE.NAME.asc(), MODULE.ID.asc()))
			.fetch(r -> new ExportedModule(r.get(DEVICE.ID), r.get(DEVICE.NAME), r.get(MODULE.SLOT), r.get(MODULE.PART_NUMBER),
				r.get(MODULE.SERIAL_NUMBER), toDate(r.get(MODULE.FIRST_SEEN_DATE)), toDate(r.get(MODULE.LAST_SEEN_DATE)),
				Boolean.TRUE.equals(r.get(MODULE.REMOVED))));
	}

	/**
	 * Finds the group memberships to export (the groups hidden from the reports are left out),
	 * by device name.
	 *
	 * @param dsl the jOOQ context
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @return the memberships
	 */
	public List<ExportedMembership> findMembershipsToExport(DSLContext dsl, Set<Long> domains, Set<Long> groups) {
		return dsl.select(DEVICE.ID, DEVICE.NAME, DEVICE_GROUP.ID, DEVICE_GROUP.NAME)
			.from(DEVICE_GROUP_CACHED_DEVICES)
			.join(DEVICE).on(DEVICE.ID.eq(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES))
			.join(DEVICE_GROUP).on(DEVICE_GROUP.ID.eq(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS))
			.where(exportedDevices(domains, groups), DEVICE_GROUP.HIDDEN_FROM_REPORTS.isFalse())
			.orderBy(DEVICE.NAME.asc(), DEVICE.ID.asc(), DEVICE_GROUP.ID.asc())
			.fetch(r -> new ExportedMembership(r.value1(), r.value2(), r.value3(), r.value4()));
	}

	/**
	 * A device attribute to export.
	 *
	 * @param deviceId the ID of the device
	 * @param deviceName the name of the device
	 * @param driver the driver of the device
	 * @param name the name of the attribute
	 * @param value the value of the attribute
	 */
	public record AttributeToExport(long deviceId, String deviceName, String driver, String name, Object value) {
	}

	/**
	 * Finds a page of the device attributes to export, by device name.
	 *
	 * @param dsl the jOOQ context
	 * @param domains the domains to filter on (empty: any)
	 * @param groups the groups to filter on (empty: any)
	 * @param page the window on the result
	 * @return the attributes
	 */
	public List<AttributeToExport> findDeviceAttributesToExport(DSLContext dsl, Set<Long> domains, Set<Long> groups, Page page) {
		return page.apply(dsl.select(DEVICE.ID, DEVICE.NAME, DEVICE.DRIVER, DEVICE_ATTRIBUTE.TYPE, DEVICE_ATTRIBUTE.NAME,
					DEVICE_ATTRIBUTE.TEXT, DEVICE_ATTRIBUTE.NUMBER, DEVICE_ATTRIBUTE.ASSUMPTION, LONG_TEXT_CONFIGURATION.TEXT)
				.from(DEVICE_ATTRIBUTE)
				.join(DEVICE).on(DEVICE.ID.eq(DEVICE_ATTRIBUTE.DEVICE))
				.leftJoin(LONG_TEXT_CONFIGURATION).on(LONG_TEXT_CONFIGURATION.ID.eq(DEVICE_ATTRIBUTE.LONG_TEXT))
				.where(exportedDevices(domains, groups))
				.orderBy(DEVICE.NAME.asc(), DEVICE_ATTRIBUTE.ID.asc()))
			.fetch(r -> new AttributeToExport(r.get(DEVICE.ID), r.get(DEVICE.NAME), r.get(DEVICE.DRIVER), r.get(DEVICE_ATTRIBUTE.NAME),
				switch (r.get(DEVICE_ATTRIBUTE.TYPE)) {
					case "N" -> r.get(DEVICE_ATTRIBUTE.NUMBER);
					case "B" -> r.get(DEVICE_ATTRIBUTE.ASSUMPTION);
					case "T" -> r.get(LONG_TEXT_CONFIGURATION.TEXT);
					default -> r.get(DEVICE_ATTRIBUTE.TEXT);
				}));
	}

	private static Field<Instant> milestoneField(EndOfSupport milestone) {
		return switch (milestone) {
			case SALE -> DEVICE.EOS_DATE;
			case LIFE -> DEVICE.EOL_DATE;
		};
	}

	private static Date toDate(Instant instant) {
		return instant == null ? null : Date.from(instant);
	}

	private static ResultOption toResult(String name) {
		return name == null ? null : ResultOption.valueOf(name);
	}

	/**
	 * Restricts the devices to some domains.
	 *
	 * @param domains the domains (none: any)
	 * @return the condition
	 */
	private static Condition inDomains(Set<Long> domains) {
		return domains.isEmpty() ? DSL.noCondition() : DEVICE.MGMT_DOMAIN.in(domains);
	}

	/**
	 * Restricts the devices to some groups, with a subquery so that a device which is in several groups is listed once.
	 *
	 * @param groups the groups (none: any)
	 * @return the condition
	 */
	private static Condition inGroups(Set<Long> groups) {
		if (groups.isEmpty()) {
			return DSL.noCondition();
		}
		var gd = DEVICE.as("gd");
		var gm = DEVICE_GROUP_CACHED_DEVICES.as("gm");
		return DEVICE.ID.in(DSL.select(gd.ID).from(gd).leftJoin(gm).on(gm.CACHED_DEVICES.eq(gd.ID)).where(gm.OWNER_GROUPS.in(groups)));
	}

	/**
	 * Counts the devices in production of a group matching a condition.
	 *
	 * @param g the group table of the outer query
	 * @param condition the condition on the devices
	 * @return the count
	 */
	private static Field<Long> groupDeviceCount(net.netshot.netshot.platform.database.schema.tables.DeviceGroup g, Condition condition) {
		var dm = DEVICE_GROUP_CACHED_DEVICES.as("dm");
		return DSL.field(DSL.select(DSL.count(DEVICE.ID).cast(Long.class)).from(dm).join(DEVICE).on(DEVICE.ID.eq(dm.CACHED_DEVICES))
			.where(dm.OWNER_GROUPS.eq(g.ID).and(DEVICE.STATUS.eq(Device.Status.INPRODUCTION.name())).and(condition)));
	}

	private static PolicyRuleDevice toPolicyRuleDevice(Record rec) {
		return new PolicyRuleDevice(DeviceSummary.fromRecord(rec), rec.get(POLICY.ID), rec.get(POLICY.NAME), rec.get(RULE.ID),
			rec.get(RULE.NAME), toDate(rec.get(CHECK_RESULT.CHECK_DATE)), toResult(rec.get(CHECK_RESULT.RESULT)));
	}

	private static List<SelectField<?>> policyRuleDeviceFields() {
		List<SelectField<?>> fields = new ArrayList<>(DeviceSummary.FIELDS);
		fields.addAll(List.of(POLICY.ID, POLICY.NAME, RULE.ID, RULE.NAME, CHECK_RESULT.CHECK_DATE, CHECK_RESULT.RESULT));
		return fields;
	}

}
