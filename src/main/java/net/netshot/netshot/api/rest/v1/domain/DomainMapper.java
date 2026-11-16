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
package net.netshot.netshot.api.rest.v1.domain;

import java.util.stream.Collectors;

import jakarta.inject.Inject;

import net.netshot.netshot.api.rest.v1.RefDto;
import net.netshot.netshot.api.rest.v1.StatsResponse;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.domain.DomainDefinition;
import net.netshot.netshot.domain.DomainService;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * Maps the domains to their v1 representation, and the requests to the definitions the service takes.
 */
public class DomainMapper {

	/** The path of the domains. */
	public static final String PATH = "domains";

	/** The paths of the v1 objects. */
	private final V1Links links;

	/**
	 * Instantiates the mapper.
	 *
	 * @param links the paths of the v1 objects
	 */
	@Inject
	public DomainMapper(V1Links links) {
		this.links = links;
	}

	/**
	 * Maps a domain.
	 *
	 * @param domain the domain (null gives null)
	 * @return the representation
	 */
	public DomainDto toDto(Domain domain) {
		if (domain == null) {
			return null;
		}
		return new DomainDto(domain.id(), this.links.of(PATH, domain.id()), domain.name(), domain.version(),
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
	public DomainRequest toRequest(Domain domain) {
		return new DomainRequest(domain.name(), domain.description(),
			domain.server4Address() == null ? null : domain.server4Address().getIp());
	}

	/**
	 * Maps a request to the definition the service takes.
	 *
	 * @param request the request
	 * @return the definition
	 */
	public DomainDefinition toDefinition(DomainRequest request) {
		return new DomainDefinition(request.name(), request.description(), request.server4Address());
	}

	/**
	 * Builds the resolver of domain IDs into references (statistics grouped by domain).
	 *
	 * @param domainService the domain use cases
	 * @return the resolver
	 */
	public StatsResponse.Resolver resolver(DomainService domainService) {
		return ids -> domainService.list(ListQuery.byIds(ids)).items().stream()
			.collect(Collectors.toMap(Domain::id, d -> new RefDto(d.id(), this.links.of(PATH, d.id()), d.name())));
	}
}
