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
package net.netshot.netshot.platform.log;

import org.graalvm.polyglot.HostAccess.Export;
import org.slf4j.event.Level;

/**
 * Task context (logging, etc.) to be attached to scripts etc.
 */
public interface TaskContext {

	@Export
	String getIdentifier();

	/**
	 * Convert a string to an hexadecimal representation.
	 * @param text The original text
	 * @return the hexadecimal representation of the text
	 */
	default String toHexAscii(String text) {
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			if (i % 32 == 0 && i > 0) {
				hex.append("\n");
			}
			hex.append(" ").append(String.format("%02x", (int) text.charAt(i)));
		}
		return hex.toString();
	}

	default boolean isTracing() {
		return true;
	}

	void log(Level level, String message, Object... params);

	@Export
	default void hexTrace(String message) {
		this.log(Level.TRACE, "Hexadecimal\n" + toHexAscii(message));
	}

	@Export
	default void trace(String message, Object... params) {
		this.log(Level.TRACE, message, params);
	}

	@Export
	default void debug(String message, Object... params) {
		this.log(Level.DEBUG, message, params);
	}

	@Export
	default void info(String message, Object... params) {
		this.log(Level.INFO, message, params);
	}

	@Export
	default void warn(String message, Object... params) {
		this.log(Level.WARN, message, params);
	}

	@Export
	default void error(String message, Object... params) {
		this.log(Level.ERROR, message, params);
	}

}
