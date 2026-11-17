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

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigException;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.tls.BouncyCastleLoader;
import net.netshot.netshot.platform.tls.Truststore;

/**
 * Prepares the process before the application is built: loads the configuration, sets up the logging, the crypto
 * libraries and the truststore. Used by the server ({@code Netshot.main}) and by the tools, so that they all start
 * the same way.
 */
@Slf4j
public final class Bootstrap {

	/** The configuration files to look at, in sequence, when none is given. */
	public static final List<String> DEFAULT_FILES = List.of("netshot.conf", "/etc/netshot.conf");

	private Bootstrap() {
	}

	/**
	 * Loads the configuration from the files and prepares the process.
	 *
	 * <p>The crypto libraries are registered before anything initializes the SSH library, which checks the
	 * available crypto providers (EdDSA, X25519...) only once. Building the catalog of the settings initializes the
	 * classes which declare them, the SSH ones included: the settings of the crypto libraries are read from a first
	 * load of the configuration, without catalog (without the secrets), then the configuration is loaded again with
	 * the catalog. The logging is set up from each load, and again on each change of the configuration (first, so
	 * that the other changes are logged accordingly).</p>
	 *
	 * @param files the configuration files to look at, in sequence (the first readable one is used), on startup and
	 *     on each reload
	 * @return the configuration store, holding the loaded configuration
	 * @throws BootstrapException if the configuration can't be read, or the logging or the crypto libraries can't be
	 *     set up
	 */
	public static ConfigStore load(List<String> files) throws BootstrapException {
		ConfigStore config = new ConfigStore(() -> Config.load(files));
		read(config);
		try {
			log.info("Registering crypto libraries");
			BouncyCastleLoader.registerBouncyCastle(config.current());
		}
		catch (Exception e) {
			throw new BootstrapException(String.valueOf(e.getMessage()), e);
		}
		config.setCatalog(NetshotSettings.catalog());
		read(config);
		config.addListener(LoggingSetup::apply);
		config.catalog().warnUnknownItems(config.current());
		log.info("Checking current JVM.");
		checkJvm();
		log.info("Selecting truststore.");
		Truststore.apply(config.current());
		return config;
	}

	/**
	 * Loads the configuration into the store from its source, and sets up the logging from it.
	 *
	 * @param config the configuration store
	 * @throws BootstrapException if the configuration can't be read (the message tells why) or the logging can't be
	 *     set up
	 */
	private static void read(ConfigStore config) throws BootstrapException {
		try {
			config.load();
		}
		catch (ConfigException e) {
			throw new BootstrapException("Unable to read the configuration: " + e.getMessage());
		}
		if (!LoggingSetup.apply(config.current())) {
			throw new BootstrapException("Unable to set up the logging.");
		}
	}

	/**
	 * Checks the JVM, and logs an error if it's not GraalVM 25.
	 */
	private static void checkJvm() {
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
