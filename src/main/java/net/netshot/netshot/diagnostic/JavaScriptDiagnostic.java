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
package net.netshot.netshot.diagnostic;

import java.time.Instant;

import net.netshot.netshot.driver.AttributeDefinition.AttributeType;

/**
 * A diagnostic running a JavaScript script on the devices.
 *
 * @param id the ID (0 for a diagnostic not stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the date of the last change
 * @param name the name
 * @param enabled true if the diagnostic is run
 * @param targetGroupId the ID of the group of devices the diagnostic is run on
 * @param resultType the type of result
 * @param script the script
 */
public record JavaScriptDiagnostic(long id, int version, Instant changeDate, String name, boolean enabled, Long targetGroupId, AttributeType resultType,
	String script) implements Diagnostic {

	@Override
	public JavaScriptDiagnostic withId(long newId) {
		return new JavaScriptDiagnostic(newId, this.version, this.changeDate, this.name, this.enabled, this.targetGroupId, this.resultType, this.script);
	}

	@Override
	public String toString() {
		return Diagnostic.describe(this);
	}
}
