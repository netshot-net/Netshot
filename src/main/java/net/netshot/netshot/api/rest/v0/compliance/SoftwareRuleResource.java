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

import java.util.List;


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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v0.PaginationParams;
import net.netshot.netshot.api.rest.v0.ResponseCodeFilter;
import net.netshot.netshot.api.rest.v0.V0Errors;
import net.netshot.netshot.compliance.RankedSoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRuleService;
import net.netshot.netshot.compliance.SoftwareRuleService.Replacement;
import net.netshot.netshot.compliance.SoftwareRuleView;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The REST resource of the software compliance rules: list, add, edit, delete and sort.
 *
 * <p>It only translates between HTTP and the {@link SoftwareRuleService}. The class is mounted on
 * {@code /} with the full path on each method (see {@code DeviceResource}).</p>
 */
@Path("/")
@Slf4j
public class SoftwareRuleResource {

	private final SoftwareRuleService softwareRuleService;
	private final GroupService groupService;
	private final DriverRegistry driverRegistry;

	/** An unknown target group, as v0 always reported it. */
	private static final V0Errors.Translation INVALID_GROUP = V0Errors.target("group", ErrorCode.NETSHOT_INVALID_GROUP, "Invalid group");

	/** An unknown rule, as v0 always reported it. */
	private static final V0Errors.Translation UNKNOWN_RULE = V0Errors.code(ErrorCode.NETSHOT_RULE_NOT_FOUND, ErrorCode.NETSHOT_INVALID_RULE,
		"Unable to find this rule.");

	@Context
	private HttpServletRequest request;

	/**
	 * Instantiates the resource.
	 *
	 * @param softwareRuleService the software rule use cases
	 * @param groupService the group use cases (to write the target group of a rule)
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public SoftwareRuleResource(SoftwareRuleService softwareRuleService, GroupService groupService, DriverRegistry driverRegistry) {
		this.softwareRuleService = softwareRuleService;
		this.groupService = groupService;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Gets the software rules.
	 *
	 * @param paginationParams = the pagination parameters
	 * @return the software rules
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/softwarerules")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the software compliance rules",
		description = "Returns the list of software compliance rules."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<SoftwareRuleDto> getSoftwareRules(@BeanParam PaginationParams paginationParams) throws WebApplicationException {
		log.debug("REST request, software rules.");
		return SoftwareRuleMapper.toDtos(this.driverRegistry, this.softwareRuleService.list(paginationParams.toPage()));
	}

	/**
	 * Creates a software rule.
	 *
	 * @param rsRule the rule
	 * @return the software rule
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/softwarerules")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Add a software compliance rule",
		description = "Creates a software compliance rule."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public SoftwareRuleDto addSoftwareRule(SoftwareRuleRequest rsRule) throws WebApplicationException {
		log.debug("REST request, add software rule.");
		SoftwareRule newRule = SoftwareRuleMapper.toRule(this.driverRegistry, SoftwareRuleMapper.toDefinition(rsRule));
		RankedSoftwareRule rule = V0Errors.translate(() -> this.softwareRuleService.create(newRule, null), INVALID_GROUP);
		ResponseCodeFilter.suggest(this.request, Response.Status.CREATED);
		return this.toDto(rule.rule());
	}

	/**
	 * Delete software rule.
	 *
	 * @param id the id
	 * @throws WebApplicationException the web application exception
	 */
	@DELETE
	@Path("/softwarerules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Remove a software compliance rule",
		description = "Removes a software compliance rule, by ID"
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public void deleteSoftwareRule(@PathParam("id") @Parameter(description = "Software rule ID") Long id)
		throws WebApplicationException {
		log.debug("REST request, delete software rule {}.", id);
		if (V0Errors.found(() -> this.softwareRuleService.delete(id, null), ErrorCode.NETSHOT_RULE_NOT_FOUND)) {
			ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
		}
		else {
			ResponseCodeFilter.suggest(this.request, Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Updates the software rule.
	 *
	 * @param id the id
	 * @param rsRule the rule
	 * @return the software rule
	 * @throws WebApplicationException the web application exception
	 */
	@PUT
	@Path("/softwarerules/{id}")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Update a software compliance rule",
		description = "Edits a software compliance rule."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public SoftwareRuleDto setSoftwareRule(
		@PathParam("id") @Parameter(description = "Software rule ID") Long id,
		SoftwareRuleRequest rsRule)
		throws WebApplicationException {
		log.debug("REST request, edit software rule {}.", id);
		SoftwareRule newRule = SoftwareRuleMapper.toRule(this.driverRegistry, SoftwareRuleMapper.toDefinition(rsRule));
		RankedSoftwareRule rule = V0Errors.translate(() -> this.softwareRuleService.update(id, _ -> new Replacement(newRule, null), null),
			UNKNOWN_RULE, INVALID_GROUP);
		return this.toDto(rule.rule());
	}

	/**
	 * Sort a software rule.
	 *
	 * @param id the ID of the rule
	 * @param nextId the next ID
	 * @throws WebApplicationException the web application exception
	 */
	@POST
	@Path("/softwarerules/{id}/sort")
	@RolesAllowed(User.ROLE_READWRITE)
	@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Sort a software compliance rule",
		description = "Sorts a software compliance rule by moving it before another one."
	)
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public void sortSoftwareRule(
		@PathParam("id") @Parameter(description = "Software rule ID to move") Long id,
		@QueryParam("next") @Parameter(description = "ID of the other rule to move this rule before (omit parameter to place the rule at last position)") Long nextId)
		throws WebApplicationException {
		log.debug("REST request, sort software rule.");
		// The rule takes the position of the next rule (the one before it, if the rule is before it), or the last one
		Integer nextRank = null;
		if (nextId != null) {
			if (nextId.equals(id)) {
				throw new NetshotException(ErrorCode.NETSHOT_INVALID_RULE, "Unable to find this rule.");
			}
			nextRank = V0Errors.translate(() -> this.softwareRuleService.get(nextId).rank(), UNKNOWN_RULE);
		}
		Integer next = nextRank;
		V0Errors.translate(() -> this.softwareRuleService.update(id, rule -> new Replacement(rule.rule(),
			next == null ? Integer.MAX_VALUE : next - (rule.rank() < next ? 1 : 0)), null), UNKNOWN_RULE);
		ResponseCodeFilter.suggest(this.request, Response.Status.NO_CONTENT);
	}

	private SoftwareRuleDto toDto(SoftwareRule rule) {
		return SoftwareRuleMapper.toDto(this.driverRegistry,
			new SoftwareRuleView(rule, TargetGroups.find(this.groupService, rule.targetGroupId())));
	}
}
