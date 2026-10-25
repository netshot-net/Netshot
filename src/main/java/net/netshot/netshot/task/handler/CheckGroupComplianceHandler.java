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
package net.netshot.netshot.task.handler;

import static net.netshot.netshot.platform.database.schema.Tables.CHECK_RESULT;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP_CACHED_DEVICES;
import static net.netshot.netshot.platform.database.schema.Tables.POLICY_TARGET_GROUPS;

import java.util.List;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import lombok.extern.slf4j.Slf4j;
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
 * Runs a group compliance check task.
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

		List<Long> deviceIds = devices;

		try {
			this.transactions.run(dsl -> {
				List<Policy> policies = this.policyRepository.findAll(dsl, Page.ALL);
				// The devices of the group (or the list)
				Condition targetDevices = groupId != null
					? DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(groupId)
					: DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.in(deviceIds);
				if (groupId != null) {
					dsl.deleteFrom(CHECK_RESULT)
						.where(CHECK_RESULT.DEVICE.in(DSL.select(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES).from(DEVICE_GROUP_CACHED_DEVICES)
							.where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(groupId))))
						.execute();
				}
				else {
					dsl.deleteFrom(CHECK_RESULT).where(CHECK_RESULT.DEVICE.in(deviceIds)).execute();
				}
				for (Policy policy : policies) {
					// Get devices which are part of the target group/list and which are in a group which the policy is applied to
					var dm = DEVICE_GROUP_CACHED_DEVICES.as("dm");
					var inPolicy = DSL.select(dm.CACHED_DEVICES).from(POLICY_TARGET_GROUPS)
						.join(dm).on(dm.OWNER_GROUPS.eq(POLICY_TARGET_GROUPS.TARGET_GROUPS))
						.where(POLICY_TARGET_GROUPS.POLICY.eq(policy.id()));
					List<Long> policyDeviceIds = groupId != null
						? dsl.select(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES).from(DEVICE_GROUP_CACHED_DEVICES)
							.where(targetDevices.and(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.in(inPolicy)))
							.fetch(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES)
						: dsl.select(DEVICE.ID).from(DEVICE).where(DEVICE.ID.in(deviceIds).and(DEVICE.ID.in(inPolicy))).fetch(DEVICE.ID);
					List<Rule> rules = this.ruleRepository.findByPolicy(dsl, policy.id(), Page.ALL);
					for (Long deviceId : policyDeviceIds) {
						Device device = this.deviceRepository.find(dsl, deviceId);
						run.logger.info("Checking configuration compliance of device {} ({})", device.name(), device.id());
						DeviceDraft draft = new DeviceDraft(device);
						draft.setLastConfig(device.lastConfigId() == null ? null : new ConfigRepository().find(dsl, device.lastConfigId(), true));
						this.ruleRepository.saveCheckResults(dsl, this.complianceChecker.check(policy, rules, draft,
							this.ruleRepository.findExemptionsOfDevice(dsl, deviceId), run.logger));
						// The rules may change the tags or comments of the device
						this.deviceRepository.save(dsl, device, draft.toDevice());
					}
				}
			});
			run.setStatus(Status.SUCCESS);
		}
		catch (Exception e) {
			log.error("Task {}. Error while checking compliance.", task.id(), e);
			run.logger.error("Error while checking compliance: {}", e.getMessage());
			run.setStatus(Status.FAILURE);
			return;
		}

		log.debug("Task {}. Request to refresh all the groups after compliance check.", task.id());
		this.dynamicGroups.refreshAllGroups();
		log.debug("Task {}. Group refreshing done.", task.id());
	}

}
