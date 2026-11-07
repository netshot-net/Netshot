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
package net.netshot.netshot.inventory.config;

import lombok.Getter;

/**
 * Thrown when the content of a configuration item can't be given. Unlike the other failures it
 * has no {@link net.netshot.netshot.platform.error.ErrorCode}: the clients which download an item
 * only know the status code, not an error body.
 */
public class ConfigItemException extends RuntimeException {

	private static final long serialVersionUID = -1590233113702547712L;

	/** Why the item can't be given. */
	public enum Reason {
		/** There is no such configuration. */
		CONFIG_NOT_FOUND,
		/** The item exists but has no content. */
		ITEM_NOT_AVAILABLE,
		/** The configuration has no such item, or not one which can be downloaded. */
		INVALID_ITEM,
		/** The configuration could not be read. */
		DATABASE_ERROR,
	}

	/** The reason. */
	@Getter
	private final Reason reason;

	/**
	 * Instantiates a new exception.
	 *
	 * @param reason the reason
	 * @param message the message
	 */
	public ConfigItemException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}
}
