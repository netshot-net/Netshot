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

import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.quartz.JobKey;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.task.Task;

/**
 * This task checks the configuration compliance status of a group of devices
 * (either a real device group, or a one-time device list).
 */
@Entity
@DiscriminatorValue("CheckGroupComplianceTask")
@Slf4j
public final class CheckGroupComplianceTask extends Task implements GroupBasedTask, DeviceListBasedTask {

	/**
	 * Instantiates a new check group compliance task.
	 */
	public CheckGroupComplianceTask() {

	}

	/**
	 * Instantiates a new check group compliance task, targeting a real device group.
	 *
	 * @param group the group
	 * @param comments the comments
	 * @param author the author
	 */
	public CheckGroupComplianceTask(DeviceGroup group, String comments, String author) {
		super(comments, group.getName(), author);
		this.setDeviceGroup(group);
	}

	/**
	 * Instantiates a new check group compliance task, targeting a one-time device list.
	 *
	 * @param devices the ordered list of devices
	 * @param comments the comments
	 * @param author the author
	 */
	public CheckGroupComplianceTask(List<Device> devices, String comments, String author) {
		super(comments, String.format("%d device(s)", devices.size()), author);
		this.setDeviceList(devices);
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getTaskDescription()
	 */
	@Override
	@Transient
	public String getTaskDescription() {
		return "Group compliance check";
	}

	/**
	 * Get the ID of the associate group.
	 * @return the ID of the group
	 */
	@Transient
	public long getDeviceGroupId() {
		if (this.getDeviceGroup() == null) {
			return 0;
		}
		return this.getDeviceGroup().getId();
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#prepare()
	 */
	@Override
	public void prepare(Session session) {
		Hibernate.initialize(this.getDeviceGroup());
		if (this.getDeviceGroup() == null) {
			Hibernate.initialize(this.getDeviceListMembers());
		}
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		CheckGroupComplianceTask task = (CheckGroupComplianceTask) super.clone();
		task.setDeviceGroup(this.getDeviceGroup());
		if (this.getDeviceGroup() == null) {
			// Mirrors prepare(), which only initializes the lazy deviceListMembers
			// collection in this same case -- device group and device list are
			// mutually exclusive, so there is nothing to copy (and nothing safe to
			// touch) otherwise.
			task.setDeviceList(this.getDeviceList());
		}
		return task;
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getIdentity()
	 */
	@Override
	@Transient
	public JobKey getIdentity() {
		return new JobKey(String.format("Task_%d", this.getId()),
			String.format("CheckGroupCompliance_%d", this.getDeviceGroupId()));
	}

}
