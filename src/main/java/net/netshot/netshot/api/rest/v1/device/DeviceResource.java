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
package net.netshot.netshot.api.rest.v1.device;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListFilters;
import net.netshot.netshot.api.rest.v1.ListSchema;
import net.netshot.netshot.api.rest.v1.export.ExportFormat;
import net.netshot.netshot.api.rest.v1.export.ListExport;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.report.ReportExportSelection;
import net.netshot.netshot.report.ReportService;
import net.netshot.netshot.report.XlsxReportWriter;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.platform.query.StatsRow;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.domain.DomainService;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.api.rest.v1.domain.DomainMapper;
import net.netshot.netshot.api.rest.v1.StatsResponse;
import net.netshot.netshot.api.rest.v1.StatsParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.MergePatch;
import net.netshot.netshot.api.rest.v1.RefId;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.api.rest.v1.task.TaskDto;
import net.netshot.netshot.api.rest.v1.task.TaskRequest;
import net.netshot.netshot.api.rest.v1.task.TaskResponses;
import net.netshot.netshot.api.rest.v1.task.TaskScheduling;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.task.DeviceCreationService;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskService;
import net.netshot.netshot.userscript.DeviceJsScript;
import net.netshot.netshot.userscript.ScriptService;

@Path("/" + DeviceMapper.PATH)
@Tag(name = "Devices", description = "Devices (network or security equipment)")
public class DeviceResource {

	/** The parameter of the device search query. */
	static final String QUERY = "query";

	/** The parameter listing the sheets of the Excel workbook export. */
	static final String INCLUDE = "include";

	/** The sheets of the Excel workbook export which can be included. */
	private static final List<String> WORKBOOK_SHEETS = List.of("interfaces", "inventory", "inventoryHistory", "locations",
		"compliance", "groups", "driverAttributes");

	/** The property of each device collection a list loads only if it is selected. */
	private static final Map<Device.Part, String> PART_PROPERTIES = Map.of(
		Device.Part.TAGS, "tags",
		Device.Part.VIRTUAL_DEVICES, "virtualDevices",
		Device.Part.VRF_INSTANCES, "vrfInstances",
		Device.Part.ATTRIBUTES, "attributes",
		Device.Part.ACCESSES, "accesses",
		Device.Part.OWNER_GROUPS, "ownerGroups");

	private final DeviceService deviceService;
	private final DeviceCreationService deviceCreationService;
	private final SearchService searchService;
	private final GroupService groupService;
	private final DomainService domainService;
	private final DriverRegistry driverRegistry;
	private final ScriptService scriptService;
	private final ReportService reportService;
	private final TaskResponses taskResponses;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private HttpHeaders headers;

	@Context
	private SecurityContext securityContext;

	/**
	 * Instantiates the resource.
	 *
	 * @param deviceService the device use cases
	 * @param deviceCreationService the use case which adds a device
	 * @param searchService the device search use cases
	 * @param driverRegistry the loaded device drivers
	 * @param taskService the task use cases
	 * @param groupService the group use cases
	 * @param scriptService the saved script use cases
	 * @param domainService the domain use cases
	 * @param reportService the report use cases (the export of the devices as a workbook)
	 */
	@Inject
	public DeviceResource(DeviceService deviceService, DeviceCreationService deviceCreationService, SearchService searchService,
		DriverRegistry driverRegistry, TaskService taskService, GroupService groupService, ScriptService scriptService,
		DomainService domainService, ReportService reportService) {
		this.deviceService = deviceService;
		this.reportService = reportService;
		this.groupService = groupService;
		this.domainService = domainService;
		this.deviceCreationService = deviceCreationService;
		this.searchService = searchService;
		this.driverRegistry = driverRegistry;
		this.scriptService = scriptService;
		this.taskResponses = new TaskResponses(taskService, searchService, groupService, driverRegistry);
	}

