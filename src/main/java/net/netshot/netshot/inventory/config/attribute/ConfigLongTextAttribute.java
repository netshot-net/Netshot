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

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.config.Config;

@Entity
@DiscriminatorValue("T")
public final class ConfigLongTextAttribute extends ConfigAttribute {

	@Getter(onMethod = @__({
		@OneToOne(orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL),
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	private LongTextConfiguration longText;

	protected ConfigLongTextAttribute() {
	}

	public ConfigLongTextAttribute(Config config, String name, String value) {
		super(config, name);
		this.longText = new LongTextConfiguration(value);
	}

	@Override
	@Transient
	public String getAsText() {
		if (this.longText == null || this.longText.getText() == null) {
			return "";
		}
		return this.longText.getText();
	}

	@Override
	@Transient
	public Object getData() {
		if (getLongText() == null) {
			return null;
		}
		return getLongText().getText();
	}

	@Override
	public boolean valueEquals(ConfigAttribute obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ConfigLongTextAttribute)) {
			return false;
		}
		ConfigLongTextAttribute other = (ConfigLongTextAttribute) obj;
		if (longText == null) {
			if (other.longText != null) {
				return false;
			}
		}
		else if (!longText.equals(other.longText)) {
			return false;
		}
		return true;
	}

}
