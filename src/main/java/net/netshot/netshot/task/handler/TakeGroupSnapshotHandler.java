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
package net.netshot.netshot.task.handler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.engine.ChildTaskOrchestrator;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Schedules the snapshot of every device of a group or list.
 */
@Slf4j
public class TakeGroupSnapshotHandler implements TaskHandler<TakeGroupSnapshotTask> {

	private final ChildTaskOrchestrator childTaskOrchestrator;

	/** The transaction support. */
	private final Transactions transactions;

	/**
	 * Creates the handler.
	 * @param transactions the transaction support
	 * @param childTaskOrchestrator the child task orchestrator
	 */
	public TakeGroupSnapshotHandler(Transactions transactions, ChildTaskOrchestrator childTaskOrchestrator) {
		this.transactions = transactions;
		this.childTaskOrchestrator = childTaskOrchestrator;
	}

	@Override
	public Class<TakeGroupSnapshotTask> taskType() {
		return TakeGroupSnapshotTask.class;
	}

	@Override
	public void run(Task task, TakeGroupSnapshotTask spec, TaskRun run) {
		TaskTargets targets = TaskTargets.load(this.transactions, spec);
		List<Device> allDevices = targets.devices();
		if (targets.group() == null && allDevices.isEmpty()) {
			run.logger.info("Neither a device group nor a device list is set, the task will be cancelled.");
			run.setStatus(Status.CANCELLED);
			return;
		}
		String comment = targets.group() == null
			? String.format("Started due to %d-device snapshot list", allDevices.size())
			: String.format("Started due to group %s snapshot", targets.group().name());

		Calendar referenceDate = Calendar.getInstance();
		referenceDate.add(Calendar.HOUR, -spec.limitToOutofdateDeviceHours());
		List<Device> devices = new ArrayList<>();
		for (Device device : allDevices) {
			if (referenceDate.getTime().toInstant().isBefore(device.changeDate())) {
				run.logger.info("Ignoring device {} because it changed less than {} hours ago",
					device.name(), spec.limitToOutofdateDeviceHours());
				continue;
			}
			devices.add(device);
		}
		log.debug("Task {}. {} device(s) to process (out of {}).", task.id(), devices.size(), allDevices.size());

		List<Task> children = this.childTaskOrchestrator.reloadExistingChildren(run);
		if (children.isEmpty()) {
			children = new ArrayList<>();
			for (Device device : devices) {
				children.add(TakeSnapshotTask.of(device, comment, task.author(), false,
					spec.dontRunDiagnostics(), spec.dontCheckCompliance()));
			}
			children = this.childTaskOrchestrator.preCreateChildren(run, children);
		}
		run.setStatus(this.childTaskOrchestrator.orchestrateChildren(run, children));
	}

}
