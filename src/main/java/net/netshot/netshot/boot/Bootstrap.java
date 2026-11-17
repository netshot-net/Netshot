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

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigException;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.tls.BouncyCastleLoader;
import net.netshot.netshot.platform.tls.Truststore;

/**
 * Initializes the process before the application is built, once per JVM: loads the configuration store, sets up the
 * logging, the crypto libraries and the truststore. Used by the server ({@code Netshot.main}) and by the tools, so
 * that they all start the same way (the tests, which build several applications, set up what they need
 * themselves).
 */
@Slf4j
public final class Bootstrap {

	private Bootstrap() {
	}

	/**
	 * Initializes the process, once per JVM (a second call is a mistake: the crypto libraries, the logging and the
	 * truststore are global to the JVM), in this order:
	 * <ol>
	 * <li>the configuration store is loaded from its source, and the logging is set up from it;</li>
	 * <li>the crypto libraries are registered, before anything initializes the SSH library, which checks the
	 * available crypto providers (EdDSA, X25519...) only once;</li>
	 * <li>the catalog of the settings is built (it initializes the classes which declare them, the SSH ones
	 * included), and the loaded configuration is checked against it (secret files, unknown items);</li>
	 * <li>the logging follows the changes of the configuration (first listener, so that the other changes are
	 * logged accordingly);</li>
	 * <li>the JVM is checked, the truststore is applied.</li>
	 * </ol>
	 *
	 * @param config the configuration store, with its source (e.g. {@link ConfigStore#ofFiles})
	 * @throws BootstrapException if the configuration can't be read or checked, or the logging or the crypto
	 *     libraries can't be set up
	 */
	public static void initialize(ConfigStore config) throws BootstrapException {
		load(config);
		registerCryptoLibraries(config.current());
		checkSettings(config);
		config.addListener(LoggingSetup::apply);
		checkJvm();
		log.info("Selecting truststore.");
		Truststore.apply(config.current());
	}

	/**
	 * Loads the configuration into the store from its source, and sets up the logging from it.
	 *
	 * @param config the configuration store
	 * @throws BootstrapException if the configuration can't be read (the message tells why) or the logging can't be
	 *     set up
	 */
	private static void load(ConfigStore config) throws BootstrapException {
		try {
			config.load();
		}
		catch (ConfigException e) {
			throw new BootstrapException("Unable to read the configuration: " + e.getMessage());
		}
		try {
			LoggingSetup.apply(config.current());
		}
		catch (IllegalStateException e) {
			throw new BootstrapException("Unable to set up the logging: " + e.getMessage());
		}
	}

	/**
	 * Registers the additional crypto libraries (Bouncy Castle).
	 *
	 * @param config the configuration
	 * @throws BootstrapException if the libraries can't be registered
	 */
	private static void registerCryptoLibraries(Config config) throws BootstrapException {
		log.info("Registering crypto libraries");
		try {
			BouncyCastleLoader.registerBouncyCastle(config);
		}
		catch (Exception e) {
			throw new BootstrapException(String.valueOf(e.getMessage()), e);
		}
	}

	/**
	 * Sets the catalog of the settings, and checks the loaded configuration against it.
	 *
	 * @param config the configuration store
	 * @throws BootstrapException if the configuration is invalid (the message tells why)
	 */
	private static void checkSettings(ConfigStore config) throws BootstrapException {
		try {
			config.setCatalog(NetshotSettings.catalog());
		}
		catch (ConfigException e) {
			throw new BootstrapException("Invalid configuration: " + e.getMessage());
		}
	}

	/**
	 * Checks the JVM, and logs an error if it's not GraalVM 25.
	 */
	private static void checkJvm() {
		log.info("Checking current JVM.");
		final String vendorVersion = System.getProperty("java.vendor.version"); // e.g. Oracle GraalVM 25.0.4+7.1
		final String vendor = System.getProperty("java.vendor"); // e.g. Oracle Corporation
		final String version = System.getProperty("java.vm.version"); // e.g. 25.0.4+7-LTS-jvmci-b01
		log.warn("Running on JVM: {}", vendorVersion);
		if (!vendorVersion.matches(".*GraalVM.*")) {
			log.error("The current JVM vendor version '{}' by '{}' doesn't look like GraalVM, Netshot might not work properly.", vendorVersion, vendor);
		}
		if (!version.matches("^25\\..*")) {
			log.error("The JVM version '{}' doesn't look like version 25, Netshot might not work properly.", version);
		}
	}
}
