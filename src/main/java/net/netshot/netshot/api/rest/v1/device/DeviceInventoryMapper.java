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
package net.netshot.netshot.api.rest.v1.device;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.OptionDefinition;
import net.netshot.netshot.inventory.device.DeviceFamilyCount;
import net.netshot.netshot.inventory.device.DeviceTagCount;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.PartNumberCount;
import net.netshot.netshot.inventory.device.SoftwareVersionCount;
import net.netshot.netshot.platform.net.NetworkAddress;

/**
 * Maps the sub-resources of the devices (interfaces, modules) and the reference lists built from the devices
 * (families, tags, part numbers, software versions, device types) to their v1 representation.
 */
public final class DeviceInventoryMapper {

	/** The path of the device families. */
	public static final String FAMILIES_PATH = "device-families";

	/** The path of the device tags. */
	public static final String TAGS_PATH = "device-tags";

	/** The path of the device types. */
	public static final String TYPES_PATH = "device-types";

	/** The path of the part numbers. */
	public static final String PART_NUMBERS_PATH = "part-numbers";

	/** The path of the software versions. */
	public static final String SOFTWARE_VERSIONS_PATH = "software-versions";

	private DeviceInventoryMapper() {
	}

	/**
	 * Maps a network interface.
	 *
	 * @param networkInterface the interface
	 * @return the representation
	 */
	public static DeviceInterfaceDto toDto(NetworkInterface networkInterface) {
		List<DeviceInterfaceDto.IpAddress> addresses = new ArrayList<>();
		networkInterface.ip4Addresses().forEach(a -> addresses.add(toDto(a, 4)));
		networkInterface.ip6Addresses().forEach(a -> addresses.add(toDto(a, 6)));
		return new DeviceInterfaceDto(networkInterface.id(), networkInterface.interfaceName(), networkInterface.interfaceName(),
			networkInterface.description(), networkInterface.enabled(), networkInterface.level3(), networkInterface.vrfInstance(),
			networkInterface.virtualDevice(), networkInterface.getMacAddress(), addresses);
	}

	private static DeviceInterfaceDto.IpAddress toDto(NetworkAddress address, int version) {
		return new DeviceInterfaceDto.IpAddress(address.getIp(), address.getPrefixLength(), version, address.getAddressUsage());
	}

	/**
	 * Maps a module of a device.
	 *
	 * @param module the module
	 * @return the representation
	 */
	public static DeviceModuleDto toDto(Module module) {
		String display = module.partNumber().isEmpty() ? module.slot() : "%s (%s)".formatted(module.slot(), module.partNumber());
		return new DeviceModuleDto(module.id(), display, module.slot(), module.partNumber(), module.serialNumber(),
			module.firstSeenDate(), module.lastSeenDate(), module.removed());
	}

	/**
	 * Maps a device family.
	 *
	 * @param family the family and its number of devices
	 * @return the representation
	 */
	public static DeviceFamilyDto toDto(DeviceFamilyCount family) {
		return new DeviceFamilyDto(family.driver(), family.family(), family.deviceCount());
	}

	/**
	 * Maps a device tag.
	 *
	 * @param tag the tag and its number of devices
	 * @return the representation
	 */
	public static DeviceTagDto toDto(DeviceTagCount tag) {
		return new DeviceTagDto(tag.name(), tag.deviceCount());
	}

	/**
	 * Maps a part number.
	 *
	 * @param partNumber the part number and its number of modules
	 * @return the representation
	 */
	public static PartNumberDto toDto(PartNumberCount partNumber) {
		return new PartNumberDto(partNumber.partNumber(), partNumber.moduleCount());
	}

	/**
	 * Maps a software version.
	 *
	 * @param version the software version and its number of devices
	 * @return the representation
	 */
	public static SoftwareVersionDto toDto(SoftwareVersionCount version) {
		return new SoftwareVersionDto(version.version(), version.deviceCount());
	}

	/**
	 * Maps a device driver.
	 *
	 * @param driver the driver
	 * @return the representation
	 */
	public static DeviceTypeDto toDto(DeviceDriver driver) {
		List<DeviceTypeDto.Attribute> attributes = driver.getAttributes().stream()
			.sorted(Comparator.comparing(AttributeDefinition::getLevel).thenComparing(AttributeDefinition::getName))
			.map(a -> new DeviceTypeDto.Attribute(a.getName(), a.getTitle(), a.getType(), a.getLevel(), a.isComparable(),
				a.isSearchable(), a.isCheckable()))
			.toList();
		List<DeviceTypeDto.Option> options = driver.getOptions().entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(Map.Entry::getValue)
			.map(DeviceInventoryMapper::toDto)
			.toList();
		List<DeviceTypeDto.Access> accesses = driver.getAccessDefinitions().entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(Map.Entry::getValue)
			.map(a -> new DeviceTypeDto.Access(a.getName(), a.getProtocol(), a.getDescription(), a.getDefaultPort(), a.getGroup(),
				a.getPriority()))
			.toList();
		DeviceDriver.Location location = driver.getLocation();
		return new DeviceTypeDto(driver.getName(), driver.getDescription(), driver.getVersion(), driver.getAuthor(),
			driver.getPriority(), driver.getProtocols().stream().sorted().toList(), driver.getCliMainModes().stream().sorted().toList(),
			driver.getSourceHash(), location == null ? null : new DeviceTypeDto.Location(location.getType(), location.getFileName()),
			attributes, options, accesses);
	}

	private static DeviceTypeDto.Option toDto(OptionDefinition option) {
		return new DeviceTypeDto.Option(option.getName(), option.getTitle(), option.getType(), option.getChoices(),
			option.getDefaultValue());
	}
}
