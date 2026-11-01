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
package net.netshot.netshot.api.rest.v1.domain;

import java.util.stream.Collectors;

import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.domain.DomainService;
import net.netshot.netshot.api.rest.v1.StatsResponse;
import net.netshot.netshot.api.rest.v1.RefDto;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.domain.DomainDefinition;

/**
 * Maps the domains to their v1 representation, and the requests to the definitions the service takes.
 */
public final class DomainMapper {

	/** The path of the domains. */
	public static final String PATH = "domains";

	private DomainMapper() {
	}

	/**
	 * Maps a domain.
	 *
	 * @param domain the domain (null gives null)
	 * @return the representation
	 */
	public static DomainDto toDto(Domain domain) {
		if (domain == null) {
			return null;
		}
		return new DomainDto(domain.id(), V1Links.of(PATH, domain.id()), domain.name(), domain.version(),
			domain.changeDate(), domain.name(), domain.description(),
			domain.server4Address() == null ? null : domain.server4Address().getIp(),
			domain.server6Address() == null ? null : domain.server6Address().getIp());
	}

	/**
	 * Maps a domain to the request which would define it as it is (the base of a merge patch).
	 *
	 * @param domain the domain
	 * @return the request
	 */
	public static DomainRequest toRequest(Domain domain) {
		return new DomainRequest(domain.name(), domain.description(),
			domain.server4Address() == null ? null : domain.server4Address().getIp());
	}

	/**
	 * Maps a request to the definition the service takes.
	 *
	 * @param request the request
	 * @return the definition
	 */
	public static DomainDefinition toDefinition(DomainRequest request) {
		return new DomainDefinition(request.name(), request.description(), request.server4Address());
	}

	/**
	 * Builds the resolver of domain IDs into references (statistics grouped by domain).
	 *
	 * @param domainService the domain use cases
	 * @return the resolver
	 */
	public static StatsResponse.Resolver resolver(DomainService domainService) {
		return ids -> domainService.list(ListQuery.byIds(ids)).items().stream()
			.collect(Collectors.toMap(Domain::id, d -> new RefDto(d.id(), V1Links.of(PATH, d.id()), d.name())));
	}
}
