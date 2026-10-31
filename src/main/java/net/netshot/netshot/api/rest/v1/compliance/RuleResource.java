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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

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
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.MergePatch;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.compliance.Exemption;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.compliance.RuleTestResult;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;

/**
 * The compliance rules (v1): list, read, create, replace, update and delete, their exemptions, and the test of a
 * rule definition against a device.
 */
@Path("/" + RuleMapper.PATH)
@Tag(name = "Compliance rules", description = "Compliance rules (text, JavaScript, Python), their exemptions and tests")
public class RuleResource {

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
	 * @param ruleService the rule use cases
	 * @param policyService the policy use cases
	 * @param groupService the group use cases
	 * @param searchService the device search use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public RuleResource(RuleService ruleService, PolicyService policyService, GroupService groupService, SearchService searchService,
		DriverRegistry driverRegistry) {
		this.ruleService = ruleService;
		this.refs = new ComplianceRefs(groupService, policyService, ruleService, searchService, driverRegistry);
	}

	/**
	 * Lists the rules, optionally restricted by a fixed filter (the rules of a policy).
	 *
	 * @param ruleService the rule use cases
	 * @param refs the loader of the referenced objects
	 * @param request the request
	 * @param uriInfo the URI of the request
	 * @param fixed the filter to add to the ones of the request, or null
	 * @return the page of rules
	 */
	static ListResponse<RuleDto> list(RuleService ruleService, ComplianceRefs refs, HttpServletRequest request, UriInfo uriInfo,
		Criterion fixed) {
		ListQuery query = ListParams.parse(uriInfo.getQueryParameters(), ruleService.listSchema());
		Selection selection = Selection.of(uriInfo.getQueryParameters(), RuleDto.class, Selection.View.BRIEF);
		V1Response.select(request, selection, RuleDto.class);
		ListQuery restricted = fixed == null ? query : query.and(fixed);
		ListResult<Rule> rules = ruleService.list(restricted);
		var policies = refs.policies(selection, "policy", rules.items().stream().map(Rule::policyId).toList());
		return ListResponse.of(uriInfo, query, rules, r -> RuleMapper.toDto(r, policies));
	}

