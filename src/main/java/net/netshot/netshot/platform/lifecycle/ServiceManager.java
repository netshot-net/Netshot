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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Starts the services in the given order, and stops them in the reverse order.
 *
 * <p>If a service fails to start, the services already started are stopped (in the reverse order) and
 * {@link #start()} throws: the server doesn't run half started. {@link #stop()} stops every running service,
 * even if some of them fail to stop. Typically called by a shutdown hook (see {@link #shutdownHook()}).</p>
 */
@Slf4j
public final class ServiceManager {

	/** A managed service, with its state. */
	private static final class Managed {
		private final Service service;
		private volatile ServiceState state = ServiceState.NEW;
		private volatile String error;

		private Managed(Service service) {
			this.service = service;
		}
	}

	/** The services, in start order. */
	private final List<Managed> services = new ArrayList<>();

	/**
	 * Creates the manager.
	 *
	 * @param services the services, in start order
	 */
	public ServiceManager(List<Service> services) {
		for (Service service : services) {
			this.services.add(new Managed(service));
		}
	}

	/**
	 * Starts the services, in order.
	 *
	 * @throws ServiceStartException if a service fails to start (the started ones are stopped then)
	 */
	public synchronized void start() throws ServiceStartException {
		for (Managed managed : this.services) {
			if (managed.state != ServiceState.NEW) {
				continue;
			}
			String name = managed.service.name();
			log.info("Starting the {}.", name);
			managed.state = ServiceState.STARTING;
			long startTime = System.currentTimeMillis();
			try {
				managed.service.start();
				managed.state = ServiceState.RUNNING;
				log.debug("The {} was started in {}ms.", name, System.currentTimeMillis() - startTime);
			}
			catch (Exception | LinkageError e) {
				managed.state = ServiceState.FAILED;
				managed.error = String.valueOf(e.getMessage());
				log.error("Unable to start the {}, stopping the started services.", name, e);
				this.stop();
				throw new ServiceStartException("Unable to start the %s".formatted(name), e);
			}
		}
	}

	/**
	 * Stops the running services, in the reverse order. Can be called several times.
	 */
	public synchronized void stop() {
		for (int i = this.services.size() - 1; i >= 0; i--) {
			Managed managed = this.services.get(i);
			if (managed.state != ServiceState.RUNNING) {
				continue;
			}
			String name = managed.service.name();
			log.info("Stopping the {}.", name);
			managed.state = ServiceState.STOPPING;
			long startTime = System.currentTimeMillis();
			try {
				managed.service.stop();
				managed.state = ServiceState.STOPPED;
				log.debug("The {} was stopped in {}ms.", name, System.currentTimeMillis() - startTime);
			}
			catch (Exception | LinkageError e) {
				managed.state = ServiceState.FAILED;
				managed.error = String.valueOf(e.getMessage());
				log.error("Error while stopping the {}.", name, e);
			}
		}
	}

	/**
	 * Gets the status of the services.
	 *
	 * @return the status of each service, in start order
	 */
	public List<ServiceStatus> status() {
		List<ServiceStatus> status = new ArrayList<>();
		for (Managed managed : this.services) {
			boolean healthy = false;
			Map<String, Object> stats = Map.of();
			if (managed.state == ServiceState.RUNNING) {
				try {
					healthy = managed.service.isHealthy();
					stats = new LinkedHashMap<>(managed.service.stats());
				}
				catch (RuntimeException e) {
					log.warn("Unable to get the status of the {}", managed.service.name(), e);
				}
			}
			status.add(new ServiceStatus(managed.service.name(), managed.state, healthy, managed.error, stats));
		}
		return status;
	}

	/**
	 * Whether all the services are running and healthy.
	 *
	 * @return true if all the services are running and healthy
	 */
	public boolean isHealthy() {
		return this.status().stream().allMatch(ServiceStatus::healthy);
	}

	/**
	 * Creates a thread which stops the services, to be registered as a JVM shutdown hook
	 * (run on SIGTERM/SIGINT, or when the JVM exits).
	 *
	 * @return the thread
	 */
	public Thread shutdownHook() {
		return new Thread(() -> {
			log.warn("Netshot is stopping.");
			this.stop();
			log.warn("Netshot is stopped.");
		}, "NetshotShutdown");
	}
}
