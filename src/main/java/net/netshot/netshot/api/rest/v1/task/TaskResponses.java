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
package net.netshot.netshot.api.rest.v1.task;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongFunction;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;
import net.netshot.netshot.api.rest.v1.device.DeviceRefs;
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskService;
import net.netshot.netshot.task.type.DeviceBasedTask;
import net.netshot.netshot.task.type.GroupBasedTask;

/**
 * Builds the responses carrying a task: the task itself, or the task created by an asynchronous request (answered
 * {@code 202 Accepted} with the location of the task). The objects the task refers to are loaded in batch.
 */
public final class TaskResponses {

	private final TaskService taskService;
	private final GroupService groupService;
	private final DeviceRefs deviceRefs;

	/**
	 * Instantiates the helper.
	 *
	 * @param taskService the task use cases
	 * @param groupService the group use cases
	 * @param deviceRefs writes the devices
	 */
	public TaskResponses(TaskService taskService, GroupService groupService, DeviceRefs deviceRefs) {
		this.taskService = taskService;
		this.groupService = groupService;
		this.deviceRefs = deviceRefs;
	}

	/**
	 * Builds the response carrying a task (with its ETag).
	 *
	 * @param request the current request
	 * @param uriInfo the URI of the request (its query parameters are the selection)
	 * @param task the task
	 * @return the representation of the task
	 */
	public TaskDto respond(HttpServletRequest request, UriInfo uriInfo, Task task) {
		Selection selection = Selection.of(uriInfo.getQueryParameters(), TaskDto.class, Selection.View.FULL);
		V1Response.select(request, selection, TaskDto.class);
		V1Response.etag(request, task.version());
		return this.map(selection, task);
	}

	/**
	 * Creates a task for the user making the request, who needs the role of the type of task.
	 *
	 * @param securityContext the security context of the request
	 * @param taskRequest the task to create
	 * @return the created task
	 * @throws ForbiddenException if the user doesn't have the role the type of task needs
	 * @throws net.netshot.netshot.platform.error.NetshotException if the task is invalid, or can't be stored or scheduled
	 */
	public Task create(SecurityContext securityContext, TaskRequest taskRequest) {
		String role = TaskCreationMapper.requiredRole(taskRequest.type());
		if (role != null && !securityContext.isUserInRole(role)) {
			throw new ForbiddenException("Insufficient permissions to create a task of the type %s.".formatted(taskRequest.type()));
		}
		User user = (User) securityContext.getUserPrincipal();
		return this.taskService.create(TaskCreationMapper.toCreation(user.username(), taskRequest));
	}

	/**
	 * Builds the response to a request which created a task: {@code 202 Accepted}, with the location of the task.
	 *
	 * @param request the current request
	 * @param uriInfo the URI of the request (its query parameters are the selection)
	 * @param task the task which was created
	 * @return the representation of the task
	 */
	public TaskDto accepted(HttpServletRequest request, UriInfo uriInfo, Task task) {
		Selection selection = Selection.of(uriInfo.getQueryParameters(), TaskDto.class, Selection.View.FULL);
		V1Response.select(request, selection, TaskDto.class);
		V1Response.accepted(request, V1Links.of(TaskMapper.PATH, task.id()));
		return this.map(selection, task);
	}

	private TaskDto map(Selection selection, Task task) {
		return TaskMapper.toDto(task, this.refs(selection, Collections.singletonList(deviceId(task)),
			Collections.singletonList(groupId(task)), Collections.singletonList(task.parentTaskId())));
	}

	/**
	 * Gets the ID of the device a task targets.
	 *
	 * @param task the task
	 * @return the ID, or null for a task which doesn't target a device
	 */
	public static Long deviceId(Task task) {
		return task.spec() instanceof DeviceBasedTask t ? t.deviceId() : null;
	}

	/**
	 * Gets the ID of the group a task targets.
	 *
	 * @param task the task
	 * @return the ID, or null for a task which doesn't target a group
	 */
	public static Long groupId(Task task) {
		return task.spec() instanceof GroupBasedTask t ? t.deviceGroupId() : null;
	}

	/**
	 * Loads the objects the tasks refer to, one query per kind of reference (only for the references written, in
	 * their full representation only when expanded).
	 *
	 * @param selection the selection of the response
	 * @param deviceIds the IDs of the target devices (null elements ignored)
	 * @param groupIds the IDs of the target groups
	 * @param parentIds the IDs of the parent tasks
	 * @return the references
	 */
	public TaskMapper.Refs refs(Selection selection, Collection<Long> deviceIds, Collection<Long> groupIds, Collection<Long> parentIds) {
		LongFunction<DeviceDto> devices = this.deviceRefs.byIds(selection, "device", deviceIds);
		Map<Long, GroupDto> groups = new HashMap<>();
		Set<Long> ids = nonNull(groupIds);
		if (!ids.isEmpty() && selection.includes("group")) {
			this.groupService.list(ListQuery.byIds(ids)).items().forEach(g -> groups.put(g.id(), GroupMapper.toDto(g)));
		}
		Map<Long, TaskDto> parents = new HashMap<>();
		ids = nonNull(parentIds);
		if (!ids.isEmpty() && selection.includes("parentTask")) {
			this.taskService.listSummaries(ListQuery.byIds(ids)).items()
				.forEach(t -> parents.put(t.id(), TaskMapper.toDto(t, TaskMapper.Refs.IDS_ONLY)));
		}
		return new TaskMapper.Refs(devices,
			orIds(groups, TaskMapper.Refs.IDS_ONLY.groups()::apply), orIds(parents, TaskMapper.Refs.IDS_ONLY.tasks()::apply));
	}

	private static Set<Long> nonNull(Collection<Long> ids) {
		Set<Long> set = new HashSet<>(ids.size());
		ids.stream().filter(Objects::nonNull).forEach(set::add);
		return set;
	}

	// The loaded object, or a reference by ID only (deleted object, or reference not written)
	private static <T> LongFunction<T> orIds(Map<Long, T> loaded, Function<Long, T> byId) {
		return id -> {
			T found = loaded.get(id);
			return found != null ? found : byId.apply(id);
		};
	}
}
