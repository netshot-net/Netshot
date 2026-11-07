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
package net.netshot.netshot.vault;

/**
 * What the Vault instance use cases need to try a login to a Vault server, without
 * persisting anything. The composition root ({@code boot.Wiring}) provides the real one
 * ({@link VaultManager#testConnection}), the tests a fake one.
 */
@FunctionalInterface
public interface VaultConnectionTester {

	/**
	 * Attempts a fresh login to the Vault instance.
	 *
	 * @param instance the Vault instance to test (possibly not persisted)
	 * @throws VaultException if the login fails
	 */
	void test(HashicorpVaultKv2Instance instance) throws VaultException;
}
