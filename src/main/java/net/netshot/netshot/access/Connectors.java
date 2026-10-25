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
package net.netshot.netshot.access;

import org.apache.sshd.client.SshClient;

import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.vault.SecretResolver;

/**
 * What the device accesses and the device scripts need from the server: the resolver of the secrets of
 * the credentials (local or Vault-backed), the shared SSH client and the transaction support.
 *
 * @param secrets resolves the secrets of the credentials
 * @param sshClient the shared SSH client (see {@link SshClientService}), or null when no device is contacted
 * @param transactions the transaction support (the credential sets usable on a device, the data the scripts
 *        read), or null without database
 */
public record Connectors(SecretResolver secrets, SshClient sshClient, Transactions transactions) {

	/** For the code which contacts no device: the local secrets only, no SSH client and no database. */
	public static final Connectors OFFLINE = new Connectors(SecretResolver.LOCAL, null, null);
}
