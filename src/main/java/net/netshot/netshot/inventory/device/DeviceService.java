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
package net.netshot.netshot.inventory.device;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.CredentialSets;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device.Status;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.domain.DomainRepository;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.TakeSnapshotTask;

/**
 * The device use cases: list, read, add, edit and delete devices.
 *
 * <p>Each public method is one use case. It opens its own transactions (through
 * {@link Transactions}), reports the failures the API clients know as a
 * {@link NetshotException} carrying the {@link ErrorCode}, and runs the side effects of a
 * use case (refresh of the dynamic groups, scheduling of the tasks, removal of files) explicitly,
 * after the transaction has been committed. It knows nothing of HTTP.</p>
 *
 * <p>The devices it returns are records loaded with everything their representation reads
 * (see {@link DeviceRepository}).</p>
 */
@Slf4j
public class DeviceService {

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

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the device repository
	 * @param taskSubmitter how to hand a new task over to the task engine
	 * @param driverRegistry the loaded device drivers
	 */
	public DeviceService(Transactions transactions, DeviceRepository repository, TaskSubmitter taskSubmitter,
		DriverRegistry driverRegistry) {
		this.transactions = transactions;
		this.repository = repository;
		this.taskSubmitter = taskSubmitter;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Lists the devices in their summary form.
	 *
	 * @param groupId the ID of a group whose members are listed (in the order of the group), or null for all the devices
	 * @param page the window on the result
	 * @return the device summaries
	 * @throws NetshotException if the devices can't be read
	 */
	public List<DeviceSummary> list(Long groupId, Page page) {
		try {
			return this.transactions.readDsl(dsl -> this.repository.findSummaries(dsl, groupId, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the devices", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the devices");
		}
	}

	/**
	 * Gets a device with all its details (see {@link DeviceRepository} for what is loaded).
	 *
	 * @param id the device ID
	 * @return the device
	 * @throws NetshotException if the device doesn't exist, or can't be read
	 */
	public Device get(long id) {
		try {
			return this.transactions.readDsl(dsl -> {
				Device device = this.repository.find(dsl, id);
				if (device == null) {
					throw new NetshotException(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, "Can't find this device");
				}
				return device;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the device", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the device");
		}
	}

	/**
	 * Lists the network interfaces of a device, with their IP addresses.
	 *
	 * @param deviceId the device ID
	 * @param page the window on the result
	 * @return the interfaces (none if there is no such device)
	 * @throws NetshotException if the interfaces can't be read
	 */
	public List<NetworkInterface> getInterfaces(long deviceId, Page page) {
		try {
			return this.transactions.readDsl(dsl -> this.repository.findInterfaces(dsl, deviceId, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the interfaces.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the interfaces");
		}
	}

	/**
	 * Lists the hardware modules of a device.
	 *
	 * @param deviceId the device ID
	 * @param includeHistory whether to include the modules which have been removed
	 * @param page the window on the result
	 * @return the modules (none if there is no such device)
	 * @throws NetshotException if the modules can't be read
	 */
	public List<Module> getModules(long deviceId, boolean includeHistory, Page page) {
		try {
			return this.transactions.readDsl(dsl -> this.repository.findModules(dsl, deviceId, includeHistory, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the modules.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the modules");
		}
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
			return this.transactions.writeDsl(dsl -> {
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
			created = this.transactions.writeDsl(dsl -> {
				List<DeviceAccess> accesses = DeviceValidation.validateAccesses(this.credentialSetRepository, dsl, driver, creation.accesses());
				Map<String, Object> options = DeviceValidation.validateOptions(driver, creation.options());
				Device.DeviceBuilder newDevice = Device.create(driver.getName(), creation.ipAddress(), domain, creation.author()).toBuilder();
				if (accesses != null) {
					newDevice.accesses(Device.mergeAccesses(List.of(), this.resolveSpecificCredentials(dsl, List.of(), accesses)));
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
		DynamicGroups.refreshAllGroupsOfOneDevice(created.device().id());
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

	/**
	 * Edits a device. Only the attributes of the command which are not null are changed.
	 *
	 * @param id the device ID
	 * @param update the changes to apply
	 * @return the device as it is once edited, as {@link #get(long)} returns it
	 * @throws NetshotException if the device doesn't exist, a change is invalid, the management
	 *         address is already used by an active device, or the device can't be stored
	 */
	public Device update(long id, DeviceUpdate update) {
		Device device;
		try {
			device = this.transactions.writeDsl(dsl -> {
				Device existing = this.repository.find(dsl, id);
				if (existing == null) {
					log.error("The device of ID {} doesn't exist.", id);
					throw new NetshotException(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, "The device doesn't exist.");
				}
				Device edited = this.applyUpdate(dsl, existing, update);
				if (!Device.Status.DISABLED.equals(edited.status())) {
					log.debug("Looking for an active device with this IP address.");
					DeviceSummary duplicate = this.repository.findActiveByMgmtAddress(dsl, edited.mgmtAddress(), edited.id());
					if (duplicate != null) {
						log.error("Device {} is already present with this IP address.", duplicate.id());
						throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_DEVICE, String.format(
							"The device '%s' (%d) already exists with this IP address.",
							duplicate.name(), duplicate.id()));
					}
				}
				this.repository.save(dsl, existing, edited);
				return edited;
			});
			AAA_LOG.info("{} has been edited.", device);
		}
		catch (DataAccessException e) {
			log.error("Cannot edit the device.", e);
			if (DatabaseErrors.isUniqueViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DUPLICATE_DEVICE, "A device with this IP address already exists.");
			}
			if (DatabaseErrors.isForeignKeyViolation(e) && e.getMessage() != null && e.getMessage().contains("domain")) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "Unable to find the domain");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to save the device.");
		}
		catch (NetshotException e) {
			log.error("Cannot edit the device.", e);
			throw e;
		}
		DynamicGroups.refreshAllGroupsOfOneDevice(device.id());
		return this.get(id);
	}

	private Device applyUpdate(DSLContext dsl, Device device, DeviceUpdate update) {
		Device.DeviceBuilder edited = device.toBuilder();
		if (update.enabled() != null) {
			edited.status(update.enabled() ? Status.INPRODUCTION : Status.DISABLED);
		}
		if (update.ipAddress() != null) {
			DeviceValidation.validateMgmtAddress(update.ipAddress(), false);
			edited.mgmtAddress(update.ipAddress());
			edited.cachedIpAddress(Device.resolveIpAddress(update.ipAddress()));
		}
		if (update.accesses() != null) {
			DeviceDriver driver = this.driverRegistry.getDriverByName(device.driver());
			List<DeviceAccess> accesses = DeviceValidation.validateAccesses(this.credentialSetRepository, dsl, driver, update.accesses());
			edited.accesses(Device.mergeAccesses(device.accesses(), this.resolveSpecificCredentials(dsl, device.accesses(), accesses)));
		}
		if (update.options() != null) {
			DeviceDriver driver = this.driverRegistry.getDriverByName(device.driver());
			edited.options(DeviceValidation.validateOptions(driver, update.options()));
		}
		if (update.comments() != null) {
			edited.comments(update.comments());
		}
		if (update.tags() != null) {
			DeviceValidation.validateTags(update.tags());
			edited.tags(Device.normalizeTags(update.tags()));
		}
		if (update.mgmtDomainId() != null) {
			Domain domain = this.domainRepository.find(dsl, update.mgmtDomainId());
			if (domain == null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "Invalid domain");
			}
			edited.mgmtDomain(domain);
		}
		return edited.build();
	}

	/**
	 * Deletes a device, with everything attached to it.
	 *
	 * @param id the device ID
	 * @return true if the device has been deleted, false if there is no such device
	 * @throws NetshotException if other objects use the device, or it can't be deleted
	 */
	public boolean delete(long id) {
		List<Path> filesToDelete;
		try {
			filesToDelete = this.transactions.writeDsl(dsl -> {
				List<Path> paths = this.repository.findConfigBinaryFilePaths(dsl, id);
				if (!this.repository.remove(dsl, id)) {
					log.info("No such device of ID {}", id);
					return null;
				}
				return paths;
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to delete the device {}.", id, e);
			if (DatabaseErrors.isForeignKeyViolation(e)) {
				throw new NetshotException(ErrorCode.NETSHOT_DEVICE_IN_USE,
					"Unable to delete the device, there must be other objects using it.");
			}
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to delete the device");
		}
		if (filesToDelete == null) {
			return false;
		}
		AAA_LOG.info("Device of ID {} has been deleted.", id);
		for (Path path : filesToDelete) {
			try {
				Files.delete(path);
			}
			catch (Exception e) {
				log.error("Error while removing binary file {}", path, e);
			}
		}
		return true;
	}

	/**
	 * Stores the specific credential set of each submitted access (created, or merged into the
	 * current one of the access, see {@link CredentialSets#applySpecificCredentialSet}).
	 *
	 * @param dsl the jOOQ context
	 * @param current the current accesses of the device (none for a new device)
	 * @param accesses the submitted accesses (already validated)
	 * @return the accesses, with their stored specific credential sets
	 */
	private List<DeviceAccess> resolveSpecificCredentials(DSLContext dsl, List<DeviceAccess> current, List<DeviceAccess> accesses) {
		List<DeviceAccess> resolved = new ArrayList<>(accesses.size());
		for (DeviceAccess input : accesses) {
			if (input.specificCredentialSet() == null) {
				resolved.add(input);
				continue;
			}
			DeviceCredentialSet existingSpecific = null;
			for (DeviceAccess access : current) {
				if (access.accessName().equals(input.accessName())) {
					existingSpecific = access.specificCredentialSet();
				}
			}
			DeviceCredentialSet stored = CredentialSets.applySpecificCredentialSet(dsl, this.credentialSetRepository,
				existingSpecific, input.specificCredentialSet());
			resolved.add(input.toBuilder().specificCredentialSet(stored).build());
		}
		return resolved;
	}
}
