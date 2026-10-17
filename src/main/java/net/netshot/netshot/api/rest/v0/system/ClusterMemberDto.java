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
package net.netshot.netshot.api.rest.v0.system;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;


/**
 * A member of the high availability cluster.
 *
 * @param local whether it is the local server
 * @param instanceId the unique ID of the member
 * @param hostname the hostname
 * @param clusteringVersion the clustering version (to check compatibility)
 * @param masterPriority the mastership priority (higher is better)
 * @param runnerPriority the job runner priority (higher is better)
 * @param runnerWeight the job runner weight (for runners of the same priority)
 * @param appVersion the Netshot version
 * @param jvmVersion the JVM version
 * @param driverHash the hash of all the drivers
 * @param status the mastership status (name of the status)
 * @param lastStatusChangeTime the time of the last status change (epoch milliseconds)
 * @param lastSeenTime the time the member was last seen (epoch milliseconds)
 */
@JacksonXmlRootElement(localName = "ClusterMember")
public record ClusterMemberDto(
	boolean local,
	String instanceId,
	String hostname,
	int clusteringVersion,
	int masterPriority,
	int runnerPriority,
	int runnerWeight,
	String appVersion,
	String jvmVersion,
	String driverHash,
	String status,
	long lastStatusChangeTime,
	long lastSeenTime) {
}
