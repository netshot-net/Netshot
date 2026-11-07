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
package net.netshot.netshot.report;

import java.util.Date;
import java.util.List;

import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.platform.net.NetworkAddress.AddressUsage;

/**
 * The receiver of the data export: {@link ReportService#export} pushes the exported data to it,
 * section by section and row by row, in the order of the sheets. The data is streamed (the
 * service never holds a whole section, except for the interfaces and the compliance results),
 * so the implementation decides how it is written (the REST layer writes a spreadsheet).
 *
 * <p>A {@code ...Start} call opens a section, the rows follow. A section is only started if it
 * was selected.</p>
 */
public interface ReportExportSink {

	/**
	 * The summary of the export (always the first call).
	 *
	 * @param domains the "name (ID)" of the selected domains, or null if the export is not filtered on domains
	 * @param groups the "name (ID)" of the selected groups, or null if the export is not filtered on groups
	 */
	void summary(List<String> domains, List<String> groups);

	/**
	 * Starts the devices section (always started).
	 *
	 * @param compliance whether the compliance columns are exported
	 * @param locations whether the location columns are exported
	 */
	void devicesStart(boolean compliance, boolean locations);

	/**
	 * A device.
	 *
	 * @param device the device
	 */
	void device(ExportedDevice device);

	/** Starts the interfaces section. */
	void interfacesStart();

	/**
	 * An interface.
	 *
	 * @param networkInterface the interface
	 */
	void networkInterface(ExportedInterface networkInterface);

	/**
	 * Starts the inventory section.
	 *
	 * @param history whether the history columns are exported
	 */
	void inventoryStart(boolean history);

	/**
	 * A module.
	 *
	 * @param module the module
	 */
	void module(ExportedModule module);

	/** Starts the compliance results section. */
	void complianceStart();

	/**
	 * A compliance result.
	 *
	 * @param result the result
	 */
	void complianceResult(PolicyRuleDevice result);

	/**
	 * Starts the groups section.
	 *
	 * @param compliance whether the compliance columns are exported
	 */
	void groupsStart(boolean compliance);

	/**
	 * A group.
	 *
	 * @param group the group and its configuration compliance
	 */
	void group(GroupConfigComplianceStat group);

	/** Starts the group memberships section. */
	void membershipsStart();

	/**
	 * A group membership.
	 *
	 * @param membership the membership
	 */
	void membership(ExportedMembership membership);

	/** Starts the driver-specific attributes section. */
	void attributesStart();

	/**
	 * A driver-specific attribute.
	 *
	 * @param attribute the attribute
	 */
	void attribute(ExportedAttribute attribute);

	/**
	 * An exported device.
	 *
	 * @param id the ID
	 * @param name the name
	 * @param mgmtAddress the management address
	 * @param domainName the name of the management domain
	 * @param networkClass the network class
	 * @param family the family
	 * @param createdDate the creation date
	 * @param changeDate the last change date
	 * @param softwareVersion the software version
	 * @param softwareLevel the software compliance level (may be null)
	 * @param eosDate the end-of-sale date (may be null)
	 * @param eolDate the end-of-life date (may be null)
	 * @param location the location
	 * @param contact the contact
	 */
	record ExportedDevice(
		long id,
		String name,
		String mgmtAddress,
		String domainName,
		NetworkClass networkClass,
		String family,
		Date createdDate,
		Date changeDate,
		String softwareVersion,
		ConformanceLevel softwareLevel,
		Date eosDate,
		Date eolDate,
		String location,
		String contact) {
	}

	/**
	 * An address of an exported interface.
	 *
	 * @param ip the IP address
	 * @param prefixLength the prefix length
	 * @param usage the usage (may be null)
	 */
	record ExportedAddress(String ip, int prefixLength, AddressUsage usage) {
	}

	/**
	 * An exported interface.
	 *
	 * @param deviceId the device ID
	 * @param deviceName the device name
	 * @param virtualDevice the virtual device
	 * @param name the interface name
	 * @param description the description
	 * @param vrf the VRF instance
	 * @param macAddress the MAC address
	 * @param enabled whether the interface is enabled
	 * @param level3 whether the interface is a level 3 one
	 * @param addresses the IP addresses
	 */
	record ExportedInterface(
		long deviceId,
		String deviceName,
		String virtualDevice,
		String name,
		String description,
		String vrf,
		String macAddress,
		boolean enabled,
		boolean level3,
		List<ExportedAddress> addresses) {
	}

	/**
	 * An exported module.
	 *
	 * @param deviceId the device ID
	 * @param deviceName the device name
	 * @param slot the slot
	 * @param partNumber the part number
	 * @param serialNumber the serial number
	 * @param firstSeenDate the date the module was first seen
	 * @param lastSeenDate the date the module was last seen
	 * @param removed whether the module has been removed
	 */
	record ExportedModule(
		long deviceId,
		String deviceName,
		String slot,
		String partNumber,
		String serialNumber,
		Date firstSeenDate,
		Date lastSeenDate,
		boolean removed) {
	}

	/**
	 * An exported group membership.
	 *
	 * @param deviceId the device ID
	 * @param deviceName the device name
	 * @param groupId the group ID
	 * @param groupName the group name
	 */
	record ExportedMembership(long deviceId, String deviceName, long groupId, String groupName) {
	}

	/**
	 * An exported driver-specific attribute.
	 *
	 * @param deviceId the device ID
	 * @param deviceName the device name
	 * @param title the title of the attribute, as defined by the driver (null if the driver doesn't define it)
	 * @param value the value
	 */
	record ExportedAttribute(long deviceId, String deviceName, String title, String value) {
	}
}
