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
package net.netshot.netshot.devicescript.helper;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.proxy.ProxyObject;

import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.platform.log.TaskContext;

/**
 * The options object to pass data to JavaScript entry function in a generic way.
 * @author sylvain.cadilhac
 *
 */
public final class JsDeviceScriptOptions {
	@Getter(onMethod = @__({
		@Export
	}))
	@Setter
	private JsCliHelper cliHelper;

	@Getter(onMethod = @__({
		@Export
	}))
	@Setter
	private JsSnmpHelper snmpHelper;

	@Getter(onMethod = @__({
		@Export
	}))
	@Setter
	private JsClientFactory clientFactory;

	@Getter(onMethod = @__({
		@Export
	}))
	@Setter
	private JsConfigHelper configHelper;

	@Getter(onMethod = @__({
		@Export
	}))
	@Setter
	private JsDeviceHelper deviceHelper;

	@Getter(onMethod = @__({
		@Export
	}))
	@Setter
	private JsDiagnosticHelper diagnosticHelper;

	@Getter(onMethod = @__({
		@Export
	}))
	private ProxyObject userInputs;

	@Getter(onMethod = @__({
		@Export
	}))
	private TaskContext taskContext;

	public JsDeviceScriptOptions(JsCliHelper cliHelper, JsSnmpHelper snmpHelper, TaskContext taskContext) {
		this.cliHelper = cliHelper;
		this.snmpHelper = snmpHelper;
		this.taskContext = taskContext;
	}

	public void setUserInputs(ProxyObject userInputs) {
		this.userInputs = userInputs;
	}

	public void setUserInputs(Map<String, String> inputs) {
		this.userInputs = null;
		if (inputs != null) {
			this.userInputs = ProxyObject.fromMap(new HashMap<>(inputs));
		}
	}

}
