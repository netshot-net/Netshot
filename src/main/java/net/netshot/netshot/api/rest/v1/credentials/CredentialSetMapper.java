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
package net.netshot.netshot.api.rest.v1.credentials;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

import net.netshot.netshot.api.rest.v1.RefId;
import net.netshot.netshot.api.rest.v1.Secret;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.domain.DomainMapper;
import net.netshot.netshot.api.rest.v1.vault.VaultInstanceDto;
import net.netshot.netshot.api.rest.v1.vault.VaultInstanceMapper;
import net.netshot.netshot.credentials.CredentialSetDefinition;
import net.netshot.netshot.credentials.CredentialSetDefinition.Kind;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.vault.SecretChange;
import net.netshot.netshot.vault.VaultableSecret;

/**
 * Maps the credential sets between the domain records and their v1 representation. The representation never
 * holds a secret, only whether it is set and where a Vault-backed one is read from.
 */
public final class CredentialSetMapper {

	/** The path of the credential sets. */
	public static final String PATH = "credential-sets";

	/** The references to Vault instances by ID only (no instance loaded). */
	private static final LongFunction<VaultInstanceDto> IDS_ONLY = id ->
		new VaultInstanceDto(id, V1Links.of(VaultInstanceMapper.PATH, id), null, 0, null, null, null, null, null, null, null, null,
			null, null, null, false, null, null, null, false, null, null);

	private CredentialSetMapper() {
	}

	/**
	 * Gives the representation of a Vault instance by its ID only (not loaded).
	 *
	 * @param id the ID of the Vault instance
	 * @return the representation
	 */
	public static VaultInstanceDto vaultById(long id) {
		return IDS_ONLY.apply(id);
	}

	/**
	 * Gets the v1 type name of a credential set.
	 *
	 * @param credentialSet the credential set
	 * @return the type name
	 */
	public static String typeOf(DeviceCredentialSet credentialSet) {
		return Kind.of(credentialSet).name();
	}

	/**
	 * Gets the IDs of the Vault instances a credential set reads values from.
	 *
	 * @param credentialSet the credential set
	 * @return the IDs (possibly repeated)
	 */
	public static List<Long> vaultInstanceIds(DeviceCredentialSet credentialSet) {
		List<Long> ids = new ArrayList<>();
		for (VaultableSecret secret : secrets(credentialSet)) {
			if (secret != null && secret.vaultInstanceId() != null) {
				ids.add(secret.vaultInstanceId());
			}
		}
		return ids;
	}

	/**
	 * Lists the vaultable fields of a credential set: username, password, super password, private key,
	 * community, authentication key, privacy key (null when they don't apply to the type).
	 *
	 * @param cs the credential set
	 * @return the 7 fields, in that order
	 */
	private static List<VaultableSecret> secrets(DeviceCredentialSet cs) {
		return switch (cs) {
			case DeviceSshKeyAccount c -> Arrays.asList(c.usernameSecret(), c.passwordSecret(), c.superPasswordSecret(),
				c.privateKeySecret(), null, null, null);
			case DeviceSshAccount c -> Arrays.asList(c.usernameSecret(), c.passwordSecret(), c.superPasswordSecret(),
				null, null, null, null);
			case DeviceTelnetAccount c -> Arrays.asList(c.usernameSecret(), c.passwordSecret(), c.superPasswordSecret(),
				null, null, null, null);
			case DeviceHttpAccount c -> Arrays.asList(
				new VaultableSecret(c.username(), c.usernameVaultInstanceId(), c.usernameVaultPath()),
				new VaultableSecret(c.password(), c.passwordVaultInstanceId(), c.passwordVaultPath()), null, null, null, null, null);
			case DeviceSnmpv1Community c -> Arrays.asList(null, null, null, null, c.communitySecret(), null, null);
			case DeviceSnmpv2cCommunity c -> Arrays.asList(null, null, null, null, c.communitySecret(), null, null);
			case DeviceSnmpv3Community c -> Arrays.asList(c.usernameSecret(), null, null, null, null, c.authKeySecret(),
				c.privKeySecret());
		};
	}

	private static Boolean has(VaultableSecret secret) {
		return secret == null ? null : secret.localValue() != null || secret.vaultInstanceId() != null;
	}

	private static VaultSecretDto vault(VaultableSecret secret, LongFunction<VaultInstanceDto> vaults) {
		return secret == null || secret.vaultInstanceId() == null ? null
			: new VaultSecretDto(vaults.apply(secret.vaultInstanceId()), secret.vaultPath());
	}

	/**
	 * Maps a credential set (without its secrets), the Vault instances being given by ID only.
	 *
	 * @param credentialSet the credential set (null gives null)
	 * @return the representation
	 */
	public static CredentialSetDto toDto(DeviceCredentialSet credentialSet) {
		return toDto(credentialSet, IDS_ONLY);
	}

