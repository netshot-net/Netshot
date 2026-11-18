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
package net.netshot.netshot.boot;

import java.util.List;
import java.util.Optional;

import net.netshot.netshot.access.Connectors;
import net.netshot.netshot.access.collector.SnmpTrapReceiver;
import net.netshot.netshot.access.collector.SyslogServer;
import net.netshot.netshot.cluster.ClusterLink;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.compliance.check.ComplianceChecker;
import net.netshot.netshot.diagnostic.run.DiagnosticAdapters;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.AutoSnapshotQueue;
import net.netshot.netshot.task.AutoSnapshotScheduler;
import net.netshot.netshot.task.DeviceCreationService;
import net.netshot.netshot.task.TaskHandlers;
import net.netshot.netshot.task.TaskService;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.task.engine.ChildTaskOrchestrator;
import net.netshot.netshot.task.engine.TaskJobFactory;
import net.netshot.netshot.task.engine.TaskLockManager;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.task.handler.CheckComplianceHandler;
import net.netshot.netshot.task.handler.CheckGroupComplianceHandler;
import net.netshot.netshot.task.handler.CheckGroupSoftwareHandler;
import net.netshot.netshot.task.handler.DiscoverDeviceTypeHandler;
import net.netshot.netshot.task.handler.PurgeDatabaseHandler;
import net.netshot.netshot.task.handler.RunDeviceGroupScriptHandler;
import net.netshot.netshot.task.handler.RunDeviceScriptHandler;
import net.netshot.netshot.task.handler.RunDiagnosticsHandler;
import net.netshot.netshot.task.handler.RunGroupDiagnosticsHandler;
import net.netshot.netshot.task.handler.ScanSubnetsHandler;
import net.netshot.netshot.task.handler.TakeGroupSnapshotHandler;
import net.netshot.netshot.task.handler.TakeSnapshotHandler;

/**
 * The tasks: the task engine (locks, task manager, automatic snapshots, the cluster manager when clustering is
 * enabled), the task handlers, the use cases which submit tasks, and the receivers of the device events (Syslog,
 * SNMP traps), which schedule snapshots.
 *
 * @param lockManager the task lock manager (a service)
 * @param taskManager the task manager (a service)
 * @param autoSnapshotScheduler the scheduler of the automatic snapshots
 * @param clusterManager the cluster manager (a service), absent without clustering
 * @param cluster the link to the cluster ({@link ClusterLink#NONE} without clustering)
 * @param diagnosticAdapters the adapters of the diagnostics
 * @param taskHandlers the task handlers
 * @param taskJobFactory the factory of the task jobs
 * @param taskService the task use cases
 * @param deviceCreationService the device creation use cases (which submit discovery tasks)
 * @param syslogServer the Syslog server (a service)
 * @param snmpTrapReceiver the SNMP trap receiver (a service)
 */
