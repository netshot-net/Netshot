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
package net.netshot.netshot.testing.fake;

import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

import net.netshot.netshot.platform.log.TaskContext;

public class FakeTaskContext implements TaskContext {

	private final StringBuffer buffer = new StringBuffer();

	public String getLog() {
		return buffer.toString();
	}

	@Override
	public void log(Level level, String message, Object... params) {
		buffer.append("[%s] ".formatted(level.toString()));
		buffer.append(
			MessageFormatter.arrayFormat(message, params).getMessage());
		buffer.append("\n");
	}

	@Override
	public String getIdentifier() {
		return "Test";
	}
}
