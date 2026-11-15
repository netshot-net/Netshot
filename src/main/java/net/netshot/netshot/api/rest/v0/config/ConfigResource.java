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
package net.netshot.netshot.api.rest.v0.config;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.undertow.util.Headers;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v0.PaginationParams;
import net.netshot.netshot.api.rest.v0.V0Lists;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigItem;
import net.netshot.netshot.inventory.config.ConfigItemException;
import net.netshot.netshot.inventory.config.ConfigService;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.platform.query.SortOrder;

/**
 * The REST resource of the configurations: list the configurations of a device or the
 * configuration changes, download an item of a configuration, and compare two configurations.
 *
 * <p>It only translates between HTTP and the {@link ConfigService}. The class is mounted on
 * {@code /} with the full path on each method, because a narrower class-level path would hide
 * the other {@code /devices/...} methods served by other classes.</p>
 */
@Path("/")
@Slf4j
public class ConfigResource {

	private final ConfigService configService;
	private final SearchService searchService;

	/**
	 * Instantiates the resource.
	 *
	 * @param configService the configuration use cases
	 * @param searchService the device search use cases
	 */
	@Inject
	public ConfigResource(ConfigService configService, SearchService searchService) {
		this.configService = configService;
		this.searchService = searchService;
	}

	/**
	 * Gets the device configs.
	 *
	 * @param id the id
	 * @param paginationParams = the pagination parameters
	 * @return the device configs
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/devices/{id}/configs")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get device configs",
		description = "Returns the list of configurations of the given device, by ID."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<ConfigDto> getDeviceConfigs(@PathParam("id") @Parameter(description = "Device ID") Long id, @BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, get device {} configs.", id);
		Page page = paginationParams.toPage();
		return ConfigMapper.toDtos(this.configService.list(V0Lists.sorted(page, List.of(SortOrder.desc("changeDate"), SortOrder.desc("id")),
			new Criterion("device", Lookup.EXACT, List.of(id))), true).items(), page.isRestricted());
	}

	/**
	 * Gets the device config as plain text.
	 *
	 * @param id the id
	 * @param item the item
	 * @return the device config plain
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/configs/{id}/{item}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_OCTET_STREAM})
	@Operation(
		summary = "Get a device configuration item",
		description = "Retrieves a device configuration item, in plain text."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public Response getDeviceConfigPlain(@PathParam("id") @Parameter(description = "Config ID") Long id,
		@PathParam("item") @Parameter(description = "Config item name") String item) throws WebApplicationException {
		log.debug("REST request, get device {} config {}.", id, item);
		ConfigItem content;
		try {
			content = this.configService.getItem(id, item);
		}
		catch (ConfigItemException e) {
			throw new WebApplicationException(e.getMessage(), switch (e.getReason()) {
				case CONFIG_NOT_FOUND -> Response.Status.NOT_FOUND;
				case ITEM_NOT_AVAILABLE, INVALID_ITEM -> Response.Status.BAD_REQUEST;
				case DATABASE_ERROR -> Response.Status.INTERNAL_SERVER_ERROR;
			});
		}
		Object entity = content.file() != null ? content.file() : content.text();
		return Response.ok(entity, MediaType.APPLICATION_OCTET_STREAM)
			.header(Headers.CONTENT_DISPOSITION_STRING, "attachment; filename=\"%s\"".formatted(content.fileName()))
			.header(Headers.CONTENT_LENGTH_STRING, content.length())
			.build();
	}

	/**
	 * Gets the device config diff.
	 *
	 * @param id1 the id1
	 * @param id2 the id2
	 * @param includeDeltas whether to include deltas
	 * @param includeConfigs whether to include configs
	 * @return the device config diff
	 */
	@GET
	@Path("/configs/{id1}/vs/{id2}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON})
	@Operation(
		summary = "Get the diff between two configuration objects",
		description = "Retrieves the differences between two given device configuration objets, identified by full IDs."
	)
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public ConfigDiffDto getDeviceConfigDiff(
		@PathParam("id1") @Parameter(description = "First config ID") Long id1,
		@PathParam("id2") @Parameter(description = "Second config ID") Long id2,
		@DefaultValue("true") @QueryParam("deltas") @Parameter(description = "Include/compute deltas") boolean includeDeltas,
		@DefaultValue("false") @QueryParam("fullconfigs") @Parameter(description = "Include full configs") boolean includeConfigs) {
		log.debug("REST request, get device config diff, id {} and {} ({} deltas).",
			id1, id2, includeDeltas ? "with" : "without");
		return ConfigMapper.toDto(this.configService.diff(id1, id2, includeDeltas, includeConfigs));
	}

	/**
	 * Get configuration changes with optinal filtering criteria.
	 *
	 * @param startDate = after date filter
	 * @param endDate = before date filter
	 * @param domains = domain filter
	 * @param deviceGroups = group filter
	 * @param paginationParams = the pagination parameters
	 * @return the changes
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/configs")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get configuration changes",
		description = "Retrieves a list of configurations, based on given criteria."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Devices", description = "Device (such as network or security equipment) management")
	public List<ConfigSummaryDto> getConfigs(
		@QueryParam("after") @Parameter(description = "Configs gathered after this date (as milliseconds since 1970)") Long startDate,
		@QueryParam("before") @Parameter(description = "Configs gathered before this date (as milliseconds since 1970)") Long endDate,
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@QueryParam("group") @Parameter(description = "Filter on given group ID(s)") Set<Long> deviceGroups,
		@BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, config changes.");
		List<Config> configs = this.configService.list(V0Lists.sorted(paginationParams.toPage(), List.of(SortOrder.asc("id")),
			ConfigMapper.toCriteria(startDate, endDate, domains, deviceGroups)), false).items();
		Map<Long, DeviceSummary> devices = new HashMap<>();
		if (!configs.isEmpty()) {
			this.searchService.listDeviceSummaries(ListQuery.byIds(configs.stream().map(Config::deviceId).collect(Collectors.toSet())), null)
				.items().forEach(device -> devices.put(device.id(), device));
		}
		return ConfigMapper.toSummaryDtos(configs.stream().map(config -> {
			DeviceSummary device = devices.get(config.deviceId());
			return new ConfigSummary(config.id(), device.name(), config.deviceId(),
				config.changeDate() == null ? null : Date.from(config.changeDate()), config.author(), device.networkClass());
		}).toList());
	}
}
