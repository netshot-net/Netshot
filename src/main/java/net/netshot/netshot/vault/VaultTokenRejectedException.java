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
 * Thrown when Vault refuses the client token of a read (HTTP 403): the token may have been revoked, or have used up
 * its number of uses, before its lease ends. The cached token is then replaced by a new login.
 */
public class VaultTokenRejectedException extends VaultException {

	private static final long serialVersionUID = 1L;

	public VaultTokenRejectedException(String message) {
		super(message);
	}

}
