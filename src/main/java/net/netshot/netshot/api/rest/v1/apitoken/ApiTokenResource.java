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
package net.netshot.netshot.api.rest.v1.apitoken;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.ApiTokenService;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListFilters;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.ListSchema;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.api.rest.v1.auth.AuthMapper;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * The API tokens (administration).
 */
@Path("/" + AuthMapper.API_TOKENS_PATH)
@Tag(name = "API tokens", description = "API tokens, to authenticate scripts and integrations")
public class ApiTokenResource {

	private final ApiTokenService apiTokenService;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

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
	 * Lists the API tokens.
	 *
	 * @return the page of API tokens
	 */
	@GET
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the API tokens", description = "Lists the API tokens (never their values), in their brief "
		+ "view by default.")
	@ListFilters(ListSchema.API_TOKENS)
	public ListResponse<ApiTokenDto> listApiTokens() {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.API_TOKENS.schema());
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), ApiTokenDto.class, Selection.View.BRIEF),
			ApiTokenDto.class);
		return ListResponse.of(this.uriInfo, query, this.apiTokenService.list(query), ApiTokenMapper::toDto);
	}

	/**
	 * Gets an API token.
	 *
	 * @param id the API token ID
	 * @return the API token
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get an API token", description = "Gets an API token, without its value.")
	public ApiTokenDto getApiToken(@PathParam("id") @Parameter(description = "API token ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), ApiTokenDto.class, Selection.View.FULL),
			ApiTokenDto.class);
		return ApiTokenMapper.toDto(this.apiTokenService.get(id));
	}

	/**
	 * Creates an API token.
	 *
	 * @param tokenRequest the definition of the API token
	 * @return the new API token, with its value
	 */
	@POST
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create an API token", description = "Creates an API token, with a random value or the one given "
		+ "(32 alphanumeric characters). The value is in the answer, and ONLY in that answer: Netshot only keeps a hash of "
		+ "it, so it can't be read afterwards.")
	public ApiTokenCreatedDto createApiToken(ApiTokenRequest tokenRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		String value = tokenRequest.token() == null ? ApiToken.generateToken() : tokenRequest.token().value();
		if (value == null) {
			throw NetshotException.invalidParameter("token", "The token value can't be null");
		}
		ApiToken token = this.apiTokenService.create(tokenRequest.description(), value, tokenRequest.level());
		V1Response.created(this.request, V1Links.of(AuthMapper.API_TOKENS_PATH, token.id()));
		V1Response.noStore(this.request);
		return ApiTokenMapper.toCreatedDto(token, value);
	}

	/**
	 * Deletes an API token.
	 *
	 * @param id the API token ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Operation(summary = "Delete an API token", description = "Deletes an API token, which can't authenticate anymore.")
	public void deleteApiToken(@PathParam("id") @Parameter(description = "API token ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		if (!this.apiTokenService.delete(id)) {
			throw new NetshotException(ErrorCode.NETSHOT_API_TOKEN_NOT_FOUND, "The API token doesn't exist.");
		}
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}
}
