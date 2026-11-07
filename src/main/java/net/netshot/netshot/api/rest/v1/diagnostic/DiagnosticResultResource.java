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
package net.netshot.netshot.api.rest.v1.diagnostic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListFilters;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.ListSchema;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticService;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.Lookup;

/**
 * The diagnostic results of a device (v1): list.
 */
@Path("/devices/{id}/diagnostic-results")
@Tag(name = "Diagnostics", description = "Diagnostics (data collected from devices by commands or scripts)")
public class DiagnosticResultResource {

	private final DiagnosticService diagnosticService;
	private final DeviceService deviceService;
	private final GroupService groupService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	/**
	 * Instantiates the resource.
	 *
	 * @param diagnosticService the diagnostic use cases
	 * @param deviceService the device use cases
	 * @param groupService the group use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public DiagnosticResultResource(DiagnosticService diagnosticService, DeviceService deviceService, GroupService groupService,
		DriverRegistry driverRegistry) {
		this.diagnosticService = diagnosticService;
		this.deviceService = deviceService;
		this.groupService = groupService;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Lists the diagnostic results of a device.
	 *
	 * @param id the device ID
	 * @return the page of results
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the diagnostic results of a device", description = "Lists the last result of each diagnostic "
		+ "run on a device, with the diagnostic as a reference.")
	@ListFilters(ListSchema.DIAGNOSTIC_RESULTS)
	public ListResponse<DiagnosticResultDto> listDiagnosticResults(@PathParam("id") @Parameter(description = "Device ID") long id) {
		this.deviceService.get(id);
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.DIAGNOSTIC_RESULTS.schema());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), DiagnosticResultDto.class, Selection.View.FULL);
		V1Response.select(this.request, selection, DiagnosticResultDto.class);
		ListResult<DiagnosticResult> results = this.diagnosticService.listResults(query.and(new Criterion("device", Lookup.EXACT, List.of(id))));
		Set<Long> ids = results.items().stream().map(DiagnosticResult::diagnosticId).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, DiagnosticDto> diagnostics = new HashMap<>();
		if (!ids.isEmpty() && selection.includes("diagnostic")) {
			List<Diagnostic> loaded = this.diagnosticService.list(ListQuery.byIds(ids)).items();
			LongFunction<GroupDto> groups = DiagnosticResource.groups(this.groupService, selection.expands("diagnostic"), loaded);
			loaded.forEach(d -> diagnostics.put(d.id(), DiagnosticMapper.toDto(this.driverRegistry, d, groups)));
		}
		return ListResponse.of(this.uriInfo, query, results,
			r -> DiagnosticMapper.toDto(r, diagnosticId -> diagnostics.get(diagnosticId)));
	}
}
