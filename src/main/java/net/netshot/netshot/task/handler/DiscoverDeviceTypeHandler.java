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

import org.hibernate.HibernateException;
import org.hibernate.Session;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.access.Snmp;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSnmpCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.NetworkAddress;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
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

	@Override
	public void run(DiscoverDeviceTypeTask task) {
		log.debug("Task {}. Starting autodiscovery process.", task.getId());
		boolean didTrySnmp = false;

		try {
			task.setResolvedAddress(NetworkAddress.getNetworkAddress(InetAddress.getByName(task.getDeviceAddress())));
		}
		catch (UnknownHostException e) {
			log.warn("Task {}. Unable to resolve device address '{}'.", task.getId(), task.getDeviceAddress(), e);
			task.logger.error("Unable to resolve device address '{}'.", task.getDeviceAddress());
			task.setStatus(Status.FAILURE);
			return;
		}

		log.trace("Task {}. {} credential sets in the list.", task.getId(), task.getCredentialSets().size());
		for (DeviceCredentialSet credentialSet : task.getCredentialSets()) {
			if (credentialSet instanceof DeviceSnmpv1Community snmpCommunity) {
				log.trace("Task {}. SNMPv1 credential set.", task.getId());
				didTrySnmp = true;
				if (this.snmpv1Discover(task, snmpCommunity)) {
					task.setStatus(Status.SUCCESS);
					task.setSuccessCredentialSet(credentialSet);
					break;
				}
			}
			else if (credentialSet instanceof DeviceSnmpv2cCommunity snmpCommunity) {
				log.trace("Task {}. SNMPv2c credential set.", task.getId());
				didTrySnmp = true;
				if (this.snmpv2cDiscover(task, snmpCommunity)) {
					task.setStatus(Status.SUCCESS);
					task.setSuccessCredentialSet(credentialSet);
					break;
				}
			}
			else if (credentialSet instanceof DeviceSnmpv3Community snmpCreds) {
				log.trace("Task {}. SNMPv3 credential set.", task.getId());
				didTrySnmp = true;
				if (this.snmpv3Discover(task, snmpCreds)) {
					task.setStatus(Status.SUCCESS);
					task.setSuccessCredentialSet(credentialSet);
					break;
				}
			}
		}
		if (task.getStatus() == Status.SUCCESS) {
			Task snapshotTask = null;
			Session session = Database.getSession();
			task.setDiscoverResultDevice(null);
			Device newDevice = null;
			try {
				session.beginTransaction();
				newDevice = new Device(task.getDiscoveredDeviceType(), task.getDeviceAddress(), task.getDomain(), task.getAuthor());
				// Create a bare (enabled, no pin) DeviceAccess row for every access the
				// driver declares, so each is usable via the domain's auto-try pool -
				// an access with no row at all is never used (see AccessManager).
				DeviceDriver newDeviceDriver = this.driverRegistry.getDriverByName(task.getDiscoveredDeviceType());
				if (newDeviceDriver != null) {
					for (String accessName : newDeviceDriver.getAccessDefinitions().keySet()) {
						newDevice.getAccesses().add(new DeviceAccess(newDevice, accessName));
					}
				}
				if (task.getDeviceComments() != null) {
					newDevice.setComments(task.getDeviceComments());
				}
				newDevice.replaceTags(task.getDeviceTags());
				task.setDiscoverResultDevice(newDevice);
				session.persist(newDevice);
				snapshotTask = new TakeSnapshotTask(newDevice,
					"Automatic snapshot after discovery", task.getAuthor(), true, false, false);
				snapshotTask.setPriority(task.getPriority());
				snapshotTask.setParentTaskId(task.getId());
				session.persist(snapshotTask);
				session.getTransaction().commit();
				task.setSnapshotTaskId(snapshotTask.getId());
			}
			catch (HibernateException e) {
				Database.rollbackSilently(session);
				log.error("Task {}. Couldn't save the new device.", task.getId(), e);
				task.logger.error("Database error while adding the device");
			}
			catch (Exception e) {
				log.error("Task {}. Error while saving the new device or the new task.", task.getId(), e);
				task.logger.error("Couldn't add the device after discovery");
			}
			finally {
				session.close();
			}

			if (newDevice != null) {
				DynamicDeviceGroup.refreshAllGroupsOfOneDevice(newDevice);
			}

			try {
				if (snapshotTask != null) {
					this.taskSubmitter.submit(snapshotTask);
				}
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the new snapshot task.", task.getId(), e);
			}

			return;
		}
		if (!didTrySnmp) {
			log.warn("Task {}. No available SNMP credential set.", task.getId());
			task.logger.error(
				"No available SNMP credential set... cannot start autodiscovery.");
		}
		task.setStatus(Status.FAILURE);
	}

	private boolean snmpDiscover(DiscoverDeviceTypeTask task, Snmp poller) {
		log.debug("Task {}. Trying SNMP discovery.", task.getId());
		String sysObjectId;
		String sysDesc;
		try {
			sysDesc = poller.getAsString("1.3.6.1.2.1.1.1.0");
			sysObjectId = poller.getAsString("1.3.6.1.2.1.1.2.0");
			task.logger.debug("Got sysDesc = {}", sysDesc);
			task.logger.debug("Got sysObjectID = {}", sysObjectId);
			log.trace("Got sysDesc '{}' and sysObjectID '{}'.", sysDesc,
				sysObjectId);

			// Iterates over possible device classes
			for (DeviceDriver driver : this.driverRegistry.getAllDrivers()) {
				if (driver.snmpAutoDiscover(task, sysObjectId, sysDesc, task.logger)) {
					log.trace("The driver {} did accept the OID.", driver.getName());
					task.setDiscoveredDeviceType(driver.getName());
					return true;
				}
			}
		}
		catch (IOException e) {
			log.warn("Error while polling the device via SNMP.", e);
			task.logger.error("Error while polling the device via SNMP.");
		}
		finally {
			try {
				poller.stop();
			}
			catch (IOException e) {
			}
		}
		log.debug("No driver has accepted the OID.");
		return false;
	}

	private boolean snmpv1Discover(DiscoverDeviceTypeTask task, DeviceSnmpCommunity community) {
		log.trace("Task {}. SNMPv1 discovery with credential set {}.",
			task.getId(), community.getName());
		task.logger.trace("Trying SNMPv1 discovery (credentials {})", community.getName());
		try {
			Snmp poller = new Snmp(task.getResolvedAddress(), community);
			return this.snmpDiscover(task, poller);
		}
		catch (UnknownHostException e) {
			log.warn("Task {}. SNMPv1 unknown host error.", task.getId(), e);
			task.logger.warn("SNMPv1 unknown host error: {}", e.getMessage());
		}
		catch (Exception e) {
			log.error("Task {}. SNMPv1 error while polling the device.", task.getId(), e);
			task.logger.warn("Error while SNMPv1 polling the device: {}", e.getMessage());
		}
		return false;
	}

	private boolean snmpv2cDiscover(DiscoverDeviceTypeTask task, DeviceSnmpCommunity community) {
		log.trace("Task {}. SNMPv2c discovery with credential set {}.",
			task.getId(), community.getName());
		task.logger.trace("Trying SNMPv2c discovery (credential set {})", community.getName());
		try {
			Snmp poller = new Snmp(task.getResolvedAddress(), community);
			return this.snmpDiscover(task, poller);
		}
		catch (UnknownHostException e) {
			log.warn("Task {}. SNMPv2 unknown host error.", task.getId(), e);
			task.logger.warn("SNMPv2 unknown host error: {}", e.getMessage());
		}
		catch (Exception e) {
			log.error("Task {}. SNMPv2 error while polling the device.", task.getId(), e);
			task.logger.warn("Error while SNMPv2 polling the device: {}", e.getMessage());
		}
		return false;
	}

	private boolean snmpv3Discover(DiscoverDeviceTypeTask task, DeviceSnmpv3Community cred) {
		log.trace("Task {}. SNMPv3 discovery with credential set {}.",
			task.getId(), cred.getName());
		task.logger.trace("Trying SNMPv3 discovery (credentials {})", cred.getName());
		try {
			Snmp poller = new Snmp(task.getResolvedAddress(), cred);
			return this.snmpDiscover(task, poller);
		}
		catch (UnknownHostException e) {
			log.warn("Task {}. SNMPv3 unknown host error.", task.getId(), e);
			task.logger.warn("SNMPv3 unknown host error: {}", e.getMessage());
		}
		catch (Exception e) {
			log.error("Task {}. SNMPv3 error while polling the device.", task.getId(), e);
			task.logger.warn("Error while SNMPv3 polling the device: {}", e.getMessage());
		}
		return false;
	}

}
