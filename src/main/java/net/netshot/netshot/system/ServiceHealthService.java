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
package net.netshot.netshot.system;

import java.util.List;

import net.netshot.netshot.platform.lifecycle.ServiceManager;
import net.netshot.netshot.platform.lifecycle.ServiceStatus;

/**
 * Gives the state of the services of the server (see {@link ServiceManager}): whether it is ready and healthy,
 * and the status and statistics of each service. The manager is created after the wiring (from its services), and
 * attached to this service by the startup code; as long as there is none, there is no service to report.
 */
public class ServiceHealthService {

	/** The manager of the services of the server (null until attached). */
	private volatile ServiceManager manager;

	/**
	 * Attaches the manager of the services of the server.
	 *
	 * @param serviceManager the manager
	 */
	public void attach(ServiceManager serviceManager) {
		this.manager = serviceManager;
	}

	/**
	 * Gets the status of the services.
	 *
	 * @return the status of each service, in start order (empty if no manager is attached)
	 */
	public List<ServiceStatus> services() {
		ServiceManager current = this.manager;
		return current == null ? List.of() : current.status();
	}

	/**
	 * Tells whether all the services are running and healthy.
	 *
	 * @return true if the server is ready and healthy
	 */
	public boolean isHealthy() {
		return this.services().stream().allMatch(ServiceStatus::healthy);
	}
}
