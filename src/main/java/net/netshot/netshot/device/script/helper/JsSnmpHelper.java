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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.proxy.ProxyObject;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.device.DeviceDriver.AccessDefinition;
import net.netshot.netshot.device.access.AccessManager;
import net.netshot.netshot.device.access.AccessManager.Resolution;
import net.netshot.netshot.device.access.Client;
import net.netshot.netshot.device.access.InvalidCredentialsException;
import net.netshot.netshot.device.access.Snmp;
import net.netshot.netshot.device.credentials.DeviceSnmpCommunity;
import net.netshot.netshot.work.TaskContext;

/**
 * This class is used to pass SNMP control to JavaScript.
 * <p>
 * Connection (and credential-set fallback) is lazy: nothing happens until the
 * first {@code get}/{@code walk} call actually needs it - see
 * {@link #ensureResolved()}. A community is validated with a lightweight
 * {@code sysUptime.0} probe (as before), and any probe failure is always
 * treated as "this credential didn't work, try the next one" - SNMP has no
 * separate transport-vs-auth distinction, unlike SSH/Telnet.
 */
@Slf4j
public class JsSnmpHelper {

	private final AccessManager accessManager;
	private final Resolution resolution;
	private final boolean autoTryCredentials;
	private final TaskContext taskContext;

	/** The resolved poller, once connected. */
	private Snmp poller;
	/** The resolved community, once connected. */
	protected DeviceSnmpCommunity community;

	/** An error was raised. */
	private boolean errored;

	/**
	 * Instantiate a new JsSnmpHelper object.
	 * @param accessManager the access manager (shared across all clients of this task attempt)
	 * @param accessDefs the ordered list of SNMP accesses to try (e.g. [snmpv3, snmpv2c])
	 * @param autoTryCredentials whether to transparently loop through all candidate
	 *        credential sets (true) or stop at the first failure (false)
	 * @param taskContext The task context
	 */
	public JsSnmpHelper(AccessManager accessManager, List<AccessDefinition> accessDefs,
			boolean autoTryCredentials, TaskContext taskContext) {
		this.accessManager = accessManager;
		this.autoTryCredentials = autoTryCredentials;
		this.taskContext = taskContext;
		this.resolution = accessManager.newResolution(accessDefs, this::buildClient);
	}

	private Client buildClient(AccessDefinition accessDef, net.netshot.netshot.device.credentials.DeviceCredentialSet credentialSet)
			throws IOException {
		DeviceSnmpCommunity resolvedCommunity = (DeviceSnmpCommunity) credentialSet;
		net.netshot.netshot.device.NetworkAddress address = this.accessManager.resolveAddress(accessDef);
		int port = this.accessManager.resolvePort(accessDef);
		this.taskContext.debug("Trying access '{}' ({}) using credentials '{}', at {}:{}.",
			accessDef.getName(), accessDef.getProtocol(), resolvedCommunity.getName(), address.getIp(), port);
		Snmp snmp = new Snmp(address, port, resolvedCommunity);
		try {
			snmp.getAsString("1.3.6.1.2.1.1.3.0"); /* sysUptime.0 */
		}
		catch (IOException e) {
			if (e.getMessage() == null || !e.getMessage().contains("noSuchObject")) {
				snmp.disconnect();
				throw new InvalidCredentialsException("SNMP probe failed: " + e.getMessage());
			}
		}
		return snmp;
	}

	private void ensureResolved() throws IOException {
		if (this.poller != null) {
			return;
		}
		Client client = this.resolution.ensureResolved(this.autoTryCredentials);
		this.poller = (Snmp) client;
		this.community = (DeviceSnmpCommunity) this.resolution.getCurrentCredentialSet();
		// The community was already validated by a sysUptime probe in buildClient(),
		// so a successful resolution here is a reliable "credentials work" signal.
		this.resolution.confirmCredentialWorks();
	}

	/**
	 * Manually advances to the next candidate credential set (only relevant
	 * when {@code autoTryCredentials} is disabled for this client).
	 * @return true if a new client is now connected, false if exhausted
	 */
	@Export
	public boolean tryNextCredentials() {
		boolean success = this.resolution.tryNextCredentials();
		if (success) {
			this.poller = (Snmp) this.resolution.getCurrentClient();
			this.community = (DeviceSnmpCommunity) this.resolution.getCurrentCredentialSet();
		}
		else {
			this.poller = null;
			this.community = null;
		}
		return success;
	}

	/**
	 * Check whether there was an error after the last command.
	 * @return true if there was an error
	 */
	@Export
	public boolean isErrored() {
		return errored;
	}

	/**
	 * SNMP get.
	 * @param oid The OID to look for
	 * @param hex Whether to always render OctetStrings as hex
	 * @return SNMP result
	 * @throws IOException It can happen
	 */
	@Export
	public String getAsString(String oid, boolean hex) throws IOException {
		this.ensureResolved();
		if (this.taskContext.isTracing()) {
			this.taskContext.trace("About to send SNMP GET for OID '{}'.", oid);
		}
		try {
			String value = this.poller.getAsString(oid, hex);
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("Received SNMP response: '{}' = '{}'.", oid, value);
			}
			return value;
		}
		catch (IOException e) {
			log.error("SNMP I/O error.", e);
			this.taskContext.error("I/O error: " + e.getMessage());
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("I/O exception: {}", e.getMessage());
			}
			throw e;
		}
	}

	/**
	 * SNMP walk.
	 * @param oid The base OID to explore.
	 * @param hex Whether to always render OctetStrings as hex
	 * @return a map (OID => value) of results
	 * @throws IOException It can happen
	 */
	@Export
	public ProxyObject walkAsString(String oid, boolean hex) throws IOException {
		this.ensureResolved();
		if (this.taskContext.isTracing()) {
			this.taskContext.trace("About to send SNMP WALK on OID '{}'.", oid);
		}
		try {
			Map<String, String> results = this.poller.walkAsString(oid, hex);
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("Received {} SNMP response(s) for the walk:", results.size());
				for (Map.Entry<String, String> entry : results.entrySet()) {
					this.taskContext.trace("'{}' = '{}'.", entry.getKey(), entry.getValue());
				}
			}
			return ProxyObject.fromMap(new HashMap<String, Object>(results));
		}
		catch (IOException e) {
			log.error("SNMP I/O error.", e);
			this.taskContext.error("I/O error: " + e.getMessage());
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("I/O exception: {}", e.getMessage());
			}
			throw e;
		}
	}

	/**
	 * Pause the thread for the given number of milliseconds.
	 * @param millis The number of milliseconds to wait for
	 */
	@Export
	public void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
		}
	}

}
