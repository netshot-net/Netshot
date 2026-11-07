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

import java.time.Instant;
import java.util.Set;

/**
 * A hook, run after some events (see {@link HookTrigger}).
 */
public sealed interface Hook permits WebHook {

	/**
	 * Gets the ID of the hook (0 for a hook which is not stored yet).
	 *
	 * @return the ID
	 */
	long id();

	/**
	 * Gets the version of the hook, incremented on each change.
	 *
	 * @return the version
	 */
	int version();

	/**
	 * Gets the date of the last change of the hook.
	 *
	 * @return the date
	 */
	Instant changeDate();

	/**
	 * Gets the name of the hook (unique).
	 *
	 * @return the name
	 */
	String name();

	/**
	 * Tells whether the hook is enabled.
	 *
	 * @return true if enabled
	 */
	boolean enabled();

	/**
	 * Gets the triggers of the hook (null when not given in a request).
	 *
	 * @return the triggers
	 */
	Set<HookTrigger> triggers();

	/**
	 * Gets the same hook with another ID.
	 *
	 * @param newId the ID
	 * @return the hook
	 */
	Hook withId(long newId);

	/**
	 * Gets the same hook with other triggers.
	 *
	 * @param newTriggers the triggers
	 * @return the hook
	 */
	Hook withTriggers(Set<HookTrigger> newTriggers);
}
