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
package net.netshot.netshot.api.rest.v0.system;

import java.util.ArrayList;
import java.util.List;

import net.netshot.netshot.cluster.ClusterMember;
import net.netshot.netshot.cluster.ClusterService.MasterStatus;
import net.netshot.netshot.system.ServerInfo;

/**
 * Maps the cluster and the server information to their REST representation.
 * All the methods are pure functions.
 */
public final class SystemMapper {

	private SystemMapper() {
		// Static helpers only
	}

	/**
	 * Maps a cluster member.
	 *
	 * @param member the member
	 * @return the DTO
	 */
	public static ClusterMemberDto toDto(ClusterMember member) {
		return new ClusterMemberDto(
			member.isLocal(),
			member.getInstanceId(),
			member.getHostname(),
			member.getClusteringVersion(),
			member.getMasterPriority(),
			member.getRunnerPriority(),
			member.getRunnerWeight(),
			member.getAppVersion(),
			member.getJvmVersion(),
			member.getDriverHash(),
			member.getStatus() == null ? null : member.getStatus().name(),
			member.getLastStatusChangeTime(),
			member.getLastSeenTime());
	}

	/**
	 * Maps some cluster members.
	 *
	 * @param members the members
	 * @return the DTOs
	 */
	public static List<ClusterMemberDto> toMemberDtos(List<ClusterMember> members) {
		List<ClusterMemberDto> dtos = new ArrayList<>();
		members.forEach(member -> dtos.add(toDto(member)));
		return dtos;
	}

	/**
	 * Maps the mastership status of the local server.
	 *
	 * @param status the status
	 * @return the DTO
	 */
	public static MasterStatusDto toDto(MasterStatus status) {
		return new MasterStatusDto(status.clusterEnabled(), status.master(), status.currentMasterId());
	}

	/**
	 * Maps the server information.
	 *
	 * @param info the information
	 * @return the DTO
	 */
	public static ServerInfoDto toDto(ServerInfo info) {
		return new ServerInfoDto(info.serverVersion(), info.maxIdleTimeout());
	}
}
