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
package net.netshot.netshot.api.rest.v0.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.domain.DomainDefinition;

/**
 * Maps the domains between the records of the inventory and their REST representations.
 * All the methods are pure functions.
 */
public final class DomainMapper {

	private DomainMapper() {
		// Static helpers only
	}

	private static Date date(Instant instant) {
		return instant == null ? null : Date.from(instant);
	}

	/**
	 * Maps a domain to its complete representation.
	 *
	 * @param domain the domain (may be null)
	 * @return the DTO, or null if there is no domain
	 */
	public static DomainDto toDto(Domain domain) {
		if (domain == null) {
			return null;
		}
		return new DomainDto(
			domain.id(),
			date(domain.changeDate()),
			domain.name(),
			domain.description(),
			domain.server4Address() == null ? null : domain.server4Address().getIp(),
			domain.server6Address() == null ? null : domain.server6Address().getIp());
	}

	/**
	 * Maps a domain to the representation given by the domain endpoints.
	 *
	 * @param domain the domain
	 * @return the DTO
	 */
	public static DomainSummaryDto toSummaryDto(Domain domain) {
		return new DomainSummaryDto(
			domain.id(),
			domain.name(),
			domain.description(),
			domain.server4Address() == null ? null : domain.server4Address().getIp());
	}

	/**
	 * Maps the domains to the representation given by the domain endpoints.
	 *
	 * @param domains the domains
	 * @return the DTOs, in the same order
	 */
	public static List<DomainSummaryDto> toSummaryDtos(List<Domain> domains) {
		List<DomainSummaryDto> dtos = new ArrayList<>(domains.size());
		domains.forEach(domain -> dtos.add(toSummaryDto(domain)));
		return dtos;
	}

	/**
	 * Maps a request to add or edit a domain to the definition given to the service.
	 *
	 * @param request the request
	 * @return the definition
	 */
	public static DomainDefinition toDefinition(DomainRequest request) {
		return new DomainDefinition(request.getName(), request.getDescription(), request.getIpAddress());
	}
}
