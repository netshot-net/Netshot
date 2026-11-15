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
package net.netshot.netshot.api.rest.v0.task;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.undertow.util.Headers;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
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
import net.netshot.netshot.api.rest.v0.NetshotNotAuthorizedException;
import net.netshot.netshot.api.rest.v0.PaginationParams;
import net.netshot.netshot.api.rest.v0.ResponseCodeFilter;
import net.netshot.netshot.api.rest.v0.V0Errors;
import net.netshot.netshot.api.rest.v0.V0Lists;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.platform.query.SortOrder;
import net.netshot.netshot.platform.query.StatsQuery;
import net.netshot.netshot.platform.query.StatsRow;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskService;
import net.netshot.netshot.task.type.TaskSpec;

/**
 * The REST resource of the tasks: list, read, create, cancel, debug log and statistics, and the
 * list of the tasks of a device.
 *
 * <p>It only translates between HTTP and the {@link TaskService}. The class is mounted on {@code /}
 * with the full path on each method, because a narrower class-level path would hide the methods of
 * the other resources sharing the prefix (for instance {@code /devices/...}).</p>
 */
@Path("/")
@Slf4j
public class TaskResource {

	/** The names of the types of task. */
	private static final Set<String> TYPE_NAMES = Arrays.stream(TaskSpec.class.getPermittedSubclasses())
		.map(Class::getSimpleName).collect(Collectors.toSet());

	/** The final (completed) statuses, the ones of the statistics. */
	private static final List<Task.Status> FINAL_STATUSES = List.of(Task.Status.SUCCESS, Task.Status.FAILURE, Task.Status.CANCELLED);

	private final TaskService taskService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	@Context
	private SecurityContext securityContext;

	/**
	 * Instantiates the resource.
	 *
	 * @param taskService the task use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public TaskResource(TaskService taskService, DriverRegistry driverRegistry) {
		this.taskService = taskService;
		this.driverRegistry = driverRegistry;
	}

	private String getUsername() {
		try {
			return ((User) this.securityContext.getUserPrincipal()).username();
		}
		catch (Exception e) {
			// Whatever may happen
		}
		return "<Unknown>";
	}

	/**
	 * Gets the device last n (default 100) tasks.
	 *
	 * @param id the id
	 * @param paginationParams = the pagination parameters
	 * @return the device tasks
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}/tasks")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get device tasks",
		description = "Returns the list of tasks of a given device (by ID). Tasks are returned sorted by status and significant date."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	@Tag(name = "Tasks", description = "Task management")
	public List<TaskSummaryDto> getDeviceTasks(@PathParam("id") @Parameter(description = "Device ID") Long id,
		@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, get device {} tasks.", id);
		// As before, any failure (even an invalid window) is reported as a database access error
		return TaskMapper.toSummaryDtos(V0Errors.translate(() -> this.taskService.listSummaries(V0Lists.sorted(
			window(paginationParams.getOffset(), paginationParams.getLimit()), List.of(SortOrder.asc("activity"), SortOrder.desc("date"),
				SortOrder.desc("id")), new Criterion("device", Lookup.EXACT, List.of(id)))).items(),
			V0Errors.code(null, ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the tasks")));
	}

	/**
	 * Gets the task.
	 *
	 * @param id the id
	 * @return the task
	 */
	@GET
	@Path("/tasks/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get a task.",
		description = "Retrieves the status of a given task, by ID."
	)
	@Tag(name = "Tasks", description = "Task management")
	public TaskDto getTask(@PathParam("id") @Parameter(description = "Task ID") Long id) {
		log.debug("REST request, get task {}", id);
		Task task = V0Errors.translate(() -> this.taskService.get(id),
			V0Errors.code(ErrorCode.NETSHOT_TASK_NOT_FOUND, ErrorCode.NETSHOT_TASK_NOT_FOUND, "Task not found."));
		return TaskMapper.toDto(this.driverRegistry, task);
	}