	/**
	 * Lists the devices.
	 *
	 * @param query the device search query
	 * @param include the sheets of the workbook of an Excel export (read from the request, here for the documentation)
	 * @return the page of devices
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the devices", description = "Lists the devices, in their brief view by default. "
		+ "Besides the filters on the fields, the query parameter takes a device search query "
		+ "(such as [Family] is \"Cisco IOS\" and [Location] contains \"Paris\"), and group, tag and compliant filter "
		+ "on the group membership, the tags and the configuration compliance. Like any list, it can be exported "
		+ "as CSV (Accept: text/csv) or Excel (Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet). "
		+ "With the Excel format, the include parameter writes a workbook with more sheets for the matching devices.")
	@ListFilters(ListSchema.DEVICES)
	public ListResponse<DeviceDto> listDevices(
		@QueryParam(QUERY) @Parameter(description = "Device search query, in the Netshot query language") String query,
		@QueryParam(INCLUDE) @Parameter(description = "Excel export only: the extra sheets of the workbook, among interfaces, inventory, "
			+ "inventoryHistory (with the removed modules), locations, compliance, groups and driverAttributes") String include) {
		if (this.uriInfo.getQueryParameters().containsKey(INCLUDE)) {
			return this.exportWorkbook(query);
		}
		return list(this.searchService, this.driverRegistry, this.request, this.uriInfo, query, null);
	}

	/**
	 * Prepares the export of the matching devices as a workbook with a sheet for the devices and a sheet for each
	 * kind of data asked for with the include parameter (the workbook of the v0 export, restricted to the devices of
	 * the list). The workbook is written with the response, and the returned list is empty.
	 *
	 * @param query the device search query
	 * @return an empty list (the workbook replaces it)
	 * @throws NetshotException if the export isn't an Excel one, or a parameter is invalid
	 */
	private ListResponse<DeviceDto> exportWorkbook(String query) {
		if (ListExport.format(this.request) != ExportFormat.XLSX) {
			throw NetshotException.invalidParameter(INCLUDE, "The include parameter only applies to an export as an Excel workbook "
				+ "(Accept: " + ExportFormat.APPLICATION_XLSX + ")");
		}
		Set<String> include = new HashSet<>();
		for (String value : this.uriInfo.getQueryParameters().get(INCLUDE)) {
			for (String item : value.split(",")) {
				String name = item.trim();
				if (name.isEmpty()) {
					continue;
				}
				if (!WORKBOOK_SHEETS.contains(name)) {
					throw NetshotException.invalidParameter(INCLUDE, "Unknown sheet '%s': %s are expected".formatted(name, WORKBOOK_SHEETS));
				}
				include.add(name);
			}
		}
		ListQuery listQuery = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.DEVICES.schema(), QUERY, INCLUDE);
		ListResult<DeviceSummary> devices = this.searchService.listDeviceSummaries(listQuery, query);
		if (devices.items().size() > ListParams.exportMaxRows()) {
			throw NetshotException.invalidParameter(ListParams.LIMIT,
				"The export can't have more than %d devices: filter the list or use limit and offset".formatted(ListParams.exportMaxRows()));
		}
		Set<Long> ids = new HashSet<>();
		devices.items().forEach(device -> ids.add(device.id()));
		ReportExportSelection selection = new ReportExportSelection(Set.of(), Set.of(), include.contains("interfaces"),
			include.contains("inventory") || include.contains("inventoryHistory"), include.contains("inventoryHistory"),
			include.contains("locations"), include.contains("compliance"), include.contains("groups"),
			include.contains("driverAttributes"), ids);
		String userName = ((User) this.securityContext.getUserPrincipal()).username();
		ListExport.write(this.request, output -> {
			SXSSFWorkbook workBook = new SXSSFWorkbook(100);
			try {
				this.reportService.export(selection, new XlsxReportWriter(workBook, Netshot.VERSION, userName));
				workBook.write(output);
			}
			finally {
				workBook.close();
			}
		});
		return new ListResponse<>(devices.count(), 0, devices.items().size(), null, null, List.of());
	}

	/**
	 * Counts the devices.
	 *
	 * @param query the device search query
	 * @return the counts
	 */
	@GET
	@Path("/stats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Count the devices", description = "Counts the devices matching the filters of the device list (and the "
		+ "query), by the values of up to two fields (groupBy: status, mgmtDomain, family, driver, networkClass, softwareVersion, "
		+ "softwareLevel, location, eolDate, eosDate, group, tag, compliant) and/or by time bucket (interval: hour, day, week "
		+ "or month, on createdDate, changeDate, eolDate or eosDate, from/to, timeZone).")
	@ListFilters(ListSchema.DEVICES)
	public StatsResponse getDeviceStats(
		@QueryParam(QUERY) @Parameter(description = "Device search query, in the Netshot query language") String query) {
		StatsParams.Parsed parsed = StatsParams.parse(this.uriInfo.getQueryParameters(), ListSchema.DEVICES.schema(),
			"createdDate", QUERY);
		ListResult<StatsRow> rows = this.searchService.deviceStats(parsed.query(), parsed.stats(), query);
		return StatsResponse.of(parsed.stats(), rows.items(), rows.count(), Map.of(
			"mgmtDomain", DomainMapper.resolver(this.domainService),
			"group", GroupMapper.resolver(this.groupService)));
	}

	/**
	 * Answers a page of devices (of the device list, or of a list of devices of another object).
	 *
	 * @param searchService the device search use cases
	 * @param driverRegistry the loaded device drivers
	 * @param request the current request
	 * @param uriInfo the URI of the request (its query parameters are the list parameters)
	 * @param query the device search query, or null
	 * @param scope a criterion the devices must also match (the devices of a group...), or null
	 * @return the page of devices
	 * @throws NetshotException if a parameter is invalid
	 */
	public static ListResponse<DeviceDto> list(SearchService searchService, DriverRegistry driverRegistry,
		HttpServletRequest request, UriInfo uriInfo, String query, Criterion scope) {
		MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
		ListQuery listQuery = ListParams.parse(params, ListSchema.DEVICES.schema(), QUERY);
		Selection selection = Selection.of(params, DeviceDto.class, Selection.View.BRIEF);
		V1Response.select(request, selection, DeviceDto.class);
		ListQuery scoped = scope == null ? listQuery : listQuery.and(scope);
		if (selection.onlyIncludes(DeviceDto.SUMMARY_PROPERTIES)) {
			return ListResponse.of(uriInfo, listQuery, searchService.listDeviceSummaries(scoped, query),
				d -> DeviceMapper.toDto(driverRegistry, d));
		}
		Set<Device.Part> parts = EnumSet.noneOf(Device.Part.class);
		PART_PROPERTIES.forEach((part, property) -> {
			if (selection.includes(property)) {
				parts.add(part);
			}
		});
		return ListResponse.of(uriInfo, listQuery, searchService.listDevices(scoped, query, parts),
			d -> DeviceMapper.toDto(driverRegistry, d));
	}

	/**
	 * Gets a device.
	 *
	 * @param id the device ID
	 * @return the device
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a device")
	public DeviceDto getDevice(@PathParam("id") @Parameter(description = "Device ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), DeviceDto.class, Selection.View.FULL);
		return this.respond(this.deviceService.get(id), selection);
	}

	/**
	 * Adds a device.
	 *
	 * @param deviceRequest the device to add
	 * @return the task which creates the device (the discovery of its type, or its first snapshot)
	 */
	@POST
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Add a device", description = "Adds a device, asynchronously: answers 202 with the task doing the work, "
		+ "whose location is in the Location header. Without a driver, a task discovers the type of the device and the device "
		+ "is only created if the discovery succeeds (an SNMP community is needed). With a driver, the device is created "
		+ "immediately and a task takes its first snapshot.")
	public TaskDto addDevice(DeviceCreationRequest deviceRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		User user = (User) this.securityContext.getUserPrincipal();
		Task task = this.deviceCreationService.create(DeviceMapper.toCreation(deviceRequest, user.username()));
		return this.taskResponses.accepted(this.request, this.uriInfo, task);
	}

	/**
	 * Replaces the editable properties of a device.
	 *
	 * @param id the device ID
	 * @param deviceRequest the new properties
	 * @return the device
	 */
	@PUT
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Replace a device", description = "Replaces the editable properties of a device (status, management "
		+ "address and domain, comments, tags, options, accesses). The other properties are read only and ignored. "
		+ "The secrets of the credential sets specific to the device which are left out are kept. "
		+ "With If-Match, the device must still be at the given version (ETag).")
	public DeviceDto replaceDevice(@PathParam("id") @Parameter(description = "Device ID") long id, DeviceRequest deviceRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Device device = this.deviceService.update(id, DeviceMapper.toChange(deviceRequest), V1Response.ifMatch(this.headers));
		return this.respond(device, Selection.of(this.uriInfo.getQueryParameters(), DeviceDto.class, Selection.View.FULL));
	}

	/**
	 * Updates a device with a merge patch.
	 *
	 * @param id the device ID
	 * @param patch the merge patch
	 * @return the device
	 */
	@PATCH
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({V1Mappers.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Update a device", description = "Changes some editable properties of a device (JSON merge patch, "
		+ "RFC 7396; a list such as the accesses is replaced as a whole). "
		+ "With If-Match, the device must still be at the given version (ETag).")
	public DeviceDto updateDevice(@PathParam("id") @Parameter(description = "Device ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Device device = this.deviceService.update(id, MergePatch.of(patch, this.headers.getMediaType(), DeviceRequest.class,
			DeviceMapper::toRequest, DeviceMapper::toChange), V1Response.ifMatch(this.headers));
		return this.respond(device, Selection.of(this.uriInfo.getQueryParameters(), DeviceDto.class, Selection.View.FULL));
	}

	/**
	 * Deletes a device.
	 *
	 * @param id the device ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Operation(summary = "Delete a device", description = "Deletes a device, with its configurations and results. "
		+ "With If-Match, the device must still be at the given version (ETag).")
	public void deleteDevice(@PathParam("id") @Parameter(description = "Device ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		if (!this.deviceService.delete(id, V1Response.ifMatch(this.headers))) {
			throw new NetshotException(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, "The device doesn't exist.");
		}
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	/**
	 * Takes a snapshot of a device.
	 *
	 * @param id the device ID
	 * @param snapshotRequest the options (may be absent)
	 * @return the task
	 */
	@POST
	@Path("/{id}/snapshot")
	@RolesAllowed(User.ROLE_OPERATOR)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Take a snapshot of a device", description = "Creates a task which takes a snapshot of the device "
		+ "(as soon as possible by default). Answers 202 with the task, whose location is in the Location header.")
	public TaskDto takeSnapshot(@PathParam("id") @Parameter(description = "Device ID") long id, SnapshotRequest snapshotRequest) {
		SnapshotRequest options = snapshotRequest == null ? new SnapshotRequest(null, null, null, null, null, null, null, null)
			: snapshotRequest;
		Map<String, Object> parameters = new LinkedHashMap<>();
		parameters.put("dontRunDiagnostics", Boolean.TRUE.equals(options.dontRunDiagnostics()));
		parameters.put("dontCheckCompliance", Boolean.TRUE.equals(options.dontCheckCompliance()));
		return this.createTask(id, "TakeSnapshotTask", parameters, options);
	}

	/**
	 * Checks the compliance of a device.
	 *
	 * @param id the device ID
	 * @param complianceCheckRequest the options (may be absent)
	 * @return the task
	 */
	@POST
	@Path("/{id}/compliance-check")
	@RolesAllowed(User.ROLE_OPERATOR)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Check the compliance of a device", description = "Creates a task which checks the configuration "
		+ "compliance of the device (as soon as possible by default). Answers 202 with the task, whose location is in the "
		+ "Location header.")
	public TaskDto checkCompliance(@PathParam("id") @Parameter(description = "Device ID") long id,
		ComplianceCheckRequest complianceCheckRequest) {
		ComplianceCheckRequest options = complianceCheckRequest == null ? new ComplianceCheckRequest(null, null, null, null, null, null)
			: complianceCheckRequest;
		return this.createTask(id, "CheckComplianceTask", Map.of(), options);
	}

	/**
	 * Runs the diagnostics of a device.
	 *
	 * @param id the device ID
	 * @param diagnosticsRunRequest the options (may be absent)
	 * @return the task
	 */
	@POST
	@Path("/{id}/diagnostics-run")
	@RolesAllowed(User.ROLE_OPERATOR)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Run the diagnostics of a device", description = "Creates a task which runs the diagnostics of the "
		+ "device (as soon as possible by default). Answers 202 with the task, whose location is in the Location header.")
	public TaskDto runDiagnostics(@PathParam("id") @Parameter(description = "Device ID") long id,
		DiagnosticsRunRequest diagnosticsRunRequest) {
		DiagnosticsRunRequest options = diagnosticsRunRequest == null ? new DiagnosticsRunRequest(null, null, null, null, null, null, null)
			: diagnosticsRunRequest;
		return this.createTask(id, "RunDiagnosticsTask", Map.of("dontCheckCompliance", Boolean.TRUE.equals(options.dontCheckCompliance())),
			options);
	}

	/**
	 * Runs a script on a device.
	 *
	 * @param id the device ID
	 * @param scriptRunRequest the script to run
	 * @return the task
	 */
	@POST
	@Path("/{id}/script-runs")
	@RolesAllowed(User.ROLE_EXECUTEREADWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Run a script on a device", description = "Creates a task which runs a script on the device (as soon as "
		+ "possible by default): either a saved script (savedScript), or the text of a script with the driver it is written for "
		+ "(script and deviceDriver). Answers 202 with the task, whose location is in the Location header.")
	public TaskDto runScript(@PathParam("id") @Parameter(description = "Device ID") long id, ScriptRunRequest scriptRunRequest) {
		if (scriptRunRequest == null) {
			throw NetshotException.invalidParameter(null, "A script to run is required.");
		}
		String script = scriptRunRequest.script();
		String driver = scriptRunRequest.deviceDriver();
		if (scriptRunRequest.savedScript() != null) {
			if (script != null || driver != null) {
				throw NetshotException.invalidParameter("savedScript", "Give either 'savedScript', or 'script' and 'deviceDriver'.");
			}
			DeviceJsScript saved = this.scriptService.get(scriptRunRequest.savedScript().id());
			if (saved == null) {
				throw NetshotException.invalidParameter("savedScript", "The saved script doesn't exist.");
			}
			script = saved.script();
			driver = saved.deviceDriver();
		}
		else if (script == null || driver == null) {
			throw NetshotException.invalidParameter("script", "Give either 'savedScript', or 'script' and 'deviceDriver'.");
		}
		Map<String, Object> parameters = new LinkedHashMap<>();
		parameters.put("script", script);
		parameters.put("deviceDriver", driver);
		parameters.put("userInputValues", scriptRunRequest.userInputValues());
		parameters.put("runSnapshot", Boolean.TRUE.equals(scriptRunRequest.runSnapshot()));
		parameters.put("runDiagnostics", Boolean.TRUE.equals(scriptRunRequest.runDiagnostics()));
		parameters.put("checkCompliance", Boolean.TRUE.equals(scriptRunRequest.checkCompliance()));
		return this.createTask(id, "RunDeviceScriptTask", parameters, scriptRunRequest);
	}

	// Creates a task of a type on a device, from the options of an action
	private TaskDto createTask(long deviceId, String type, Map<String, Object> parameters, TaskScheduling scheduling) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		// 404 rather than the 400 the task service gives for an unknown target
		this.deviceService.get(deviceId);
		TaskRequest taskRequest = new TaskRequest(type, new RefId(deviceId), null, null, parameters, scheduling.comments(),
			scheduling.scheduleType(), scheduling.scheduleReference(), scheduling.scheduleFactor(), scheduling.priority(),
			scheduling.debugEnabled(), null, null);
		Task task = this.taskResponses.create(this.securityContext, taskRequest);
		return this.taskResponses.accepted(this.request, this.uriInfo, task);
	}

	private DeviceDto respond(Device device, Selection selection) {
		V1Response.select(this.request, selection, DeviceDto.class);
		V1Response.etag(this.request, device.version());
		return DeviceMapper.toDto(this.driverRegistry, device);
	}
}
