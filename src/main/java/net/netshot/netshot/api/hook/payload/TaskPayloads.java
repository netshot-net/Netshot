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
package net.netshot.netshot.api.hook.payload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.hibernate.Hibernate;

import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device.MissingDeviceDriverException;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * Maps a task to its webhook payload. All the methods are pure functions.
 *
 * <p>{@link #toPayload(Task)} reads the task while the session which loaded it is still open (see
 * {@code TaskService.mapTask}): the payload embeds the target device, with its accesses and
 * groups, which are lazily loaded.</p>
 */
public final class TaskPayloads {

	/** What stands for a secret in the payloads. */
	private static final String MASK = "=";

	private TaskPayloads() {
		// Static helpers only
	}

	/**
	 * Maps a task to its payload.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param task the task, attached to an open session and prepared
	 * @return the payload, of the subtype matching the type of the task
	 * @throws IllegalArgumentException if the type of task has no payload
	 */
	public static TaskPayload toPayload(DriverRegistry driverRegistry, Task task) {
		Task concrete = (Task) Hibernate.unproxy(task);
		TaskBasePayload base = toBasePayload(driverRegistry, concrete);
		if (concrete instanceof TakeSnapshotTask snapshot) {
			return new TaskPayload.TakeSnapshot(base, snapshot.isAutomatic(), deviceId(concrete));
		}
		else if (concrete instanceof CheckComplianceTask) {
			return new TaskPayload.CheckCompliance(base, deviceId(concrete));
		}
		else if (concrete instanceof RunDeviceScriptTask) {
			return new TaskPayload.RunDeviceScript(base, deviceId(concrete));
		}
		else if (concrete instanceof RunDiagnosticsTask) {
			return new TaskPayload.RunDiagnostics(base, deviceId(concrete));
		}
		else if (concrete instanceof DiscoverDeviceTypeTask discover) {
			Device resultDevice = discover.getDiscoverResultDevice();
			return new TaskPayload.DiscoverDeviceType(base, discover.getDeviceAddress(), resultDevice == null ? 0 : resultDevice.getId(),
				toPayload(driverRegistry, resultDevice), discoveredDeviceTypeDescription(driverRegistry, discover), discover.getSnapshotTaskId());
		}
		else if (concrete instanceof TakeGroupSnapshotTask snapshot) {
			return new TaskPayload.TakeGroupSnapshot(base, deviceGroupId(concrete), snapshot.getLimitToOutofdateDeviceHours());
		}
		else if (concrete instanceof CheckGroupComplianceTask) {
			return new TaskPayload.CheckGroupCompliance(base, deviceGroupId(concrete));
		}
		else if (concrete instanceof CheckGroupSoftwareTask) {
			return new TaskPayload.CheckGroupSoftware(base, deviceGroupId(concrete));
		}
		else if (concrete instanceof RunGroupDiagnosticsTask) {
			return new TaskPayload.RunGroupDiagnostics(base, deviceGroupId(concrete));
		}
		else if (concrete instanceof RunDeviceGroupScriptTask) {
			return new TaskPayload.RunDeviceGroupScript(base, deviceGroupId(concrete));
		}
		else if (concrete instanceof PurgeDatabaseTask purge) {
			return new TaskPayload.PurgeDatabase(base, purge.getConfigDays(), purge.getConfigKeepDays(), purge.getConfigSize(),
				purge.getDays(), deviceGroupId(concrete), purge.getModuleDays(), purge.isRemoveOrphanFiles());
		}
		else if (concrete instanceof ScanSubnetsTask) {
			return new TaskPayload.ScanSubnets(base);
		}
		throw new IllegalArgumentException("No webhook payload for the task type " + concrete.getClass().getSimpleName());
	}

