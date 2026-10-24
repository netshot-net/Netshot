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

import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.vault.VaultInstanceRepository;
import net.netshot.netshot.vault.VaultableSecret;

/**
 * Applies incoming (deserialized) credential sets onto stored ones: merges the values (a secret
 * left out means "unchanged") and resolves the Local/Vault state of the vaultable fields.
 */
public final class CredentialSets {

	private CredentialSets() {
		// Static helpers only
	}

	/**
	 * Resolves an incoming vaultable field's Local/Vault state: either the referenced
	 * {@link net.netshot.netshot.vault.VaultInstance} is checked and the local value cleared,
	 * or any Vault reference is cleared and the local value is taken from {@code incoming}
	 * if it was given, or kept from {@code current} otherwise (the usual "null = unchanged"
	 * masking convention for the secret fields).
	 *
	 * @param dsl the jOOQ context, used to check the Vault instance
	 * @param incoming the incoming field
	 * @param current the current value of the field (the same as {@code incoming} for a new credential set)
	 * @return the resolved field
	 * @throws NetshotException if the referenced Vault instance doesn't exist
	 */
	private static VaultableSecret resolve(DSLContext dsl, VaultableSecret incoming, VaultableSecret current) {
		Long vaultInstanceId = incoming.vaultInstanceId();
		if (vaultInstanceId != null) {
			if (new VaultInstanceRepository().find(dsl, vaultInstanceId) == null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE, "Invalid Vault instance.");
			}
			return new VaultableSecret(null, vaultInstanceId, incoming.vaultPath());
		}
		return new VaultableSecret(incoming.localValue() != null ? incoming.localValue() : current.localValue(), null, null);
	}

	/**
	 * Resolves every vaultable field of a credential set (see {@link #resolve}).
	 *
	 * @param dsl the jOOQ context
	 * @param target the credential set to update (may be the same object as {@code source})
	 * @param source the incoming credential set, of the same type
	 * @return the credential set, with its vaultable fields resolved
	 * @throws NetshotException if a referenced Vault instance doesn't exist
	 */
	public static DeviceCredentialSet resolveVaultableFields(DSLContext dsl, DeviceCredentialSet target, DeviceCredentialSet source) {
		return switch (target) {
			case DeviceSshAccount t -> {
				DeviceSshAccount s = (DeviceSshAccount) source;
				VaultableSecret u = resolve(dsl, s.usernameSecret(), t.usernameSecret());
				VaultableSecret p = resolve(dsl, s.passwordSecret(), t.passwordSecret());
				VaultableSecret sp = resolve(dsl, s.superPasswordSecret(), t.superPasswordSecret());
				yield t.toBuilder()
					.username(u.localValue()).usernameVaultInstanceId(u.vaultInstanceId()).usernameVaultPath(u.vaultPath())
					.password(p.localValue()).passwordVaultInstanceId(p.vaultInstanceId()).passwordVaultPath(p.vaultPath())
					.superPassword(sp.localValue()).superPasswordVaultInstanceId(sp.vaultInstanceId()).superPasswordVaultPath(sp.vaultPath())
					.build();
			}
			case DeviceSshKeyAccount t -> {
				DeviceSshKeyAccount s = (DeviceSshKeyAccount) source;
				VaultableSecret u = resolve(dsl, s.usernameSecret(), t.usernameSecret());
				VaultableSecret p = resolve(dsl, s.passwordSecret(), t.passwordSecret());
				VaultableSecret sp = resolve(dsl, s.superPasswordSecret(), t.superPasswordSecret());
				VaultableSecret k = resolve(dsl, s.privateKeySecret(), t.privateKeySecret());
				yield t.toBuilder()
					.username(u.localValue()).usernameVaultInstanceId(u.vaultInstanceId()).usernameVaultPath(u.vaultPath())
					.password(p.localValue()).passwordVaultInstanceId(p.vaultInstanceId()).passwordVaultPath(p.vaultPath())
					.superPassword(sp.localValue()).superPasswordVaultInstanceId(sp.vaultInstanceId()).superPasswordVaultPath(sp.vaultPath())
					.privateKey(k.localValue()).privateKeyVaultInstanceId(k.vaultInstanceId()).privateKeyVaultPath(k.vaultPath())
					.build();
			}
			case DeviceTelnetAccount t -> {
				DeviceTelnetAccount s = (DeviceTelnetAccount) source;
				VaultableSecret u = resolve(dsl, s.usernameSecret(), t.usernameSecret());
				VaultableSecret p = resolve(dsl, s.passwordSecret(), t.passwordSecret());
				VaultableSecret sp = resolve(dsl, s.superPasswordSecret(), t.superPasswordSecret());
				yield t.toBuilder()
					.username(u.localValue()).usernameVaultInstanceId(u.vaultInstanceId()).usernameVaultPath(u.vaultPath())
					.password(p.localValue()).passwordVaultInstanceId(p.vaultInstanceId()).passwordVaultPath(p.vaultPath())
					.superPassword(sp.localValue()).superPasswordVaultInstanceId(sp.vaultInstanceId()).superPasswordVaultPath(sp.vaultPath())
					.build();
			}
			case DeviceHttpAccount t -> {
				DeviceHttpAccount s = (DeviceHttpAccount) source;
				VaultableSecret u = resolve(dsl, s.usernameSecret(), t.usernameSecret());
				VaultableSecret p = resolve(dsl, s.passwordSecret(), t.passwordSecret());
				yield t.toBuilder()
					.username(u.localValue()).usernameVaultInstanceId(u.vaultInstanceId()).usernameVaultPath(u.vaultPath())
					.password(p.localValue()).passwordVaultInstanceId(p.vaultInstanceId()).passwordVaultPath(p.vaultPath())
					.build();
			}
			case DeviceSnmpv1Community t -> {
				VaultableSecret c = resolve(dsl, ((DeviceSnmpCommunity) source).communitySecret(), t.communitySecret());
				yield t.toBuilder().community(c.localValue()).communityVaultInstanceId(c.vaultInstanceId()).communityVaultPath(c.vaultPath()).build();
			}
			case DeviceSnmpv2cCommunity t -> {
				VaultableSecret c = resolve(dsl, ((DeviceSnmpCommunity) source).communitySecret(), t.communitySecret());
				yield t.toBuilder().community(c.localValue()).communityVaultInstanceId(c.vaultInstanceId()).communityVaultPath(c.vaultPath()).build();
			}
			case DeviceSnmpv3Community t -> {
				DeviceSnmpv3Community s = (DeviceSnmpv3Community) source;
				VaultableSecret c = resolve(dsl, s.communitySecret(), t.communitySecret());
				VaultableSecret u = resolve(dsl, s.usernameSecret(), t.usernameSecret());
				VaultableSecret a = resolve(dsl, s.authKeySecret(), t.authKeySecret());
				VaultableSecret p = resolve(dsl, s.privKeySecret(), t.privKeySecret());
				yield t.toBuilder()
					.community(c.localValue()).communityVaultInstanceId(c.vaultInstanceId()).communityVaultPath(c.vaultPath())
					.username(u.localValue()).usernameVaultInstanceId(u.vaultInstanceId()).usernameVaultPath(u.vaultPath())
					.authKey(a.localValue()).authKeyVaultInstanceId(a.vaultInstanceId()).authKeyVaultPath(a.vaultPath())
					.privKey(p.localValue()).privKeyVaultInstanceId(p.vaultInstanceId()).privKeyVaultPath(p.vaultPath())
					.build();
			}
		};
	}

	/**
	 * Tells whether an incoming secret replaces the stored one: a secret left out ({@code null})
	 * is unchanged, and so is the {@code "="} sentinel for a device specific credential set.
	 *
	 * @param secret the incoming secret
	 * @param specific whether the credential set is specific to a device
	 * @return true if the incoming secret replaces the stored one
	 */
	private static boolean changes(String secret, boolean specific) {
		return secret != null && !(specific && "=".equals(secret));
	}

	/**
	 * Copies the local values of an incoming credential set onto a stored one of the same type,
	 * the secrets which are left out being kept. The identity (ID, name, domain...) of the stored
	 * credential set is kept, and so are its Vault references (see {@link #resolveVaultableFields},
	 * to apply afterwards).
	 *
	 * @param existing the stored credential set
	 * @param incoming the incoming credential set, of the same type
	 * @param specific whether this is a device specific credential set (the {@code "="} sentinel
	 *        then means "unchanged" too, and the private key is always replaced)
	 * @return the merged credential set
	 */
	public static DeviceCredentialSet merge(DeviceCredentialSet existing, DeviceCredentialSet incoming, boolean specific) {
		return switch (existing) {
			case DeviceSshAccount e -> {
				DeviceSshAccount i = (DeviceSshAccount) incoming;
				yield e.toBuilder()
					.username(i.username())
					.password(changes(i.password(), specific) ? i.password() : e.password())
					.superPassword(changes(i.superPassword(), specific) ? i.superPassword() : e.superPassword())
					.build();
			}
			case DeviceSshKeyAccount e -> {
				DeviceSshKeyAccount i = (DeviceSshKeyAccount) incoming;
				yield e.toBuilder()
					.username(i.username())
					.password(changes(i.password(), specific) ? i.password() : e.password())
					.superPassword(changes(i.superPassword(), specific) ? i.superPassword() : e.superPassword())
					.privateKey(specific || i.privateKey() != null ? i.privateKey() : e.privateKey())
					.build();
			}
			case DeviceTelnetAccount e -> {
				DeviceTelnetAccount i = (DeviceTelnetAccount) incoming;
				yield e.toBuilder()
					.username(i.username())
					.password(changes(i.password(), specific) ? i.password() : e.password())
					.superPassword(changes(i.superPassword(), specific) ? i.superPassword() : e.superPassword())
					.build();
			}
			case DeviceHttpAccount e -> {
				DeviceHttpAccount i = (DeviceHttpAccount) incoming;
				yield e.toBuilder()
					.username(i.username())
					.password(changes(i.password(), specific) ? i.password() : e.password())
					.build();
			}
			case DeviceSnmpv1Community e -> {
				String community = ((DeviceSnmpCommunity) incoming).community();
				yield e.toBuilder().community(community != null ? community : e.community()).build();
			}
			case DeviceSnmpv2cCommunity e -> {
				String community = ((DeviceSnmpCommunity) incoming).community();
				yield e.toBuilder().community(community != null ? community : e.community()).build();
			}
			case DeviceSnmpv3Community e -> {
				DeviceSnmpv3Community i = (DeviceSnmpv3Community) incoming;
				yield e.toBuilder()
					.username(i.username())
					.authType(i.authType())
					.authKey(changes(i.authKey(), specific) ? i.authKey() : e.authKey())
					.privType(i.privType())
					.privKey(changes(i.privKey(), specific) ? i.privKey() : e.privKey())
					.build();
			}
		};
	}

	/**
	 * Creates or updates the credential set specific to a device access: if {@code existing}
	 * is null or of another type than {@code incoming}, {@code incoming} is stored as a new
	 * specific credential set (with a generated name); otherwise its values are copied onto
	 * {@code existing}, the secrets submitted as unchanged being kept. A replaced credential set
	 * is not removed: the caller removes it once the access doesn't reference it anymore.
	 *
	 * @param dsl the jOOQ context
	 * @param repository the credential set repository
	 * @param existing the current specific credential set of the access, or null if there is none
	 * @param incoming the incoming credential set
	 * @return the stored credential set
	 * @throws NetshotException if a referenced Vault instance doesn't exist
	 */
	public static DeviceCredentialSet applySpecificCredentialSet(DSLContext dsl, CredentialSetRepository repository,
			DeviceCredentialSet existing, DeviceCredentialSet incoming) {
		if (existing == null || !existing.getClass().equals(incoming.getClass())) {
			DeviceCredentialSet created = incoming.withIdentity(0, DeviceCredentialSet.generateSpecificName(), true);
			return repository.add(dsl, resolveVaultableFields(dsl, created, created));
		}
		// The Vault references are applied last, so that switching a field to Vault mode always
		// wins over the unconditional local value copies of the merge (e.g. the username)
		DeviceCredentialSet merged = resolveVaultableFields(dsl, merge(existing, incoming, true), incoming);
		return repository.save(dsl, merged);
	}
}
