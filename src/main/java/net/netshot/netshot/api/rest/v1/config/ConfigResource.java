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
package net.netshot.netshot.api.rest.v1.config;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import net.netshot.netshot.api.rest.v1.ListFilters;
import net.netshot.netshot.api.rest.v1.ListSchema;
import net.netshot.netshot.platform.query.StatsRow;
import net.netshot.netshot.api.rest.v1.StatsResponse;
import net.netshot.netshot.api.rest.v1.StatsParams;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.v1.ListParams;
import net.netshot.netshot.api.rest.v1.ListResponse;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.V1Mappers;
import net.netshot.netshot.api.rest.v1.V1Response;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;
import net.netshot.netshot.api.rest.v1.device.DeviceMapper;
import net.netshot.netshot.api.rest.v1.task.TaskMapper;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigDiff;
import net.netshot.netshot.inventory.config.ConfigItem;
import net.netshot.netshot.inventory.config.ConfigItemException;
import net.netshot.netshot.inventory.config.ConfigService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.query.Criterion;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.ListResult;

/**
 * The configurations of the devices (v1): list, read, download an item, compare two.
 */
@Path("/" + ConfigMapper.PATH)
@Tag(name = "Configurations", description = "Device configurations (snapshots)")
public class ConfigResource {

	/** The parameter of the configuration to compare with. */
	static final String WITH = "with";

	private final ConfigService configService;
	private final SearchService searchService;
	private final DriverRegistry driverRegistry;

	@Context
	private HttpServletRequest request;

	@Context
	private UriInfo uriInfo;

	/**
	 * Instantiates the resource.
	 *
	 * @param configService the configuration use cases
	 * @param searchService the device search use cases
	 * @param driverRegistry the loaded device drivers
	 */
	@Inject
	public ConfigResource(ConfigService configService, SearchService searchService, DriverRegistry driverRegistry) {
		this.configService = configService;
		this.searchService = searchService;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Counts the configurations.
	 *
	 * @return the counts
	 */
	@GET
	@Path("/stats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Count the configurations", description = "Counts the configurations (the configuration changes) matching "
		+ "the filters of the configuration list, by device and/or author (groupBy) and/or by time bucket (interval: hour, "
		+ "day, week or month, on changeDate, from/to, timeZone).")
	@ListFilters(ListSchema.CONFIGS)
	public StatsResponse getConfigStats() {
		StatsParams.Parsed parsed = StatsParams.parse(this.uriInfo.getQueryParameters(), ListSchema.CONFIGS.schema(), "changeDate");
		ListResult<StatsRow> rows = this.configService.stats(parsed.query(), parsed.stats());
		return StatsResponse.of(parsed.stats(), rows.items(), rows.count(), Map.of("device", DeviceMapper.resolver(this.searchService)));
	}

	/**
	 * Lists the configurations.
	 *
	 * @return the page of configurations
	 */
	@GET
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "List the configurations", description = "Lists the configurations of all the devices, "
		+ "in their brief view by default (device, date, author), the latest first. The attributes are only there "
		+ "in the full view (or with fields=attributes), and never hold the long texts and files: download them "
		+ "from their url. The filters on domain and group apply to the device of the configuration.")
	@ListFilters(ListSchema.CONFIGS)
	public ListResponse<ConfigDto> listConfigs() {
		return list(this.configService, this.searchService, this.driverRegistry, this.request, this.uriInfo, null);
	}

	/**
	 * Answers a page of configurations (of the configuration list, or of a list of configurations of another object).
	 *
	 * @param configService the configuration use cases
	 * @param searchService the device search use cases
	 * @param driverRegistry the loaded device drivers
	 * @param request the current request
	 * @param uriInfo the URI of the request (its query parameters are the list parameters)
	 * @param scope a criterion the configurations must also match (the configurations of a device...), or null
	 * @return the page of configurations
	 * @throws NetshotException if a parameter is invalid
	 */
	public static ListResponse<ConfigDto> list(ConfigService configService, SearchService searchService,
		DriverRegistry driverRegistry, HttpServletRequest request, UriInfo uriInfo, Criterion scope) {
		MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
		ListQuery listQuery = ListParams.parse(params, ListSchema.CONFIGS.schema());
		Selection selection = Selection.of(params, ConfigDto.class, Selection.View.BRIEF);
		V1Response.select(request, selection, ConfigDto.class);
		boolean withAttributes = selection.includes("attributes");
		ListResult<Config> configs = configService.list(scope == null ? listQuery : listQuery.and(scope), withAttributes);
		LongFunction<DeviceDto> devices = devices(searchService, driverRegistry, selection,
			configs.items().stream().map(Config::deviceId).toList());
		return ListResponse.of(uriInfo, listQuery, configs, c -> ConfigMapper.toDto(c, devices, withAttributes));
	}

