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

import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.Exemption;
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.HardwareRuleRepository;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.PolicyRepository;
import net.netshot.netshot.compliance.RuleRepository;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.compliance.SoftwareRuleRepository;
import net.netshot.netshot.compliance.check.ComplianceChecker;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.type.CheckComplianceTask;

/**
 * Runs a device compliance check task.
 */
@Slf4j
public class CheckComplianceHandler implements TaskHandler<CheckComplianceTask> {

	private final ComplianceChecker complianceChecker;

	private final PolicyRepository policyRepository = new PolicyRepository();

	private final RuleRepository ruleRepository = new RuleRepository();

	private final SoftwareRuleRepository softwareRuleRepository = new SoftwareRuleRepository();

	private final HardwareRuleRepository hardwareRuleRepository = new HardwareRuleRepository();

	private final DeviceRepository deviceRepository = new DeviceRepository();

	private final Transactions transactions = new Transactions();

	/** The refresher of the dynamic groups. */
	private final DynamicGroups dynamicGroups;

	/**
	 * Creates the handler.
	 * @param complianceChecker the compliance checker
	 * @param dynamicGroups the refresher of the dynamic groups
	 */
	public CheckComplianceHandler(ComplianceChecker complianceChecker, DynamicGroups dynamicGroups) {
		this.dynamicGroups = dynamicGroups;
		this.complianceChecker = complianceChecker;
	}

	@Override
	public Class<CheckComplianceTask> taskType() {
		return CheckComplianceTask.class;
	}

	@Override
	public void run(Task task, CheckComplianceTask spec, TaskRun run) {
		Long deviceId = spec.deviceId();
		log.debug("Task {}. Starting check compliance task for device {}.", task.id(),
			deviceId);
		if (deviceId == null) {
			run.logger.info("The device doesn't exist, the task will be cancelled.");
			run.setStatus(Status.CANCELLED);
			return;
		}

		try {
			this.transactions.run(dsl -> {
				// Delete previous results
				this.ruleRepository.deleteCheckResultsOfDevice(dsl, deviceId);
				// Start over from a fresh device from DB
				Device device = this.deviceRepository.find(dsl, deviceId);
				if (device == null) {
					throw new IllegalStateException("The device %d doesn't exist anymore".formatted(deviceId));
				}
				run.logger.info("Check compliance task for device {} ({}).",
					device.name(), device.mgmtAddress());
				if (device.lastConfigId() == null) {
					log.info("Task {}. Unable to fetch the device with its last config... has it been captured at least once?",
						task.id());
					throw new IllegalStateException("No last config for this device. Has it been captured at least once?");
				}
				List<Policy> policies = this.policyRepository.findOfDevice(dsl, device.id());
				List<Exemption> exemptions = this.ruleRepository.findExemptionsOfDevice(dsl, device.id());

				run.logger.info("Checking configuration compliance of device {} ({})...",
					device.name(), device.id());
				DeviceDraft draft = new DeviceDraft(device);
				draft.setLastConfig(device.lastConfigId() == null ? null : new ConfigRepository().find(dsl, device.lastConfigId(), true));
				for (Policy policy : policies) {
					this.ruleRepository.saveCheckResults(dsl, this.complianceChecker.check(policy,
						this.ruleRepository.findByPolicy(dsl, policy.id(), Page.ALL), draft, exemptions, run.logger));
				}
				Set<Long> groupIds = GroupRepository.findGroupIdsOfDevice(dsl, device.id());
				List<SoftwareRule> softwareRules = this.softwareRuleRepository.findAll(dsl, Page.ALL).stream()
					.filter(rule -> rule.targetGroupId() == null || groupIds.contains(rule.targetGroupId()))
					.toList();
				List<HardwareRule> hardwareRules = this.hardwareRuleRepository.findAll(dsl);
				Device edited = checkSoftwareAndHardware(draft.toDevice(), groupIds, softwareRules, hardwareRules);
				this.deviceRepository.save(dsl, device, edited);
			});
			run.setStatus(Status.SUCCESS);
		}
		catch (Exception e) {
			log.error("Task {}. Error while checking compliance.", task.id(), e);
			run.logger.error("Error while checking compliance: {}", e.getMessage());
			run.setStatus(Status.FAILURE);
			return;
		}

		log.debug("Task {}. Request to refresh all the groups for the device after compliance check.", task.id());
		this.dynamicGroups.refreshAllGroupsOfOneDevice(deviceId);
	}

	/**
	 * Checks the software compliance (the first matching software rule gives the level) and the
	 * hardware support status (the earliest dates of the matching hardware rules) of a device.
	 *
	 * @param device the device
	 * @param groupIds the IDs of the groups of the device
	 * @param softwareRules the software rules, by priority
	 * @param hardwareRules the hardware rules
	 * @return the device, with its software level and its end of sale and end of life dates
	 */
	static Device checkSoftwareAndHardware(Device device, Set<Long> groupIds, List<SoftwareRule> softwareRules,
			List<HardwareRule> hardwareRules) {
		ConformanceLevel level = ConformanceLevel.UNKNOWN;
		for (SoftwareRule rule : softwareRules) {
			level = rule.check(device, groupIds);
			if (level != ConformanceLevel.UNKNOWN) {
				break;
			}
		}
		Device checked = device.toBuilder()
			.softwareLevel(level)
			.eolDate(null)
			.eolModule(null)
			.eosDate(null)
			.eosModule(null)
			.build();
		for (HardwareRule rule : hardwareRules) {
			checked = rule.check(checked, groupIds);
		}
		return checked;
	}

}
