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
package net.netshot.netshot.api.rest.v1.device;

import java.time.Instant;

import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.credentials.CredentialSetMapper;
import net.netshot.netshot.api.rest.v1.domain.DomainMapper;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;

/**
 * Maps the devices to their v1 representation.
 */
public final class DeviceMapper {

	/** The path of the devices. */
	public static final String PATH = "devices";

	private DeviceMapper() {
	}

	private static String driverDescription(DriverRegistry driverRegistry, String driverName) {
		DeviceDriver driver = driverName == null ? null : driverRegistry.getDriverByName(driverName);
		return driver == null ? "Unknown driver" : driver.getDescription();
	}

	private static boolean isPast(Instant date) {
		return date != null && date.isBefore(Instant.now());
	}

	/**
	 * Maps a device.
	 *
	 * @param driverRegistry the loaded drivers (for the description of the driver)
	 * @param device the device
	 * @return the representation
	 */
	public static DeviceDto toDto(DriverRegistry driverRegistry, Device device) {
		return new DeviceDto(device.id(), V1Links.of(PATH, device.id()), device.name(), (long) device.version(),
			device.changeDate(), device.createdDate(), device.creator(), device.name(), device.mgmtAddress(), device.status(),
			DomainMapper.toDto(device.mgmtDomain()), device.family(), device.driver(),
			driverDescription(driverRegistry, device.driver()), device.networkClass(), device.softwareVersion(),
			device.softwareLevel(), device.serialNumber(), device.location(), device.contact(), device.comments(),
			device.cachedIpAddress() == null ? null : device.cachedIpAddress().getHostAddress(),
			device.compliant(), isPast(device.eolDate()), isPast(device.eosDate()),
			device.eolDate(), toDto(device.eolModule()), device.eosDate(), toDto(device.eosModule()),
			device.tags(), device.virtualDevices(), device.vrfInstances(), device.options(),
			device.attributes() == null ? null : device.attributes().stream().map(DeviceMapper::toDto).toList(),
			device.accesses() == null ? null : device.accesses().stream().map(DeviceMapper::toDto).toList(),
			device.ownerGroups() == null ? null : device.ownerGroups().stream().map(GroupMapper::toDto).toList());
	}

	/**
	 * Maps a device summary: the properties the summary doesn't hold are null (the resource only uses a summary
	 * when the selection writes none of them, see {@link DeviceDto#SUMMARY_PROPERTIES}).
	 *
	 * @param driverRegistry the loaded drivers (for the description of the driver)
	 * @param device the device summary
	 * @return the representation
	 */
	public static DeviceDto toDto(DriverRegistry driverRegistry, DeviceSummary device) {
		return new DeviceDto(device.id(), V1Links.of(PATH, device.id()), device.name(), null, null, null, null,
			device.name(), device.mgmtAddress(), device.status(), null, device.family(), device.driver(),
			driverDescription(driverRegistry, device.driver()), device.networkClass(), null, device.softwareLevel(), null,
			null, null, null, null, device.configCompliant(), device.eol(), device.eos(), null, null, null, null,
			null, null, null, null, null, null, null);
	}

	/**
	 * Maps a module.
	 *
	 * @param module the module (null gives null)
	 * @return the representation
	 */
	public static ModuleDto toDto(Module module) {
		if (module == null) {
			return null;
		}
		return new ModuleDto(module.id(), module.slot(), module.partNumber(), module.serialNumber(), module.firstSeenDate(),
			module.lastSeenDate(), module.removed());
	}

	private static DeviceAttributeDto toDto(DeviceAttribute attribute) {
		return switch (attribute) {
			case DeviceTextAttribute a -> new DeviceAttributeDto(a.name(), "TEXT", a.text());
			case DeviceLongTextAttribute a -> new DeviceAttributeDto(a.name(), "LONGTEXT", a.text());
			case DeviceNumericAttribute a -> new DeviceAttributeDto(a.name(), "NUMERIC", a.number());
			case DeviceBinaryAttribute a -> new DeviceAttributeDto(a.name(), "BINARY", a.assumption());
		};
	}

	private static DeviceAccessDto toDto(DeviceAccess access) {
		return new DeviceAccessDto(access.accessName(), access.address(), access.port(),
			CredentialSetMapper.toDto(access.globalCredentialSet()), CredentialSetMapper.toDto(access.specificCredentialSet()),
			access.sshHostKeyVerification(), access.sshTrustedHostKeys(), access.sshTrustedHostKeysSince(),
			access.httpsCaTrustMode(), access.httpsCustomCaCertificate());
	}
}
