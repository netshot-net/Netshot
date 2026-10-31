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
package net.netshot.netshot.vault;

/**
 * What a client wants done to a secret field when it defines an object: keep it, clear it, give it a (local)
 * value, or make it read from a Vault instance. A field a client doesn't mention is {@link #UNCHANGED}.
 */
public sealed interface SecretChange {

	/** Keep the current value (or reference). */
	SecretChange UNCHANGED = new Unchanged();

	/** Remove the current value (and reference). */
	SecretChange CLEARED = new Cleared();

	/** The field is kept as it is. */
	record Unchanged() implements SecretChange {
	}

	/** The field is emptied. */
	record Cleared() implements SecretChange {
	}

	/**
	 * The field gets a local (encrypted-at-rest) value, and no Vault reference.
	 *
	 * @param value the value
	 */
	record Local(String value) implements SecretChange {
	}

	/**
	 * The field is read from a Vault instance, and has no local value.
	 *
	 * @param vaultInstanceId the ID of the Vault instance
	 * @param vaultPath the path (and key) of the secret in the instance
	 */
	record Referenced(long vaultInstanceId, String vaultPath) implements SecretChange {
	}

	/**
	 * Applies the change to the current state of a field.
	 *
	 * @param current the current state
	 * @return the new state (the Vault instance is not checked)
	 */
	default VaultableSecret applyTo(VaultableSecret current) {
		return switch (this) {
			case Unchanged _ -> current;
			case Cleared _ -> new VaultableSecret(null, null, null);
			case Local l -> new VaultableSecret(l.value(), null, null);
			case Referenced r -> new VaultableSecret(null, r.vaultInstanceId(), r.vaultPath());
		};
	}
}
