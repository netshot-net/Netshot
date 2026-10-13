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

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

@Entity
@DiscriminatorValue("B")
public final class ConfigBinaryAttribute extends ConfigAttribute {

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private Boolean assumption;

	protected ConfigBinaryAttribute() {
	}

	public ConfigBinaryAttribute(Config config, String name, boolean value) {
		super(config, name);
		this.assumption = value;
	}

	@Override
	@Transient
	public String getAsText() {
		if (Boolean.TRUE.equals(this.assumption)) {
			return "true";
		}
		else {
			return "false";
		}
	}

	@Override
	@Transient
	public Object getData() {
		return getAssumption();
	}

	@Override
	public boolean valueEquals(ConfigAttribute obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ConfigBinaryAttribute)) {
			return false;
		}
		ConfigBinaryAttribute other = (ConfigBinaryAttribute) obj;
		if (this.assumption == null) {
			if (other.assumption != null) {
				return false;
			}
		}
		else if (!this.assumption.equals(other.assumption)) {
			return false;
		}
		return true;
	}

}