	/**
	 * Maps a credential set (without its secrets).
	 *
	 * @param credentialSet the credential set (null gives null)
	 * @param vaults gives the representation of a Vault instance by ID
	 * @return the representation
	 */
	public static CredentialSetDto toDto(DeviceCredentialSet credentialSet, LongFunction<VaultInstanceDto> vaults) {
		if (credentialSet == null) {
			return null;
		}
		List<VaultableSecret> s = secrets(credentialSet);
		VaultableSecret username = s.get(0);
		DeviceSnmpv3Community v3 = credentialSet instanceof DeviceSnmpv3Community c ? c : null;
		return new CredentialSetDto(credentialSet.id(),
			credentialSet.deviceSpecific() ? null : V1Links.of(PATH, credentialSet.id()),
			credentialSet.name(), credentialSet.version(), credentialSet.changeDate(), credentialSet.name(),
			typeOf(credentialSet), DomainMapper.toDto(credentialSet.mgmtDomain()), credentialSet.deviceSpecific(),
			username == null ? null : username.localValue(), vault(username, vaults),
			has(s.get(1)), vault(s.get(1), vaults),
			has(s.get(2)), vault(s.get(2), vaults),
			has(s.get(3)), vault(s.get(3), vaults),
			has(s.get(4)), vault(s.get(4), vaults),
			v3 == null ? null : v3.authType(), has(s.get(5)), vault(s.get(5), vaults),
			v3 == null ? null : v3.privType(), has(s.get(6)), vault(s.get(6), vaults));
	}

	/**
	 * Maps a credential set to the request which would define it as it is (the base of a merge patch): the
	 * vaultable fields are absent, which leaves them unchanged.
	 *
	 * @param credentialSet the credential set
	 * @return the request
	 */
	public static CredentialSetRequest toRequest(DeviceCredentialSet credentialSet) {
		DeviceSnmpv3Community v3 = credentialSet instanceof DeviceSnmpv3Community c ? c : null;
		return new CredentialSetRequest(credentialSet.name(), typeOf(credentialSet),
			credentialSet.mgmtDomain() == null ? null : new RefId(credentialSet.mgmtDomain().id()),
			null, null, null, null, null, null, null, null, null, null,
			v3 == null ? null : v3.authType(), null, null, v3 == null ? null : v3.privType(), null, null);
	}

	/**
	 * Gets the properties each type accepts, besides the name, the type and the domain.
	 *
	 * @param kind the type of credential set
	 * @return the names of the properties
	 */
	private static Set<String> applicable(Kind kind) {
		return switch (kind) {
			case SSH, TELNET -> Set.of("username", "password", "superPassword");
			case SSH_KEY -> Set.of("username", "password", "superPassword", "privateKey");
			case HTTP -> Set.of("username", "password");
			case SNMP_V1, SNMP_V2C -> Set.of("community");
			case SNMP_V3 -> Set.of("username", "authType", "authKey", "privType", "privKey");
		};
	}

	/**
	 * Reads the change a request asks for a vaultable field.
	 *
	 * @param kind the type of credential set
	 * @param property the name of the property
	 * @param secret the value (absent: null, to clear: {@link Secret#CLEARED})
	 * @param vault where to read the value from
	 * @return the change
	 * @throws NetshotException if the property doesn't apply to the type, or both a value and a Vault location are given
	 */
	private static SecretChange change(Kind kind, String property, Secret secret, VaultSecretRequest vault) {
		boolean given = vault != null || (secret != null && secret.value() != null);
		if (given && !applicable(kind).contains(property)) {
			throw NetshotException.invalidParameter(property, "The property '%s' doesn't apply to the type %s.".formatted(property, kind));
		}
		if (vault != null) {
			if (secret != null) {
				throw NetshotException.invalidParameter(property,
					"Give either '%s' or '%sVault', not both. To switch between them, set the other one to null.".formatted(property, property));
			}
			if (vault.vaultInstance() == null || vault.path() == null || vault.path().isBlank()) {
				throw NetshotException.invalidParameter(property + "Vault", "A Vault instance and a path are required.");
			}
			return new SecretChange.Referenced(vault.vaultInstance().id(), vault.path().trim());
		}
		if (secret == null) {
			return SecretChange.UNCHANGED;
		}
		return secret.value() == null ? (applicable(kind).contains(property) ? SecretChange.CLEARED : SecretChange.UNCHANGED)
			: new SecretChange.Local(secret.value());
	}

	/**
	 * Maps a request to the definition the service takes.
	 *
	 * @param request the request
	 * @return the definition
	 * @throws NetshotException if the type is unknown, or a property doesn't apply to it
	 */
	public static CredentialSetDefinition toDefinition(CredentialSetRequest request) {
		Kind kind;
		try {
			kind = request.type() == null ? null : Kind.valueOf(request.type());
		}
		catch (IllegalArgumentException e) {
			kind = null;
		}
		if (kind == null) {
			throw NetshotException.invalidParameter("type", "Unknown type of credential set '%s'.".formatted(request.type()));
		}
		if (request.authType() != null && !applicable(kind).contains("authType")) {
			throw NetshotException.invalidParameter("authType", "The property 'authType' doesn't apply to the type %s.".formatted(kind));
		}
		if (request.privType() != null && !applicable(kind).contains("privType")) {
			throw NetshotException.invalidParameter("privType", "The property 'privType' doesn't apply to the type %s.".formatted(kind));
		}
		SecretChange username = change(kind, "username", request.username() == null ? null : new Secret(request.username()),
			request.usernameVault());
		return new CredentialSetDefinition(kind, request.name(), request.mgmtDomain() == null ? null : request.mgmtDomain().id(),
			username,
			change(kind, "password", request.password(), request.passwordVault()),
			change(kind, "superPassword", request.superPassword(), request.superPasswordVault()),
			change(kind, "privateKey", request.privateKey(), request.privateKeyVault()),
			change(kind, "community", request.community(), request.communityVault()),
			request.authType(),
			change(kind, "authKey", request.authKey(), request.authKeyVault()),
			request.privType(),
			change(kind, "privKey", request.privKey(), request.privKeyVault()));
	}
}
