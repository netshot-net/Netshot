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
package net.netshot.netshot.api.rest.v1.system;

import java.time.Instant;

import net.netshot.netshot.cluster.ClusterMember;
import net.netshot.netshot.cluster.ClusterService.MasterStatus;
import net.netshot.netshot.platform.lifecycle.ServiceStatus;
import net.netshot.netshot.system.ServerInfo;

/**
 * Maps the system information to its v1 representation.
 */
public final class SystemMapper {

	private SystemMapper() {
	}

	/**
	 * Maps the server information.
	 *
	 * @param info the information
	 * @param now the current time
	 * @return the representation
	 */
	public static SystemInfoDto toDto(ServerInfo info, Instant now) {
		return new SystemInfoDto(info.serverVersion(), now, info.maxIdleTimeout());
	}

	/**
	 * Maps a cluster member.
	 *
	 * @param member the member
	 * @return the representation
	 */
	public static ClusterMemberDto toDto(ClusterMember member) {
		return new ClusterMemberDto(member.getInstanceId(), member.getHostname(), member.isLocal(), member.getHostname(),
			member.getClusteringVersion(), member.getMasterPriority(), member.getRunnerPriority(), member.getRunnerWeight(),
			member.getAppVersion(), member.getJvmVersion(), member.getDriverHash(),
			member.getStatus() == null ? null : member.getStatus().name(),
			Instant.ofEpochMilli(member.getLastStatusChangeTime()), Instant.ofEpochMilli(member.getLastSeenTime()));
	}

	/**
	 * Maps the mastership status of the local server.
	 *
	 * @param status the status
	 * @return the representation
	 */
	public static ClusterMasterDto toDto(MasterStatus status) {
		return new ClusterMasterDto(status.clusterEnabled(), status.master(), status.currentMasterId());
	}

	/**
	 * Maps the status of a service.
	 *
	 * @param status the status
	 * @return the representation
	 */
	public static ServiceDto toDto(ServiceStatus status) {
		return new ServiceDto(status.name(), status.name(), status.state().name(), status.healthy(), status.error(), status.stats());
	}
}
