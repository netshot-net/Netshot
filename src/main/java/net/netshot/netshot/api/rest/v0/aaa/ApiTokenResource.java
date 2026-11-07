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
package net.netshot.netshot.api.rest.v0.aaa;

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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.ApiTokenService;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v0.PaginationParams;
import net.netshot.netshot.api.rest.v0.ResponseCodeFilter;

/**
 * The REST resource of the API tokens (administrators only): list, add and delete.
 *
 * <p>It only translates between HTTP and the {@link ApiTokenService}. The token value is read from
 * the request of the creation and is never written back. The class is mounted on {@code /} with
 * the full path on each method.</p>
 */
@Path("/")
@Slf4j
public class ApiTokenResource {

	private final ApiTokenService apiTokenService;

	@Context
	private HttpServletRequest request;

	/**
	 * Instantiates the resource.
	 *
	 * @param apiTokenService the API token use cases
	 */
	@Inject
	public ApiTokenResource(ApiTokenService apiTokenService) {
		this.apiTokenService = apiTokenService;
	}

	/**
	 * Adds the API token.
	 *
	 * @param rsApiToken the posted API token
	 * @return the API token
	 */
	@POST
	@Path("/apitokens")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add a new API token",
		description = "Creates a new API token."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public ApiTokenDto addApiToken(ApiTokenRequest rsApiToken) {
		log.debug("REST request, add API token");
		ApiTokenDto apiToken = UserMapper.toDto(
			this.apiTokenService.create(rsApiToken.description(), rsApiToken.token(), rsApiToken.level()));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return apiToken;
	}

	/**
	 * Gets the API tokens.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the API tokens
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/apitokens")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the API tokens",
		description = "Returns the list of API tokens."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<ApiTokenDto> getApiTokens(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, get API token list.");
		return UserMapper.toApiTokenDtos(this.apiTokenService.list(paginationParams.toPage()));
	}

	/**
	 * Delete API token.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/apitokens/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Remove an API token",
		description = "Removes an API token, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public void deleteApiToken(@PathParam("id") @Parameter(description = "Token ID") Long id) throws WebApplicationException {
		log.debug("REST request, delete API token {}.", id);
		if (this.apiTokenService.delete(id)) {
			ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
		}
		else {
			ResponseCodeFilter.suggest(this.request, Response.Status.NOT_FOUND);
		}
	}
}