	/**
	 * Gets the debug log of a task.
	 *
	 * @param id the id
	 * @return the debug log, as an attachment
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/tasks/{id}/debuglog")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_OCTET_STREAM})
	@Operation(
		summary = "Get the debug log of a task",
		description = "Retrieves the full debug log of a given task, by ID."
	)
	@Tag(name = "Tasks", description = "Task management")
	public Response getTaskDebugLog(@PathParam("id") @Parameter(description = "Task ID") Long id) throws WebApplicationException {
		log.debug("REST request, get task {} debug log.", id);
		String text = this.taskService.getDebugLog(id);
		String fileName = String.format("debug_%d.log", id);
		byte[] content = text.getBytes(StandardCharsets.UTF_8);
		return Response.ok(content, MediaType.APPLICATION_OCTET_STREAM)
			.header(Headers.CONTENT_DISPOSITION_STRING, "attachment; filename=\"%s\"".formatted(fileName))
			.header(Headers.CONTENT_LENGTH_STRING, content.length)
			.build();
	}

	/**
	 * Gets the tasks.
	 *
	 * @param paginationParams = the pagination parameters
	 * @param statuses = the statuses to include
	 * @param types = the task type(s) to include
	 * @param startDate = the start date
	 * @param endDate = the end date
	 * @param parentTaskId = the parent task ID
	 * @return the tasks
	 */
	@GET
	@Path("/tasks")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the tasks",
		description = "Returns the list of tasks. Limited to 100 if no specific limit is provided."
	)
	@Tag(name = "Tasks", description = "Task management")
	public List<TaskSummaryDto> getTasks(@BeanParam PaginationParams paginationParams,
		@QueryParam("status") @Parameter(description = "Include tasks of given status(es)") Set<Task.Status> statuses,
		@QueryParam("type") @Parameter(description = "Include tasks of given type(s)") Set<String> types,
		@QueryParam("after") @Parameter(description = "Tasks executed or changed after this date (as milliseconds since 1970)") Long startDate,
		@QueryParam("before") @Parameter(description = "Tasks executed or changed before this date (as milliseconds since 1970)") Long endDate,
		@QueryParam("parentTaskId") @Parameter(description = "Only include child tasks of the given parent task ID") Long parentTaskId) {
		log.debug("REST request, get tasks.");
		Page page = paginationParams.toPage();
		List<Criterion> criteria = new ArrayList<>();
		if (statuses != null && !statuses.isEmpty()) {
			criteria.add(new Criterion("status", Lookup.EXACT, statuses.stream().map(status -> (Object) status.name()).toList()));
		}
		if (types != null && !types.isEmpty()) {
			// The unknown type names are ignored
			List<Object> known = types.stream().filter(TYPE_NAMES::contains).map(type -> (Object) type).toList();
			if (known.isEmpty()) {
				return new ArrayList<>();
			}
			criteria.add(new Criterion("type", Lookup.EXACT, known));
		}
		if (startDate != null) {
			criteria.add(new Criterion("date", Lookup.GTE, List.of(Instant.ofEpochMilli(startDate))));
		}
		if (endDate != null) {
			criteria.add(new Criterion("date", Lookup.LTE, List.of(Instant.ofEpochMilli(endDate))));
		}
		List<SortOrder> sort = List.of();
		if (parentTaskId != null) {
			criteria.add(new Criterion("parentTask", Lookup.EXACT, List.of(parentTaskId)));
			sort = List.of(SortOrder.asc("childOrder"));
		}
		return TaskMapper.toSummaryDtos(this.taskService.listSummaries(V0Lists.sorted(window(page.offset(), page.limit()), sort,
			criteria.toArray(Criterion[]::new))).items());
	}

	/**
	 * Sets the task.
	 *
	 * @param id the id
	 * @param rsTask the request
	 * @return the task
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/tasks/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Update a task",
		description = "Edits a task, by ID. Set 'cancel' property to true to cancel the task."
	)
	@Tag(name = "Tasks", description = "Task management")
	public TaskDto setTask(@PathParam("id") @Parameter(description = "Task ID") Long id, TaskRequest rsTask)
		throws WebApplicationException {
		log.debug("REST request, edit task {}.", id);
		// Cancelling is the only change; without it, the task is returned as it is
		Task task = rsTask.isCancelled() ? this.taskService.cancel(id, this.getUsername())
			: V0Errors.translate(() -> this.taskService.get(id),
				V0Errors.code(ErrorCode.NETSHOT_TASK_NOT_FOUND, ErrorCode.NETSHOT_TASK_NOT_FOUND, "Unable to find the task."));
		return TaskMapper.toDto(this.driverRegistry, task);
	}

	/**
	 * Gets global info about the tasks.
	 *
	 * @param parentTaskId the parent task ID
	 * @return the overview
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/tasks/summary")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get summary/overview of tasks",
		description = "Retrieves global info about tasks."
	)
	@Tag(name = "Tasks", description = "Task management")
	public TaskOverviewDto getTaskSummary(
		@QueryParam("parentTaskId") @Parameter(description = "Only summarize child tasks of the given parent task ID") Long parentTaskId)
		throws WebApplicationException {
		log.debug("REST request, get task summary.");
		return TaskMapper.toDto(this.taskService.getOverview(parentTaskId));
	}

	/**
	 * Gets time-based statistics (histogram) of completed tasks over a date range.
	 *
	 * @param startDate the start date (defaults to epoch if not provided)
	 * @param endDate the end date (defaults to now if not provided)
	 * @param buckets the number of time buckets to split the range into
	 * @return the task stats
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/tasks/stats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get time-based statistics about completed tasks",
		description = "Returns, for the given date range, the number of completed tasks (success, failure, cancelled) "
			+ "bucketed over time, plus totals by status and by task type over the whole range."
	)
	@Tag(name = "Tasks", description = "Task management")
	public TaskStatsDto getTaskStats(
		@QueryParam("after") @Parameter(description = "Start of the range (as milliseconds since 1970)") Long startDate,
		@QueryParam("before") @Parameter(description = "End of the range (as milliseconds since 1970)") Long endDate,
		@QueryParam("buckets") @Parameter(description = "Number of time buckets to split the range into") Integer buckets)
		throws WebApplicationException {
		log.debug("REST request, get task stats.");
		return TaskMapper.toDto(this.stats(startDate, endDate, buckets));
	}

	/**
	 * Adds a new task.
	 *
	 * @param rsTask the request
	 * @return the task
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/tasks")
	@RolesAllowed(User.ROLE_OPERATOR)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add a task",
		description = "Creates a task and schedule it for execution."
	)
	@Tag(name = "Tasks", description = "Task management")
	public TaskDto addTask(TaskRequest rsTask) throws WebApplicationException {
		log.debug("REST request, add task.");
		this.checkPermission(rsTask.getType());
		Task task = this.taskService.create(TaskMapper.toCreation(rsTask, this.getUsername()));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return TaskMapper.toDto(this.driverRegistry, task);
	}

	// Some task types need a role higher than the one required to create tasks
	private void checkPermission(String type) {
		if ("RunDeviceScriptTask".equals(type) || "RunDeviceGroupScriptTask".equals(type)) {
			if (!this.securityContext.isUserInRole(User.ROLE_EXECUTEREADWRITE)) {
				throw new NetshotNotAuthorizedException("Insufficient permissions to run scripts on devices.", 0);
			}
		}
		else if ("ScanSubnetsTask".equals(type)) {
			if (!this.securityContext.isUserInRole(User.ROLE_READWRITE)) {
				throw new NetshotNotAuthorizedException("Insufficient permissions to scan for devices.", 0);
			}
		}
		else if ("PurgeDatabaseTask".equals(type)) {
			if (!this.securityContext.isUserInRole(User.ROLE_ADMIN)) {
				throw new NetshotNotAuthorizedException("Insufficient permissions to purge database.", 0);
			}
		}
	}

	// The window of a v0 task list: at most DEFAULT_LIMIT tasks unless a limit is given
	private static Page window(Integer offset, Integer limit) {
		return new Page(offset, limit == null ? TaskService.DEFAULT_LIMIT : limit);
	}

	// The completed tasks of a range, counted by status in equal buckets, and by status and type over the range
	private TaskStats stats(Long after, Long before, Integer buckets) {
		long from = after == null ? 0L : after;
		long to = before == null ? System.currentTimeMillis() : before;
		int binCount = buckets == null ? 48 : Math.max(1, Math.min(366, buckets));
		double step = ((double) Math.max(1L, to - from)) / binCount;
		Map<Task.Status, Long> statusCounts = new HashMap<>();
		FINAL_STATUSES.forEach(status -> statusCounts.put(status, 0L));
		List<TaskStats.Bin> bins = new ArrayList<>();
		for (int i = 0; i < binCount; i++) {
			Instant bucketStart = Instant.ofEpochMilli(from + Math.round(i * step));
			Instant bucketEnd = Instant.ofEpochMilli(i == binCount - 1 ? to : from + Math.round((i + 1) * step));
			Map<Task.Status, Long> countByStatus = new HashMap<>();
			FINAL_STATUSES.forEach(status -> countByStatus.put(status, 0L));
			for (StatsRow row : this.count("status", bucketStart, bucketEnd)) {
				Task.Status status = Task.Status.valueOf(row.keys().get(0).toString());
				countByStatus.put(status, row.count());
				statusCounts.merge(status, row.count(), Long::sum);
			}
			bins.add(new TaskStats.Bin(Date.from(bucketStart), Date.from(bucketEnd), countByStatus));
		}
		Map<String, Long> typeCounts = new HashMap<>();
		TYPE_NAMES.forEach(type -> typeCounts.put(type, 0L));
		for (StatsRow row : this.count("type", Instant.ofEpochMilli(from), Instant.ofEpochMilli(to))) {
			typeCounts.put(row.keys().get(0).toString(), row.count());
		}
		return new TaskStats(bins, statusCounts, typeCounts);
	}

	// Counts the completed tasks dated in a range (the execution date, or the change date if not run), by a field
	private List<StatsRow> count(String field, Instant start, Instant end) {
		return this.taskService.stats(V0Lists.query(Page.ALL,
			new Criterion("status", Lookup.EXACT, FINAL_STATUSES.stream().map(status -> (Object) status.name()).toList()),
			new Criterion("date", Lookup.GTE, List.of(start)), new Criterion("date", Lookup.LT, List.of(end))),
			new StatsQuery(List.of(field), null, null, null, null, null)).items();
	}
}
