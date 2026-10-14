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
package net.netshot.netshot.boot;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import net.netshot.netshot.compliance.HardwareRuleRepository;
import net.netshot.netshot.compliance.HardwareRuleService;
import net.netshot.netshot.compliance.PolicyRepository;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleRepository;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.compliance.SoftwareRuleRepository;
import net.netshot.netshot.compliance.SoftwareRuleService;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.task.TaskService;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.task.engine.TaskManagerEngine;

/**
 * The composition root: builds the long-lived objects of the application
 * (transaction support, the repositories and the services) and holds them.
 *
 * <p>{@link #init()} initializes the database access and builds the wiring;
 * it must be called once the database schema is up to date, and before
 * anything that uses a service (the REST application, the task manager...).
 * It is the single entry point for the server startup as well as for the
 * tests which need a working database.</p>
 *
 * <p>The REST resources receive the objects of the wiring through dependency
 * injection: {@link #binder()} is handed to the web application, which registers
 * it into Jersey. Code which is
 * not created by an injection container (Quartz jobs, cluster code, entities)
 * can only reach them through {@link #get()}.</p>
 */
public final class Wiring {

	private static volatile Wiring instance;

	private final Transactions transactions;
	// Device
	private final DeviceService deviceService;
	// Compliance
	private final PolicyService policyService;
	private final RuleService ruleService;
	private final SoftwareRuleService softwareRuleService;
	private final HardwareRuleService hardwareRuleService;
	// Tasks
	private final TaskService taskService;

	/**
	 * Builds the objects of the application. Each feature adds its own block
	 * (repositories, then services).
	 */
	private Wiring() {
		this.transactions = new Transactions();
		// Device
		DeviceRepository deviceRepository = new DeviceRepository();
		this.deviceService = new DeviceService(this.transactions, deviceRepository, TaskManager::addTask);
		// Compliance
		PolicyRepository policyRepository = new PolicyRepository();
		this.policyService = new PolicyService(this.transactions, policyRepository);
		this.ruleService = new RuleService(this.transactions, new RuleRepository(), policyRepository);
		this.softwareRuleService = new SoftwareRuleService(this.transactions, new SoftwareRuleRepository());
		this.hardwareRuleService = new HardwareRuleService(this.transactions, new HardwareRuleRepository());
		// Tasks
		TaskRepository taskRepository = new TaskRepository();
		this.taskService = new TaskService(this.transactions, taskRepository, TaskManager::addTask, new TaskManagerEngine());
	}

	/**
	 * Initializes the database access and builds the wiring.
	 *
	 * @return the new wiring
	 */
	public static synchronized Wiring init() {
		Database.init(EntityModel.build());
		instance = new Wiring();
		return instance;
	}

	/**
	 * Gets the wiring, for the code which cannot receive its dependencies by injection.
	 *
	 * <p><b>Transitional:</b> a static lookup is a service locator, which hides the dependencies
	 * of its callers. It only exists for the code not yet created by an injection
	 * container (Quartz jobs, cluster code), and is meant to disappear as such code gets
	 * its dependencies explicitly. Do not use it from new code which can be injected.</p>
	 *
	 * @return the wiring
	 * @throws IllegalStateException if {@link #init()} has not been called yet
	 */
	public static Wiring get() {
		Wiring wiring = instance;
		if (wiring == null) {
			throw new IllegalStateException("The wiring has not been initialized");
		}
		return wiring;
	}

	/**
	 * Gets the dependency injection binder exposing the objects of this wiring
	 * to the REST resources. To be handed to the web application.
	 *
	 * @return a new binder
	 */
	public AbstractBinder binder() {
		return new WiringBinder(this);
	}

	/**
	 * Gets the transaction support.
	 *
	 * @return the transactions
	 */
	public Transactions transactions() {
		return this.transactions;
	}

	/**
	 * Gets the device use cases.
	 *
	 * @return the device service
	 */
	public DeviceService deviceService() {
		return this.deviceService;
	}

	/**
	 * Gets the compliance policy use cases.
	 *
	 * @return the policy service
	 */
	public PolicyService policyService() {
		return this.policyService;
	}

	/**
	 * Gets the compliance rule use cases.
	 *
	 * @return the rule service
	 */
	public RuleService ruleService() {
		return this.ruleService;
	}

	/**
	 * Gets the software compliance rule use cases.
	 *
	 * @return the software rule service
	 */
	public SoftwareRuleService softwareRuleService() {
		return this.softwareRuleService;
	}

	/**
	 * Gets the hardware compliance rule use cases.
	 *
	 * @return the hardware rule service
	 */
	public HardwareRuleService hardwareRuleService() {
		return this.hardwareRuleService;
	}

	/**
	 * Gets the task use cases.
	 *
	 * @return the task service
	 */
	public TaskService taskService() {
		return this.taskService;
	}
}
