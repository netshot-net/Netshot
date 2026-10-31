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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v0.report.XlsxReportWriter;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.api.rest.v1.compliance.PolicyMapper;
import net.netshot.netshot.api.rest.v1.compliance.RuleMapper;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;
import net.netshot.netshot.api.rest.v1.device.DeviceMapper;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.QuerySchema;
import net.netshot.netshot.report.AccessFailureDevice;
import net.netshot.netshot.report.DailyChangeCount;
import net.netshot.netshot.report.GroupConfigComplianceStat;
import net.netshot.netshot.report.GroupSoftwareComplianceStat;
import net.netshot.netshot.report.HardwareSupportCount;
import net.netshot.netshot.report.HardwareSupportStats;
import net.netshot.netshot.report.PolicyRuleDevice;
import net.netshot.netshot.report.ReportExportSelection;
import net.netshot.netshot.report.ReportService;
import net.netshot.netshot.report.SoftwareLevelDevice;

/**
 * The reports (v1): statistics, the lists of devices behind them, and the data export.
 *
 * <p>The filters are query parameters ({@code domain}, {@code group}, {@code policy}... repeated or comma-separated;
 * no filter means any). The lists of devices are paginated in the standard envelope (in memory: the reports are
 * computed as a whole); the statistics are small, so they are written in the same envelope but never paginated
 * (everything in one page, {@code count} = number of rows).</p>
 */
@Path("/reports")
@Tag(name = "Reports", description = "Reports and statistics")
public class ReportResource {

	/** The media type of the export. */
	public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	/** The default number of days of the access failure report. */
	private static final int DEFAULT_ACCESS_FAILURE_DAYS = 3;

	/** The default and maximum number of days of the changes by day report. */
	private static final int DEFAULT_CHANGE_DAYS = 7;
	private static final int MAX_CHANGE_DAYS = 366;

	/** The lists of the reports have no filter of the generic kind (the reports read their own parameters). */
	private static final QuerySchema NO_FILTER = new QuerySchema(Map.of(), List.of());

	/** The sheets the export can add to the devices. */
	private static final Set<String> EXPORT_SECTIONS = Set.of("interfaces", "inventory", "inventoryHistory", "locations", "compliance",
		"groups", "driverAttributes");

	private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

	private final ReportService reportService;
	private final SearchService searchService;
	private final GroupService groupService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private SecurityContext securityContext;

	/**
	 * Instantiates the resource.
	 *
	 * @param reportService the report use cases
	 * @param searchService the device search use cases
	 * @param groupService the group use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public ReportResource(ReportService reportService, SearchService searchService, GroupService groupService, DriverRegistry driverRegistry) {
		this.reportService = reportService;
		this.searchService = searchService;
		this.groupService = groupService;
		this.driverRegistry = driverRegistry;
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private static Instant instant(Date date) {
		return date == null ? null : Instant.ofEpochMilli(date.getTime());
	}

	/**
	 * Wraps statistics in the envelope of the lists: one page holding all the rows.
	 *
	 * @param <T> the type of the rows
	 * @param rows the rows
	 * @return the page
	 */
	private static <T> ListResponse<T> rows(List<T> rows) {
		return new ListResponse<>((long) rows.size(), 0, rows.size(), null, null, rows);
	}

	/**
	 * Reads the pagination parameters of a report listing devices.
	 *
	 * @param params the query parameters
	 * @param filters the filters the report reads itself
	 * @return the list query (only its window and count flag are meaningful)
	 */
	private static ListQuery pagination(MultivaluedMap<String, String> params, String... filters) {
		ListQuery query = ListParams.parse(params, NO_FILTER, filters);
		if (query.search() != null) {
			throw NetshotException.invalidParameter(ListParams.SEARCH, "This report can't be searched");
		}
		return query;
	}

	/**
	 * Takes the requested window of a complete result.
	 *
	 * @param <T> the type of the items
	 * @param all the complete result
	 * @param query the list query
	 * @return the window, with the total count (unless disabled)
	 */
	private static <T> ListResult<T> window(List<T> all, ListQuery query) {
		int offset = Math.min(query.page().offset() == null ? 0 : query.page().offset(), all.size());
		int end = query.page().limit() == null ? all.size() : (int) Math.min((long) all.size(), (long) offset + query.page().limit());
		return new ListResult<>(all.subList(offset, end), query.count() ? (long) all.size() : null);
	}

