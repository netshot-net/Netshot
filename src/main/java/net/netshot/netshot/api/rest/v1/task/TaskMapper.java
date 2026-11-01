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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongFunction;

import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;
import net.netshot.netshot.api.rest.v1.group.GroupDto;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskSummary;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.DeviceBasedTask;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.GroupBasedTask;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.task.type.TaskSpecs;

/**
 * Maps the tasks to their v1 representation.
 */
public final class TaskMapper {

	/** The path of the tasks. */
	public static final String PATH = "tasks";

	/**
	 * The objects a task refers to, already mapped (brief or full representations).
	 *
	 * @param devices the devices by ID (null for a missing one)
	 * @param groups the groups by ID
	 * @param tasks the (parent) tasks by ID
	 */
	public record Refs(LongFunction<DeviceDto> devices, LongFunction<GroupDto> groups, LongFunction<TaskDto> tasks) {

		/** No reference resolved: the references are given by ID only. */
		public static final Refs IDS_ONLY = new Refs(
			id -> new DeviceDto(id, V1Links.of("devices", id), null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null),
			id -> new GroupDto(id, V1Links.of("groups", id), null, 0, null, null, null, null, false, null),
			id -> brief(id, null, null, null));
	}

	private TaskMapper() {
	}

	private static String display(String typeName, String target) {
		String description = TaskSpecs.description(typeName);
		return target == null || target.isBlank() ? description : description + " - " + target;
	}

	/**
	 * Builds the brief representation of a task.
	 *
	 * @param id the ID
	 * @param typeName the type
	 * @param status the status
	 * @param target the target
	 * @return the representation
	 */
	public static TaskDto brief(long id, String typeName, Task.Status status, String target) {
		return new TaskDto(id, V1Links.of(PATH, id), typeName == null ? null : display(typeName, target), null, typeName, status,
			null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
			null, null, null, null, null);
	}

	/**
	 * Maps a task summary.
	 *
	 * @param summary the summary
	 * @param refs the referenced objects
	 * @return the representation (without the parameters, the log and the next execution)
	 */
	public static TaskDto toDto(TaskSummary summary, Refs refs) {
		return new TaskDto(summary.id(), V1Links.of(PATH, summary.id()), display(summary.type(), summary.target()),
			(long) summary.version(), summary.type(), summary.status(), TaskSpecs.description(summary.type()), summary.target(),
			summary.author(), summary.comments(),
			summary.deviceId() == null ? null : refs.devices().apply(summary.deviceId()),
			summary.deviceGroupId() == null ? null : refs.groups().apply(summary.deviceGroupId()),
			summary.parentTaskId() == null ? null : refs.tasks().apply(summary.parentTaskId()),
			summary.childOrder(), summary.priority(), summary.scheduleType(), summary.scheduleFactor(), summary.scheduleReference(),
			null, null, summary.scheduleMode(), summary.stopOnFailure(), summary.creationDate(), summary.changeDate(),
			summary.executionDate(), summary.runnerId(), summary.debugEnabled(), null, null, null);
	}

	/**
	 * Maps a task.
	 *
	 * @param task the task
	 * @param refs the referenced objects
	 * @return the representation
	 */
	public static TaskDto toDto(Task task, Refs refs) {
		Long deviceId = task.spec() instanceof DeviceBasedTask t ? t.deviceId() : null;
		Long groupId = task.spec() instanceof GroupBasedTask t ? t.deviceGroupId() : null;
		String type = task.spec().typeName();
		return new TaskDto(task.id(), V1Links.of(PATH, task.id()), display(type, task.target()), (long) task.version(), type,
			task.status(), task.description(), task.target(), task.author(), task.comments(),
			deviceId == null ? null : refs.devices().apply(deviceId),
			groupId == null ? null : refs.groups().apply(groupId),
			task.parentTaskId() == null ? null : refs.tasks().apply(task.parentTaskId()),
			task.childOrder(), task.priority(), task.scheduleType(), task.scheduleFactor(), task.scheduleReference(),
			task.nextExecutionDate() == null ? null : task.nextExecutionDate().toInstant(), task.isRepeating(),
			task.scheduleMode(), task.stopOnFailure(), task.creationDate(), task.changeDate(), task.executionDate(),
			task.runnerId(), task.debugEnabled(), task.cancelRequested(), task.log(), parameters(task));
	}

	/**
	 * Gets the parameters of the type of a task (the target device or group is a reference of its own).
	 *
	 * @param task the task
	 * @return the parameters, by name
	 */
	static Map<String, Object> parameters(Task task) {
		Map<String, Object> p = new LinkedHashMap<>();
		switch (task.spec()) {
			case TakeSnapshotTask s -> {
				p.put("automatic", s.automatic());
				p.put("dontRunDiagnostics", s.dontRunDiagnostics());
				p.put("dontCheckCompliance", s.dontCheckCompliance());
			}
			case RunDiagnosticsTask s -> p.put("dontCheckCompliance", s.dontCheckCompliance());
			case CheckComplianceTask _ -> {
			}
			case RunDeviceScriptTask s -> {
				p.put("script", s.script());
				p.put("deviceDriver", s.deviceDriver());
				p.put("userInputValues", s.userInputValues());
				p.put("runSnapshot", s.runSnapshot());
				p.put("runDiagnostics", s.runDiagnostics());
				p.put("checkCompliance", s.checkCompliance());
			}
			case DiscoverDeviceTypeTask s -> {
				p.put("deviceAddress", s.deviceAddress());
				p.put("domainId", s.domainId());
				p.put("credentialSetIds", s.credentialSetIds());
				p.put("deviceComments", s.deviceComments());
				p.put("deviceTags", s.deviceTags());
				p.put("discoveredDeviceType", s.discoveredDeviceType());
				p.put("snapshotTaskId", s.snapshotTaskId() == 0 ? null : s.snapshotTaskId());
				p.put("discoveredDeviceId", s.discoverResultDeviceId());
			}
			case ScanSubnetsTask s -> {
				p.put("domainId", s.domainId());
				p.put("subnets", s.subnets().stream().map(n -> n.getPrefix()).sorted().toList());
			}
			case PurgeDatabaseTask s -> {
				p.put("days", s.days());
				p.put("configDays", s.configDays());
				p.put("configSize", s.configSize());
				p.put("configKeepDays", s.configKeepDays());
				p.put("moduleDays", s.moduleDays());
				p.put("removeOrphanFiles", s.removeOrphanFiles());
			}
			case TakeGroupSnapshotTask s -> {
				p.put("limitToOutofdateDeviceHours", s.limitToOutofdateDeviceHours());
				p.put("dontRunDiagnostics", s.dontRunDiagnostics());
				p.put("dontCheckCompliance", s.dontCheckCompliance());
			}
			case CheckGroupComplianceTask _ -> {
			}
			case CheckGroupSoftwareTask _ -> {
			}
			case RunGroupDiagnosticsTask s -> p.put("dontCheckCompliance", s.dontCheckCompliance());
			case RunDeviceGroupScriptTask s -> {
				p.put("script", s.script());
				p.put("deviceDriver", s.deviceDriver());
				p.put("userInputValues", s.userInputValues());
				p.put("runSnapshot", s.runSnapshot());
				p.put("runDiagnostics", s.runDiagnostics());
				p.put("checkCompliance", s.checkCompliance());
			}
		}
		return p;
	}
}
