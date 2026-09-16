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
package net.netshot.netshot.work.tasks;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.quartz.JobKey;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DeviceGroup;
import net.netshot.netshot.rest.RestViews.DefaultView;
import net.netshot.netshot.work.Task;

/**
 * This task schedules new tasks to take a new snapshot of each device of the
 * given group (either a real device group, or a one-time device list),
 * either in parallel or sequentially.
 */
@Entity
@DiscriminatorValue("TakeGroupSnapshotTask")
@Slf4j
public final class TakeGroupSnapshotTask extends Task
	implements GroupBasedTask, DeviceListBasedTask, ChildOrchestratingTask {

	/**
	 * Instantiates a new take group snapshot task.
	 */
	public TakeGroupSnapshotTask() {

	}

	/**
	 * Instantiates a new take group snapshot task, targeting a real device group.
	 *
	 * @param group the group
	 * @param comments the comments
	 * @param author the author
	 * @param limitToOutofdateDeviceHours ignore devices that had a successful snapshot in the last given hours
	 * @param dontRunDiagnostics Set to the true to disable running diagnostics
	 * @param dontCheckCompliance Set to true to disable compliance checking
	 */
	public TakeGroupSnapshotTask(DeviceGroup group, String comments, String author,
		int limitToOutofdateDeviceHours, boolean dontRunDiagnostics, boolean dontCheckCompliance) {
		super(comments, group.getName(), author);
		this.setDeviceGroup(group);
		this.setLimitToOutofdateDeviceHours(limitToOutofdateDeviceHours);
		this.setDontRunDiagnostics(dontRunDiagnostics);
		this.setDontCheckCompliance(dontCheckCompliance);
	}

	/**
	 * Instantiates a new take group snapshot task, targeting a one-time device list.
	 *
	 * @param devices the ordered list of devices
	 * @param comments the comments
	 * @param author the author
	 * @param limitToOutofdateDeviceHours ignore devices that had a successful snapshot in the last given hours
	 * @param dontRunDiagnostics Set to the true to disable running diagnostics
	 * @param dontCheckCompliance Set to true to disable compliance checking
	 */
	public TakeGroupSnapshotTask(List<Device> devices, String comments, String author,
		int limitToOutofdateDeviceHours, boolean dontRunDiagnostics, boolean dontCheckCompliance) {
		super(comments, String.format("%d device(s)", devices.size()), author);
		this.setDeviceList(devices);
		this.setLimitToOutofdateDeviceHours(limitToOutofdateDeviceHours);
		this.setDontRunDiagnostics(dontRunDiagnostics);
		this.setDontCheckCompliance(dontCheckCompliance);
	}

	/**
	 * Only capture devices updated more than X hours ago.
	 * @return the limit, in hours (-1 = no limit)
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public int getLimitToOutofdateDeviceHours() {
		return this.getIntAttribute("limitToOutofdateDeviceHours", -1);
	}

	/**
	 * Sets the out-of-date device limit, in hours.
	 * @param limitToOutofdateDeviceHours the limit
	 */
	public void setLimitToOutofdateDeviceHours(int limitToOutofdateDeviceHours) {
		this.setAttribute("limitToOutofdateDeviceHours", limitToOutofdateDeviceHours);
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#getTaskDescription()
	 */
	@Override
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public String getTaskDescription() {
		return "Group snapshot";
	}

	/**
	 * Get the ID of the associate group.
	 * @return the ID of the group
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public long getDeviceGroupId() {
		if (this.getDeviceGroup() == null) {
			return 0;
		}
		return this.getDeviceGroup().getId();
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#prepare()
	 */
	@Override
	public void prepare(Session session) {
		Hibernate.initialize(this.getDeviceGroup());
		if (this.getDeviceGroup() != null) {
			Hibernate.initialize(this.getDeviceGroup().getCachedDevices());
		}
		else {
			Hibernate.initialize(this.getDeviceListMembers());
		}
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#run()
	 */
	@Override
	public void run() {
		List<Device> allDevices = this.getTargetDevices();
		if (this.getDeviceGroup() == null && allDevices.isEmpty()) {
			this.logger.info("Neither a device group nor a device list is set, the task will be cancelled.");
			this.status = Status.CANCELLED;
			return;
		}
		String comment = this.getDeviceGroup() == null
			? String.format("Started due to %d-device snapshot list", allDevices.size())
			: String.format("Started due to group %s snapshot", this.getDeviceGroup().getName());

		Calendar referenceDate = Calendar.getInstance();
		referenceDate.add(Calendar.HOUR, -this.getLimitToOutofdateDeviceHours());
		List<Device> devices = new ArrayList<>();
		for (Device device : allDevices) {
			if (referenceDate.getTime().before(device.getChangeDate())) {
				this.logger.info("Ignoring device {} because it changed less than {} hours ago",
					device.getName(), this.getLimitToOutofdateDeviceHours());
				continue;
			}
			devices.add(device);
		}
		log.debug("Task {}. {} device(s) to process (out of {}).", this.getId(), devices.size(), allDevices.size());

		List<Task> children = this.reloadExistingChildren();
		if (children.isEmpty()) {
			children = new ArrayList<>();
			for (Device device : devices) {
				children.add(new TakeSnapshotTask(device, comment, author, false,
					this.isDontRunDiagnostics(), this.isDontCheckCompliance()));
			}
			this.preCreateChildren(children);
		}
		this.status = this.orchestrateChildren(children);
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		TakeGroupSnapshotTask task = (TakeGroupSnapshotTask) super.clone();
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
	 * @see net.netshot.netshot.work.Task#getIdentity()
	 */
	@Override
	@Transient
	public JobKey getIdentity() {
		return new JobKey(String.format("Task_%d", this.getId()),
			String.format("TakeGroupSnapshot_%d", this.getDeviceGroupId()));
	}
}
