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

import java.time.Instant;

import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * An external secret store (Vault server) the secrets of the credential sets can be read from.
 */
public sealed interface VaultInstance permits HashicorpVaultKv2Instance {

	/**
	 * Gets the ID.
	 *
	 * @return the ID (0 for an instance not stored yet)
	 */
	long id();

	/**
	 * Gets the version, incremented at each change (optimistic locking).
	 *
	 * @return the version
	 */
	int version();

	/**
	 * Gets the date of the last change.
	 *
	 * @return the date (null for an instance not stored yet)
	 */
	Instant changeDate();

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * Gets the base URL of the Vault server.
	 *
	 * @return the URL
	 */
	String baseUrl();

	/**
	 * Gets the namespace (Vault Enterprise).
	 *
	 * @return the namespace
	 */
	String namespace();

	/**
	 * Gets how the HTTPS certificate of the server is trusted.
	 *
	 * @return the trust mode
	 */
	HttpsCaTrustMode httpsCaTrustMode();

	/**
	 * Gets the CA certificate trusted in {@link HttpsCaTrustMode#CUSTOM_CA} mode.
	 *
	 * @return the PEM certificate
	 */
	String httpsCustomCaCertificate();

	/**
	 * Gets a copy of the instance with another ID.
	 *
	 * @param id the ID
	 * @return the instance
	 */
	VaultInstance withId(long id);

	/**
	 * Describes the instance, for the logs (without secret).
	 *
	 * @param instance the instance
	 * @return the description
	 */
	static String describe(VaultInstance instance) {
		return "Vault Instance " + instance.id() + " (name '" + instance.name() + "', type " + instance.getClass().getSimpleName() + ")";
	}
}
