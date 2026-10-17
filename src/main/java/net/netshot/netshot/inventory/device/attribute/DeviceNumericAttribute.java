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

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.device.Device;

@Entity
@DiscriminatorValue("N")
public final class DeviceNumericAttribute extends DeviceAttribute {

	@Getter
	@Setter
	private Double number;

	protected DeviceNumericAttribute() {
	}

	public DeviceNumericAttribute(Device device, String name, double value) {
		super(device, name);
		this.number = value;
	}

	@Override
	@Transient
	public Object getData() {
		return this.number;
	}

}
