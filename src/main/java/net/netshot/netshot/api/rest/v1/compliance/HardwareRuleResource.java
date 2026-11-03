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
package net.netshot.netshot.api.rest.v1.compliance;

import java.util.List;

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
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.HardwareRuleService;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;

/**
 * The hardware compliance rules (v1): list, read, create, replace, update and delete.
 */
@Path("/" + HardwareRuleMapper.PATH)
@Tag(name = "Hardware rules", description = "Hardware compliance rules (end of sale and end of life dates)")
public class HardwareRuleResource {

	private final HardwareRuleService hardwareRuleService;
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
	 * @param hardwareRuleService the hardware rule use cases
	 * @param groupService the group use cases
	 * @param policyService the policy use cases
	 * @param ruleService the rule use cases
	 * @param searchService the device search use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public HardwareRuleResource(HardwareRuleService hardwareRuleService, GroupService groupService, PolicyService policyService,
		RuleService ruleService, SearchService searchService, DriverRegistry driverRegistry) {
		this.hardwareRuleService = hardwareRuleService;
		this.refs = new ComplianceRefs(groupService, policyService, ruleService, searchService, driverRegistry);
	}

	/**
	 * Lists the hardware rules.
	 *
	 * @return the page of rules
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the hardware rules", description = "Lists the hardware compliance rules, in their brief view by default.")
	@ListFilters(ListSchema.HARDWARE_RULES)
	public ListResponse<HardwareRuleDto> listHardwareRules() {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.HARDWARE_RULES.schema());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), HardwareRuleDto.class, Selection.View.BRIEF);
		V1Response.select(this.request, selection, HardwareRuleDto.class);
		ListResult<HardwareRule> rules = this.hardwareRuleService.list(query);
		var groups = this.refs.groups(selection, "group", rules.items().stream().map(HardwareRule::targetGroupId).toList());
		return ListResponse.of(this.uriInfo, query, rules, r -> HardwareRuleMapper.toDto(r, groups));
	}

	/**
	 * Gets a hardware rule.
	 *
	 * @param id the rule ID
	 * @return the rule
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a hardware rule")
	public HardwareRuleDto getHardwareRule(@PathParam("id") @Parameter(description = "Hardware rule ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.hardwareRuleService.get(id));
	}

	/**
	 * Creates a hardware rule.
	 *
	 * @param ruleRequest the definition of the rule
	 * @return the new rule
	 */
	@POST
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a hardware rule")
	public HardwareRuleDto createHardwareRule(HardwareRuleRequest ruleRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		HardwareRule rule = this.hardwareRuleService.add(HardwareRuleMapper.toRule(ruleRequest));
		V1Response.created(this.request, V1Links.of(HardwareRuleMapper.PATH, rule.id()));
		return this.respond(rule);
	}

	/**
	 * Replaces a hardware rule.
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
	@Operation(summary = "Replace a hardware rule", description = "Replaces the definition of a hardware rule.")
	public HardwareRuleDto replaceHardwareRule(@PathParam("id") @Parameter(description = "Hardware rule ID") long id,
		HardwareRuleRequest ruleRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.hardwareRuleService.replace(id, HardwareRuleMapper.toRule(ruleRequest)));
	}

	/**
	 * Updates a hardware rule with a merge patch.
	 *
	 * @param id the rule ID
	 * @param patch the merge patch
	 * @return the rule
	 * @throws JsonProcessingException if the patched rule isn't a valid definition
	 */
	@PATCH
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({V1Mappers.APPLICATION_MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Update a hardware rule", description = "Changes some properties of a hardware rule (JSON merge patch, RFC 7396).")
	public HardwareRuleDto updateHardwareRule(@PathParam("id") @Parameter(description = "Hardware rule ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch)
		throws JsonProcessingException {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		HardwareRuleRequest patched = MergePatch.apply(HardwareRuleMapper.toRequest(this.hardwareRuleService.get(id)), patch,
			this.headers.getMediaType(), HardwareRuleRequest.class);
		return this.respond(this.hardwareRuleService.replace(id, HardwareRuleMapper.toRule(patched)));
	}

	/**
	 * Deletes a hardware rule.
	 *
	 * @param id the rule ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Operation(summary = "Delete a hardware rule")
	public void deleteHardwareRule(@PathParam("id") @Parameter(description = "Hardware rule ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		if (!this.hardwareRuleService.delete(id)) {
			throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "The hardware rule doesn't exist.");
		}
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	private HardwareRuleDto respond(HardwareRule rule) {
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), HardwareRuleDto.class, Selection.View.FULL);
		V1Response.select(this.request, selection, HardwareRuleDto.class);
		return HardwareRuleMapper.toDto(rule, this.refs.groups(selection, "group",
			rule.targetGroupId() == null ? List.of() : List.of(rule.targetGroupId())));
	}
}
