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

import java.util.ArrayList;
import java.util.List;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.aaa.auth.ConfiguredRemoteAuthenticator;
import net.netshot.netshot.aaa.auth.LoginService;
import net.netshot.netshot.aaa.auth.OidcDiscovery;
import net.netshot.netshot.aaa.user.ApiTokenRepository;
import net.netshot.netshot.aaa.user.ApiTokenService;
import net.netshot.netshot.aaa.user.UserRepository;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.access.collector.SnmpTrapReceiver;
import net.netshot.netshot.access.collector.SshServer;
import net.netshot.netshot.access.collector.SyslogServer;
import net.netshot.netshot.api.hook.HookDispatcher;
import net.netshot.netshot.api.rest.RestServer;
import net.netshot.netshot.api.hook.WebHookSender;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.cluster.ClusterService;
import net.netshot.netshot.compliance.HardwareRuleRepository;
import net.netshot.netshot.compliance.HardwareRuleService;
import net.netshot.netshot.compliance.PolicyRepository;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleRepository;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.compliance.SoftwareRuleRepository;
import net.netshot.netshot.compliance.SoftwareRuleService;
import net.netshot.netshot.compliance.check.ComplianceChecker;
import net.netshot.netshot.compliance.check.JsRuleRuntime;
import net.netshot.netshot.compliance.check.PyRuleRuntime;
import net.netshot.netshot.compliance.check.RuleEvaluators;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.CredentialSetService;
import net.netshot.netshot.diagnostic.DiagnosticRepository;
import net.netshot.netshot.diagnostic.DiagnosticService;
import net.netshot.netshot.diagnostic.run.DiagnosticAdapters;
import net.netshot.netshot.driver.DeviceDriverService;
import net.netshot.netshot.driver.DriverLoader;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.hook.HookRepository;
import net.netshot.netshot.hook.HookService;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.config.ConfigService;
import net.netshot.netshot.inventory.device.DeviceCatalogService;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.inventory.domain.DomainRepository;
import net.netshot.netshot.inventory.domain.DomainService;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.Finder;
import net.netshot.netshot.inventory.search.SearchRepository;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.event.DomainEvents;
import net.netshot.netshot.platform.lifecycle.Service;
import net.netshot.netshot.report.ReportRepository;
import net.netshot.netshot.report.ReportService;
import net.netshot.netshot.system.ServerInfoService;
import net.netshot.netshot.task.AutoSnapshotScheduler;
import net.netshot.netshot.task.TaskHandlers;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.task.TaskSubmitter;
import net.netshot.netshot.task.TaskService;
import net.netshot.netshot.task.engine.ChildTaskOrchestrator;
import net.netshot.netshot.task.engine.TaskJobFactory;
import net.netshot.netshot.task.engine.TaskLockManager;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.task.engine.TaskManagerEngine;
import net.netshot.netshot.task.engine.TaskManagerService;
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
import net.netshot.netshot.userscript.ScriptRepository;
import net.netshot.netshot.userscript.ScriptService;
import net.netshot.netshot.vault.VaultInstanceRepository;
import net.netshot.netshot.vault.VaultInstanceService;
import net.netshot.netshot.vault.VaultManager;
import net.netshot.netshot.vault.VaultTokenRefresher;

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
 * gets what it needs from the entry point ({@code Netshot.main}) or from the handlers.</p>
 */
@Slf4j
public final class Wiring {

	private static volatile Wiring instance;

	private final Transactions transactions;
	// Configuration
	private final ConfigStore config;
	// Drivers
	private final DriverRegistry driverRegistry;
	// Device
	private final DeviceService deviceService;
	// Compliance
	private final PolicyService policyService;
	private final RuleService ruleService;
	private final SoftwareRuleService softwareRuleService;
	private final HardwareRuleService hardwareRuleService;
	private final JsRuleRuntime jsRuleRuntime;
	private final PyRuleRuntime pyRuleRuntime;
	private final RuleEvaluators ruleEvaluators;
	private final ComplianceChecker complianceChecker;
	// Tasks
	private final TaskService taskService;
	// Reports
	private final ReportService reportService;
	// Inventory
	private final DeviceCatalogService deviceCatalogService;
	private final DeviceDriverService deviceDriverService;
	private final DomainService domainService;
	private final GroupService groupService;
	private final SearchService searchService;
	private final ConfigService configService;
	// AAA and system
	private final UserService userService;
	private final ApiTokenService apiTokenService;
	private final LoginService loginService;
	private final ClusterService clusterService;
	private final ServerInfoService serverInfoService;
	// Diagnostics
	private final DiagnosticService diagnosticService;
	private final DiagnosticAdapters diagnosticAdapters;
	// Scripts
	private final ScriptService scriptService;
	// Credentials
	private final CredentialSetService credentialSetService;
	// Vault
	private final VaultInstanceService vaultInstanceService;
	// Hooks
	private final HookService hookService;
	// Events
	private final DomainEvents domainEvents;
	// Task handlers
	private final AutoSnapshotScheduler autoSnapshotScheduler;
	private final TaskHandlers taskHandlers;
	private final TaskJobFactory taskJobFactory;

