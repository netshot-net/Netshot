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
package net.netshot.netshot.task;

import java.util.List;

import org.hibernate.Session;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Schedules the automatic snapshots of the devices which signal a configuration change
 * (syslog message, SNMP trap, cluster notification).
 */
@Slf4j
public class AutoSnapshotScheduler {

	/**
	 * Settings/config of the automatic snapshots.
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

	private final Settings settings = new Settings();
	private final TaskSubmitter taskSubmitter;

	/**
	 * Creates the scheduler, and loads its settings.
	 * @param taskSubmitter what new tasks are handed to
	 */
	public AutoSnapshotScheduler(TaskSubmitter taskSubmitter) {
		this.taskSubmitter = taskSubmitter;
		this.loadConfig();
	}

	/**
	 * Gets the settings.
	 * @return the settings
	 */
	public Settings getSettings() {
		return this.settings;
	}

	/**
	 * Loads (or reloads) the settings from the Netshot config file.
	 */
	public void loadConfig() {
		this.settings.load();
	}

	public boolean scheduleSnapshotIfNeeded(List<String> drivers, Network4Address address) {
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
			if (device == null && this.settings.isAutoSnapshotAnyIp()) {
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

		return this.scheduleSnapshotIfNeeded(device);
	}

	public boolean scheduleSnapshotIfNeeded(long deviceId) {
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

		return this.scheduleSnapshotIfNeeded(device);
	}

	private boolean scheduleSnapshotIfNeeded(Device device) {
		if (!TakeSnapshotTask.checkAutoSnapshot(device.getId())) {
			log.debug("A snapshot task is already scheduled.");
			return true;
		}
		try {
			Task snapshot = new TakeSnapshotTask(device, "Automatic snapshot after config change", "Auto", true, false, false);
			snapshot.schedule(this.settings.getAutoSnapshotInterval());
			this.taskSubmitter.submit(snapshot);
		}
		catch (Exception e) {
			log.error("Error while scheduling the automatic snapshot.", e);
		}
		return true;
	}

}
