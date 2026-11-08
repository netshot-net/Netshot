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
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.HardwareRuleRepository;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRuleRepository;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;

/**
 * Runs a group software compliance and hardware support check task.
 */
@Slf4j
public class CheckGroupSoftwareHandler implements TaskHandler<CheckGroupSoftwareTask> {


	private final SoftwareRuleRepository softwareRuleRepository = new SoftwareRuleRepository();

	private final HardwareRuleRepository hardwareRuleRepository = new HardwareRuleRepository();

	private final DeviceRepository deviceRepository = new DeviceRepository();

	/** The transaction support. */
	private final Transactions transactions;

	/**
	 * Instantiates the handler.
	 *
	 * @param transactions the transaction support
	 */
	public CheckGroupSoftwareHandler(Transactions transactions) {
		this.transactions = transactions;
	}

	@Override
	public Class<CheckGroupSoftwareTask> taskType() {
		return CheckGroupSoftwareTask.class;
	}

	@Override
	public void run(Task task, CheckGroupSoftwareTask spec, TaskRun run) {
		Long groupId = spec.deviceGroupId();
		List<Long> deviceList = groupId == null ? spec.deviceIds() : null;
		if (groupId == null && (deviceList == null || deviceList.isEmpty())) {
			run.logger.info("Neither a device group nor a device list is set, the task will be cancelled.");
			run.setStatus(Status.CANCELLED);
			return;
		}
		log.debug("Task {}. Starting check software compliance and hardware support status task for {}.",
			task.id(), groupId == null ? deviceList.size() + " listed device(s)" : "group " + groupId);

		List<Long> deviceIds;
		List<SoftwareRule> softwareRules;
		List<HardwareRule> hardwareRules;
		try {
			softwareRules = this.transactions.read(dsl -> this.softwareRuleRepository.findAll(dsl, Page.ALL));
			hardwareRules = this.transactions.read(dsl -> this.hardwareRuleRepository.findAll(dsl));
			deviceIds = groupId != null ? this.transactions.read(dsl -> new GroupRepository().findMemberIds(dsl, groupId)) : deviceList;
		}
		catch (Exception e) {
			log.error("Task {}. Error while loading the devices and rules to check.", task.id(), e);
			run.logger.error("Error while loading the devices and rules to check: {}", e.getMessage());
			run.setStatus(Status.FAILURE);
			return;
		}

		// One transaction per device: its row is locked for its own check only, and an error only concerns it
		List<Long> failedDeviceIds = new ArrayList<>();
		for (Long deviceId : deviceIds) {
			try {
				this.transactions.run(dsl -> {
					Device device = this.deviceRepository.find(dsl, deviceId);
					if (device == null) {
						return;
					}
					Set<Long> groupIds = GroupRepository.findGroupIdsOfDevice(dsl, device.id());
					this.deviceRepository.save(dsl, device,
						CheckComplianceHandler.checkSoftwareAndHardware(device, groupIds, softwareRules, hardwareRules));
				});
			}
			catch (Exception e) {
				log.error("Task {}. Error while checking the software and hardware of device {}.", task.id(), deviceId, e);
				run.logger.error("Error while checking the software and hardware of device {}: {}", deviceId, e.getMessage());
				failedDeviceIds.add(deviceId);
			}
		}
		if (failedDeviceIds.isEmpty()) {
			run.setStatus(Status.SUCCESS);
		}
		else {
			run.logger.error("The check failed for {} of {} device(s): {}", failedDeviceIds.size(), deviceIds.size(), failedDeviceIds);
			run.setStatus(Status.FAILURE);
		}
	}

}
