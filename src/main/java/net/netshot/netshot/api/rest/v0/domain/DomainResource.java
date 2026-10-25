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

import java.util.List;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.PaginationParams;
import net.netshot.netshot.api.rest.ResponseCodeFilter;
import net.netshot.netshot.domain.DomainService;

/**
 * The REST resource of the device domains: list, add, edit and delete.
 *
 * <p>It only translates between HTTP and the {@link DomainService}. The class is mounted on
 * {@code /} with the full path on each method, like the other resources.</p>
 */
@Path("/")
@Slf4j
public class DomainResource {

	private final DomainService domainService;

	@Context
	private HttpServletRequest request;

	/**
	 * Instantiates the resource.
	 *
	 * @param domainService the domain use cases
	 */
	@Inject
	public DomainResource(DomainService domainService) {
		this.domainService = domainService;
	}

	/**
	 * Gets the domains.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the domains
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/domains")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the device domains",
		description = "Returns the list of device domains."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<DomainSummaryDto> getDomains(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, domains.");
		return DomainMapper.toSummaryDtos(this.domainService.list(paginationParams.toPage()));
	}

	/**
	 * Adds the domain.
	 *
	 * @param newDomain the new domain
	 * @return the domain
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/domains")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add a device domain",
		description = "Creates a device domain."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public DomainSummaryDto addDomain(DomainRequest newDomain) throws WebApplicationException {
		log.debug("REST request, add a domain");
		DomainSummaryDto domain = DomainMapper.toSummaryDto(this.domainService.create(DomainMapper.toDefinition(newDomain)));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return domain;
	}

	/**
	 * Sets the domain.
	 *
	 * @param id the id
	 * @param rsDomain the domain
	 * @return the domain
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/domains/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Update a device domain",
		description = "Edits a device domain, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public DomainSummaryDto setDomain(@PathParam("id") @Parameter(description = "Domain ID") Long id, DomainRequest rsDomain)
		throws WebApplicationException {
		log.debug("REST request, edit domain {}.", id);
		return DomainMapper.toSummaryDto(this.domainService.update(id, DomainMapper.toDefinition(rsDomain)));
	}

	/**
	 * Delete a domain.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/domains/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Remove a device domain",
		description = "Remove the given device domain, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public void deleteDomain(@PathParam("id") @Parameter(description = "Domain ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete domain {}.", id);
		this.domainService.delete(id);
		ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
	}
}