	/**
	 * Builds the objects of the application. Each feature adds its own block
	 * (repositories, then services).
	 *
	 * @param driverRegistry the registry of the device drivers
	 * @param config the configuration store
	 */
	private Wiring(DriverRegistry driverRegistry, ConfigStore config) {
		this.transactions = new Transactions();
		this.config = config;
		// Drivers
		this.driverRegistry = driverRegistry;
		Finder.setDriverRegistry(driverRegistry);
		// Device
		DeviceRepository deviceRepository = new DeviceRepository();
		this.deviceService = new DeviceService(this.transactions, deviceRepository, TaskManager::addTask, this.driverRegistry);
		// Compliance
		PolicyRepository policyRepository = new PolicyRepository();
		this.policyService = new PolicyService(this.transactions, policyRepository);
		this.jsRuleRuntime = new JsRuleRuntime(config);
		this.pyRuleRuntime = new PyRuleRuntime(config);
		this.ruleEvaluators = RuleEvaluators.standard(this.jsRuleRuntime, this.pyRuleRuntime, this.driverRegistry);
		this.complianceChecker = new ComplianceChecker(this.ruleEvaluators);
		this.ruleService = new RuleService(this.transactions, new RuleRepository(), policyRepository, this.ruleEvaluators);
		this.softwareRuleService = new SoftwareRuleService(this.transactions, new SoftwareRuleRepository(), this.driverRegistry);
		this.hardwareRuleService = new HardwareRuleService(this.transactions, new HardwareRuleRepository(), this.driverRegistry);
		// Tasks
		TaskRepository taskRepository = new TaskRepository();
		this.taskService = new TaskService(this.transactions, taskRepository, TaskManager::addTask, new TaskManagerEngine(),
			this.driverRegistry);
		// Reports
		this.reportService = new ReportService(this.transactions, new ReportRepository(), this.driverRegistry);
		// Inventory
		this.deviceCatalogService = new DeviceCatalogService(this.transactions, deviceRepository);
		this.deviceDriverService = new DeviceDriverService(this.driverRegistry);
		this.domainService = new DomainService(this.transactions, new DomainRepository());
		this.groupService = new GroupService(this.transactions, new GroupRepository());
		this.searchService = new SearchService(this.transactions, new SearchRepository());
		this.configService = new ConfigService(this.transactions, new ConfigRepository(), this.driverRegistry);
		// AAA and system
		UserRepository userRepository = new UserRepository();
		this.userService = new UserService(this.transactions, userRepository);
		this.apiTokenService = new ApiTokenService(this.transactions, new ApiTokenRepository());
		this.loginService = new LoginService(this.transactions, userRepository, new ConfiguredRemoteAuthenticator());
		this.clusterService = new ClusterService(ClusterManager::getClusterMembers);
		this.serverInfoService = new ServerInfoService();
		// Diagnostics
		this.diagnosticService = new DiagnosticService(this.transactions, new DiagnosticRepository());
		this.diagnosticAdapters = DiagnosticAdapters.standard();
		// Scripts
		this.scriptService = new ScriptService(this.transactions, new ScriptRepository(), this.driverRegistry);
		// Credentials
		this.credentialSetService = new CredentialSetService(this.transactions, new CredentialSetRepository());
		// Vault
		this.vaultInstanceService = new VaultInstanceService(this.transactions, new VaultInstanceRepository(),
			vaultInstanceId -> {
				VaultManager.invalidate(vaultInstanceId);
				ClusterManager.requestVaultInstanceReload(vaultInstanceId);
			},
			VaultManager::testConnection);
		// Hooks
		this.hookService = new HookService(this.transactions, new HookRepository());
		// Events
		this.domainEvents = new DomainEvents();
		new HookDispatcher(this.hookService, this.taskService, new WebHookSender(), this.driverRegistry).register(this.domainEvents);
		// Task handlers
		TaskSubmitter taskSubmitter = TaskManager::addTask;
		ChildTaskOrchestrator childTaskOrchestrator = new ChildTaskOrchestrator(TaskManager::addTask);
		this.autoSnapshotScheduler = new AutoSnapshotScheduler(taskSubmitter, config);
		this.taskHandlers = new TaskHandlers(List.of(
			new CheckComplianceHandler(this.complianceChecker),
			new CheckGroupComplianceHandler(this.complianceChecker),
			new CheckGroupSoftwareHandler(),
			new DiscoverDeviceTypeHandler(this.driverRegistry, taskSubmitter),
			new PurgeDatabaseHandler(),
			new RunDeviceGroupScriptHandler(childTaskOrchestrator, this.driverRegistry),
			new RunDeviceScriptHandler(this.driverRegistry, taskSubmitter),
			new RunDiagnosticsHandler(this.driverRegistry, this.diagnosticAdapters, taskSubmitter),
			new RunGroupDiagnosticsHandler(childTaskOrchestrator),
			new ScanSubnetsHandler(childTaskOrchestrator),
			new TakeGroupSnapshotHandler(childTaskOrchestrator),
			new TakeSnapshotHandler(this.driverRegistry, taskSubmitter)));
		this.taskJobFactory = new TaskJobFactory(this.taskHandlers, this.domainEvents);
	}

