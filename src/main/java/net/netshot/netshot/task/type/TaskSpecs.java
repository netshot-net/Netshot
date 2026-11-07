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
package net.netshot.netshot.task.type;

import java.util.Map;

/**
 * The parameters shared by several types of task, read from any task specification (for the
 * representations which show them for every type of task, with a default value for the types
 * which don't have them).
 */
public final class TaskSpecs {

	private TaskSpecs() {
	}

	/**
	 * Gets the description of a type of task from its name, without a specification (the same as
	 * {@link TaskSpec#description()}).
	 *
	 * @param typeName the name of the type of task (e.g. TakeSnapshotTask)
	 * @return the description, or the name for an unknown type
	 */
	public static String description(String typeName) {
		return switch (typeName == null ? "" : typeName) {
			case "TakeSnapshotTask" -> "Device snapshot";
			case "RunDiagnosticsTask" -> "Device diagnostics";
			case "CheckComplianceTask" -> "Device compliance check";
			case "RunDeviceScriptTask" -> "Device script execution";
			case "DiscoverDeviceTypeTask" -> "Device autodiscovery";
			case "ScanSubnetsTask" -> "Subnet scan";
			case "PurgeDatabaseTask" -> "Database purge";
			case "TakeGroupSnapshotTask" -> "Group snapshot";
			case "CheckGroupComplianceTask" -> "Group compliance check";
			case "CheckGroupSoftwareTask" -> "Group software compliance and hardware support check";
			case "RunGroupDiagnosticsTask" -> "Group diagnostics";
			case "RunDeviceGroupScriptTask" -> "Group script execution";
			default -> typeName;
		};
	}

	/**
	 * Gets the ID of the target device.
	 *
	 * @param spec the specification
	 * @return the device ID, 0 for none
	 */
	public static long deviceId(TaskSpec spec) {
		return spec instanceof DeviceBasedTask t && t.deviceId() != null ? t.deviceId() : 0;
	}

	/**
	 * Gets the ID of the target group.
	 *
	 * @param spec the specification
	 * @return the group ID, 0 for none
	 */
	public static long deviceGroupId(TaskSpec spec) {
		return spec instanceof GroupBasedTask t && t.deviceGroupId() != null ? t.deviceGroupId() : 0;
	}

	/**
	 * Gets the script.
	 *
	 * @param spec the specification
	 * @return the script, null for none
	 */
	public static String script(TaskSpec spec) {
		return switch (spec) {
			case RunDeviceScriptTask t -> t.script();
			case RunDeviceGroupScriptTask t -> t.script();
			default -> null;
		};
	}

	/**
	 * Gets the driver the script is written for.
	 *
	 * @param spec the specification
	 * @return the driver name, null for none
	 */
	public static String deviceDriver(TaskSpec spec) {
		return switch (spec) {
			case RunDeviceScriptTask t -> t.deviceDriver();
			case RunDeviceGroupScriptTask t -> t.deviceDriver();
			default -> null;
		};
	}

	/**
	 * Gets the values of the user inputs of the script.
	 *
	 * @param spec the specification
	 * @return the values, null for none
	 */
	public static Map<String, String> userInputValues(TaskSpec spec) {
		return switch (spec) {
			case RunDeviceScriptTask t -> t.userInputValues();
			case RunDeviceGroupScriptTask t -> t.userInputValues();
			default -> null;
		};
	}

	/**
	 * Tells whether a snapshot is taken after the script.
	 *
	 * @param spec the specification
	 * @return true to take a snapshot
	 */
	public static boolean runSnapshot(TaskSpec spec) {
		return switch (spec) {
			case RunDeviceScriptTask t -> t.runSnapshot();
			case RunDeviceGroupScriptTask t -> t.runSnapshot();
			default -> false;
		};
	}

	/**
	 * Tells whether the diagnostics are run after the script.
	 *
	 * @param spec the specification
	 * @return true to run the diagnostics
	 */
	public static boolean runDiagnostics(TaskSpec spec) {
		return switch (spec) {
			case RunDeviceScriptTask t -> t.runDiagnostics();
			case RunDeviceGroupScriptTask t -> t.runDiagnostics();
			default -> false;
		};
	}

	/**
	 * Tells whether the compliance is checked after the script.
	 *
	 * @param spec the specification
	 * @return true to check the compliance
	 */
	public static boolean checkCompliance(TaskSpec spec) {
		return switch (spec) {
			case RunDeviceScriptTask t -> t.checkCompliance();
			case RunDeviceGroupScriptTask t -> t.checkCompliance();
			default -> false;
		};
	}

	/**
	 * Tells whether the diagnostics are not run after the snapshot.
	 *
	 * @param spec the specification
	 * @return true not to run the diagnostics
	 */
	public static boolean dontRunDiagnostics(TaskSpec spec) {
		return switch (spec) {
			case TakeSnapshotTask t -> t.dontRunDiagnostics();
			case TakeGroupSnapshotTask t -> t.dontRunDiagnostics();
			default -> false;
		};
	}

	/**
	 * Tells whether the compliance is not checked after the snapshot or the diagnostics.
	 *
	 * @param spec the specification
	 * @return true not to check the compliance
	 */
	public static boolean dontCheckCompliance(TaskSpec spec) {
		return switch (spec) {
			case TakeSnapshotTask t -> t.dontCheckCompliance();
			case TakeGroupSnapshotTask t -> t.dontCheckCompliance();
			case RunDiagnosticsTask t -> t.dontCheckCompliance();
			case RunGroupDiagnosticsTask t -> t.dontCheckCompliance();
			default -> false;
		};
	}
}
