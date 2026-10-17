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
package net.netshot.netshot.inventory.device;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.hibernate.Session;

import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.OptionDefinition;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.tls.CaCertificates;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * Validation of the user-provided data of a device (management address, tags, accesses,
 * driver options), shared by the device use cases. Every check failure is reported as a
 * {@link NetshotException} carrying the {@link ErrorCode} the API clients rely on.
 *
 * <p>These are pure functions of their arguments (the access validation also resolves
 * the credential set pins through the given repository).</p>
 */
public final class DeviceValidation {

	/** Strict IPv4 dotted-quad pattern (each octet 0-255), to detect an IPv4 literal without ever risking a DNS lookup. */
	private static final Pattern IPV4_LITERAL_PATTERN = Pattern.compile(
		"^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

	/** RFC-1123-ish hostname/FQDN pattern. */
	private static final Pattern HOSTNAME_PATTERN = Pattern.compile(
		"^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$");

	/** Recognized SSH public key algorithm name prefixes, to locate the algorithm token in a pasted line. */
	private static final Pattern SSH_KEY_TYPE_PATTERN = Pattern.compile(
		"^(ssh-|ecdsa-sha2-|rsa-sha2-|sk-ssh-|sk-ecdsa-).+");

	private DeviceValidation() {
		// Static helpers only
	}

	/**
	 * Validates device tags: each tag must be a non-blank string
	 * of at most {@link Device#MAX_TAG_LENGTH} characters (once trimmed).
	 * @param tags the tags to validate (may be null)
	 * @throws NetshotException if any tag is invalid
	 */
	public static void validateTags(List<String> tags) {
		if (tags == null) {
			return;
		}
		for (String tag : tags) {
			if (Device.normalizeTag(tag) == null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
					"Invalid device tag (tags must be non-empty and at most %d characters long).".formatted(Device.MAX_TAG_LENGTH));
			}
		}
	}

