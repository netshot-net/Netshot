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
package net.netshot.netshot.api.rest.v1.system;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A member of the high availability cluster.
 *
 * @param instanceId the unique ID of the instance
 * @param display the host name of the member
 * @param local whether the member is the server answering
 * @param hostname the host name
 * @param clusteringVersion the version of the clustering protocol
 * @param masterPriority the priority to become the master (the higher the better)
 * @param runnerPriority the priority to run tasks
 * @param runnerWeight the weight of the member when tasks are distributed
 * @param appVersion the version of Netshot
 * @param jvmVersion the version of the JVM
 * @param driverHash the hash of the loaded drivers
 * @param status the mastership status ({@code MEMBER}, {@code MASTER}, {@code EXPIRED} or {@code NEGOTIATING})
 * @param lastStatusChangeDate when the status last changed
 * @param lastSeenDate when the member was last seen
 */
@Schema(name = "ClusterMember", description = "Member of the high availability cluster")
public record ClusterMemberDto(
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String instanceId,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String display,
	boolean local,
	String hostname,
	int clusteringVersion,
	int masterPriority,
	int runnerPriority,
	int runnerWeight,
	String appVersion,
	String jvmVersion,
	String driverHash,
	String status,
	Instant lastStatusChangeDate,
	Instant lastSeenDate) {
}
