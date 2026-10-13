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
package net.netshot.netshot.inventory.group;

import java.io.Serializable;
import java.util.Objects;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.device.Device;

/**
 * Device membership within a group.
 */
@Entity
@Table(name = "device_group_cached_devices")
public class DeviceGroupMembership {

	@Embeddable
	private static class Key implements Serializable {

		private static final long serialVersionUID = 10209729310192924L;

		@Getter(onMethod = @__({
			@ManyToOne,
			@JoinColumn(name = "owner_groups"),
			@OnDelete(action = OnDeleteAction.CASCADE),
		}))
		@Setter
		private DeviceGroup group;

		@Getter(onMethod = @__({
			@ManyToOne,
			@JoinColumn(name = "cached_devices"),
			@OnDelete(action = OnDeleteAction.CASCADE),
		}))
		@Setter
		private Device device;

		protected Key() {
			//
		}

		Key(Device device, DeviceGroup group) {
			this.device = device;
			this.group = group;
		}

		@Override
		public int hashCode() {
			return Objects.hash(group, device);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof Key)) {
				return false;
			}
			Key other = (Key) obj;
			return Objects.equals(group, other.group) && Objects.equals(device, other.device);
		}
	}

	/** The key. */
	@Getter(onMethod = @__({
		@EmbeddedId
	}))
	@Setter
	private Key key = new Key();

	/** Position of the device within the group's ordered membership list. */
	@Getter(onMethod = @__({
		@Column(name = "list_position")
	}))
	@Setter
	private int position;

	protected DeviceGroupMembership() {
		//
	}

	public DeviceGroupMembership(Device device, DeviceGroup group, int position) {
		this.key = new Key(device, group);
		this.position = position;
	}

	@Transient
	public DeviceGroup getGroup() {
		return this.key.getGroup();
	}

	@Transient
	public Device getDevice() {
		return this.key.getDevice();
	}

}
