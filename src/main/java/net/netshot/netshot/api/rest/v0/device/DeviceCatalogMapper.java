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
package net.netshot.netshot.api.rest.v0.device;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.OptionDefinition;
import net.netshot.netshot.inventory.device.DeviceFamily;
import net.netshot.netshot.inventory.device.DeviceTagCount;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.NetworkAddress;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.search.SearchResult;

/**
 * Maps the sub-resources of the devices (interfaces, modules), the search result, the device
 * types and the reference lists to their REST representation. All the methods are pure functions.
 *
 * <p>The collections keep the iteration order of the objects they map, which is the order in which
 * the objects used to be serialized.</p>
 */
public final class DeviceCatalogMapper {

	private DeviceCatalogMapper() {
		// Static helpers only
	}

	/**
	 * Maps the modules to their representation.
	 *
	 * @param modules the modules
	 * @return the DTOs, in the same order
	 */
	public static List<ModuleDto> toModuleDtos(List<Module> modules) {
		List<ModuleDto> dtos = new ArrayList<>(modules.size());
		modules.forEach(module -> dtos.add(DeviceMapper.toDto(module)));
		return dtos;
	}

	/**
	 * Maps the network interfaces to their representation.
	 *
	 * @param interfaces the interfaces, with their IP addresses loaded
	 * @return the DTOs, in the same order
	 */
	public static List<NetworkInterfaceDto> toInterfaceDtos(List<NetworkInterface> interfaces) {
		List<NetworkInterfaceDto> dtos = new ArrayList<>(interfaces.size());
		interfaces.forEach(networkInterface -> dtos.add(toDto(networkInterface)));
		if (interfaces.getClass() != ArrayList.class) {
			// Quirk kept: a paginated query with a collection fetch is paginated in memory and answers a
			// sub list, which XML publishes as <SubList> instead of <ArrayList>
			return dtos.subList(0, dtos.size());
		}
		return dtos;
	}

	private static NetworkInterfaceDto toDto(NetworkInterface networkInterface) {
		return new NetworkInterfaceDto(
			networkInterface.getId(),
			networkInterface.getInterfaceName(),
			toAddressDtos(networkInterface.getIp4Addresses()),
			toAddressDtos(networkInterface.getIp6Addresses()),
			networkInterface.getVrfInstance(),
			networkInterface.getVirtualDevice(),
			networkInterface.getDescription(),
			networkInterface.isEnabled(),
			networkInterface.isLevel3(),
			networkInterface.getMacAddress());
	}

	private static List<NetworkInterfaceDto.Address> toAddressDtos(Iterable<? extends NetworkAddress> addresses) {
		List<NetworkInterfaceDto.Address> dtos = new ArrayList<>();
		addresses.forEach(address -> dtos.add(new NetworkInterfaceDto.Address(address.getIp(), address.getPrefixLength(), address.getAddressUsage())));
		return dtos;
	}

	/**
	 * Maps a search result to its representation.
	 *
	 * @param result the result
	 * @return the DTO
	 */
	public static SearchResultsDto toDto(SearchResult result) {
		return new SearchResultsDto(result.query(), DeviceMapper.toSummaryDtos(result.devices()));
	}

	/**
	 * Maps the device families to their representation.
	 *
	 * @param families the families
	 * @return the DTOs, in the same order
	 */
	public static List<DeviceFamilyDto> toFamilyDtos(List<DeviceFamily> families) {
		List<DeviceFamilyDto> dtos = new ArrayList<>(families.size());
		families.forEach(family -> dtos.add(new DeviceFamilyDto(family.driver(), family.family())));
		return dtos;
	}

	/**
	 * Maps the part numbers to their representation.
	 *
	 * @param partNumbers the part numbers
	 * @return the DTOs, in the same order
	 */
	public static List<PartNumberDto> toPartNumberDtos(List<String> partNumbers) {
		List<PartNumberDto> dtos = new ArrayList<>(partNumbers.size());
		partNumbers.forEach(partNumber -> dtos.add(new PartNumberDto(partNumber)));
		return dtos;
	}

	/**
	 * Maps the device tags to their representation.
	 *
	 * @param tags the tags with their number of devices
	 * @return the DTOs, in the same order
	 */
	public static List<DeviceTagDto> toTagDtos(List<DeviceTagCount> tags) {
		List<DeviceTagDto> dtos = new ArrayList<>(tags.size());
		tags.forEach(tag -> dtos.add(new DeviceTagDto(tag.name(), tag.deviceCount())));
		return dtos;
	}

	/**
	 * Maps the software versions to their representation.
	 *
	 * @param versions the software versions
	 * @return the DTOs, in the same order
	 */
	public static List<SoftwareVersionDto> toSoftwareVersionDtos(List<String> versions) {
		List<SoftwareVersionDto> dtos = new ArrayList<>(versions.size());
		versions.forEach(version -> dtos.add(new SoftwareVersionDto(version)));
		return dtos;
	}

	/**
	 * Maps the device drivers to their representation.
	 *
	 * @param drivers the drivers
	 * @return the DTOs, in the same order
	 */
	public static List<DeviceTypeDto> toDeviceTypeDtos(List<DeviceDriver> drivers) {
		List<DeviceTypeDto> dtos = new ArrayList<>(drivers.size());
		drivers.forEach(driver -> dtos.add(toDto(driver)));
		return dtos;
	}

	private static DeviceTypeDto toDto(DeviceDriver driver) {
		List<DeviceTypeDto.Attribute> attributes = new ArrayList<>();
		for (AttributeDefinition attribute : driver.getAttributes()) {
			attributes.add(new DeviceTypeDto.Attribute(attribute.getType(), attribute.getLevel(), attribute.getName(),
				attribute.getTitle(), attribute.isComparable(), attribute.isSearchable(), attribute.isCheckable()));
		}
		Map<String, DeviceTypeDto.Option> options = new LinkedHashMap<>();
		for (Map.Entry<String, OptionDefinition> entry : driver.getOptions().entrySet()) {
			OptionDefinition option = entry.getValue();
			options.put(entry.getKey(), new DeviceTypeDto.Option(option.getName(), option.getTitle(), option.getType(),
				option.getChoices(), option.getDefaultValue()));
		}
		Map<String, DeviceTypeDto.Access> accesses = new LinkedHashMap<>();
		for (Map.Entry<String, DeviceDriver.AccessDefinition> entry : driver.getAccessDefinitions().entrySet()) {
			DeviceDriver.AccessDefinition access = entry.getValue();
			accesses.put(entry.getKey(), new DeviceTypeDto.Access(access.getName(), access.getProtocol(), access.getDescription(),
				access.getDefaultPort(), access.getGroup(), access.getPriority()));
		}
		DeviceDriver.Location location = driver.getLocation();
		return new DeviceTypeDto(
			driver.getName(),
			driver.getAuthor(),
			driver.getDescription(),
			driver.getVersion(),
			driver.getPriority(),
			attributes,
			options,
			new ArrayList<>(driver.getProtocols()),
			new ArrayList<>(driver.getCliMainModes()),
			driver.getSourceHash(),
			location == null ? null : new DeviceTypeDto.Location(location.getType(), location.getFileName()),
			accesses);
	}
}
