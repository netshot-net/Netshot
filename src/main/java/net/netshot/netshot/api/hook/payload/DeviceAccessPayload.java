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
package net.netshot.netshot.api.hook.payload;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonView;

import net.netshot.netshot.access.DeviceAccess.SshHostKeyVerification;
import net.netshot.netshot.platform.rest.RestViews.HookView;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * The settings of one access (SSH, Telnet, HTTPS...) of a device, as nested in the webhook payloads.
 *
 * @param accessName the access name, as declared by the device driver
 * @param address the address to connect to instead of the management address
 * @param port the port to connect to instead of the default one
 * @param globalCredentialSet the shared credential set pinned to the access
 * @param specificCredentialSet the credential set specific to the access
 * @param sshHostKeyVerification the SSH host key verification mode
 * @param sshTrustedHostKeys the trusted SSH host keys, one "algorithm key" entry per line
 * @param sshTrustedHostKeysSince when the trusted host keys were last learned or edited
 * @param httpsCaTrustMode the HTTPS certificate trust mode
 * @param httpsCustomCaCertificate the PEM trust anchor certificate(s) for the custom CA trust mode
 */
@JsonView(HookView.class)
public record DeviceAccessPayload(
	String accessName,
	String address,
	Integer port,
	CredentialSetPayload globalCredentialSet,
	CredentialSetPayload specificCredentialSet,
	SshHostKeyVerification sshHostKeyVerification,
	String sshTrustedHostKeys,
	Date sshTrustedHostKeysSince,
	HttpsCaTrustMode httpsCaTrustMode,
	String httpsCustomCaCertificate) {
}
