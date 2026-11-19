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

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
		/** The hostname of the machine, on Linux (as the hostname command reads it). */
		private static final Path KERNEL_HOSTNAME = Path.of("/proc/sys/kernel/hostname");

		/** The hostname (after KERNEL_HOSTNAME: the static fields are initialized in order). */
		static final String VALUE = read();

		private static String read() {
			try {
				if (Files.isReadable(KERNEL_HOSTNAME)) {
					String hostname = Files.readString(KERNEL_HOSTNAME, StandardCharsets.UTF_8).strip();
					if (!hostname.isEmpty()) {
						return hostname;
					}
				}
			}
			catch (IOException e) {
				log.warn("Unable to read the hostname from {}.", KERNEL_HOSTNAME, e);
			}
			try {
				// Other systems: can resolve the name
				return InetAddress.getLocalHost().getHostName();
			}
			catch (IOException e) {
				log.error("Unable to get the hostname of the local machine.", e);
				return "unknown";
			}
		}
	}
}
