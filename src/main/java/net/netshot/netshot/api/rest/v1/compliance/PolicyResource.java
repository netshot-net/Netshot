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
package net.netshot.netshot.api.rest.v1.compliance;

import java.util.List;

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
import net.netshot.netshot.api.rest.v1.device.DeviceRefs;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PolicyDefinition;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;

/**
 * The compliance policies (v1): list, read, create, replace, update and delete, and their rules.
 */
@Path("/" + PolicyMapper.PATH)
@Tag(name = "Compliance policies", description = "Compliance policies, sets of rules checked on the devices of target groups")
public class PolicyResource {

	private final PolicyService policyService;
	private final RuleService ruleService;
	private final ComplianceRefs refs;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	@Context
	private HttpHeaders headers;

	/**
	 * Instantiates the resource.
	 *
	 * @param policyService the policy use cases
	 * @param ruleService the rule use cases
	 * @param groupService the group use cases
	 * @param deviceRefs writes the devices
	 */
	@Inject
	public PolicyResource(PolicyService policyService, RuleService ruleService, GroupService groupService, DeviceRefs deviceRefs) {
		this.policyService = policyService;
		this.ruleService = ruleService;
		this.refs = new ComplianceRefs(groupService, policyService, ruleService, deviceRefs);
	}

	/**
	 * Lists the policies.
	 *
	 * @return the page of policies
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the compliance policies", description = "Lists the compliance policies, in their brief view by default. "
		+ "The targetGroup filter gives the policies applying to a group.")
	@ListFilters(ListSchema.POLICIES)
	public ListResponse<PolicyDto> listPolicies() {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.POLICIES.schema());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), PolicyDto.class, Selection.View.BRIEF);
		V1Response.select(this.request, selection, PolicyDto.class);
		var policies = this.policyService.list(query);
		var groups = this.refs.groups(selection, "targetGroups", policies.items().stream().flatMap(p -> p.targetGroupIds().stream()).toList());
		return ListResponse.of(this.uriInfo, query, policies, p -> PolicyMapper.toDto(p, groups));
	}

	/**
	 * Gets a policy.
	 *
	 * @param id the policy ID
	 * @return the policy
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a compliance policy")
	public PolicyDto getPolicy(@PathParam("id") @Parameter(description = "Policy ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.policyService.get(id));
	}

	/**
	 * Lists the rules of a policy.
	 *
	 * @param id the policy ID
	 * @return the page of rules
	 */
	@GET
	@Path("/{id}/rules")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the rules of a policy", description = "Lists the rules of a policy, "
		+ "as the rule list filtered on the policy (same parameters).")
	@ListFilters(ListSchema.RULES)
	public ListResponse<RuleDto> listPolicyRules(@PathParam("id") @Parameter(description = "Policy ID") long id) {
		this.policyService.get(id);
		return RuleResource.list(this.ruleService, this.refs, this.request, this.uriInfo, new Criterion("policy", Lookup.EXACT, List.of(id)));
	}

	/**
	 * Creates a policy.
	 *
	 * @param policyRequest the definition of the policy
	 * @return the new policy
	 */
	@POST
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a compliance policy", description = "Creates a compliance policy, without rules.")
	public PolicyDto createPolicy(PolicyRequest policyRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Policy policy = this.policyService.add(PolicyMapper.toDefinition(policyRequest));
		V1Response.created(this.request, V1Links.of(PolicyMapper.PATH, policy.id()));
		return this.respond(policy);
	}

	/**
	 * Replaces a policy.
	 *
	 * @param id the policy ID
	 * @param policyRequest the new definition of the policy
	 * @return the policy
	 */
	@PUT
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Replace a compliance policy", description = "Replaces the name and the target groups of a policy. "
		+ "The compliance results of the devices of a group removed from the policy are deleted. With If-Match, the policy must "
		+ "still be at the given version (ETag).")
	public PolicyDto replacePolicy(@PathParam("id") @Parameter(description = "Policy ID") long id, PolicyRequest policyRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		PolicyDefinition definition = PolicyMapper.toDefinition(policyRequest);
		return this.respond(this.policyService.replace(id, _ -> definition, V1Response.ifMatch(this.headers)));
	}

	/**
	 * Updates a policy with a merge patch.
	 *
	 * @param id the policy ID
	 * @param patch the merge patch
	 * @return the policy
	 */
	@PATCH
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({V1Mappers.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Update a compliance policy", description = "Changes some properties of a policy (JSON merge patch, RFC 7396). "
		+ "With If-Match, the policy must still be at the given version (ETag).")
	public PolicyDto updatePolicy(@PathParam("id") @Parameter(description = "Policy ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.policyService.replace(id, MergePatch.of(patch, this.headers.getMediaType(), PolicyRequest.class,
			PolicyMapper::toRequest, PolicyMapper::toDefinition), V1Response.ifMatch(this.headers)));
	}

	/**
	 * Deletes a policy.
	 *
	 * @param id the policy ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Operation(summary = "Delete a compliance policy", description = "Deletes a policy, with its rules, their exemptions and results. "
		+ "With If-Match, the policy must still be at the given version (ETag).")
	public void deletePolicy(@PathParam("id") @Parameter(description = "Policy ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		if (!this.policyService.delete(id, V1Response.ifMatch(this.headers))) {
			throw new NetshotException(ErrorCode.NETSHOT_POLICY_NOT_FOUND, "The policy doesn't exist.");
		}
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	private PolicyDto respond(Policy policy) {
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), PolicyDto.class, Selection.View.FULL);
		V1Response.select(this.request, selection, PolicyDto.class);
		V1Response.etag(this.request, policy.version());
		return PolicyMapper.toDto(policy, this.refs.groups(selection, "targetGroups", policy.targetGroupIds()));
	}
}
