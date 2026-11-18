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

import net.netshot.netshot.cluster.ClusterService;
import net.netshot.netshot.platform.lifecycle.ServiceManager;
import net.netshot.netshot.system.ServerInfoService;
import net.netshot.netshot.system.ServiceHealthService;

/**
 * The system: the cluster members, the server information and the health of the services.
 *
 * @param clusterService the cluster use cases
 * @param serverInfoService the server information use cases
 * @param serviceHealthService the health of the services
 */
public record SystemModule(
	ClusterService clusterService,
	ServerInfoService serverInfoService,
	ServiceHealthService serviceHealthService) {

	/**
	 * Builds the module.
	 *
	 * @param tasks the task module (the cluster)
	 * @param aaa the AAA module (the max idle time of the sessions)
	 * @param services the manager of the services of the server
	 * @return the module
	 */
	public static SystemModule create(TaskModule tasks, AaaModule aaa, ServiceManager services) {
		return new SystemModule(new ClusterService(tasks.cluster()::getClusterMembers), new ServerInfoService(aaa.loginService()),
			new ServiceHealthService(services));
	}
}
