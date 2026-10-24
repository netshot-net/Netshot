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
package net.netshot.netshot.task;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.task.engine.TaskContext;

/**
 * The state of one run of a task: status, logs, debug log and results. Created by the task engine
 * when the task starts, changed by the task handler, and saved back to the task at the end of the run.
 */
public final class TaskRun {

	/**
	 * Formats a log line.
	 *
	 * @param level the level
	 * @param message the message (with {} placeholders)
	 * @param params the parameters of the message
	 * @return the log line (with final line feed)
	 */
	public static String formatLine(Level level, String message, Object... params) {
		String noNullMessage = message.replace('\0', '␀');
		return Instant.now() + " [" + level + "] " + MessageFormatter.arrayFormat(noNullMessage, params).getMessage() + "\n";
	}

	/** The task being run, as it was when the run started. */
	@Getter
	private final Task task;

	/** When the run started. */
	@Getter
	private final Instant executionDate;

	/** The status of the run. */
	@Getter
	@Setter
	private Task.Status status = Task.Status.RUNNING;

	/** The logs (without the traces). */
	private final StringBuilder logs = new StringBuilder();

	/** The full logs, only when debugging is enabled. */
	private final StringBuilder fullLogs;

	/** The debug log, once attached. */
	@Getter
	private String debugLog;

	/** The results to store in the attributes of the task. */
	@Getter
	private final Map<String, Object> results = new HashMap<>();

	/** The ID of the device the run created (discovery). */
	@Getter
	@Setter
	private Long resultDeviceId;

	/** The logger of the task, passed to the scripts and drivers. */
	public final TaskContext logger = new TaskContext() {
		@Override
		public void log(Level level, String message, Object... params) {
			TaskRun.this.log(level, message, params);
		}

		@Override
		public boolean isTracing() {
			return TaskRun.this.task.debugEnabled();
		}

		@Override
		public String getIdentifier() {
			return "%s_%d".formatted(TaskRun.this.task.typeName(), TaskRun.this.task.id());
		}
	};

	/**
	 * Starts a run.
	 *
	 * @param task the task to run
	 */
	public TaskRun(Task task) {
		this.task = task;
		this.executionDate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		this.fullLogs = task.debugEnabled() ? new StringBuilder() : null;
	}

	private synchronized void log(Level level, String message, Object... params) {
		String line = formatLine(level, message, params);
		if (this.fullLogs != null) {
			this.fullLogs.append(line);
		}
		if (level.toInt() <= Level.TRACE.toInt()) {
			// Don't log traces to base logs
			return;
		}
		this.logs.append(line);
	}

	/**
	 * Gets the logs of the run.
	 *
	 * @return the logs
	 */
	public synchronized String getLog() {
		return this.logs.toString();
	}

	/**
	 * Cancels the run.
	 *
	 * @param reason the reason, logged as a warning
	 */
	public void cancel(String reason) {
		this.status = Task.Status.CANCELLED;
		this.logger.warn(reason);
	}

	/**
	 * Marks the run as failed.
	 */
	public void fail() {
		this.status = Task.Status.FAILURE;
	}

	/**
	 * Keeps the full logs as debug log (when debugging is enabled).
	 */
	public synchronized void attachDebugLog() {
		if (this.fullLogs != null) {
			this.debugLog = this.fullLogs.toString().replace("\0", "");
		}
	}

	/**
	 * Sets a result, stored in the attributes of the task.
	 *
	 * @param key the name of the result
	 * @param value the value
	 */
	public void setResult(String key, Object value) {
		this.results.put(key, value);
	}
}
