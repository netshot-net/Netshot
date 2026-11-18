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
package net.netshot.netshot.api.rest.v0.credentials;

import java.util.List;
import java.util.stream.Stream;


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
import net.netshot.netshot.api.rest.v0.V0Lists;
import net.netshot.netshot.api.rest.v0.V0Errors;
import net.netshot.netshot.credentials.CredentialSetService;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The REST resource of the global credential sets: list, add, edit and delete.
 *
 * <p>It only translates between HTTP and the {@link CredentialSetService}. The class is mounted on
 * {@code /} with the full path on each method, like the other resources. The secrets are never
 * written by the representation (see {@link CredentialSetDto}).</p>
 */
@Path("/")
@Slf4j
public class CredentialSetResource {

	private final CredentialSetService credentialSetService;

	/** An unknown Vault instance, in any of the secret fields, as v0 always reported it. */
	private static final V0Errors.Translation[] INVALID_VAULT = Stream.of("username", "password", "superPassword", "privateKey",
		"community", "authKey", "privKey")
		.map(field -> V0Errors.target(field + "Vault", ErrorCode.NETSHOT_INVALID_VAULT_INSTANCE, "Invalid Vault instance."))
		.toArray(V0Errors.Translation[]::new);

	@Context
	private HttpServletRequest request;

	/**
	 * Instantiates the resource.
	 *
	 * @param credentialSetService the credential set use cases
	 */
	@Inject
	public CredentialSetResource(CredentialSetService credentialSetService) {
		this.credentialSetService = credentialSetService;
	}

	/**
	 * Gets the credential sets.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the credential sets
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/credentialsets")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the global credential sets",
		description = "Returns the list of global credential sets (SSH, SNMP, etc. accounts) for authentication against the devices."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public List<CredentialSetDto> getCredentialSets(@BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, get credentials.");
		return CredentialSetMapper.toDtos(this.credentialSetService.list(V0Lists.query(paginationParams.toPage())).items());
	}

	/**
	 * Delete credential set.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/credentialsets/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Remove a credential set",
		description = "Removes the given credential set, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public void deleteCredentialSet(@PathParam("id") @Parameter(description = "Credential set ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete credential set {}", id);
		if (V0Errors.found(() -> this.credentialSetService.delete(id, null), ErrorCode.NETSHOT_CREDENTIALS_NOT_FOUND)) {
			ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
		}
		else {
			ResponseCodeFilter.suggest(this.request, Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Adds the credential set.
	 *
	 * @param credentialSet the credential set
	 * @return the device credential set
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/credentialsets")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add a credential set",
		description = "Creates a credential set, which then can be used to authenticate against the devices."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public CredentialSetDto addCredentialSet(CredentialSetDto credentialSet)
		throws WebApplicationException {
		log.debug("REST request, add credentials.");
		DeviceCredentialSet posted = CredentialSetMapper.toEntity(credentialSet);
		// The name is checked before the domain
		if (posted.name() == null || posted.name().trim().isEmpty()) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_NAME, "Invalid name for the credential set");
		}
		DeviceCredentialSet created = V0Errors.translate(
			() -> this.credentialSetService.create(CredentialSetMapper.toDefinition(posted, null)),
			translations(V0Errors.target("mgmtDomain", ErrorCode.NETSHOT_INVALID_DOMAIN, "Invalid domain")));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return CredentialSetMapper.toDto(created);
	}

	/**
	 * Sets the credential set.
	 *
	 * @param id the id
	 * @param rsCredentialSet the rs credential set
	 * @return the device credential set
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/credentialsets/{id}")
	@RolesAllowed(User.ROLE_ADMIN)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Update a credential set",
		description = "Edits a credential set, by ID."
	)
	@Tag(name = "Admin", description = "Administrative actions")
	public CredentialSetDto setCredentialSet(@PathParam("id") @Parameter(description = "Credential set ID") Long id,
		CredentialSetDto rsCredentialSet) throws WebApplicationException {
		log.debug("REST request, edit credentials {}", id);
		DeviceCredentialSet posted = CredentialSetMapper.toEntity(rsCredentialSet);
		DeviceCredentialSet updated = V0Errors.translate(() -> this.credentialSetService.update(id, existing -> {
			if (existing instanceof DeviceSshKeyAccount && posted instanceof DeviceSshAccount) {
				// The SSH key accounts used to be a kind of SSH accounts
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS_TYPE,
					"The posted credential type doesn't match the existing one.");
			}
			if (!existing.getClass().equals(posted.getClass())) {
				// A credential set of another type was not found
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_CREDENTIALS, "Unable to find the credential set.");
			}
			return CredentialSetMapper.toDefinition(posted, existing);
		}, null), translations(
			V0Errors.code(ErrorCode.NETSHOT_CREDENTIALS_NOT_FOUND, ErrorCode.NETSHOT_INVALID_CREDENTIALS, "Unable to find the credential set."),
			V0Errors.target("mgmtDomain", ErrorCode.NETSHOT_INVALID_DOMAIN, "The domain doesn't exist.")));
		return CredentialSetMapper.toDto(updated);
	}

	private static V0Errors.Translation[] translations(V0Errors.Translation... translations) {
		return Stream.concat(Stream.of(translations), Stream.of(INVALID_VAULT)).toArray(V0Errors.Translation[]::new);
	}
}
