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
package net.netshot.netshot.inventory.config.attribute;

import java.util.Objects;

/**
 * A binary (true/false) configuration attribute.
 *
 * @param name the name of the attribute
 * @param assumption the value
 */
public record ConfigBinaryAttribute(String name, Boolean assumption) implements ConfigAttribute {

	@Override
	public String getAsText() {
		return Boolean.TRUE.equals(this.assumption) ? "true" : "false";
	}

	@Override
	public Object getData() {
		return this.assumption;
	}

	@Override
	public boolean valueEquals(ConfigAttribute other) {
		return other instanceof ConfigBinaryAttribute o && Objects.equals(this.assumption, o.assumption);
	}
}
