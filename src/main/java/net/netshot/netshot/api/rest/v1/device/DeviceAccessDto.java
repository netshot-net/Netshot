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
package net.netshot.netshot.api.rest.v1.device;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.credentials.CredentialSetDto;
import net.netshot.netshot.inventory.device.DeviceAccess.SshHostKeyVerification;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * The settings of a device for one way of accessing it (CLI, SNMP, HTTP...).
 *
 * @param accessName the name of the access, as defined by the driver
 * @param address the address to connect to (the management address if null)
 * @param port the port (the default one if null)
 * @param globalCredentialSet the global credential set to use
 * @param specificCredentialSet the credential set specific to this device access (without its secrets)
 * @param sshHostKeyVerification how the SSH host keys are verified
 * @param sshTrustedHostKeys the trusted SSH host keys
 * @param sshTrustedHostKeysSince when the SSH host keys were trusted
 * @param httpsCaTrustMode how the HTTPS certificates are verified
 * @param httpsCustomCaCertificate the custom CA certificate
 */
@Schema(name = "DeviceAccess", description = "Per-access settings of a device")
public record DeviceAccessDto(
	String accessName,
	String address,
	Integer port,
	@Ref CredentialSetDto globalCredentialSet,
	CredentialSetDto specificCredentialSet,
	SshHostKeyVerification sshHostKeyVerification,
	String sshTrustedHostKeys,
	Instant sshTrustedHostKeysSince,
	HttpsCaTrustMode httpsCaTrustMode,
	String httpsCustomCaCertificate) {
}
