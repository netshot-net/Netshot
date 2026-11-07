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
package net.netshot.netshot.api.rest.v0.compliance;

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
import net.netshot.netshot.api.rest.v0.PaginationParams;
import net.netshot.netshot.api.rest.v0.ResponseCodeFilter;
import net.netshot.netshot.compliance.PolicyService;

/**
 * The REST resource of the compliance policies: list, add, edit and delete.
 *
 * <p>It only translates between HTTP and the {@link PolicyService}. The class is mounted on
 * {@code /} with the full path on each method (see {@code DeviceResource}); the rules of a
 * policy are served by {@link RuleResource}.</p>
 */
@Path("/")
@Slf4j
public class PolicyResource {

	private final PolicyService policyService;

	@Context
	private HttpServletRequest request;

	/**
	 * Instantiates the resource.
	 *
	 * @param policyService the policy use cases
	 */
	@Inject
	public PolicyResource(PolicyService policyService) {
		this.policyService = policyService;
	}

	/**
	 * Gets the policies.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the policies
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/policies")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the compliance policies",
		description = "Returns the list of compliance policies."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<PolicyDto> getPolicies(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, get policies.");
		return PolicyMapper.toDtos(this.policyService.list(paginationParams.toPage()));
	}

	/**
	 * Adds the policy.
	 *
	 * @param rsPolicy the policy
	 * @return the policy
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/policies")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add a compliance policy",
		description = "Creates a compliance policy."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public PolicyDto addPolicy(PolicyRequest rsPolicy) throws WebApplicationException {
		log.debug("REST request, add policy.");
		PolicyDto policy = PolicyMapper.toDto(this.policyService.create(PolicyMapper.toDefinition(rsPolicy)));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return policy;
	}

	/**
	 * Delete policy.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/policies/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Remove a compliance policy",
		description = "Removes a given compliance policy, by ID"
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public void deletePolicy(@PathParam("id") @Parameter(description = "Policy ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete policy {}.", id);
		if (this.policyService.delete(id)) {
			ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
		}
		else {
			ResponseCodeFilter.suggest(this.request, Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Sets the policy.
	 *
	 * @param id the id
	 * @param rsPolicy the policy
	 * @return the policy
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/policies/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Update a compliance policy",
		description = "Edits a compliance policy, by ID."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public PolicyDto setPolicy(@PathParam("id") @Parameter(description = "Policy ID") Long id, PolicyRequest rsPolicy)
		throws WebApplicationException {
		log.debug("REST request, edit policy {}.", id);
		return PolicyMapper.toDto(this.policyService.update(id, PolicyMapper.toDefinition(rsPolicy)));
	}
}
