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
package net.netshot.netshot.device.script.helper;

import java.util.List;

import org.graalvm.polyglot.HostAccess.Export;

import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.DeviceDriver.AccessDefinition;
import net.netshot.netshot.device.access.AccessManager;

/**
 * The Java-side backing of the JS {@code client.create(...)} factory.
 * Access-name resolution (literal key vs. family alias, e.g. "ssh" vs
 * "snmp") is done in {@code driver-loader.js}, since it needs to read the
 * driver's own {@code CLI}/{@code SNMP}/{@code HTTP} globals; by the time
 * this class is called, {@code accessNames} are already concrete, literal
 * keys of {@link DeviceDriver#getAccessDefinitions()}.
 */
public class JsClientFactory {

	private final AccessManager accessManager;
	private final DeviceDriver driver;

	/**
	 * The current script's options, set once available (see {@link #setOptions}) -
	 * needed so {@link #createHttp} can hand the current {@link JsConfigHelper}
	 * (if any) to the {@link JsHttpHelper} it builds, letting {@code http.download(...)}
	 * land a downloaded file straight into a config attribute. Not passed at
	 * construction time: this factory is built before the {@code JsDeviceScriptOptions}
	 * that references it back.
	 */
	private JsDeviceScriptOptions options;

	public JsClientFactory(AccessManager accessManager, DeviceDriver driver) {
		this.accessManager = accessManager;
		this.driver = driver;
	}

	/**
	 * @param options the current script's options, once built
	 */
	public void setOptions(JsDeviceScriptOptions options) {
		this.options = options;
	}

	@Export
	public JsCliHelper createCli(List<String> accessNames, boolean autoTryCredentials) {
		List<AccessDefinition> defs = this.driver.getAccessDefinitions(accessNames);
		return new JsCliHelper(this.accessManager, defs, autoTryCredentials, this.accessManager.getTaskContext());
	}

	@Export
	public JsSnmpHelper createSnmp(List<String> accessNames, boolean autoTryCredentials) {
		List<AccessDefinition> defs = this.driver.getAccessDefinitions(accessNames);
		return new JsSnmpHelper(this.accessManager, defs, autoTryCredentials, this.accessManager.getTaskContext());
	}

	@Export
	public JsHttpHelper createHttp(List<String> accessNames, boolean autoTryCredentials, String basePath) {
		List<AccessDefinition> defs = this.driver.getAccessDefinitions(accessNames);
		JsConfigHelper configHelper = this.options == null ? null : this.options.getConfigHelper();
		return new JsHttpHelper(this.accessManager, defs, autoTryCredentials, basePath,
			this.accessManager.getTaskContext(), configHelper);
	}

}
