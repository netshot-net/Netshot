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

import java.util.ArrayList;
import java.util.List;


import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.DriverLoader;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.lifecycle.Service;
import net.netshot.netshot.platform.lifecycle.ServiceManager;

/**
 * The composition root: builds the long-lived objects of the application, as modules (each one built from the
 * modules before it), and holds them.
 *
 * <p>The wiring is built without side effect, by {@link Application} (the server, the tools and the tests),
 * which starts its {@link #services()}.</p>
 *
 * <p>The REST resources receive what they may inject through the {@code RestDependencies} of the {@link ApiModule},
 * given to the REST server. The other objects receive their dependencies by constructor.</p>
 */
@Slf4j
public final class Wiring {

	private final CoreModule core;
	private final AccessModule access;
	private final ComplianceModule compliance;
	private final TaskModule tasks;
	private final InventoryModule inventory;
	private final CredentialsModule credentials;
	private final AaaModule aaa;
	private final ApiModule api;
	private final ServiceManager services;
	private final SystemModule system;

	/**
	 * Builds the objects of the application, module by module (each one from the modules built before it).
	 *
	 * @param driverRegistry the registry of the device drivers
	 * @param config the configuration store
	 */
	private Wiring(DriverRegistry driverRegistry, ConfigStore config) {
		this.core = CoreModule.create(config, driverRegistry);
		this.access = AccessModule.create(this.core);
		this.compliance = ComplianceModule.create(this.core);
		this.tasks = TaskModule.create(this.core, this.access, this.compliance);
		this.inventory = InventoryModule.create(this.core, this.tasks);
		this.credentials = CredentialsModule.create(this.core, this.tasks);
		this.aaa = AaaModule.create(this.core);
		// The lifecycle of the services (filled once they are all built), and the report of their state
		this.services = new ServiceManager(List.of());
		this.system = SystemModule.create(this.tasks, this.aaa, this.services);
		this.api = ApiModule.create(this.core, this.compliance, this.tasks, this.inventory, this.credentials, this.aaa, this.system);
		// The channels which subscribe to the domain events
		this.api.hookDispatcher().register(this.core.domainEvents());
		this.serviceList().forEach(this.services::add);
	}

	/**
	 * Builds the wiring, without side effect: the database access is initialized by the first of the services.
	 *
	 * @param config the configuration store
	 * @return the new wiring
	 */
	public static Wiring create(ConfigStore config) {
		return new Wiring(new DriverRegistry(new DriverLoader(config)), config);
	}

	/**
	 * Builds the wiring around an existing registry of drivers (loaded or not), without side effect.
	 *
	 * @param config the configuration store
	 * @param driverRegistry the registry of the device drivers
	 * @return the new wiring
	 */
	public static Wiring create(ConfigStore config, DriverRegistry driverRegistry) {
		return new Wiring(driverRegistry, config);
	}

	/**
	 * Lists the services of the server, in start order (they're stopped in the reverse order).
	 * The services disabled by the configuration with a restart (the cluster manager, the SSH server, the REST server)
	 * aren't part of the list; the Syslog server and the SNMP trap receiver, which apply their settings live, are
	 * (idle while disabled).
	 *
	 * @return the services
	 */
	private List<Service> serviceList() {
		List<Service> list = new ArrayList<>();
		list.add(this.core.database());
		list.add(this.core.driverRegistry());
		list.add(this.aaa.oidcDiscovery());
		// Before the clustering manager, which can start running tasks
		list.add(this.tasks.lockManager());
		this.tasks.clusterManager().ifPresentOrElse(list::add, () -> disabled("cluster manager", "netshot.cluster.enabled"));
		list.add(this.credentials.vaultTokenRefresher());
		// Before the task manager, which runs the tasks connecting to the devices
		list.add(this.access.sshClientService());
		list.add(this.tasks.taskManager());
		// The receivers submit tasks: after the task manager
		list.add(this.tasks.syslogServer());
		list.add(this.tasks.snmpTrapReceiver());
		this.access.sshServer().ifPresentOrElse(list::add, () -> disabled("SSH/SCP/SFTP server", "netshot.sshserver.enabled"));
		// Last: the server is ready when it accepts the requests
		this.api.restServer().ifPresentOrElse(list::add, () -> disabled("Web/REST server", "netshot.http.enabled"));
		return list;
	}

	/**
	 * Logs that a service is disabled by a setting which needs a restart.
	 *
	 * @param name the name of the service
	 * @param setting the setting which disables it
	 */
	private static void disabled(String name, String setting) {
		log.info("The {} is disabled by configuration ({}).", name, setting);
	}

	/**
	 * Gets the manager of the services of the server (started by {@link Application}).
	 *
	 * @return the service manager
	 */
	public ServiceManager services() {
		return this.services;
	}

	/**
	 * Gets the module of the objects every feature uses (configuration, database, repositories, drivers...).
	 *
	 * @return the module
	 */
	public CoreModule core() {
		return this.core;
	}

	/**
	 * Gets the module of the access to the devices.
	 *
	 * @return the module
	 */
	public AccessModule access() {
		return this.access;
	}

	/**
	 * Gets the module of the compliance.
	 *
	 * @return the module
	 */
	public ComplianceModule compliance() {
		return this.compliance;
	}

	/**
	 * Gets the module of the tasks (engine, cluster, handlers, receivers).
	 *
	 * @return the module
	 */
	public TaskModule tasks() {
		return this.tasks;
	}

	/**
	 * Gets the module of the inventory use cases.
	 *
	 * @return the module
	 */
	public InventoryModule inventory() {
		return this.inventory;
	}

	/**
	 * Gets the module of the credentials (credential sets, Vault).
	 *
	 * @return the module
	 */
	public CredentialsModule credentials() {
		return this.credentials;
	}

	/**
	 * Gets the module of the authentication and the authorization.
	 *
	 * @return the module
	 */
	public AaaModule aaa() {
		return this.aaa;
	}

	/**
	 * Gets the module of the API channels (hooks, REST server).
	 *
	 * @return the module
	 */
	public ApiModule api() {
		return this.api;
	}

	/**
	 * Gets the module of the system (cluster members, server information, health).
	 *
	 * @return the module
	 */
	public SystemModule system() {
		return this.system;
	}
}
