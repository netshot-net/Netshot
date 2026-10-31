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
package net.netshot.netshot.api.rest.v1.report;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;
import net.netshot.netshot.inventory.compliance.CheckResult;

/**
 * The configuration compliance of a device for one rule.
 *
 * @param device the device
 * @param policy the policy of the rule
 * @param rule the rule
 * @param checkDate the date of the last check
 * @param result the result of the last check
 */
@Schema(name = "ConfigComplianceDeviceStatus", description = "Configuration compliance of a device for one rule")
public record ConfigComplianceDeviceStatusDto(
	@Ref DeviceDto device,
	ReportObjectDto policy,
	ReportObjectDto rule,
	Instant checkDate,
	CheckResult.ResultOption result) {
}
