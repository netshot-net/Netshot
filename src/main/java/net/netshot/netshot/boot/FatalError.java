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
package net.netshot.netshot.boot;

import java.io.PrintStream;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.lifecycle.ServiceStartException;

/**
 * Reports the error which stops an entry point (the server, a tool), once in the log and once on the console.
 */
@Slf4j
public final class FatalError {

	/** The prefix of the console message. */
	static final String PREFIX = "NETSHOT FATAL ERROR: ";

	private FatalError() {
	}

	/**
	 * Reports a fatal error: on the console (the error stream), and in the log unless it's already there.
	 * <ul>
	 * <li>A {@link BootstrapException} is logged by the bootstrap, once the logging is set up (before, the console is
	 * the only output); its message tells why, its cause (if any) where.</li>
	 * <li>A {@link ServiceStartException} is logged by the service manager, with its cause: the console only gets the
	 * message and the cause.</li>
	 * <li>Any other error is logged with its stack trace, and printed with it.</li>
	 * </ul>
	 *
	 * @param e the error
	 */
	public static void report(Exception e) {
		report(e, System.err);
	}

	/**
	 * Reports a fatal error.
	 *
	 * @param e the error
	 * @param console the console stream
	 */
	static void report(Exception e, PrintStream console) {
		switch (e) {
			case BootstrapException _ -> {
				console.println(PREFIX + e.getMessage());
				if (e.getCause() != null) {
					e.printStackTrace(console);
				}
			}
			case ServiceStartException _ -> console.println(PREFIX + e.getMessage()
				+ (e.getCause() == null ? "" : ": " + e.getCause().getMessage()) + " (see the log)");
			default -> {
				log.error("Fatal error", e);
				console.println(PREFIX + e.getMessage());
				e.printStackTrace(console);
			}
		}
	}
}
