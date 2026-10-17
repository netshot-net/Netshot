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
import java.util.Calendar;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.engine.ChildTaskOrchestrator;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Schedules the snapshot of every device of a group or list.
 */
@Slf4j
public class TakeGroupSnapshotHandler implements TaskHandler<TakeGroupSnapshotTask> {

	private final ChildTaskOrchestrator childTaskOrchestrator;

	/**
	 * Creates the handler.
	 * @param childTaskOrchestrator the child task orchestrator
	 */
	public TakeGroupSnapshotHandler(ChildTaskOrchestrator childTaskOrchestrator) {
		this.childTaskOrchestrator = childTaskOrchestrator;
	}

	@Override
	public Class<TakeGroupSnapshotTask> taskType() {
		return TakeGroupSnapshotTask.class;
	}

	@Override
	public void run(TakeGroupSnapshotTask task) {
		List<Device> allDevices = task.getTargetDevices();
		if (task.getDeviceGroup() == null && allDevices.isEmpty()) {
			task.logger.info("Neither a device group nor a device list is set, the task will be cancelled.");
			task.setStatus(Status.CANCELLED);
			return;
		}
		String comment = task.getDeviceGroup() == null
			? String.format("Started due to %d-device snapshot list", allDevices.size())
			: String.format("Started due to group %s snapshot", task.getDeviceGroup().getName());

		Calendar referenceDate = Calendar.getInstance();
		referenceDate.add(Calendar.HOUR, -task.getLimitToOutofdateDeviceHours());
		List<Device> devices = new ArrayList<>();
		for (Device device : allDevices) {
			if (referenceDate.getTime().before(device.getChangeDate())) {
				task.logger.info("Ignoring device {} because it changed less than {} hours ago",
					device.getName(), task.getLimitToOutofdateDeviceHours());
				continue;
			}
			devices.add(device);
		}
		log.debug("Task {}. {} device(s) to process (out of {}).", task.getId(), devices.size(), allDevices.size());

		List<Task> children = this.childTaskOrchestrator.reloadExistingChildren(task);
		if (children.isEmpty()) {
			children = new ArrayList<>();
			for (Device device : devices) {
				children.add(new TakeSnapshotTask(device, comment, task.getAuthor(), false,
					task.isDontRunDiagnostics(), task.isDontCheckCompliance()));
			}
			this.childTaskOrchestrator.preCreateChildren(task, children);
		}
		task.setStatus(this.childTaskOrchestrator.orchestrateChildren(task, children));
	}

}
