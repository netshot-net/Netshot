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
package net.netshot.netshot.api.rest.v1.credentials;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.domain.DomainDto;

/**
 * A credential set, as seen from a device access. The secrets are never written.
 *
 * @param id the ID
 * @param url the path of the credential set (null for a set specific to a device)
 * @param display the display name
 * @param version the version
 * @param changeDate the date of the last change
 * @param name the name
 * @param type the type (SSH, SSH_KEY, TELNET, HTTP, SNMP_V1, SNMP_V2C, SNMP_V3)
 * @param mgmtDomain the domain the set is restricted to (null for all)
 * @param deviceSpecific whether the set is specific to a device access
 * @param username the user name (CLI, HTTP and SNMPv3 sets)
 */
@Schema(name = "CredentialSet", description = "Device credential set (secrets are never returned)")
public record CredentialSetDto(
	long id,
	String url,
	String display,
	long version,
	Instant changeDate,
	@Brief String name,
	@Brief String type,
	@Ref DomainDto mgmtDomain,
	boolean deviceSpecific,
	String username) {
}