	/**
	 * Initializes the database access and builds the wiring.
	 *
	 * @return the new wiring
	 */
	public static synchronized Wiring init() {
		return init(new DriverRegistry(new DriverLoader()));
	}

	/**
	 * Initializes the database access and builds the wiring around an existing registry of drivers.
	 *
	 * @param driverRegistry the registry of the device drivers (loaded or not)
	 * @return the new wiring
	 */
	public static synchronized Wiring init(DriverRegistry driverRegistry) {
		Database.init();
		instance = new Wiring(driverRegistry, Netshot.config());
		return instance;
	}

	/**
	 * Builds the wiring, without side effect: the database access is initialized by the first of the
	 * {@link #services()}.
	 *
	 * @param config the configuration store
	 * @return the new wiring
	 */
	public static synchronized Wiring create(ConfigStore config) {
		instance = new Wiring(new DriverRegistry(new DriverLoader()), config);
		return instance;
	}

	/**
	 * Gets the services of the server, in start order (they're stopped in the reverse order).
	 * The services disabled by the configuration aren't part of the list.
	 *
	 * @return the services
	 */
	public List<Service> services() {
		Config current = this.config.current();
		boolean clustered = current.get(ClusterManager.ENABLED);
		List<Service> services = new ArrayList<>();
		services.add(Service.of("database", Database::init, Database::shutdown));
		services.add(Service.of("device drivers", this.driverRegistry::refresh, () -> { }));
		services.add(new OidcDiscovery(this.config));
		// Before the clustering manager, which can start running tasks
		services.add(Service.of("task lock manager", () -> TaskLockManager.init(clustered), TaskLockManager::shutdown));
		if (clustered) {
			services.add(new ClusterManager(this.driverRegistry, this.autoSnapshotScheduler));
		}
		else {
			log.info("High Availability is not enabled.");
		}
		services.add(new VaultTokenRefresher(this.config));
		services.add(new TaskManagerService(this.taskJobFactory));
		// The receivers submit tasks: after the task manager
		if (current.get(SyslogServer.DISABLED)) {
			log.warn("The Syslog server is disabled by configuration.");
		}
		else {
			services.add(new SyslogServer(this.driverRegistry, this.autoSnapshotScheduler, this.config));
		}
		services.add(new SnmpTrapReceiver(this.driverRegistry, this.autoSnapshotScheduler, this.config));
		if (current.get(SshServer.Settings.ENABLED)) {
			services.add(new SshServer(this.config));
		}
		else {
			log.warn("The SSH server is disabled by configuration.");
		}
		// Last: the server is ready when it accepts the requests
		if (current.get(RestServer.ENABLED)) {
			services.add(new RestServer(this.binder()));
		}
		else {
			log.info("HTTP server is not enabled.");
		}
		return services;
	}

	/**
	 * Gets the wiring, for the code which cannot receive its dependencies by injection.
	 *
	 * <p><b>Transitional:</b> a static lookup is a service locator, which hides the dependencies
	 * of its callers. Nothing in the main code uses it any more; it remains for the tests.
	 * Do not use it from new code.</p>
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
	 * Gets the checker of devices against the compliance policies.
	 *
	 * @return the compliance checker
	 */
	public ComplianceChecker complianceChecker() {
		return this.complianceChecker;
	}

