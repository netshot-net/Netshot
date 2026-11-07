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
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RankedSoftwareRule;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.compliance.SoftwareRuleService;
import net.netshot.netshot.compliance.SoftwareRuleService.Replacement;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;

/**
 * The software compliance rules (v1): list, read, create, replace, update (including their position) and delete.
 */
@Path("/" + SoftwareRuleMapper.PATH)
@Tag(name = "Software rules", description = "Software compliance rules, checked in order")
public class SoftwareRuleResource {

	private final SoftwareRuleService softwareRuleService;
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
	 * @param softwareRuleService the software rule use cases
	 * @param groupService the group use cases
	 * @param policyService the policy use cases
	 * @param ruleService the rule use cases
	 * @param deviceRefs writes the devices
	 */
	@Inject
	public SoftwareRuleResource(SoftwareRuleService softwareRuleService, GroupService groupService, PolicyService policyService,
		RuleService ruleService, DeviceRefs deviceRefs) {
		this.softwareRuleService = softwareRuleService;
		this.refs = new ComplianceRefs(groupService, policyService, ruleService, deviceRefs);
	}

	/**
	 * Lists the software rules.
	 *
	 * @return the page of rules
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the software rules", description = "Lists the software compliance rules, in the order they are "
		+ "checked in by default (priority is the position of the rule, from 1).")
	@ListFilters(ListSchema.SOFTWARE_RULES)
	public ListResponse<SoftwareRuleDto> listSoftwareRules() {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.SOFTWARE_RULES.schema());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), SoftwareRuleDto.class, Selection.View.BRIEF);
		V1Response.select(this.request, selection, SoftwareRuleDto.class);
		ListResult<RankedSoftwareRule> rules = this.softwareRuleService.list(query);
		var groups = this.refs.groups(selection, "group", rules.items().stream().map(r -> r.rule().targetGroupId()).toList());
		return ListResponse.of(this.uriInfo, query, rules, r -> SoftwareRuleMapper.toDto(r, groups));
	}

	/**
	 * Gets a software rule.
	 *
	 * @param id the rule ID
	 * @return the rule
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a software rule")
	public SoftwareRuleDto getSoftwareRule(@PathParam("id") @Parameter(description = "Software rule ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.softwareRuleService.get(id));
	}

	/**
	 * Creates a software rule.
	 *
	 * @param ruleRequest the definition of the rule
	 * @return the new rule
	 */
	@POST
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a software rule", description = "Creates a software rule, at the given priority (position) "
		+ "or as the last one.")
	public SoftwareRuleDto createSoftwareRule(SoftwareRuleRequest ruleRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		RankedSoftwareRule rule = this.softwareRuleService.add(SoftwareRuleMapper.toRule(ruleRequest), position(ruleRequest));
		V1Response.created(this.request, V1Links.of(SoftwareRuleMapper.PATH, rule.rule().id()));
		return this.respond(rule);
	}

	/**
	 * Replaces a software rule.
	 *
	 * @param id the rule ID
	 * @param ruleRequest the new definition of the rule
	 * @return the rule
	 */
	@PUT
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Replace a software rule", description = "Replaces the definition of a software rule. A priority moves "
		+ "the rule to that position (the other rules shift); without priority the rule keeps its position. With If-Match, the rule "
		+ "must still be at the given version (ETag).")
	public SoftwareRuleDto replaceSoftwareRule(@PathParam("id") @Parameter(description = "Software rule ID") long id,
		SoftwareRuleRequest ruleRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Replacement replacement = replacement(ruleRequest);
		return this.respond(this.softwareRuleService.replace(id, _ -> replacement, V1Response.ifMatch(this.headers)));
	}

	/**
	 * Updates a software rule with a merge patch.
	 *
	 * @param id the rule ID
	 * @param patch the merge patch
	 * @return the rule
	 */
	@PATCH
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({V1Mappers.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Update a software rule", description = "Changes some properties of a software rule (JSON merge patch, "
		+ "RFC 7396), possibly its priority (position). With If-Match, the rule must still be at the given version (ETag).")
	public SoftwareRuleDto updateSoftwareRule(@PathParam("id") @Parameter(description = "Software rule ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.softwareRuleService.replace(id, MergePatch.of(patch, this.headers.getMediaType(),
			SoftwareRuleRequest.class, SoftwareRuleMapper::toRequest, SoftwareRuleResource::replacement), V1Response.ifMatch(this.headers)));
	}

	/**
	 * Deletes a software rule.
	 *
	 * @param id the rule ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Operation(summary = "Delete a software rule", description = "Deletes a software rule (the rules after it move up). With "
		+ "If-Match, the rule must still be at the given version (ETag).")
	public void deleteSoftwareRule(@PathParam("id") @Parameter(description = "Software rule ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		if (!this.softwareRuleService.delete(id, V1Response.ifMatch(this.headers))) {
			throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "The software rule doesn't exist.");
		}
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	private static Integer position(SoftwareRuleRequest ruleRequest) {
		if (ruleRequest.priority() != null && ruleRequest.priority() < 1) {
			throw NetshotException.invalidParameter("priority", "The priority is a position, from 1.");
		}
		return ruleRequest.priority();
	}

	private static Replacement replacement(SoftwareRuleRequest ruleRequest) {
		return new Replacement(SoftwareRuleMapper.toRule(ruleRequest), position(ruleRequest));
	}

	private SoftwareRuleDto respond(RankedSoftwareRule rule) {
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), SoftwareRuleDto.class, Selection.View.FULL);
		V1Response.select(this.request, selection, SoftwareRuleDto.class);
		V1Response.etag(this.request, rule.rule().version());
		return SoftwareRuleMapper.toDto(rule, this.refs.groups(selection, "group",
			rule.rule().targetGroupId() == null ? List.of() : List.of(rule.rule().targetGroupId())));
	}
}
