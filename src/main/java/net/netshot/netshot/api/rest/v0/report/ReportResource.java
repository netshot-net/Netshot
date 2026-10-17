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
package net.netshot.netshot.api.rest.v0.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.poi.xssf.streaming.SXSSFWorkbook;


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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.api.rest.PaginationParams;
import net.netshot.netshot.api.rest.v0.device.DeviceMapper;
import net.netshot.netshot.api.rest.v0.device.DeviceSummaryDto;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.report.ReportExportSelection;
import net.netshot.netshot.report.ReportService;

/**
 * The REST resource of the reports: statistics, device lists behind them, and the data export.
 *
 * <p>It only translates between HTTP and the {@link ReportService}. The export is written to a
 * spreadsheet by an {@link XlsxReportWriter} fed by the service. The class is mounted on
 * {@code /} with the full path on each method, like the other migrated resources.</p>
 */
@Path("/")
@Slf4j
public class ReportResource {

	private final ReportService reportService;

	@Context
	private SecurityContext securityContext;

	/**
	 * Instantiates the resource.
	 *
	 * @param reportService the report use cases
	 */
	@Inject
	public ReportResource(ReportService reportService) {
		this.reportService = reportService;
	}

	/**
	 * Gets the last7 days changes by day stats.
	 *
	 * @param jsTimeZone the time zone to base dates on
	 * @return the last7 days changes by day stats
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/last7dayschangesbyday")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the number of configuration changes for the last 7 days",
		description = "Returns the number of device configuration changes per day, for the last 7 days."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Deprecated
	public List<ConfigChangeCountDto> getLast7DaysChangesByDayStats(@QueryParam("tz") String jsTimeZone) throws WebApplicationException {
		log.debug("REST request, get last 7 day changes by day stats.");
		return ReportMapper.toChangeCountDtos(this.reportService.getLast7DaysChanges(jsTimeZone));
	}

	/**
	 * Gets the group config compliance stats.
	 *
	 * @param domains the domains
	 * @param deviceGroups the device groups
	 * @param policies the policies
	 * @return the group config compliance stats
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/groupconfigcompliancestats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the compliance status of a device group",
		description = "Returns the compliance status of a given device group, by ID."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<GroupConfigComplianceStatDto> getGroupConfigComplianceStats(
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@QueryParam("group") @Parameter(description = "Filter on given group ID(s)") Set<Long> deviceGroups,
		@QueryParam("policy") @Parameter(description = "Filter on given policy ID(s)") Set<Long> policies)
		throws WebApplicationException {
		log.debug("REST request, group config compliance stats.");
		return ReportMapper.toConfigComplianceDtos(
			this.reportService.getGroupConfigComplianceStats(domains, deviceGroups, policies));
	}

	/**
	 * Gets the hardware support stats.
	 *
	 * @param domains the domains
	 * @param deviceGroups the device groups
	 * @return the end-of-sale counts, then the end-of-life ones
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/hardwaresupportstats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the global hardware support status",
		description = "Returns the global hardware support status, i.e. a list of End-of-Life and End-of-Sale dates with the corresponding device count; "
		+ "optionally filtered by domain or group."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<HardwareSupportStatDto> getHardwareSupportStats(
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@QueryParam("group") @Parameter(description = "Filter on given group ID(s)") Set<Long> deviceGroups)
		throws WebApplicationException {
		log.debug("REST request, hardware support stats.");
		return ReportMapper.toHardwareSupportDtos(this.reportService.getHardwareSupportStats(domains, deviceGroups));
	}

	/**
	 * Gets the group software compliance stats.
	 *
	 * @param domains the domains
	 * @return the group software compliance stats
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/groupsoftwarecompliancestats")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the global software compliance status",
		description = "Returns the software compliance status of devices, optionally filtered by a list of device domains."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<GroupSoftwareComplianceStatDto> getGroupSoftwareComplianceStats(
			@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains)
		throws WebApplicationException {
		log.debug("REST request, group software compliance stats.");
		return ReportMapper.toSoftwareComplianceDtos(this.reportService.getGroupSoftwareComplianceStats(domains));
	}

	/**
	 * Gets the configuration compliance results.
	 *
	 * @param domains optional filter on device domain
	 * @param groups optional filter on device groups
	 * @param policies optional filter on compliance policy
	 * @param results optional filter on compliance result
	 * @return the config compliance results
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/configcompliancedevicestatuses")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the configuration compliance status of devices",
		description = "Returns the configuration compliance status of devices; optionally filtered by domain, group, policy or compliance level."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<PolicyRuleDeviceDto> getConfigComplianceDeviceStatuses(
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@QueryParam("group") @Parameter(description = "Filter on given group ID(s)") Set<Long> groups,
		@QueryParam("policy") @Parameter(description = "Filter on given policy ID(s)") Set<Long> policies,
		@QueryParam("result") @Parameter(description = "Filter on given result(s)") Set<CheckResult.ResultOption> results)
		throws WebApplicationException {
		log.debug("REST request, config compliant device statuses.");
		return ReportMapper.toPolicyRuleDeviceDtos(
			this.reportService.getConfigComplianceDeviceStatuses(domains, groups, policies, results));
	}

	/**
	 * Gets the group config non compliant devices.
	 *
	 * @param id the id
	 * @param domains the domains
	 * @param policies the policies
	 * @return the group config non compliant devices
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/groupconfignoncompliantdevices/{id}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the non compliant devices of a group",
		description = "Returns the list of non-compliance devices part of a given group, optionally filtered by domains and policies"
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<PolicyRuleDeviceDto> getGroupConfigNonCompliantDevices(
		@PathParam("id") @Parameter(description = "Group ID") Long id,
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@QueryParam("policy") @Parameter(description = "Filter on given policy ID(s)") Set<Long> policies)
		throws WebApplicationException {
		log.debug("REST request, group config non compliant devices.");
		return ReportMapper.toPolicyRuleDeviceDtos(
			this.reportService.getGroupConfigNonCompliantDevices(id, domains, policies));
	}

	/**
	 * Gets the devices reaching End-of-Life or End-of-Sale at a given date.
	 *
	 * @param type eos or eol
	 * @param date the date
	 * @param domains the domains
	 * @param deviceGroups the device groups
	 * @return the devices
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/hardwaresupportdevices/{type}/{date}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the End-of-Life or End-of-Sale devices matching a date.",
		description = "Returns the list of devices getting End-of-Life (type 'eol') or End-of-Sale (type 'eos') at the given date (or never if 'date' is not given); "
		+ "optionally filtered by domain or group."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<DeviceSummaryDto> getHardwareStatusDevices(
		@PathParam("type") @Parameter(description = "eos (end-of-sale) or eol (end-of-life), type of date") String type,
		@PathParam("date") @Parameter(description = "EoX date to filter on") Long date,
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@QueryParam("group") @Parameter(description = "Filter on given group ID(s)") Set<Long> deviceGroups) throws WebApplicationException {
		log.debug("REST request, EoX devices by type and date.");
		return DeviceMapper.toSummaryDtos(this.reportService.getHardwareSupportDevices(type, date, domains, deviceGroups));
	}

	/**
	 * Gets the group devices by software level.
	 *
	 * @param id the id
	 * @param level the level
	 * @param domains the domains
	 * @return the group devices by software level
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/groupdevicesbysoftwarelevel/{id}/{level}")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the devices of a group based on software compliance level",
		description = "Returns the list of devices of a given group by ID, and matching the given software compliance level."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	@Tag(name = "Compliance", description = "Configuration, software, hardware compliance")
	public List<SoftwareLevelDeviceDto> getGroupDevicesBySoftwareLevel(
		@PathParam("id") @Parameter(description = "Group ID") Long id,
		@PathParam("level") @Parameter(description = "Software compliance level") String level,
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains)
		throws WebApplicationException {
		log.debug("REST request, group {} devices by software level {}.", id, level);
		return ReportMapper.toSoftwareLevelDeviceDtos(this.reportService.getGroupDevicesBySoftwareLevel(id, level, domains));
	}

	/**
	 * Returns the devices with recent access failure.
	 * @param days = the number of days
	 * @param domains = the domains to filter on
	 * @param paginationParams = the pagination parameters
	 * @return the list of devices
	 */
	@GET
	@Path("/reports/accessfailuredevices")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	@Operation(
		summary = "Get the devices without successful snapshot over a given period",
		description = "Returns the list of devices which didn't have a successful snapshot over the given number of days, optionally "
			+ "filtered by device domain."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	public List<AccessFailureDeviceDto> getAccessFailureDevices(
		@QueryParam("days") @Parameter(description = "Look for the given number of last days") Integer days,
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@BeanParam PaginationParams paginationParams)
		throws WebApplicationException {
		log.debug("REST request, devices without successful snapshot over the last {} days.", days);
		return ReportMapper.toAccessFailureDeviceDtos(
			this.reportService.getAccessFailureDevices(days, domains, paginationParams.toPage()));
	}

	/**
	 * Exports the data as a spreadsheet.
	 *
	 * @param groups the groups to export the devices of
	 * @param domains the domains to export the devices of
	 * @param exportInterfaces whether to export the interfaces
	 * @param exportInventory whether to export the inventory
	 * @param exportInventoryHistory whether the inventory includes the removed modules
	 * @param exportLocations whether to export the locations
	 * @param exportCompliance whether to export the compliance results
	 * @param exportGroups whether to export the groups
	 * @param exportDeviceDriverAttributes whether to export the driver-specific attributes
	 * @param fileFormat the format (only xlsx is supported)
	 * @return the spreadsheet
	 * @throws WebApplicationException the web application exception
	 */
	@GET
	@Path("/reports/export")
	@RolesAllowed(User.ROLE_READONLY)
	@Produces({"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"})
	@Operation(
		summary = "Export data",
		description = "Exports data as Excel datasheet. The devices can be filtered by groups or domains. "
			+ "The report can be customized to include or not interfaces, inventory, locations, compliance, groups. "
			+ "The only supported and default output format is xlsx (Excel file)."
	)
	@Tag(name = "Reports", description = "Report and statistics")
	public Response getDataXLSX(
		@QueryParam("group") @Parameter(description = "Filter on given group ID(s)") Set<Long> groups,
		@QueryParam("domain") @Parameter(description = "Filter on given domain ID(s)") Set<Long> domains,
		@DefaultValue("false") @QueryParam("interfaces") @Parameter(description = "Whether to export interface data") boolean exportInterfaces,
		@DefaultValue("false") @QueryParam("inventory") @Parameter(description = "Whether to export inventory data") boolean exportInventory,
		@DefaultValue("false") @QueryParam("inventoryhistory") @Parameter(description = "Whether to export interface history") boolean exportInventoryHistory,
		@DefaultValue("false") @QueryParam("locations") @Parameter(description = "Whether to export locations") boolean exportLocations,
		@DefaultValue("false") @QueryParam("compliance") @Parameter(description = "Whether to export compliance results") boolean exportCompliance,
		@DefaultValue("false") @QueryParam("groups") @Parameter(description = "Whether to export group info") boolean exportGroups,
		@DefaultValue("false") @QueryParam("devicedriverattributes") @Parameter(description = "Whether to export driver-specific attributes for devices") boolean exportDeviceDriverAttributes,
		@DefaultValue("xlsx") @QueryParam("format") @Parameter(description = "Export format (xlsx is supported)") String fileFormat) throws WebApplicationException {
		log.debug("REST request, export data.");
		User user = (User) this.securityContext.getUserPrincipal();

		if (fileFormat.compareToIgnoreCase("xlsx") == 0) {
			String fileName = String.format("netshot-export_%s.xlsx", new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));

			ReportExportSelection selection = new ReportExportSelection(domains, groups, exportInterfaces, exportInventory,
				exportInventoryHistory, exportLocations, exportCompliance, exportGroups, exportDeviceDriverAttributes);
			SXSSFWorkbook workBook = new SXSSFWorkbook(100);
			try {
				this.reportService.export(selection, new XlsxReportWriter(workBook, Netshot.VERSION, user.getName()));
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				workBook.write(output);
				byte[] content = output.toByteArray();
				return Response
					.ok(content, MediaType.APPLICATION_OCTET_STREAM)
					.header(Headers.CONTENT_DISPOSITION_STRING, "attachment; filename=\"%s\"".formatted(fileName))
					.header(Headers.CONTENT_LENGTH_STRING, content.length)
					.build();
			}
			catch (IOException e) {
				log.error("Unable to write the resulting file.", e);
				throw new WebApplicationException(
					"Unable to write the resulting file.",
					Response.Status.INTERNAL_SERVER_ERROR);
			}
			catch (Exception e) {
				log.error("Unable to generate the report.", e);
				throw new WebApplicationException("Unable to generate the report.",
					Response.Status.INTERNAL_SERVER_ERROR);
			}
			finally {
				try {
					workBook.close();
				}
				catch (IOException e) {
					log.warn("Error while closing work book", e);
				}
			}
		}

		log.warn("Invalid requested file format.");
		throw new WebApplicationException(
			"The requested file format is invalid or not supported.",
			Response.Status.BAD_REQUEST);
	}
}
