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

/**
 * Applies the definitions of the credential sets specific to the device accesses onto the stored ones.
 */
public final class CredentialSets {

	private CredentialSets() {
		// Static helpers only
	}

	/**
	 * Creates or updates the credential set specific to a device access from a definition: the secrets the definition
	 * leaves out are kept from the current credential set when it is of the same type, otherwise a new specific
	 * credential set is stored (a replaced one is not removed: the caller removes it once the access doesn't reference
	 * it anymore).
	 *
	 * @param dsl the jOOQ context
	 * @param repository the credential set repository
	 * @param assembler the credential set assembler
	 * @param accessName the name of the access
	 * @param existing the current specific credential set of the access, or null if there is none
	 * @param definition the definition (its name and domain are not used)
	 * @param expectedClass the class of credential set the access takes
	 * @return the stored credential set
	 * @throws NetshotException if the definition is invalid or incomplete, a referenced Vault instance doesn't
	 *         exist, or the credential set is not of the class the access takes
	 */
	public static DeviceCredentialSet applySpecificDefinition(DSLContext dsl, CredentialSetRepository repository, CredentialSetAssembler assembler,
			String accessName,
			DeviceCredentialSet existing, CredentialSetDefinition definition, Class<? extends DeviceCredentialSet> expectedClass) {
		DeviceCredentialSet base = existing != null && CredentialSetDefinition.Kind.of(existing) == definition.kind() ? existing : null;
		String name = base != null ? base.name() : DeviceCredentialSet.generateSpecificName();
		CredentialSetDefinition named = new CredentialSetDefinition(definition.kind(), name, null, definition.username(),
			definition.password(), definition.superPassword(), definition.privateKey(), definition.community(), definition.authType(),
			definition.authKey(), definition.privType(), definition.privKey());
		DeviceCredentialSet assembled = assembler.assemble(dsl, base, named, null);
		if (!expectedClass.isInstance(assembled)) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE,
				"The specific credential set is not compatible with access '" + accessName + "'.");
		}
		if (base == null) {
			return repository.add(dsl, assembled.withIdentity(0, name, true));
		}
		return repository.save(dsl, assembled);
	}
}
