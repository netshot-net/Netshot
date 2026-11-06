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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

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
import net.netshot.netshot.api.rest.v1.vault.VaultInstanceDto;
import net.netshot.netshot.api.rest.v1.vault.VaultInstanceMapper;
import net.netshot.netshot.credentials.CredentialSetService;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.vault.VaultInstanceService;

/**
 * The global credential sets of the v1 API: the accounts and SNMP communities Netshot tries on the devices.
 * Their secrets are write-only.
 */
@Path("/" + CredentialSetMapper.PATH)
@Tag(name = "Credential sets", description = "Credentials (accounts, SNMP communities) used to access the devices")
@Slf4j
public class CredentialSetResource {

	/** The properties of the representation which read a Vault instance. */
	private static final Set<String> VAULT_PROPERTIES = Set.of("usernameVault", "passwordVault", "superPasswordVault",
		"privateKeyVault", "communityVault", "authKeyVault", "privKeyVault");

	private final CredentialSetService credentialSetService;
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
	 * @param credentialSetService the credential set use cases
	 * @param vaultInstanceService the Vault instance use cases (to name the instances credentials refer to)
	 */
	@Inject
	public CredentialSetResource(CredentialSetService credentialSetService, VaultInstanceService vaultInstanceService) {
		this.credentialSetService = credentialSetService;
		this.vaultInstanceService = vaultInstanceService;
	}

	/**
	 * Lists the credential sets.
	 *
	 * @return the page of credential sets
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the credential sets", description = "Lists the global credential sets, in their brief view by "
		+ "default. The credential sets specific to a device access are left out, unless the deviceSpecific filter is given "
		+ "(deviceSpecific=true lists only those, deviceSpecific=false only the global ones). The secrets are never returned.")
	@ListFilters(ListSchema.CREDENTIAL_SETS)
	public ListResponse<CredentialSetDto> listCredentialSets() {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.CREDENTIAL_SETS.schema());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), CredentialSetDto.class, Selection.View.BRIEF);
		V1Response.select(this.request, selection, CredentialSetDto.class);
		var result = this.credentialSetService.list(query);
		LongFunction<VaultInstanceDto> vaults = this.vaults(selection, result.items());
		return ListResponse.of(this.uriInfo, query, result, c -> CredentialSetMapper.toDto(c, vaults));
	}

	/**
	 * Gets a credential set.
	 *
	 * @param id the credential set ID
	 * @return the credential set
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a credential set", description = "Gets a credential set. The secrets are never returned, "
		+ "only whether they are set.")
	public CredentialSetDto getCredentialSet(@PathParam("id") @Parameter(description = "Credential set ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), CredentialSetDto.class, Selection.View.FULL);
		return this.respond(this.credentialSetService.get(id), selection);
	}

	/**
	 * Creates a credential set.
	 *
	 * @param credentialSetRequest the definition of the credential set
	 * @return the new credential set
	 */
	@POST
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a credential set", description = "Creates a global credential set. The secrets the type needs "
		+ "must be given, by value or as a location in a Vault instance.")
	public CredentialSetDto createCredentialSet(CredentialSetRequest credentialSetRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		DeviceCredentialSet credentialSet = this.credentialSetService.create(CredentialSetMapper.toDefinition(credentialSetRequest));
		V1Response.created(this.request, V1Links.of(CredentialSetMapper.PATH, credentialSet.id()));
		return this.respond(credentialSet, Selection.of(this.uriInfo.getQueryParameters(), CredentialSetDto.class, Selection.View.FULL));
	}

	/**
	 * Replaces a credential set.
	 *
	 * @param id the credential set ID
	 * @param credentialSetRequest the new definition of the credential set
	 * @return the credential set
	 */
	@PUT
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Replace a credential set", description = "Replaces the definition of a global credential set (its "
		+ "type can't change). A username, secret or Vault location left out is unchanged; a secret set to null is cleared "
		+ "(which the secrets the type needs refuse). With If-Match, the credential set must still be at the given "
		+ "version (ETag).")
	public CredentialSetDto replaceCredentialSet(@PathParam("id") @Parameter(description = "Credential set ID") long id,
		CredentialSetRequest credentialSetRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		DeviceCredentialSet credentialSet = this.credentialSetService.update(id, CredentialSetMapper.toDefinition(credentialSetRequest),
			V1Response.ifMatch(this.headers));
		return this.respond(credentialSet, Selection.of(this.uriInfo.getQueryParameters(), CredentialSetDto.class, Selection.View.FULL));
	}

	/**
	 * Updates a credential set with a merge patch.
	 *
	 * @param id the credential set ID
	 * @param patch the merge patch
	 * @return the credential set
	 */
	@PATCH
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({V1Mappers.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Update a credential set", description = "Changes some properties of a global credential set "
		+ "(JSON merge patch, RFC 7396). The usernames and secrets are unchanged unless the patch gives them (or a Vault "
		+ "location replacing them). With If-Match, the credential set must still be at the given version (ETag).")
	public CredentialSetDto updateCredentialSet(@PathParam("id") @Parameter(description = "Credential set ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		DeviceCredentialSet credentialSet = this.credentialSetService.update(id, MergePatch.of(patch, this.headers.getMediaType(),
			CredentialSetRequest.class, CredentialSetMapper::toRequest, CredentialSetMapper::toDefinition), V1Response.ifMatch(this.headers));
		return this.respond(credentialSet, Selection.of(this.uriInfo.getQueryParameters(), CredentialSetDto.class, Selection.View.FULL));
	}

	/**
	 * Deletes a credential set.
	 *
	 * @param id the credential set ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Operation(summary = "Delete a credential set", description = "Deletes a global credential set, which must not be used by "
		+ "any device or task. With If-Match, the credential set must still be at the given version (ETag).")
	public void deleteCredentialSet(@PathParam("id") @Parameter(description = "Credential set ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		this.credentialSetService.delete(id, V1Response.ifMatch(this.headers));
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	private CredentialSetDto respond(DeviceCredentialSet credentialSet, Selection selection) {
		V1Response.select(this.request, selection, CredentialSetDto.class);
		V1Response.etag(this.request, credentialSet.version());
		return CredentialSetMapper.toDto(credentialSet, this.vaults(selection, List.of(credentialSet)));
	}

	/**
	 * Loads the Vault instances the credential sets read values from (in one query, and only if the response
	 * writes the locations).
	 *
	 * @param selection the selection of the response
	 * @param credentialSets the credential sets
	 * @return the Vault instances by ID (the ones not found, by ID only)
	 */
	private LongFunction<VaultInstanceDto> vaults(Selection selection, Collection<DeviceCredentialSet> credentialSets) {
		List<Long> ids = new ArrayList<>();
		if (VAULT_PROPERTIES.stream().anyMatch(selection::includes)) {
			credentialSets.forEach(c -> ids.addAll(CredentialSetMapper.vaultInstanceIds(c)));
		}
		return VaultInstanceMapper.resolver(this.vaultInstanceService, ids);
	}
}
