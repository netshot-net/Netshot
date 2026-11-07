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
 * What the Vault instance use cases need to tell the rest of the system (this node and the
 * other nodes of the cluster) that a Vault instance has been edited or deleted, so that the
 * tokens and secrets cached for it are dropped. The composition root ({@code boot.Wiring})
 * provides the real one ({@link VaultManager} and the cluster manager), the tests a recording one.
 */
@FunctionalInterface
public interface VaultInstanceChangeNotifier {

	/**
	 * Called once the change of a Vault instance has been committed.
	 *
	 * @param vaultInstanceId the ID of the Vault instance
	 */
	void changed(long vaultInstanceId);
}
