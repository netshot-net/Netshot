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
package net.netshot.netshot.driver;

import java.lang.reflect.Field;

import net.netshot.netshot.FakeTaskContext;
import net.netshot.netshot.WithDeviceSimulatorTest;
import net.netshot.netshot.access.Telnet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Telnet config merge test")
public class TelnetConfigMergeTest extends WithDeviceSimulatorTest {

	/**
	 * Reads a private {@link Telnet.TelnetConfig} field via reflection - mirrors how
	 * {@link RekeyTest} already reaches into {@code Ssh}'s own private state, since
	 * {@code TelnetConfig} deliberately exposes no public getters (same minimalism as
	 * {@link Ssh.SshConfig}).
	 * @param config the Telnet configuration
	 * @param fieldName the name of the field
	 * @return the value of the field
	 */
	private Object getTelnetConfigField(Telnet.TelnetConfig config, String fieldName) throws Exception {
		Field field = Telnet.TelnetConfig.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(config);
	}

	@Test
	@DisplayName("applyTelnetConfig(...) merges non-null fields only, leaving the rest at their prior value")
	void applyTelnetConfigMergesNonNullFieldsOnly() throws Exception {
		Telnet telnet = new Telnet("127.0.0.1", 23, new FakeTaskContext());

		// Fresh session: seeded with the historical defaults (vt100/80/24).
		Telnet.TelnetConfig initial = telnet.getTelnetConfig();
		Assertions.assertEquals("vt100", this.getTelnetConfigField(initial, "terminalType"));
		Assertions.assertEquals(80, this.getTelnetConfigField(initial, "terminalCols"));
		Assertions.assertEquals(24, this.getTelnetConfigField(initial, "terminalRows"));

		// Driver-declared delta (as DeviceDriver.parseTelnetConfig would produce from
		// `CLI.telnet.config`): only terminalCols set.
		Telnet.TelnetConfig declared = new Telnet.TelnetConfig(false);
		declared.setTerminalCols(132);
		telnet.applyTelnetConfig(declared);
		Telnet.TelnetConfig afterDeclared = telnet.getTelnetConfig();
		Assertions.assertEquals("vt100", this.getTelnetConfigField(afterDeclared, "terminalType"),
			"Unset fields in the declared delta must leave the default untouched");
		Assertions.assertEquals(132, this.getTelnetConfigField(afterDeclared, "terminalCols"));
		Assertions.assertEquals(24, this.getTelnetConfigField(afterDeclared, "terminalRows"));

		// client.create(...) "telnetConfig" advanced-option override: only terminalRows set -
		// must merge on top without disturbing the already-applied terminalCols=132.
		Telnet.TelnetConfig override = new Telnet.TelnetConfig(false);
		override.setTerminalRows(50);
		telnet.applyTelnetConfig(override);
		Telnet.TelnetConfig afterOverride = telnet.getTelnetConfig();
		Assertions.assertEquals("vt100", this.getTelnetConfigField(afterOverride, "terminalType"));
		Assertions.assertEquals(132, this.getTelnetConfigField(afterOverride, "terminalCols"),
			"The client.create(...) override must not clobber a field it didn't set itself");
		Assertions.assertEquals(50, this.getTelnetConfigField(afterOverride, "terminalRows"));
	}
}
