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
package net.netshot.netshot.task.handler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.jooq.exception.DataAccessException;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.access.Snmp;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSnmpCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.NetworkAddress;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.domain.DomainRepository;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Discovers the type of a device.
 */
@Slf4j
public class DiscoverDeviceTypeHandler implements TaskHandler<DiscoverDeviceTypeTask> {

	private final DriverRegistry driverRegistry;
	private final TaskSubmitter taskSubmitter;
	private final TaskRepository taskRepository = new TaskRepository();

	/**
	 * Creates the handler.
	 * @param driverRegistry the driver registry
	 * @param taskSubmitter the task submitter
	 */
	public DiscoverDeviceTypeHandler(DriverRegistry driverRegistry, TaskSubmitter taskSubmitter) {
		this.driverRegistry = driverRegistry;
		this.taskSubmitter = taskSubmitter;
	}

	@Override
	public Class<DiscoverDeviceTypeTask> taskType() {
		return DiscoverDeviceTypeTask.class;
	}

	// Loads the credential sets to try, by ID
	private static List<DeviceCredentialSet> loadCredentialSets(DiscoverDeviceTypeTask spec) {
		if (spec.credentialSetIds().isEmpty()) {
			return List.of();
		}
		return new Transactions().read(dsl -> new CredentialSetRepository().findAll(dsl, spec.credentialSetIds()));
	}

	@Override
	public void run(Task task, DiscoverDeviceTypeTask spec, TaskRun run) {
		log.debug("Task {}. Starting autodiscovery process.", task.id());
		boolean didTrySnmp = false;

		NetworkAddress address;
		try {
			address = NetworkAddress.getNetworkAddress(InetAddress.getByName(spec.deviceAddress()));
		}
		catch (UnknownHostException e) {
			log.warn("Task {}. Unable to resolve device address '{}'.", task.id(), spec.deviceAddress(), e);
			run.logger.error("Unable to resolve device address '{}'.", spec.deviceAddress());
			run.setStatus(Status.FAILURE);
			return;
		}

		List<DeviceCredentialSet> credentialSets = loadCredentialSets(spec);
		log.trace("Task {}. {} credential sets in the list.", task.id(), credentialSets.size());
		String discoveredDeviceType = null;
		for (DeviceCredentialSet credentialSet : credentialSets) {
			if (credentialSet instanceof DeviceSnmpv1Community snmpCommunity) {
				log.trace("Task {}. SNMPv1 credential set.", task.id());
				didTrySnmp = true;
				discoveredDeviceType = this.snmpv1Discover(task, run, address, snmpCommunity);
			}
			else if (credentialSet instanceof DeviceSnmpv2cCommunity snmpCommunity) {
				log.trace("Task {}. SNMPv2c credential set.", task.id());
				didTrySnmp = true;
				discoveredDeviceType = this.snmpv2cDiscover(task, run, address, snmpCommunity);
			}
			else if (credentialSet instanceof DeviceSnmpv3Community snmpCreds) {
				log.trace("Task {}. SNMPv3 credential set.", task.id());
				didTrySnmp = true;
				discoveredDeviceType = this.snmpv3Discover(task, run, address, snmpCreds);
			}
			if (discoveredDeviceType != null) {
				run.setStatus(Status.SUCCESS);
				run.setResult(DiscoverDeviceTypeTask.DISCOVERED_DEVICE_TYPE, discoveredDeviceType);
				break;
			}
		}
		if (run.getStatus() == Status.SUCCESS) {
			Task snapshotTask = null;
			Device newDevice = null;
			try {
				final String deviceType = discoveredDeviceType;
				Object[] created = new Transactions().write(dsl -> {
					Domain domain = spec.domainId() == null ? null : new DomainRepository().find(dsl, spec.domainId());
					Device.DeviceBuilder device = Device.create(deviceType, spec.deviceAddress(), domain, task.author()).toBuilder();
					// Create a bare (enabled, no pin) DeviceAccess row for every access the
					// driver declares, so each is usable via the domain's auto-try pool -
					// an access with no row at all is never used (see AccessManager).
					DeviceDriver newDeviceDriver = this.driverRegistry.getDriverByName(deviceType);
					if (newDeviceDriver != null) {
						List<DeviceAccess> accesses = new ArrayList<>();
						for (String accessName : newDeviceDriver.getAccessDefinitions().keySet()) {
							accesses.add(DeviceAccess.of(accessName));
						}
						device.accesses(accesses);
					}
					if (spec.deviceComments() != null) {
						device.comments(spec.deviceComments());
					}
					device.tags(Device.normalizeTags(spec.deviceTags()));
					Device added = new DeviceRepository().add(dsl, device.build());
					Task added2 = this.taskRepository.add(dsl, TakeSnapshotTask.of(added,
						"Automatic snapshot after discovery", task.author(), true, false, false).toBuilder()
						.priority(task.priority())
						.parentTaskId(task.id())
						.build());
					return new Object[] {added, added2};
				});
				newDevice = (Device) created[0];
				snapshotTask = (Task) created[1];
				run.setResultDeviceId(newDevice.id());
				run.setResult(DiscoverDeviceTypeTask.SNAPSHOT_TASK_ID, snapshotTask.id());
			}
			catch (DataAccessException e) {
				log.error("Task {}. Couldn't save the new device.", task.id(), e);
				run.logger.error("Database error while adding the device");
				newDevice = null;
				snapshotTask = null;
			}
			catch (Exception e) {
				log.error("Task {}. Error while saving the new device or the new task.", task.id(), e);
				run.logger.error("Couldn't add the device after discovery");
			}

			if (newDevice != null) {
				DynamicGroups.refreshAllGroupsOfOneDevice(newDevice.id());
			}

			try {
				if (snapshotTask != null) {
					this.taskSubmitter.submit(snapshotTask);
				}
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the new snapshot task.", task.id(), e);
			}

			return;
		}
		if (!didTrySnmp) {
			log.warn("Task {}. No available SNMP credential set.", task.id());
			run.logger.error(
				"No available SNMP credential set... cannot start autodiscovery.");
		}
		run.setStatus(Status.FAILURE);
	}

