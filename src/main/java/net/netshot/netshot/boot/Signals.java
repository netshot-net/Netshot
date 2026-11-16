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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.concurrent.NamedThreadFactory;
//CHECKSTYLE:OFF: IllegalImport
import sun.misc.Signal;
//CHECKSTYLE:ON: IllegalImport

/**
 * The signals of the server process: the services are stopped when the JVM shuts down (SIGTERM, SIGINT, exit), and
 * the configuration is reloaded on HUP.
 */
@Slf4j
public final class Signals {

	private Signals() {
	}

	/**
	 * Stops the services of the application (in the reverse order) when the JVM shuts down: on SIGTERM or SIGINT, or
	 * when the startup fails.
	 *
	 * @param application the application
	 */
	public static void stopOnShutdown(Application application) {
		Runtime.getRuntime().addShutdownHook(application.services().shutdownHook());
	}

	/**
	 * Reloads the configuration of the application on HUP. The reload runs in its own thread (the reloads are run
	 * one at a time, out of the signal dispatcher of the JVM, along with what the components do on a change).
	 *
	 * @param application the application
	 * @param files the configuration files to reload from
	 */
	public static void reloadOnHangup(Application application, List<String> files) {
		ExecutorService reloader = Executors.newSingleThreadExecutor(new NamedThreadFactory("NetshotReload", true));
		try {
			Signal.handle(new Signal("HUP"), _ -> reloader.execute(() -> application.reload(files)));
		}
		catch (IllegalArgumentException e) {
			reloader.shutdown();
			log.warn("The HUP signal isn't available on this platform: the configuration can't be reloaded.");
		}
	}
}
