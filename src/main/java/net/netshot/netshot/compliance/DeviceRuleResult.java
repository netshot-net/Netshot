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
package net.netshot.netshot.compliance;

import java.util.Date;

import net.netshot.netshot.inventory.compliance.CheckResult;

/**
 * The compliance state of a device for one rule (projection of the compliance results of a device).
 *
 * @param ruleId the rule ID
 * @param ruleName the rule name
 * @param policyId the ID of the policy owning the rule
 * @param policyName the name of the policy
 * @param result the result of the last check, or null if the device has not been checked
 * @param comment the comment of the last check
 * @param checkDate the date of the last check
 * @param expirationDate the expiration date of the exemption of the device, or null if it isn't exempted
 */
public record DeviceRuleResult(
	long ruleId,
	String ruleName,
	long policyId,
	String policyName,
	CheckResult.ResultOption result,
	String comment,
	Date checkDate,
	Date expirationDate) {
}
