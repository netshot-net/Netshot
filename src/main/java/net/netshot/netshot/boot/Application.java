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

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.lifecycle.ServiceManager;
import net.netshot.netshot.platform.lifecycle.ServiceStartException;

/**
 * The running application: the object graph ({@link Wiring}) built from a configuration, and the lifecycle of its
 * services. The server, the tools and the tests all go through it: create (without side effect), migrate the
 * database schema, start the services (all, or the ones needed), close.
 */
@Slf4j
public final class Application implements AutoCloseable {

	private final ConfigStore config;
	private final Wiring wiring;
	private final ServiceManager services;

	private Application(ConfigStore config, Wiring wiring) {
		this.config = config;
		this.wiring = wiring;
		this.services = wiring.services();
	}

	/**
	 * Builds the application, without side effect (nothing is started).
	 *
	 * @param config the configuration store
	 * @return the application
	 */
	public static Application create(ConfigStore config) {
		return new Application(config, Wiring.create(config));
	}

	/**
	 * Builds the application around an existing registry of device drivers (loaded or not), without side effect.
	 *
	 * @param config the configuration store
	 * @param driverRegistry the registry of the device drivers
	 * @return the application
	 */
	public static Application create(ConfigStore config, DriverRegistry driverRegistry) {
		return new Application(config, Wiring.create(config, driverRegistry));
	}

	/**
	 * Gets the configuration store.
	 *
	 * @return the configuration store
	 */
	public ConfigStore config() {
		return this.config;
	}

	/**
	 * Gets the object graph.
	 *
	 * @return the wiring
	 */
	public Wiring wiring() {
		return this.wiring;
	}

	/**
	 * Gets the manager of the services.
	 *
	 * @return the service manager
	 */
	public ServiceManager services() {
		return this.services;
	}

	/**
	 * Updates the database schema, if necessary (before the services are started).
	 *
	 * @throws Exception if the update fails
	 */
	public void migrate() throws Exception {
		log.info("Updating the database schema, if necessary.");
		Database.update(this.config.current());
	}

	/**
	 * Starts all the services, in order.
	 *
	 * @throws ServiceStartException if a service fails to start (the started ones are stopped then)
	 */
	public void start() throws ServiceStartException {
		this.services.start();
	}

	/**
	 * Starts some of the services, in order (the tools and the tests).
	 *
	 * @param ids the IDs of the services to start (e.g. {@code database})
	 * @throws ServiceStartException if a service fails to start (the started ones are stopped then)
	 */
	public void start(String... ids) throws ServiceStartException {
		this.services.start(List.of(ids));
	}

	/**
	 * Reloads the configuration from its source, the files given at startup (the services are notified of the change
	 * by the configuration store). If the configuration can't be read, the current one is kept.
	 *
	 * @return true if the configuration was reloaded
	 */
	public boolean reload() {
		return this.config.reload();
	}

	/**
	 * Stops the started services, in the reverse order. Can be called several times.
	 */
	@Override
	public void close() {
		this.services.stop();
	}
}
