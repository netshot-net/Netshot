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
package net.netshot.netshot.api.rest.v1.config;

import java.util.List;

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
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.ListSchema;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.device.DeviceMapper;
import net.netshot.netshot.api.rest.v1.device.DeviceRefs;
import net.netshot.netshot.inventory.config.ConfigService;
import net.netshot.netshot.inventory.device.DeviceCatalogService;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.Lookup;

/**
 * The configurations of a device (v1).
 */
@Path("/" + DeviceMapper.PATH + "/{id}/configs")
@Tag(name = "Configurations", description = "Device configurations (snapshots)")
public class DeviceConfigResource {

	private final DeviceCatalogService deviceCatalogService;
	private final ConfigService configService;
	private final DeviceRefs deviceRefs;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	/**
	 * Instantiates the resource.
	 *
	 * @param deviceCatalogService the device sub-resource use cases (to check that the device exists)
	 * @param configService the configuration use cases
	 * @param deviceRefs writes the devices
	 */
	@Inject
	public DeviceConfigResource(DeviceCatalogService deviceCatalogService, ConfigService configService, DeviceRefs deviceRefs) {
		this.deviceCatalogService = deviceCatalogService;
		this.configService = configService;
		this.deviceRefs = deviceRefs;
	}

	/**
	 * Lists the configurations of a device.
	 *
	 * @param id the device ID
	 * @return the page of configurations
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the configurations of a device", description = "Lists the configurations of a device, "
		+ "as the configuration list filtered on the device (same parameters), the latest first.")
	@ListFilters(ListSchema.CONFIGS)
	public ListResponse<ConfigDto> listDeviceConfigs(@PathParam("id") @Parameter(description = "Device ID") long id) {
		this.deviceCatalogService.checkDevice(id);
		return ConfigResource.list(this.configService, this.deviceRefs, this.request, this.uriInfo,
			new Criterion("device", Lookup.EXACT, List.of(id)));
	}
}
