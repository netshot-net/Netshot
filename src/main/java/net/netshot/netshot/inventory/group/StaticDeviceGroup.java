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

import java.util.Iterator;

import org.hibernate.Session;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Entity;
import net.netshot.netshot.inventory.device.Device;

/**
 * A static group of devices.
 * The devices are manually listed.
 */
@Entity
@OnDelete(action = OnDeleteAction.CASCADE)
public class StaticDeviceGroup extends DeviceGroup {

	/**
	 * Instantiates a new static device group.
	 */
	protected StaticDeviceGroup() {

	}

	/**
	 * Instantiates a new static device group.
	 *
	 * @param name the name
	 */
	public StaticDeviceGroup(String name) {
		super(name);
	}

	/**
	 * Adds the device at the end of the group's ordered list.
	 *
	 * @param device the device
	 */
	public void addDevice(Device device) {
		DeviceGroupMembership membership = new DeviceGroupMembership(device, this, this.cachedMemberships.size());
		this.cachedMemberships.add(membership);
	}

	/**
	 * Removes the device.
	 *
	 * @param device the device
	 */
	public void removeDevice(Device device) {
		Iterator<DeviceGroupMembership> membershipIt = this.cachedMemberships.iterator();
		while (membershipIt.hasNext()) {
			DeviceGroupMembership membership = membershipIt.next();
			if (membership.getDevice().equals(device)) {
				membershipIt.remove();
			}
		}
	}

	/**
	 * Clear devices.
	 */
	public void clearDevices() {
		this.cachedMemberships.clear();
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.inventory.group.DeviceGroup#refreshCache(org.hibernate.Session)
	 */
	@Override
	public void refreshCache(Session session) throws Exception {
	}

}
