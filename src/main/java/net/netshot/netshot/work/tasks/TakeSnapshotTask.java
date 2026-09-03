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

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.Session;
import org.quartz.JobKey;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.TaskManager;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.Config;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DynamicDeviceGroup;
import net.netshot.netshot.device.Network4Address;
import net.netshot.netshot.device.attribute.ConfigAttribute;
import net.netshot.netshot.device.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.device.script.DeviceScript;
import net.netshot.netshot.device.script.SnapshotDeviceScript;
import net.netshot.netshot.rest.RestViews.DefaultView;
import net.netshot.netshot.rest.RestViews.HookView;
import net.netshot.netshot.work.DebugLog;
import net.netshot.netshot.work.Task;

/**
 * This task takes a snapshot of a device.
 */
@Entity
@DiscriminatorValue("TakeSnapshotTask")
@Slf4j
public final class TakeSnapshotTask extends Task implements DeviceBasedTask {

	/**
	 * Settings/config for the current class.
	 */
	public static final class Settings {

		/** Allow trap from any IP of a device to trigger a automatic snapshot. */
		@Getter
		private boolean autoSnapshotAnyIp;

		/** Minutes to wait before starting an automatic snapshot. */
		@Getter
		private int autoSnapshotInterval;

		/**
		 * Load settings from config.
		 */
		private void load() {
			this.autoSnapshotAnyIp = Netshot.getConfig("netshot.snapshots.auto.anyip", false);
			this.autoSnapshotInterval = Netshot.getConfig("netshot.snapshots.auto.interval", 10, 1, 60 * 24 * 7);
		}
	}

	/** Settings for this class. */
	public static final Settings SETTINGS = new Settings();

	/** The scheduled automatic snapshots. */
	private static Set<Long> scheduledAutoSnapshots = ConcurrentHashMap.newKeySet();

	/** Device IDs on which a snapshot is currently running. */
	private static Set<Long> runningSnapshots = ConcurrentHashMap.newKeySet();

	/**
	 * Clear scheduled auto snapshot.
	 *
	 * @param deviceId the device
	 */
	public static void clearScheduledAutoSnapshot(Long deviceId) {
		scheduledAutoSnapshots.remove(deviceId);
	}

