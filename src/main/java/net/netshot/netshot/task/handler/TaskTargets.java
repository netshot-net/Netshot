/**
 * Copyright 2013-present Netshot
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
package net.netshot.netshot.task.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.type.GroupBasedTask;

/**
 * The targets of a task working on a group or a list of devices, loaded for the run.
 *
 * @param group the target group (null for a device list, or if the group was deleted)
 * @param devices the target devices: the devices of the group, or the listed devices which still exist, in order
 */
record TaskTargets(DeviceGroup group, List<Device> devices) {

	/**
	 * Loads the targets of a task.
	 *
	 * @param transactions the transaction support
	 * @param spec the task specification
	 * @return the targets
	 */
	static TaskTargets load(Transactions transactions, GroupBasedTask spec) {
		if (spec.deviceGroupId() == null && spec.deviceIds().isEmpty()) {
			return new TaskTargets(null, List.of());
		}
		return transactions.read(dsl -> {
			DeviceRepository deviceRepository = new DeviceRepository();
			if (spec.deviceGroupId() != null) {
				GroupRepository groupRepository = new GroupRepository();
				DeviceGroup group = groupRepository.find(dsl, spec.deviceGroupId());
				if (group != null) {
					List<Long> memberIds = groupRepository.findMemberIds(dsl, group.id());
					return new TaskTargets(group, inOrder(deviceRepository.findAll(dsl, memberIds), memberIds));
				}
				return new TaskTargets(null, List.of());
			}
			return new TaskTargets(null, inOrder(deviceRepository.findAll(dsl, spec.deviceIds()), spec.deviceIds()));
		});
	}

	/**
	 * Sorts devices in the order of a list of IDs.
	 *
	 * @param devices the devices
	 * @param ids the IDs, in the order to give (the IDs of devices which don't exist are ignored)
	 * @return the devices, in order
	 */
	private static List<Device> inOrder(List<Device> devices, List<Long> ids) {
		Map<Long, Device> byId = new HashMap<>();
		for (Device device : devices) {
			byId.put(device.id(), device);
		}
		List<Device> ordered = new ArrayList<>();
		for (Long id : ids) {
			Device device = byId.remove(id);
			if (device != null) {
				ordered.add(device);
			}
		}
		return ordered;
	}

	/**
	 * Gets the IDs of the target devices.
	 *
	 * @return the device IDs
	 */
	List<Long> deviceIds() {
		return this.devices.stream().map(Device::id).toList();
	}
}
