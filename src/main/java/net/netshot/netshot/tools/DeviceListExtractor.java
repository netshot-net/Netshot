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
package net.netshot.netshot.tools;

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.boot.Application;
import net.netshot.netshot.boot.Bootstrap;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.credentials.DeviceCliAccount;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSnmpCommunity;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.vault.SecretResolver;
import net.netshot.netshot.vault.VaultException;
import net.netshot.netshot.platform.runtime.BuildInfo;

@Slf4j
public final class DeviceListExtractor {

	private DeviceListExtractor() {
	}

	/**
	 * Resolves a credential field's actual value (local, or from Vault),
	 * logging a warning and returning an empty string on failure rather than
	 * aborting the whole export for one bad/unreachable Vault-backed field.
	 * @param secret the vaultable secret to resolve
	 * @param device the device the secret belongs to, used for the warning log message
	 * @param secrets resolves the secrets (local, or from Vault)
	 * @return the resolved value, or an empty string on failure
	 */
	private static String resolveSecret(Secret secret, Device device, SecretResolver secrets) {
		try {
			String value = secrets.resolve(secret);
			return value == null ? "" : value;
		}
		catch (VaultException e) {
			log.warn("Unable to resolve a Vault-backed credential for device {}: {}", device.name(), e.getMessage());
			return "";
		}
	}

	/**
	 * Function entry point.
	 * @param args the program arguments
	 */
	public static void main(String[] args) {
		System.out.println("Netshot Device List Extractor -- extracts and formats device data.");
		System.out.println(String.format("Based on Netshot version %s.", BuildInfo.VERSION));
		log.info("Starting now.");

		StringBuffer output = new StringBuffer();

		try {
			ConfigStore config = ConfigStore.ofFiles(ToolCommandLine.configFiles(args));
			Bootstrap.prepare(config);
			try (Application application = Application.create(config)) {
				log.info("Initializing access to the database.");
				application.start("database");
				Wiring wiring = application.wiring();

				log.info("Requesting data from the DB...");
				DeviceRepository deviceRepository = wiring.core().repositories().device();
				List<Device> devices = wiring.core().transactions().read(dsl -> deviceRepository.findAll(dsl,
					dsl.select(DEVICE.ID).from(DEVICE).where(DEVICE.STATUS.eq(Device.Status.INPRODUCTION.name())).fetch(DEVICE.ID)));
				for (Device device : devices) {
					DeviceCliAccount cliAccount = null;
					DeviceSnmpCommunity community = null;

					for (String accessName : List.of("telnet", "ssh")) {
						DeviceAccess access = device.getDeviceAccess(accessName);
						if (access == null) {
							continue;
						}
						DeviceCredentialSet pinned = access.specificCredentialSet() != null
							? access.specificCredentialSet() : access.globalCredentialSet();
						if (pinned instanceof DeviceCliAccount ca) {
							cliAccount = ca;
						}
					}
					for (String accessName : List.of("snmpv1", "snmpv2c", "snmpv3")) {
						DeviceAccess access = device.getDeviceAccess(accessName);
						if (access == null) {
							continue;
						}
						DeviceCredentialSet pinned = access.specificCredentialSet() != null
							? access.specificCredentialSet() : access.globalCredentialSet();
						if (pinned instanceof DeviceSnmpCommunity sc) {
							community = sc;
						}
					}
					if (cliAccount == null) {
						log.warn("No CLI account found for device {}.", device.name());
					}
					if (community == null) {
						log.warn("No SNMP community found for device {}.", device.name());
					}
					List<String> fields = new ArrayList<>();
					String ipAddress;
					try {
						ipAddress = InetAddress.getByName(device.mgmtAddress()).getHostAddress();
					}
					catch (UnknownHostException e) {
						ipAddress = device.mgmtAddress();
					}
					fields.add(ipAddress);					// Col. 1 = IP Address <<<
					fields.add(device.name());			// Col. 2 = Host Name <<<
					fields.add("");							// Col. 3 = Domain Name
					fields.add("");							// Col. 4 = Device Identity
					fields.add("");							// Col. 5 = Display Name
					fields.add("");							// Col. 6 = SysObjectID
					fields.add("");							// Col. 7 = DCR Device Type
					fields.add("");							// Col. 8 = MDF Type
					fields.add(community == null ? "" : resolveSecret(community.community(), device, wiring.core().vaultManager()));			// Col. 9 = SNMP RO <<<
					fields.add("");							// Col. 10 = SNMP RW
					fields.add("");							// Col. 11 = SNMPv3 User Name
					fields.add("");							// Col. 12 = SNMPv3 Auth Pass 
					fields.add("");							// Col. 13 = SNMPv3 Engine ID
					fields.add("");							// Col. 14 = SNMPv3 Auth Algorithm
					fields.add("");							// Col. 15 = RX Boot Mode User
					fields.add("");							// Col. 16 = RX Boot Mode Pass
					fields.add(cliAccount == null ? "" : resolveSecret(cliAccount.username(), device, wiring.core().vaultManager()));	// Col. 17 = Primary User <<<
					fields.add(cliAccount == null ? "" : resolveSecret(cliAccount.password(), device, wiring.core().vaultManager()));	// Col. 18 = Primary Pass <<<
					fields.add(cliAccount == null ? "" : resolveSecret(cliAccount.superPassword(), device, wiring.core().vaultManager()));	// Col. 19 = Primary Enable Pass <<<
					fields.add("");							// Col. 20 = HTTP User
					fields.add("");							// Col. 21 = HTTP Pass
					fields.add("");							// Col. 22 = HTTP Mode
					fields.add("");							// Col. 23 = HTTP Port
					fields.add("");							// Col. 24 = HTTPS Port
					fields.add("");							// Col. 25 = Cert Common Name
					fields.add("");							// Col. 26 = Secondary User
					fields.add("");							// Col. 27 = Secondary Pass
					fields.add("");							// Col. 28 = Secondary Enable Pass
					fields.add("");							// Col. 29 = Secondary HTTP User
					fields.add("");							// Col. 30 = Secondary HTTP Pass
					fields.add("");							// Col. 31 = SNMPv3 Priv Algo
					fields.add("");							// Col. 32 = SNMPv3 Priv Pass
					fields.add("");							// Col. 33 = User Field 1
					fields.add("");							// Col. 34 = User Field 2
					fields.add("");							// Col. 35 = User Field 3
					fields.add("");							// Col. 36 = User Field 4
					output.append(String.join(";", fields));
					output.append("\n");
				}
				System.out.println(output);

			}
		}
		catch (Exception e) {
			System.err.println("NETSHOT FATAL ERROR: " + e.getMessage());
			System.exit(1);
		}
	}

}
