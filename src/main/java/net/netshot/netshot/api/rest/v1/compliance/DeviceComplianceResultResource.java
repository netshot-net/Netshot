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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListFilters;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.ListSchema;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.device.DeviceRefs;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.platform.query.ListQuery;

/**
 * The compliance results of a device (v1).
 */
@Path("/devices/{id}/compliance-results")
@Tag(name = "Compliance results", description = "Compliance state of the devices")
public class DeviceComplianceResultResource {

	private final RuleService ruleService;
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
	 * @param deviceRefs writes the devices
	 */
	@Inject
	public DeviceComplianceResultResource(RuleService ruleService, PolicyService policyService, GroupService groupService,
		DeviceRefs deviceRefs) {
		this.ruleService = ruleService;
		this.refs = new ComplianceRefs(groupService, policyService, ruleService, deviceRefs);
	}

	/**
	 * Lists the compliance results of a device.
	 *
	 * @param id the device ID
	 * @return the page of results
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the compliance results of a device", description = "Lists the rules of the policies applying to a device "
		+ "(through its groups) with the result of their last check (none if never checked) and the exemption of the device. "
		+ "(unlike the list across devices, which only has the checked ones). Filters: rule, ruleName, policy, policyName, result, checkDate, exemptionExpirationDate.")
	@ListFilters(ListSchema.DEVICE_COMPLIANCE_RESULTS)
	public ListResponse<DeviceComplianceResultDto> listResults(@PathParam("id") @Parameter(description = "Device ID") long id) {
		ListQuery query = ListParams.parse(this.uriInfo.getQueryParameters(), ListSchema.DEVICE_COMPLIANCE_RESULTS.schema());
		return ComplianceResultResource.respond(this.refs, this.request, this.uriInfo, query, this.ruleService.listDeviceResults(id, query));
	}
}
