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
package net.netshot.netshot.task.type;

/**
 * Marks a group-based task that schedules per-device child tasks and stays RUNNING for the
 * whole orchestration (via {@code ChildTaskOrchestrator#orchestrateChildren}), in either PARALLEL or SEQUENTIAL
 * mode -- as opposed to other group-based tasks (e.g. CheckGroupComplianceTask,
 * CheckGroupSoftwareTask) which act on the whole group directly, without spawning/tracking
 * per-device children this way. Used by the REST layer to decide whether a RUNNING task can
 * still be cancelled (its own {@code cancelRequested} flag being polled by the orchestration
 * loop), regardless of which of the two schedule modes it's running in.
 */
public interface ChildOrchestratingTask {
}