	// Polls the device, returns the name of the matching driver (or null)
	private String snmpDiscover(Task task, TaskRun run, Snmp poller) {
		log.debug("Task {}. Trying SNMP discovery.", task.id());
		String sysObjectId;
		String sysDesc;
		try {
			sysDesc = poller.getAsString("1.3.6.1.2.1.1.1.0");
			sysObjectId = poller.getAsString("1.3.6.1.2.1.1.2.0");
			run.logger.debug("Got sysDesc = {}", sysDesc);
			run.logger.debug("Got sysObjectID = {}", sysObjectId);
			log.trace("Got sysDesc '{}' and sysObjectID '{}'.", sysDesc,
				sysObjectId);

			// Iterates over possible device classes
			for (DeviceDriver driver : this.driverRegistry.getAllDrivers()) {
				if (driver.snmpAutoDiscover(sysObjectId, sysDesc, run.logger)) {
					return driver.getName();
				}
			}
		}
		catch (IOException e) {
			log.warn("Error while polling the device via SNMP.", e);
			run.logger.error("Error while polling the device via SNMP.");
		}
		finally {
			try {
				poller.stop();
			}
			catch (IOException e) {
				// Ignore
			}
		}
		log.debug("No driver has accepted the OID.");
		return null;
	}

	private String snmpv1Discover(Task task, TaskRun run, NetworkAddress address, DeviceSnmpCommunity community) {
		log.trace("Task {}. SNMPv1 discovery with credential set {}.",
			task.id(), community.name());
		run.logger.trace("Trying SNMPv1 discovery (credentials {})", community.name());
		try {
			Snmp poller = new Snmp(address, community);
			return this.snmpDiscover(task, run, poller);
		}
		catch (UnknownHostException e) {
			log.warn("Task {}. SNMPv1 unknown host error.", task.id(), e);
			run.logger.warn("SNMPv1 unknown host error: {}", e.getMessage());
		}
		catch (Exception e) {
			log.error("Task {}. SNMPv1 error while polling the device.", task.id(), e);
			run.logger.warn("Error while SNMPv1 polling the device: {}", e.getMessage());
		}
		return null;
	}

	private String snmpv2cDiscover(Task task, TaskRun run, NetworkAddress address, DeviceSnmpCommunity community) {
		log.trace("Task {}. SNMPv2c discovery with credential set {}.",
			task.id(), community.name());
		run.logger.trace("Trying SNMPv2c discovery (credential set {})", community.name());
		try {
			Snmp poller = new Snmp(address, community);
			return this.snmpDiscover(task, run, poller);
		}
		catch (UnknownHostException e) {
			log.warn("Task {}. SNMPv2 unknown host error.", task.id(), e);
			run.logger.warn("SNMPv2 unknown host error: {}", e.getMessage());
		}
		catch (Exception e) {
			log.error("Task {}. SNMPv2 error while polling the device.", task.id(), e);
			run.logger.warn("Error while SNMPv2 polling the device: {}", e.getMessage());
		}
		return null;
	}

	private String snmpv3Discover(Task task, TaskRun run, NetworkAddress address, DeviceSnmpv3Community cred) {
		log.trace("Task {}. SNMPv3 discovery with credential set {}.",
			task.id(), cred.name());
		run.logger.trace("Trying SNMPv3 discovery (credentials {})", cred.name());
		try {
			Snmp poller = new Snmp(address, cred);
			return this.snmpDiscover(task, run, poller);
		}
		catch (UnknownHostException e) {
			log.warn("Task {}. SNMPv3 unknown host error.", task.id(), e);
			run.logger.warn("SNMPv3 unknown host error: {}", e.getMessage());
		}
		catch (Exception e) {
			log.error("Task {}. SNMPv3 error while polling the device.", task.id(), e);
			run.logger.warn("Error while SNMPv3 polling the device: {}", e.getMessage());
		}
		return null;
	}
}
