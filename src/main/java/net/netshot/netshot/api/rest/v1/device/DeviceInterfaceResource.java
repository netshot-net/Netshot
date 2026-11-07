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
package net.netshot.netshot.api.rest.v1.device;

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
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListFilters;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.ListSchema;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.inventory.device.DeviceCatalogService;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * The network interfaces of a device (v1).
 */
@Path("/" + DeviceMapper.PATH + "/{id}/interfaces")
@Tag(name = "Devices", description = "Devices (network or security equipment)")
public class DeviceInterfaceResource {

	private final DeviceCatalogService catalogService;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	/**
	 * Instantiates the resource.
	 *
	 * @param catalogService the device sub-resource use cases
	 */
	@Inject
	public DeviceInterfaceResource(DeviceCatalogService catalogService) {
		this.catalogService = catalogService;
	}

	/**
	 * Lists the network interfaces of a device.
	 *
	 * @param id the device ID
	 * @return the page of interfaces
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the interfaces of a device", description = "Lists the network interfaces of a device, "
		+ "in their brief view by default (name, description, enabled; view=full adds the addresses). "
		+ "Besides the filters on the fields, ipAddress (exact or net lookup) and macAddress filter on the addresses.")
	@ListFilters(ListSchema.DEVICE_INTERFACES)
	public ListResponse<DeviceInterfaceDto> listDeviceInterfaces(@PathParam("id") @Parameter(description = "Device ID") long id) {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ListQuery query = ListParams.parse(params, ListSchema.DEVICE_INTERFACES.schema());
		Selection selection = Selection.of(params, DeviceInterfaceDto.class, Selection.View.BRIEF);
		V1Response.select(this.request, selection, DeviceInterfaceDto.class);
		return ListResponse.of(this.uriInfo, query, this.catalogService.listInterfaces(id, query), DeviceInventoryMapper::toDto);
	}
}
