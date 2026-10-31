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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.MergePatch;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.domain.DomainService;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * The device domains (v1): list, read, create, replace, update and delete.
 */
@Path("/" + DomainMapper.PATH)
@Tag(name = "Domains", description = "Device domains")
@Slf4j
public class DomainResource {

	private final DomainService domainService;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private HttpHeaders headers;

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
	 * Lists the domains.
	 *
	 * @return the page of domains
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the domains", description = "Lists the device domains, in their brief view by default.")
	public ListResponse<DomainDto> listDomains() {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), this.domainService.listSchema());
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), DomainDto.class, Selection.View.BRIEF),
			DomainDto.class);
		return ListResponse.of(this.uriInfo, query, this.domainService.list(query), DomainMapper::toDto);
	}

	/**
	 * Gets a domain.
	 *
	 * @param id the domain ID
	 * @return the domain
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a domain")
	public DomainDto getDomain(@PathParam("id") @Parameter(description = "Domain ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), DomainDto.class, Selection.View.FULL);
		return this.respond(this.domainService.get(id), selection);
	}

	/**
	 * Creates a domain.
	 *
	 * @param domainRequest the definition of the domain
	 * @return the new domain
	 */
	@POST
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a domain")
	public DomainDto createDomain(DomainRequest domainRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Domain domain = this.domainService.create(DomainMapper.toDefinition(domainRequest));
		V1Response.created(this.request, V1Links.of(DomainMapper.PATH, domain.id()));
		return this.respond(domain, Selection.of(this.uriInfo.getQueryParameters(), DomainDto.class, Selection.View.FULL));
	}

	/**
	 * Replaces a domain.
	 *
	 * @param id the domain ID
	 * @param domainRequest the new definition of the domain
	 * @return the domain
	 */
	@PUT
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Replace a domain", description = "Replaces the definition of a domain. "
		+ "With If-Match, the domain must still be at the given version (ETag).")
	public DomainDto replaceDomain(@PathParam("id") @Parameter(description = "Domain ID") long id, DomainRequest domainRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Domain domain = this.domainService.update(id, DomainMapper.toDefinition(domainRequest), V1Response.ifMatch(this.headers));
		return this.respond(domain, Selection.of(this.uriInfo.getQueryParameters(), DomainDto.class, Selection.View.FULL));
	}

	/**
	 * Updates a domain with a merge patch.
	 *
	 * @param id the domain ID
	 * @param patch the merge patch
	 * @return the domain
	 * @throws JsonProcessingException if the patched domain isn't a valid definition
	 */
	@PATCH
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({V1Mappers.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Update a domain", description = "Changes some properties of a domain (JSON merge patch, RFC 7396). "
		+ "With If-Match, the domain must still be at the given version (ETag).")
	public DomainDto updateDomain(@PathParam("id") @Parameter(description = "Domain ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch)
		throws JsonProcessingException {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Long expectedVersion = V1Response.ifMatch(this.headers);
		Domain current = this.domainService.get(id);
		DomainRequest patched = MergePatch.apply(DomainMapper.toRequest(current), patch, this.headers.getMediaType(), DomainRequest.class);
		Domain domain = this.domainService.update(id, DomainMapper.toDefinition(patched),
			expectedVersion == null ? Long.valueOf(current.version()) : expectedVersion);
		return this.respond(domain, Selection.of(this.uriInfo.getQueryParameters(), DomainDto.class, Selection.View.FULL));
	}

	/**
	 * Deletes a domain.
	 *
	 * @param id the domain ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Operation(summary = "Delete a domain", description = "Deletes a domain, which must not be used by any device or credential set. "
		+ "With If-Match, the domain must still be at the given version (ETag).")
	public void deleteDomain(@PathParam("id") @Parameter(description = "Domain ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		this.domainService.delete(id, V1Response.ifMatch(this.headers));
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	private DomainDto respond(Domain domain, Selection selection) {
		V1Response.select(this.request, selection, DomainDto.class);
		V1Response.etag(this.request, domain.version());
		return DomainMapper.toDto(domain);
	}
}
