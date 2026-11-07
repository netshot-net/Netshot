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
package net.netshot.netshot.credentials;

import org.jooq.DSLContext;

import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.platform.secret.VaultSecret;
import net.netshot.netshot.vault.VaultInstanceRepository;

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
	 * {@link net.netshot.netshot.vault.VaultInstance} is checked and the reference taken,
	 * or the local value is taken from {@code incoming} if it was given, or kept from
	 * {@code current} otherwise (the usual "null = unchanged" masking convention for the
	 * secret fields), without any Vault reference.
	 *
	 * @param dsl the jOOQ context, used to check the Vault instance
	 * @param incoming the incoming field
	 * @param current the current value of the field (the same as {@code incoming} for a new credential set)
	 * @return the resolved field
	 * @throws NetshotException if the referenced Vault instance doesn't exist
	 */
	private static Secret resolve(DSLContext dsl, Secret incoming, Secret current) {
		if (incoming instanceof VaultSecret vault) {
			if (new VaultInstanceRepository().find(dsl, vault.vaultInstanceId()) == null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE, "Invalid Vault instance.");
			}
			return vault;
		}
		return incoming != null ? incoming : Secret.localValue(current);
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
				yield t.toBuilder()
					.username(resolve(dsl, s.username(), t.username()))
					.password(resolve(dsl, s.password(), t.password()))
					.superPassword(resolve(dsl, s.superPassword(), t.superPassword()))
					.build();
			}
			case DeviceSshKeyAccount t -> {
				DeviceSshKeyAccount s = (DeviceSshKeyAccount) source;
				yield t.toBuilder()
					.username(resolve(dsl, s.username(), t.username()))
					.password(resolve(dsl, s.password(), t.password()))
					.superPassword(resolve(dsl, s.superPassword(), t.superPassword()))
					.privateKey(resolve(dsl, s.privateKey(), t.privateKey()))
					.build();
			}
			case DeviceTelnetAccount t -> {
				DeviceTelnetAccount s = (DeviceTelnetAccount) source;
				yield t.toBuilder()
					.username(resolve(dsl, s.username(), t.username()))
					.password(resolve(dsl, s.password(), t.password()))
					.superPassword(resolve(dsl, s.superPassword(), t.superPassword()))
					.build();
			}
			case DeviceHttpAccount t -> {
				DeviceHttpAccount s = (DeviceHttpAccount) source;
				yield t.toBuilder()
					.username(resolve(dsl, s.username(), t.username()))
					.password(resolve(dsl, s.password(), t.password()))
					.build();
			}
			case DeviceSnmpv1Community t ->
				t.toBuilder().community(resolve(dsl, ((DeviceSnmpCommunity) source).community(), t.community())).build();
			case DeviceSnmpv2cCommunity t ->
				t.toBuilder().community(resolve(dsl, ((DeviceSnmpCommunity) source).community(), t.community())).build();
			case DeviceSnmpv3Community t -> {
				DeviceSnmpv3Community s = (DeviceSnmpv3Community) source;
				yield t.toBuilder()
					.community(resolve(dsl, s.community(), t.community()))
					.username(resolve(dsl, s.username(), t.username()))
					.authKey(resolve(dsl, s.authKey(), t.authKey()))
					.privKey(resolve(dsl, s.privKey(), t.privKey()))
					.build();
			}
		};
	}

	/**
	 * Gets the local value of an incoming secret if it replaces the stored one: a secret left out ({@code null})
	 * is unchanged, and so is the {@code "="} sentinel for a device specific credential set.
	 *
	 * @param incoming the incoming secret
	 * @param current the stored secret
	 * @param specific whether the credential set is specific to a device
	 * @return the local value to keep
	 */
	private static LocalSecret changed(Secret incoming, Secret current, boolean specific) {
		LocalSecret value = Secret.localValue(incoming);
		boolean changes = value != null && !(specific && "=".equals(value.reveal()));
		return changes ? value : Secret.localValue(current);
	}

	/**
	 * Copies the local values of an incoming credential set onto a stored one of the same type,
	 * the secrets which are left out being kept. The identity (ID, name, domain...) of the stored
	 * credential set is kept, its Vault references are not (see {@link #resolveVaultableFields},
	 * to apply afterwards, which takes those of the incoming credential set).
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
					.username(Secret.localValue(i.username()))
					.password(changed(i.password(), e.password(), specific))
					.superPassword(changed(i.superPassword(), e.superPassword(), specific))
					.build();
			}
			case DeviceSshKeyAccount e -> {
				DeviceSshKeyAccount i = (DeviceSshKeyAccount) incoming;
				LocalSecret privateKey = Secret.localValue(i.privateKey());
				yield e.toBuilder()
					.username(Secret.localValue(i.username()))
					.password(changed(i.password(), e.password(), specific))
					.superPassword(changed(i.superPassword(), e.superPassword(), specific))
					.privateKey(specific || privateKey != null ? privateKey : Secret.localValue(e.privateKey()))
					.build();
			}
			case DeviceTelnetAccount e -> {
				DeviceTelnetAccount i = (DeviceTelnetAccount) incoming;
				yield e.toBuilder()
					.username(Secret.localValue(i.username()))
					.password(changed(i.password(), e.password(), specific))
					.superPassword(changed(i.superPassword(), e.superPassword(), specific))
					.build();
			}
			case DeviceHttpAccount e -> {
				DeviceHttpAccount i = (DeviceHttpAccount) incoming;
				yield e.toBuilder()
					.username(Secret.localValue(i.username()))
					.password(changed(i.password(), e.password(), specific))
					.build();
			}
			case DeviceSnmpv1Community e ->
				e.toBuilder().community(changed(((DeviceSnmpCommunity) incoming).community(), e.community(), false)).build();
			case DeviceSnmpv2cCommunity e ->
				e.toBuilder().community(changed(((DeviceSnmpCommunity) incoming).community(), e.community(), false)).build();
			case DeviceSnmpv3Community e -> {
				DeviceSnmpv3Community i = (DeviceSnmpv3Community) incoming;
				yield e.toBuilder()
					.community(Secret.localValue(e.community()))
					.username(Secret.localValue(i.username()))
					.authType(i.authType())
					.authKey(changed(i.authKey(), e.authKey(), specific))
					.privType(i.privType())
					.privKey(changed(i.privKey(), e.privKey(), specific))
					.build();
			}
		};
	}

	/**
	 * Creates or updates the credential set specific to a device access from a definition: the secrets the definition
	 * leaves out are kept from the current credential set when it is of the same type, otherwise a new specific
	 * credential set is stored (a replaced one is not removed: the caller removes it once the access doesn't reference
	 * it anymore).
	 *
	 * @param dsl the jOOQ context
	 * @param repository the credential set repository
	 * @param existing the current specific credential set of the access, or null if there is none
	 * @param definition the definition (its name and domain are not used)
	 * @param expectedClass the class of credential set the access takes, or null for any
	 * @return the stored credential set
	 * @throws NetshotException if the definition is invalid or incomplete, a referenced Vault instance doesn't
	 *         exist, or the credential set is not of the class the access takes
	 */
	public static DeviceCredentialSet applySpecificDefinition(DSLContext dsl, CredentialSetRepository repository,
			DeviceCredentialSet existing, CredentialSetDefinition definition, Class<? extends DeviceCredentialSet> expectedClass) {
		DeviceCredentialSet base = existing != null && CredentialSetDefinition.Kind.of(existing) == definition.kind() ? existing : null;
		String name = base != null ? base.name() : DeviceCredentialSet.generateSpecificName();
		CredentialSetDefinition named = new CredentialSetDefinition(definition.kind(), name, null, definition.username(),
			definition.password(), definition.superPassword(), definition.privateKey(), definition.community(), definition.authType(),
			definition.authKey(), definition.privType(), definition.privKey());
		DeviceCredentialSet assembled = CredentialSetAssembler.assemble(dsl, base, named, null);
		if (expectedClass != null && !expectedClass.isInstance(assembled)) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE,
				"The specific credential set is not compatible with its access.");
		}
		if (base == null) {
			return repository.add(dsl, assembled.withIdentity(0, name, true));
		}
		return repository.save(dsl, assembled);
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
