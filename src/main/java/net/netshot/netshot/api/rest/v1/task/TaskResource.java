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
package net.netshot.netshot.api.rest.v1.task;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongFunction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;
import net.netshot.netshot.api.rest.v1.device.DeviceMapper;
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.api.rest.v1.report.ReportParams;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskService;
import net.netshot.netshot.task.TaskSummary;
import net.netshot.netshot.task.type.DeviceBasedTask;
import net.netshot.netshot.task.type.GroupBasedTask;

/**
 * The tasks (v1): list, read, cancel, debug log.
 */
@Path("/" + TaskMapper.PATH)
@Tag(name = "Tasks", description = "Tasks (snapshots, scripts, compliance checks...)")
public class TaskResource {

	private final TaskService taskService;
	private final SearchService searchService;
	private final GroupService groupService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private SecurityContext securityContext;

	/**
	 * Instantiates the resource.
	 *
	 * @param taskService the task use cases
	 * @param searchService the device search use cases
	 * @param groupService the group use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public TaskResource(TaskService taskService, SearchService searchService, GroupService groupService, DriverRegistry driverRegistry) {
		this.taskService = taskService;
		this.searchService = searchService;
		this.groupService = groupService;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Lists the tasks.
	 *
	 * @return the page of tasks
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the tasks", description = "Lists the tasks, in their brief view by default, the latest first.")
	public ListResponse<TaskDto> listTasks() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ListQuery query = ListParams.parse(params, this.taskService.listSchema());
		Selection selection = Selection.of(params, TaskDto.class, Selection.View.BRIEF);
		V1Response.select(this.request, selection, TaskDto.class);
		if (selection.onlyIncludes(TaskDto.SUMMARY_PROPERTIES)) {
			ListResult<TaskSummary> summaries = this.taskService.listSummaries(query);
			TaskMapper.Refs refs = this.refs(selection, summaries.items().stream().map(TaskSummary::deviceId).toList(),
				summaries.items().stream().map(TaskSummary::deviceGroupId).toList(),
				summaries.items().stream().map(TaskSummary::parentTaskId).toList());
			return ListResponse.of(this.uriInfo, query, summaries, s -> TaskMapper.toDto(s, refs));
		}
		ListResult<Task> tasks = this.taskService.list(query);
		TaskMapper.Refs refs = this.refs(selection, tasks.items().stream().map(TaskResource::deviceId).toList(),
			tasks.items().stream().map(TaskResource::groupId).toList(), tasks.items().stream().map(Task::parentTaskId).toList());
		return ListResponse.of(this.uriInfo, query, tasks, t -> TaskMapper.toDto(t, refs));
	}

	/**
	 * Gets a task.
	 *
	 * @param id the task ID
	 * @return the task
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a task")
	public TaskDto getTask(@PathParam("id") @Parameter(description = "Task ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.taskService.get(id));
	}

	/**
	 * Cancels a task.
	 *
	 * @param id the task ID
	 * @return the task, after the cancellation (or the request of cancellation, for a running task)
	 */
	@POST
	@Path("/{id}/cancel")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Cancel a task", description = "Cancels a scheduled task, or requests the cancellation of a running "
		+ "group task or of a device task still waiting for its device (cancelRequested). Refused (409) in any other status.")
	public TaskDto cancelTask(@PathParam("id") @Parameter(description = "Task ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		User user = (User) this.securityContext.getUserPrincipal();
		return this.respond(this.taskService.update(id, true, user.username()));
	}

	/**
	 * Gets the debug log of a task.
	 *
	 * @param id the task ID
	 * @return the debug log, empty if there is none
	 */
	@GET
	@Path("/{id}/debug-log")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces(MediaType.TEXT_PLAIN)
	@Operation(summary = "Get the debug log of a task", description = "Gets the full debug log of a task run with debug enabled.")
	public String getTaskDebugLog(@PathParam("id") @Parameter(description = "Task ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.taskService.getDebugLog(id);
	}

	private TaskDto respond(Task task) {
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), TaskDto.class, Selection.View.FULL);
		V1Response.select(this.request, selection, TaskDto.class);
		V1Response.etag(this.request, task.version());
		return TaskMapper.toDto(task, this.refs(selection, Collections.singletonList(deviceId(task)),
			Collections.singletonList(groupId(task)), Collections.singletonList(task.parentTaskId())));
	}

	private static Long deviceId(Task task) {
		return task.spec() instanceof DeviceBasedTask t ? t.deviceId() : null;
	}

	private static Long groupId(Task task) {
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
	private TaskMapper.Refs refs(Selection selection, Collection<Long> deviceIds, Collection<Long> groupIds, Collection<Long> parentIds) {
		Map<Long, DeviceDto> devices = new HashMap<>();
		Set<Long> ids = nonNull(deviceIds);
		if (!ids.isEmpty() && selection.includes("device")) {
			ListQuery query = ListQuery.byIds(ids);
			if (selection.expands("device")) {
				this.searchService.listDevices(query, null).items()
					.forEach(d -> devices.put(d.id(), DeviceMapper.toDto(this.driverRegistry, d)));
			}
			else {
				this.searchService.listDeviceSummaries(query, null).items()
					.forEach(d -> devices.put(d.id(), DeviceMapper.toDto(this.driverRegistry, d)));
			}
		}
		Map<Long, GroupDto> groups = new HashMap<>();
		ids = nonNull(groupIds);
		if (!ids.isEmpty() && selection.includes("group")) {
			this.groupService.list(ListQuery.byIds(ids)).items().forEach(g -> groups.put(g.id(), GroupMapper.toDto(g)));
		}
		Map<Long, TaskDto> parents = new HashMap<>();
		ids = nonNull(parentIds);
		if (!ids.isEmpty() && selection.includes("parentTask")) {
			this.taskService.listSummaries(ListQuery.byIds(ids)).items()
				.forEach(t -> parents.put(t.id(), TaskMapper.toDto(t, TaskMapper.Refs.IDS_ONLY)));
		}
		return new TaskMapper.Refs(orIds(devices, TaskMapper.Refs.IDS_ONLY.devices()::apply),
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

	/**
	 * Gets time-based statistics about the completed tasks.
	 *
	 * @return the statistics
	 */
	@GET
	@Path("/stats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get time-based statistics about completed tasks",
		description = "Returns, for the date range, the number of completed tasks (success, failure, cancelled) bucketed over time, "
			+ "plus the totals by status and by task type over the whole range.",
		parameters = {
			@Parameter(name = "after", in = ParameterIn.QUERY, description = "Start of the range, ISO-8601 (default: 1970-01-01T00:00:00Z)"),
			@Parameter(name = "before", in = ParameterIn.QUERY, description = "End of the range, ISO-8601 (default: now)"),
			@Parameter(name = "buckets", in = ParameterIn.QUERY, description = "Number of time buckets the range is split into, from 1 to 366 (default: 48)")
		})
	public TaskStatsDto getTaskStats() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ReportParams.only(params, "after", "before", "buckets");
		Instant from = ReportParams.instant(params, "after");
		Instant to = ReportParams.instant(params, "before");
		Integer binCount = ReportParams.integer(params, "buckets", 1, 366);
		Instant start = from == null ? Instant.EPOCH : from;
		Instant end = to == null ? Instant.now() : to;
		if (!start.isBefore(end)) {
			throw NetshotException.invalidParameter("before", "The end of the range must be after its start");
		}
		return TaskStatsDto.of(this.taskService.getStats(start.toEpochMilli(), end.toEpochMilli(), binCount), start, end);
	}

	/**
	 * Gets the number of tasks by status.
	 *
	 * @return the counts
	 */
	@GET
	@Path("/status-counts")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get the number of tasks by status",
		description = "Returns the number of tasks in each status (0 included), and the number of task threads.",
		parameters = {
			@Parameter(name = "parentTask", in = ParameterIn.QUERY, description = "Only count the child tasks of this parent task ID")
		})
	public TaskStatusCountsDto getTaskStatusCounts() {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ReportParams.only(params, "parentTask");
		String parent = ReportParams.single(params, "parentTask");
		return TaskStatusCountsDto.of(this.taskService.getOverview(parent == null ? null : ReportParams.id("parentTask", parent)));
	}
}
