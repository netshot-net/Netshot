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
package net.netshot.netshot.driver;

/**
 * Raised when the driver of a device isn't loaded.
 */
public final class MissingDeviceDriverException extends Exception {
	private static final long serialVersionUID = -7286042265874166550L;

	/**
	 * Instantiates the exception.
	 *
	 * @param message the message
	 */
	public MissingDeviceDriverException(String message) {
		super(message);
	}
}
