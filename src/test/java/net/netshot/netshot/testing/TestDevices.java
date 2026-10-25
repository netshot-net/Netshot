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
package net.netshot.netshot.testing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticResultRepository;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.domain.DomainRepository;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.inventory.search.Finder.FinderParseException;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.testing.fake.FakeDeviceFactory;

/**
 * Stores and reads devices, domains, groups and configurations for the tests, each call in its own
 * transaction, through the repositories.
 */
public final class TestDevices {

	private static final Transactions TRANSACTIONS = new Transactions();

	private static final DeviceRepository DEVICES = new DeviceRepository();

	private static final ConfigRepository CONFIGS = new ConfigRepository();

	private static final GroupRepository GROUPS = new GroupRepository();

	private TestDevices() {
	}

	/**
	 * Stores a domain.
	 *
	 * @param domain the domain (its ID is ignored)
	 * @return the stored domain
	 */
	public static Domain addDomain(Domain domain) {
		return TRANSACTIONS.write(dsl -> new DomainRepository().add(dsl, domain));
	}

	/**
	 * Stores a domain.
	 *
	 * @param name the name
	 * @param description the description
	 * @return the stored domain
	 */
	public static Domain addDomain(String name, String description) {
		return addDomain(Domain.builder().name(name).description(description).build());
	}

	/**
	 * Stores a credential set.
	 *
	 * @param credentialSet the credential set (its ID is ignored)
	 * @return the stored credential set
	 */
	public static DeviceCredentialSet addCredentialSet(DeviceCredentialSet credentialSet) {
		return TRANSACTIONS.write(dsl -> new CredentialSetRepository().add(dsl, credentialSet));
	}

	/**
	 * Reads a credential set.
	 *
	 * @param id the credential set ID
	 * @return the credential set, or null if there is none
	 */
	public static DeviceCredentialSet findCredentialSet(long id) {
		return TRANSACTIONS.read(dsl -> new CredentialSetRepository().find(dsl, id));
	}

	/**
	 * Counts the devices.
	 *
	 * @return the number of devices
	 */
	public static int count() {
		return TRANSACTIONS.read(dsl -> dsl.fetchCount(net.netshot.netshot.platform.database.schema.Tables.DEVICE));
	}

	/**
	 * Stores a device, with the specific credential sets of its accesses (which are stored first).
	 *
	 * @param device the device (its ID is ignored)
	 * @return the stored device
	 */
	public static Device add(Device device) {
		return TRANSACTIONS.write(dsl -> {
			CredentialSetRepository credentialSets = new CredentialSetRepository();
			List<DeviceAccess> accesses = new ArrayList<>();
			for (DeviceAccess access : device.accesses()) {
				DeviceCredentialSet specific = access.specificCredentialSet();
				if (specific != null && specific.id() == 0) {
					access = access.toBuilder().specificCredentialSet(credentialSets.add(dsl, specific)).build();
				}
				accesses.add(access);
			}
			return DEVICES.add(dsl, device.toBuilder().accesses(accesses).build());
		});
	}

	/**
	 * Stores a device, with a configuration (which becomes its last one) and diagnostic results.
	 *
	 * @param device the device (its ID is ignored)
	 * @param config the configuration (its device ID is ignored), or null
	 * @param results the diagnostic results
	 * @return the stored device
	 */
	public static Device add(Device device, Config config, DiagnosticResult... results) {
		Device added = add(device);
		if (config != null) {
			addConfig(added, config);
		}
		if (results.length > 0) {
			TRANSACTIONS.run(dsl -> new DiagnosticResultRepository().save(dsl, added.id(), List.of(results)));
		}
		return find(added.id());
	}

	/**
	 * Stores a fake Cisco IOS device (see {@link FakeDeviceFactory}), with its configuration.
	 *
	 * @param domain the domain of the device (stored)
	 * @param shift a number making the device unique
	 * @return the stored device
	 */
	public static Device addFakeCiscoIos(Domain domain, int shift) {
		Device device = FakeDeviceFactory.getFakeCiscoIosDevice(domain, null, shift);
		return add(device, FakeDeviceFactory.getFakeCiscoIosConfig(device));
	}

	/**
	 * Stores a configuration of a device, which becomes its last one.
	 *
	 * @param device the device
	 * @param config the configuration (its device ID is ignored)
	 * @return the ID of the configuration
	 */
	public static long addConfig(Device device, Config config) {
		return TRANSACTIONS.write(dsl -> {
			long configId = CONFIGS.add(dsl, config.toBuilder().deviceId(device.id()).build());
			Device stored = DEVICES.find(dsl, device.id());
			DEVICES.save(dsl, stored, stored.toBuilder().lastConfigId(configId).build());
			return configId;
		});
	}

