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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * The devices (v1).
 */
@Path("/" + DeviceMapper.PATH)
@Tag(name = "Devices", description = "Devices (network or security equipment)")
public class DeviceResource {

	/** The parameter of the device search query. */
	static final String QUERY = "query";

	private final DeviceService deviceService;
	private final SearchService searchService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	/**
	 * Instantiates the resource.
	 *
	 * @param deviceService the device use cases
	 * @param searchService the device search use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public DeviceResource(DeviceService deviceService, SearchService searchService, DriverRegistry driverRegistry) {
		this.deviceService = deviceService;
		this.searchService = searchService;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Lists the devices.
	 *
	 * @param query the device search query
	 * @return the page of devices
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the devices", description = "Lists the devices, in their brief view by default. "
		+ "Besides the filters on the fields, the query parameter takes a device search query "
		+ "(such as [Family] is \"Cisco IOS\" and [Location] contains \"Paris\"), and group, tag and compliant filter "
		+ "on the group membership, the tags and the configuration compliance.")
	public ListResponse<DeviceDto> listDevices(
		@QueryParam(QUERY) @Parameter(description = "Device search query, in the Netshot query language") String query) {
		return list(this.searchService, this.driverRegistry, this.request, this.uriInfo, query, null);
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
		ListQuery listQuery = ListParams.parse(params, searchService.deviceListSchema(), QUERY);
		Selection selection = Selection.of(params, DeviceDto.class, Selection.View.BRIEF);
		V1Response.select(request, selection, DeviceDto.class);
		ListQuery scoped = scope == null ? listQuery : listQuery.and(scope);
		if (selection.onlyIncludes(DeviceDto.SUMMARY_PROPERTIES)) {
			return ListResponse.of(uriInfo, listQuery, searchService.listDeviceSummaries(scoped, query),
				d -> DeviceMapper.toDto(driverRegistry, d));
		}
		return ListResponse.of(uriInfo, listQuery, searchService.listDevices(scoped, query),
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
		Device device = this.deviceService.get(id);
		V1Response.select(this.request, selection, DeviceDto.class);
		V1Response.etag(this.request, device.version());
		return DeviceMapper.toDto(this.driverRegistry, device);
	}

	/**
	 * Deletes a device.
	 *
	 * @param id the device ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Operation(summary = "Delete a device", description = "Deletes a device, with its configurations and results.")
	public void deleteDevice(@PathParam("id") @Parameter(description = "Device ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		if (!this.deviceService.delete(id)) {
			throw new NetshotException(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, "The device doesn't exist.");
		}
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}
}