public record TaskModule(
	TaskLockManager lockManager,
	TaskManager taskManager,
	AutoSnapshotScheduler autoSnapshotScheduler,
	Optional<ClusterManager> clusterManager,
	ClusterLink cluster,
	DiagnosticAdapters diagnosticAdapters,
	TaskHandlers taskHandlers,
	TaskJobFactory taskJobFactory,
	TaskService taskService,
	DeviceCreationService deviceCreationService,
	SyslogServer syslogServer,
	SnmpTrapReceiver snmpTrapReceiver) {

	/**
	 * Builds the module. The task manager and the cluster manager, or the task handlers, use each other: the task
	 * manager receives them once built (the only two-step initialization of the wiring).
	 *
	 * @param core the core module
	 * @param access the access module
	 * @param compliance the compliance module
	 * @return the module
	 */
	public static TaskModule create(CoreModule core, AccessModule access, ComplianceModule compliance) {
		ConfigStore config = core.config();
		Transactions transactions = core.transactions();
		Repositories r = core.repositories();
		DriverRegistry drivers = core.driverRegistry();
		boolean clustered = config.current().get(ClusterManager.ENABLED);
		TaskLockManager lockManager = TaskLockManager.create(clustered, core.database());
		AutoSnapshotQueue autoSnapshots = new AutoSnapshotQueue();
		TaskManager taskManager = new TaskManager(transactions, config, core.database(), autoSnapshots, lockManager, r.task());
		TaskSubmitter submitter = taskManager::addTask;
		AutoSnapshotScheduler autoSnapshotScheduler = new AutoSnapshotScheduler(submitter, config, taskManager, autoSnapshots, r.device());
		Optional<ClusterManager> clusterManager = clustered
			? Optional.of(new ClusterManager(drivers, autoSnapshotScheduler, taskManager, core.vaultManager(), core.database(), config))
			: Optional.empty();
		clusterManager.ifPresent(taskManager::setCluster);
		ClusterLink cluster = clusterManager.<ClusterLink>map(m -> m).orElse(ClusterLink.NONE);
		DiagnosticAdapters diagnosticAdapters = DiagnosticAdapters.standard();
		ChildTaskOrchestrator orchestrator = new ChildTaskOrchestrator(transactions, taskManager::addTask, r.task(), config);
		Connectors connectors = access.connectors();
		ComplianceChecker checker = compliance.complianceChecker();
		DynamicGroups dynamicGroups = core.dynamicGroups();
		TaskHandlers taskHandlers = new TaskHandlers(List.of(
			new CheckComplianceHandler(transactions, checker, dynamicGroups, r.policy(), r.rule(),
				r.softwareRule(), r.hardwareRule(), r.device(), r.config(), r.group()),
			new CheckGroupComplianceHandler(transactions, checker, dynamicGroups, r.policy(), r.rule(),
				r.softwareRule(), r.hardwareRule(), r.device(), r.config(), r.group()),
			new CheckGroupSoftwareHandler(transactions, r.softwareRule(), r.hardwareRule(), r.device(), r.group()),
			new DiscoverDeviceTypeHandler(transactions, drivers, submitter, dynamicGroups, connectors,
				r.task(), r.credentialSet(), r.domain(), r.device()),
			new PurgeDatabaseHandler(transactions, r.task(), r.binaryFiles()),
			new RunDeviceGroupScriptHandler(transactions, orchestrator, drivers, r.device(), r.group()),
			new RunDeviceScriptHandler(transactions, drivers, submitter, dynamicGroups, connectors, r.device()),
			new RunDiagnosticsHandler(transactions, drivers, diagnosticAdapters, submitter, dynamicGroups, connectors,
				r.device(), r.diagnostic()),
			new RunGroupDiagnosticsHandler(transactions, orchestrator, r.device(), r.group()),
			new ScanSubnetsHandler(transactions, orchestrator, r.credentialSet()),
			new TakeGroupSnapshotHandler(transactions, orchestrator, r.device(), r.group()),
			new TakeSnapshotHandler(transactions, drivers, submitter, autoSnapshots, access.sshServer().orElse(null), dynamicGroups,
				connectors, r.device(), config, r.binaryFiles())));
		TaskJobFactory taskJobFactory = new TaskJobFactory(transactions, taskHandlers, core.domainEvents(), taskManager, lockManager, r.task(), config);
		taskManager.setJobFactory(taskJobFactory);
		TaskService taskService = new TaskService(transactions, r.task(), submitter, taskManager, drivers, r.device(), r.domain(), r.group());
		DeviceCreationService deviceCreationService = new DeviceCreationService(transactions, r.device(), submitter,
			drivers, dynamicGroups, r.domain(), r.credentialSet(), r.task(), r.credentialSetAssembler());
		SyslogServer syslogServer = new SyslogServer(drivers, autoSnapshotScheduler::scheduleSnapshotIfNeeded, config);
		SnmpTrapReceiver snmpTrapReceiver = new SnmpTrapReceiver(drivers, autoSnapshotScheduler::scheduleSnapshotIfNeeded, config);
		return new TaskModule(lockManager, taskManager, autoSnapshotScheduler, clusterManager, cluster, diagnosticAdapters, taskHandlers,
			taskJobFactory, taskService, deviceCreationService, syslogServer, snmpTrapReceiver);
	}
}
