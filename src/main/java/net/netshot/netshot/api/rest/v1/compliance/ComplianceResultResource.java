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

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListFilters;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.ListSchema;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.StatsParams;
import net.netshot.netshot.api.rest.v1.StatsResponse;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.api.rest.v1.device.DeviceMapper;
import net.netshot.netshot.api.rest.v1.domain.DomainMapper;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.compliance.DeviceCheckResult;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.domain.DomainService;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;
import net.netshot.netshot.platform.query.StatsRow;

/**
 * The compliance results across all the devices (v1): list and statistics.
 */
@Path("/compliance-results")
@Tag(name = "Compliance results", description = "Compliance state of the devices")
public class ComplianceResultResource {

	private final RuleService ruleService;
	private final PolicyService policyService;
	private final GroupService groupService;
	private final SearchService searchService;
	private final DomainService domainService;
	private final ComplianceRefs refs;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	/**
	 * Instantiates the resource.
	 *
	 * @param ruleService the rule use cases
	 * @param policyService the policy use cases
	 * @param groupService the group use cases
	 * @param searchService the device search use cases
	 * @param domainService the domain use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public ComplianceResultResource(RuleService ruleService, PolicyService policyService, GroupService groupService,
		SearchService searchService, DomainService domainService, DriverRegistry driverRegistry) {
		this.ruleService = ruleService;
		this.policyService = policyService;
		this.groupService = groupService;
		this.searchService = searchService;
		this.domainService = domainService;
		this.refs = new ComplianceRefs(groupService, policyService, ruleService, searchService, driverRegistry);
	}

	/**
	 * Answers a page of compliance results (of the list across devices, or of the one of a device).
	 *
	 * @param refs the loader of the references
	 * @param request the current request
	 * @param uriInfo the URI of the request (its query parameters are the list parameters)
	 * @param query the list query, as parsed from the request
	 * @param results the results of the window of the query
	 * @return the page
	 */
	static ListResponse<DeviceComplianceResultDto> respond(ComplianceRefs refs, HttpServletRequest request, UriInfo uriInfo,
		ListQuery query, ListResult<DeviceCheckResult> results) {
		Selection selection = Selection.of(uriInfo.getQueryParameters(), DeviceComplianceResultDto.class, Selection.View.BRIEF);
		V1Response.select(request, selection, DeviceComplianceResultDto.class);
		var devices = refs.devices(selection, "device", results.items().stream().map(DeviceCheckResult::deviceId).toList());
		// The policies of the results are those of their rules: loaded once for both
		var loadedPolicies = refs.loadedPolicies(selection, "policy", results.items().stream().map(DeviceCheckResult::policyId).toList());
		var rules = refs.rules(selection, "rule", results.items().stream().map(DeviceCheckResult::ruleId).toList(), loadedPolicies);
		var policies = ComplianceRefs.policies(loadedPolicies);
		return ListResponse.of(uriInfo, query, results, r -> new DeviceComplianceResultDto(devices.apply(r.deviceId()),
			rules.apply(r.ruleId()), policies.apply(r.policyId()), r.result(), r.comment(), r.checkDate(), r.exemptionExpirationDate()));
	}

	/**
	 * Lists the compliance results across devices.
	 *
	 * @return the page of results
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the compliance results", description = "Lists the results of the last compliance checks, one per "
		+ "device and rule (a rule never checked on a device has no result here), with the exemption of the device from the rule. "
		+ "Filters: device, deviceName, domain and group (of the device), rule, ruleName, policy, policyName, result, checkDate, "
		+ "exemptionExpirationDate; q searches the device, rule and policy names.")
	@ListFilters(ListSchema.COMPLIANCE_RESULTS)
	public ListResponse<DeviceComplianceResultDto> listResults() {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.COMPLIANCE_RESULTS.schema());
		return respond(this.refs, this.request, this.uriInfo, query, this.ruleService.listResults(query));
	}

	/**
	 * Counts the compliance results.
	 *
	 * @return the counts
	 */
	@GET
	@Path("/stats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Count the compliance results", description = "Counts the compliance results matching the filters of the "
		+ "list, by the values of up to two fields (groupBy: result, policy, rule, device, group, domain) and/or by time bucket "
		+ "(interval: hour, day, week or month, on checkDate, from/to, timeZone). A result is counted once per group of its device.")
	@ListFilters(ListSchema.COMPLIANCE_RESULTS)
	public StatsResponse getResultStats() {
		StatsParams.Parsed parsed = StatsParams.parse(this.uriInfo.getQueryParameters(), ListSchema.COMPLIANCE_RESULTS.schema(), "checkDate");
		ListResult<StatsRow> rows = this.ruleService.resultStats(parsed.query(), parsed.stats());
		return StatsResponse.of(parsed.stats(), rows.items(), rows.count(), Map.of(
			"device", DeviceMapper.resolver(this.searchService),
			"group", GroupMapper.resolver(this.groupService),
			"domain", DomainMapper.resolver(this.domainService),
			"policy", PolicyMapper.resolver(this.policyService),
			"rule", RuleMapper.resolver(this.ruleService)));
	}
}