	/**
	 * Clear running snapshot status.
	 *
	 * @param deviceId the device
	 */
	public static void clearRunningSnapshot(Long deviceId) {
		runningSnapshots.remove(deviceId);
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
	 * Check whether a snapshot is currently running for the given device.
	 *
	 * @param deviceId the device
	 * @return true, if successful
	 */
	public static boolean checkRunningSnapshot(Long deviceId) {
		return runningSnapshots.add(deviceId);
	}

	/**
	 * Load TakeSnapshotTask specific configuration from Netshot config file.
	 */
	public static void loadConfig() {
		TakeSnapshotTask.SETTINGS.load();
	}

	static {
		TakeSnapshotTask.loadConfig();
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
	 * @see net.netshot.netshot.work.Task#run()
	 */
	@Override
	public void run() {
		log.debug("Task {}. Starting snapshot task for device {}.", this.getId(),
			device == null ? "null" : device.getId());
		if (device == null) {
			this.logger.info("The device doesn't exist, the task will be cancelled.");
			this.status = Status.CANCELLED;
			return;
		}
		boolean locked = false;

		Session session = Database.getSession();
		DeviceScript deviceScript = new SnapshotDeviceScript(this.logger);
		try {
			session.beginTransaction();
			// Start over from a fresh device from DB
			device = session.get(Device.class, device.getId());
			this.logger.info("Snapshot task for device {} ({}).",
				device.getName(), device.getMgmtAddress());
			if (device.getStatus() != Device.Status.INPRODUCTION) {
				log.trace("Task {}. Device not INPRODUCTION, stopping the snapshot task.", this.getId());
				this.logger.warn("The device is not enabled (not in production).");
				this.status = Status.CANCELLED;
				return;
			}
			locked = checkRunningSnapshot(device.getId());
			if (!locked) {
				log.trace("Task {}. Snapshot task already ongoing for this device, cancelling.", this.getId());
				this.logger.warn("A snapshot task is already running for this device, cancelling this task.");
				this.status = Status.CANCELLED;
				return;
			}

			deviceScript.connectRun(session, device);
			session.persist(device);
			session.getTransaction().commit();
			Config lastConfig = device.getLastConfig();
			if (lastConfig != null) {
				for (ConfigAttribute ca : lastConfig.getAttributes()) {
					if (ca instanceof ConfigBinaryFileAttribute cbfa) {
						cbfa.finalizeStorage();
					}
				}
			}
			this.status = Status.SUCCESS;
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Task {}. Error while taking the snapshot.", this.getId(), e);
			this.logger.error("Error while taking the snapshot: {}", e.getMessage());
			this.status = Status.FAILURE;
			return;
		}
		finally {
			try {
				if (this.fullLogs != null) {
					this.debugLog = new DebugLog(this.fullLogs.toString());
				}
			}
			catch (Exception e1) {
				log.error("Task {}. Error while saving the debug logs.", this.getId(), e1);
			}
			session.close();
			if (locked) {
				clearRunningSnapshot(device.getId());
			}
			if (this.isAutomatic()) {
				clearScheduledAutoSnapshot(this.getDevice().getId());
			}
		}

		log.debug("Task {}. Request to refresh all the groups for the device after the snapshot.", this.getId());
		DynamicDeviceGroup.refreshAllGroupsOfOneDevice(device);

		if (!this.dontRunDiagnostics) {
			try {
				Task diagTask = new RunDiagnosticsTask(device, "Run diagnostics after device snapshot", "Auto", this.dontCheckCompliance);
				diagTask.setPriority(this.getPriority());
				diagTask.setParentTaskId(this.getId());
				TaskManager.addTask(diagTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the diagnostic task.", this.getId(), e);
			}

		}
		else if (!this.dontCheckCompliance) {
			try {
				Task checkTask = new CheckComplianceTask(device, "Check compliance after device snapshot", "Auto");
				checkTask.setPriority(this.getPriority());
				checkTask.setParentTaskId(this.getId());
				TaskManager.addTask(checkTask);
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the check compliance task.", this.getId(), e);
			}
		}
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#getTaskDescription()
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
	 * @see net.netshot.netshot.work.Task#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		TakeSnapshotTask task = (TakeSnapshotTask) super.clone();
		task.setDevice(this.device);
		return task;
	}

	public static boolean scheduleSnapshotIfNeeded(List<String> drivers, Network4Address address) {
		log.debug("Request to take a snapshot of device with IP {}, if necessary.",
			address.getIp());
		Device device;
		Session session = Database.getSession();
		try {
			log.trace("Retrieving the device.");
			device = session.createQuery(
				"select d from Device d where d.status = :inprod and d.cachedIpAddress = :ip",
				Device.class)
				.setParameter("inprod", Device.Status.INPRODUCTION)
				.setParameter("ip", address.getInetAddress())
				.uniqueResult();
			if (device == null && TakeSnapshotTask.SETTINGS.isAutoSnapshotAnyIp()) {
				log.warn("No device with such management IP {} in the database. Looking for this address in the interface table.",
					address.getIp());
				device = session
					.createQuery(
						"select d from Device d join d.networkInterfaces ni join ni.ip4Addresses a where d.status = :inprod and a.address = :ip",
						Device.class)
					.setParameter("inprod", Device.Status.INPRODUCTION)
					.setParameter("ip", address.getAddress())
					.uniqueResult();
			}
			if (device == null) {
				log.warn("No device with such IP address {} in the database.", address.getIp());
				return false;
			}
			if (!drivers.contains(device.getDriver())) {
				log.warn("The driver {} of the device {} in database isn't in the list of drivers requesting a snapshot (address {}).",
					device.getDriver(), device.getId(), device.getMgmtAddress());
				return false;
			}
		}
		catch (Exception e) {
			log.error("Error while checking whether the snapshot is needed or not.", e);
			return true;
		}
		finally {
			session.close();
		}

		if (TaskManager.Mode.CLUSTER_MEMBER.equals(TaskManager.getMode())) {
			log.debug("The local instance is cluster member: sending notification to master for an auto snapshot of device ID {}", device.getId());
			ClusterManager.requestAutoSnapshot(device.getId());
			return true;
		}

		return scheduleSnapshotIfNeeded(device);
	}

	public static boolean scheduleSnapshotIfNeeded(long deviceId) {
		log.debug("Request to take a snapshot of device ID {}.", deviceId);
		Device device;
		Session session = Database.getSession();
		try {
			log.trace("Retrieving the device.");
			device = session.get(Device.class, deviceId);
			if (device == null) {
				log.warn("No device with such ID {} in the database.", deviceId);
				return false;
			}
		}
		catch (Exception e) {
			log.error("Error while checking whether the snapshot is needed or not.", e);
			return true;
		}
		finally {
			session.close();
		}

		return scheduleSnapshotIfNeeded(device);
	}

	private static boolean scheduleSnapshotIfNeeded(Device device) {
		if (!checkAutoSnapshot(device.getId())) {
			log.debug("A snapshot task is already scheduled.");
			return true;
		}
		try {
			Task snapshot = new TakeSnapshotTask(device, "Automatic snapshot after config change", "Auto", true, false, false);
			snapshot.schedule(TakeSnapshotTask.SETTINGS.getAutoSnapshotInterval());
			TaskManager.addTask(snapshot);
		}
		catch (Exception e) {
			log.error("Error while scheduling the automatic snapshot.", e);
		}
		return true;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#onSchedule()
	 */
	@Override
	public void onSchedule() {
		if (this.isAutomatic()) {
			checkAutoSnapshot(this.getDevice().getId());
		}
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#onCancel()
	 */
	@Override
	public void onCancel() {
		clearScheduledAutoSnapshot(this.device.getId());
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.work.Task#getIdentity()
	 */
	@Override
	@Transient
	public JobKey getIdentity() {
		return new JobKey(String.format("Task_%d", this.getId()),
			String.format("RunDevice_%d", this.getDeviceId()));
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.work.Task#getRunnerHash()
	 */
	@Override
	@Transient
	public long getRunnerHash() {
		return this.getDeviceId();
	}

}
