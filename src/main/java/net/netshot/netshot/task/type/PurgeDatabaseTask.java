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

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.task.Task;

/**
 * This task makes some clean up on the database, optionally restricted to a
 * device group or a one-time device list.
 */
@Entity
@DiscriminatorValue("PurgeDatabaseTask")
@Slf4j
public final class PurgeDatabaseTask extends Task implements GroupBasedTask, DeviceListBasedTask {

	/**
	 * Instantiates a new task.
	 */
	protected PurgeDatabaseTask() {
	}

	/**
	 * Instantiates a new purge database task, optionally restricted to a device group.
	 *
	 * @param comments = the comments
	 * @param author = the author
	 * @param days = remove tasks older than this number of days
	 * @param configDays = remove configs older than this number of days
	 * @param configSize = remove configs bigger than this size
	 * @param configKeepDays = keep one config every this number of days
	 * @param moduleDays = remove modules older than this number of days
	 * @param group = device group to act on, or null for no restriction
	 */
	public PurgeDatabaseTask(String comments, String author, int days, int configDays,
		int configSize, int configKeepDays, int moduleDays, DeviceGroup group) {
		super(comments, "Global", author);
		this.setDays(days);
		this.setConfigDays(configDays);
		this.setConfigSize(configSize);
		this.setConfigKeepDays(configKeepDays);
		this.setModuleDays(moduleDays);
		this.setDeviceGroup(group);
	}

	/**
	 * Instantiates a new purge database task, restricted to a one-time device list.
	 *
	 * @param comments = the comments
	 * @param author = the author
	 * @param days = remove tasks older than this number of days
	 * @param configDays = remove configs older than this number of days
	 * @param configSize = remove configs bigger than this size
	 * @param configKeepDays = keep one config every this number of days
	 * @param moduleDays = remove modules older than this number of days
	 * @param devices = the ordered list of devices to act on
	 */
	public PurgeDatabaseTask(String comments, String author, int days, int configDays,
		int configSize, int configKeepDays, int moduleDays, List<Device> devices) {
		super(comments, "Global", author);
		this.setDays(days);
		this.setConfigDays(configDays);
		this.setConfigSize(configSize);
		this.setConfigKeepDays(configKeepDays);
		this.setModuleDays(moduleDays);
		this.setDeviceList(devices);
	}

	/**
	 * Remove tasks older than this number of days.
	 * @return the number of days
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public int getDays() {
		return this.getIntAttribute("days", 0);
	}

	public void setDays(int days) {
		this.setAttribute("days", days);
	}

	/**
	 * Remove configs older than this number of days.
	 * @return the number of days
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public int getConfigDays() {
		return this.getIntAttribute("configDays", -1);
	}

	public void setConfigDays(int configDays) {
		this.setAttribute("configDays", configDays);
	}

	/**
	 * Remove configs bigger than this size (KB).
	 * @return the size
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public int getConfigSize() {
		return this.getIntAttribute("configSize", 0);
	}

	public void setConfigSize(int configSize) {
		this.setAttribute("configSize", configSize);
	}

	/**
	 * Keep one config every this number of days.
	 * @return the number of days
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public int getConfigKeepDays() {
		return this.getIntAttribute("configKeepDays", 0);
	}

	public void setConfigKeepDays(int configKeepDays) {
		this.setAttribute("configKeepDays", configKeepDays);
	}

	/**
	 * Remove modules removed more than this number of days ago.
	 * @return the number of days
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public int getModuleDays() {
		return this.getIntAttribute("moduleDays", -1);
	}

	public void setModuleDays(int moduleDays) {
		this.setAttribute("moduleDays", moduleDays);
	}

	/**
	 * Whether to remove orphan binary attribute files (files on disk with no
	 * corresponding database record) from the binary attribute storage folder.
	 * @return true if orphan files should be removed
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public boolean isRemoveOrphanFiles() {
		return this.getBooleanAttribute("removeOrphanFiles", false);
	}

	public void setRemoveOrphanFiles(boolean removeOrphanFiles) {
		this.setAttribute("removeOrphanFiles", removeOrphanFiles);
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getTaskDescription()
	 */
	@Override
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public String getTaskDescription() {
		return "Database purge";
	}

	/**
	 * Get the ID of the associated group, if the purge is limited to one.
	 * @return the ID of the group, or 0
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
		PurgeDatabaseTask task = (PurgeDatabaseTask) super.clone();
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
		return new JobKey(String.format("Task_%d", this.getId()), "PurgeDatabase");
	}

}
