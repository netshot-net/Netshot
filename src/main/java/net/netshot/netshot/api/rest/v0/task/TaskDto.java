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
package net.netshot.netshot.api.rest.v0.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;


/**
 * The full representation of a task. The {@code type} property (the simple class name of the
 * task type, as it always was) tells which subtype it is, and the XML root element is named
 * after the type. Every subtype carries the properties common to all tasks
 * ({@link TaskBaseDto}) and the ones specific to its type.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = TaskDto.CheckCompliance.class, name = "CheckComplianceTask"),
	@JsonSubTypes.Type(value = TaskDto.CheckGroupCompliance.class, name = "CheckGroupComplianceTask"),
	@JsonSubTypes.Type(value = TaskDto.CheckGroupSoftware.class, name = "CheckGroupSoftwareTask"),
	@JsonSubTypes.Type(value = TaskDto.DiscoverDeviceType.class, name = "DiscoverDeviceTypeTask"),
	@JsonSubTypes.Type(value = TaskDto.PurgeDatabase.class, name = "PurgeDatabaseTask"),
	@JsonSubTypes.Type(value = TaskDto.RunDeviceGroupScript.class, name = "RunDeviceGroupScriptTask"),
	@JsonSubTypes.Type(value = TaskDto.RunDeviceScript.class, name = "RunDeviceScriptTask"),
	@JsonSubTypes.Type(value = TaskDto.RunDiagnostics.class, name = "RunDiagnosticsTask"),
	@JsonSubTypes.Type(value = TaskDto.RunGroupDiagnostics.class, name = "RunGroupDiagnosticsTask"),
	@JsonSubTypes.Type(value = TaskDto.ScanSubnets.class, name = "ScanSubnetsTask"),
	@JsonSubTypes.Type(value = TaskDto.TakeGroupSnapshot.class, name = "TakeGroupSnapshotTask"),
	@JsonSubTypes.Type(value = TaskDto.TakeSnapshot.class, name = "TakeSnapshotTask"),
})
public sealed interface TaskDto {

	/**
	 * A device compliance check.
	 *
	 * @param base the common properties
	 * @param deviceId the ID of the target device
	 */
	@JacksonXmlRootElement(localName = "CheckComplianceTask")
	record CheckCompliance(@JsonUnwrapped TaskBaseDto base, long deviceId) implements TaskDto {
	}

	/**
	 * A group compliance check.
	 *
	 * @param base the common properties
	 * @param deviceGroupId the ID of the target group
	 */
	@JacksonXmlRootElement(localName = "CheckGroupComplianceTask")
	record CheckGroupCompliance(@JsonUnwrapped TaskBaseDto base, long deviceGroupId) implements TaskDto {
	}

	/**
	 * A group software compliance and hardware support check.
	 *
	 * @param base the common properties
	 * @param deviceGroupId the ID of the target group
	 */
	@JacksonXmlRootElement(localName = "CheckGroupSoftwareTask")
	record CheckGroupSoftware(@JsonUnwrapped TaskBaseDto base, long deviceGroupId) implements TaskDto {
	}

	/**
	 * A device auto discovery.
	 *
	 * @param base the common properties
	 * @param deviceAddress the address of the device to discover
	 * @param deviceId the ID of the discovered device, 0 if there is none
	 * @param discoveredDeviceTypeDescription the description of the discovered device type
	 * @param snapshotTaskId the ID of the snapshot task started after the discovery
	 */
	@JacksonXmlRootElement(localName = "DiscoverDeviceTypeTask")
	record DiscoverDeviceType(@JsonUnwrapped TaskBaseDto base, String deviceAddress, long deviceId,
		String discoveredDeviceTypeDescription, long snapshotTaskId) implements TaskDto {
	}

	/**
	 * A database purge.
	 *
	 * @param base the common properties
	 * @param configDays the number of days of configurations to keep
	 * @param configKeepDays the interval of days between two configurations to keep when purging
	 * @param configSize the size (KB) over which configurations are purged
	 * @param days the number of days of tasks to keep
	 * @param deviceGroupId the ID of the group whose devices are purged, 0 for none
	 * @param moduleDays the number of days of removed modules to keep
	 * @param removeOrphanFiles whether the orphan binary attribute files are removed
	 */
	@JacksonXmlRootElement(localName = "PurgeDatabaseTask")
	record PurgeDatabase(@JsonUnwrapped TaskBaseDto base, int configDays, int configKeepDays, int configSize, int days,
		long deviceGroupId, int moduleDays, boolean removeOrphanFiles) implements TaskDto {
	}

	/**
	 * A script execution on a group of devices.
	 *
	 * @param base the common properties
	 * @param deviceGroupId the ID of the target group
	 */
	@JacksonXmlRootElement(localName = "RunDeviceGroupScriptTask")
	record RunDeviceGroupScript(@JsonUnwrapped TaskBaseDto base, long deviceGroupId) implements TaskDto {
	}

	/**
	 * A script execution on a device.
	 *
	 * @param base the common properties
	 * @param deviceId the ID of the target device
	 */
	@JacksonXmlRootElement(localName = "RunDeviceScriptTask")
	record RunDeviceScript(@JsonUnwrapped TaskBaseDto base, long deviceId) implements TaskDto {
	}

	/**
	 * A device diagnostics run.
	 *
	 * @param base the common properties
	 * @param deviceId the ID of the target device
	 */
	@JacksonXmlRootElement(localName = "RunDiagnosticsTask")
	record RunDiagnostics(@JsonUnwrapped TaskBaseDto base, long deviceId) implements TaskDto {
	}

	/**
	 * A group diagnostics run.
	 *
	 * @param base the common properties
	 * @param deviceGroupId the ID of the target group
	 */
	@JacksonXmlRootElement(localName = "RunGroupDiagnosticsTask")
	record RunGroupDiagnostics(@JsonUnwrapped TaskBaseDto base, long deviceGroupId) implements TaskDto {
	}

	/**
	 * A subnet scan.
	 *
	 * @param base the common properties
	 */
	@JacksonXmlRootElement(localName = "ScanSubnetsTask")
	record ScanSubnets(@JsonUnwrapped TaskBaseDto base) implements TaskDto {
	}

	/**
	 * A snapshot of a group of devices.
	 *
	 * @param base the common properties
	 * @param deviceGroupId the ID of the target group
	 * @param limitToOutofdateDeviceHours the devices having a successful snapshot in the last given hours are ignored
	 */
	@JacksonXmlRootElement(localName = "TakeGroupSnapshotTask")
	record TakeGroupSnapshot(@JsonUnwrapped TaskBaseDto base, long deviceGroupId, int limitToOutofdateDeviceHours) implements TaskDto {
	}

	/**
	 * A device snapshot.
	 *
	 * @param base the common properties
	 * @param deviceId the ID of the target device
	 */
	@JacksonXmlRootElement(localName = "TakeSnapshotTask")
	record TakeSnapshot(@JsonUnwrapped TaskBaseDto base, long deviceId) implements TaskDto {
	}
}
