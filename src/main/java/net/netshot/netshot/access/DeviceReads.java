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
package net.netshot.netshot.access;

import java.util.List;

import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResultRepository;
import net.netshot.netshot.platform.database.Transactions;

/**
 * The data the device accesses and the device scripts read from the database, each read in its own
 * transaction.
 */
public final class DeviceReads {

	/** The transaction support. */
	private final Transactions transactions;

	/** The device queries. */
	private final DeviceRepository deviceRepository;

	/** The configuration queries. */
	private final ConfigRepository configRepository;

	/** The diagnostic result queries. */
	private final DiagnosticResultRepository diagnosticResultRepository;

	/** The credential set queries. */
	private final CredentialSetRepository credentialSetRepository;

	/**
	 * Instantiates the reads.
	 *
	 * @param transactions the transaction support
	 * @param deviceRepository the device repository
	 * @param configRepository the configuration repository
	 * @param diagnosticResultRepository the diagnostic result repository
	 * @param credentialSetRepository the credential set repository
	 */
	public DeviceReads(Transactions transactions, DeviceRepository deviceRepository, ConfigRepository configRepository,
		DiagnosticResultRepository diagnosticResultRepository, CredentialSetRepository credentialSetRepository) {
		this.transactions = transactions;
		this.deviceRepository = deviceRepository;
		this.configRepository = configRepository;
		this.diagnosticResultRepository = diagnosticResultRepository;
		this.credentialSetRepository = credentialSetRepository;
	}

	/**
	 * Finds a device by ID.
	 *
	 * @param id the device ID
	 * @return the device, or null if there is no such device
	 */
	public Device findDevice(long id) {
		return this.transactions.read(dsl -> this.deviceRepository.find(dsl, id));
	}

	/**
	 * Finds a device by name.
	 *
	 * @param name the device name
	 * @return the device, or null if there is no such device
	 */
	public Device findDevice(String name) {
		return this.transactions.read(dsl -> this.deviceRepository.findByName(dsl, name));
	}

	/**
	 * Finds a configuration, with its long texts.
	 *
	 * @param id the configuration ID
	 * @return the configuration, or null if there is no such configuration
	 */
	public Config findConfig(long id) {
		return this.transactions.read(dsl -> this.configRepository.find(dsl, id, true));
	}

	/**
	 * Finds the diagnostic results of a device.
	 *
	 * @param deviceId the device ID
	 * @return the results
	 */
	public List<DiagnosticResult> findDiagnosticResults(long deviceId) {
		return this.transactions.read(dsl -> this.diagnosticResultRepository.findByDevice(dsl, deviceId));
	}

	/**
	 * Finds the global credential sets usable on the devices of a domain.
	 *
	 * @param domainId the domain ID, null for the devices without domain
	 * @return the credential sets
	 */
	public List<DeviceCredentialSet> findUsableCredentialSets(Long domainId) {
		return this.transactions.read(dsl -> this.credentialSetRepository.findUsableInDomain(dsl, domainId));
	}
}
