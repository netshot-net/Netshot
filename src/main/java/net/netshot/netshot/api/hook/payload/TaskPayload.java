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
package net.netshot.netshot.api.hook.payload;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;


/**
 * What a webhook posts about a finished task. The {@code type} property (the simple class name of
 * the task type, as it always was) tells which subtype it is, and the XML root element is named
 * after the type. Every subtype carries the properties common to all tasks ({@link TaskBasePayload})
 * and the ones specific to its type.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = TaskPayload.CheckCompliance.class, name = "CheckComplianceTask"),
	@JsonSubTypes.Type(value = TaskPayload.CheckGroupCompliance.class, name = "CheckGroupComplianceTask"),
	@JsonSubTypes.Type(value = TaskPayload.CheckGroupSoftware.class, name = "CheckGroupSoftwareTask"),
	@JsonSubTypes.Type(value = TaskPayload.DiscoverDeviceType.class, name = "DiscoverDeviceTypeTask"),
	@JsonSubTypes.Type(value = TaskPayload.PurgeDatabase.class, name = "PurgeDatabaseTask"),
	@JsonSubTypes.Type(value = TaskPayload.RunDeviceGroupScript.class, name = "RunDeviceGroupScriptTask"),
	@JsonSubTypes.Type(value = TaskPayload.RunDeviceScript.class, name = "RunDeviceScriptTask"),
	@JsonSubTypes.Type(value = TaskPayload.RunDiagnostics.class, name = "RunDiagnosticsTask"),
	@JsonSubTypes.Type(value = TaskPayload.RunGroupDiagnostics.class, name = "RunGroupDiagnosticsTask"),
	@JsonSubTypes.Type(value = TaskPayload.ScanSubnets.class, name = "ScanSubnetsTask"),
	@JsonSubTypes.Type(value = TaskPayload.TakeGroupSnapshot.class, name = "TakeGroupSnapshotTask"),
	@JsonSubTypes.Type(value = TaskPayload.TakeSnapshot.class, name = "TakeSnapshotTask"),
})
public sealed interface TaskPayload {

	/**
	 * A device compliance check.
	 *
	 * @param base the common properties
	 * @param deviceId the ID of the target device
	 */
	@JacksonXmlRootElement(localName = "CheckComplianceTask")
	record CheckCompliance(@JsonUnwrapped TaskBasePayload base, long deviceId) implements TaskPayload {
	}

	/**
	 * A group compliance check.
	 *
	 * @param base the common properties
	 * @param deviceGroupId the ID of the target group
	 */
	@JacksonXmlRootElement(localName = "CheckGroupComplianceTask")
	record CheckGroupCompliance(@JsonUnwrapped TaskBasePayload base, long deviceGroupId) implements TaskPayload {
	}

	/**
	 * A group software compliance and hardware support check.
	 *
	 * @param base the common properties
	 * @param deviceGroupId the ID of the target group
	 */
	@JacksonXmlRootElement(localName = "CheckGroupSoftwareTask")
	record CheckGroupSoftware(@JsonUnwrapped TaskBasePayload base, long deviceGroupId) implements TaskPayload {
	}

	/**
	 * A device auto discovery.
	 *
	 * @param base the common properties
	 * @param deviceAddress the address of the device to discover
	 * @param deviceId the ID of the discovered device, 0 if there is none
	 * @param discoverResultDevice the discovered device, if any
	 * @param discoveredDeviceTypeDescription the description of the discovered device type
	 * @param snapshotTaskId the ID of the snapshot task started after the discovery
	 */
	@JacksonXmlRootElement(localName = "DiscoverDeviceTypeTask")
	record DiscoverDeviceType(@JsonUnwrapped TaskBasePayload base, String deviceAddress, long deviceId,
		DevicePayload discoverResultDevice, String discoveredDeviceTypeDescription, long snapshotTaskId) implements TaskPayload {
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
	record PurgeDatabase(@JsonUnwrapped TaskBasePayload base, int configDays, int configKeepDays, int configSize, int days,
		long deviceGroupId, int moduleDays, boolean removeOrphanFiles) implements TaskPayload {
	}

	/**
	 * A script execution on a group of devices.
	 *
	 * @param base the common properties
	 * @param deviceGroupId the ID of the target group
	 */
	@JacksonXmlRootElement(localName = "RunDeviceGroupScriptTask")
	record RunDeviceGroupScript(@JsonUnwrapped TaskBasePayload base, long deviceGroupId) implements TaskPayload {
	}

	/**
	 * A script execution on a device.
	 *
	 * @param base the common properties
	 * @param deviceId the ID of the target device
	 */
	@JacksonXmlRootElement(localName = "RunDeviceScriptTask")
	record RunDeviceScript(@JsonUnwrapped TaskBasePayload base, long deviceId) implements TaskPayload {
	}

	/**
	 * A device diagnostics run.
	 *
	 * @param base the common properties
	 * @param deviceId the ID of the target device
	 */
	@JacksonXmlRootElement(localName = "RunDiagnosticsTask")
	record RunDiagnostics(@JsonUnwrapped TaskBasePayload base, long deviceId) implements TaskPayload {
	}

	/**
	 * A group diagnostics run.
	 *
	 * @param base the common properties
	 * @param deviceGroupId the ID of the target group
	 */
	@JacksonXmlRootElement(localName = "RunGroupDiagnosticsTask")
	record RunGroupDiagnostics(@JsonUnwrapped TaskBasePayload base, long deviceGroupId) implements TaskPayload {
	}

	/**
	 * A subnet scan.
	 *
	 * @param base the common properties
	 */
	@JacksonXmlRootElement(localName = "ScanSubnetsTask")
	record ScanSubnets(@JsonUnwrapped TaskBasePayload base) implements TaskPayload {
	}

	/**
	 * A snapshot of a group of devices.
	 *
	 * @param base the common properties
	 * @param deviceGroupId the ID of the target group
	 * @param limitToOutofdateDeviceHours the devices having a successful snapshot in the last given hours are ignored
	 */
	@JacksonXmlRootElement(localName = "TakeGroupSnapshotTask")
	record TakeGroupSnapshot(@JsonUnwrapped TaskBasePayload base, long deviceGroupId, int limitToOutofdateDeviceHours) implements TaskPayload {
	}

	/**
	 * A device snapshot.
	 *
	 * @param base the common properties
	 * @param automatic whether the snapshot was automatically started (not by a user)
	 * @param deviceId the ID of the target device
	 */
	@JacksonXmlRootElement(localName = "TakeSnapshotTask")
	record TakeSnapshot(@JsonUnwrapped TaskBasePayload base, boolean automatic, long deviceId) implements TaskPayload {
	}
}
