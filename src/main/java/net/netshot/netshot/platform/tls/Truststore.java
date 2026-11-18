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
package net.netshot.netshot.platform.tls;

import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.Setting;

/**
 * The custom truststore of the JVM (the CA certificates to trust), set at start.
 */
public final class Truststore {

	/** A custom truststore (CA certificates to trust). */
	public static final Setting<String> FILE = Setting.text("netshot.ssl.truststore.file").restart();

	private Truststore() {
	}

	/**
	 * Sets the custom truststore of the JVM, if one is configured.
	 *
	 * @param config the configuration
	 */
	public static void apply(Config config) {
		String path = config.get(FILE);
		if (path != null) {
			System.setProperty("javax.net.ssl.trustStore", path);
		}
	}
}
