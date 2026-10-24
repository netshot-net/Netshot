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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
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
import net.netshot.netshot.task.type.TaskSpec;
import net.netshot.netshot.task.type.TaskSpecs;

/**
 * Maps the tasks between the records (or the projections) of the task package and their REST
 * representation. All the methods are pure functions.
 *
 * <p>{@link #toDto(Task)} is the single way to represent a task: every endpoint answering with a
 * task uses it. It only uses the task record, with the IDs of the objects it refers to.</p>
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
		TaskBaseDto base = toBaseDto(task);
		TaskSpec spec = task.spec();
		return switch (spec) {
			case TakeSnapshotTask s -> new TaskDto.TakeSnapshot(base, TaskSpecs.deviceId(s));
			case CheckComplianceTask s -> new TaskDto.CheckCompliance(base, TaskSpecs.deviceId(s));
			case RunDeviceScriptTask s -> new TaskDto.RunDeviceScript(base, TaskSpecs.deviceId(s));
			case RunDiagnosticsTask s -> new TaskDto.RunDiagnostics(base, TaskSpecs.deviceId(s));
			case DiscoverDeviceTypeTask s -> new TaskDto.DiscoverDeviceType(base, s.deviceAddress(),
				s.discoverResultDeviceId() == null ? 0 : s.discoverResultDeviceId(),
				discoveredDeviceTypeDescription(driverRegistry, s), s.snapshotTaskId());
			case TakeGroupSnapshotTask s -> new TaskDto.TakeGroupSnapshot(base, TaskSpecs.deviceGroupId(s), s.limitToOutofdateDeviceHours());
			case CheckGroupComplianceTask s -> new TaskDto.CheckGroupCompliance(base, TaskSpecs.deviceGroupId(s));
			case CheckGroupSoftwareTask s -> new TaskDto.CheckGroupSoftware(base, TaskSpecs.deviceGroupId(s));
			case RunGroupDiagnosticsTask s -> new TaskDto.RunGroupDiagnostics(base, TaskSpecs.deviceGroupId(s));
			case RunDeviceGroupScriptTask s -> new TaskDto.RunDeviceGroupScript(base, TaskSpecs.deviceGroupId(s));
			case PurgeDatabaseTask s -> new TaskDto.PurgeDatabase(base, s.configDays(), s.configKeepDays(), s.configSize(),
				s.days(), TaskSpecs.deviceGroupId(s), s.moduleDays(), s.removeOrphanFiles());
			case ScanSubnetsTask _ -> new TaskDto.ScanSubnets(base);
		};
	}

	// The description of the discovered device type, "Unknown" when there is no such driver
	private static String discoveredDeviceTypeDescription(DriverRegistry driverRegistry, DiscoverDeviceTypeTask task) {
		DeviceDriver deviceDriver = driverRegistry.getDriverByName(task.discoveredDeviceType());
		if (deviceDriver == null) {
			return "Unknown";
		}
		return deviceDriver.getDescription();
	}

	private static Date date(Instant instant) {
		return instant == null ? null : Date.from(instant);
	}

	private static TaskBaseDto toBaseDto(Task task) {
		TaskSpec spec = task.spec();
		return new TaskBaseDto(
			task.author(),
			task.cancelRequested(),
			date(task.changeDate()),
			TaskSpecs.checkCompliance(spec),
			task.childOrder(),
			task.comments(),
			date(task.creationDate()),
			task.debugEnabled(),
			TaskSpecs.deviceDriver(spec),
			date(task.executionDate()),
			task.id(),
			task.log(),
			task.nextExecutionDate(),
			task.parentTaskId(),
			task.priority(),
			task.isRepeating(),
			TaskSpecs.runDiagnostics(spec),
			TaskSpecs.runSnapshot(spec),
			task.runnerId(),
			task.scheduleFactor(),
			task.scheduleMode(),
			date(task.scheduleReference()),
			task.scheduleType(),
			TaskSpecs.script(spec),
			task.status(),
			task.stopOnFailure(),
			task.target(),
			task.description(),
			TaskSpecs.userInputValues(spec));
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
			summary.type(),
			summary.status(),
			summary.author(),
			summary.target(),
			summary.comments(),
			date(summary.creationDate()),
			date(summary.changeDate()),
			date(summary.executionDate()),
			date(summary.scheduleReference()),
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
