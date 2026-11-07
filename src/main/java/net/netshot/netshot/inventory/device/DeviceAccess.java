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
package net.netshot.netshot.inventory.device;

import java.time.Instant;

import lombok.Builder;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * Per-(device, access) configuration: an optional address and/or TCP port
 * override for one of the driver's declared accesses (e.g. "ssh", "telnet",
 * "https", "alternateSsh"), keyed by access name, plus an optional credential
 * pin for that same access. The row's mere existence means the access is
 * used at all - an access with no {@code DeviceAccess} row is never tried
 * (see {@code AccessManager}).
 * <p>
 * Credential pinning is derived purely from which of {@link #globalCredentialSet}
 * / {@link #specificCredentialSet} is set (never both - REST-side validation
 * rejects that): if neither is set, the access falls through to the device's
 * own default credential resolution (see {@code AccessManager}).
 * <p>
 * (device, accessName) is naturally unique and is used directly as the
 * primary key - no separate surrogate id.
 *
 * @param accessName the name of the access, as declared by the driver
 * @param address the address to connect to instead of the management address of the device, or null
 * @param port the TCP port to connect to instead of the default port of the access, or null
 * @param globalCredentialSet a pinned, existing, shared credential set for this access only; removing
 *        the access never removes it
 * @param specificCredentialSet a pinned credential set owned by this access (removed with it)
 * @param sshHostKeyVerification the SSH host key verification mode (only meaningful for a SSH access)
 * @param sshTrustedHostKeys the trusted SSH host keys for this access, known_hosts-style: one
 *        "{@code <algorithm> <base64-key>}" entry per line (e.g.
 *        {@code ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI...}), no hostname prefix.
 *        When empty, the first host key presented is learned and appended
 *        automatically; once at least one line is present (whether learned or
 *        entered manually), only a matching key is ever accepted - a key for a
 *        not-yet-seen algorithm is rejected rather than silently added.
 * @param sshTrustedHostKeysSince when the trusted SSH host keys were last learned or edited (display only)
 * @param httpsCaTrustMode how the TLS certificate of the device is checked (for a HTTPS access)
 * @param httpsCustomCaCertificate the CA certificate(s) to trust, PEM, in the custom CA mode
 */
@Builder(toBuilder = true)
public record DeviceAccess(
	String accessName,
	String address,
	Integer port,
	DeviceCredentialSet globalCredentialSet,
	DeviceCredentialSet specificCredentialSet,
	SshHostKeyVerification sshHostKeyVerification,
	String sshTrustedHostKeys,
	Instant sshTrustedHostKeysSince,
	HttpsCaTrustMode httpsCaTrustMode,
	String httpsCustomCaCertificate) {

	/**
	 * SSH server host key verification mode for this access (meaningful only
	 * when the access's protocol is SSH).
	 */
	public enum SshHostKeyVerification {
		/** No verification - any presented host key is accepted (historical behavior). */
		TRUST_ANY,
		/**
		 * Only host keys listed in {@link DeviceAccess#sshTrustedHostKeys} are accepted - whether that
		 * list was learned automatically or entered manually is not tracked as a separate
		 * setting: as long as {@link DeviceAccess#sshTrustedHostKeys} is empty, the first host key
		 * presented is learned and stored; once at least one key is on record, only a
		 * matching key is ever accepted (a key for a not-yet-seen algorithm is never
		 * silently learned in addition to an existing one).
		 */
		TRUST_KNOWN
	}

	/**
	 * Builds a bare access (no override, no pinned credentials, default modes: any SSH host key,
	 * the system trust store).
	 *
	 * @param accessName the name of the access
	 * @return the access
	 */
	public static DeviceAccess of(String accessName) {
		return DeviceAccess.builder()
			.accessName(accessName)
			.sshHostKeyVerification(SshHostKeyVerification.TRUST_ANY)
			.httpsCaTrustMode(HttpsCaTrustMode.SYSTEM_TRUSTSTORE)
			.build();
	}
}