	/**
	 * Stores a configuration of a device, without making it the last one.
	 *
	 * @param deviceId the device ID
	 * @param config the configuration (its device ID is ignored)
	 * @return the ID of the configuration
	 */
	public static long addOldConfig(long deviceId, Config config) {
		return TRANSACTIONS.write(dsl -> CONFIGS.add(dsl, config.toBuilder().deviceId(deviceId).build()));
	}

	/**
	 * Saves the changes of a device.
	 *
	 * @param device the edited device (as loaded, with its changes)
	 * @return the saved device
	 */
	public static Device save(Device device) {
		return TRANSACTIONS.write(dsl -> {
			DEVICES.save(dsl, DEVICES.find(dsl, device.id()), device);
			return DEVICES.find(dsl, device.id());
		});
	}

	/**
	 * Changes the change date of a device, bypassing the repository (which sets it to now).
	 *
	 * @param deviceId the device ID
	 * @param changeDate the change date
	 */
	public static void setChangeDate(long deviceId, Instant changeDate) {
		TRANSACTIONS.run(dsl -> dsl.update(net.netshot.netshot.platform.database.schema.Tables.DEVICE)
			.set(net.netshot.netshot.platform.database.schema.Tables.DEVICE.CHANGE_DATE, changeDate)
			.where(net.netshot.netshot.platform.database.schema.Tables.DEVICE.ID.eq(deviceId))
			.execute());
	}

	/**
	 * Removes a device, with everything attached to it.
	 *
	 * @param id the device ID
	 */
	public static void delete(long id) {
		TRANSACTIONS.run(dsl -> DEVICES.remove(dsl, id));
	}

	/**
	 * Removes all the devices, configurations, groups, credential sets and domains (and, by cascade,
	 * what references them, the tasks included).
	 */
	public static void deleteAll() {
		TRANSACTIONS.run(dsl -> dsl.execute("truncate table device, config, config_attribute, long_text_configuration, "
			+ "device_group, device_credential_set, domain cascade"));
	}

	/**
	 * Reads a device.
	 *
	 * @param id the device ID
	 * @return the device, or null if there is no such device
	 */
	public static Device find(long id) {
		return TRANSACTIONS.read(dsl -> DEVICES.find(dsl, id));
	}

	/**
	 * Reads a configuration, with its long texts.
	 *
	 * @param id the configuration ID
	 * @return the configuration, or null if there is no such configuration
	 */
	public static Config findConfig(long id) {
		return TRANSACTIONS.read(dsl -> CONFIGS.find(dsl, id, true));
	}

	/**
	 * Reads the diagnostic results of a device.
	 *
	 * @param deviceId the device ID
	 * @return the results
	 */
	public static List<DiagnosticResult> diagnosticResults(long deviceId) {
		return TRANSACTIONS.read(dsl -> new DiagnosticResultRepository().findByDevice(dsl, deviceId));
	}

	/**
	 * Stores a static group of devices.
	 *
	 * @param name the name of the group
	 * @param devices the devices of the group, in order
	 * @return the stored group
	 */
	public static StaticDeviceGroup addStaticGroup(String name, Device... devices) {
		return TRANSACTIONS.write(dsl -> {
			StaticDeviceGroup group = (StaticDeviceGroup) GROUPS.add(dsl, StaticDeviceGroup.builder().name(name).folder("").build());
			List<Long> ids = new ArrayList<>();
			for (Device device : devices) {
				ids.add(device.id());
			}
			GROUPS.setMembers(dsl, group.id(), ids);
			return group;
		});
	}

	/**
	 * Stores a dynamic group of devices, and computes its members.
	 *
	 * @param name the name of the group
	 * @param query the query of the group
	 * @return the stored group
	 */
	public static DynamicDeviceGroup addDynamicGroup(String name, String query) {
		return TRANSACTIONS.write(dsl -> {
			DynamicDeviceGroup group = (DynamicDeviceGroup) GROUPS.add(dsl,
				DynamicDeviceGroup.builder().name(name).folder("").query(query).build());
			try {
				new DynamicGroups(TestDrivers.registry()).refresh(dsl, group);
			}
			catch (FinderParseException e) {
				throw new IllegalArgumentException("Invalid query", e);
			}
			return group;
		});
	}

	/**
	 * Adds devices to a group (at the end).
	 *
	 * @param group the group
	 * @param devices the devices
	 */
	public static void addMembers(DeviceGroup group, Device... devices) {
		TRANSACTIONS.run(dsl -> {
			for (Device device : devices) {
				GROUPS.addMember(dsl, group.id(), device.id());
			}
		});
	}

	/**
	 * Lists the IDs of the members of a group, in order.
	 *
	 * @param groupId the group ID
	 * @return the device IDs
	 */
	public static List<Long> memberIds(long groupId) {
		return TRANSACTIONS.read(dsl -> GROUPS.findMemberIds(dsl, groupId));
	}
}
