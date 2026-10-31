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
package net.netshot.netshot.inventory.device;

import net.netshot.netshot.credentials.CredentialSetDefinition;

/**
 * The definition of the credential set specific to a device access, as a command: the secrets it leaves out are
 * kept, and it is stored by the device service together with the device.
 *
 * @param accessName the name of the access the credential set is specific to
 * @param definition the definition (its name is not used: the credential set of an access is named by the server)
 */
public record AccessSpecificCredential(String accessName, CredentialSetDefinition definition) {
}
