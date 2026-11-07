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

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.Provider;
import java.security.Security;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.util.security.SecurityUtils;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.Setting;

/**
 * Helper to load BouncyCastle library (JAR files).
 * BC provider JAR is signed, and its signature is verified by the JVM
 * when using crypto features that it provides.
 * This is not compatible with JAR shading where the libraries are
 * re-packaged all together, thus losing the signature.
 * This class here extracts the BC JARs from uber-JAR into temporary
 * files and loads them using Java instrumentation.
 */
@Slf4j
public final class BouncyCastleLoader {

	/** Whether the additional crypto libraries are loaded. */
	static final Setting<Boolean> LOAD = Setting.bool("netshot.cryptolibs.load").defaultValue(true).restart();

	/** The folder to extract the crypto libraries to (default: the system temp folder). */
	static final Setting<String> TMP_PATH = Setting.text("netshot.cryptolibs.tmppath").restart();

	/**
	 * Private constructor to hide utility class constructor.
	 */
	private BouncyCastleLoader() {
		// Utility class
	}

	/** Folder in main JAR where to find the BC JARs to load. */
	private static final String RESOURCE_PATH = "bc/";

	/** BC JCE Provider class (to register). */
	private static final String PROVIDER_CLASS =
		"org.bouncycastle.jce.provider.BouncyCastleProvider";

	/**
	 * Extract a single JAR from inside the uber-jar to a temporary file,
	 * and return its URL.
	 *
	 * @param jar the JAR file
	 * @param je the JAR entry
	 * @throws Exception in case of error
	 */
	private static void loadJar(JarFile jar, JarEntry je) throws Exception {
		log.debug("Extracting embedded JAR {}", je.getName());

		String tmpPath = Netshot.config().current().get(TMP_PATH);

		try (InputStream in = jar.getInputStream(je)) {
			String baseName = new File(je.getName()).getName();
			String prefix = "netshot_"
					+ baseName
							.replace(".jar", "")
							.replaceAll("\\W+", "-");
			String suffix = ".jar";
			Path tempFile = tmpPath == null
					? Files.createTempFile(prefix, suffix)
					: Files.createTempFile(Path.of(tmpPath), prefix, suffix);
			log.trace("Copying {} into {}", je.getName(), tempFile);
			tempFile.toFile().deleteOnExit();
			Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);

			Netshot.appendJar(new JarFile(tempFile.toFile()));
		}
	}

	/**
	 * Extract all JARs from the RESOURCE_PATH inside the uber-jar.
	 *
	 * @throws Exception in case of error
	 */
	private static void loadAllJars() throws Exception {
		// Locate the running uber-jar
		CodeSource codeSource = Netshot.class.getProtectionDomain().getCodeSource();
		if (codeSource == null) {
			throw new IllegalStateException("Cannot determine location of running JAR");
		}
		File jarFile = new File(codeSource.getLocation().toURI());

		try (JarFile jar = new JarFile(jarFile)) {
			Enumeration<JarEntry> entries = jar.entries();

			while (entries.hasMoreElements()) {
				JarEntry je = entries.nextElement();
				String name = je.getName();

				if (!je.isDirectory()
						&& name.startsWith(RESOURCE_PATH)
						&& name.endsWith(".jar")) {
					log.debug("Found embedded BC JAR: {}", name);
					loadJar(jar, je);
				}
			}
		}
	}

	/**
	 * Force eager initialization of crypto algorithms to prevent race conditions.
	 */
	private static void loadMainAlgos() {
		try {
			SecurityUtils.getKeyPairResourceParser();
		}
		catch (Exception e) {
			log.warn("Error getting key pair resource parser during crypto warmup", e);
		}

		// Warm up key pair generators
		final String[] keyTypes = new String[] {
			"EdDSA",  // For curve25519-sha256@libssh.org and ssh-ed25519
			"EC",     // For ECDSA variants
			"RSA",    // For ssh-rsa
		};
		for (String keyType : keyTypes) {
			try {
				log.trace("Warming up key pair generator: {}", keyType);
				SecurityUtils.getKeyPairGenerator(keyType);
			}
			catch (Exception e) {
				log.warn("Error during key pair generator warmup, {} algorithm might not be available: {}",
					keyType, e.getMessage());
			}
		}

		// Warm up KEX (Key Exchange) algorithms
		final BuiltinDHFactories[] kexTypes = new BuiltinDHFactories[] {
			BuiltinDHFactories.curve25519,       // For curve25519-sha256
			BuiltinDHFactories.curve25519_libssh, // For curve25519-sha256@libssh.org
			BuiltinDHFactories.curve448,         // For curve448-sha512
		};
		for (BuiltinDHFactories kexType : kexTypes) {
			try {
				log.trace("Warming up KEX algorithm: {}", kexType.getName());
				// Force the DHFactory to check if it's supported (triggers internal initialization)
				if (kexType.isSupported()) {
					kexType.create();
				}
			}
			catch (Exception e) {
				log.trace("KEX algorithm {} not available: {}", kexType.getName(), e.getMessage());
			}
		}

		// Warm up ciphers
		final String[] cipherTypes = new String[] {
			"ChaCha20-Poly1305",  // For chacha20-poly1305@openssh.com
			"AES/GCM/NoPadding",  // For aes*-gcm@openssh.com
			"AES/CTR/NoPadding",  // For aes*-ctr
			"AES/CBC/NoPadding",  // For aes*-cbc
		};
		for (String cipherType : cipherTypes) {
			try {
				log.trace("Warming up cipher: {}", cipherType);
				SecurityUtils.getCipher(cipherType);
			}
			catch (Exception e) {
				log.trace("Cipher {} not available: {}", cipherType, e.getMessage());
			}
		}

		log.debug("Crypto algorithms warmup complete");
	}

	/**
	 * Try to register BC assuming it can be found in the classpath.
	 *
	 * @param source the source description
	 * @return true if registering was successful
	 * @throws Exception in case of error
	 */
	private static boolean tryRegister(String source) throws Exception {
		try {
			Class<?> clazz = Class.forName(PROVIDER_CLASS);
			Provider bcProvider = (Provider) clazz.getDeclaredConstructor().newInstance();
			Security.insertProviderAt(bcProvider, 1);
			log.info("Bouncy Castle provider registered ({}): {}", source, bcProvider.getName());
			BouncyCastleLoader.loadMainAlgos();
			return true;
		}
		catch (ClassNotFoundException ignore) {
			log.debug("Bouncy Castle not found ({}).", source);
		}
		return false;
	}

	/**
	 * Load BC jars if needed and register the Bouncy Castle provider.
	 *
	 * @throws Exception in case of error
	 */
	public static void registerBouncyCastle() throws Exception {
		// Check if BC classes are already on the classpath (dev mode)
		if (BouncyCastleLoader.tryRegister("native classpath")) {
			return;
		}

		if (!Netshot.config().current().get(LOAD)) {
			log.warn("Loading of additional crypto libraries is disabled by configuration.");
			return;
		}

		// Otherwise, extract jars and register
		BouncyCastleLoader.loadAllJars();
		if (BouncyCastleLoader.tryRegister("embedded JARs")) {
			return;
		}
		log.warn("Could not load BouncyCastle crypto library: "
				+ "some algorithms might not be available for protocols like SSH");
	}
}

