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

import net.netshot.netshot.diagnostic.DiagnosticService;
import net.netshot.netshot.domain.DomainService;
import net.netshot.netshot.driver.DeviceDriverService;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.config.ConfigService;
import net.netshot.netshot.inventory.device.DeviceCatalogService;
import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.report.ReportService;
import net.netshot.netshot.userscript.ScriptService;

/**
 * The inventory: the use cases of the devices, their drivers, catalog, domains, groups, configurations, reports, diagnostics and scripts.
 *
 * @param deviceService the device use cases
 * @param deviceDriverService the device driver use cases
 * @param deviceCatalogService the device catalog use cases
 * @param domainService the domain use cases
 * @param groupService the group use cases
 * @param searchService the device search use cases
 * @param configService the configuration use cases
 * @param reportService the report use cases
 * @param diagnosticService the diagnostic use cases
 * @param scriptService the device script use cases
 */
public record InventoryModule(
	DeviceService deviceService,
	DeviceDriverService deviceDriverService,
	DeviceCatalogService deviceCatalogService,
	DomainService domainService,
	GroupService groupService,
	SearchService searchService,
	ConfigService configService,
	ReportService reportService,
	DiagnosticService diagnosticService,
	ScriptService scriptService) {

	/**
	 * Builds the module.
	 *
	 * @param core the core module
	 * @param tasks the task module (the cluster, notified of the driver reloads)
	 * @return the module
	 */
	public static InventoryModule create(CoreModule core, TaskModule tasks) {
		Transactions transactions = core.transactions();
		Repositories r = core.repositories();
		DriverRegistry drivers = core.driverRegistry();
		return new InventoryModule(
			new DeviceService(transactions, r.device(), drivers, core.dynamicGroups(), r.domain(), r.credentialSet(),
				r.credentialSetAssembler(), r.binaryFiles()),
			new DeviceDriverService(drivers, tasks.cluster()::requestDriverReload),
			new DeviceCatalogService(transactions, r.deviceInventory()),
			new DomainService(transactions, r.domain()),
			new GroupService(transactions, r.group(), core.dynamicGroups()),
			new SearchService(transactions, r.device(), drivers),
			new ConfigService(transactions, r.config(), drivers, r.device(), r.binaryFiles()),
			new ReportService(transactions, r.report(), drivers),
			new DiagnosticService(transactions, r.diagnostic(), r.diagnosticResult(), r.group()),
			new ScriptService(transactions, r.script(), drivers));
	}
}
