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
package net.netshot.netshot.boot;

import java.util.List;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Configuration option of the command line")
class ConfigOptionTest {

	private static List<String> parse(String... args) throws Exception {
		return ConfigOption.files(new DefaultParser().parse(new Options().addOption(ConfigOption.option()), args));
	}

	@Test
	@DisplayName("The configuration file given on the command line, or the default ones")
	void files() throws Exception {
		Assertions.assertEquals(List.of("netshot.conf", "/etc/netshot.conf"), parse());
		Assertions.assertEquals(List.of("/opt/netshot.conf"), parse("-c", "/opt/netshot.conf"));
		Assertions.assertEquals(List.of("/opt/netshot.conf"), parse("--config", "/opt/netshot.conf"));
	}
}
