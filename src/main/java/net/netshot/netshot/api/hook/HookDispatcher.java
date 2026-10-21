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
package net.netshot.netshot.api.hook;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.api.hook.payload.TaskPayload;
import net.netshot.netshot.api.hook.payload.TaskPayloads;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.hook.Hook;
import net.netshot.netshot.hook.HookService;
import net.netshot.netshot.hook.HookTrigger;
import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.platform.event.DomainEvents;
import net.netshot.netshot.task.TaskService;
import net.netshot.netshot.task.event.TaskFinished;

/**
 * The webhook channel: executes the post-task hooks when a task has finished.
 *
 * <p>It listens to {@link TaskFinished}, which is delivered synchronously in the thread which ran
 * the task. For each enabled hook triggered by the type of task, the task is posted in the format
 * of the hook. A failing hook is logged and does not prevent the other ones from being executed.</p>
 */
@Slf4j
public class HookDispatcher {

	private final HookService hookService;
	private final TaskService taskService;
	private final WebHookSender webHookSender;
	private final DriverRegistry driverRegistry;

	/**
	 * Instantiates the dispatcher.
	 *
	 * @param hookService the hook use cases
	 * @param taskService the task use cases
	 * @param webHookSender the sender of the web hooks
	 * @param driverRegistry the loaded device drivers
	 */
	public HookDispatcher(HookService hookService, TaskService taskService, WebHookSender webHookSender,
		DriverRegistry driverRegistry) {
		this.hookService = hookService;
		this.taskService = taskService;
		this.webHookSender = webHookSender;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Subscribes the dispatcher to the events it reacts to.
	 *
	 * @param events the domain events
	 */
	public void register(DomainEvents events) {
		events.subscribe(TaskFinished.class, this::onTaskFinished);
	}

	/**
	 * Executes the post-task hooks of a task which has finished.
	 *
	 * @param event the event
	 */
	void onTaskFinished(TaskFinished event) {
		long id = event.taskId();
		try {
			List<Hook> hooks = this.hookService.findEnabled(HookTrigger.TriggerType.POST_TASK, event.taskType());
			if (hooks.isEmpty()) {
				return;
			}
			// Mapped once, in the transaction which loads the task, for all the hooks
			TaskPayload payload = this.taskService.mapTask(id, mapped -> TaskPayloads.toPayload(this.driverRegistry, mapped));
			for (Hook hook : hooks) {
				log.trace("Executing post-task hook {}", hook.name());
				try {
					String result = this.execute(hook, payload);
					log.info("Result of post-task hook '{}' after task {} is: {}", hook.name(), id, result);
				}
				catch (Exception e) {
					log.warn("Error while executing hook {} after task {}", hook.name(), id, e);
				}
			}
		}
		catch (Exception e) {
			log.error("Error while processing hooks after task {}.", id, e);
		}
	}

	private String execute(Hook hook, TaskPayload payload) throws Exception {
		return switch (hook) {
			case WebHook webHook -> this.webHookSender.send(webHook, payload);
		};
	}
}