	/**
	 * Validates a device management/connect address, accepting an IPv4 literal, an IPv6
	 * literal, or a syntactically valid hostname/FQDN. Never performs a DNS lookup:
	 * hostnames are only resolved later, at actual connect time.
	 *
	 * @param address the address to validate
	 * @param allowLoopback whether a loopback address should be accepted (used for the
	 *                      optional "connect address" override, which may point to a
	 *                      local port-forwarded tunnel)
	 * @throws NetshotException if the address is malformed or invalid
	 */
	public static void validateMgmtAddress(String address, boolean allowLoopback) {
		if (address == null || "".equals(address)) {
			throw new NetshotException(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, "Malformed IP address");
		}
		if (IPV4_LITERAL_PATTERN.matcher(address).matches()) {
			try {
				Network4Address v4Address = new Network4Address(address);
				if (!v4Address.isNormalUnicast() && !(allowLoopback && v4Address.isLoopback())) {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, "Invalid IP address");
				}
			}
			catch (UnknownHostException e) {
				throw new NetshotException(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, "Malformed IP address");
			}
		}
		else if (address.contains(":")) {
			try {
				Network6Address v6Address = new Network6Address(address);
				if (v6Address.isMulticast()) {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_IP_ADDRESS, "Invalid IP address");
				}
			}
			catch (UnknownHostException e) {
				throw new NetshotException(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, "Malformed IP address");
			}
		}
		else if (!HOSTNAME_PATTERN.matcher(address).matches()) {
			throw new NetshotException(ErrorCode.NETSHOT_MALFORMED_IP_ADDRESS, "Malformed IP address or hostname");
		}
	}

	/**
	 * Validates a list of per-(device, access) configurations submitted by a
	 * device create/update request: address must be a valid address/hostname
	 * if set, port must be in the 1-65535 range if set, access name must be
	 * present. If a credential is pinned (at most one of globalCredentialSet/
	 * specificCredentialSet may be set - both is rejected), the access name
	 * must be a real access declared by the driver and the credential's
	 * runtime class must match that access's declared credential family. A
	 * submitted {@code globalCredentialSet} is resolved here (by id, through the
	 * repository) to the managed, persistent entity and written back onto the same
	 * {@link DeviceAccess} instance, so the caller can pass the list straight into
	 * {@link Device#replaceAccesses} afterwards.
	 * @param repository the repository used to resolve a submitted globalCredentialSet id
	 * @param session the Hibernate session of the repository lookups
	 * @param driver the device's driver, or null if not known yet (e.g. an
	 *        autodiscovering device) - credential pins are rejected in that case
	 * @param accesses the accesses to validate (may be null)
	 * @throws NetshotException if any entry is invalid
	 */
	public static void validateAccesses(DeviceRepository repository, Session session,
			DeviceDriver driver, List<DeviceAccess> accesses) {
		if (accesses == null) {
			return;
		}
		for (DeviceAccess access : accesses) {
			if (access.getAccessName() == null || access.getAccessName().isEmpty()) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
					"Missing access name in one of the device accesses.");
			}
			if (access.getAddress() != null && !access.getAddress().isEmpty()) {
				validateMgmtAddress(access.getAddress(), true);
			}
			if (access.getPort() != null && (access.getPort() < 1 || access.getPort() > 65535)) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_PORT,
					"Invalid port for access '" + access.getAccessName() + "'.");
			}
			if (access.getSshHostKeyVerification() == null) {
				access.setSshHostKeyVerification(DeviceAccess.SshHostKeyVerification.TRUST_KNOWN);
			}
			access.setSshTrustedHostKeys(
				normalizeSshTrustedHostKeys(access.getSshTrustedHostKeys(), access.getAccessName()));
			if (access.getHttpsCaTrustMode() == null) {
				access.setHttpsCaTrustMode(HttpsCaTrustMode.SYSTEM_TRUSTSTORE);
			}
			if (access.getHttpsCaTrustMode() == HttpsCaTrustMode.CUSTOM_CA) {
				if (access.getHttpsCustomCaCertificate() == null || access.getHttpsCustomCaCertificate().isBlank()) {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
						"Access '" + access.getAccessName() + "' requires a custom CA certificate "
							+ "when the HTTPS trust mode is CUSTOM_CA.");
				}
				CaCertificates.validateCustomCa(access.getHttpsCustomCaCertificate(),
					"access '" + access.getAccessName() + "'");
			}
			if (access.getGlobalCredentialSet() == null && access.getSpecificCredentialSet() == null) {
				continue;
			}
			if (access.getGlobalCredentialSet() != null && access.getSpecificCredentialSet() != null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS,
					"Cannot set both a global and a specific credential set for access '" + access.getAccessName() + "'.");
			}
			if (driver == null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS,
					"Cannot pin credentials for access '" + access.getAccessName() + "' without a known device type.");
			}
			DeviceDriver.AccessDefinition accessDef = driver.getAccessDefinition(access.getAccessName());
			if (accessDef == null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
					"Unknown access '" + access.getAccessName() + "' for this device type.");
			}
			Class<? extends DeviceCredentialSet> credentialClass = accessDef.getCredentialClass();
			if (access.getGlobalCredentialSet() != null) {
				DeviceCredentialSet global = repository.findCredentialSet(session, access.getGlobalCredentialSet().getId());
				if (global == null || global.isDeviceSpecific()) {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS,
						"Invalid global credential set for access '" + access.getAccessName() + "'.");
				}
				if (!credentialClass.isInstance(global)) {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE,
						"The selected credential set is not compatible with access '" + access.getAccessName() + "'.");
				}
				access.setGlobalCredentialSet(global);
			}
			else if (!credentialClass.isInstance(access.getSpecificCredentialSet())) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE,
					"The specific credential set is not compatible with access '" + access.getAccessName() + "'.");
			}
		}
	}

	/**
	 * Validates per-device Options values against the driver's declared
	 * {@link OptionDefinition}s: every key must be a known option, and the value
	 * must be properly typed for it (a boolean for BOOLEAN, a string for
	 * TEXT - one of {@code choices} when declared) - values are stored as real JSON,
	 * not stringified (see {@link Device#getOptions()}). A BOOLEAN option may also be
	 * given as the text {@code true} or {@code false} (the only way in XML, where all
	 * the values are text): it is then converted to a boolean.
	 * @param driver the device's driver, or null if not known yet - a non-empty
	 *        options map is rejected in that case
	 * @param options the options to validate (may be null)
	 * @return the options to store (the given ones, with the converted booleans), or the given map if null or empty
	 * @throws NetshotException if any entry is invalid
	 */
	public static Map<String, Object> validateOptions(DeviceDriver driver, Map<String, Object> options) {
		if (options == null || options.isEmpty()) {
			return options;
		}
		Map<String, Object> normalized = new LinkedHashMap<>();
		if (driver == null) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
				"Cannot set options without a known device type.");
		}
		for (Map.Entry<String, Object> entry : options.entrySet()) {
			OptionDefinition definition = driver.getOptions().get(entry.getKey());
			if (definition == null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
					"Unknown option '%s' for this device type.".formatted(entry.getKey()));
			}
			Object value = entry.getValue();
			normalized.put(entry.getKey(), value);
			if (value == null) {
				continue;
			}
			switch (definition.getType()) {
				case BOOLEAN:
					if ("true".equals(value) || "false".equals(value)) {
						value = Boolean.valueOf((String) value);
						normalized.put(entry.getKey(), value);
					}
					if (!(value instanceof Boolean)) {
						throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
							"Option '%s' should be a boolean.".formatted(entry.getKey()));
					}
					break;
				case TEXT:
					if (!(value instanceof String)) {
						throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
							"Option '%s' should be a string.".formatted(entry.getKey()));
					}
					if (definition.getChoices() != null && !definition.getChoices().contains(value)) {
						throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
							"Option '%s' is not one of the allowed choices.".formatted(entry.getKey()));
					}
					break;
				default:
					break;
			}
		}
		return normalized;
	}

	/**
	 * Normalizes a block of admin-pasted SSH host key entries into the canonical, storage-ready
	 * form: one "{@code <algorithm> <base64-key>}" per line, no hostname prefix or trailing
	 * comment (both optional and tolerated on input, e.g. pasted straight from {@code ssh-keyscan}
	 * or a {@code known_hosts} file), each validated to actually decode as a public key.
	 * @param raw the raw, possibly null/blank, pasted block
	 * @param accessName the access name (for the error message)
	 * @return the normalized block, or null if {@code raw} had no usable entry
	 * @throws NetshotException if any non-blank, non-comment line is malformed
	 */
	static String normalizeSshTrustedHostKeys(String raw, String accessName) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		List<String> normalized = new ArrayList<>();
		for (String rawLine : raw.split("\\R")) {
			String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] tokens = line.split("\\s+");
			int algorithmIndex = -1;
			for (int i = 0; i < tokens.length; i++) {
				if (SSH_KEY_TYPE_PATTERN.matcher(tokens[i]).matches()) {
					algorithmIndex = i;
					break;
				}
			}
			if (algorithmIndex < 0 || algorithmIndex + 1 >= tokens.length) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
					"Invalid SSH host key entry for access '" + accessName + "': '" + line + "'.");
			}
			String algorithm = tokens[algorithmIndex];
			String keyData = tokens[algorithmIndex + 1];
			try {
				PublicKeyEntry.parsePublicKeyEntry(algorithm + " " + keyData)
					.resolvePublicKey(null, Collections.emptyMap(), null);
			}
			catch (Exception e) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
					"Invalid SSH host key entry for access '" + accessName + "': " + e.getMessage());
			}
			normalized.add(algorithm + " " + keyData);
		}
		return normalized.isEmpty() ? null : String.join("\n", normalized);
	}
}
