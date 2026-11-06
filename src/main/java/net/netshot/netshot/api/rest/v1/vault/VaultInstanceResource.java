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
package net.netshot.netshot.api.rest.v1.vault;

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
import net.netshot.netshot.api.rest.v1.ListFilters;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.ListSchema;
import net.netshot.netshot.api.rest.v1.MergePatch;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.vault.VaultInstance;
import net.netshot.netshot.vault.VaultInstanceService;

/**
 * The Vault instances of the v1 API: external secret stores the secrets of the credential sets can be read from.
 * The secrets of the instances are write-only.
 */
@Path("/" + VaultInstanceMapper.PATH)
@Tag(name = "Vault instances", description = "External secret stores (Vault) the credential secrets can be read from")
@Slf4j
public class VaultInstanceResource {

	private final VaultInstanceService vaultInstanceService;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private HttpHeaders headers;

	/**
	 * Instantiates the resource.
	 *
	 * @param vaultInstanceService the Vault instance use cases
	 */
	@Inject
	public VaultInstanceResource(VaultInstanceService vaultInstanceService) {
		this.vaultInstanceService = vaultInstanceService;
	}

	/**
	 * Lists the Vault instances.
	 *
	 * @return the page of Vault instances
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the Vault instances", description = "Lists the Vault instances, in their brief view by default. "
		+ "The secrets are never returned.")
	@ListFilters(ListSchema.VAULT_INSTANCES)
	public ListResponse<VaultInstanceDto> listVaultInstances() {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.VAULT_INSTANCES.schema());
		V1Response.select(this.request, Selection.of(this.uriInfo.getQueryParameters(), VaultInstanceDto.class, Selection.View.BRIEF),
			VaultInstanceDto.class);
		return ListResponse.of(this.uriInfo, query, this.vaultInstanceService.list(query), VaultInstanceMapper::toDto);
	}

	/**
	 * Gets a Vault instance.
	 *
	 * @param id the Vault instance ID
	 * @return the Vault instance
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a Vault instance", description = "Gets a Vault instance. The secrets are never returned, "
		+ "only whether they are set.")
	public VaultInstanceDto getVaultInstance(@PathParam("id") @Parameter(description = "Vault instance ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), VaultInstanceDto.class, Selection.View.FULL);
		return this.respond(this.vaultInstanceService.get(id), selection);
	}

	/**
	 * Creates a Vault instance.
	 *
	 * @param vaultInstanceRequest the definition of the Vault instance
	 * @return the new Vault instance
	 */
	@POST
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a Vault instance", description = "Creates a Vault instance. The secrets required by "
		+ "the authentication method must be given.")
	public VaultInstanceDto createVaultInstance(VaultInstanceRequest vaultInstanceRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		VaultInstance vaultInstance = this.vaultInstanceService.create(VaultInstanceMapper.toDefinition(vaultInstanceRequest));
		V1Response.created(this.request, V1Links.of(VaultInstanceMapper.PATH, vaultInstance.id()));
		return this.respond(vaultInstance, Selection.of(this.uriInfo.getQueryParameters(), VaultInstanceDto.class, Selection.View.FULL));
	}

	/**
	 * Replaces a Vault instance.
	 *
	 * @param id the Vault instance ID
	 * @param vaultInstanceRequest the new definition of the Vault instance
	 * @return the Vault instance
	 */
	@PUT
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Replace a Vault instance", description = "Replaces the definition of a Vault instance. "
		+ "A secret left out is unchanged; a secret set to null is cleared (which an authentication method needing it refuses). "
		+ "With If-Match, the Vault instance must still be at the given version (ETag).")
	public VaultInstanceDto replaceVaultInstance(@PathParam("id") @Parameter(description = "Vault instance ID") long id,
		VaultInstanceRequest vaultInstanceRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		VaultInstance vaultInstance = this.vaultInstanceService.update(id, VaultInstanceMapper.toDefinition(vaultInstanceRequest),
			V1Response.ifMatch(this.headers));
		return this.respond(vaultInstance, Selection.of(this.uriInfo.getQueryParameters(), VaultInstanceDto.class, Selection.View.FULL));
	}

	/**
	 * Updates a Vault instance with a merge patch.
	 *
	 * @param id the Vault instance ID
	 * @param patch the merge patch
	 * @return the Vault instance
	 */
	@PATCH
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({V1Mappers.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Update a Vault instance", description = "Changes some properties of a Vault instance "
		+ "(JSON merge patch, RFC 7396). The secrets are unchanged unless the patch gives them. "
		+ "With If-Match, the Vault instance must still be at the given version (ETag).")
	public VaultInstanceDto updateVaultInstance(@PathParam("id") @Parameter(description = "Vault instance ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		VaultInstance vaultInstance = this.vaultInstanceService.update(id, MergePatch.of(patch, this.headers.getMediaType(),
			VaultInstanceRequest.class, VaultInstanceMapper::toRequest, VaultInstanceMapper::toDefinition), V1Response.ifMatch(this.headers));
		return this.respond(vaultInstance, Selection.of(this.uriInfo.getQueryParameters(), VaultInstanceDto.class, Selection.View.FULL));
	}

	/**
	 * Deletes a Vault instance.
	 *
	 * @param id the Vault instance ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Operation(summary = "Delete a Vault instance", description = "Deletes a Vault instance, which must not be referenced "
		+ "by any credential field. With If-Match, the Vault instance must still be at the given version (ETag).")
	public void deleteVaultInstance(@PathParam("id") @Parameter(description = "Vault instance ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		this.vaultInstanceService.delete(id, V1Response.ifMatch(this.headers));
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	/**
	 * Tests the connection to a saved Vault instance.
	 *
	 * @param id the Vault instance ID
	 * @return the outcome of the test
	 */
	@POST
	@Path("/{id}/test")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Test a Vault instance", description = "Tries to log in to a saved Vault instance, without changing "
		+ "anything. A failed login is a successful answer with success=false and the reason.")
	public VaultConnectionTestDto testVaultInstance(@PathParam("id") @Parameter(description = "Vault instance ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return VaultInstanceMapper.toDto(this.vaultInstanceService.test(id));
	}

	/**
	 * Tests the connection to a Vault instance which isn't saved.
	 *
	 * @param vaultInstanceRequest the definition of the Vault instance to test
	 * @return the outcome of the test
	 */
	@POST
	@Path("/test")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Test a Vault instance definition", description = "Tries to log in with a Vault instance definition "
		+ "which isn't saved (it must be complete, secrets included). A failed login is a successful answer with "
		+ "success=false and the reason.")
	public VaultConnectionTestDto testVaultInstanceDefinition(VaultInstanceRequest vaultInstanceRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return VaultInstanceMapper.toDto(this.vaultInstanceService.test(VaultInstanceMapper.toDefinition(vaultInstanceRequest)));
	}

	private VaultInstanceDto respond(VaultInstance vaultInstance, Selection selection) {
		V1Response.select(this.request, selection, VaultInstanceDto.class);
		V1Response.etag(this.request, vaultInstance.version());
		return VaultInstanceMapper.toDto(vaultInstance);
	}
}
