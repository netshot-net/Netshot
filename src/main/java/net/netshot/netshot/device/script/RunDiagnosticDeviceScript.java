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
package net.netshot.netshot.device.script;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.hibernate.Session;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.Device.MissingDeviceDriverException;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.access.AccessManager;
import net.netshot.netshot.device.access.InvalidCredentialsException;
import net.netshot.netshot.device.script.helper.JsCliHelper;
import net.netshot.netshot.device.script.helper.JsDeviceScriptOptions;
import net.netshot.netshot.device.script.helper.JsClientFactory;
import net.netshot.netshot.device.script.helper.JsDeviceHelper;
import net.netshot.netshot.device.script.helper.JsDiagnosticHelper;
import net.netshot.netshot.device.script.helper.JsSnmpHelper;
import net.netshot.netshot.device.script.helper.JsUtils;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.work.TaskContext;

@Slf4j
public final class RunDiagnosticDeviceScript extends DeviceScript {

	/** The diagnostics to execute. */
	private List<Diagnostic> diagnostics;

	/**
	 * Instantiates a diagnostic CLI script.
	 * @param diagnostics = the list of diagnostics
	 * @param logger = the task context
	 */
	public RunDiagnosticDeviceScript(List<Diagnostic> diagnostics, TaskContext logger) {
		super(logger);
		this.diagnostics = diagnostics;
	}

	@Override
	protected void run(Session session, Device device, AccessManager accessManager)
		throws InvalidCredentialsException, IOException, UnsupportedOperationException, MissingDeviceDriverException {
		DeviceDriver driver = device.getDeviceDriver();
		JsCliHelper jsCliHelper = new JsCliHelper(accessManager, driver.getDefaultCliAccessDefinitions(), true, this.taskContext);
		JsSnmpHelper jsSnmpHelper = new JsSnmpHelper(accessManager, driver.getDefaultSnmpAccessDefinitions(), true, this.taskContext);
		JsClientFactory clientFactory = new JsClientFactory(accessManager, driver);
		// Filter on the device driver
		try (Context context = driver.getContext()) {
			driver.loadCode(context);
			JsDeviceScriptOptions options = new JsDeviceScriptOptions(jsCliHelper, jsSnmpHelper, this.taskContext);
			options.setClientFactory(clientFactory);
			clientFactory.setOptions(options);
			options.setDeviceHelper(new JsDeviceHelper(device, jsCliHelper, null, this.taskContext, false));

			Map<String, Object> jsDiagnostics = new HashMap<String, Object>();
			for (Diagnostic diagnostic : this.diagnostics) {
				try {
					Value jsObject = diagnostic.getJsObject(device, context);
					if (jsObject == null) {
						continue;
					}
					jsDiagnostics.put(diagnostic.getName(), jsObject);
				}
				catch (Exception e1) {
					log.error("Error while preparing the diagnostic {} for JS", diagnostic.getName(), e1);
					this.taskContext.error("Error while preparing the diagnostic {} for JS: '{}'.",
						diagnostic.getName(), e1.getMessage());
				}
			}
			options.setDiagnosticHelper(new JsDiagnosticHelper(device, diagnostics, jsDiagnostics, this.taskContext));

			if (jsDiagnostics.size() > 0) {
				context.getBindings("js")
					.getMember("_connect")
					.execute("diagnostics", options);
			}

		}
		catch (PolyglotException e) {
			log.error("Error while running script using driver {}.", driver.getName(), e);
			this.taskContext.error("Error while running script using driver {}: '{}'.",
				driver.getName(), JsUtils.jsErrorToMessage(e));
			if (e.getMessage().contains("Authentication failed")) {
				throw new InvalidCredentialsException("Authentication failed");
			}
			else {
				throw e;
			}
		}
		catch (UnsupportedOperationException e) {
			log.error("No such method while using driver {}.", driver.getName(), e);
			this.taskContext.error("No such method while using driver {} to execute script: '{}'.",
				driver.getName(), e.getMessage());
			throw e;
		}
	}

}
