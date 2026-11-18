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

import java.util.Properties;

import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;

/**
 * The configuration store of the tests (the main code has no static configuration: the store is given to the
 * application by its entry point). The tests share it: {@link DatabaseExtension} sets the configuration of each
 * database test class, the other tests set what they need.
 */
public final class TestConfig {

	/** The configuration store of the tests. */
	private static final ConfigStore STORE = new ConfigStore(Config.empty());

	private TestConfig() {
	}

	/**
	 * Gets the configuration store of the tests.
	 *
	 * @return the configuration store
	 */
	public static ConfigStore store() {
		return STORE;
	}

	/**
	 * Replaces the configuration of the tests (the bindings and the listeners are refreshed, as on a reload).
	 *
	 * @param config the new configuration
	 */
	public static void init(Properties config) {
		STORE.replace(Config.of(config));
	}

	/**
	 * Gets a copy of the items of the current configuration of the tests (to modify and init again).
	 *
	 * @return the configuration items
	 */
	public static Properties properties() {
		return STORE.current().toProperties();
	}
}
