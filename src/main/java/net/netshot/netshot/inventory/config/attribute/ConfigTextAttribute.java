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
@DiscriminatorValue("S")
public final class ConfigTextAttribute extends ConfigAttribute {

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String text;

	protected ConfigTextAttribute() {
	}

	public ConfigTextAttribute(Config config, String name, String value) {
		super(config, name);
		this.text = value;
	}

	@Override
	@Transient
	public String getAsText() {
		if (this.text == null) {
			return "";
		}
		return this.text;
	}

	@Override
	@Transient
	public Object getData() {
		return getText();
	}

	@Override
	public boolean valueEquals(ConfigAttribute obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ConfigTextAttribute)) {
			return false;
		}
		ConfigTextAttribute other = (ConfigTextAttribute) obj;
		if (this.text == null) {
			if (other.text != null) {
				return false;
			}
		}
		else if (!this.text.equals(other.text)) {
			return false;
		}
		return true;
	}

}
