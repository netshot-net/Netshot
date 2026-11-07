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

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.inventory.compliance.CheckResult;

/**
 * The outcome of a test of a rule against a device.
 *
 * @param result the result of the check
 * @param log what the check logged (including the script errors), null if the check did not run
 * @param comment the comment of the check
 */
@Schema(name = "RuleTestResult", description = "Result of the test of a compliance rule")
public record RuleTestResultDto(
	CheckResult.ResultOption result,
	String log,
	String comment) {
}
