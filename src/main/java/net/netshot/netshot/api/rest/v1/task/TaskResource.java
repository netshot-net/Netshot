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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
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
import net.netshot.netshot.api.rest.v1.report.ReportParams;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskService;
import net.netshot.netshot.task.TaskSummary;

/**
 * The tasks (v1): list, read, cancel, debug log.
 */
@Path("/" + TaskMapper.PATH)
@Tag(name = "Tasks", description = "Tasks (snapshots, scripts, compliance checks...)")
public class TaskResource {

	private final TaskService taskService;
	private final TaskResponses responses;

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
		this.responses = new TaskResponses(taskService, searchService, groupService, driverRegistry);
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
		return list(this.taskService, this.responses, this.request, this.uriInfo, null);
	}

	/**
	 * Answers a page of tasks (of the task list, or of a list of tasks of another object).
	 *
	 * @param taskService the task use cases
	 * @param responses the builder of the task responses (loads the references)
	 * @param request the current request
	 * @param uriInfo the URI of the request (its query parameters are the list parameters)
	 * @param scope a criterion the tasks must also match (the tasks of a device...), or null
	 * @return the page of tasks
	 */
	public static ListResponse<TaskDto> list(TaskService taskService, TaskResponses responses, HttpServletRequest request,
		UriInfo uriInfo, Criterion scope) {
		MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
		ListQuery listQuery = ListParams.parse(params, taskService.listSchema());
		Selection selection = Selection.of(params, TaskDto.class, Selection.View.BRIEF);
		V1Response.select(request, selection, TaskDto.class);
		ListQuery query = scope == null ? listQuery : listQuery.and(scope);
		if (selection.onlyIncludes(TaskDto.SUMMARY_PROPERTIES)) {
			ListResult<TaskSummary> summaries = taskService.listSummaries(query);
			TaskMapper.Refs refs = responses.refs(selection, summaries.items().stream().map(TaskSummary::deviceId).toList(),
				summaries.items().stream().map(TaskSummary::deviceGroupId).toList(),
				summaries.items().stream().map(TaskSummary::parentTaskId).toList());
			return ListResponse.of(uriInfo, listQuery, summaries, s -> TaskMapper.toDto(s, refs));
		}
		ListResult<Task> tasks = taskService.list(query);
		TaskMapper.Refs refs = responses.refs(selection, tasks.items().stream().map(TaskResponses::deviceId).toList(),
			tasks.items().stream().map(TaskResponses::groupId).toList(), tasks.items().stream().map(Task::parentTaskId).toList());
		return ListResponse.of(uriInfo, listQuery, tasks, t -> TaskMapper.toDto(t, refs));
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
		return this.responses.respond(this.request, this.uriInfo, this.taskService.get(id));
	}

	/**
	 * Creates a task.
	 *
	 * @param taskRequest the task to create
	 * @return the created task
	 */
	@POST
	@RolesAllowed(User.ROLE_OPERATOR)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a task", description = "Creates a task and schedules it (as soon as possible by default). "
		+ "Answers 202 with the task, whose location is in the Location header. Running scripts needs the execute-read-write "
		+ "role, scanning subnets the read-write role, and purging the database the admin role.")
	public TaskDto createTask(TaskRequest taskRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Task task = this.responses.create(this.securityContext, taskRequest);
		return this.responses.accepted(this.request, this.uriInfo, task);
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
		return this.responses.respond(this.request, this.uriInfo, this.taskService.update(id, true, user.username()));
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
