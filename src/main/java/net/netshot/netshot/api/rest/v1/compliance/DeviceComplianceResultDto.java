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
package net.netshot.netshot.api.rest.v1.compliance;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;
import net.netshot.netshot.inventory.compliance.CheckResult;

/**
 * The compliance state of a device for one rule (a link between the two, not an object of its own: it has no ID of
 * its own, but identified by the pair of the device and of the rule).
 *
 * @param device the device
 * @param rule the rule
 * @param policy the policy of the rule
 * @param result the result of the last check, or null if the device has not been checked
 * @param comment the comment of the last check
 * @param checkDate the date of the last check
 * @param exemptionExpirationDate when the exemption of the device from the rule ends, or null if it isn't exempted
 */
@Schema(name = "DeviceComplianceResult", description = "Compliance state of a device for a rule")
public record DeviceComplianceResultDto(
	@Brief @Ref DeviceDto device,
	@Brief @Ref RuleDto rule,
	@Brief @Ref PolicyDto policy,
	@Brief CheckResult.ResultOption result,
	String comment,
	@Brief Instant checkDate,
	Instant exemptionExpirationDate) {
}
