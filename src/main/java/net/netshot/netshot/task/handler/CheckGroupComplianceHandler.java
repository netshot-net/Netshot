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

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP_CACHED_DEVICES;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.Exemption;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PolicyRepository;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.RuleRepository;
import net.netshot.netshot.compliance.check.ComplianceChecker;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;

/**
 * Runs a group compliance check task: the devices of the group (or list) are checked one after the other, each in its
 * own transaction.
 */
@Slf4j
public class CheckGroupComplianceHandler implements TaskHandler<CheckGroupComplianceTask> {

	private final ComplianceChecker complianceChecker;

	private final PolicyRepository policyRepository = new PolicyRepository();

	private final RuleRepository ruleRepository = new RuleRepository();

	private final DeviceRepository deviceRepository = new DeviceRepository();

	/** The transaction support. */
	private final Transactions transactions;

	/** The refresher of the dynamic groups. */
	private final DynamicGroups dynamicGroups;

	/**
	 * Creates the handler.
	 * @param transactions the transaction support
	 * @param complianceChecker the compliance checker
	 * @param dynamicGroups the refresher of the dynamic groups
	 */
	public CheckGroupComplianceHandler(Transactions transactions, ComplianceChecker complianceChecker, DynamicGroups dynamicGroups) {
		this.transactions = transactions;
		this.dynamicGroups = dynamicGroups;
		this.complianceChecker = complianceChecker;
	}

	@Override
	public Class<CheckGroupComplianceTask> taskType() {
		return CheckGroupComplianceTask.class;
	}

	@Override
	public void run(Task task, CheckGroupComplianceTask spec, TaskRun run) {
		Long groupId = spec.deviceGroupId();
		List<Long> devices = groupId == null ? spec.deviceIds() : null;
		if (groupId == null && (devices == null || devices.isEmpty())) {
			run.logger.info("Neither a device group nor a device list is set, the task will be cancelled.");
			run.setStatus(Status.CANCELLED);
			return;
		}
		log.debug("Task {}. Starting check compliance task for {}.", task.id(),
			groupId == null ? devices.size() + " listed device(s)" : "group " + groupId);

		// The devices of the group (or the existing devices of the list), and the rules of each policy
		List<Long> deviceIds;
		Map<Long, List<Rule>> rulesByPolicy = new HashMap<>();
		try {
			deviceIds = this.transactions.read(dsl -> {
				for (Policy policy : this.policyRepository.findAll(dsl, Page.ALL)) {
					rulesByPolicy.put(policy.id(), this.ruleRepository.findByPolicy(dsl, policy.id(), Page.ALL));
				}
				return groupId != null
					? dsl.select(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES).from(DEVICE_GROUP_CACHED_DEVICES)
						.where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(groupId))
						.orderBy(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES)
						.fetch(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES)
					: dsl.select(DEVICE.ID).from(DEVICE).where(DEVICE.ID.in(devices)).orderBy(DEVICE.ID).fetch(DEVICE.ID);
			});
		}
		catch (Exception e) {
			log.error("Task {}. Error while loading the devices and rules to check.", task.id(), e);
			run.logger.error("Error while loading the devices and rules to check: {}", e.getMessage());
			run.setStatus(Status.FAILURE);
			return;
		}

		// One transaction per device: its rows are locked for its own check only, and an error only concerns it
		List<Long> failedDeviceIds = new ArrayList<>();
		for (Long deviceId : deviceIds) {
			try {
				this.transactions.run(dsl -> this.checkDevice(dsl, deviceId, rulesByPolicy, run));
			}
			catch (Exception e) {
				log.error("Task {}. Error while checking compliance of device {}.", task.id(), deviceId, e);
				run.logger.error("Error while checking compliance of device {}: {}", deviceId, e.getMessage());
				failedDeviceIds.add(deviceId);
			}
		}

		log.debug("Task {}. Request to refresh all the groups after compliance check.", task.id());
		this.dynamicGroups.refreshAllGroups();
		log.debug("Task {}. Group refreshing done.", task.id());

		if (failedDeviceIds.isEmpty()) {
			run.setStatus(Status.SUCCESS);
		}
		else {
			run.logger.error("The compliance check failed for {} of {} device(s): {}", failedDeviceIds.size(), deviceIds.size(), failedDeviceIds);
			run.setStatus(Status.FAILURE);
		}
	}

	/**
	 * Checks one device against the policies applied to it, replacing its results.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @param rulesByPolicy the rules of each policy
	 * @param run the run of the task
	 */
	private void checkDevice(DSLContext dsl, long deviceId, Map<Long, List<Rule>> rulesByPolicy, TaskRun run) {
		this.ruleRepository.deleteCheckResultsOfDevice(dsl, deviceId);
		List<Policy> policies = this.policyRepository.findOfDevice(dsl, deviceId);
		if (policies.isEmpty()) {
			return;
		}
		Device device = this.deviceRepository.find(dsl, deviceId);
		if (device == null) {
			// Removed since the start of the task
			return;
		}
		run.logger.info("Checking configuration compliance of device {} ({})", device.name(), device.id());
		DeviceDraft draft = new DeviceDraft(device);
		draft.setLastConfig(device.lastConfigId() == null ? null : new ConfigRepository().find(dsl, device.lastConfigId(), true));
		List<Exemption> exemptions = this.ruleRepository.findExemptionsOfDevice(dsl, deviceId);
		for (Policy policy : policies) {
			List<Rule> rules = rulesByPolicy.get(policy.id());
			if (rules != null) {
				this.ruleRepository.saveCheckResults(dsl, this.complianceChecker.check(policy, rules, draft, exemptions, run.logger));
			}
		}
		// The rules may change the tags or comments of the device
		this.deviceRepository.save(dsl, device, draft.toDevice());
	}

}