	/**
	 * Gets the task use cases.
	 *
	 * @return the task service
	 */
	public TaskService taskService() {
		return this.taskService;
	}

	/**
	 * Gets the scheduler of the automatic snapshots.
	 *
	 * @return the automatic snapshot scheduler
	 */
	public AutoSnapshotScheduler autoSnapshotScheduler() {
		return this.autoSnapshotScheduler;
	}

	/**
	 * Gets the registry of the task handlers.
	 *
	 * @return the task handlers
	 */
	public TaskHandlers taskHandlers() {
		return this.taskHandlers;
	}

	/**
	 * Gets the factory of the Quartz task jobs.
	 *
	 * @return the task job factory
	 */
	public TaskJobFactory taskJobFactory() {
		return this.taskJobFactory;
	}

	/**
	 * Gets the reference lists built from the devices.
	 *
	 * @return the device catalog service
	 */
	public DeviceCatalogService deviceCatalogService() {
		return this.deviceCatalogService;
	}

	/**
	 * Gets the registry of the loaded device drivers.
	 *
	 * @return the driver registry
	 */
	public DriverRegistry driverRegistry() {
		return this.driverRegistry;
	}

	/**
	 * Gets the device driver (device type) use case.
	 *
	 * @return the device driver service
	 */
	public DeviceDriverService deviceDriverService() {
		return this.deviceDriverService;
	}

	/**
	 * Gets the domain use cases.
	 *
	 * @return the domain service
	 */
	public DomainService domainService() {
		return this.domainService;
	}

	/**
	 * Gets the device group use cases.
	 *
	 * @return the group service
	 */
	public GroupService groupService() {
		return this.groupService;
	}

	/**
	 * Gets the device search use case.
	 *
	 * @return the search service
	 */
	public SearchService searchService() {
		return this.searchService;
	}

	/**
	 * Gets the configuration use cases.
	 *
	 * @return the config service
	 */
	public ConfigService configService() {
		return this.configService;
	}

	/**
	 * Gets the user use cases.
	 *
	 * @return the user service
	 */
	public UserService userService() {
		return this.userService;
	}

	/**
	 * Gets the API token use cases.
	 *
	 * @return the API token service
	 */
	public ApiTokenService apiTokenService() {
		return this.apiTokenService;
	}

	/**
	 * Gets the login use case.
	 *
	 * @return the login service
	 */
	public LoginService loginService() {
		return this.loginService;
	}

	/**
	 * Gets the cluster use cases.
	 *
	 * @return the cluster service
	 */
	public ClusterService clusterService() {
		return this.clusterService;
	}

	/**
	 * Gets the server information use case.
	 *
	 * @return the server information service
	 */
	public ServerInfoService serverInfoService() {
		return this.serverInfoService;
	}

	/**
	 * Gets the diagnostic use cases.
	 *
	 * @return the diagnostic service
	 */
	public DiagnosticService diagnosticService() {
		return this.diagnosticService;
	}

	/**
	 * Gets the adapters of the diagnostics to the device scripts running them.
	 *
	 * @return the diagnostic adapters
	 */
	public DiagnosticAdapters diagnosticAdapters() {
		return this.diagnosticAdapters;
	}

	/**
	 * Gets the command script use cases.
	 *
	 * @return the script service
	 */
	public ScriptService scriptService() {
		return this.scriptService;
	}

	/**
	 * Gets the report use cases.
	 *
	 * @return the report service
	 */
	public ReportService reportService() {
		return this.reportService;
	}

	/**
	 * Gets the global credential set use cases.
	 *
	 * @return the credential set service
	 */
	public CredentialSetService credentialSetService() {
		return this.credentialSetService;
	}

	/**
	 * Gets the Vault instance use cases.
	 *
	 * @return the Vault instance service
	 */
	public VaultInstanceService vaultInstanceService() {
		return this.vaultInstanceService;
	}

	/**
	 * Gets the hook use cases.
	 *
	 * @return the hook service
	 */
	public HookService hookService() {
		return this.hookService;
	}

	/**
	 * Gets the domain events, to publish them from the code which cannot receive them by injection.
	 *
	 * @return the domain events
	 */
	public DomainEvents domainEvents() {
		return this.domainEvents;
	}
}
