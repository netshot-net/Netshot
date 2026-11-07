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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.RefId;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskCreation;

/**
 * Maps the requests which create a task to the command of the task service.
 */
public final class TaskCreationMapper {

	/** The parameters each type of task which can be created accepts (the ones the type of task returns, but the read only ones). */
	private static final Map<String, Set<String>> PARAMETERS = Map.ofEntries(
		Map.entry("TakeSnapshotTask", Set.of("dontRunDiagnostics", "dontCheckCompliance")),
		Map.entry("RunDiagnosticsTask", Set.of("dontCheckCompliance")),
		Map.entry("CheckComplianceTask", Set.of()),
		Map.entry("RunDeviceScriptTask", Set.of("script", "deviceDriver", "userInputValues", "runSnapshot", "runDiagnostics",
			"checkCompliance")),
		Map.entry("RunDeviceGroupScriptTask", Set.of("script", "deviceDriver", "userInputValues", "runSnapshot", "runDiagnostics",
			"checkCompliance")),
		Map.entry("ScanSubnetsTask", Set.of("domainId", "subnets")),
		Map.entry("PurgeDatabaseTask", Set.of("days", "configDays", "configSize", "configKeepDays", "moduleDays", "removeOrphanFiles")),
		Map.entry("TakeGroupSnapshotTask", Set.of("limitToOutofdateDeviceHours", "dontRunDiagnostics", "dontCheckCompliance")),
		Map.entry("CheckGroupComplianceTask", Set.of()),
		Map.entry("CheckGroupSoftwareTask", Set.of()),
		Map.entry("RunGroupDiagnosticsTask", Set.of("dontCheckCompliance")));

	private TaskCreationMapper() {
	}

	/**
	 * Gets the role a type of task needs, when it is higher than the one which creates tasks.
	 *
	 * @param type the type of task
	 * @return the role, or null if the role of the operator is enough
	 */
	public static String requiredRole(String type) {
		if ("RunDeviceScriptTask".equals(type) || "RunDeviceGroupScriptTask".equals(type)) {
			return User.ROLE_EXECUTEREADWRITE;
		}
		if ("ScanSubnetsTask".equals(type)) {
			return User.ROLE_READWRITE;
		}
		if ("PurgeDatabaseTask".equals(type)) {
			return User.ROLE_ADMIN;
		}
		return null;
	}

	/**
	 * Maps a request to create a task.
	 *
	 * @param author the name of the user creating the task
	 * @param request the request
	 * @return the command
	 * @throws NetshotException if the type or a parameter is invalid, or the schedule is incomplete
	 */
	public static TaskCreation toCreation(String author, TaskRequest request) {
		String type = request.type();
		Set<String> allowed = type == null ? null : PARAMETERS.get(type);
		if (allowed == null) {
			throw NetshotException.invalidParameter("type", "Unknown type of task '%s', or a type which can't be created.".formatted(type));
		}
		Parameters p = new Parameters(request.parameters(), allowed);
		Task.ScheduleType scheduleType = request.scheduleType() == null ? Task.ScheduleType.ASAP : request.scheduleType();
		if (scheduleType == Task.ScheduleType.AT && request.scheduleReference() == null) {
			throw NetshotException.invalidParameter("scheduleReference", "The scheduled date is required for the type AT.");
		}
		List<Long> deviceList = new ArrayList<>();
		if (request.deviceList() != null) {
			for (RefId ref : request.deviceList()) {
				if (ref == null) {
					throw NetshotException.invalidParameter("deviceList", "A device reference can't be null.");
				}
				deviceList.add(ref.id());
			}
		}
		return new TaskCreation(
			author,
			type,
			request.device() == null ? 0L : request.device().id(),
			request.group() == null ? 0L : request.group().id(),
			deviceList,
			request.scheduleMode() == null ? Task.ScheduleMode.PARALLEL : request.scheduleMode(),
			Boolean.TRUE.equals(request.stopOnFailure()),
			p.getLong("domainId", 0L),
			p.getSubnets(),
			Date.from(request.scheduleReference() == null ? Instant.now() : request.scheduleReference()),
			scheduleType,
			request.scheduleFactor() == null ? 1 : request.scheduleFactor(),
			request.priority() == null ? 5 : request.priority(),
			request.comments() == null ? "" : request.comments(),
			Boolean.TRUE.equals(request.debugEnabled()),
			p.getInt("limitToOutofdateDeviceHours", -1),
			p.getInt("days", 90),
			p.getInt("configDays", -1),
			p.getInt("configSize", 0),
			p.getInt("configKeepDays", 0),
			p.getInt("moduleDays", -1),
			p.getBoolean("removeOrphanFiles"),
			p.getString("script", ""),
			p.getString("deviceDriver", null),
			p.getStringMap("userInputValues"),
			p.getBoolean("dontRunDiagnostics"),
			p.getBoolean("dontCheckCompliance"),
			p.getBoolean("runSnapshot"),
			p.getBoolean("runDiagnostics"),
			p.getBoolean("checkCompliance"));
	}

	/**
	 * Reads the parameters of a task, checking their names and types.
	 */
	private static final class Parameters {

		private final Map<String, Object> values;

		Parameters(Map<String, Object> values, Set<String> allowed) {
			this.values = values == null ? Map.of() : values;
			for (String name : this.values.keySet()) {
				if (!allowed.contains(name)) {
					throw NetshotException.invalidParameter("parameters." + name, "Unknown parameter '%s' for this type of task.".formatted(name));
				}
			}
		}

		private static NetshotException invalid(String name, String expected) {
			return NetshotException.invalidParameter("parameters." + name, "The parameter '%s' must be %s.".formatted(name, expected));
		}

		boolean getBoolean(String name) {
			Object value = this.values.get(name);
			if (value == null) {
				return false;
			}
			if (value instanceof Boolean b) {
				return b;
			}
			throw invalid(name, "a boolean");
		}

		long getLong(String name, long defaultValue) {
			Object value = this.values.get(name);
			if (value == null) {
				return defaultValue;
			}
			if (value instanceof Integer || value instanceof Long) {
				return ((Number) value).longValue();
			}
			throw invalid(name, "an integer");
		}

		int getInt(String name, int defaultValue) {
			long value = this.getLong(name, defaultValue);
			if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
				throw invalid(name, "a smaller integer");
			}
			return (int) value;
		}

		String getString(String name, String defaultValue) {
			Object value = this.values.get(name);
			if (value == null) {
				return defaultValue;
			}
			if (value instanceof String s) {
				return s;
			}
			throw invalid(name, "a string");
		}

		// The subnets, as a list of prefixes or as a text (separated by commas, spaces or new lines)
		String getSubnets() {
			Object value = this.values.get("subnets");
			if (value == null) {
				return "";
			}
			if (value instanceof String s) {
				return s;
			}
			if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
				return String.join(",", list.stream().map(String.class::cast).toList());
			}
			throw invalid("subnets", "a list of strings");
		}

		Map<String, String> getStringMap(String name) {
			Object value = this.values.get(name);
			if (value == null) {
				return null;
			}
			if (value instanceof Map<?, ?> map) {
				Map<String, String> result = new LinkedHashMap<>();
				for (Map.Entry<?, ?> entry : map.entrySet()) {
					if (entry.getValue() != null && !(entry.getValue() instanceof String)) {
						throw invalid(name, "an object whose values are strings");
					}
					result.put(String.valueOf(entry.getKey()), (String) entry.getValue());
				}
				return result;
			}
			throw invalid(name, "an object whose values are strings");
		}
	}
}
