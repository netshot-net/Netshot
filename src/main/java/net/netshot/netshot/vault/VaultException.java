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
package net.netshot.netshot.vault;

/**
 * Thrown when a Vault-backed secret cannot be resolved (login failure,
 * unreachable instance, missing path/key, etc.). Callers must treat this as
 * a hard failure of whatever device connection/task triggered the
 * resolution - there is no stale-value fallback.
 */
public class VaultException extends Exception {

	private static final long serialVersionUID = 1L;

	public VaultException(String message) {
		super(message);
	}

	public VaultException(String message, Throwable cause) {
		super(message, cause);
	}

}
