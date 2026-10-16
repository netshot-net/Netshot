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

import net.netshot.netshot.aaa.auth.LoginService;
import net.netshot.netshot.aaa.user.ApiTokenService;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.cluster.ClusterService;
import net.netshot.netshot.compliance.HardwareRuleService;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.compliance.SoftwareRuleService;
import net.netshot.netshot.diagnostic.DiagnosticService;
import net.netshot.netshot.driver.DeviceDriverService;
import net.netshot.netshot.inventory.config.ConfigService;
import net.netshot.netshot.inventory.device.DeviceCatalogService;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.inventory.domain.DomainService;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.report.ReportService;
import net.netshot.netshot.system.ServerInfoService;
import net.netshot.netshot.task.TaskService;
import net.netshot.netshot.userscript.ScriptService;

/**
 * Exposes the objects of the {@link Wiring} to the dependency injection of the REST
 * resources (HK2, the injection container of Jersey).
 *
 * <p>The web application does not know the composition root: {@code boot} hands this
 * binder over to it, and the resources declare what they need with {@code @Inject}.</p>
 */
final class WiringBinder extends AbstractBinder {

	private final Wiring wiring;

	WiringBinder(Wiring wiring) {
		this.wiring = wiring;
	}

	@Override
	protected void configure() {
		this.bind(this.wiring.transactions()).to(Transactions.class);
		this.bind(this.wiring.deviceService()).to(DeviceService.class);
		// Compliance
		this.bind(this.wiring.policyService()).to(PolicyService.class);
		this.bind(this.wiring.ruleService()).to(RuleService.class);
		this.bind(this.wiring.softwareRuleService()).to(SoftwareRuleService.class);
		this.bind(this.wiring.hardwareRuleService()).to(HardwareRuleService.class);
		// Tasks
		this.bind(this.wiring.taskService()).to(TaskService.class);
		// Inventory
		this.bind(this.wiring.deviceCatalogService()).to(DeviceCatalogService.class);
		this.bind(this.wiring.deviceDriverService()).to(DeviceDriverService.class);
		this.bind(this.wiring.domainService()).to(DomainService.class);
		this.bind(this.wiring.groupService()).to(GroupService.class);
		this.bind(this.wiring.searchService()).to(SearchService.class);
		this.bind(this.wiring.configService()).to(ConfigService.class);
		// AAA and system
		this.bind(this.wiring.userService()).to(UserService.class);
		this.bind(this.wiring.apiTokenService()).to(ApiTokenService.class);
		this.bind(this.wiring.loginService()).to(LoginService.class);
		this.bind(this.wiring.clusterService()).to(ClusterService.class);
		this.bind(this.wiring.serverInfoService()).to(ServerInfoService.class);
		// Diagnostics
		this.bind(this.wiring.diagnosticService()).to(DiagnosticService.class);
		// Scripts
		this.bind(this.wiring.scriptService()).to(ScriptService.class);
		// Reports
		this.bind(this.wiring.reportService()).to(ReportService.class);
	}
}