	/**
	 * Gets a configuration.
	 *
	 * @param id the configuration ID
	 * @return the configuration
	 */
	@GET
	@Path("/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Get a configuration", description = "Gets a configuration with its attributes. The long texts "
		+ "and files are not inlined: the attributes give the url to download them.")
	public ConfigDto getConfig(@PathParam("id") @Parameter(description = "Configuration ID") long id) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		Selection selection = Selection.of(this.uriInfo.getQueryParameters(), ConfigDto.class, Selection.View.FULL);
		V1Response.select(this.request, selection, ConfigDto.class);
		Config config = this.configService.get(id);
		V1Response.etag(this.request, config.version());
		return ConfigMapper.toDto(config, devices(this.searchService, this.driverRegistry, selection, List.of(config.deviceId())), true);
	}

	/**
	 * Downloads an item of a configuration.
	 *
	 * @param id the configuration ID
	 * @param item the name of the item
	 * @return the content of the item
	 */
	@GET
	@Path("/{id}/items/{item}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
	@Operation(summary = "Download an item of a configuration", description = "Downloads a long text item "
		+ "(text/plain, UTF-8) or a file item (application/octet-stream) of a configuration, as an attachment. "
		+ "404 if the configuration has no such item, or not a downloadable one (a short value), or if it has no content.")
	public Response getConfigItem(@PathParam("id") @Parameter(description = "Configuration ID") long id,
		@PathParam("item") @Parameter(description = "Name of the item (an attribute of the configuration)") String item) {
		ListParams.checkSelectionOnly(this.uriInfo.getQueryParameters());
		ConfigItem content;
		try {
			content = this.configService.getItem(id, item);
		}
		catch (ConfigItemException e) {
			throw switch (e.getReason()) {
				case CONFIG_NOT_FOUND -> new NetshotException(ErrorCode.NETSHOT_CONFIG_NOT_FOUND, "The configuration doesn't exist.");
				case ITEM_NOT_AVAILABLE, INVALID_ITEM -> new NetshotException(ErrorCode.NETSHOT_CONFIG_NOT_FOUND,
					"The configuration has no downloadable item '%s'.".formatted(item));
				case DATABASE_ERROR -> new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, e.getMessage());
			};
		}
		boolean file = content.file() != null;
		MediaType type = file ? MediaType.APPLICATION_OCTET_STREAM_TYPE : new MediaType("text", "plain", "UTF-8");
		return Response.ok(file ? content.file() : content.text(), type)
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(content.fileName()))
			.header(HttpHeaders.CONTENT_LENGTH, content.length())
			.build();
	}

	/**
	 * Compares a configuration with another one.
	 *
	 * @param id the ID of the revised configuration
	 * @param with the ID of the original configuration, or none for the previous one of the same device
	 * @return the differences
	 */
	@GET
	@Path("/{id}/diff")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML})
	@Operation(summary = "Compare a configuration with another one", description = "Compares the configuration (the revised "
		+ "one) with another one (the original one, given by with, by default the one gathered just before it on the same "
		+ "device, or an empty configuration if it is the first). The configurations must be of the same type of device. "
		+ "The configurations are written in their brief view unless expanded (expand=original,revised).")
	public ConfigDiffDto getConfigDiff(@PathParam("id") @Parameter(description = "Configuration ID (the revised one)") long id,
		@QueryParam(WITH) @Parameter(description = "ID of the configuration to compare with (the original one)") String with) {
		MultivaluedMap<String, String> params = this.uriInfo.getQueryParameters();
		ListParams.checkSelectionOnly(params, WITH);
		Selection selection = Selection.of(params, ConfigDiffDto.class, Selection.View.FULL);
		V1Response.select(this.request, selection, ConfigDiffDto.class);
		long originalId = 0;
		if (with != null) {
			try {
				originalId = Long.parseLong(with.trim());
			}
			catch (NumberFormatException e) {
				throw NetshotException.invalidParameter(WITH, "Invalid parameter 'with': a configuration ID is expected");
			}
			if (originalId <= 0) {
				throw NetshotException.invalidParameter(WITH, "Invalid parameter 'with': a configuration ID is expected");
			}
		}
		ConfigDiff diff = this.configService.diff(originalId, id, true, false);
		Set<Long> ids = new LinkedHashSet<>();
		if (diff.originalConfigId() != 0) {
			ids.add(diff.originalConfigId());
		}
		ids.add(diff.revisedConfigId());
		Map<Long, Config> configs = new HashMap<>();
		this.configService.list(ListQuery.byIds(ids), false).items().forEach(c -> configs.put(c.id(), c));
		LongFunction<DeviceDto> devices = devices(this.searchService, this.driverRegistry, selection,
			configs.values().stream().map(Config::deviceId).toList());
		Config original = configs.get(diff.originalConfigId());
		Config revised = configs.get(diff.revisedConfigId());
		return ConfigMapper.toDto(diff, original == null ? null : ConfigMapper.toDto(original, devices, false),
			revised == null ? null : ConfigMapper.toDto(revised, devices, false));
	}

	/**
	 * Loads the devices the configurations refer to, in one query (a device which isn't found, deleted meanwhile,
	 * is given by its ID only).
	 *
	 * @param searchService the device search use cases
	 * @param driverRegistry the loaded device drivers
	 * @param selection the selection of the response
	 * @param deviceIds the IDs of the devices
	 * @return resolves a device by its ID
	 */
	private static LongFunction<DeviceDto> devices(SearchService searchService, DriverRegistry driverRegistry, Selection selection,
		Collection<Long> deviceIds) {
		LongFunction<DeviceDto> idsOnly = TaskMapper.Refs.IDS_ONLY.devices();
		Map<Long, DeviceDto> devices = new HashMap<>();
		Set<Long> ids = new LinkedHashSet<>(deviceIds);
		if (!ids.isEmpty() && selection.includes("device")) {
			ListQuery query = ListQuery.byIds(ids);
			if (selection.expands("device")) {
				searchService.listDevices(query, null).items().forEach(d -> devices.put(d.id(), DeviceMapper.toDto(driverRegistry, d)));
			}
			else {
				searchService.listDeviceSummaries(query, null).items()
					.forEach(d -> devices.put(d.id(), DeviceMapper.toDto(driverRegistry, d)));
			}
		}
		return id -> {
			DeviceDto found = devices.get(id);
			return found != null ? found : idsOnly.apply(id);
		};
	}
}
