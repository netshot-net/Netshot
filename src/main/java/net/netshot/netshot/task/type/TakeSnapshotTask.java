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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.quartz.JobKey;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.platform.rest.RestViews.HookView;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.engine.TaskLockManager;

/**
 * This task takes a snapshot of a device.
 */
@Entity
@DiscriminatorValue("TakeSnapshotTask")
@Slf4j
public final class TakeSnapshotTask extends Task implements DeviceBasedTask {

	/** The scheduled automatic snapshots. */
	private static Set<Long> scheduledAutoSnapshots = ConcurrentHashMap.newKeySet();

	/**
	 * Clear scheduled auto snapshot.
	 *
	 * @param deviceId the device
	 */
	public static void clearScheduledAutoSnapshot(Long deviceId) {
		scheduledAutoSnapshots.remove(deviceId);
	}

	/**
	 * Check whether an automatic snapshot is queued for the given device.
	 *
	 * @param deviceId the device
	 * @return true, if successful
	 */
	public static boolean checkAutoSnapshot(Long deviceId) {
		return scheduledAutoSnapshots.add(deviceId);
	}

	/**
	 * Instantiates a new take snapshot task.
	 */
	protected TakeSnapshotTask() {
	}

	/**
	 * Whether this is an automatic snapshot (as opposed to user/API requested).
	 * @return true if automatic
	 */
	@XmlElement
	@JsonView(HookView.class)
	@Transient
	public boolean isAutomatic() {
		return this.getBooleanAttribute("automatic", false);
	}

	/**
	 * Sets whether this is an automatic snapshot.
	 * @param automatic true if automatic
	 */
	public void setAutomatic(boolean automatic) {
		this.setAttribute("automatic", automatic);
	}

	/**
	 * Instantiates a new take snapshot task.
	 * @param device The device to run the snapshot on
	 * @param comments Any comment about the task
	 * @param author The author of the task
	 * @param automatic Is it an automatic snapshot?
	 * @param dontRunDiagnostics Set to the true to disable running diagnostics
	 * @param dontCheckCompliance Set to true to disable compliance checking
	 */
	public TakeSnapshotTask(Device device, String comments, String author, boolean automatic,
		boolean dontRunDiagnostics, boolean dontCheckCompliance) {
		super(comments, device.getLastConfig() == null ? device.getMgmtAddress() : device.getName(),
			author);
		this.setDevice(device);
		this.setAutomatic(automatic);
		this.setDontRunDiagnostics(dontRunDiagnostics);
		this.setDontCheckCompliance(dontCheckCompliance);
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#deviceLockPolicy()
	 */
	@Override
	public DeviceLockPolicy deviceLockPolicy() {
		return DeviceLockPolicy.CANCEL_IF_SAME_TYPE;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#deviceLockTypeNamespace()
	 */
	@Override
	public int deviceLockTypeNamespace() {
		return TaskLockManager.NS_SNAPSHOT;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getTaskDescription()
	 */
	@Override
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public String getTaskDescription() {
		return "Device snapshot";
	}

	/**
	 * Get the ID of the device.
	 * 
	 * @return the ID of the device
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	protected long getDeviceId() {
		if (this.device == null) {
			return 0;
		}
		return device.getId();
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		TakeSnapshotTask task = (TakeSnapshotTask) super.clone();
		task.setDevice(this.device);
		return task;
	}

	/**
	 * Schedules an automatic snapshot of the device with the given address, if needed.
	 *
	 * @param drivers the names of the drivers requesting a snapshot
	 * @param address the address of the device
	 * @return true if the request was handled
	 * @deprecated use {@link net.netshot.netshot.task.AutoSnapshotScheduler}; kept for the callers which can't be injected
	 */
	@Deprecated
	public static boolean scheduleSnapshotIfNeeded(List<String> drivers, Network4Address address) {
		return Wiring.get().autoSnapshotScheduler().scheduleSnapshotIfNeeded(drivers, address);
	}

	/**
	 * Schedules an automatic snapshot of the given device, if needed.
	 *
	 * @param deviceId the device ID
	 * @return true if the request was handled
	 * @deprecated use {@link net.netshot.netshot.task.AutoSnapshotScheduler}; kept for the callers which can't be injected
	 */
	@Deprecated
	public static boolean scheduleSnapshotIfNeeded(long deviceId) {
		return Wiring.get().autoSnapshotScheduler().scheduleSnapshotIfNeeded(deviceId);
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#onSchedule()
	 */
	@Override
	public void onSchedule() {
		if (this.isAutomatic()) {
			checkAutoSnapshot(this.getDevice().getId());
		}
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#onCancel()
	 */
	@Override
	public void onCancel() {
		clearScheduledAutoSnapshot(this.device.getId());
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getIdentity()
	 */
	@Override
	@Transient
	public JobKey getIdentity() {
		return new JobKey(String.format("Task_%d", this.getId()),
			String.format("RunDevice_%d", this.getDeviceId()));
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getRunnerHash()
	 */
	@Override
	@Transient
	public long getRunnerHash() {
		return this.getDeviceId();
	}

}
