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

import net.netshot.netshot.aaa.auth.ConfiguredRemoteAuthenticator;
import net.netshot.netshot.aaa.auth.LoginService;
import net.netshot.netshot.aaa.user.ApiTokenRepository;
import net.netshot.netshot.aaa.user.ApiTokenService;
import net.netshot.netshot.aaa.user.UserRepository;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.cluster.ClusterService;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.system.ServerInfoService;
import net.netshot.netshot.task.engine.TaskManager;

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
	private final DeviceService deviceService;
	// AAA and system
	private final UserService userService;
	private final ApiTokenService apiTokenService;
	private final LoginService loginService;
	private final ClusterService clusterService;
	private final ServerInfoService serverInfoService;

	private Wiring(Transactions transactions, DeviceService deviceService, UserService userService,
		ApiTokenService apiTokenService, LoginService loginService, ClusterService clusterService,
		ServerInfoService serverInfoService) {
		this.transactions = transactions;
		this.deviceService = deviceService;
		this.userService = userService;
		this.apiTokenService = apiTokenService;
		this.loginService = loginService;
		this.clusterService = clusterService;
		this.serverInfoService = serverInfoService;
	}

	/**
	 * Initializes the database access and builds the wiring.
	 *
	 * @return the new wiring
	 */
	public static synchronized Wiring init() {
		Database.init(EntityModel.build());
		Transactions transactions = new Transactions();
		DeviceRepository deviceRepository = new DeviceRepository();
		DeviceService deviceService = new DeviceService(transactions, deviceRepository, TaskManager::addTask);
		// AAA and system
		UserRepository userRepository = new UserRepository();
		UserService userService = new UserService(transactions, userRepository);
		ApiTokenService apiTokenService = new ApiTokenService(transactions, new ApiTokenRepository());
		LoginService loginService = new LoginService(transactions, userRepository, new ConfiguredRemoteAuthenticator());
		ClusterService clusterService = new ClusterService(ClusterManager::getClusterMembers);
		ServerInfoService serverInfoService = new ServerInfoService();
		instance = new Wiring(transactions, deviceService, userService, apiTokenService, loginService,
			clusterService, serverInfoService);
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
}