	private static TaskBasePayload toBasePayload(DriverRegistry driverRegistry, Task task) {
		return new TaskBasePayload(
			task.getAuthor(),
			task.isCancelRequested(),
			task.getChangeDate(),
			task.isCheckCompliance(),
			task.getChildOrder(),
			task.getComments(),
			task.getCreationDate(),
			task.isDebugEnabled(),
			toPayload(driverRegistry, task.getDevice()),
			task.getDeviceDriver(),
			task.isDontCheckCompliance(),
			task.isDontRunDiagnostics(),
			task.getExecutionDate(),
			task.getId(),
			task.getLog(),
			task.getNextExecutionDate(),
			task.getParentTaskId(),
			task.getPriority(),
			task.isRepeating(),
			task.isRunDiagnostics(),
			task.isRunSnapshot(),
			task.getRunnerId(),
			task.getScheduleFactor(),
			task.getScheduleMode(),
			task.getScheduleReference(),
			task.getScheduleType(),
			task.getScript(),
			task.getStatus(),
			task.isStopOnFailure(),
			task.getTarget(),
			task.getTaskDescription(),
			task.getUserInputValues());
	}

	// The ID of the target device, 0 for none.
	private static long deviceId(Task task) {
		Device device = task.getDevice();
		return device == null ? 0 : device.getId();
	}

	// The ID of the target group, 0 for none.
	private static long deviceGroupId(Task task) {
		DeviceGroup group = task.getDeviceGroup();
		return group == null ? 0 : group.getId();
	}

	// The description of the driver of the device
	private static String realDeviceType(DriverRegistry driverRegistry, Device device) {
		try {
			return driverRegistry.forDevice(device).getDescription();
		}
		catch (MissingDeviceDriverException e) {
			return "Unknown driver";
		}
	}

	// The description of the discovered device type, "Unknown" when there is no such driver
	private static String discoveredDeviceTypeDescription(DriverRegistry driverRegistry, DiscoverDeviceTypeTask task) {
		DeviceDriver deviceDriver = driverRegistry.getDriverByName(task.getDiscoveredDeviceType());
		if (deviceDriver == null) {
			return "Unknown";
		}
		return deviceDriver.getDescription();
	}

	private static DevicePayload toPayload(DriverRegistry driverRegistry, Device deviceOrProxy) {
		if (deviceOrProxy == null) {
			return null;
		}
		Device device = (Device) Hibernate.unproxy(deviceOrProxy);
		// The collections of the device are sets: they are listed in a stable order
		List<DeviceAccessPayload> accesses = new ArrayList<>();
		device.getAccesses().stream().sorted(Comparator.comparing(DeviceAccess::getAccessName, Comparator.nullsFirst(Comparator.naturalOrder())))
			.forEach(access -> accesses.add(toPayload(access)));
		List<DeviceGroupPayload> ownerGroups = new ArrayList<>();
		device.getOwnerGroups().stream().sorted(Comparator.comparingLong(DeviceGroup::getId))
			.forEach(group -> ownerGroups.add(toPayload(group)));
		return new DevicePayload(
			accesses,
			device.getCachedIpAddressAsString(),
			device.getChangeDate(),
			device.getComments(),
			device.isCompliant(),
			device.getContact(),
			device.getCreatedDate(),
			device.getCreator(),
			device.getDriver(),
			device.isEndOfLife(),
			device.isEndOfSale(),
			device.getEolDate(),
			toPayload(device.getEolModule()),
			device.getEosDate(),
			toPayload(device.getEosModule()),
			device.getFamily(),
			device.getLocation(),
			device.getMgmtAddress(),
			device.getName(),
			device.getNetworkClass(),
			ownerGroups,
			realDeviceType(driverRegistry, device),
			device.getSerialNumber(),
			device.getSoftwareLevel(),
			device.getSoftwareVersion(),
			device.getStatus(),
			device.getTags().stream().sorted().toList());
	}

	private static ModulePayload toPayload(Module moduleOrProxy) {
		if (moduleOrProxy == null) {
			return null;
		}
		Module module = (Module) Hibernate.unproxy(moduleOrProxy);
		return new ModulePayload(
			module.getId(),
			module.getSlot(),
			module.getPartNumber(),
			module.getSerialNumber(),
			module.getFirstSeenDate(),
			module.getLastSeenDate(),
			module.isRemoved());
	}

	private static DeviceAccessPayload toPayload(DeviceAccess access) {
		return new DeviceAccessPayload(
			access.getAccessName(),
			access.getAddress(),
			access.getPort(),
			toPayload(access.getGlobalCredentialSet()),
			toPayload(access.getSpecificCredentialSet()),
			access.getSshHostKeyVerification(),
			access.getSshTrustedHostKeys(),
			access.getSshTrustedHostKeysSince(),
			access.getHttpsCaTrustMode(),
			access.getHttpsCustomCaCertificate());
	}

