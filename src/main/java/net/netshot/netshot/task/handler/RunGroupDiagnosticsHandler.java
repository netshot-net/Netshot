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
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.engine.ChildTaskOrchestrator;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;

/**
 * Schedules the diagnostics of every device of a group or list.
 */
@Slf4j
public class RunGroupDiagnosticsHandler implements TaskHandler<RunGroupDiagnosticsTask> {

	private final ChildTaskOrchestrator childTaskOrchestrator;

	/** The transaction support. */
	private final Transactions transactions;

	/**
	 * Creates the handler.
	 * @param transactions the transaction support
	 * @param childTaskOrchestrator the child task orchestrator
	 */
	public RunGroupDiagnosticsHandler(Transactions transactions, ChildTaskOrchestrator childTaskOrchestrator) {
		this.transactions = transactions;
		this.childTaskOrchestrator = childTaskOrchestrator;
	}

	@Override
	public Class<RunGroupDiagnosticsTask> taskType() {
		return RunGroupDiagnosticsTask.class;
	}

	@Override
	public void run(Task task, RunGroupDiagnosticsTask spec, TaskRun run) {
		TaskTargets targets = TaskTargets.load(this.transactions, spec);
		List<Device> devices = targets.devices();
		if (targets.group() == null && devices.isEmpty()) {
			run.logger.info("Neither a device group nor a device list is set, the task will be cancelled.");
			run.setStatus(Status.CANCELLED);
			return;
		}
		log.debug("Task {}. {} device(s) to process.", task.id(), devices.size());
		String comment = targets.group() == null
			? String.format("Started due to %d-device diagnostics list", devices.size())
			: String.format("Started due to group %s diagnostics", targets.group().name());

		List<Task> children = this.childTaskOrchestrator.reloadExistingChildren(run);
		if (children.isEmpty()) {
			children = new ArrayList<>();
			for (Device device : devices) {
				children.add(RunDiagnosticsTask.of(device, comment, task.author(), spec.dontCheckCompliance()));
			}
			children = this.childTaskOrchestrator.preCreateChildren(run, children);
		}
		run.setStatus(this.childTaskOrchestrator.orchestrateChildren(run, children));
	}

}
