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
package net.netshot.netshot.cluster.messages;

import java.lang.management.ManagementFactory;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.platform.rest.RestViews.ClusteringView;

/**
 * HA-related message to be be exchanged with other Netshot instances
 * in a serialized form.
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement()
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@Type(value = HelloClusterMessage.class, name = "Hello"),
	@Type(value = ReloadDriversMessage.class, name = "ReloadDrivers"),
	@Type(value = LoadTasksMessage.class, name = "LoadTasks"),
	@Type(value = AssignTasksMessage.class, name = "AssignTasks"),
	@Type(value = AutoSnapshotMessage.class, name = "AutoSnapshot"),
	@Type(value = VaultInstanceChangedMessage.class, name = "VaultInstanceChanged"),
})
public abstract class ClusterMessage {

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(ClusteringView.class)
	}))
	@Setter
	private String instanceId;

	/** Current system time. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(ClusteringView.class)
	}))
	@Setter
	private long currentTime;

	/** Current Netshot uptime. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(ClusteringView.class)
	}))
	@Setter
	private long upTime;

	/** Message ID (set by the cluster manager when it sends the message, increasing for each sending instance). */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(ClusteringView.class)
	}))
	@Setter
	private long messageId;

	/**
	 * Default constructor.
	 * @param instanceId = ID of the instance
	 */
	public ClusterMessage(String instanceId) {
		this.instanceId = instanceId;
		this.currentTime = System.currentTimeMillis();
		this.upTime = ManagementFactory.getRuntimeMXBean().getUptime();
	}

	/**
	 * Hidden constructor.
	 */
	protected ClusterMessage() {
	}

	@Override
	public String toString() {
		return "ClusterMessage [currentTime=" + currentTime + ", instanceId=" + instanceId + ", messageId=" + messageId
			+ ", upTime=" + upTime + "]";
	}
}
