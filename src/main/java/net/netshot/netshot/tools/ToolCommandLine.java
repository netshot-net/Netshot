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
package net.netshot.netshot.tools;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import net.netshot.netshot.boot.ConfigOption;

/**
 * The command line of the tools: the configuration file to use, as for the server ({@code -c}).
 */
final class ToolCommandLine {

	private ToolCommandLine() {
	}

	/**
	 * Gets the configuration files to look at from the arguments of a tool.
	 *
	 * @param args the arguments of the tool
	 * @return the file given with {@code -c}, or the default files of the server
	 * @throws ParseException if the arguments can't be parsed
	 */
	static List<String> configFiles(String[] args) throws ParseException {
		CommandLine commandLine = new DefaultParser().parse(new Options().addOption(ConfigOption.option()), args);
		return ConfigOption.files(commandLine);
	}
}
