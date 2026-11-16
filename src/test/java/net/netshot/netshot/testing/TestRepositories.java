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
package net.netshot.netshot.testing;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.access.DeviceReads;
import net.netshot.netshot.compliance.HardwareRuleRepository;
import net.netshot.netshot.compliance.PolicyRepository;
import net.netshot.netshot.compliance.RuleRepository;
import net.netshot.netshot.compliance.SoftwareRuleRepository;
import net.netshot.netshot.credentials.CredentialSetAssembler;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.diagnostic.DiagnosticRepository;
import net.netshot.netshot.domain.DomainRepository;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.config.attribute.BinaryFileStorage;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResultRepository;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.LocalSecretConverter;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.vault.VaultInstanceRepository;

/**
 * The repositories (and the storage of the binary files) for the tests which build the objects they test themselves
 * (the main code receives them from the wiring). The repositories are stateless: the tests share one of each.
 */
public final class TestRepositories {

	public static final ConfigRepository CONFIG = new ConfigRepository();
	public static final DiagnosticResultRepository DIAGNOSTIC_RESULT = new DiagnosticResultRepository();
	/** The converter of the encrypted columns, with the encryption password of the configuration of the tests. */
	public static final LocalSecretConverter SECRET_CONVERTER = new LocalSecretConverter(Database.getEncryptionPassword(Netshot.config().current()));
	public static final CredentialSetRepository CREDENTIAL_SET = new CredentialSetRepository(SECRET_CONVERTER);
	public static final DeviceRepository DEVICE = new DeviceRepository(CONFIG, DIAGNOSTIC_RESULT, CREDENTIAL_SET);
	public static final DomainRepository DOMAIN = new DomainRepository();
	public static final GroupRepository GROUP = new GroupRepository();
	public static final PolicyRepository POLICY = new PolicyRepository();
	public static final RuleRepository RULE = new RuleRepository();
	public static final SoftwareRuleRepository SOFTWARE_RULE = new SoftwareRuleRepository();
	public static final HardwareRuleRepository HARDWARE_RULE = new HardwareRuleRepository();
	public static final TaskRepository TASK = new TaskRepository();
	public static final DiagnosticRepository DIAGNOSTIC = new DiagnosticRepository();
	public static final VaultInstanceRepository VAULT_INSTANCE = new VaultInstanceRepository(SECRET_CONVERTER);
	public static final CredentialSetAssembler CREDENTIAL_SET_ASSEMBLER = new CredentialSetAssembler(VAULT_INSTANCE);
	/** The storage of the binary file attributes, bound to the configuration of the tests. */
	public static final BinaryFileStorage BINARY_FILES = new BinaryFileStorage(Netshot.config());

	private TestRepositories() {
	}

	/**
	 * Builds the reads of the database of the device accesses and scripts.
	 *
	 * @param transactions the transaction support
	 * @return the reads
	 */
	public static DeviceReads deviceReads(Transactions transactions) {
		return new DeviceReads(transactions, DEVICE, CONFIG, DIAGNOSTIC_RESULT, CREDENTIAL_SET);
	}
}
