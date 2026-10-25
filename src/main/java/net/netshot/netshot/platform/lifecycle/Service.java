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
package net.netshot.netshot.platform.lifecycle;

import java.util.Map;

/**
 * A long-running component of the server (a listener, a scheduler, a background daemon, a connection pool...),
 * started and stopped by the {@link ServiceManager}, in the order given by the composition root.
 *
 * <p>A service owns its threads (created through {@code platform.concurrent.NamedThreadFactory}): {@link #start()}
 * returns once the service runs, {@link #stop()} once its threads are stopped and its resources released.</p>
 */
public interface Service {

	/**
	 * Gets the name of the service, for the logs and the status.
	 *
	 * @return the name, e.g. {@code Syslog server}
	 */
	String name();

	/**
	 * Starts the service.
	 *
	 * @throws Exception if the service can't be started
	 */
	void start() throws Exception;

	/**
	 * Stops the service, releasing its resources. Called once, if the service was started.
	 *
	 * @throws Exception if the service couldn't be stopped cleanly
	 */
	void stop() throws Exception;

	/**
	 * Whether the started service works as expected (e.g. a listener which lost its socket isn't healthy).
	 *
	 * @return true if healthy
	 */
	default boolean isHealthy() {
		return true;
	}

	/**
	 * Gets the statistics of the service (e.g. the number of received messages), for the status.
	 *
	 * @return the statistics, by name
	 */
	default Map<String, Object> stats() {
		return Map.of();
	}

	/**
	 * Creates a service from start and stop actions (e.g. a startup step without resources to release).
	 *
	 * @param name the name
	 * @param start the start action
	 * @param stop the stop action
	 * @return the service
	 */
	static Service of(String name, Action start, Action stop) {
		return new Service() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public void start() throws Exception {
				start.run();
			}

			@Override
			public void stop() throws Exception {
				stop.run();
			}
		};
	}

	/** An action which can fail. */
	@FunctionalInterface
	interface Action {
		/**
		 * Runs the action.
		 *
		 * @throws Exception if it fails
		 */
		void run() throws Exception;
	}
}
