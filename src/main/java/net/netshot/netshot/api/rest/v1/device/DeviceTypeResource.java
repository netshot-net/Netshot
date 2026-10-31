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
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.driver.DeviceDriverService;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * The device types (v1).
 */
@Path("/" + DeviceInventoryMapper.TYPES_PATH)
@Tag(name = "Device drivers", description = "Device types (the device drivers loaded by the server)")
public class DeviceTypeResource {

	private final DeviceDriverService driverService;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	/**
	 * Instantiates the resource.
	 *
	 * @param driverService the device driver use cases
	 */
	@Inject
	public DeviceTypeResource(DeviceDriverService driverService) {
		this.driverService = driverService;
	}

	/**
	 * Lists the device types.
	 *
	 * @return the page of device types
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the device types", description = "Lists the device types: the device drivers loaded by the server, in their brief view by default "
		+ "(name, description, version). View full adds the attributes, options, accesses... Items have no id "
		+ "nor url: the name is what the devices refer to.")
	public ListResponse<DeviceTypeDto> listDeviceTypes() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ListQuery query = ListParams.parse(params, this.driverService.listSchema());
		Selection selection = Selection.of(params, DeviceTypeDto.class, Selection.View.BRIEF);
		V1Response.select(this.request, selection, DeviceTypeDto.class);
		return ListResponse.of(this.uriInfo, query, this.driverService.list(query), DeviceInventoryMapper::toDto);
	}
}
