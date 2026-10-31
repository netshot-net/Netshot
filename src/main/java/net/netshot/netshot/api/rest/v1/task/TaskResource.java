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


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
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
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ListQuery query = ListParams.parse(params, this.taskService.listSchema());
		Selection selection = Selection.of(params, TaskDto.class, Selection.View.BRIEF);
		V1Response.select(this.request, selection, TaskDto.class);
		if (selection.onlyIncludes(TaskDto.SUMMARY_PROPERTIES)) {
			ListResult<TaskSummary> summaries = this.taskService.listSummaries(query);
			TaskMapper.Refs refs = this.responses.refs(selection, summaries.items().stream().map(TaskSummary::deviceId).toList(),
				summaries.items().stream().map(TaskSummary::deviceGroupId).toList(),
				summaries.items().stream().map(TaskSummary::parentTaskId).toList());
			return ListResponse.of(this.uriInfo, query, summaries, s -> TaskMapper.toDto(s, refs));
		}
		ListResult<Task> tasks = this.taskService.list(query);
		TaskMapper.Refs refs = this.responses.refs(selection, tasks.items().stream().map(TaskResponses::deviceId).toList(),
			tasks.items().stream().map(TaskResponses::groupId).toList(), tasks.items().stream().map(Task::parentTaskId).toList());
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
}
