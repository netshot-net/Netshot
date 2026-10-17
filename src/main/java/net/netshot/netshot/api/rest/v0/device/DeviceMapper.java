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
import java.util.Comparator;
import java.util.List;

import org.hibernate.Hibernate;

import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.api.rest.v0.domain.DomainMapper;
import net.netshot.netshot.api.rest.v0.group.DeviceGroupDto;
import net.netshot.netshot.api.rest.v0.group.GroupMapper;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device.MissingDeviceDriverException;
import net.netshot.netshot.inventory.device.Device;
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
 * Maps the devices between the entities (or the projections) of the inventory and their REST
 * representation. All the methods are pure functions.
 *
 * <p>{@link #toDto(Device)} reads a <em>detached</em> device: it only uses what the fetch plan of
 * {@code DeviceRepository.findWithDetails} loaded, and fails with a
 * {@code LazyInitializationException} if the device comes from somewhere else.</p>
 */
public final class DeviceMapper {

	private DeviceMapper() {
		// Static helpers only
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
	 * @param device the device, loaded with the fetch plan of {@code DeviceRepository.findWithDetails}
	 * @return the DTO
	 */
	public static DeviceDto toDto(DriverRegistry driverRegistry, Device device) {
		// The collections of the device are sets: they are listed in a stable order
		List<DeviceAccessDto> accesses = new ArrayList<>();
		device.getAccesses().stream().sorted(Comparator.comparing(DeviceAccess::getAccessName, Comparator.nullsFirst(Comparator.naturalOrder())))
			.forEach(access -> accesses.add(DeviceAccessMapper.toDto(access)));
		List<DeviceAttributeDto> attributes = new ArrayList<>();
		device.getAttributes().stream().sorted(Comparator.comparing(DeviceAttribute::getName, Comparator.nullsFirst(Comparator.naturalOrder())))
			.forEach(attribute -> attributes.add(toDto(attribute)));
		List<DeviceGroupDto> ownerGroups = new ArrayList<>();
		device.getOwnerGroups().stream().sorted(Comparator.comparingLong(DeviceGroup::getId))
			.forEach(group -> ownerGroups.add(GroupMapper.toDto(group)));
		return new DeviceDto(
			accesses,
			attributes,
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
			toDto(device.getEolModule()),
			device.getEosDate(),
			toDto(device.getEosModule()),
			device.getFamily(),
			device.getId(),
			device.getLocation(),
			device.getMgmtAddress(),
			DomainMapper.toDto(device.getMgmtDomain()),
			device.getName(),
			device.getNetworkClass(),
			device.getOptions(),
			ownerGroups,
			realDeviceType(driverRegistry, device),
			device.getSerialNumber(),
			device.getSoftwareLevel(),
			device.getSoftwareVersion(),
			device.getStatus(),
			device.getTags().stream().sorted().toList());
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
		return new DeviceCreation(
			request.autoDiscover(),
			request.ipAddress(),
			request.domainId(),
			request.deviceType(),
			toAccesses(request.accesses()),
			request.options(),
			request.comments(),
			request.tags(),
			author);
	}

	/**
	 * Maps a request to edit a device to the command of the service.
	 *
	 * @param request the request
	 * @return the command
	 */
	public static DeviceUpdate toUpdate(UpdateDeviceRequest request) {
		return new DeviceUpdate(
			request.enabled(),
			request.comments(),
			request.ipAddress(),
			request.mgmtDomain(),
			toAccesses(request.accesses()),
			request.options(),
			request.tags());
	}

	private static List<DeviceAccess> toAccesses(List<DeviceAccessDto> dtos) {
		if (dtos == null) {
			return null;
		}
		List<DeviceAccess> accesses = new ArrayList<>(dtos.size());
		dtos.forEach(dto -> accesses.add(DeviceAccessMapper.toEntity(dto)));
		return accesses;
	}

	private static DeviceAttributeDto toDto(DeviceAttribute attribute) {
		if (attribute instanceof DeviceTextAttribute text) {
			return new DeviceAttributeDto.Text(text.getName(), text.getText());
		}
		else if (attribute instanceof DeviceNumericAttribute numeric) {
			return new DeviceAttributeDto.Numeric(numeric.getName(), numeric.getNumber());
		}
		else if (attribute instanceof DeviceBinaryAttribute binary) {
			return new DeviceAttributeDto.Binary(binary.getName(), binary.getAssumption());
		}
		else if (attribute instanceof DeviceLongTextAttribute) {
			return new DeviceAttributeDto.LongText(attribute.getName());
		}
		throw new IllegalArgumentException("Unknown kind of device attribute: " + attribute.getClass().getName());
	}

	public static ModuleDto toDto(Module moduleOrProxy) {
		if (moduleOrProxy == null) {
			return null;
		}
		Module module = (Module) Hibernate.unproxy(moduleOrProxy);
		return new ModuleDto(
			module.getId(),
			module.getSlot(),
			module.getPartNumber(),
			module.getSerialNumber(),
			module.getFirstSeenDate(),
			module.getLastSeenDate(),
			module.isRemoved());
	}

}
