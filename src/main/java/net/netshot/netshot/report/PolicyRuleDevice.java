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
package net.netshot.netshot.report;

import java.util.Date;

import net.netshot.netshot.inventory.compliance.CheckResult.ResultOption;
import net.netshot.netshot.inventory.device.DeviceSummary;

/**
 * The result of a compliance rule for a device.
 *
 * @param device the device
 * @param policyId the policy ID
 * @param policyName the policy name
 * @param ruleId the rule ID
 * @param ruleName the rule name
 * @param checkDate the date of the check
 * @param result the result
 */
public record PolicyRuleDevice(
	DeviceSummary device,
	long policyId,
	String policyName,
	long ruleId,
	String ruleName,
	Date checkDate,
	ResultOption result) {
}