	/**
	 * Builds the page of a report listing devices: the devices of the window are loaded in one query, in their
	 * summary form unless the selection needs more.
	 *
	 * @param <S> the type of the report rows
	 * @param <R> the type of the representation of the rows
	 * @param query the list query
	 * @param all the complete result
	 * @param full whether the full devices are needed
	 * @param device the device of a row
	 * @param row the representation of a row, from the row and its device
	 * @return the page
	 */
	private <S, R> ListResponse<R> devicePage(ListQuery query, List<S> all, boolean full, Function<S, DeviceSummary> device,
		BiFunction<S, DeviceDto, R> row) {
		ListResult<S> result = window(all, query);
		Map<Long, DeviceDto> devices = new HashMap<>();
		Set<Long> ids = new HashSet<>();
		for (S item : result.items()) {
			DeviceSummary summary = device.apply(item);
			devices.put(summary.id(), DeviceMapper.toDto(this.driverRegistry, summary));
			ids.add(summary.id());
		}
		if (full && !ids.isEmpty()) {
			this.searchService.listDevices(ListQuery.byIds(ids), null).items()
				.forEach(d -> devices.put(d.id(), DeviceMapper.toDto(this.driverRegistry, d)));
		}
		return ListResponse.of(this.uriInfo, query, result, item -> row.apply(item, devices.get(device.apply(item).id())));
	}

	private static ReportObjectDto group(long id, String name) {
		return new ReportObjectDto(id, V1Links.of(GroupMapper.PATH, id), name);
	}

	private static ConfigComplianceDeviceStatusDto toDto(PolicyRuleDevice row, DeviceDto device) {
		return new ConfigComplianceDeviceStatusDto(device,
			new ReportObjectDto(row.policyId(), V1Links.of(PolicyMapper.PATH, row.policyId()), row.policyName()),
			new ReportObjectDto(row.ruleId(), V1Links.of(RuleMapper.PATH, row.ruleId()), row.ruleName()),
			instant(row.checkDate()), row.result());
	}

	/**
	 * Lists the compliance results of devices as a report.
	 *
	 * @param params the query parameters
	 * @param query the list query
	 * @param results the complete result
	 * @return the page
	 */
	private ListResponse<ConfigComplianceDeviceStatusDto> complianceRows(MultivaluedMap<String, String> params, ListQuery query,
		List<PolicyRuleDevice> results) {
		Selection selection = Selection.of(params, ConfigComplianceDeviceStatusDto.class, Selection.View.FULL);
		V1Response.select(this.request, selection, ConfigComplianceDeviceStatusDto.class);
		return this.devicePage(query, results, selection.expands("device"), PolicyRuleDevice::device, ReportResource::toDto);
	}

	// ------------------------------------------------------------------
	// Statistics
	// ------------------------------------------------------------------

