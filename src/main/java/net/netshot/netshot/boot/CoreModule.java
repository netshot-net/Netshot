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

import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.group.DynamicGroups;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.database.DatabaseService;
import net.netshot.netshot.platform.database.LocalSecretConverter;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.event.DomainEvents;
import net.netshot.netshot.vault.VaultManager;

/**
 * The objects every feature uses: the configuration, the database access and the repositories, the secrets, the
 * domain events, the device drivers, the refresher of the dynamic groups and the Vault manager.
 *
 * @param config the configuration store
 * @param database the database access (the connection pools, a service)
 * @param transactions the transaction support
 * @param secretConverter the converter of the encrypted columns
 * @param repositories the repositories
 * @param domainEvents the domain events
 * @param driverRegistry the device drivers (a service)
 * @param dynamicGroups the refresher of the dynamic groups
 * @param vaultManager the Vault manager (the secret resolver of the device accesses)
 */
public record CoreModule(
	ConfigStore config,
	DatabaseService database,
	Transactions transactions,
	LocalSecretConverter secretConverter,
	Repositories repositories,
	DomainEvents domainEvents,
	DriverRegistry driverRegistry,
	DynamicGroups dynamicGroups,
	VaultManager vaultManager) {

	/**
	 * Builds the module.
	 *
	 * @param config the configuration store
	 * @param driverRegistry the device drivers
	 * @return the module
	 */
	public static CoreModule create(ConfigStore config, DriverRegistry driverRegistry) {
		DatabaseService database = new DatabaseService(config);
		Transactions transactions = new Transactions(database);
		LocalSecretConverter secretConverter = new LocalSecretConverter(Database.getEncryptionPassword(config.current()));
		Repositories repositories = Repositories.create(transactions, secretConverter, config);
		return new CoreModule(config, database, transactions, secretConverter, repositories, new DomainEvents(), driverRegistry,
			new DynamicGroups(transactions, driverRegistry, repositories.group()),
			new VaultManager(config, transactions, repositories.vaultInstance()));
	}
}
