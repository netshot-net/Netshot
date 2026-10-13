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
package net.netshot.netshot.devicescript.helper;

import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.engine.TaskContext;

/**
 * This class is used to control the diagnostic results from JavaScript.
 * @author sylvain.cadilhac
 *
 */
@Slf4j
public class JsDiagnosticHelper {

	/** The device the diagnostic is running on. */
	public final Device device;
	/** The list of diagnostics. */
	public List<Diagnostic> diagnostics;
	/** The list of JS diagnostics. */
	public Map<String, Object> jsDiagnostics;
	/** The JS Logger. */
	private TaskContext taskContext;

	/**
	 * Instantiate a new JS diagnostic helper.
	 * @param device The device to run the diagnostics on
	 * @param diagnostics The list of diagnostics
	 * @param jsDiagnostics The set of JS diagnostics to run
	 * @param taskContext the JS logger
	 */
	public JsDiagnosticHelper(Device device, List<Diagnostic> diagnostics, Map<String, Object> jsDiagnostics, TaskContext taskContext) {
		this.device = device;
		this.diagnostics = diagnostics;
		this.jsDiagnostics = jsDiagnostics;
		this.taskContext = taskContext;
	}

	/**
	 * Set a diagnostic result.
	 * @param key The key
	 * @param value The value
	 */
	@Export
	public void set(String key, Value value) {
		if (value == null) {
			this.taskContext.warn("Value for diagnostic key '{}' is null", key);
			return;
		}
		try {
			for (Diagnostic diagnostic : diagnostics) {
				if (diagnostic.getName().equals(key)) {
					this.taskContext.warn("Setting value for diagnostic key '{}'", key);
					DiagnosticResult result = diagnostic.makeResult(this.device, value);
					if (result != null) {
						this.device.addDiagnosticResult(result);
					}
				}
			}
		}
		catch (Exception e) {
			log.warn("Error while setting the diagnostic result '{}'.", key, e);
			this.taskContext.error("Can't set diagnostic result {}: {}", key, e.toString());
		}
	}

	/**
	 * Get the diagnostics.
	 * @return the diagnostics
	 */
	@Export
	public ProxyObject getDiagnostics() {
		return ProxyObject.fromMap(this.jsDiagnostics);
	}

}
