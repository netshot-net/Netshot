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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.exception.DataAccessException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.domain.DomainRepository;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceCreation;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.device.DeviceValidation;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * The use case which adds a device: it creates the tasks which discover or snapshot the new
 * device, so it belongs to the task feature (the other device use cases are in
 * {@link net.netshot.netshot.inventory.device.DeviceService}).
 *
 * <p>Like the other services, it opens its own transactions, reports the failures as a
 * {@link NetshotException}, and runs the side effects (refresh of the dynamic groups, scheduling
 * of the task) after the transaction has been committed.</p>
 */
@Slf4j
public class DeviceCreationService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/**
	 * The values loaded to prepare the creation of a device.
	 *
	 * @param domain the management domain
	 * @param snmpCommunities the SNMP communities usable to poll the device
	 */
	private record CreationContext(Domain domain, List<DeviceCredentialSet> snmpCommunities) {
	}

	/**
	 * The result of the creation of a device.
	 *
	 * @param device the new device
	 * @param snapshotTask the initial snapshot task of the device
	 */
	private record CreatedDevice(Device device, Task snapshotTask) {
	}

	private final Transactions transactions;
	private final DeviceRepository repository;
	private final TaskSubmitter taskSubmitter;
	private final DriverRegistry driverRegistry;
	private final DomainRepository domainRepository = new DomainRepository();
	private final CredentialSetRepository credentialSetRepository = new CredentialSetRepository();

	/** The refresher of the dynamic groups. */
	private final DynamicGroups dynamicGroups;

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the device repository
	 * @param taskSubmitter how to hand a new task over to the task engine
	 * @param driverRegistry the loaded device drivers
	 * @param dynamicGroups the refresher of the dynamic groups
	 */
	public DeviceCreationService(Transactions transactions, DeviceRepository repository, TaskSubmitter taskSubmitter,
		DriverRegistry driverRegistry, DynamicGroups dynamicGroups) {
		this.transactions = transactions;
		this.repository = repository;
		this.taskSubmitter = taskSubmitter;
		this.driverRegistry = driverRegistry;
		this.dynamicGroups = dynamicGroups;
	}

	/**
	 * Adds a device.
	 *
	 * <p>With auto discovery, a "discover device type" task is created, and the device will be
	 * created if the discovery succeeds. Otherwise the device is immediately created and an initial
	 * "take snapshot" task is created.</p>
	 *
	 * @param creation the device to add
	 * @return the created task
	 * @throws NetshotException if the command is invalid, the device already exists,
	 *         or the device or the task can't be stored or scheduled
	 */
	public Task create(DeviceCreation creation) {
		String deviceAddress = creation.ipAddress();
		DeviceValidation.validateMgmtAddress(deviceAddress, false);
		DeviceValidation.validateTags(creation.tags());
		CreationContext context = this.loadCreationContext(creation);
		if (creation.autoDiscover()) {
			return this.createDiscoveryTask(creation, context);
		}
		return this.createDeviceAndSnapshotTask(creation, context.domain());
	}

	private CreationContext loadCreationContext(DeviceCreation creation) {
		try {
			// Read-write transaction on purpose (the duplicate check must see the latest data)
			return this.transactions.write(dsl -> {
				log.debug("Looking for an active device with this IP address.");
				DeviceSummary duplicate = this.repository.findActiveByMgmtAddress(dsl, creation.ipAddress(), null);
				if (duplicate != null) {
					log.error("Device {} is already present with this IP address.", duplicate.id());
					throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_DEVICE, String.format(
						"The device '%s' (%d) already exists with this IP address.",
						duplicate.name(), duplicate.id()));
				}
				Domain domain = this.domainRepository.find(dsl, creation.domainId());
				if (domain == null) {
					log.error("No such domain of ID {}.", creation.domainId());
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "No such domain");
				}
				List<DeviceCredentialSet> snmpCommunities = this.credentialSetRepository.findSnmpCommunities(dsl, domain.id());
				if (snmpCommunities.isEmpty() && creation.autoDiscover()) {
					log.error("No available SNMP community");
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS,
						"There is no known SNMP community in the database to poll the device.");
				}
				return new CreationContext(domain, snmpCommunities);
			});
		}
		catch (DataAccessException e) {
			log.error("Error while loading domain or communities.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Database error");
		}
	}

	private Task createDiscoveryTask(DeviceCreation creation, CreationContext context) {
		try {
			Set<Long> credentialSetIds = new HashSet<>();
			for (DeviceCredentialSet credentialSet : context.snmpCommunities()) {
				credentialSetIds.add(credentialSet.id());
			}
			DiscoverDeviceTypeTask spec = DiscoverDeviceTypeTask.builder()
				.deviceAddress(creation.ipAddress())
				.domainId(context.domain().id())
				.credentialSetIds(credentialSetIds)
				.deviceComments(creation.comments())
				.deviceTags(creation.tags())
				.build();
			Task task = this.taskSubmitter.submit(Task.of(spec, String.format("Autodiscover device %s", creation.ipAddress()),
				creation.ipAddress(), creation.author()));
			AAA_LOG.info("{} has been added.", task);
			return task;
		}
		catch (SchedulerException e) {
			log.error("Unable to schedule the discovery task.", e);
			throw new NetshotException(ErrorCode.NETSHOT_SCHEDULE_ERROR, "Unable to schedule the task");
		}
		catch (DataAccessException e) {
			log.error("Error while adding the discovery task.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Database error");
		}
	}

	private Task createDeviceAndSnapshotTask(DeviceCreation creation, Domain domain) {
		DeviceDriver driver = this.driverRegistry.getDriverByName(creation.deviceType());
		if (driver == null) {
			log.warn("Invalid posted device driver.");
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DEVICE_DRIVER, "Invalid device type.");
		}
		CreatedDevice created;
		try {
			created = this.transactions.write(dsl -> {
				List<DeviceAccess> accesses = DeviceValidation.validateAccesses(this.credentialSetRepository, dsl, driver, creation.accesses());
				Map<String, Object> options = DeviceValidation.validateOptions(driver, creation.options());
				Device.DeviceBuilder newDevice = Device.create(driver.getName(), creation.ipAddress(), domain, creation.author()).toBuilder();
				if (accesses != null) {
					newDevice.accesses(Device.mergeAccesses(List.of(), this.repository.saveSpecificCredentialSets(dsl, List.of(), accesses)));
				}
				if (options != null) {
					newDevice.options(options);
				}
				if (creation.comments() != null) {
					newDevice.comments(creation.comments());
				}
				newDevice.tags(Device.normalizeTags(creation.tags()));
				Device added = this.repository.add(dsl, newDevice.build());
				Task task = new TaskRepository().add(dsl, TakeSnapshotTask.of(added, "Initial snapshot after device creation",
					creation.author(), true, false, false));
				return new CreatedDevice(added, task);
			});
		}
		catch (NetshotException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Error while creating the device", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Database error");
		}
		AAA_LOG.info("{} has been created.", created.device());
		this.dynamicGroups.refreshAllGroupsOfOneDevice(created.device().id());
		try {
			return this.taskSubmitter.submit(created.snapshotTask());
		}
		catch (DataAccessException e) {
			log.error("Unable to add the task.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to add the task to the database.");
		}
		catch (SchedulerException e) {
			log.error("Unable to schedule the task.", e);
			throw new NetshotException(ErrorCode.NETSHOT_SCHEDULE_ERROR, "Unable to schedule the task.");
		}
	}
}
