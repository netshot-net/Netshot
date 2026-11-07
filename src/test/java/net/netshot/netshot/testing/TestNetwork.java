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
package net.netshot.netshot.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;

/**
 * Network helpers for the tests starting the embedded HTTP server.
 */
public final class TestNetwork {

	private TestNetwork() {
	}

	/**
	 * Finds a free local TCP port, so that test runs in parallel (several worktrees)
	 * don't compete for a fixed port.
	 * @return a TCP port which was free when checked
	 */
	public static int freePort() {
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			socket.setReuseAddress(true);
			return socket.getLocalPort();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Unable to find a free TCP port", e);
		}
	}

	/**
	 * Builds the URL of the REST API served on a free local port.
	 * @return the API URL
	 */
	public static String freeLocalApiUrl() {
		return "http://localhost:" + freePort() + "/api";
	}
}
