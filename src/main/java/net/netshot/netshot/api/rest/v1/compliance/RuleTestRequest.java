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

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.RefId;

/**
 * The test of a rule definition against a device.
 *
 * @param rule the definition of the rule to test (not stored; its name, policy and enabled flag are not used)
 * @param device the ID of the device to check the rule against
 */
@Schema(name = "RuleTestRequest", description = "Test of a compliance rule definition against a device")
public record RuleTestRequest(
	@JsonProperty(required = true) RuleRequest rule,
	@JsonProperty(required = true) RefId device) {
}