	private static DeviceGroupPayload toPayload(DeviceGroup groupOrProxy) {
		DeviceGroup group = (DeviceGroup) Hibernate.unproxy(groupOrProxy);
		if (group instanceof DynamicDeviceGroup dynamic) {
			return new DeviceGroupPayload.DynamicGroup(dynamic.getId(), dynamic.getChangeDate(), dynamic.getName(),
				dynamic.getFolder(), dynamic.isHiddenFromReports(), dynamic.getQuery());
		}
		else if (group instanceof StaticDeviceGroup) {
			return new DeviceGroupPayload.StaticGroup(group.getId(), group.getChangeDate(), group.getName(),
				group.getFolder(), group.isHiddenFromReports());
		}
		throw new IllegalArgumentException("Unknown kind of device group: " + group.getClass().getName());
	}

	private static DomainPayload toPayload(Domain domain) {
		if (domain == null) {
			return null;
		}
		return new DomainPayload(
			domain.getId(),
			domain.getChangeDate(),
			domain.getName(),
			domain.getDescription(),
			domain.getServer4Address() == null ? null : domain.getServer4Address().getIp(),
			domain.getServer6Address() == null ? null : domain.getServer6Address().getIp());
	}

	// A secret never goes into a payload: it is replaced by the mask when there is one.
	private static String masked(String secret) {
		return secret == null ? null : MASK;
	}

	private static CredentialSetPayload toPayload(DeviceCredentialSet credentialSet) {
		if (credentialSet == null) {
			return null;
		}
		DeviceCredentialSet cs = (DeviceCredentialSet) Hibernate.unproxy(credentialSet);
		DomainPayload domain = toPayload(cs.getMgmtDomain());
		if (cs instanceof DeviceSnmpv3Community c) {
			return new CredentialSetPayload.SnmpV3(c.getId(), c.getName(), domain, c.isDeviceSpecific(),
				masked(c.getCommunity()), c.getResolvedCommunityVaultInstanceId(), c.getCommunityVaultPath(),
				c.getUsername(), c.getResolvedUsernameVaultInstanceId(), c.getUsernameVaultPath(),
				c.getAuthType(), masked(c.getAuthKey()), c.getResolvedAuthKeyVaultInstanceId(), c.getAuthKeyVaultPath(),
				c.getPrivType(), masked(c.getPrivKey()), c.getResolvedPrivKeyVaultInstanceId(), c.getPrivKeyVaultPath());
		}
		else if (cs instanceof DeviceSnmpv2cCommunity c) {
			return new CredentialSetPayload.SnmpV2c(c.getId(), c.getName(), domain, c.isDeviceSpecific(),
				masked(c.getCommunity()), c.getResolvedCommunityVaultInstanceId(), c.getCommunityVaultPath());
		}
		else if (cs instanceof DeviceSnmpv1Community c) {
			return new CredentialSetPayload.SnmpV1(c.getId(), c.getName(), domain, c.isDeviceSpecific(),
				masked(c.getCommunity()), c.getResolvedCommunityVaultInstanceId(), c.getCommunityVaultPath());
		}
		else if (cs instanceof DeviceSshKeyAccount c) {
			return new CredentialSetPayload.SshKey(c.getId(), c.getName(), domain, c.isDeviceSpecific(),
				masked(c.getPrivateKey()), c.getResolvedPrivateKeyVaultInstanceId(), c.getPrivateKeyVaultPath());
		}
		else if (cs instanceof DeviceSshAccount) {
			return new CredentialSetPayload.Ssh(cs.getId(), cs.getName(), domain, cs.isDeviceSpecific());
		}
		else if (cs instanceof DeviceTelnetAccount) {
			return new CredentialSetPayload.Telnet(cs.getId(), cs.getName(), domain, cs.isDeviceSpecific());
		}
		else if (cs instanceof DeviceHttpAccount) {
			return new CredentialSetPayload.Http(cs.getId(), cs.getName(), domain, cs.isDeviceSpecific());
		}
		throw new IllegalArgumentException("Unknown kind of credential set: " + cs.getClass().getName());
	}
}
