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
package net.netshot.netshot.platform.lifecycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

/**
 * Starts the services in the given order, and stops them in the reverse order.
 *
 * <p>If a service fails to start, it is stopped (to release what its start built), then the services already
 * started (in the reverse order), and {@link #start()} throws: the server doesn't run half started. {@link #stop()} stops every running service,
 * even if some of them fail to stop. Typically called by a JVM shutdown hook.</p>
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
	 * Adds a service, started after the ones added before it (while building the application: the services which need
	 * the manager, such as the report of their health, are built before the manager is complete).
	 *
	 * @param service the service
	 * @throws IllegalStateException if a service was already started
	 */
	public synchronized void add(Service service) {
		if (this.services.stream().anyMatch(managed -> managed.state != ServiceState.NEW)) {
			throw new IllegalStateException("The services are started: no service can be added");
		}
		this.services.add(new Managed(service));
	}

	/**
	 * Starts the services, in order.
	 *
	 * @throws ServiceStartException if a service fails to start (the started ones are stopped then)
	 */
	public void start() throws ServiceStartException {
		this.start(_ -> true);
	}

	/**
	 * Starts some of the services, in the order of the manager (for the tools and the tests, which need only some
	 * of them).
	 *
	 * @param ids the IDs of the services to start
	 * @throws ServiceStartException if a service fails to start (the started ones are stopped then)
	 * @throws IllegalArgumentException if no service has one of the IDs
	 */
	public void start(Collection<String> ids) throws ServiceStartException {
		Set<String> known = new HashSet<>();
		this.services.forEach(managed -> known.add(managed.service.id()));
		for (String id : ids) {
			if (!known.contains(id)) {
				throw new IllegalArgumentException("No service '%s'".formatted(id));
			}
		}
		this.start(service -> ids.contains(service.id()));
	}

	/**
	 * Starts the services matching a filter, in order.
	 *
	 * @param filter the filter of the services to start
	 * @throws ServiceStartException if a service fails to start (the started ones are stopped then)
	 */
	private synchronized void start(Predicate<Service> filter) throws ServiceStartException {
		for (Managed managed : this.services) {
			if (managed.state != ServiceState.NEW || !filter.test(managed.service)) {
				continue;
			}
			String name = managed.service.name();
			log.info("Starting the service '{}'.", name);
			managed.state = ServiceState.STARTING;
			long startTime = System.currentTimeMillis();
			try {
				managed.service.start();
				managed.state = ServiceState.RUNNING;
				log.debug("The service '{}' was started in {}ms.", name, System.currentTimeMillis() - startTime);
			}
			catch (Exception | LinkageError e) {
				managed.state = ServiceState.FAILED;
				managed.error = String.valueOf(e.getMessage());
				log.error("Unable to start the service '{}', stopping the started services.", name, e);
				this.release(managed);
				this.stop();
				throw new ServiceStartException("Unable to start the service '%s'".formatted(name), e);
			}
		}
	}

	/**
	 * Stops a service whose start failed, to release what it built before the failure (best effort: its state stays
	 * failed).
	 *
	 * @param managed the service
	 */
	private void release(Managed managed) {
		try {
			managed.service.stop();
		}
		catch (Exception | LinkageError e) {
			log.warn("Error while releasing the service '{}' after its failed start.", managed.service.name(), e);
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
			log.info("Stopping the service '{}'.", name);
			managed.state = ServiceState.STOPPING;
			long startTime = System.currentTimeMillis();
			try {
				managed.service.stop();
				managed.state = ServiceState.STOPPED;
				log.debug("The service '{}' was stopped in {}ms.", name, System.currentTimeMillis() - startTime);
			}
			catch (Exception | LinkageError e) {
				managed.state = ServiceState.FAILED;
				managed.error = String.valueOf(e.getMessage());
				log.error("Error while stopping the service '{}'.", name, e);
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
					log.warn("Unable to get the status of the service '{}'.", managed.service.name(), e);
				}
			}
			status.add(new ServiceStatus(managed.service.id(), managed.service.name(), managed.state, healthy, managed.error, stats));
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
}
