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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.device.Device;

/**
 * A group of devices.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", length = 16)
public abstract class DeviceGroup {

	/** The cached devices, in user-defined order (for static groups). */
	@Getter(onMethod = @__({
		@OneToMany(mappedBy = "key.group", cascade = CascadeType.ALL, orphanRemoval = true),
		@OrderBy("position asc"),
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	protected List<DeviceGroupMembership> cachedMemberships = new ArrayList<>();

	/** The change date. */
	@Getter
	@Setter
	protected Date changeDate;

	/** Version internal field. */
	@Getter(onMethod = @__({
		@Version
	}))
	@Setter
	private int version;

	/** The id. */
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	protected long id;

	/** The name. */
	@Getter(onMethod = @__({
		@NaturalId(mutable = true)
	}))
	@Setter
	protected String name;

	/** Folder containing the group. */
	@Getter(onMethod = @__({
		@Column(length = 1000)
	}))
	@Setter
	protected String folder = "";

	/** Whether the group should be hidden in reports. */
	@Getter
	@Setter
	protected boolean hiddenFromReports;

	/**
	 * Instantiates a new device group.
	 */
	protected DeviceGroup() {

	}

	/**
	 * Instantiates a new device group.
	 *
	 * @param name the name
	 */
	public DeviceGroup(String name) {
		this.name = name;
	}

	/**
	 * Return cached members of this group, in their stored order.
	 * @return the ordered cached devices of the group
	 */
	@Transient
	public List<Device> getCachedDevices() {
		List<Device> devices = new ArrayList<>(this.cachedMemberships.size());
		for (DeviceGroupMembership membership : this.cachedMemberships) {
			devices.add(membership.getDevice());
		}
		return devices;
	}

	/**
	 * Refresh cache.
	 *
	 * @param session the session
	 * @throws Exception the exception
	 */
	public abstract void refreshCache(Session session) throws Exception;

	/**
	 * Update cached devices, preserving the given order.
	 * Existing membership rows are reused (only their position is updated) so that
	 * unaffected devices aren't needlessly deleted and reinserted on refresh.
	 *
	 * @param devices the ordered devices
	 */
	public void updateCachedDevices(List<Device> devices) {
		Map<Device, DeviceGroupMembership> existingByDevice = new HashMap<>();
		for (DeviceGroupMembership membership : this.cachedMemberships) {
			existingByDevice.put(membership.getDevice(), membership);
		}
		List<DeviceGroupMembership> memberships = new ArrayList<>(devices.size());
		int position = 0;
		for (Device device : devices) {
			DeviceGroupMembership membership = existingByDevice.get(device);
			if (membership == null) {
				membership = new DeviceGroupMembership(device, this, position);
			}
			else {
				membership.setPosition(position);
			}
			memberships.add(membership);
			position++;
		}
		this.cachedMemberships.clear();
		this.cachedMemberships.addAll(memberships);
	}

	@Override
	public String toString() {
		return "Device Group " + id + " (name '" + name + "')";
	}

}
