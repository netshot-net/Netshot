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
package net.netshot.netshot.devicescript;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import javax.script.ScriptException;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.access.InvalidCredentialsException;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device.MissingDeviceDriverException;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.task.engine.TaskContext;

/**
 * Something to execute on a device.
 * <p>
 * Unlike before, connecting to the device (and iterating over candidate
 * credential sets) is no longer done up-front here: it now happens lazily,
 * per access, the first time the driver's JS code actually uses a given
 * client (see {@link AccessManager}). This class is only responsible for
 * resolving the address to connect to and building the {@link AccessManager}
 * that the JS-facing helpers will use.
 */
@Slf4j
public abstract class DeviceScript {

	protected final transient DriverRegistry driverRegistry;

	protected transient TaskContext taskContext;

	protected DeviceScript(DriverRegistry driverRegistry, TaskContext taskContext) {
		this.driverRegistry = driverRegistry;
		this.taskContext = taskContext;
	}

	protected abstract void run(DeviceDraft device, AccessManager accessManager)
		throws InvalidCredentialsException, IOException, ScriptException, MissingDeviceDriverException;

	public void connectRun(DeviceDraft device)
		throws IOException, MissingDeviceDriverException, InvalidCredentialsException, ScriptException, MissingDeviceDriverException {
		this.connectRun(device, null);
	}

	public void connectRun(DeviceDraft device, Set<DeviceCredentialSet> oneTimeCredentialSets)
		throws IOException, MissingDeviceDriverException, InvalidCredentialsException, ScriptException, MissingDeviceDriverException {
		this.driverRegistry.forDevice(device); // fail fast if the driver is missing

		Set<DeviceCredentialSet> credentialSets = new HashSet<>();
		if (oneTimeCredentialSets != null) {
			credentialSets.addAll(oneTimeCredentialSets);
		}

		// AccessManager.forDevice resolves (and caches) the device's default
		// management address, used by any access without its own address
		// override. Failure there isn't immediately fatal: an access with its
		// own override address may still be usable, so it only surfaces as a
		// warning, not an exception.
		AccessManager accessManager = AccessManager.forDevice(this.driverRegistry, device, this.taskContext, credentialSets);

		try {
			this.run(device, accessManager);
		}
		finally {
			// Whatever the driver script actually connected to (CLI, SNMP, and/or any
			// ad-hoc client created via client.create(...)) must be explicitly closed
			// here - otherwise it lingers until the underlying transport's own idle
			// timeout eventually cleans it up (e.g. ~10mn by default for SSH).
			accessManager.disconnectAll();
		}
	}

}
