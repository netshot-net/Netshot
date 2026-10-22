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

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.group.DeviceGroup;

/**
 * This is a simple diagnostic: runs a CLI command in a CLI mode, and optionally
 * transforms the result using a regular expression.
 * 
 * @author sylvain.cadilhac
 */
@Entity
@DiscriminatorValue("SIMPLE")
public class SimpleDiagnostic extends Diagnostic {

	/**
	 * A simple object to store command and CLI mode, to be passed to Javascript for execution.
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

	/**
	 * The type of device this diagnostic applies to.
	 */
	@Getter
	@Setter
	private String deviceDriver;

	/**
	 * The CLI mode to run the command in.
	 */
	@Getter
	@Setter
	private String cliMode;

	/**
	 * The CLI command to run.
	 */
	@Getter
	@Setter
	private String command;

	/**
	 * The pattern to search for in the result string.
	 */
	@Getter
	@Setter
	private String modifierPattern;

	/**
	 * The replacement string (to replace the modifierPattern with). Also supports
	 * regular expression backreferences.
	 */
	@Getter
	@Setter
	private String modifierReplacement;

	/**
	 * Instantiates a new SimpleDiagnostic. For Hibernate.
	 */
	protected SimpleDiagnostic() {
	}

	/**
	 * Instantiates a new diagnostic.
	 * 
	 * @param name = The name
	 * @param enabled = True to enable the diagnostic
	 * @param targetGroup = The group of devices the diagnostic applies to
	 * @param resultType = The type of result expected by this diagnostic
	 * @param deviceDriver = The device driver
	 * @param cliMode = The CLI mode
	 * @param command = The CLI command to issue
	 * @param modifierPattern = The pattern to match
	 * @param modifierReplacement = The replacement script if the pattern is matched
	 */
	public SimpleDiagnostic(String name, boolean enabled, DeviceGroup targetGroup, AttributeType resultType,
		String deviceDriver, String cliMode, String command, String modifierPattern, String modifierReplacement) {
		super(name, enabled, targetGroup, resultType);
		this.deviceDriver = deviceDriver;
		this.cliMode = cliMode;
		this.command = command;
		this.modifierPattern = modifierPattern;
		this.modifierReplacement = modifierReplacement;
	}

}
