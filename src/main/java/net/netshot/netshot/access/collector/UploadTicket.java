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
package net.netshot.netshot.access.collector;

import java.nio.file.Path;
import java.util.Set;

import net.netshot.netshot.access.collector.SshServer.SessionLogBuffer;
import net.netshot.netshot.platform.net.NetworkAddress;

/**
 * This class represents a "ticket" or permission to upload data
 * to the embedded server.
 */
public interface UploadTicket {

	/** Interface to mark possible ticket owners. */
	interface Owner {
		// Empty
	}

	/**
	 * Get the owner of the ticket.
	 *
	 * @return the owner
	 */
	Owner getOwner();

	/**
	 * Get the allowed protocols (SFTP, SCP).
	 *
	 * @return the allowed protocols
	 */
	Set<TransferProtocol> getAllowedProtocols();

	/**
	 * Get the unique username for this ticket.
	 *
	 * @return the username
	 */
	String getUsername();

	/**
	 * Check the password.
	 *
	 * @param source the source address
	 * @param password the password
	 * @return true if password is valid
	 */
	boolean checkPassword(NetworkAddress source, String password);

	/**
	 * Get the file path where to write the file.
	 *
	 * @return the root path
	 */
	Path getRootPath();

	/**
	 * Check whether the ticket is still valid.
	 *
	 * @return true if valid
	 */
	boolean isValid();

	/**
	 * Called when the SSH session is authenticated and started.
	 *
	 * @param logBuffer the SSH session logs (protocol negotiation details)
	 */
	void onSessionStarted(SessionLogBuffer logBuffer);

	/**
	 * Called when the SSH session is closed.
	 */
	void onSessionStopped();

	/**
	 * Called when a file has been written via SFTP or SCP.
	 *
	 * @param filePath the relative path from the root path
	 * @return true if the file was consumed, false to remove it
	 */
	boolean onFileWritten(Path filePath);
}
