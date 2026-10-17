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
package net.netshot.netshot.api.rest.v0.task;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;

import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskCreation;
import net.netshot.netshot.task.TaskOverview;
import net.netshot.netshot.task.TaskStats;
import net.netshot.netshot.task.TaskSummary;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Maps the tasks between the entities (or the projections) of the task package and their REST
 * representation. All the methods are pure functions.
 *
 * <p>{@link #toDto(Task)} is the single way to represent a task: every endpoint answering with a
 * task uses it. It reads a <em>detached</em> task: it only uses the row of the task and the
 * IDs of the entities it refers to (see {@code TaskRepository.find}), and fails with a
 * {@code LazyInitializationException} if it needs more.</p>
 */
public final class TaskMapper {

	private TaskMapper() {
		// Static helpers only
	}

	/**
	 * Maps a task to its full representation.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param task the task, detached
	 * @return the DTO, of the subtype matching the type of the task
	 * @throws IllegalArgumentException if the type of task has no representation
	 */
	public static TaskDto toDto(DriverRegistry driverRegistry, Task task) {
		Task concrete = (Task) Hibernate.unproxy(task);
		TaskBaseDto base = toBaseDto(concrete);
		if (concrete instanceof TakeSnapshotTask) {
			return new TaskDto.TakeSnapshot(base, deviceId(concrete));
		}
		else if (concrete instanceof CheckComplianceTask) {
			return new TaskDto.CheckCompliance(base, deviceId(concrete));
		}
		else if (concrete instanceof RunDeviceScriptTask) {
			return new TaskDto.RunDeviceScript(base, deviceId(concrete));
		}
		else if (concrete instanceof RunDiagnosticsTask) {
			return new TaskDto.RunDiagnostics(base, deviceId(concrete));
		}
		else if (concrete instanceof DiscoverDeviceTypeTask discover) {
			Device resultDevice = discover.getDiscoverResultDevice();
			return new TaskDto.DiscoverDeviceType(base, discover.getDeviceAddress(), resultDevice == null ? 0 : resultDevice.getId(),
				discoveredDeviceTypeDescription(driverRegistry, discover), discover.getSnapshotTaskId());
		}
		else if (concrete instanceof TakeGroupSnapshotTask snapshot) {
			return new TaskDto.TakeGroupSnapshot(base, deviceGroupId(concrete), snapshot.getLimitToOutofdateDeviceHours());
		}
		else if (concrete instanceof CheckGroupComplianceTask) {
			return new TaskDto.CheckGroupCompliance(base, deviceGroupId(concrete));
		}
		else if (concrete instanceof CheckGroupSoftwareTask) {
			return new TaskDto.CheckGroupSoftware(base, deviceGroupId(concrete));
		}
		else if (concrete instanceof RunGroupDiagnosticsTask) {
			return new TaskDto.RunGroupDiagnostics(base, deviceGroupId(concrete));
		}
		else if (concrete instanceof RunDeviceGroupScriptTask) {
			return new TaskDto.RunDeviceGroupScript(base, deviceGroupId(concrete));
		}
		else if (concrete instanceof PurgeDatabaseTask purge) {
			return new TaskDto.PurgeDatabase(base, purge.getConfigDays(), purge.getConfigKeepDays(), purge.getConfigSize(),
				purge.getDays(), deviceGroupId(concrete), purge.getModuleDays(), purge.isRemoveOrphanFiles());
		}
		else if (concrete instanceof ScanSubnetsTask) {
			return new TaskDto.ScanSubnets(base);
		}
		throw new IllegalArgumentException("No representation for the task type " + concrete.getClass().getSimpleName());
	}

	// The description of the discovered device type, "Unknown" when there is no such driver
	private static String discoveredDeviceTypeDescription(DriverRegistry driverRegistry, DiscoverDeviceTypeTask task) {
		DeviceDriver deviceDriver = driverRegistry.getDriverByName(task.getDiscoveredDeviceType());
		if (deviceDriver == null) {
			return "Unknown";
		}
		return deviceDriver.getDescription();
	}

	private static TaskBaseDto toBaseDto(Task task) {
		return new TaskBaseDto(
			task.getAuthor(),
			task.isCancelRequested(),
			task.getChangeDate(),
			task.isCheckCompliance(),
			task.getChildOrder(),
			task.getComments(),
			task.getCreationDate(),
			task.isDebugEnabled(),
			task.getDeviceDriver(),
			task.getExecutionDate(),
			task.getId(),
			task.getLog(),
			task.getNextExecutionDate(),
			task.getParentTaskId(),
			task.getPriority(),
			task.isRepeating(),
			task.isRunDiagnostics(),
			task.isRunSnapshot(),
			task.getRunnerId(),
			task.getScheduleFactor(),
			task.getScheduleMode(),
			task.getScheduleReference(),
			task.getScheduleType(),
			task.getScript(),
			task.getStatus(),
			task.isStopOnFailure(),
			task.getTarget(),
			task.getTaskDescription(),
			task.getUserInputValues());
	}

	// The ID of the target device, 0 for none. Only the ID of the (possibly lazy) device is read.
	private static long deviceId(Task task) {
		Device device = task.getDevice();
		return device == null ? 0 : device.getId();
	}

	// The ID of the target group, 0 for none. Only the ID of the (possibly lazy) group is read.
	private static long deviceGroupId(Task task) {
		DeviceGroup group = task.getDeviceGroup();
		return group == null ? 0 : group.getId();
	}

	/**
	 * Maps a task summary to its representation.
	 *
	 * @param summary the summary
	 * @return the DTO
	 */
	public static TaskSummaryDto toDto(TaskSummary summary) {
		return new TaskSummaryDto(
			summary.id(),
			summary.type() == null ? null : summary.type().getSimpleName(),
			summary.status(),
			summary.author(),
			summary.target(),
			summary.comments(),
			summary.creationDate(),
			summary.changeDate(),
			summary.executionDate(),
			summary.scheduleReference(),
			summary.scheduleType(),
			summary.scheduleFactor(),
			summary.priority(),
			summary.runnerId(),
			summary.debugEnabled(),
			summary.deviceId(),
			summary.deviceGroupId(),
			summary.parentTaskId(),
			summary.childOrder(),
			summary.scheduleMode(),
			summary.stopOnFailure());
	}

	/**
	 * Maps task summaries to their representation.
	 *
	 * @param summaries the summaries
	 * @return the DTOs, in the same order
	 */
	public static List<TaskSummaryDto> toSummaryDtos(List<TaskSummary> summaries) {
		List<TaskSummaryDto> dtos = new ArrayList<>(summaries.size());
		summaries.forEach(summary -> dtos.add(toDto(summary)));
		return dtos;
	}

	/**
	 * Maps the overview of the tasks to its representation.
	 *
	 * @param overview the overview
	 * @return the DTO
	 */
	public static TaskOverviewDto toDto(TaskOverview overview) {
		return new TaskOverviewDto(overview.countByStatus(), overview.threadCount());
	}

	/**
	 * Maps the statistics of the tasks to their representation.
	 *
	 * @param stats the statistics
	 * @return the DTO
	 */
	public static TaskStatsDto toDto(TaskStats stats) {
		List<TaskStatsDto.Bin> bins = new ArrayList<>(stats.bins().size());
		stats.bins().forEach(bin -> bins.add(new TaskStatsDto.Bin(bin.from(), bin.to(), bin.countByStatus())));
		return new TaskStatsDto(bins, stats.statusCounts(), stats.typeCounts());
	}

	/**
	 * Maps a request to create a task to the command of the service.
	 *
	 * @param request the request
	 * @param author the user creating the task
	 * @return the command
	 */
	public static TaskCreation toCreation(TaskRequest request, String author) {
		return new TaskCreation(
			author,
			request.getType(),
			request.getDevice(),
			request.getGroup(),
			request.getDeviceList(),
			request.getScheduleMode(),
			request.isStopOnFailure(),
			request.getDomain(),
			request.getSubnets(),
			request.getScheduleReference(),
			request.getScheduleType(),
			request.getScheduleFactor(),
			request.getSchedulePriority(),
			request.getComments(),
			request.isDebugEnabled(),
			request.getLimitToOutofdateDeviceHours(),
			request.getDaysToPurge(),
			request.getConfigDaysToPurge(),
			request.getConfigSizeToPurge(),
			request.getConfigKeepDays(),
			request.getModuleDaysToPurge(),
			request.isRemoveOrphanFiles(),
			request.getScript(),
			request.getDriver(),
			request.getUserInputs(),
			request.isDontRunDiagnostics(),
			request.isDontCheckCompliance(),
			request.isRunSnapshot(),
			request.isRunDiagnostics(),
			request.isCheckCompliance());
	}
}