	/**
	 * Gets the configuration compliance of the device groups.
	 *
	 * @return the statistics, a row per group
	 */
	@GET
	@Path("/group-config-compliance-stats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get the configuration compliance of the device groups",
		description = "Returns, for each group not hidden from the reports, the number of devices in production and the number "
			+ "of them without non-conforming configuration compliance result. Not paginated.",
		parameters = {
			@Parameter(name = "domain", in = ParameterIn.QUERY, description = "Only count the devices of these domain IDs"),
			@Parameter(name = "group", in = ParameterIn.QUERY, description = "Only report these group IDs"),
			@Parameter(name = "policy", in = ParameterIn.QUERY, description = "Only take into account these policy IDs")
		})
	public ListResponse<GroupConfigComplianceStatDto> getGroupConfigComplianceStats() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ReportParams.only(params, "domain", "group", "policy");
		List<GroupConfigComplianceStat> stats = this.reportService.getGroupConfigComplianceStats(ReportParams.ids(params, "domain"),
			ReportParams.ids(params, "group"), ReportParams.ids(params, "policy"));
		return rows(stats.stream().map(s -> new GroupConfigComplianceStatDto(group(s.groupId(), s.groupName()), s.groupFolder(),
			s.compliantDeviceCount(), s.deviceCount())).toList());
	}

	/**
	 * Gets the software compliance of the device groups.
	 *
	 * @return the statistics, a row per group
	 */
	@GET
	@Path("/group-software-compliance-stats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get the software compliance of the device groups",
		description = "Returns, for each group not hidden from the reports, the number of devices in production at each software "
			+ "compliance level. Not paginated.",
		parameters = {
			@Parameter(name = "domain", in = ParameterIn.QUERY, description = "Only count the devices of these domain IDs")
		})
	public ListResponse<GroupSoftwareComplianceStatDto> getGroupSoftwareComplianceStats() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ReportParams.only(params, "domain");
		List<GroupSoftwareComplianceStat> stats = this.reportService.getGroupSoftwareComplianceStats(ReportParams.ids(params, "domain"));
		return rows(stats.stream().map(s -> new GroupSoftwareComplianceStatDto(group(s.groupId(), s.groupName()),
			s.goldDeviceCount(), s.silverDeviceCount(), s.bronzeDeviceCount(), s.deviceCount())).toList());
	}

	/**
	 * Gets the hardware support status of the devices.
	 *
	 * @return the statistics, a row per milestone and date
	 */
	@GET
	@Path("/hardware-support-stats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get the hardware support status of the devices",
		description = "Returns the number of devices in production by end-of-sale date, then by end-of-life date (a row with a null "
			+ "date counts the devices without date). Not paginated.",
		parameters = {
			@Parameter(name = "domain", in = ParameterIn.QUERY, description = "Only count the devices of these domain IDs"),
			@Parameter(name = "group", in = ParameterIn.QUERY, description = "Only count the devices of these group IDs")
		})
	public ListResponse<HardwareSupportStatDto> getHardwareSupportStats() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ReportParams.only(params, "domain", "group");
		HardwareSupportStats stats = this.reportService.getHardwareSupportStats(ReportParams.ids(params, "domain"),
			ReportParams.ids(params, "group"));
		List<HardwareSupportStatDto> rows = new ArrayList<>();
		for (HardwareSupportCount count : stats.endOfSale()) {
			rows.add(new HardwareSupportStatDto(HardwareSupportType.EOS, instant(count.eoxDate()), count.deviceCount()));
		}
		for (HardwareSupportCount count : stats.endOfLife()) {
			rows.add(new HardwareSupportStatDto(HardwareSupportType.EOL, instant(count.eoxDate()), count.deviceCount()));
		}
		return rows(rows);
	}

	/**
	 * Gets the number of configuration changes of each of the last days.
	 *
	 * @return the counts, oldest day first
	 */
	@GET
	@Path("/changes-by-day")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get the number of configuration changes by day",
		description = "Returns the number of device configuration changes of each of the last days, today included, oldest first. Not paginated.",
		parameters = {
			@Parameter(name = "days", in = ParameterIn.QUERY, description = "Number of days, from 1 to 366 (default: 7)"),
			@Parameter(name = "timeZone", in = ParameterIn.QUERY, description = "Time zone the days are based on, such as Europe/Paris (default: the server one)")
		})
	public ListResponse<DailyChangeCountDto> getChangesByDay() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ReportParams.only(params, "days", "timeZone");
		Integer count = ReportParams.integer(params, "days", 1, MAX_CHANGE_DAYS);
		ZoneId zone = ReportParams.zone(params, "timeZone");
		List<DailyChangeCount> counts = this.reportService.getDailyChanges(count == null ? DEFAULT_CHANGE_DAYS : count,
			zone == null ? TimeZone.getDefault() : TimeZone.getTimeZone(zone));
		return rows(counts.stream().map(c -> new DailyChangeCountDto(instant(c.changeDay()), c.changeCount())).toList());
	}

	// ------------------------------------------------------------------
	// Lists of devices
	// ------------------------------------------------------------------

	/**
	 * Lists the devices without recent successful snapshot.
	 *
	 * @return the page of devices
	 */
	@GET
	@Path("/access-failure-devices")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the devices without successful snapshot over a period",
		description = "Lists the devices which didn't have a successful snapshot over the last days, the most recent success first.",
		parameters = {
			@Parameter(name = "days", in = ParameterIn.QUERY, description = "Number of days to look back, at least 1 (default: 3)"),
			@Parameter(name = "domain", in = ParameterIn.QUERY, description = "Only the devices of these domain IDs")
		})
	public ListResponse<AccessFailureDeviceDto> getAccessFailureDevices() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ListQuery query = pagination(params, "days", "domain");
		Selection selection = Selection.of(params, AccessFailureDeviceDto.class, Selection.View.FULL);
		V1Response.select(this.request, selection, AccessFailureDeviceDto.class);
		Integer period = ReportParams.integer(params, "days", 1, 36500);
		List<AccessFailureDevice> devices = this.reportService.getAccessFailureDevices(period == null ? DEFAULT_ACCESS_FAILURE_DAYS : period,
			ReportParams.ids(params, "domain"), Page.ALL);
		return this.devicePage(query, devices, selection.expands("device"), AccessFailureDevice::device,
			(d, device) -> new AccessFailureDeviceDto(device, instant(d.lastSuccess()), instant(d.lastFailure())));
	}

	/**
	 * Lists the configuration compliance results of devices.
	 *
	 * @return the page of results
	 */
	@GET
	@Path("/config-compliance-device-statuses")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the configuration compliance of devices",
		description = "Lists the compliance result of each device for each rule, for the devices in production.",
		parameters = {
			@Parameter(name = "domain", in = ParameterIn.QUERY, description = "Only the devices of these domain IDs"),
			@Parameter(name = "group", in = ParameterIn.QUERY, description = "Only the devices of these group IDs"),
			@Parameter(name = "policy", in = ParameterIn.QUERY, description = "Only the rules of these policy IDs"),
			@Parameter(name = "result", in = ParameterIn.QUERY, description = "Only these results (CONFORMING, NONCONFORMING, EXEMPTED...)")
		})
	public ListResponse<ConfigComplianceDeviceStatusDto> getConfigComplianceDeviceStatuses() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ListQuery query = pagination(params, "domain", "group", "policy", "result");
		List<PolicyRuleDevice> results = this.reportService.getConfigComplianceDeviceStatuses(ReportParams.ids(params, "domain"),
			ReportParams.ids(params, "group"), ReportParams.ids(params, "policy"),
			ReportParams.enums(params, "result", CheckResult.ResultOption.class));
		return this.complianceRows(params, query, results);
	}

	/**
	 * Lists the non-conforming results of the devices of a group.
	 *
	 * @return the page of results
	 */
	@GET
	@Path("/group-config-non-compliant-devices")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the non-compliant devices of a group",
		description = "Lists the non-conforming compliance results of the devices of a group.",
		parameters = {
			@Parameter(name = "group", in = ParameterIn.QUERY, description = "Group ID (required)"),
			@Parameter(name = "domain", in = ParameterIn.QUERY, description = "Only the devices of these domain IDs"),
			@Parameter(name = "policy", in = ParameterIn.QUERY, description = "Only the rules of these policy IDs")
		})
	public ListResponse<ConfigComplianceDeviceStatusDto> getGroupConfigNonCompliantDevices() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ListQuery query = pagination(params, "group", "domain", "policy");
		long groupId = ReportParams.requiredId(params, "group");
		this.groupService.get(groupId);
		List<PolicyRuleDevice> results = this.reportService.getGroupConfigNonCompliantDevices(groupId, ReportParams.ids(params, "domain"),
			ReportParams.ids(params, "policy"));
		return this.complianceRows(params, query, results);
	}

	/**
	 * Lists the devices of a group at a software compliance level.
	 *
	 * @return the page of devices
	 */
	@GET
	@Path("/group-devices-by-software-level")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the devices of a group by software compliance level",
		description = "Lists the devices of a group at a software compliance level, with their software version.",
		parameters = {
			@Parameter(name = "group", in = ParameterIn.QUERY, description = "Group ID (required)"),
			@Parameter(name = "level", in = ParameterIn.QUERY, description = "Software compliance level: GOLD, SILVER, BRONZE or UNKNOWN (required)"),
			@Parameter(name = "domain", in = ParameterIn.QUERY, description = "Only the devices of these domain IDs")
		})
	public ListResponse<SoftwareLevelDeviceDto> getGroupDevicesBySoftwareLevel() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ListQuery query = pagination(params, "group", "level", "domain");
		Selection selection = Selection.of(params, SoftwareLevelDeviceDto.class, Selection.View.FULL);
		V1Response.select(this.request, selection, SoftwareLevelDeviceDto.class);
		long groupId = ReportParams.requiredId(params, "group");
		ConformanceLevel conformanceLevel = ReportParams.constant("level", ReportParams.required(params, "level"), ConformanceLevel.class);
		this.groupService.get(groupId);
		List<SoftwareLevelDevice> devices = this.reportService.getGroupDevicesBySoftwareLevel(groupId, conformanceLevel.name(),
			ReportParams.ids(params, "domain"));
		return this.devicePage(query, devices, selection.expands("device"), SoftwareLevelDevice::device,
			(d, device) -> new SoftwareLevelDeviceDto(device, d.softwareVersion()));
	}

	/**
	 * Lists the devices reaching a hardware support milestone at a date.
	 *
	 * @return the page of devices
	 */
	@GET
	@Path("/hardware-support-devices")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the devices reaching end of sale or end of life at a date",
		description = "Lists the devices in production reaching the end of sale (EOS) or the end of life (EOL) at the date (the "
			+ "devices without date if it isn't given), as counted by the hardware support statistics.",
		parameters = {
			@Parameter(name = "type", in = ParameterIn.QUERY, description = "EOS (end of sale) or EOL (end of life) (required)"),
			@Parameter(name = "date", in = ParameterIn.QUERY, description = "The date, ISO-8601, as given by the statistics (default: the devices without date)"),
			@Parameter(name = "domain", in = ParameterIn.QUERY, description = "Only the devices of these domain IDs"),
			@Parameter(name = "group", in = ParameterIn.QUERY, description = "Only the devices of these group IDs")
		})
	public ListResponse<DeviceDto> getHardwareSupportDevices() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ListQuery query = pagination(params, "type", "date", "domain", "group");
		Selection selection = Selection.of(params, DeviceDto.class, Selection.View.BRIEF);
		V1Response.select(this.request, selection, DeviceDto.class);
		HardwareSupportType milestone = ReportParams.constant("type", ReportParams.required(params, "type"), HardwareSupportType.class);
		Instant when = ReportParams.instant(params, "date");
		List<DeviceSummary> devices = this.reportService.getHardwareSupportDevices(milestone.code(), when == null ? 0 : when.toEpochMilli(),
			ReportParams.ids(params, "domain"), ReportParams.ids(params, "group"));
		return this.devicePage(query, devices, !selection.onlyIncludes(DeviceDto.SUMMARY_PROPERTIES), Function.identity(),
			(_, device) -> device);
	}

	// ------------------------------------------------------------------
	// Export
	// ------------------------------------------------------------------

	/**
	 * Exports the data as a spreadsheet.
	 *
	 * @return the spreadsheet
	 */
	@GET
	@Path("/export")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces(XLSX)
	@Operation(summary = "Export the data as a spreadsheet",
		description = "Exports the devices (filtered by domain or group) as an Excel workbook, optionally with their interfaces, inventory, "
			+ "locations, compliance results, groups and driver-specific attributes.",
		parameters = {
			@Parameter(name = "domain", in = ParameterIn.QUERY, description = "Only the devices of these domain IDs"),
			@Parameter(name = "group", in = ParameterIn.QUERY, description = "Only the devices of these group IDs"),
			@Parameter(name = "include", in = ParameterIn.QUERY, description = "Sheets to add, comma-separated or repeated: interfaces, inventory, "
				+ "inventoryHistory (inventory with the removed modules), locations, compliance, groups (groups and memberships), "
				+ "driverAttributes"),
			@Parameter(name = "format", in = ParameterIn.QUERY, description = "Format of the export: xlsx (default, the only one)")
		})
	public Response export() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ReportParams.only(params, "domain", "group", "include", "format");
		Set<Long> domains = ReportParams.ids(params, "domain");
		Set<Long> groups = ReportParams.ids(params, "group");
		Set<String> sections = ReportParams.multiple(params, "include");
		for (String section : sections) {
			if (!EXPORT_SECTIONS.contains(section)) {
				throw NetshotException.invalidParameter("include", "Unknown section '%s'".formatted(section));
			}
		}
		String fileFormat = ReportParams.single(params, "format");
		if (fileFormat != null && !"xlsx".equalsIgnoreCase(fileFormat)) {
			throw NetshotException.invalidParameter("format", "Unsupported format '%s': xlsx is expected".formatted(fileFormat));
		}
		ReportExportSelection selection = new ReportExportSelection(domains, groups, sections.contains("interfaces"),
			sections.contains("inventory") || sections.contains("inventoryHistory"), sections.contains("inventoryHistory"),
			sections.contains("locations"), sections.contains("compliance"), sections.contains("groups"),
			sections.contains("driverAttributes"));
		User user = (User) this.securityContext.getUserPrincipal();
		String fileName = "netshot-export_%s.xlsx".formatted(FILE_DATE.format(Instant.now()));
		try (SXSSFWorkbook workBook = new SXSSFWorkbook(100)) {
			this.reportService.export(selection, new XlsxReportWriter(workBook, Netshot.VERSION, user.username()));
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			workBook.write(output);
			byte[] content = output.toByteArray();
			return Response.ok(content, XLSX)
				.header("Content-Disposition", "attachment; filename=\"%s\"".formatted(fileName))
				.header("Content-Length", content.length)
				.build();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Unable to write the resulting file", e);
		}
	}
}
