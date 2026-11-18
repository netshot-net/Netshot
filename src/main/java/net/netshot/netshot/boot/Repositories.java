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

import net.netshot.netshot.aaa.user.ApiTokenRepository;
import net.netshot.netshot.aaa.user.UserRepository;
import net.netshot.netshot.access.DeviceReads;
import net.netshot.netshot.compliance.HardwareRuleRepository;
import net.netshot.netshot.compliance.PolicyRepository;
import net.netshot.netshot.compliance.RuleRepository;
import net.netshot.netshot.compliance.SoftwareRuleRepository;
import net.netshot.netshot.credentials.CredentialSetAssembler;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.diagnostic.DiagnosticRepository;
import net.netshot.netshot.domain.DomainRepository;
import net.netshot.netshot.hook.HookRepository;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.config.attribute.BinaryFileStorage;
import net.netshot.netshot.inventory.device.DeviceInventoryRepository;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResultRepository;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.database.LocalSecretConverter;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.report.ReportRepository;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.userscript.ScriptRepository;
import net.netshot.netshot.vault.VaultInstanceRepository;

/**
 * The repositories of the application, one of each (they are stateless, and shared by the features), with the
 * objects built on them which the features share: the assembler of the credential sets, the storage of the binary
 * files and the reads of the device accesses.
 *
 * @param config the configurations
 * @param diagnosticResult the diagnostic results
 * @param credentialSet the credential sets
 * @param device the devices
 * @param deviceInventory the inventory of the devices (families, modules, interfaces...)
 * @param domain the domains
 * @param group the device groups
 * @param policy the compliance policies
 * @param rule the compliance rules and their results
 * @param softwareRule the software rules
 * @param hardwareRule the hardware rules
 * @param task the tasks
 * @param diagnostic the diagnostics
 * @param vaultInstance the Vault instances
 * @param user the users
 * @param apiToken the API tokens
 * @param report the reports
 * @param script the device scripts
 * @param hook the hooks
 * @param credentialSetAssembler assembles the credential sets with their Vault instances
 * @param binaryFiles the storage of the binary file attributes
 * @param deviceReads the reads of the device accesses and the device scripts
 */
public record Repositories(
	ConfigRepository config,
	DiagnosticResultRepository diagnosticResult,
	CredentialSetRepository credentialSet,
	DeviceRepository device,
	DeviceInventoryRepository deviceInventory,
	DomainRepository domain,
	GroupRepository group,
	PolicyRepository policy,
	RuleRepository rule,
	SoftwareRuleRepository softwareRule,
	HardwareRuleRepository hardwareRule,
	TaskRepository task,
	DiagnosticRepository diagnostic,
	VaultInstanceRepository vaultInstance,
	UserRepository user,
	ApiTokenRepository apiToken,
	ReportRepository report,
	ScriptRepository script,
	HookRepository hook,
	CredentialSetAssembler credentialSetAssembler,
	BinaryFileStorage binaryFiles,
	DeviceReads deviceReads) {

	/**
	 * Builds the repositories.
	 *
	 * @param transactions the transaction support (of the reads of the device accesses)
	 * @param secrets the converter of the encrypted columns
	 * @param config the configuration store (the folder of the binary files)
	 * @return the repositories
	 */
	public static Repositories create(Transactions transactions, LocalSecretConverter secrets, ConfigStore config) {
		ConfigRepository configs = new ConfigRepository();
		DiagnosticResultRepository diagnosticResult = new DiagnosticResultRepository();
		CredentialSetRepository credentialSet = new CredentialSetRepository(secrets);
		DeviceRepository device = new DeviceRepository(configs, diagnosticResult, credentialSet);
		VaultInstanceRepository vaultInstance = new VaultInstanceRepository(secrets);
		return new Repositories(configs, diagnosticResult, credentialSet, device, new DeviceInventoryRepository(),
			new DomainRepository(), new GroupRepository(), new PolicyRepository(), new RuleRepository(), new SoftwareRuleRepository(),
			new HardwareRuleRepository(), new TaskRepository(), new DiagnosticRepository(), vaultInstance, new UserRepository(),
			new ApiTokenRepository(), new ReportRepository(), new ScriptRepository(), new HookRepository(),
			new CredentialSetAssembler(vaultInstance), new BinaryFileStorage(config),
			new DeviceReads(transactions, device, configs, diagnosticResult, credentialSet));
	}
}
