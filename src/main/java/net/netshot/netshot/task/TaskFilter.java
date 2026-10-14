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

import java.util.Set;

/**
 * The criteria of the task list. A {@code null} or empty component means no restriction.
 *
 * @param statuses the statuses to include
 * @param types the simple class names of the task types to include (unknown names are ignored)
 * @param after only the tasks executed (or changed, if never executed) after this date, in milliseconds since 1970
 * @param before only the tasks executed (or changed, if never executed) before this date, in milliseconds since 1970
 * @param parentTaskId only the child tasks of this parent task
 */
public record TaskFilter(Set<Task.Status> statuses, Set<String> types, Long after, Long before, Long parentTaskId) {
}
