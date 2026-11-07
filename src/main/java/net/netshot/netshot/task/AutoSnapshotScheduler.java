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
package net.netshot.netshot.task;

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.NETWORK_INTERFACE;
import static net.netshot.netshot.platform.database.schema.Tables.NETWORK_INTERFACE_IP4ADDRESSES;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Schedules the automatic snapshots of the devices which signal a configuration change
 * (syslog message, SNMP trap, cluster notification).
 */
@Slf4j
public class AutoSnapshotScheduler {

	/**
	 * The settings of the automatic snapshots.
	 *
	 * @param autoSnapshotAnyIp whether a message from any IP address of a device (not only the management one)
	 *        triggers an automatic snapshot
	 * @param autoSnapshotInterval the minutes to wait before starting an automatic snapshot
	 */
	public record Settings(boolean autoSnapshotAnyIp, int autoSnapshotInterval) {

		/** Whether a message from any IP address of a device triggers an automatic snapshot. */
		static final Setting<Boolean> ANY_IP = Setting.bool("netshot.snapshots.auto.anyip").defaultValue(false).live();

		/** Minutes to wait before starting an automatic snapshot. */
		static final Setting<Integer> INTERVAL = Setting.integer("netshot.snapshots.auto.interval")
			.defaultValue(10).range(1, 60 * 24 * 7).live();

		/**
		 * Reads the settings.
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			return new Settings(config.get(ANY_IP), config.get(INTERVAL));
		}
	}

	private final ConfigStore.Binding<Settings> settings;
	private final TaskSubmitter taskSubmitter;
	private final TaskManager taskManager;
	private final AutoSnapshotQueue queue;

	/**
	 * Creates the scheduler.
	 * @param taskSubmitter what new tasks are handed to
	 * @param config the configuration store, to read the settings from
	 * @param taskManager the task manager (to know whether the local instance is a cluster member)
	 * @param queue the devices with a queued automatic snapshot
	 */
	public AutoSnapshotScheduler(TaskSubmitter taskSubmitter, ConfigStore config, TaskManager taskManager, AutoSnapshotQueue queue) {
		this.taskSubmitter = taskSubmitter;
		this.settings = config.bind(Settings::from);
		this.taskManager = taskManager;
		this.queue = queue;
	}

	/**
	 * Gets the settings.
	 * @return the settings
	 */
	public Settings getSettings() {
		return this.settings.get();
	}

	public boolean scheduleSnapshotIfNeeded(List<String> drivers, Network4Address address) {
		log.debug("Request to take a snapshot of device with IP {}, if necessary.",
			address.getIp());
		Device device;
		try {
			log.trace("Retrieving the device.");
			device = this.taskManager.transactions().read(dsl -> {
				Long deviceId = dsl.select(DEVICE.ID).from(DEVICE)
					.where(DEVICE.STATUS.eq(Device.Status.INPRODUCTION.name()), DEVICE.CACHED_IP_ADDRESS.eq(address.getInetAddress()))
					.fetchOne(DEVICE.ID);
				if (deviceId == null && this.settings.get().autoSnapshotAnyIp()) {
					log.warn("No device with such management IP {} in the database. Looking for this address in the interface table.",
						address.getIp());
					deviceId = dsl.selectDistinct(DEVICE.ID).from(DEVICE)
						.join(NETWORK_INTERFACE).on(NETWORK_INTERFACE.DEVICE.eq(DEVICE.ID))
						.join(NETWORK_INTERFACE_IP4ADDRESSES).on(NETWORK_INTERFACE_IP4ADDRESSES.NETWORK_INTERFACE.eq(NETWORK_INTERFACE.ID))
						.where(DEVICE.STATUS.eq(Device.Status.INPRODUCTION.name()), NETWORK_INTERFACE_IP4ADDRESSES.ADDRESS.eq(address.getAddress()))
						.fetchOne(DEVICE.ID);
				}
				return deviceId == null ? null : new DeviceRepository().find(dsl, deviceId);
			});
			if (device == null) {
				log.warn("No device with such IP address {} in the database.", address.getIp());
				return false;
			}
			if (!drivers.contains(device.driver())) {
				log.warn("The driver {} of the device {} in database isn't in the list of drivers requesting a snapshot (address {}).",
					device.driver(), device.id(), device.mgmtAddress());
				return false;
			}
		}
		catch (Exception e) {
			log.error("Error while checking whether the snapshot is needed or not.", e);
			return true;
		}

		if (TaskManager.Mode.CLUSTER_MEMBER.equals(this.taskManager.getMode())) {
			log.debug("The local instance is cluster member: sending notification to master for an auto snapshot of device ID {}", device.id());
			this.taskManager.cluster().requestAutoSnapshot(device.id());
			return true;
		}

		return this.scheduleSnapshotIfNeeded(device);
	}

	public boolean scheduleSnapshotIfNeeded(long deviceId) {
		log.debug("Request to take a snapshot of device ID {}.", deviceId);
		Device device;
		try {
			log.trace("Retrieving the device.");
			device = this.taskManager.transactions().read(dsl -> new DeviceRepository().find(dsl, deviceId));
			if (device == null) {
				log.warn("No device with such ID {} in the database.", deviceId);
				return false;
			}
		}
		catch (Exception e) {
			log.error("Error while checking whether the snapshot is needed or not.", e);
			return true;
		}

		return this.scheduleSnapshotIfNeeded(device);
	}

	private boolean scheduleSnapshotIfNeeded(Device device) {
		if (!this.queue.markQueued(device.id())) {
			log.debug("A snapshot task is already scheduled.");
			return true;
		}
		try {
			Task snapshot = TakeSnapshotTask.of(device, "Automatic snapshot after config change", "Auto", true, false, false)
				.scheduledIn(this.settings.get().autoSnapshotInterval());
			this.taskSubmitter.submit(snapshot);
		}
		catch (Exception e) {
			log.error("Error while scheduling the automatic snapshot.", e);
		}
		return true;
	}

}