	/**
	 * Lists the rules.
	 *
	 * @return the page of rules
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the compliance rules", description = "Lists the compliance rules of all the policies, in their brief "
		+ "view by default (the type, TEXT, JAVASCRIPT or PYTHON, tells which other properties a rule has).")
	public ListResponse<RuleDto> listRules() {
		return list(this.ruleService, this.refs, this.request, this.uriInfo, null);
	}

	/**
	 * Gets a rule.
	 *
	 * @param id the rule ID
	 * @return the rule
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a compliance rule")
	public RuleDto getRule(@PathParam("id") @Parameter(description = "Rule ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.ruleService.get(id));
	}

	/**
	 * Creates a rule.
	 *
	 * @param ruleRequest the definition of the rule
	 * @return the new rule
	 */
	@POST
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Create a compliance rule", description = "Creates a rule in a policy (required). "
		+ "A script rule without script gets the template script; the rule is disabled unless enabled is true.")
	public RuleDto createRule(RuleRequest ruleRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		if (ruleRequest.policy() == null) {
			throw NetshotException.invalidParameter("policy", "The policy is required.");
		}
		Rule rule = this.ruleService.add(RuleMapper.toRule(ruleRequest));
		V1Response.created(this.request, V1Links.of(RuleMapper.PATH, rule.id()));
		return this.respond(rule);
	}

	/**
	 * Replaces a rule.
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
	@Operation(summary = "Replace a compliance rule", description = "Replaces the definition of a rule (its type and its policy "
		+ "can't change; its exemptions are unchanged).")
	public RuleDto replaceRule(@PathParam("id") @Parameter(description = "Rule ID") long id, RuleRequest ruleRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		return this.respond(this.ruleService.replace(id, RuleMapper.toRule(ruleRequest)));
	}

	/**
	 * Updates a rule with a merge patch.
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
	@Operation(summary = "Update a compliance rule", description = "Changes some properties of a rule (JSON merge patch, RFC 7396).")
	public RuleDto updateRule(@PathParam("id") @Parameter(description = "Rule ID") long id,
		@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The properties to change") JsonNode patch)
		throws JsonProcessingException {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		RuleRequest patched = MergePatch.apply(RuleMapper.toRequest(this.ruleService.get(id)), patch, this.headers.getMediaType(),
			RuleRequest.class);
		return this.respond(this.ruleService.replace(id, RuleMapper.toRule(patched)));
	}

	/**
	 * Deletes a rule.
	 *
	 * @param id the rule ID
	 */
	@DELETE
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Operation(summary = "Delete a compliance rule", description = "Deletes a rule, with its exemptions and results.")
	public void deleteRule(@PathParam("id") @Parameter(description = "Rule ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		if (!this.ruleService.delete(id)) {
			throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "The rule doesn't exist.");
		}
		V1Response.status(this.request, Response.Status.NO_CONTENT);
	}

	/**
	 * Lists the exemptions of a rule.
	 *
	 * @param id the rule ID
	 * @return the page of exemptions
	 */
	@GET
	@Path("/{id}/exemptions")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the exemptions of a rule", description = "Lists the devices exempted from a rule, with the date "
		+ "their exemption ends. Filters: device, expirationDate.")
	public ListResponse<ExemptionDto> listExemptions(@PathParam("id") @Parameter(description = "Rule ID") long id) {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), this.ruleService.exemptionSchema());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), ExemptionDto.class, Selection.View.BRIEF);
		V1Response.select(this.request, selection, ExemptionDto.class);
		ListResult<Exemption> exemptions = this.ruleService.listExemptions(id, query);
		var devices = this.refs.devices(selection, "device", exemptions.items().stream().map(Exemption::deviceId).toList());
		return ListResponse.of(this.uriInfo, query, exemptions, e -> new ExemptionDto(devices.apply(e.deviceId()), e.expirationDate()));
	}

	/**
	 * Replaces the exemptions of a rule.
	 *
	 * @param id the rule ID
	 * @param exemptionsRequest the new set of exemptions
	 * @return the page of exemptions
	 */
	@PUT
	@Path("/{id}/exemptions")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Replace the exemptions of a rule", description = "Replaces the whole set of devices exempted from a rule "
		+ "(an empty list removes them all), and answers the new set.")
	public ListResponse<ExemptionDto> replaceExemptions(@PathParam("id") @Parameter(description = "Rule ID") long id,
		ExemptionsRequest exemptionsRequest) {
		Map<Long, Instant> expirations = new LinkedHashMap<>();
		for (ExemptionRequest exemption : exemptionsRequest.exemptions()) {
			if (expirations.put(exemption.device().id(), exemption.expirationDate()) != null) {
				throw NetshotException.invalidParameter("exemptions", "The device %d is given twice.".formatted(exemption.device().id()));
			}
		}
		this.ruleService.replaceExemptions(id, expirations);
		return this.listExemptions(id);
	}

	/**
	 * Tests a rule definition against a device.
	 *
	 * @param testRequest the rule definition and the device
	 * @return the result of the check
	 */
	@POST
	@Path("/test")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Test a compliance rule", description = "Checks a rule definition (which isn't stored) against a device, "
		+ "without any side effect, and answers the result, with what the check logged. "
		+ "A device without configuration gives a NOTAPPLICABLE result.")
	public RuleTestResultDto testRule(RuleTestRequest testRequest) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		RuleTestResult result = this.ruleService.test(testRequest.device().id(), RuleMapper.toRule(testRequest.rule(), true));
		return new RuleTestResultDto(result.result(), result.log(), result.comment());
	}

	private RuleDto respond(Rule rule) {
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), RuleDto.class, Selection.View.FULL);
		V1Response.select(this.request, selection, RuleDto.class);
		return RuleMapper.toDto(rule, this.refs.policies(selection, "policy", java.util.List.of(rule.policyId())));
	}
}
