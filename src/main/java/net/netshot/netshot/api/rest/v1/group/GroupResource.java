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
package net.netshot.netshot.api.rest.v1.group;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListFilters;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.ListSchema;
import net.netshot.netshot.api.rest.v1.MergePatch;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;
import net.netshot.netshot.api.rest.v1.device.DeviceResource;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;

/**
 * The device groups (v1): list, read, create, replace, update and delete, and their devices.
 */
@Path("/" + GroupMapper.PATH)
@Tag(name = "Groups", description = "Device groups")
public class GroupResource {

	private final GroupService groupService;
	private final SearchService searchService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private HttpHeaders headers;

	/**
	 * Instantiates the resource.
	 *
	 * @param groupService the group use cases
	 * @param searchService the device search use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public GroupResource(GroupService groupService, SearchService searchService, DriverRegistry driverRegistry) {
		this.groupService = groupService;
		this.searchService = searchService;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Lists the groups.
	 *
	 * @return the page of groups
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the groups", description = "Lists the device groups, in their brief view by default. "
		+ "The device filter gives the groups a device is a member of.")
	@ListFilters(ListSchema.GROUPS)
	public ListResponse<GroupDto> listGroups() {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.GROUPS.schema());
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), GroupDto.class, Selection.View.BRIEF),
			GroupDto.class);
		return ListResponse.of(this.uriInfo, query, this.groupService.list(query), GroupMapper::toDto);
	}

	/**
	 * Gets a group.
	 *
	 * @param id the group ID
	 * @return the group
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a group")
	public GroupDto getGroup(@PathParam("id") @Parameter(description = "Group ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.groupService.get(id));
	}

	/**
	 * Lists the devices of a group.
	 *
	 * @param id the group ID
	 * @param query the device search query
	 * @return the page of devices
	 */
	@GET
	@Path("/{id}/devices")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the devices of a group", description = "Lists the devices of a group, "
		+ "as the device list filtered on the group (same parameters).")
	@ListFilters(ListSchema.DEVICES)
	public ListResponse<DeviceDto> listGroupDevices(@PathParam("id") @Parameter(description = "Group ID") long id,
		@QueryParam("query") @Parameter(description = "Device search query, in the Netshot query language") String query) {
		this.groupService.get(id);
		return DeviceResource.list(this.searchService, this.driverRegistry, this.request, this.uriInfo, query,
			new Criterion("group", Lookup.EXACT, List.of(id)));
	}

	/**
	 * Creates a group.
	 *
	 * @param groupRequest the definition of the group
	 * @return the new group
	 */
	@POST
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a group", description = "Creates a device group: a static group of the given devices, "
		+ "or a dynamic group of the devices matching the query.")
	public GroupDto createGroup(GroupRequest groupRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		DeviceGroup group = this.groupService.create(GroupMapper.toDefinition(groupRequest));
		V1Response.created(this.request, V1Links.of(GroupMapper.PATH, group.id()));
		return this.respond(group);
	}

	/**
	 * Replaces a group.
	 *
	 * @param id the group ID
	 * @param groupRequest the new definition of the group
	 * @return the group
	 */
	@PUT
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Replace a group", description = "Replaces the definition of a group (its type can't change; "
		+ "the devices of a static group are unchanged unless given). "
		+ "With If-Match, the group must still be at the given version (ETag).")
	public GroupDto replaceGroup(@PathParam("id") @Parameter(description = "Group ID") long id, GroupRequest groupRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.groupService.update(id, GroupMapper.toDefinition(groupRequest), V1Response.ifMatch(this.headers)));
	}

	/**
	 * Updates a group with a merge patch.
	 *
	 * @param id the group ID
	 * @param patch the merge patch
	 * @return the group
	 * @throws JsonProcessingException if the patched group isn't a valid definition
	 */
	@PATCH
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({V1Mappers.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Update a group", description = "Changes some properties of a group (JSON merge patch, RFC 7396). "
		+ "With If-Match, the group must still be at the given version (ETag).")
	public GroupDto updateGroup(@PathParam("id") @Parameter(description = "Group ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch)
		throws JsonProcessingException {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Long expectedVersion = V1Response.ifMatch(this.headers);
		DeviceGroup current = this.groupService.get(id);
		GroupRequest patched = MergePatch.apply(GroupMapper.toRequest(current), patch, this.headers.getMediaType(), GroupRequest.class);
		return this.respond(this.groupService.update(id, GroupMapper.toDefinition(patched),
			expectedVersion == null ? Long.valueOf(current.version()) : expectedVersion));
	}

	/**
	 * Deletes a group.
	 *
	 * @param id the group ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Operation(summary = "Delete a group", description = "Deletes a group (not its devices). "
		+ "With If-Match, the group must still be at the given version (ETag).")
	public void deleteGroup(@PathParam("id") @Parameter(description = "Group ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		if (!this.groupService.delete(id, V1Response.ifMatch(this.headers))) {
			throw new NetshotException(ErrorCode.NETSHOT_GROUP_NOT_FOUND, "The group doesn't exist.");
		}
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	private GroupDto respond(DeviceGroup group) {
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), GroupDto.class, Selection.View.FULL),
			GroupDto.class);
		V1Response.etag(this.request, group.version());
		return GroupMapper.toDto(group);
	}
}
