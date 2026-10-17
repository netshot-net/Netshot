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
package net.netshot.netshot.compliance.check;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.Session;

import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.task.engine.TaskContext;

/**
 * Checks devices against the rules of the policies.
 */
public class ComplianceChecker {

	private final RuleEvaluators evaluators;

	/**
	 * Builds the checker.
	 *
	 * @param evaluators the rule evaluators
	 */
	public ComplianceChecker(RuleEvaluators evaluators) {
		this.evaluators = evaluators;
	}

	/**
	 * Check all devices of the target groups against the policy.
	 *
	 * @param policy the policy
	 * @param session the session
	 * @param taskContext the task context
	 */
	public void check(Policy policy, Session session, TaskContext taskContext) {
		if (policy.getTargetGroups() == null) {
			return;
		}
		Set<Device> devices = new HashSet<>();
		for (DeviceGroup group : policy.getTargetGroups()) {
			devices.addAll(group.getCachedDevices());
		}
		for (Device device : devices) {
			this.check(policy, device, session, taskContext);
		}
	}

	/**
	 * Check a given device against the policy.
	 *
	 * @param policy the policy
	 * @param device the device
	 * @param session the session
	 * @param taskContext the task context
	 */
	public void check(Policy policy, Device device, Session session, TaskContext taskContext) {
		for (Rule rule : policy.getRules()) {
			taskContext.info("Evaluating rule {} (policy {})...", rule.getName(), policy.getName());
			device.getComplianceCheckResults().add(this.evaluators.evaluate(rule, device, session, taskContext));
		}
	}

}
