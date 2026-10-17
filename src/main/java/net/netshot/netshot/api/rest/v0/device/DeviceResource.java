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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.PaginationParams;
import net.netshot.netshot.api.rest.ResponseCodeFilter;
import net.netshot.netshot.api.rest.v0.task.TaskDto;
import net.netshot.netshot.api.rest.v0.task.TaskMapper;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.task.Task;

/**
 * The REST resource of the devices: list, read, add, edit and delete.
 *
 * <p>It only translates between HTTP and the {@link DeviceService}: it reads the request into a
 * command, calls the service, maps the result to a DTO, and chooses the status code. The class
 * is mounted on {@code /} with the full path on each method, because a narrower class-level
 * path would hide the other {@code /devices/...} methods which are served by the other resources.</p>
 */
@Path("/")
@Slf4j
public class DeviceResource {

	private final DeviceService deviceService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	@Context
	private SecurityContext securityContext;

	/**
	 * Instantiates the resource.
	 *
	 * @param deviceService the device use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public DeviceResource(DeviceService deviceService, DriverRegistry driverRegistry) {
		this.deviceService = deviceService;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Gets the devices.
	 *
	 * @param paginationParams = the pagination parameters
	 * @param groupId = the group ID
	 * @param withDetails = whether to include details
	 * @return the devices
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the devices",
		description = "Retrieves the device list with minimal details."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<DeviceSummaryDto> getDevices(
		@BeanParam PaginationParams paginationParams,
		@QueryParam("group") @Parameter(description = "Filter on devices of the given group ID") Long groupId,
		@QueryParam("details") Boolean withDetails) throws WebApplicationException {
		log.debug("REST request, devices.");
		return DeviceMapper.toSummaryDtos(this.deviceService.list(groupId, paginationParams.toPage()));
	}

	/**
	 * Gets a specific device.
	 *
	 * @param id the id
	 * @return the device
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get a device",
		description = "Retrieve a device will all details."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public DeviceDto getDevice(@PathParam("id") @Parameter(description = "Device ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, device {}.", id);
		return DeviceMapper.toDto(this.driverRegistry, this.deviceService.get(id));
	}

	/**
	 * Adds the device.
	 *
	 * @param device the device
	 * @return the task
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/devices")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add a device",
		description = "In auto discovery mode, this will create a 'discover device' task, and the device will be create if the discovery is successful."
			+ " Otherwise, the device will be immediately created in the database, and a 'snapshot' task will be created."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public TaskDto addDevice(NewDeviceRequest device) throws WebApplicationException {
		log.debug("REST request, new device.");
		User user = (User) this.securityContext.getUserPrincipal();
		Task task = this.deviceService.create(DeviceMapper.toCreation(device, user.getUsername()));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return TaskMapper.toDto(this.driverRegistry, task);
	}

	/**
	 * Delete device.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/devices/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Remove a device",
		description = "Remove the given device, by ID."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public void deleteDevice(@PathParam("id") @Parameter(description = "Device ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete device {}.", id);
		if (this.deviceService.delete(id)) {
			ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
		}
		else {
			ResponseCodeFilter.suggest(this.request, Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Sets the device.
	 *
	 * @param id the id
	 * @param rsDevice the rs device
	 * @return the device
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/devices/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Update a device",
		description = "Edits a device, by ID."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public DeviceDto setDevice(@PathParam("id") @Parameter(description = "Device ID") Long id, UpdateDeviceRequest rsDevice)
		throws WebApplicationException {
		log.debug("REST request, edit device {}.", id);
		return DeviceMapper.toDto(this.driverRegistry, this.deviceService.update(id, DeviceMapper.toUpdate(rsDevice)));
	}
}
