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
package net.netshot.netshot.cluster;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.platform.rest.RestViews.RestApiView;

@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement()
public class ClusterMember implements Comparable<ClusterMember>, Cloneable {

	/**
	 * Mastership status of a cluster member.
	 */
	public enum MastershipStatus {
		MEMBER,
		MASTER,
		EXPIRED,
		NEGOTIATING,
	}

	/** Is it the local server? */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(RestApiView.class)
	}))
	@Setter
	private boolean local;

	/** Cluster member unique ID. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String instanceId;

	/** Member hostname. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String hostname;

	/** Clustering version (to check compatibility). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private int clusteringVersion;

	/** Mastership priority of the instance (higher = better). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private int masterPriority;

	/** Job runner priority (higher = better). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private int runnerPriority;

	/** Job runner weight (for runners of same priority). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private int runnerWeight;

	/** Netshot version. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String appVersion;

	/** JVM version. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String jvmVersion;

	/** Hash of all drivers. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String driverHash;

	/** Current status. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private MastershipStatus status;

	/** Last status change. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(RestApiView.class)
	}))
	@Setter
	private long lastStatusChangeTime;

	/** Last seen. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(RestApiView.class)
	}))
	@Setter
	private long lastSeenTime;

	/**
	 * Constructs a new ClusterMember instance with the specified parameters.
	 *
	 * @param instanceId        the unique identifier for the cluster member instance
	 * @param hostname          the hostname of the cluster member
	 * @param clusteringVersion the version of the clustering protocol used
	 * @param masterPriority    the priority value for selection as master
	 * @param runnerPriority    the priority value for selection as runner
	 * @param runnerWeight      the weight value for runner tasks
	 * @param appVersion        the application version running on the member
	 * @param jvmVersion        the Java Virtual Machine version used by the member
	 * @param driverHash        the hash of the driver set loaded on the member
	 */
	public ClusterMember(String instanceId, String hostname, int clusteringVersion, int masterPriority,
		int runnerPriority, int runnerWeight, String appVersion, String jvmVersion, String driverHash) {
		this.local = true;
		this.instanceId = instanceId;
		this.hostname = hostname;
		this.clusteringVersion = clusteringVersion;
		this.masterPriority = masterPriority;
		this.runnerPriority = runnerPriority;
		this.runnerWeight = runnerWeight;
		this.appVersion = appVersion;
		this.jvmVersion = jvmVersion;
		this.driverHash = driverHash;
		this.status = MastershipStatus.MEMBER;
		this.lastStatusChangeTime = System.currentTimeMillis();
		this.lastSeenTime = 0L;
	}

	/**
	 * Hidden constructor for deserialization.
	 */
	public ClusterMember() {
	}

	public void setStatus(MastershipStatus status) {
		if (!status.equals(this.status)) {
			this.lastStatusChangeTime = System.currentTimeMillis();
		}
		this.status = status;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (this.instanceId == null ? 0 : this.instanceId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ClusterMember other = (ClusterMember) obj;
		if (this.instanceId == null) {
			if (other.instanceId != null) {
				return false;
			}
		}
		else if (!this.instanceId.equals(other.instanceId)) {
			return false;
		}
		return true;
	}

	/**
	 * Two ClusterMember's are compared based on mastership priority.
	 */
	@Override
	public int compareTo(ClusterMember other) {
		int r = Long.compare(this.getMasterPriority(), other.getMasterPriority());
		if (r != 0) {
			return r;
		}
		return this.getInstanceId().compareTo(other.getInstanceId());
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

	@Override
	public String toString() {
		return "ClusterMember [appVersion=" + appVersion
			+ ", clusteringVersion=" + clusteringVersion
			+ ", driverHash=" + driverHash
			+ ", instanceId=" + instanceId
			+ ", jvmVersion=" + jvmVersion
			+ ", lastSeenTime=" + lastSeenTime
			+ ", lastStatusChangeTime=" + lastStatusChangeTime
			+ ", masterPriority=" + masterPriority
			+ ", runnerPriority=" + runnerPriority
			+ ", runnerWeight=" + runnerWeight
			+ ", status=" + status + "]";
	}
}
