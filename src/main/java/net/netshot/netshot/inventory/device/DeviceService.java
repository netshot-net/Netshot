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
package net.netshot.netshot.inventory.device;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.domain.DomainRepository;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device.Status;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.platform.database.DatabaseErrors;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The device use cases: list, read, edit and delete devices (adding one is a task use case,
 * see {@code task.DeviceCreationService}).
 *
 * <p>Each public method is one use case. It opens its own transactions (through
 * {@link Transactions}), reports the failures the API clients know as a
 * {@link NetshotException} carrying the {@link ErrorCode}, and runs the side effects of a
 * use case (refresh of the dynamic groups, removal of files) explicitly,
 * after the transaction has been committed. It knows nothing of HTTP.</p>
 *
 * <p>The devices it returns are records loaded with everything their representation reads
 * (see {@link DeviceRepository}).</p>
 */
@Slf4j
public class DeviceService {

	/** Authentication, Authorization, Accounting logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	private final Transactions transactions;
	private final DeviceRepository repository;
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
	 * @param driverRegistry the loaded device drivers
	 * @param dynamicGroups the refresher of the dynamic groups
	 */
	public DeviceService(Transactions transactions, DeviceRepository repository,
		DriverRegistry driverRegistry, DynamicGroups dynamicGroups) {
		this.dynamicGroups = dynamicGroups;
		this.transactions = transactions;
		this.repository = repository;
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
			return this.transactions.read(dsl -> this.repository.findSummaries(dsl, groupId, page));
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
			return this.transactions.read(dsl -> {
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
			return this.transactions.read(dsl -> this.repository.findInterfaces(dsl, deviceId, page));
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
			return this.transactions.read(dsl -> this.repository.findModules(dsl, deviceId, includeHistory, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the modules.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the modules");
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
			device = this.transactions.write(dsl -> {
				if (this.repository.lock(dsl, id) == null) {
					log.error("The device of ID {} doesn't exist.", id);
					throw new NetshotException(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, "The device doesn't exist.");
				}
				Device existing = this.repository.find(dsl, id);
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
		this.dynamicGroups.refreshAllGroupsOfOneDevice(device.id());
		return this.get(id);
	}

	/**
	 * Replaces the editable properties of a device, if it is at a given version.
	 *
	 * @param id the device ID
	 * @param change the new properties
	 * @param expectedVersion the version the device must be at, or null for any
	 * @return the device as it is once edited, as {@link #get(long)} returns it
	 * @throws NetshotException if the device doesn't exist or isn't at the expected version, a property is invalid,
	 *         the management address is already used by an active device, or the device can't be stored
	 */
	public Device update(long id, DeviceChange change, Long expectedVersion) {
		return this.update(id, _ -> change, expectedVersion);
	}

	/**
	 * Replaces the editable properties of a device, if it is at a given version: the new properties are computed
	 * from the device as it is, which can't change meanwhile (to apply a patch).
	 *
	 * @param id the device ID
	 * @param change gives the new properties from the current device
	 * @param expectedVersion the version the device must be at, or null for any
	 * @return the device as it is once edited, as {@link #get(long)} returns it
	 * @throws NetshotException if the device doesn't exist or isn't at the expected version, a property is invalid,
	 *         the management address is already used by an active device, or the device can't be stored
	 */
	public Device update(long id, Function<Device, DeviceChange> change, Long expectedVersion) {
		Device device;
		try {
			device = this.transactions.write(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				if (version == null) {
					throw new NetshotException(ErrorCode.NETSHOT_DEVICE_NOT_FOUND, "The device doesn't exist.");
				}
				Versions.check("device", version, expectedVersion);
				Device existing = this.repository.find(dsl, id);
				Device edited = this.applyChange(dsl, existing, change.apply(existing));
				if (!Device.Status.DISABLED.equals(edited.status())) {
					DeviceSummary duplicate = this.repository.findActiveByMgmtAddress(dsl, edited.mgmtAddress(), edited.id());
					if (duplicate != null) {
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
		this.dynamicGroups.refreshAllGroupsOfOneDevice(device.id());
		return this.get(id);
	}

	private Device applyChange(DSLContext dsl, Device device, DeviceChange change) {
		if (!change.mgmtAddress().equals(device.mgmtAddress())) {
			DeviceValidation.validateMgmtAddress(change.mgmtAddress(), false);
		}
		DeviceValidation.validateTags(change.tags() == null ? null : List.copyOf(change.tags()));
		Domain domain = this.domainRepository.find(dsl, change.mgmtDomainId());
		if (domain == null) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_DOMAIN, "Invalid domain");
		}
		DeviceDriver driver = this.driverRegistry.getDriverByName(device.driver());
		List<DeviceAccess> accesses = DeviceValidation.validateAccesses(this.credentialSetRepository, dsl, driver,
			change.accesses() == null ? List.of() : change.accesses(), change.specificCredentials());
		accesses = DeviceValidation.applySpecificCredentials(this.credentialSetRepository, dsl, driver, device.accesses(), accesses,
			change.specificCredentials());
		Map<String, Object> options = DeviceValidation.validateOptions(driver, change.options());
		Device.DeviceBuilder edited = device.toBuilder()
			.status(change.status() == null ? Status.INPRODUCTION : change.status())
			.comments(change.comments() == null ? "" : change.comments())
			.mgmtAddress(change.mgmtAddress())
			.mgmtDomain(domain)
			.accesses(Device.mergeAccesses(device.accesses(), accesses))
			.options(options == null ? Map.of() : options)
			.tags(Device.normalizeTags(change.tags()));
		if (!change.mgmtAddress().equals(device.mgmtAddress())) {
			edited.cachedIpAddress(Device.resolveIpAddress(change.mgmtAddress()));
		}
		return edited.build();
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
			List<DeviceAccess> accesses = DeviceValidation.validateAccesses(this.credentialSetRepository, dsl, driver, update.accesses(),
				update.specificCredentials());
			accesses = DeviceValidation.applySpecificCredentials(this.credentialSetRepository, dsl, driver, device.accesses(), accesses,
				update.specificCredentials());
			edited.accesses(Device.mergeAccesses(device.accesses(), accesses));
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
		return this.delete(id, null);
	}

	/**
	 * Deletes a device if it is at a given version, with everything attached to it.
	 *
	 * @param id the device ID
	 * @param expectedVersion the version the device must be at, or null for any
	 * @return true if the device has been deleted, false if there is no such device
	 * @throws NetshotException if the device isn't at the expected version, other objects use it, or it can't be deleted
	 */
	public boolean delete(long id, Long expectedVersion) {
		List<Path> filesToDelete;
		try {
			filesToDelete = this.transactions.write(dsl -> {
				Integer version = this.repository.lock(dsl, id);
				if (version == null) {
					log.info("No such device of ID {}", id);
					return null;
				}
				Versions.check("device", version, expectedVersion);
				List<Path> paths = this.repository.findConfigBinaryFilePaths(dsl, id);
				this.repository.remove(dsl, id);
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
}
