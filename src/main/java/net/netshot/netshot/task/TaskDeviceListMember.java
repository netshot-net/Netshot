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
package net.netshot.netshot.task;

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
 * One member (device, at a given position) of a task's one-time, ordered device list.
 * Mirrors {@link net.netshot.netshot.inventory.group.DeviceGroupMembership}'s composite-key pattern,
 * with an added ordering column.
 */
@Entity
@Table(name = "task_device_list_members")
public class TaskDeviceListMember {

	@Embeddable
	private static class Key implements Serializable {

		private static final long serialVersionUID = 1L;

		@Getter(onMethod = @__({
			@ManyToOne,
			@JoinColumn(name = "task_id"),
			@OnDelete(action = OnDeleteAction.CASCADE),
		}))
		@Setter
		private Task task;

		@Getter(onMethod = @__({
			@ManyToOne,
			@JoinColumn(name = "device_id"),
			@OnDelete(action = OnDeleteAction.CASCADE),
		}))
		@Setter
		private Device device;

		protected Key() {
			//
		}

		Key(Task task, Device device) {
			this.task = task;
			this.device = device;
		}

		@Override
		public int hashCode() {
			return Objects.hash(task, device);
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
			return Objects.equals(task, other.task) && Objects.equals(device, other.device);
		}
	}

	/** The key. */
	@Getter(onMethod = @__({
		@EmbeddedId
	}))
	@Setter
	private Key key = new Key();

	/** Position of the device within the ordered list. */
	@Getter(onMethod = @__({
		@Column(name = "list_position")
	}))
	@Setter
	private int position;

	protected TaskDeviceListMember() {
		//
	}

	public TaskDeviceListMember(Task task, Device device, int position) {
		this.key = new Key(task, device);
		this.position = position;
	}

	@Transient
	public Task getTask() {
		return this.key.getTask();
	}

	@Transient
	public Device getDevice() {
		return this.key.getDevice();
	}

}
