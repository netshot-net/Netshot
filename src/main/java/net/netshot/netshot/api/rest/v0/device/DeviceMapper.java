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
package net.netshot.netshot.api.rest.v0.device;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import net.netshot.netshot.api.rest.v0.domain.DomainMapper;
import net.netshot.netshot.api.rest.v0.group.DeviceGroupDto;
import net.netshot.netshot.api.rest.v0.group.GroupMapper;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.driver.MissingDeviceDriverException;
import net.netshot.netshot.inventory.device.AccessSpecificCredential;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceCreation;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.device.DeviceUpdate;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.group.DeviceGroup;

/**
 * Maps the devices between the records (or the projections) of the inventory and their REST
 * representation. All the methods are pure functions.
 */
public final class DeviceMapper {

	private DeviceMapper() {
		// Static helpers only
	}

	private static Date date(Instant instant) {
		return instant == null ? null : Date.from(instant);
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

	/**
	 * Maps a device to its full representation.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param device the device
	 * @return the DTO
	 */
	public static DeviceDto toDto(DriverRegistry driverRegistry, Device device) {
		// The collections of the device are sets: they are listed in a stable order
		List<DeviceAccessDto> accesses = new ArrayList<>();
		device.accesses().stream().sorted(Comparator.comparing(DeviceAccess::accessName, Comparator.nullsFirst(Comparator.naturalOrder())))
			.forEach(access -> accesses.add(DeviceAccessMapper.toDto(access)));
		List<DeviceAttributeDto> attributes = new ArrayList<>();
		device.attributes().stream().sorted(Comparator.comparing(DeviceAttribute::name, Comparator.nullsFirst(Comparator.naturalOrder())))
			.forEach(attribute -> attributes.add(toDto(attribute)));
		List<DeviceGroupDto> ownerGroups = new ArrayList<>();
		device.ownerGroups().stream().sorted(Comparator.comparingLong(DeviceGroup::id))
			.forEach(group -> ownerGroups.add(GroupMapper.toDto(group)));
		return new DeviceDto(
			accesses,
			attributes,
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
			toDto(device.eolModule()),
			date(device.eosDate()),
			toDto(device.eosModule()),
			device.family(),
			device.id(),
			device.location(),
			device.mgmtAddress(),
			DomainMapper.toDto(device.mgmtDomain()),
			device.name(),
			device.networkClass(),
			device.options(),
			ownerGroups,
			realDeviceType(driverRegistry, device),
			device.serialNumber(),
			device.softwareLevel(),
			device.softwareVersion(),
			device.status(),
			device.tags().stream().sorted().toList());
	}

	/**
	 * Maps a device summary to its representation.
	 *
	 * @param summary the summary
	 * @return the DTO
	 */
	public static DeviceSummaryDto toDto(DeviceSummary summary) {
		return new DeviceSummaryDto(
			summary.id(),
			summary.name(),
			summary.family(),
			summary.mgmtAddress(),
			summary.status(),
			summary.driver(),
			summary.eol(),
			summary.eos(),
			summary.configCompliant(),
			summary.softwareLevel(),
			summary.networkClass());
	}

	/**
	 * Maps the device summaries to their representation.
	 *
	 * @param summaries the summaries
	 * @return the DTOs, in the same order
	 */
	public static List<DeviceSummaryDto> toSummaryDtos(List<DeviceSummary> summaries) {
		List<DeviceSummaryDto> dtos = new ArrayList<>(summaries.size());
		summaries.forEach(summary -> dtos.add(toDto(summary)));
		return dtos;
	}

	/**
	 * Maps a request to add a device to the command of the service.
	 *
	 * @param request the request
	 * @param author the name of the user adding the device
	 * @return the command
	 */
	public static DeviceCreation toCreation(NewDeviceRequest request, String author) {
		Accesses accesses = toAccesses(request.accesses());
		return new DeviceCreation(
			request.autoDiscover(),
			request.ipAddress(),
			request.domainId(),
			request.deviceType(),
			accesses.accesses(),
			request.options(),
			request.comments(),
			request.tags(),
			author,
			accesses.specificCredentials());
	}

	/**
	 * Maps a request to edit a device to the command of the service.
	 *
	 * @param request the request
	 * @return the command
	 */
	public static DeviceUpdate toUpdate(UpdateDeviceRequest request) {
		Accesses accesses = toAccesses(request.accesses());
		return new DeviceUpdate(
			request.enabled(),
			request.comments(),
			request.ipAddress(),
			request.mgmtDomain(),
			accesses.accesses(),
			accesses.specificCredentials(),
			request.options(),
			request.tags());
	}

	/**
	 * The accesses of a request, and the specific credential sets they carried.
	 *
	 * @param accesses the transient accesses, without specific credential set (null if none was given)
	 * @param specificCredentials the specific credential sets of the accesses
	 */
	private record Accesses(List<DeviceAccess> accesses, List<AccessSpecificCredential> specificCredentials) {
	}

	private static Accesses toAccesses(List<DeviceAccessDto> dtos) {
		if (dtos == null) {
			return new Accesses(null, null);
		}
		List<DeviceAccess> accesses = new ArrayList<>(dtos.size());
		List<AccessSpecificCredential> specifics = new ArrayList<>();
		for (DeviceAccessDto dto : dtos) {
			DeviceAccess access = DeviceAccessMapper.toEntity(dto);
			if (access.specificCredentialSet() != null) {
				specifics.add(new AccessSpecificCredential.Legacy(access.accessName(), access.specificCredentialSet()));
				access = access.toBuilder().specificCredentialSet(null).build();
			}
			accesses.add(access);
		}
		return new Accesses(accesses, specifics);
	}

	private static DeviceAttributeDto toDto(DeviceAttribute attribute) {
		return switch (attribute) {
			case DeviceTextAttribute text -> new DeviceAttributeDto.Text(text.name(), text.text());
			case DeviceNumericAttribute numeric -> new DeviceAttributeDto.Numeric(numeric.name(), numeric.number());
			case DeviceBinaryAttribute binary -> new DeviceAttributeDto.Binary(binary.name(), binary.assumption());
			case DeviceLongTextAttribute longText -> new DeviceAttributeDto.LongText(longText.name());
		};
	}

	public static ModuleDto toDto(Module module) {
		if (module == null) {
			return null;
		}
		return new ModuleDto(
			module.id(),
			module.slot(),
			module.partNumber(),
			module.serialNumber(),
			date(module.firstSeenDate()),
			date(module.lastSeenDate()),
			module.removed());
	}

}
