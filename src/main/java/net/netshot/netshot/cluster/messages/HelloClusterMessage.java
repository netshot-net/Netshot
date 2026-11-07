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
package net.netshot.netshot.cluster.messages;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.cluster.ClusterMember;
import net.netshot.netshot.platform.rest.RestViews.ClusteringView;

/**
 * Hello message from cluster member.
 */
@XmlRootElement
public class HelloClusterMessage extends ClusterMessage {

	/** Member info. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(ClusteringView.class)
	}))
	@Setter
	private ClusterMember memberInfo;

	public HelloClusterMessage(ClusterMember memberInfo) {
		super(memberInfo.getInstanceId());
		this.memberInfo = memberInfo;
	}

	/**
	 * Hidden constructor.
	 */
	protected HelloClusterMessage() {
	}

	@Override
	public String toString() {
		return "HelloClusterMessage [memberInfo=" + memberInfo + "]";
	}
}
