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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.jooq.DSLContext;

import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.CredentialSets;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.OptionDefinition;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
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
	 * present. If a global credential set is pinned, the access name must be
	 * a real access declared by the driver and the credential's type must match
	 * that access's declared credential family. A submitted
	 * {@code globalCredentialSet} is resolved here (by id, through the
	 * repository) to the stored credential set. The specific credential sets
	 * are given apart (stored by {@link #applySpecificCredentials}): an access
	 * can't have both a global and a specific one.
	 * @param credentialSetRepository the repository used to resolve a submitted globalCredentialSet id
	 * @param dsl the jOOQ context of the repository lookups
	 * @param driver the device's driver, or null if not known yet (e.g. an
	 *        autodiscovering device) - credential pins are rejected in that case
	 * @param accesses the accesses to validate (may be null)
	 * @param specifics the specific credential sets of the accesses (may be null)
	 * @return the accesses, normalized (default modes, normalized SSH host keys, resolved global
	 *         credential sets), or null if none was given
	 * @throws NetshotException if any entry is invalid
	 * @throws IllegalArgumentException if an access carries a specific credential set
	 */
	public static List<DeviceAccess> validateAccesses(CredentialSetRepository credentialSetRepository, DSLContext dsl,
			DeviceDriver driver, List<DeviceAccess> accesses, List<AccessSpecificCredential> specifics) {
		if (accesses == null) {
			return null;
		}
		Set<String> specificNames = new HashSet<>();
		if (specifics != null) {
			specifics.forEach(specific -> specificNames.add(specific.accessName()));
		}
		List<DeviceAccess> validated = new ArrayList<>(accesses.size());
		for (DeviceAccess input : accesses) {
			if (input.accessName() == null || input.accessName().isEmpty()) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
					"Missing access name in one of the device accesses.");
			}
			if (input.address() != null && !input.address().isEmpty()) {
				validateMgmtAddress(input.address(), true);
			}
			if (input.port() != null && (input.port() < 1 || input.port() > 65535)) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_PORT,
					"Invalid port for access '" + input.accessName() + "'.");
			}
			DeviceAccess access = input.toBuilder()
				.sshHostKeyVerification(input.sshHostKeyVerification() == null
					? DeviceAccess.SshHostKeyVerification.TRUST_KNOWN : input.sshHostKeyVerification())
				.sshTrustedHostKeys(normalizeSshTrustedHostKeys(input.sshTrustedHostKeys(), input.accessName()))
				.httpsCaTrustMode(input.httpsCaTrustMode() == null ? HttpsCaTrustMode.SYSTEM_TRUSTSTORE : input.httpsCaTrustMode())
				.build();
			if (access.httpsCaTrustMode() == HttpsCaTrustMode.CUSTOM_CA) {
				if (access.httpsCustomCaCertificate() == null || access.httpsCustomCaCertificate().isBlank()) {
					throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
						"Access '" + access.accessName() + "' requires a custom CA certificate "
							+ "when the HTTPS trust mode is CUSTOM_CA.");
				}
				CaCertificates.validateCustomCa(access.httpsCustomCaCertificate(),
					"access '" + access.accessName() + "'");
			}
			if (access.specificCredentialSet() != null) {
				throw new IllegalArgumentException("The specific credential set of access '" + access.accessName() + "' must be given apart.");
			}
			if (access.globalCredentialSet() == null) {
				validated.add(access);
				continue;
			}
			if (specificNames.contains(access.accessName())) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS,
					"Cannot set both a global and a specific credential set for access '" + access.accessName() + "'.");
			}
			if (driver == null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS,
					"Cannot pin credentials for access '" + access.accessName() + "' without a known device type.");
			}
			DeviceDriver.AccessDefinition accessDef = driver.getAccessDefinition(access.accessName());
			if (accessDef == null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
					"Unknown access '" + access.accessName() + "' for this device type.");
			}
			DeviceCredentialSet global = credentialSetRepository.find(dsl, access.globalCredentialSet().id());
			if (global == null || global.deviceSpecific()) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS,
					"Invalid global credential set for access '" + access.accessName() + "'.");
			}
			if (!accessDef.getCredentialClass().isInstance(global)) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE,
					"The selected credential set is not compatible with access '" + access.accessName() + "'.");
			}
			validated.add(access.toBuilder().globalCredentialSet(global).build());
		}
		return validated;
	}

	/**
	 * Stores the specific credential sets of the accesses: each one is created, or merged into the current specific
	 * credential set of the access of the same name when they are of the same type (a definition then keeps the
	 * secrets it leaves out, a legacy credential set follows the rules of the legacy API). To call once the accesses
	 * are validated: an access which gets no specific credential set here has none.
	 *
	 * @param credentialSetRepository the credential set repository
	 * @param dsl the jOOQ context
	 * @param driver the device's driver
	 * @param current the current accesses of the device (none for a new device)
	 * @param accesses the validated accesses of the device after the change (may be null)
	 * @param specifics the specific credential sets (may be null)
	 * @return the accesses, with their stored specific credential sets
	 * @throws NetshotException if a credential set is invalid, doesn't match an access, or doesn't fit its access
	 */
	public static List<DeviceAccess> applySpecificCredentials(CredentialSetRepository credentialSetRepository, DSLContext dsl,
			DeviceDriver driver, List<DeviceAccess> current, List<DeviceAccess> accesses, List<AccessSpecificCredential> specifics) {
		if (specifics == null || specifics.isEmpty()) {
			return accesses;
		}
		List<DeviceAccess> result = accesses == null ? new ArrayList<>() : new ArrayList<>(accesses);
		for (AccessSpecificCredential specific : specifics) {
			int index = -1;
			for (int i = 0; i < result.size(); i++) {
				if (specific.accessName().equals(result.get(i).accessName())) {
					index = i;
				}
			}
			if (index < 0) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
					"Unknown access '" + specific.accessName() + "' for the specific credential set.");
			}
			DeviceAccess access = result.get(index);
			if (access.globalCredentialSet() != null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS,
					"Cannot set both a global and a specific credential set for access '" + access.accessName() + "'.");
			}
			if (driver == null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS,
					"Cannot pin credentials for access '" + access.accessName() + "' without a known device type.");
			}
			DeviceDriver.AccessDefinition accessDef = driver.getAccessDefinition(access.accessName());
			if (accessDef == null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER,
					"Unknown access '" + access.accessName() + "' for this device type.");
			}
			DeviceCredentialSet existing = current.stream()
				.filter(a -> a.accessName().equals(access.accessName()))
				.reduce((_, second) -> second)
				.map(DeviceAccess::specificCredentialSet)
				.orElse(null);
			DeviceCredentialSet stored = switch (specific) {
				case AccessSpecificCredential.Definition d -> CredentialSets.applySpecificDefinition(dsl, credentialSetRepository, existing,
					d.definition(), accessDef.getCredentialClass());
				case AccessSpecificCredential.Legacy l -> {
					if (!accessDef.getCredentialClass().isInstance(l.credentialSet())) {
						throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE,
							"The specific credential set is not compatible with access '" + access.accessName() + "'.");
					}
					yield CredentialSets.applySpecificCredentialSet(dsl, credentialSetRepository, existing, l.credentialSet());
				}
			};
			result.set(index, access.toBuilder().specificCredentialSet(stored).build());
		}
		return result;
	}

	/**
	 * Validates per-device Options values against the driver's declared
	 * {@link OptionDefinition}s: every key must be a known option, and the value
	 * must be properly typed for it (a boolean for BOOLEAN, a string for
	 * TEXT - one of {@code choices} when declared) - values are stored as real JSON,
	 * not stringified (see {@link Device#options()}). A BOOLEAN option may also be
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
