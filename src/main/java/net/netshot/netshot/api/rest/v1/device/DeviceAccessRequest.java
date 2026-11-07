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

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.api.rest.v1.RefId;
import net.netshot.netshot.inventory.device.DeviceAccess.SshHostKeyVerification;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * The settings of one access of a device.
 *
 * @param accessName the name of the access, as the driver declares it (such as {@code ssh} or {@code snmp})
 * @param address the address to use for this access instead of the management address
 * @param port the port to use instead of the default one
 * @param globalCredentialSet the global credential set to use, instead of trying the ones of the domain
 * @param specificCredentialSet the credential set specific to the device, instead of a global one
 * @param sshHostKeyVerification how to verify the SSH host key (trust known keys by default)
 * @param sshTrustedHostKeys the trusted SSH host keys (one per line)
 * @param httpsCaTrustMode how to verify the HTTPS server certificate (the system trust store by default)
 * @param httpsCustomCaCertificate the CA certificate to trust, with the {@code CUSTOM_CA} mode
 */
@Schema(name = "DeviceAccessRequest", description = "Settings of one access of a device")
@ReadOnlyFrom(DeviceAccessDto.class)
public record DeviceAccessRequest(
	@JsonProperty(required = true) String accessName,
	String address,
	Integer port,
	RefId globalCredentialSet,
	DeviceCredentialSetRequest specificCredentialSet,
	SshHostKeyVerification sshHostKeyVerification,
	String sshTrustedHostKeys,
	HttpsCaTrustMode httpsCaTrustMode,
	String httpsCustomCaCertificate) {
}
