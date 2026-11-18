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
package net.netshot.netshot.platform.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import lombok.extern.slf4j.Slf4j;

/**
 * The local machine.
 */
@Slf4j
public final class LocalHost {

	private LocalHost() {
	}

	/**
	 * Gets the hostname of the local machine (read once, on first use).
	 *
	 * @return the hostname, or {@code unknown} if it can't be read
	 */
	public static String name() {
		return Hostname.VALUE;
	}

	/** Holder of the machine hostname, read once, on first use (lazy, thread-safe class initialization). */
	private static final class Hostname {
		static final String VALUE = read();

		private static String read() {
			String hostname = null;
			try {
				Process process = Runtime.getRuntime().exec(new String[] { "hostname" });
				BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
				hostname = stdInput.readLine();
			}
			catch (IOException e) {
				log.error("Error while getting local machine hostname", e);
			}
			return hostname == null ? "unknown" : hostname;
		}
	}
}
