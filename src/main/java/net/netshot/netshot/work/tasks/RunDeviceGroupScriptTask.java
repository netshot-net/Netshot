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
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.DeviceGroup;
import net.netshot.netshot.rest.RestViews.DefaultView;
import net.netshot.netshot.work.Task;

/**
 * This task schedules new tasks to run a script on each device of the given
 * group (either a real device group, or a one-time device list), either in
 * parallel or sequentially.
 */
@Entity
@DiscriminatorValue("RunDeviceGroupScriptTask")
@Slf4j
public final class RunDeviceGroupScriptTask extends Task
	implements GroupBasedTask, DeviceListBasedTask, ChildOrchestratingTask {

	public RunDeviceGroupScriptTask() {

	}

	/**
	 * Instantiates a new run device group script task, targeting a real device group.
	 *
	 * @param group the device group to target
	 * @param script the script to run
	 * @param driver the device driver the script applies to
	 * @param comments the task comments
	 * @param author the task author
	 */
	public RunDeviceGroupScriptTask(DeviceGroup group, String script, DeviceDriver driver,
		String comments, String author) {
		super(comments, group.getName(), author);
		this.setDeviceGroup(group);
		this.setScript(script);
		this.setDeviceDriver(driver.getName());
	}

	/**
	 * Instantiates a new run device group script task, targeting a one-time device list.
	 *
	 * @param devices the devices to target
	 * @param script the script to run
	 * @param driver the device driver the script applies to
	 * @param comments the task comments
	 * @param author the task author
	 */
	public RunDeviceGroupScriptTask(List<Device> devices, String script, DeviceDriver driver,
		String comments, String author) {
		super(comments, String.format("%d device(s)", devices.size()), author);
		this.setDeviceList(devices);
		this.setScript(script);
		this.setDeviceDriver(driver.getName());
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#getTaskDescription()
	 */
	@Override
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public String getTaskDescription() {
		return "Group script execution";
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
		List<Device> devices = this.getTargetDevices();
		if (this.getDeviceGroup() == null && devices.isEmpty()) {
			this.logger.info("Neither a device group nor a device list is set, the task will be cancelled.");
			this.status = Status.CANCELLED;
			return;
		}
		log.debug("Task {}. {} device(s) to process.", this.getId(), devices.size());
		String comment = this.getDeviceGroup() == null
			? String.format("Started due to %d-device script list", devices.size())
			: String.format("Started due to group %s script task", this.getDeviceGroup().getName());

		DeviceDriver driver = DeviceDriver.getDriverByName(this.getDeviceDriver());
		if (driver == null) {
			log.error("Task {}. No such device driver {}.", this.getId(), this.getDeviceDriver());
			this.logger.error("Unknown device driver.");
			this.status = Status.FAILURE;
			return;
		}

		List<Task> children = this.reloadExistingChildren();
		if (children.isEmpty()) {
			children = new ArrayList<>();
			for (Device device : devices) {
				children.add(this.buildChild(device, driver, comment));
			}
			this.preCreateChildren(children);
		}
		this.status = this.orchestrateChildren(children);
	}

	private RunDeviceScriptTask buildChild(Device device, DeviceDriver driver, String comment) {
		RunDeviceScriptTask child = new RunDeviceScriptTask(device, this.getScript(), driver, comment, author);
		child.setUserInputValues(this.getUserInputValues());
		child.setRunSnapshot(this.isRunSnapshot());
		child.setRunDiagnostics(this.isRunDiagnostics());
		child.setCheckCompliance(this.isCheckCompliance());
		return child;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		RunDeviceGroupScriptTask task = (RunDeviceGroupScriptTask) super.clone();
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
			String.format("RunDeviceGroupScript_%d", this.getDeviceGroupId()));
	}
}
