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

import java.util.Optional;

import net.netshot.netshot.api.hook.HookDispatcher;
import net.netshot.netshot.api.hook.WebHookSender;
import net.netshot.netshot.api.rest.ClientAddress;
import net.netshot.netshot.api.rest.RestDependencies;
import net.netshot.netshot.api.rest.RestServer;
import net.netshot.netshot.hook.HookService;

/**
 * The API channels: the hooks (their use cases, and the dispatcher of the events to the webhooks) and the REST server
 * (absent when disabled), with what its resources may inject.
 *
 * @param hookService the hook use cases
 * @param restDependencies what the REST resources may inject
 * @param restServer the REST server (a service), absent when disabled
 */
public record ApiModule(
	HookService hookService,
	RestDependencies restDependencies,
	Optional<RestServer> restServer) {

	/**
	 * Builds the module.
	 *
	 * @param core the core module
	 * @param compliance the compliance module
	 * @param tasks the task module
	 * @param inventory the inventory module
	 * @param credentials the credentials module
	 * @param aaa the authentication and authorization module
	 * @param system the system module
	 * @return the module
	 */
	public static ApiModule create(CoreModule core, ComplianceModule compliance, TaskModule tasks, InventoryModule inventory,
			CredentialsModule credentials, AaaModule aaa, SystemModule system) {
		HookService hookService = new HookService(core.transactions(), core.repositories().hook());
		new HookDispatcher(hookService, tasks.taskService(), new WebHookSender(), core.driverRegistry()).register(core.domainEvents());
		RestDependencies restDependencies = new RestDependencies(core.config(), new ClientAddress(core.config()), core.driverRegistry(),
			inventory.deviceService(), tasks.deviceCreationService(), compliance.policyService(), compliance.ruleService(),
			compliance.softwareRuleService(), compliance.hardwareRuleService(), tasks.taskService(), inventory.deviceCatalogService(),
			inventory.deviceDriverService(), inventory.domainService(), inventory.groupService(), inventory.searchService(),
			inventory.configService(), aaa.userService(), aaa.apiTokenService(), aaa.loginService(), aaa.oidc(), aaa.tacacs(),
			system.clusterService(), system.serverInfoService(), system.serviceHealthService(), inventory.diagnosticService(),
			inventory.scriptService(), inventory.reportService(), credentials.credentialSetService(), credentials.vaultInstanceService(),
			hookService);
		Optional<RestServer> restServer = core.config().current().get(RestServer.ENABLED)
			? Optional.of(new RestServer(restDependencies, core.config()))
			: Optional.empty();
		return new ApiModule(hookService, restDependencies, restServer);
	}
}
