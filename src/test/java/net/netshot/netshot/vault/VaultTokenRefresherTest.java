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
package net.netshot.netshot.vault;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Vault token refresher")
class VaultTokenRefresherTest {

	@Test
	@DisplayName("Healthy while it runs")
	void health() throws Exception {
		ConfigStore config = new ConfigStore(Config.empty());
		VaultManager vaultManager = new VaultManager(_ -> null, config);
		// No database: the refresh rounds fail, the refresher keeps running
		VaultTokenRefresher refresher = new VaultTokenRefresher(null, vaultManager, null, config);
		Assertions.assertFalse(refresher.isHealthy(), "Healthy before the start");
		refresher.start();
		Assertions.assertTrue(refresher.isHealthy(), "Unhealthy once started");
		refresher.stop();
		Assertions.assertFalse(refresher.isHealthy(), "Healthy once stopped");
	}
}
