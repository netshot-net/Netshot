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
package net.netshot.netshot.api.rest.v1;

import java.util.function.Supplier;

import net.netshot.netshot.aaa.user.ApiTokenService;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.compliance.HardwareRuleService;
import net.netshot.netshot.compliance.PolicyService;
import net.netshot.netshot.compliance.RuleService;
import net.netshot.netshot.compliance.SoftwareRuleService;
import net.netshot.netshot.credentials.CredentialSetService;
import net.netshot.netshot.diagnostic.DiagnosticService;
import net.netshot.netshot.domain.DomainService;
import net.netshot.netshot.driver.DeviceDriverService;
import net.netshot.netshot.hook.HookService;
import net.netshot.netshot.inventory.config.ConfigService;
import net.netshot.netshot.inventory.device.DeviceCatalogService;
import net.netshot.netshot.inventory.group.GroupService;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.query.QuerySchema;
import net.netshot.netshot.task.TaskService;
import net.netshot.netshot.userscript.ScriptService;
import net.netshot.netshot.vault.VaultInstanceService;

/**
 * The schemas of the v1 lists (what they filter, sort and group by), published by their use cases. A list endpoint
 * reads its query with one, and names it with {@link ListFilters} for the OpenAPI document.
 */
public enum ListSchema {

	API_TOKENS(ApiTokenService::listSchema),
	COMPLIANCE_RESULTS(RuleService::allResultsSchema),
	CONFIGS(ConfigService::listSchema),
	CREDENTIAL_SETS(CredentialSetService::listSchema),
	DEVICE_COMPLIANCE_RESULTS(RuleService::resultSchema),
	DEVICE_FAMILIES(DeviceCatalogService::familySchema),
	DEVICE_INTERFACES(DeviceCatalogService::interfaceSchema),
	DEVICE_MODULES(DeviceCatalogService::moduleSchema),
	DEVICE_TAGS(DeviceCatalogService::tagSchema),
	DEVICE_TYPES(DeviceDriverService::listSchema),
	DEVICES(SearchService::deviceListSchema),
	DIAGNOSTIC_RESULTS(DiagnosticService::listResultSchema),
	DIAGNOSTICS(DiagnosticService::listSchema),
	DOMAINS(DomainService::listSchema),
	EXEMPTIONS(RuleService::exemptionSchema),
	GROUPS(GroupService::listSchema),
	HARDWARE_RULES(HardwareRuleService::listSchema),
	HOOKS(HookService::listSchema),
	PART_NUMBERS(DeviceCatalogService::partNumberSchema),
	POLICIES(PolicyService::listSchema),
	RULES(RuleService::listSchema),
	SCRIPTS(ScriptService::listSchema),
	SOFTWARE_RULES(SoftwareRuleService::listSchema),
	SOFTWARE_VERSIONS(DeviceCatalogService::softwareVersionSchema),
	TASKS(TaskService::listSchema),
	USERS(UserService::listSchema),
	VAULT_INSTANCES(VaultInstanceService::listSchema);

	private final Supplier<QuerySchema> supplier;

	ListSchema(Supplier<QuerySchema> supplier) {
		this.supplier = supplier;
	}

	/**
	 * Gets the schema.
	 *
	 * @return the schema
	 */
	public QuerySchema schema() {
		return this.supplier.get();
	}
}
