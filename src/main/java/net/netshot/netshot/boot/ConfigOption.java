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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

/**
 * The configuration option of the command line of the server and the tools: {@code -c} (or {@code --config}) gives
 * the configuration file, by default {@code netshot.conf}, then {@code /etc/netshot.conf} (the first readable one).
 */
public final class ConfigOption {

	/** The configuration files to look at, in sequence, when none is given. */
	private static final List<String> DEFAULT_FILES = List.of("netshot.conf", "/etc/netshot.conf");

	private ConfigOption() {
	}

	/**
	 * Gets the option, to add to the options of the command line.
	 *
	 * @return the option
	 */
	public static Option option() {
		return Option.builder("c").longOpt("config").hasArg().desc("path to configuration file").get();
	}

	/**
	 * Gets the configuration files to look at, from the parsed command line.
	 *
	 * @param commandLine the parsed command line (with the {@link #option()})
	 * @return the given file, or the default files
	 */
	public static List<String> files(CommandLine commandLine) {
		String file = commandLine.getOptionValue("c");
		return file == null ? DEFAULT_FILES : List.of(file);
	}
}
