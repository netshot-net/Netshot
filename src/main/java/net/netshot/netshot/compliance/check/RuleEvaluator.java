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

import org.hibernate.Session;

import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.task.engine.TaskContext;

/**
 * Evaluates the rules of one type against a device. The rule entities only hold
 * the definition of the rule; the way it is checked lives in the evaluator of its type.
 *
 * @param <R> the type of rule evaluated
 */
public interface RuleEvaluator<R extends Rule> {

	/**
	 * Gets the type of rule this evaluator handles.
	 *
	 * @return the rule type
	 */
	Class<R> ruleType();

	/**
	 * Checks the device against the rule.
	 *
	 * @param rule the rule
	 * @param device the device
	 * @param session the session
	 * @param taskContext the task context
	 * @return the result of the check
	 */
	CheckResult evaluate(R rule, Device device, Session session, TaskContext taskContext);

}
