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

import java.time.Instant;

/**
 * The exemption of a device from a compliance rule, until a date.
 *
 * @param ruleId the ID of the rule
 * @param deviceId the ID of the device
 * @param expirationDate when the exemption ends
 */
public record Exemption(long ruleId, long deviceId, Instant expirationDate) {

	/**
	 * Tells whether the exemption is still running.
	 *
	 * @param now the current time
	 * @return true if the exemption ends after now
	 */
	public boolean isActive(Instant now) {
		return this.expirationDate != null && this.expirationDate.isAfter(now);
	}
}
