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
package net.netshot.netshot.credentials;

import org.jooq.DSLContext;

import net.netshot.netshot.credentials.CredentialSetDefinition.Kind;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.vault.SecretChange;
import net.netshot.netshot.vault.VaultInstanceRepository;
import net.netshot.netshot.vault.VaultableSecret;

/**
 * Builds a credential set from a {@link CredentialSetDefinition}, over the existing one (whose secrets are kept
 * unless the definition changes them), and checks that the result is complete: the secrets each kind needs are
 * set (locally or in a Vault instance), and the referenced Vault instances exist.
 */
final class CredentialSetAssembler {

	private CredentialSetAssembler() {
		// Static helpers only
	}

	private static DeviceCredentialSet blank(Kind kind) {
		return switch (kind) {
			case SSH -> DeviceSshAccount.builder().build();
			case SSH_KEY -> DeviceSshKeyAccount.builder().build();
			case TELNET -> DeviceTelnetAccount.builder().build();
			case HTTP -> DeviceHttpAccount.builder().build();
			case SNMP_V1 -> DeviceSnmpv1Community.builder().build();
			case SNMP_V2C -> DeviceSnmpv2cCommunity.builder().build();
			case SNMP_V3 -> DeviceSnmpv3Community.builder().build();
		};
	}

	/**
	 * Applies the change of a field, checking the Vault instance it refers to.
	 *
	 * @param dsl the jOOQ context
	 * @param property the name of the property (for the error)
	 * @param change what to do, null for unchanged
	 * @param current the current state of the field
	 * @param required whether the field must be set at the end
	 * @return the new state
	 * @throws NetshotException if the Vault instance doesn't exist, or a required field is empty
	 */
	private static VaultableSecret apply(DSLContext dsl, String property, SecretChange change, VaultableSecret current, boolean required) {
		SecretChange effective = change == null ? SecretChange.UNCHANGED : change;
		if (effective instanceof SecretChange.Referenced r && new VaultInstanceRepository().find(dsl, r.vaultInstanceId()) == null) {
			throw NetshotException.invalidParameter(property + "Vault", "The Vault instance %d doesn't exist.".formatted(r.vaultInstanceId()));
		}
		VaultableSecret result = effective.applyTo(current);
		if (required && result.localValue() == null && result.vaultInstanceId() == null) {
			throw NetshotException.invalidParameter(property, "The property '%s' is required for this type of credential set.".formatted(property));
		}
		return result;
	}

