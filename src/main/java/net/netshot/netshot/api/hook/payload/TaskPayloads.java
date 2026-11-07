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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.driver.MissingDeviceDriverException;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.type.CheckComplianceTask;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;
import net.netshot.netshot.task.type.DeviceBasedTask;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.PurgeDatabaseTask;
import net.netshot.netshot.task.type.RunDeviceGroupScriptTask;
import net.netshot.netshot.task.type.RunDeviceScriptTask;
import net.netshot.netshot.task.type.RunDiagnosticsTask;
import net.netshot.netshot.task.type.RunGroupDiagnosticsTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;
import net.netshot.netshot.task.type.TakeGroupSnapshotTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;
import net.netshot.netshot.task.type.TaskSpec;
import net.netshot.netshot.task.type.TaskSpecs;

/**
 * Maps a task to its webhook payload. All the methods are pure functions.
 *
 * <p>{@link #toPayload} runs in the read transaction of {@code TaskService.mapTask}: the payload
 * embeds the target device, with its accesses and groups, which it loads on demand through
 * the given function.</p>
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
	 * @param task the task
	 * @param devices gets a device by ID (in an open session), null for a null ID
	 * @return the payload, of the subtype matching the type of the task
	 * @throws IllegalArgumentException if the type of task has no payload
	 */
	public static TaskPayload toPayload(DriverRegistry driverRegistry, Task task, Function<Long, Device> devices) {
		TaskSpec spec = task.spec();
		Device device = spec instanceof DeviceBasedTask deviceTask ? devices.apply(deviceTask.deviceId()) : null;
		TaskBasePayload base = toBasePayload(driverRegistry, task, device);
		return switch (spec) {
			case TakeSnapshotTask s -> new TaskPayload.TakeSnapshot(base, s.automatic(), TaskSpecs.deviceId(s));
			case CheckComplianceTask s -> new TaskPayload.CheckCompliance(base, TaskSpecs.deviceId(s));
			case RunDeviceScriptTask s -> new TaskPayload.RunDeviceScript(base, TaskSpecs.deviceId(s));
			case RunDiagnosticsTask s -> new TaskPayload.RunDiagnostics(base, TaskSpecs.deviceId(s));
			case DiscoverDeviceTypeTask s -> new TaskPayload.DiscoverDeviceType(base, s.deviceAddress(),
				s.discoverResultDeviceId() == null ? 0 : s.discoverResultDeviceId(),
				toPayload(driverRegistry, devices.apply(s.discoverResultDeviceId())),
				discoveredDeviceTypeDescription(driverRegistry, s), s.snapshotTaskId());
			case TakeGroupSnapshotTask s -> new TaskPayload.TakeGroupSnapshot(base, TaskSpecs.deviceGroupId(s), s.limitToOutofdateDeviceHours());
			case CheckGroupComplianceTask s -> new TaskPayload.CheckGroupCompliance(base, TaskSpecs.deviceGroupId(s));
			case CheckGroupSoftwareTask s -> new TaskPayload.CheckGroupSoftware(base, TaskSpecs.deviceGroupId(s));
			case RunGroupDiagnosticsTask s -> new TaskPayload.RunGroupDiagnostics(base, TaskSpecs.deviceGroupId(s));
			case RunDeviceGroupScriptTask s -> new TaskPayload.RunDeviceGroupScript(base, TaskSpecs.deviceGroupId(s));
			case PurgeDatabaseTask s -> new TaskPayload.PurgeDatabase(base, s.configDays(), s.configKeepDays(), s.configSize(),
				s.days(), TaskSpecs.deviceGroupId(s), s.moduleDays(), s.removeOrphanFiles());
			case ScanSubnetsTask _ -> new TaskPayload.ScanSubnets(base);
		};
	}

	private static Date date(Instant instant) {
		return instant == null ? null : Date.from(instant);
	}

	private static TaskBasePayload toBasePayload(DriverRegistry driverRegistry, Task task, Device device) {
		TaskSpec spec = task.spec();
		return new TaskBasePayload(
			task.author(),
			task.cancelRequested(),
			date(task.changeDate()),
			TaskSpecs.checkCompliance(spec),
			task.childOrder(),
			task.comments(),
			date(task.creationDate()),
			task.debugEnabled(),
			toPayload(driverRegistry, device),
			TaskSpecs.deviceDriver(spec),
			TaskSpecs.dontCheckCompliance(spec),
			TaskSpecs.dontRunDiagnostics(spec),
			date(task.executionDate()),
			task.id(),
			task.log(),
			task.nextExecutionDate(),
			task.parentTaskId(),
			task.priority(),
			task.isRepeating(),
			TaskSpecs.runDiagnostics(spec),
			TaskSpecs.runSnapshot(spec),
			task.runnerId(),
			task.scheduleFactor(),
			task.scheduleMode(),
			date(task.scheduleReference()),
			task.scheduleType(),
			TaskSpecs.script(spec),
			task.status(),
			task.stopOnFailure(),
			task.target(),
			task.description(),
			TaskSpecs.userInputValues(spec));
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
		DeviceDriver deviceDriver = driverRegistry.getDriverByName(task.discoveredDeviceType());
		if (deviceDriver == null) {
			return "Unknown";
		}
		return deviceDriver.getDescription();
	}

	private static DevicePayload toPayload(DriverRegistry driverRegistry, Device device) {
		if (device == null) {
			return null;
		}
		// The collections of the device are sets: they are listed in a stable order
		List<DeviceAccessPayload> accesses = new ArrayList<>();
		device.accesses().stream().sorted(Comparator.comparing(DeviceAccess::accessName, Comparator.nullsFirst(Comparator.naturalOrder())))
			.forEach(access -> accesses.add(toPayload(access)));
		List<DeviceGroupPayload> ownerGroups = new ArrayList<>();
		device.ownerGroups().stream().sorted(Comparator.comparingLong(DeviceGroup::id))
			.forEach(group -> ownerGroups.add(toPayload(group)));
		return new DevicePayload(
			accesses,
			device.getCachedIpAddressAsString(),
			date(device.changeDate()),
			device.comments(),
			device.compliant(),
			device.contact(),
			date(device.createdDate()),
			device.creator(),
			device.driver(),
			device.isEndOfLife(),
			device.isEndOfSale(),
			date(device.eolDate()),
			toPayload(device.eolModule()),
			date(device.eosDate()),
			toPayload(device.eosModule()),
			device.family(),
			device.location(),
			device.mgmtAddress(),
			device.name(),
			device.networkClass(),
			ownerGroups,
			realDeviceType(driverRegistry, device),
			device.serialNumber(),
			device.softwareLevel(),
			device.softwareVersion(),
			device.status(),
			device.tags().stream().sorted().toList());
	}

	private static ModulePayload toPayload(Module module) {
		if (module == null) {
			return null;
		}
		return new ModulePayload(
			module.id(),
			module.slot(),
			module.partNumber(),
			module.serialNumber(),
			date(module.firstSeenDate()),
			date(module.lastSeenDate()),
			module.removed());
	}

	private static DeviceAccessPayload toPayload(DeviceAccess access) {
		return new DeviceAccessPayload(
			access.accessName(),
			access.address(),
			access.port(),
			toPayload(access.globalCredentialSet()),
			toPayload(access.specificCredentialSet()),
			access.sshHostKeyVerification(),
			access.sshTrustedHostKeys(),
			date(access.sshTrustedHostKeysSince()),
			access.httpsCaTrustMode(),
			access.httpsCustomCaCertificate());
	}

	private static DeviceGroupPayload toPayload(DeviceGroup group) {
		return switch (group) {
			case DynamicDeviceGroup dynamic -> new DeviceGroupPayload.DynamicGroup(dynamic.id(), date(dynamic.changeDate()), dynamic.name(),
				dynamic.folder(), dynamic.hiddenFromReports(), dynamic.query());
			case StaticDeviceGroup staticGroup -> new DeviceGroupPayload.StaticGroup(staticGroup.id(), date(staticGroup.changeDate()),
				staticGroup.name(), staticGroup.folder(), staticGroup.hiddenFromReports());
		};
	}

	private static DomainPayload toPayload(Domain domain) {
		if (domain == null) {
			return null;
		}
		return new DomainPayload(
			domain.id(),
			date(domain.changeDate()),
			domain.name(),
			domain.description(),
			domain.server4Address() == null ? null : domain.server4Address().getIp(),
			domain.server6Address() == null ? null : domain.server6Address().getIp());
	}

	// A secret never goes into a payload: its local value is replaced by the mask when there is one (it is not decrypted).
	private static String masked(Secret secret) {
		return Secret.localValue(secret) == null ? null : MASK;
	}

	// The local value of a field which isn't a secret (the username).
	private static String clear(Secret value) {
		LocalSecret local = Secret.localValue(value);
		return local == null ? null : local.reveal();
	}

	private static CredentialSetPayload toPayload(DeviceCredentialSet credentialSet) {
		if (credentialSet == null) {
			return null;
		}
		DomainPayload domain = toPayload(credentialSet.mgmtDomain());
		return switch (credentialSet) {
			case DeviceSnmpv3Community c -> new CredentialSetPayload.SnmpV3(c.id(), c.name(), domain, c.deviceSpecific(),
				masked(c.community()), Secret.vaultInstanceId(c.community()), Secret.vaultPath(c.community()),
				clear(c.username()), Secret.vaultInstanceId(c.username()), Secret.vaultPath(c.username()),
				c.authType(), masked(c.authKey()), Secret.vaultInstanceId(c.authKey()), Secret.vaultPath(c.authKey()),
				c.privType(), masked(c.privKey()), Secret.vaultInstanceId(c.privKey()), Secret.vaultPath(c.privKey()));
			case DeviceSnmpv2cCommunity c -> new CredentialSetPayload.SnmpV2c(c.id(), c.name(), domain, c.deviceSpecific(),
				masked(c.community()), Secret.vaultInstanceId(c.community()), Secret.vaultPath(c.community()));
			case DeviceSnmpv1Community c -> new CredentialSetPayload.SnmpV1(c.id(), c.name(), domain, c.deviceSpecific(),
				masked(c.community()), Secret.vaultInstanceId(c.community()), Secret.vaultPath(c.community()));
			case DeviceSshKeyAccount c -> new CredentialSetPayload.SshKey(c.id(), c.name(), domain, c.deviceSpecific(),
				masked(c.privateKey()), Secret.vaultInstanceId(c.privateKey()), Secret.vaultPath(c.privateKey()));
			case DeviceSshAccount c -> new CredentialSetPayload.Ssh(c.id(), c.name(), domain, c.deviceSpecific());
			case DeviceTelnetAccount c -> new CredentialSetPayload.Telnet(c.id(), c.name(), domain, c.deviceSpecific());
			case DeviceHttpAccount c -> new CredentialSetPayload.Http(c.id(), c.name(), domain, c.deviceSpecific());
		};
	}
}
