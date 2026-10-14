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

import org.hibernate.Session;

import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.vault.VaultInstance;
import net.netshot.netshot.vault.VaultableSecret;

/**
 * Applies incoming (deserialized, transient) credential sets onto persistent ones: resolves
 * the Local/Vault state of the vaultable fields, and creates or updates the owned,
 * device-and-access-specific credential sets.
 */
public final class CredentialSets {

	private CredentialSets() {
		// Static helpers only
	}

	/**
	 * Resolves an incoming vaultable field's Local/Vault state into a fresh
	 * {@link VaultableSecret} bundle: either the referenced {@link VaultInstance}
	 * is resolved and the local value cleared, or any Vault reference is
	 * cleared and the local value is carried over from {@code current}
	 * (respecting the usual "null = unchanged" masking convention for the
	 * genuinely secret fields) or from {@code incoming} if it was changed.
	 * <p>
	 * Callers must apply the result back onto the owning entity via its
	 * {@code setXxxSecret(...)} setter - each vaultable field is backed by
	 * plain, individually mapped columns/association on the entity (not a
	 * JPA embeddable), so mutating a bundle returned by a getter has no
	 * effect on its own.
	 * @param session the Hibernate session, used to resolve the Vault instance ID
	 * @param incoming the incoming (deserialized) field
	 * @param current the field's current value on the target entity, used to preserve
	 *        the local value when unchanged (pass the same object as {@code incoming}
	 *        when resolving a brand-new, not-yet-persisted entity's own references)
	 * @return the resolved bundle to apply via {@code setXxxSecret(...)}
	 * @throws NetshotException if the referenced Vault instance doesn't exist
	 */
	private static VaultableSecret resolveVaultableField(Session session, VaultableSecret incoming, VaultableSecret current) {
		VaultableSecret result = new VaultableSecret();
		Long vaultInstanceId = incoming.getVaultInstanceId();
		if (vaultInstanceId != null) {
			VaultInstance vaultInstance = session.get(VaultInstance.class, vaultInstanceId);
			if (vaultInstance == null) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE, "Invalid Vault instance.");
			}
			result.setVaultInstance(vaultInstance);
			result.setVaultPath(incoming.getVaultPath());
			result.setLocalValue(null);
		}
		else {
			result.setVaultInstance(null);
			result.setVaultPath(null);
			result.setLocalValue(incoming.getLocalValue() != null ? incoming.getLocalValue() : current.getLocalValue());
		}
		return result;
	}

	/**
	 * Applies {@link #resolveVaultableField} to every vaultable field of a
	 * credential set, dispatching by concrete type (most-specific first,
	 * since {@link DeviceSnmpv3Community} is itself a {@link DeviceSnmpCommunity}).
	 * @param session the Hibernate session
	 * @param target the credential set to update (may be the same object as {@code source})
	 * @param source the incoming (deserialized) credential set
	 * @throws NetshotException if a referenced Vault instance doesn't exist
	 */
	public static void applyVaultableFields(Session session, DeviceCredentialSet target, DeviceCredentialSet source) {
		if (target instanceof DeviceCliAccount cliTarget && source instanceof DeviceCliAccount cliSource) {
			cliTarget.setUsernameSecret(resolveVaultableField(session, cliSource.getUsernameSecret(), cliTarget.getUsernameSecret()));
			cliTarget.setPasswordSecret(resolveVaultableField(session, cliSource.getPasswordSecret(), cliTarget.getPasswordSecret()));
			cliTarget.setSuperPasswordSecret(resolveVaultableField(session, cliSource.getSuperPasswordSecret(), cliTarget.getSuperPasswordSecret()));
			if (cliTarget instanceof DeviceSshKeyAccount keyTarget && cliSource instanceof DeviceSshKeyAccount keySource) {
				keyTarget.setPrivateKeySecret(resolveVaultableField(session, keySource.getPrivateKeySecret(), keyTarget.getPrivateKeySecret()));
			}
		}
		else if (target instanceof DeviceSnmpv3Community snmp3Target && source instanceof DeviceSnmpv3Community snmp3Source) {
			snmp3Target.setCommunitySecret(resolveVaultableField(session, snmp3Source.getCommunitySecret(), snmp3Target.getCommunitySecret()));
			snmp3Target.setUsernameSecret(resolveVaultableField(session, snmp3Source.getUsernameSecret(), snmp3Target.getUsernameSecret()));
			snmp3Target.setAuthKeySecret(resolveVaultableField(session, snmp3Source.getAuthKeySecret(), snmp3Target.getAuthKeySecret()));
			snmp3Target.setPrivKeySecret(resolveVaultableField(session, snmp3Source.getPrivKeySecret(), snmp3Target.getPrivKeySecret()));
		}
		else if (target instanceof DeviceSnmpCommunity communityTarget && source instanceof DeviceSnmpCommunity communitySource) {
			communityTarget.setCommunitySecret(resolveVaultableField(session, communitySource.getCommunitySecret(), communityTarget.getCommunitySecret()));
		}
		else if (target instanceof DeviceHttpAccount httpTarget && source instanceof DeviceHttpAccount httpSource) {
			httpTarget.setUsernameSecret(resolveVaultableField(session, httpSource.getUsernameSecret(), httpTarget.getUsernameSecret()));
			httpTarget.setPasswordSecret(resolveVaultableField(session, httpSource.getPasswordSecret(), httpTarget.getPasswordSecret()));
		}
	}

	/**
	 * Creates or updates an owned, per-access specific credential set: if
	 * {@code existing} is null or of a different concrete class than
	 * {@code incoming}, the old one (if any) is deleted and {@code incoming}
	 * is persisted as the new owned credential set (stamped
	 * {@code deviceSpecific=true}, given a synthetic name); otherwise
	 * {@code incoming}'s fields are copied onto {@code existing} in place,
	 * preserving secret fields submitted as the unchanged sentinel ("="),
	 * matching the convention already used for the device-wide singleton.
	 * @param session the Hibernate session
	 * @param existing the currently persisted specific credential set, or null if none yet
	 * @param incoming the incoming (deserialized, not yet persisted) credential set payload
	 * @return the resulting persisted credential set
	 */
	public static DeviceCredentialSet applySpecificCredentialSet(Session session, DeviceCredentialSet existing,
			DeviceCredentialSet incoming) {
		if (existing != null && !existing.getClass().equals(incoming.getClass())) {
			session.remove(existing);
			existing = null;
		}
		if (existing == null) {
			incoming.setDeviceSpecific(true);
			incoming.setName(DeviceCredentialSet.generateSpecificName());
			applyVaultableFields(session, incoming, incoming);
			session.persist(incoming);
			return incoming;
		}
		if (DeviceCliAccount.class.isInstance(existing)) {
			DeviceCliAccount cliAccount = (DeviceCliAccount) existing;
			DeviceCliAccount rsCliAccount = (DeviceCliAccount) incoming;
			cliAccount.setUsername(rsCliAccount.getUsername());
			if (rsCliAccount.getPassword() != null && !"=".equals(rsCliAccount.getPassword())) {
				cliAccount.setPassword(rsCliAccount.getPassword());
			}
			if (rsCliAccount.getSuperPassword() != null && !"=".equals(rsCliAccount.getSuperPassword())) {
				cliAccount.setSuperPassword(rsCliAccount.getSuperPassword());
			}
			if (DeviceSshKeyAccount.class.isInstance(existing)) {
				((DeviceSshKeyAccount) cliAccount).setPrivateKey(((DeviceSshKeyAccount) rsCliAccount).getPrivateKey());
			}
		}
		else if (DeviceSnmpv3Community.class.isInstance(existing)) {
			DeviceSnmpv3Community rsSnmp3 = (DeviceSnmpv3Community) incoming;
			((DeviceSnmpv3Community) existing).setUsername(rsSnmp3.getUsername());
			((DeviceSnmpv3Community) existing).setAuthType(rsSnmp3.getAuthType());
			if (rsSnmp3.getAuthKey() != null && !"=".equals(rsSnmp3.getAuthKey())) {
				((DeviceSnmpv3Community) existing).setAuthKey(rsSnmp3.getAuthKey());
			}
			((DeviceSnmpv3Community) existing).setPrivType(rsSnmp3.getPrivType());
			if (rsSnmp3.getPrivKey() != null && !"=".equals(rsSnmp3.getPrivKey())) {
				((DeviceSnmpv3Community) existing).setPrivKey(rsSnmp3.getPrivKey());
			}
		}
		else if (DeviceSnmpCommunity.class.isInstance(existing)) {
			String community = ((DeviceSnmpCommunity) incoming).getCommunity();
			if (community != null) {
				((DeviceSnmpCommunity) existing).setCommunity(community);
			}
		}
		else if (DeviceHttpAccount.class.isInstance(existing)) {
			DeviceHttpAccount httpAccount = (DeviceHttpAccount) existing;
			DeviceHttpAccount rsHttpAccount = (DeviceHttpAccount) incoming;
			httpAccount.setUsername(rsHttpAccount.getUsername());
			if (rsHttpAccount.getPassword() != null && !"=".equals(rsHttpAccount.getPassword())) {
				httpAccount.setPassword(rsHttpAccount.getPassword());
			}
		}
		// Applied last so that switching a field to Vault mode always wins over
		// the unconditional local-value copies above (e.g. setUsername(...)).
		applyVaultableFields(session, existing, incoming);
		return existing;
	}
}
