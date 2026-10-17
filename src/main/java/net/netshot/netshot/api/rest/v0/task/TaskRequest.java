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

import java.util.Date;
import java.util.List;
import java.util.Map;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.task.Task;

/**
 * The request to create a task (any type, see {@code type}) or to edit one (only {@code cancelled} is used).
 *
 * <p>Unlike the other request types, this one is kept as a bean rather than a record: the request has
 * about thirty properties, and for every one of them an absent property, an explicit {@code null} and a
 * value must give the three outcomes they always gave (a default value, {@code null} or zero, the value).
 * A bean with initialized fields does that for free.</p>
 */
@XmlRootElement(name = "task")
@XmlAccessorType(XmlAccessType.NONE)
public class TaskRequest {

	/** The id. */
	@Schema(description = "Task unique ID")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private long id;

	/** The cancelled status. */
	@Schema(description = "Set to cancel the task")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private boolean cancelled;

	/** The type. */
	@Schema(description = "Type of task")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private String type = "";

	/** The group. */
	@Schema(description = "The group ID for group-based task")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private Long group = 0L;

	/** The one-time, ordered device list, as an alternative to a device group. */
	@Schema(description = "The ordered list of device IDs for a one-time device list, as an alternative to a group")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private List<Long> deviceList;

	/** Parallel vs sequential scheduling of child tasks (group-based tasks only). */
	@Schema(description = "Parallel vs sequential scheduling of the per-device child tasks (group-based tasks only)")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private Task.ScheduleMode scheduleMode = Task.ScheduleMode.PARALLEL;

	/** Whether to stop scheduling further child tasks after one fails (sequential mode only). */
	@Schema(description = "Whether to stop scheduling further child tasks after one fails (sequential mode only)")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private boolean stopOnFailure;

	/** The device. */
	@Schema(description = "The device ID for device-based task")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private Long device = 0L;

	/** The domain. */
	@Schema(description = "The domain ID when applicable")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private Long domain = 0L;

	/** The subnets. */
	@Schema(description = "Subnets to scan (comma, space or new line separated)")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private String subnets = "";

	/** The schedule reference. */
	@Schema(description = "Scheduling reference date for scheduled task")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private Date scheduleReference = new Date();

	/** The schedule type. */
	@Schema(description = "Scheduling mode (ASAP, once, recurring...)")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private Task.ScheduleType scheduleType = Task.ScheduleType.ASAP;

	/** The schedule factor. */
	@Schema(description = "Scheduling factor for recurring task")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private int scheduleFactor = 1;

	/** The task priority. */
	@Schema(description = "Priority for this task, from 1 to 9 (5 is default, more is better)")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private int schedulePriority = 5;

	/** The comments. */
	@Schema(description = "Task comment")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private String comments = "";

	@Schema(description = "Ignore devices that had a successful snapshot in the last given hours")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private int limitToOutofdateDeviceHours = -1;

	@Schema(description = "Purge tasks older than this number of days")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private int daysToPurge = 90;

	@Schema(description = "Purge configurations older than this number of days")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private int configDaysToPurge = -1;

	@Schema(description = "Purge configurations bigger than this size (KB)")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private int configSizeToPurge;

	@Schema(description = "When purging old configurations, keep a configuration every n days")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private int configKeepDays;

	@Schema(description = "Purge removed hardware modules older than this number of days")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private int moduleDaysToPurge = -1;

	@Schema(description = "Remove orphan binary attribute files (no matching database record)")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private boolean removeOrphanFiles = false;

	@Schema(description = "The script to execute")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private String script = "";

	@Schema(description = "The device driver to use")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private String driver;

	@Schema(description = "The user inputs for the script")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private Map<String, String> userInputs;

	@Schema(description = "Enable task debugging")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private boolean debugEnabled;

	/** Disable automatic diagnostic task (applies to snapshot tasks). */
	@Schema(description = "Disable automatic diagnostic task (applies to snapshot tasks)")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private boolean dontRunDiagnostics;

	/** Disable automatic check compliance task (applies to snapshot and diagnostic tasks).  */
	@Schema(description = "Disable automatic check compliance task (applies to snapshot and diagnostic tasks)")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private boolean dontCheckCompliance;

	/** Automatically run a snapshot after successful script execution (applies to script tasks). */
	@Schema(description = "Automatically run a snapshot after successful script execution (applies to script tasks)")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private boolean runSnapshot;

	/** Automatically run diagnostics after successful script execution (applies to script tasks). */
	@Schema(description = "Automatically run diagnostics after successful script execution (applies to script tasks)")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private boolean runDiagnostics;

	/** Automatically check compliance after successful script execution (applies to script tasks). */
	@Schema(description = "Automatically check compliance after successful script execution (applies to script tasks)")
	@Getter(onMethod = @__({
		@XmlElement
	}))
	@Setter
	private boolean checkCompliance;
}
