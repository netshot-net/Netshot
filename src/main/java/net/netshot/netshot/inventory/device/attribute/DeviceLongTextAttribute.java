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
package net.netshot.netshot.inventory.device.attribute;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.config.attribute.LongTextConfiguration;
import net.netshot.netshot.inventory.device.Device;

@Entity
@DiscriminatorValue("T")
public final class DeviceLongTextAttribute extends DeviceAttribute {

	@Getter(onMethod = @__({
		@OneToOne(orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	}))
	@Setter
	private LongTextConfiguration longText;

	protected DeviceLongTextAttribute() {
	}

	public DeviceLongTextAttribute(Device device, String name, String value) {
		super(device, name);
		this.longText = new LongTextConfiguration(value);
	}

	@Override
	@Transient
	public Object getData() {
		if (getLongText() == null) {
			return null;
		}
		return getLongText().getText();
	}

}
