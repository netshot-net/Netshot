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

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


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
import net.netshot.netshot.api.rest.v0.V0Errors;
import net.netshot.netshot.api.rest.v0.V0Lists;
import net.netshot.netshot.compliance.DeviceCheckResult;
import net.netshot.netshot.compliance.Exemption;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.platform.query.SortOrder;

/**
 * The REST resource of the compliance rules (text and script rules): list, read, add, edit,
 * delete and test, with their exemptions and the compliance state of a device.
 *
 * <p>It only translates between HTTP and the {@link RuleService}. The class is mounted on
 * {@code /} with the full path on each method (see {@code DeviceResource}).</p>
 */
@Path("/")
@Slf4j
public class RuleResource {

	private final RuleService ruleService;
	private final PolicyService policyService;
	private final SearchService searchService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	/**
	 * Instantiates the resource.
	 *
	 * @param ruleService the rule use cases
	 * @param policyService the policy use cases (to check the policy of a rule of an unknown type, as v0 always did)
	 * @param searchService the device search use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public RuleResource(RuleService ruleService, PolicyService policyService, SearchService searchService, DriverRegistry driverRegistry) {
		this.ruleService = ruleService;
		this.policyService = policyService;
		this.searchService = searchService;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Gets all the compliance rules, across all policies, with minimal details.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the rules
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/rules")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the compliance rules",
		description = "Retrieves the list of compliance rules, across all policies, with minimal details."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<RuleSummaryDto> getRules(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, get rules.");
		return RuleMapper.toSummaryDtos(this.ruleService.list(V0Lists.query(paginationParams.toPage())).items().stream()
			.map(rule -> new RuleSummary(rule.id(), rule.name(), rule.getClass(), rule.enabled(), rule.policyId()))
			.toList());
	}

	/**
	 * Gets the rules of a policy.
	 *
	 * @param paginationParams = the pagination parameters
	 * @param id the id
	 * @return the policy rules
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/policies/{id}/rules")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the compliance rules of a policy",
		description = "Returns the rules owned by a given compliance policy."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<RuleDto> getPolicyRules(
		@BeanParam PaginationParams paginationParams,
		@PathParam("id") @Parameter(description = "Policy ID") Long id) throws WebApplicationException {
		log.debug("REST request, get rules for policy {}.", id);
		V0Errors.translate(() -> this.policyService.get(id),
			V0Errors.code(ErrorCode.NETSHOT_POLICY_NOT_FOUND, ErrorCode.NETSHOT_INVALID_POLICY, "Invalid policy"));
		return RuleMapper.toDtos(this.driverRegistry, this.ruleService.list(
			V0Lists.query(paginationParams.toPage(), new Criterion("policy", Lookup.EXACT, List.of(id)))).items());
	}

	/**
	 * Gets a specific rule of a specific policy.
	 *
	 * @param pid the policy id
	 * @param id the rule id
	 * @return the rule
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/policies/{pid}/rules/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get a specific compliance rule of a specific policy",
		description = "Returns the specific rule owned by a given compliance policy."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public RuleDto getPolicyRule(
		@PathParam("pid") @Parameter(description = "Policy ID") Long pid,
		@PathParam("id") @Parameter(description = "Rule ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, get rule {}, policy {}.", id, pid);
		V0Errors.translate(() -> this.policyService.get(pid),
			V0Errors.code(ErrorCode.NETSHOT_POLICY_NOT_FOUND, ErrorCode.NETSHOT_POLICY_NOT_FOUND, "Unknown policy"));
		Rule rule = V0Errors.translate(() -> this.ruleService.get(id),
			V0Errors.code(ErrorCode.NETSHOT_RULE_NOT_FOUND, ErrorCode.NETSHOT_RULE_NOT_FOUND, "Unknown rule"));
		if (rule.policyId() != pid) {
			throw new NetshotException(ErrorCode.NETSHOT_RULE_NOT_FOUND, "Unknown rule");
		}
		return RuleMapper.toDto(this.driverRegistry, rule);
	}

	/**
	 * Adds a compliance rule.
	 *
	 * @param rsRule the rule
	 * @return the rule
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/rules")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add a compliance rule",
		description = "Creates a compliance rule. The associated policy must already exist."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public RuleDto addRule(RuleRequest rsRule) throws WebApplicationException {
		log.debug("REST request, add rule.");
		RuleDefinition definition = RuleMapper.toDefinition(rsRule);
		if (definition.name() == null || definition.name().trim().isEmpty()) {
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE_NAME, "Invalid rule name.");
		}
		V0Errors.Translation invalidPolicy = V0Errors.code(ErrorCode.NETSHOT_POLICY_NOT_FOUND, ErrorCode.NETSHOT_INVALID_POLICY,
			"Invalid policy.");
		Rule newRule = RuleMapper.toNewRule(definition);
		if (newRule == null) {
			// The policy is checked before the type of rule
			V0Errors.translate(() -> this.policyService.get(definition.policyId()), invalidPolicy);
			throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE, "Invalid rule type.");
		}
		Rule added = V0Errors.translate(() -> this.ruleService.create(newRule),
			V0Errors.target("policy", ErrorCode.NETSHOT_INVALID_POLICY, "Invalid policy."));
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return RuleMapper.toDto(this.driverRegistry, added);
	}

	/**
	 * Sets the rule.
	 *
	 * @param id the id
	 * @param rsRule the rule
	 * @return the rule
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/rules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Update a compliance rule",
		description = "Edits a compliance rule, by ID."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public RuleDto setRule(@PathParam("id") @Parameter(description = "Rule ID") Long id, RuleRequest rsRule)
		throws WebApplicationException {
		log.debug("REST request, edit rule {}.", id);
		RuleDefinition definition = RuleMapper.toDefinition(rsRule);
		Rule rule = V0Errors.translate(() -> this.ruleService.update(id, existing -> RuleMapper.toReplacement(existing, definition), null),
			V0Errors.code(ErrorCode.NETSHOT_RULE_NOT_FOUND, ErrorCode.NETSHOT_INVALID_RULE, "Unable to find this rule."),
			V0Errors.target("exemptions", ErrorCode.NETSHOT_INVALID_DEVICE, null));
		return RuleMapper.toDto(this.driverRegistry, rule);
	}

	/**
	 * Delete rule.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/rules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Remove a compliance rule",
		description = "Removes a compliance rule, by ID."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public void deleteRule(@PathParam("id") @Parameter(description = "Rule ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete rule {}.", id);
		if (V0Errors.found(() -> this.ruleService.delete(id, null), ErrorCode.NETSHOT_RULE_NOT_FOUND)) {
			ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
		}
		else {
			ResponseCodeFilter.suggest(this.request, Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Test rule.
	 *
	 * @param rsRule the rule to test, and the device to test it against
	 * @return the rule test result
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/rules/test")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Test a compliance rule",
		description = "Test a compliance rule against a given device, in dry run mode."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public RuleTestResultDto testRule(RuleTestRequest rsRule) throws WebApplicationException {
		log.debug("REST request, rule test.");
		Rule rule = RuleMapper.toTestRule(RuleMapper.toDefinition(rsRule));
		return RuleMapper.toDto(RuleMapper.toTestResult(() -> this.ruleService.test(rsRule.device(), rule)));
	}

	/**
	 * Gets the exempted devices.
	 *
	 * @param paginationParams = the pagination parameters
	 * @param id the id
	 * @return the exempted devices
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/rule/{id}/exempteddevices")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the exempted devices of a compliance rule",
		description = "Returns the list of devices which have an exemption against a given compliance rule, by ID."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<ExemptedDeviceDto> getExemptedDevices(@BeanParam PaginationParams paginationParams,
		@PathParam("id") @Parameter(description = "Rule ID") Long id) throws WebApplicationException {
		log.debug("REST request, get exemptions for rule {}.", id);
		// An unknown rule has no exempted device
		List<Exemption> exemptions = V0Lists.orEmpty(() -> this.ruleService.listExemptions(id, V0Lists.query(paginationParams.toPage())).items(),
			ErrorCode.NETSHOT_RULE_NOT_FOUND);
		Map<Long, DeviceSummary> devices = new HashMap<>();
		if (!exemptions.isEmpty()) {
			this.searchService.listDeviceSummaries(ListQuery.byIds(exemptions.stream().map(Exemption::deviceId).toList()), null).items()
				.forEach(device -> devices.put(device.id(), device));
		}
		return RuleMapper.toExemptedDtos(exemptions.stream()
			.map(exemption -> new ExemptedDevice(devices.get(exemption.deviceId()), date(exemption.expirationDate()))).toList());
	}

	/**
	 * Gets the device compliance results.
	 *
	 * @param paginationParams = the pagination parameters
	 * @param id the id
	 * @return the device compliance
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}/complianceresults")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the compliance results for a device",
		description = "Returns the compliance results for a give device, by ID."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<DeviceRuleResultDto> getDeviceComplianceResults(@BeanParam PaginationParams paginationParams,
		@PathParam("id") @Parameter(description = "Device ID") Long id) throws WebApplicationException {
		log.debug("REST request, get compliance results for device {}.", id);
		// An unknown device has no compliance result
		List<DeviceCheckResult> results = V0Lists.orEmpty(() -> this.ruleService.listDeviceResults(id,
			V0Lists.sorted(paginationParams.toPage(), List.of(SortOrder.asc("policy")))).items(), ErrorCode.NETSHOT_DEVICE_NOT_FOUND);
		Map<Long, String> ruleNames = new HashMap<>();
		Map<Long, String> policyNames = new HashMap<>();
		if (!results.isEmpty()) {
			this.ruleService.list(ListQuery.byIds(results.stream().map(DeviceCheckResult::ruleId).collect(Collectors.toSet()))).items()
				.forEach(rule -> ruleNames.put(rule.id(), rule.name()));
			this.policyService.list(ListQuery.byIds(results.stream().map(DeviceCheckResult::policyId).collect(Collectors.toSet()))).items()
				.forEach(policy -> policyNames.put(policy.id(), policy.name()));
		}
		return RuleMapper.toResultDtos(results.stream()
			.map(result -> new DeviceRuleResult(result.ruleId(), ruleNames.get(result.ruleId()), result.policyId(),
				policyNames.get(result.policyId()), result.result(), result.comment(), date(result.checkDate()),
				date(result.exemptionExpirationDate())))
			.toList());
	}

	private static Date date(Instant instant) {
		return instant == null ? null : Date.from(instant);
	}
}
