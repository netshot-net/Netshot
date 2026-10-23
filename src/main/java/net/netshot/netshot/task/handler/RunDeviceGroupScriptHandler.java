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
package net.netshot.netshot.task.handler;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.engine.ChildTaskOrchestrator;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;

/**
 * Schedules a script run on every device of a group or list.
 */
@Slf4j
public class RunDeviceGroupScriptHandler implements TaskHandler<RunDeviceGroupScriptTask> {

	private final ChildTaskOrchestrator childTaskOrchestrator;
	private final DriverRegistry driverRegistry;

	/**
	 * Creates the handler.
	 * @param childTaskOrchestrator the child task orchestrator
	 * @param driverRegistry the driver registry
	 */
	public RunDeviceGroupScriptHandler(ChildTaskOrchestrator childTaskOrchestrator, DriverRegistry driverRegistry) {
		this.childTaskOrchestrator = childTaskOrchestrator;
		this.driverRegistry = driverRegistry;
	}

	@Override
	public Class<RunDeviceGroupScriptTask> taskType() {
		return RunDeviceGroupScriptTask.class;
	}

	@Override
	public void run(Task task, RunDeviceGroupScriptTask spec, TaskRun run) {
		TaskTargets targets = TaskTargets.load(spec);
		List<Device> devices = targets.devices();
		if (targets.group() == null && devices.isEmpty()) {
			run.logger.info("Neither a device group nor a device list is set, the task will be cancelled.");
			run.setStatus(Status.CANCELLED);
			return;
		}
		log.debug("Task {}. {} device(s) to process.", task.id(), devices.size());
		String comment = targets.group() == null
			? String.format("Started due to %d-device script list", devices.size())
			: String.format("Started due to group %s script task", targets.group().name());

		DeviceDriver driver = this.driverRegistry.getDriverByName(spec.deviceDriver());
		if (driver == null) {
			log.error("Task {}. No such device driver {}.", task.id(), spec.deviceDriver());
			run.logger.error("Unknown device driver.");
			run.setStatus(Status.FAILURE);
			return;
		}

		List<Task> children = this.childTaskOrchestrator.reloadExistingChildren(run);
		if (children.isEmpty()) {
			children = new ArrayList<>();
			for (Device device : devices) {
				children.add(buildChild(task, spec, device, driver, comment));
			}
			children = this.childTaskOrchestrator.preCreateChildren(run, children);
		}
		run.setStatus(this.childTaskOrchestrator.orchestrateChildren(run, children));
	}

	private static Task buildChild(Task task, RunDeviceGroupScriptTask spec, Device device, DeviceDriver driver, String comment) {
		return RunDeviceScriptTask.of(device, new RunDeviceScriptTask(null, spec.script(), driver.getName(), spec.userInputValues(),
			spec.runSnapshot(), spec.runDiagnostics(), spec.checkCompliance()), comment, task.author());
	}

}
