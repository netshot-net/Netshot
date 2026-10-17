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
package net.netshot.netshot.task.type;

import java.util.Set;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.quartz.JobKey;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.task.Task;

/**
 * This task scans a subnet to discover devices.
 */
@Entity
@DiscriminatorValue("ScanSubnetsTask")
@Slf4j
public final class ScanSubnetsTask extends Task implements DomainBasedTask, ChildOrchestratingTask {

	/** The subnets. */
	@Getter(onMethod = @__({
		@Fetch(FetchMode.SELECT),
		@ElementCollection(fetch = FetchType.EAGER),
		// Can't make it accepted by Hibernate 6.5... nevermind
		// @OnDelete(action = OnDeleteAction.CASCADE),
		@CollectionTable(name = "scan_subnets_task_subnets",
			joinColumns = @JoinColumn(name = "scan_subnets_task")),
		@AttributeOverrides({
			@AttributeOverride(name = "address", column = @Column(name = "ipv4address")),
			@AttributeOverride(name = "addressUsage", column = @Column(name = "ipv4usage")),
			@AttributeOverride(name = "prefixLength", column = @Column(name = "ipv4mask")),
		})
	}))
	@Setter
	private Set<Network4Address> subnets;

	/**
	 * Instantiates a new scan subnet task.
	 */
	protected ScanSubnetsTask() {
	}

	/**
	 * Instantiates a new scan subnets task.
	 *
	 * @param subnets the subnets
	 * @param domain the domain
	 * @param comments the comments
	 * @param target the target
	 * @param author the author
	 */
	public ScanSubnetsTask(Set<Network4Address> subnets, Domain domain, String comments,
		String target, String author) {
		super(comments, target, author);
		this.setDomain(domain);
		this.subnets = subnets;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getTaskDescription()
	 */
	@Override
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public String getTaskDescription() {
		return "Subnet scan";
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		ScanSubnetsTask task = (ScanSubnetsTask) super.clone();
		task.setSubnets(this.subnets);
		return task;
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getIdentity()
	 */
	@Override
	@Transient
	public JobKey getIdentity() {
		return new JobKey(String.format("Task_%d", this.getId()), "ScanSubnets");
	}
}
