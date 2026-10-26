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
package net.netshot.netshot.driver.protocol;

import lombok.Getter;
import lombok.Setter;

/**
 * Telnet-specific configuration of a driver access.
 */
public class TelnetConfig {

	/** Type of terminal. Null means "not set" (see {@link #withDefaults}). */
	@Setter
	@Getter
	private String terminalType = null;

	/** Number of columns in the terminal (negotiated via the Telnet Window Size option). Null means "not set". */
	@Setter
	@Getter
	private Integer terminalCols = null;

	/** Number of rows in the terminal (negotiated via the Telnet Window Size option). Null means "not set". */
	@Setter
	@Getter
	private Integer terminalRows = null;

	/**
	 * @param withDefaults true to populate every field with its historical default value,
	 *        false to leave every field {@code null} (a "delta", only ever merged onto
	 *        another config via {@link #apply})
	 */
	public TelnetConfig(boolean withDefaults) {
		if (withDefaults) {
			this.terminalType = "vt100";
			this.terminalCols = 80;
			this.terminalRows = 24;
		}
	}

	/**
	 * Applies the set (non-null) fields of another configuration onto this one.
	 * @param other the configuration to apply
	 */
	public void apply(TelnetConfig other) {
		if (other.terminalType != null) {
			this.terminalType = other.terminalType;
		}
		if (other.terminalCols != null) {
			this.terminalCols = other.terminalCols;
		}
		if (other.terminalRows != null) {
			this.terminalRows = other.terminalRows;
		}
	}
}
