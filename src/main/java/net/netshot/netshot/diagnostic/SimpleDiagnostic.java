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
package net.netshot.netshot.diagnostic;

import org.graalvm.polyglot.HostAccess.Export;

import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;

/**
 * A diagnostic running a CLI command on the devices (of a driver), the result being modified by
 * a regular expression.
 *
 * @param id the ID (0 for a diagnostic not stored yet)
 * @param name the name
 * @param enabled true if the diagnostic is run
 * @param targetGroupId the ID of the group of devices the diagnostic is run on
 * @param resultType the type of result
 * @param deviceDriver the name of the driver of the devices
 * @param cliMode the CLI mode to run the command in
 * @param command the command
 * @param modifierPattern the regular expression to apply to the output
 * @param modifierReplacement the replacement of the regular expression
 */
public record SimpleDiagnostic(long id, String name, boolean enabled, Long targetGroupId, AttributeType resultType,
	String deviceDriver, String cliMode, String command, String modifierPattern, String modifierReplacement) implements Diagnostic {

	/**
	 * The diagnostic, as the driver scripts see it.
	 */
	public static class JsSimpleDiagnostic {
		@Getter(onMethod = @__({
			@Export
		}))
		@Setter
		private String mode;

		@Getter(onMethod = @__({
			@Export
		}))
		@Setter
		private String command;

		public JsSimpleDiagnostic(String mode, String command) {
			this.mode = mode;
			this.command = command;
		}
	}

	@Override
	public SimpleDiagnostic withId(long newId) {
		return new SimpleDiagnostic(newId, this.name, this.enabled, this.targetGroupId, this.resultType, this.deviceDriver,
			this.cliMode, this.command, this.modifierPattern, this.modifierReplacement);
	}

	@Override
	public String toString() {
		return Diagnostic.describe(this);
	}
}