	/**
	 * Builds a credential set.
	 *
	 * @param dsl the jOOQ context
	 * @param existing the stored credential set to change, or null for a new one
	 * @param definition the definition
	 * @param domain the domain the credential set is restricted to, or null
	 * @return the credential set, to store
	 * @throws NetshotException if the definition is invalid or incomplete
	 */
	static DeviceCredentialSet assemble(DSLContext dsl, DeviceCredentialSet existing, CredentialSetDefinition definition, Domain domain) {
		String name = definition.name() == null ? "" : definition.name().trim();
		if (name.isEmpty()) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, "Invalid name for the credential set");
		}
		DeviceCredentialSet base = existing != null ? existing : blank(definition.kind());
		DeviceCredentialSet result = switch (base) {
			case DeviceSshAccount b -> {
				VaultableSecret u = apply(dsl, "username", definition.username(), b.usernameSecret(), true);
				VaultableSecret p = apply(dsl, "password", definition.password(), b.passwordSecret(), true);
				VaultableSecret sp = apply(dsl, "superPassword", definition.superPassword(), b.superPasswordSecret(), false);
				yield b.toBuilder()
					.username(u.localValue()).usernameVaultInstanceId(u.vaultInstanceId()).usernameVaultPath(u.vaultPath())
					.password(p.localValue()).passwordVaultInstanceId(p.vaultInstanceId()).passwordVaultPath(p.vaultPath())
					.superPassword(sp.localValue()).superPasswordVaultInstanceId(sp.vaultInstanceId()).superPasswordVaultPath(sp.vaultPath())
					.build();
			}
			case DeviceSshKeyAccount b -> {
				VaultableSecret u = apply(dsl, "username", definition.username(), b.usernameSecret(), true);
				VaultableSecret p = apply(dsl, "password", definition.password(), b.passwordSecret(), false);
				VaultableSecret sp = apply(dsl, "superPassword", definition.superPassword(), b.superPasswordSecret(), false);
				VaultableSecret k = apply(dsl, "privateKey", definition.privateKey(), b.privateKeySecret(), true);
				yield b.toBuilder()
					.username(u.localValue()).usernameVaultInstanceId(u.vaultInstanceId()).usernameVaultPath(u.vaultPath())
					.password(p.localValue()).passwordVaultInstanceId(p.vaultInstanceId()).passwordVaultPath(p.vaultPath())
					.superPassword(sp.localValue()).superPasswordVaultInstanceId(sp.vaultInstanceId()).superPasswordVaultPath(sp.vaultPath())
					.privateKey(k.localValue()).privateKeyVaultInstanceId(k.vaultInstanceId()).privateKeyVaultPath(k.vaultPath())
					.build();
			}
			case DeviceTelnetAccount b -> {
				VaultableSecret u = apply(dsl, "username", definition.username(), b.usernameSecret(), true);
				VaultableSecret p = apply(dsl, "password", definition.password(), b.passwordSecret(), true);
				VaultableSecret sp = apply(dsl, "superPassword", definition.superPassword(), b.superPasswordSecret(), false);
				yield b.toBuilder()
					.username(u.localValue()).usernameVaultInstanceId(u.vaultInstanceId()).usernameVaultPath(u.vaultPath())
					.password(p.localValue()).passwordVaultInstanceId(p.vaultInstanceId()).passwordVaultPath(p.vaultPath())
					.superPassword(sp.localValue()).superPasswordVaultInstanceId(sp.vaultInstanceId()).superPasswordVaultPath(sp.vaultPath())
					.build();
			}
			case DeviceHttpAccount b -> {
				VaultableSecret u = apply(dsl, "username", definition.username(),
					new VaultableSecret(b.username(), b.usernameVaultInstanceId(), b.usernameVaultPath()), true);
				VaultableSecret p = apply(dsl, "password", definition.password(),
					new VaultableSecret(b.password(), b.passwordVaultInstanceId(), b.passwordVaultPath()), true);
				yield b.toBuilder()
					.username(u.localValue()).usernameVaultInstanceId(u.vaultInstanceId()).usernameVaultPath(u.vaultPath())
					.password(p.localValue()).passwordVaultInstanceId(p.vaultInstanceId()).passwordVaultPath(p.vaultPath())
					.build();
			}
			case DeviceSnmpv1Community b -> {
				VaultableSecret c = apply(dsl, "community", definition.community(), b.communitySecret(), true);
				yield b.toBuilder().community(c.localValue()).communityVaultInstanceId(c.vaultInstanceId()).communityVaultPath(c.vaultPath()).build();
			}
			case DeviceSnmpv2cCommunity b -> {
				VaultableSecret c = apply(dsl, "community", definition.community(), b.communitySecret(), true);
				yield b.toBuilder().community(c.localValue()).communityVaultInstanceId(c.vaultInstanceId()).communityVaultPath(c.vaultPath()).build();
			}
			case DeviceSnmpv3Community b -> {
				AuthProtocol authType = definition.authType() == null ? AuthProtocol.NONE : definition.authType();
				PrivProtocol privType = definition.privType() == null ? PrivProtocol.NONE : definition.privType();
				if (authType == AuthProtocol.NONE && privType != PrivProtocol.NONE) {
					throw NetshotException.invalidParameter("privType", "Privacy requires authentication: authType can't be NONE.");
				}
				VaultableSecret u = apply(dsl, "username", definition.username(), b.usernameSecret(), true);
				// The keys of a protocol which is not used are dropped
				VaultableSecret a = authType == AuthProtocol.NONE ? new VaultableSecret(null, null, null)
					: apply(dsl, "authKey", definition.authKey(), b.authKeySecret(), true);
				VaultableSecret p = privType == PrivProtocol.NONE ? new VaultableSecret(null, null, null)
					: apply(dsl, "privKey", definition.privKey(), b.privKeySecret(), true);
				yield b.toBuilder()
					.username(u.localValue()).usernameVaultInstanceId(u.vaultInstanceId()).usernameVaultPath(u.vaultPath())
					.authType(authType)
					.authKey(a.localValue()).authKeyVaultInstanceId(a.vaultInstanceId()).authKeyVaultPath(a.vaultPath())
					.privType(privType)
					.privKey(p.localValue()).privKeyVaultInstanceId(p.vaultInstanceId()).privKeyVaultPath(p.vaultPath())
					.build();
			}
		};
		return result.withIdentity(existing == null ? 0 : existing.id(), name, existing != null && existing.deviceSpecific())
			.withMgmtDomain(domain);
	}
}
