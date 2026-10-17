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
package net.netshot.netshot.api.rest.v0.device;

import java.io.IOException;
import java.util.Date;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import net.netshot.netshot.access.DeviceAccess.SshHostKeyVerification;
import net.netshot.netshot.api.rest.v0.credentials.CredentialSetDto;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * The settings of one access (SSH, Telnet, HTTPS...) of a device. The same shape is written
 * (in the device representation) and read (in the requests which add or edit a device).
 *
 * <p>When read, the {@code sshHostKeyVerification} is {@code TRUST_ANY} if the property is absent
 * from the request, and {@code null} if it is explicitly null (the device service then applies
 * {@code TRUST_KNOWN}): see {@link SshHostKeyVerificationDeserializer}.</p>
 *
 * @param accessName the access name, as declared by the device driver
 * @param address the address to connect to instead of the management address
 * @param port the port to connect to instead of the default one
 * @param globalCredentialSet the shared credential set pinned to the access (only its ID is read)
 * @param specificCredentialSet the credential set specific to the access
 * @param sshHostKeyVerification the SSH host key verification mode
 * @param sshTrustedHostKeys the trusted SSH host keys, one "algorithm key" entry per line
 * @param sshTrustedHostKeysSince when the trusted host keys were last learned or edited (not read)
 * @param httpsCaTrustMode the HTTPS certificate trust mode
 * @param httpsCustomCaCertificate the PEM trust anchor certificate(s) for the custom CA trust mode
 */
public record DeviceAccessDto(
	String accessName,
	String address,
	Integer port,
	CredentialSetDto globalCredentialSet,
	CredentialSetDto specificCredentialSet,
	@JsonDeserialize(using = DeviceAccessDto.SshHostKeyVerificationDeserializer.class) SshHostKeyVerification sshHostKeyVerification,
	String sshTrustedHostKeys,
	Date sshTrustedHostKeysSince,
	HttpsCaTrustMode httpsCaTrustMode,
	String httpsCustomCaCertificate) {

	/**
	 * Reads the SSH host key verification mode, giving a different meaning to an absent property
	 * (the default mode, {@code TRUST_ANY}) and to an explicit null (no mode: the device service
	 * applies {@code TRUST_KNOWN}), as the request used to be read.
	 */
	public static final class SshHostKeyVerificationDeserializer extends JsonDeserializer<SshHostKeyVerification> {

		@Override
		public SshHostKeyVerification deserialize(JsonParser parser, DeserializationContext context) throws IOException {
			String text = parser.getValueAsString();
			try {
				return SshHostKeyVerification.valueOf(text);
			}
			catch (IllegalArgumentException | NullPointerException e) {
				return (SshHostKeyVerification) context.handleWeirdStringValue(SshHostKeyVerification.class, text,
					"not one of the values accepted for Enum class");
			}
		}

		@Override
		public SshHostKeyVerification getNullValue(DeserializationContext context) {
			return null;
		}

		@Override
		public Object getAbsentValue(DeserializationContext context) {
			return SshHostKeyVerification.TRUST_ANY;
		}
	}
}
