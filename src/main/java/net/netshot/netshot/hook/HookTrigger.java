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
package net.netshot.netshot.hook;

/**
 * An event triggering a hook.
 *
 * @param type the kind of event
 * @param item what the event is about (for {@link TriggerType#POST_TASK}: the type of task, e.g. TakeSnapshotTask)
 */
public record HookTrigger(TriggerType type, String item) {

	/**
	 * Kinds of events.
	 */
	public enum TriggerType {
		/** A task has finished. */
		POST_TASK,
	}
}
