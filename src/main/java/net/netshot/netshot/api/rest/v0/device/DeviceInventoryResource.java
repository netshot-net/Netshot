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
package net.netshot.netshot.api.rest.v0.device;

import java.util.List;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.NetshotNotAuthorizedException;
import net.netshot.netshot.api.rest.PaginationParams;
import net.netshot.netshot.driver.DeviceDriverService;
import net.netshot.netshot.inventory.device.DeviceCatalogService;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.inventory.search.SearchService;

/**
 * The REST resource of the other device endpoints: the interfaces and the modules of a device,
 * the search of devices, and the reference lists (device types, families, part numbers, tags and
 * software versions).
 *
 * <p>It only translates between HTTP and the services. The class is mounted on {@code /} with the
 * full path on each method, because a narrower class-level path would hide the other
 * {@code /devices/...} methods served by other classes.</p>
 */
@Path("/")
@Slf4j
public class DeviceInventoryResource {

	private final DeviceService deviceService;
	private final SearchService searchService;
	private final DeviceCatalogService catalogService;
	private final DeviceDriverService driverService;

	@Context
	private SecurityContext securityContext;

	/**
	 * Instantiates the resource.
	 *
	 * @param deviceService the device use cases
	 * @param searchService the device search use case
	 * @param catalogService the reference lists built from the devices
	 * @param driverService the device driver use case
	 */
	@Inject
	public DeviceInventoryResource(DeviceService deviceService, SearchService searchService,
		DeviceCatalogService catalogService, DeviceDriverService driverService) {
		this.deviceService = deviceService;
		this.searchService = searchService;
		this.catalogService = catalogService;
		this.driverService = driverService;
	}

	/**
	 * Gets the device interfaces.
	 *
	 * @param id the id
	 * @param paginationParams = the pagination parameters
	 * @return the device interfaces
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}/interfaces")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get device interfaces",
		description = "Returns the list of interfaces of a given device (by ID)."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<NetworkInterfaceDto> getDeviceInterfaces(@PathParam("id") @Parameter(description = "Device ID") Long id,
		@BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, get device {} interfaces.", id);
		return DeviceCatalogMapper.toInterfaceDtos(this.deviceService.getInterfaces(id, paginationParams.toPage()));
	}

	/**
	 * Gets the device modules.
	 *
	 * @param id the id
	 * @param includeHistory = whether to include history
	 * @param paginationParams = the pagination parameters
	 * @return the device modules
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}/modules")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get device modules",
		description = "Returns the list of hardware modules of a given device, by ID."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<ModuleDto> getDeviceModules(@PathParam("id") @Parameter(description = "Device ID") Long id,
		@Parameter(description = "Whether to include history (removed modules with dates)") @QueryParam("history") boolean includeHistory,
		@BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, get device {} modules.", id);
		return DeviceCatalogMapper.toModuleDtos(this.deviceService.getModules(id, includeHistory, paginationParams.toPage()));
	}

	/**
	 * Search devices.
	 *
	 * @param criteria the search criteria
	 * @param paginationParams = the pagination parameters
	 * @return the search results
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/devices/search")
	@RolesAllowed(User.ROLE_READONLY)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Search for devices",
		description = "Find devices using a string-based query."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public SearchResultsDto searchDevices(SearchRequest criteria, @BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, search devices, query '{}'.", criteria.query());
		return DeviceCatalogMapper.toDto(this.searchService.searchDevices(criteria.query(), paginationParams.toPage()));
	}

	/**
	 * Gets the device types.
	 *
	 * @param refresh = whether to refresh the devices types at same time
	 * @return the device types
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devicetypes")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the device types",
		description = "Returns the list of device types (drivers)."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<DeviceTypeDto> getDeviceTypes(
			@DefaultValue("false") @QueryParam("refresh") @Parameter(description = "Whether to reload all drivers from sources") boolean refresh)
		throws WebApplicationException {
		log.debug("REST request, device types.");
		if (refresh && !this.securityContext.isUserInRole(User.ROLE_ADMIN)) {
			throw new NetshotNotAuthorizedException("Insufficient permissions to reload the device drivers.", 0);
		}
		return DeviceCatalogMapper.toDeviceTypeDtos(this.driverService.list(refresh));
	}

	/**
	 * Gets the device families.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the device families
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devicefamilies")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the existing device families",
		description = "Returns the list of device families (driver specific) currenly known in the database."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<DeviceFamilyDto> getDeviceFamilies(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, device families.");
		return DeviceCatalogMapper.toFamilyDtos(this.catalogService.getFamilies(paginationParams.toPage()));
	}

	/**
	 * Gets the known part numbers.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the part numbers
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/partnumbers")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the known part numbers",
		description = "Returns the list of all known part numbers currently existing in the module table."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<PartNumberDto> getPartNumbers(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, part numbers.");
		return DeviceCatalogMapper.toPartNumberDtos(this.catalogService.getPartNumbers(paginationParams.toPage()));
	}

	/**
	 * Gets the device tags.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the tags
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devicetags")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the existing device tags",
		description = "Returns the list of distinct tags currently attached to at least one device, with the number of devices for each."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<DeviceTagDto> getDeviceTags(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, device tags.");
		return DeviceCatalogMapper.toTagDtos(this.catalogService.getTags(paginationParams.toPage()));
	}

	/**
	 * Gets the known software versions.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the software versions
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/softwareversions")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the known software versions",
		description = "Returns the list of all known software versions currently existing in the device table."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<SoftwareVersionDto> getSoftwareVersions(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, software versions.");
		return DeviceCatalogMapper.toSoftwareVersionDtos(this.catalogService.getSoftwareVersions(paginationParams.toPage()));
	}
}
