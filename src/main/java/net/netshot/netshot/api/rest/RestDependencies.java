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
package net.netshot.netshot.api.rest;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import net.netshot.netshot.aaa.auth.LoginService;
import net.netshot.netshot.aaa.auth.Oidc;
import net.netshot.netshot.aaa.auth.Tacacs;
import net.netshot.netshot.aaa.user.ApiTokenService;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.cluster.ClusterService;
import net.netshot.netshot.compliance.HardwareRuleService;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.compliance.SoftwareRuleService;
import net.netshot.netshot.credentials.CredentialSetService;
import net.netshot.netshot.diagnostic.DiagnosticService;
import net.netshot.netshot.domain.DomainService;
import net.netshot.netshot.driver.DeviceDriverService;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.hook.HookService;
import net.netshot.netshot.inventory.config.ConfigService;
import net.netshot.netshot.inventory.device.DeviceCatalogService;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.report.ReportService;
import net.netshot.netshot.system.ServerInfoService;
import net.netshot.netshot.system.ServiceHealthService;
import net.netshot.netshot.task.DeviceCreationService;
import net.netshot.netshot.task.TaskService;
import net.netshot.netshot.userscript.ScriptService;
import net.netshot.netshot.vault.VaultInstanceService;

/**
 * What the REST resources may inject: the configuration, the drivers and the use cases.
 * Built by the composition root, given to the {@link RestServer}, which registers its {@link #binder()} into each web
 * application.
 *
 * @param config the configuration store
 * @param driverRegistry the device drivers
 * @param deviceService the device use cases
 * @param deviceCreationService the device creation use cases
 * @param policyService the policy use cases
 * @param ruleService the rule use cases
 * @param softwareRuleService the software rule use cases
 * @param hardwareRuleService the hardware rule use cases
 * @param taskService the task use cases
 * @param deviceCatalogService the device catalog use cases
 * @param deviceDriverService the device driver use cases
 * @param domainService the domain use cases
 * @param groupService the group use cases
 * @param searchService the search use cases
 * @param configService the config use cases
 * @param userService the user use cases
 * @param apiTokenService the api token use cases
 * @param loginService the login use cases
 * @param oidc the oidc use cases
 * @param tacacs the tacacs use cases
 * @param clusterService the cluster use cases
 * @param serverInfoService the server info use cases
 * @param serviceHealthService the health use cases
 * @param diagnosticService the diagnostic use cases
 * @param scriptService the script use cases
 * @param reportService the report use cases
 * @param credentialSetService the credential set use cases
 * @param vaultInstanceService the vault instance use cases
 * @param hookService the hook use cases
 */
public record RestDependencies(
	ConfigStore config,
	DriverRegistry driverRegistry,
	DeviceService deviceService,
	DeviceCreationService deviceCreationService,
	PolicyService policyService,
	RuleService ruleService,
	SoftwareRuleService softwareRuleService,
	HardwareRuleService hardwareRuleService,
	TaskService taskService,
	DeviceCatalogService deviceCatalogService,
	DeviceDriverService deviceDriverService,
	DomainService domainService,
	GroupService groupService,
	SearchService searchService,
	ConfigService configService,
	UserService userService,
	ApiTokenService apiTokenService,
	LoginService loginService,
	Oidc oidc,
	Tacacs tacacs,
	ClusterService clusterService,
	ServerInfoService serverInfoService,
	ServiceHealthService serviceHealthService,
	DiagnosticService diagnosticService,
	ScriptService scriptService,
	ReportService reportService,
	CredentialSetService credentialSetService,
	VaultInstanceService vaultInstanceService,
	HookService hookService) {

	/**
	 * Gets a dependency injection binder exposing the dependencies to the resources of a web application (HK2, the
	 * injection container of Jersey). The resources declare what they need in an {@code @Inject} constructor.
	 *
	 * @return a new binder
	 */
	public AbstractBinder binder() {
		return new AbstractBinder() {
			@Override
			protected void configure() {
				this.bind(config).to(ConfigStore.class);
				this.bind(new ClientAddress(config)).to(ClientAddress.class);
				this.bind(driverRegistry).to(DriverRegistry.class);
				this.bind(deviceService).to(DeviceService.class);
				this.bind(deviceCreationService).to(DeviceCreationService.class);
				this.bind(policyService).to(PolicyService.class);
				this.bind(ruleService).to(RuleService.class);
				this.bind(softwareRuleService).to(SoftwareRuleService.class);
				this.bind(hardwareRuleService).to(HardwareRuleService.class);
				this.bind(taskService).to(TaskService.class);
				this.bind(deviceCatalogService).to(DeviceCatalogService.class);
				this.bind(deviceDriverService).to(DeviceDriverService.class);
				this.bind(domainService).to(DomainService.class);
				this.bind(groupService).to(GroupService.class);
				this.bind(searchService).to(SearchService.class);
				this.bind(configService).to(ConfigService.class);
				this.bind(userService).to(UserService.class);
				this.bind(apiTokenService).to(ApiTokenService.class);
				this.bind(loginService).to(LoginService.class);
				this.bind(oidc).to(Oidc.class);
				this.bind(tacacs).to(Tacacs.class);
				this.bind(clusterService).to(ClusterService.class);
				this.bind(serverInfoService).to(ServerInfoService.class);
				this.bind(serviceHealthService).to(ServiceHealthService.class);
				this.bind(diagnosticService).to(DiagnosticService.class);
				this.bind(scriptService).to(ScriptService.class);
				this.bind(reportService).to(ReportService.class);
				this.bind(credentialSetService).to(CredentialSetService.class);
				this.bind(vaultInstanceService).to(VaultInstanceService.class);
				this.bind(hookService).to(HookService.class);
			}
		};
	}
}
