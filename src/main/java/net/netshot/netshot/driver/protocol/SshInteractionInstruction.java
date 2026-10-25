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

import java.util.regex.Pattern;

import lombok.Getter;

/**
 * Instruction for SSH user interactive authentication mode.
 */
public final class SshInteractionInstruction {
	@Getter
	private Pattern promptPattern;

	@Getter
	private Boolean echo;

	@Getter
	private String response;

	public SshInteractionInstruction(Pattern promptPattern, Boolean echo, String response) {
		this.promptPattern = promptPattern;
		this.echo = echo;
		this.response = response;
	}
}
