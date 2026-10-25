/**
 * Copyright 2013-2025 Netshot
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
package net.netshot.netshot.platform.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * An immutable snapshot of the Netshot configuration: the items of the configuration file,
 * overridden by the environment variables (item {@code netshot.log.file} = variable {@code NETSHOT_LOG_FILE}).
 *
 * <p>A secret item can be read from a file instead (see {@link #getSecret(String)}); a secret is read once
 * per snapshot, so a new snapshot (configuration reload) reads the secret files again.</p>
 *
 * <p>The typed getters log an error and return the default value when the configured value is invalid.</p>
 */
@Slf4j
public final class Config {

	/** Max size of a secret file (see {@link #getSecret(String)}). */
	private static final long MAX_SECRET_FILE_SIZE = 64 * 1024;

	/** The items of the configuration file. */
	private final Map<String, String> file;

	/** The environment variables. */
	private final Map<String, String> environment;

	/** The secret items already read (empty if not set). */
	private final Map<String, Optional<String>> secrets = new ConcurrentHashMap<>();

	private Config(Map<String, String> file, Map<String, String> environment) {
		this.file = Collections.unmodifiableMap(new TreeMap<>(file));
		this.environment = Map.copyOf(environment);
	}

	/**
	 * Creates a configuration from properties, overridden by the environment of the process.
	 *
	 * @param properties the configuration items
	 * @return the configuration
	 */
	public static Config of(Properties properties) {
		return of(properties, processEnvironment());
	}

	/**
	 * Creates a configuration from properties, overridden by the given environment.
	 *
	 * @param properties the configuration items
	 * @param environment the environment variables
	 * @return the configuration
	 */
	public static Config of(Properties properties, Map<String, String> environment) {
		Map<String, String> items = new TreeMap<>();
		for (String name : properties.stringPropertyNames()) {
			items.put(name, properties.getProperty(name));
		}
		return new Config(items, environment);
	}

	/**
	 * Creates an empty configuration (no file), overridden by the environment of the process.
	 *
	 * @return the configuration
	 */
	public static Config empty() {
		return of(new Properties());
	}

	/**
	 * Loads the configuration from the first readable file among the given ones.
	 *
	 * @param fileNames the names of the files to try, in sequence
	 * @return the configuration
	 * @throws ConfigException if no file could be read, or the file is empty
	 */
	public static Config load(List<String> fileNames) throws ConfigException {
		Properties properties = new Properties();
		for (String fileName : fileNames) {
			try (InputStream fileStream = new FileInputStream(fileName)) {
				log.trace("Trying to load the configuration file {}.", fileName);
				properties.load(fileStream);
				log.warn("Configuration file {} successfully read.", fileName);
				break;
			}
			catch (Exception e) {
				log.warn("Unable to read the configuration file {}.", fileName);
			}
		}
		if (properties.isEmpty()) {
			throw new ConfigException("No configuration file was found (tried %s)".formatted(String.join(", ", fileNames)));
		}
		return of(properties);
	}

	private static Map<String, String> processEnvironment() {
		try {
			return System.getenv();
		}
		catch (SecurityException e) {
			log.warn("Security exception raised while reading the environment variables", e);
			return Map.of();
		}
	}

	/**
	 * Gets the name of the environment variable overriding a configuration item.
	 *
	 * @param key the key of the item, e.g. {@code netshot.log.file}
	 * @return the name of the environment variable, e.g. {@code NETSHOT_LOG_FILE}
	 */
	public static String environmentName(String key) {
		return key.replace(".", "_").toUpperCase();
	}

	/**
	 * Gets the keys of the items of the configuration file (not the environment variables).
	 *
	 * @return the keys, sorted
	 */
	public Set<String> fileKeys() {
		return this.file.keySet();
	}

	/**
	 * Gets the names of the environment variables starting with the given prefix.
	 *
	 * @param prefix the prefix, e.g. {@code NETSHOT_}
	 * @return the names, sorted
	 */
	public Set<String> environmentNames(String prefix) {
		Set<String> names = new TreeSet<>();
		for (String name : this.environment.keySet()) {
			if (name.startsWith(prefix)) {
				names.add(name);
			}
		}
		return names;
	}

	/**
	 * Gets the configuration items as properties (the file items overridden by the environment variables
	 * which match one of them).
	 *
	 * @return a new properties object
	 */
	public Properties toProperties() {
		Properties properties = new Properties();
		for (String key : this.file.keySet()) {
			properties.setProperty(key, this.get(key));
		}
		return properties;
	}

	/**
	 * Gets an item.
	 *
	 * @param key the key
	 * @return the value (from the environment, then from the file), null if not set
	 */
	public String get(String key) {
		String value = this.environment.get(environmentName(key));
		if (value != null) {
			return value;
		}
		return this.file.get(key);
	}

	/**
	 * Gets an item, from the environment variables only.
	 *
	 * @param key the key
	 * @return the value, null if not set as an environment variable
	 */
	String getFromEnvironment(String key) {
		return this.environment.get(environmentName(key));
	}

	/**
	 * Gets an item, from the configuration file only.
	 *
	 * @param key the key
	 * @return the value, null if not set in the file
	 */
	String getFromFile(String key) {
		return this.file.get(key);
	}

	/**
	 * Gets an item.
	 *
	 * @param key the key
	 * @param defaultValue the default value
	 * @return the value, or the default value if not set
	 */
	public String get(String key, String defaultValue) {
		String value = this.get(key);
		return value == null ? defaultValue : value;
	}

	/**
	 * Gets an item as a boolean ({@code true} or {@code false}).
	 *
	 * @param key the key
	 * @param defaultValue the default value
	 * @return the value, or the default value if not set or invalid
	 */
	public boolean getBoolean(String key, boolean defaultValue) {
		String value = this.get(key);
		if ("false".equals(value)) {
			return false;
		}
		if ("true".equals(value)) {
			return true;
		}
		if (value != null) {
			log.error("Unable to parse the boolean value of configuration item '{}', using default value {}.",
				key, defaultValue);
		}
		return defaultValue;
	}

	/**
	 * Gets an item as an integer.
	 *
	 * @param key the key
	 * @param defaultValue the default value
	 * @return the value, or the default value if not set or invalid
	 */
	public int getInt(String key, int defaultValue) {
		return this.getInt(key, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	/**
	 * Gets an item as an integer, within a range.
	 *
	 * @param key the key
	 * @param defaultValue the default value
	 * @param min the minimum acceptable value
	 * @param max the maximum acceptable value
	 * @return the value, or the default value if not set, invalid or out of the range
	 */
	public int getInt(String key, int defaultValue, int min, int max) {
		String textValue = this.get(key);
		if (textValue == null) {
			return defaultValue;
		}
		int value;
		try {
			value = Integer.parseInt(textValue);
		}
		catch (NumberFormatException e) {
			log.error("Unable to parse the integer value of configuration item '{}', using default value {}.",
				key, defaultValue);
			return defaultValue;
		}
		if (value < min || value > max) {
			log.error("Unacceptable integer value for configuration item '{}' (not in the range {} to {}), using default value {}",
				key, min, max, defaultValue);
			return defaultValue;
		}
		return value;
	}

	/**
	 * Gets an item as a long integer.
	 *
	 * @param key the key
	 * @param defaultValue the default value
	 * @return the value, or the default value if not set or invalid
	 */
	public long getLong(String key, long defaultValue) {
		String textValue = this.get(key);
		if (textValue == null) {
			return defaultValue;
		}
		try {
			return Long.parseLong(textValue);
		}
		catch (NumberFormatException e) {
			log.error("Unable to parse the long value of configuration item '{}', using default value {}.",
				key, defaultValue);
			return defaultValue;
		}
	}

	/**
	 * Gets a typed setting.
	 *
	 * @param <T> the type of the value
	 * @param setting the setting
	 * @return the value, or the default value of the setting if not set or invalid
	 */
	public <T> T get(Setting<T> setting) {
		return setting.read(this);
	}

	/**
	 * Gets a secret item, see {@link #getSecret(String)}.
	 *
	 * @param key the key
	 * @param defaultValue the default value
	 * @return the value, or the default value if not set
	 */
	public String getSecret(String key, String defaultValue) {
		String value = this.getSecret(key);
		return value == null ? defaultValue : value;
	}

	/**
	 * Gets a secret item. If the {@code <key>.file} item is set (e.g. via the
	 * {@code NETSHOT_DB_PASSWORD_FILE} environment variable), the secret is read from that file;
	 * otherwise, it is read as a normal (inline) item.
	 * The value is read once for this configuration snapshot.
	 *
	 * @param key the key
	 * @return the value, null if not set
	 * @throws IllegalStateException if the secret file is set but can't be read, is empty or too big
	 */
	public String getSecret(String key) {
		return this.secrets.computeIfAbsent(key, k -> Optional.ofNullable(this.readSecret(k))).orElse(null);
	}

	/**
	 * Checks that the secret files of the given secret items, when set, can be read (reading them
	 * once for this snapshot).
	 *
	 * @param keys the keys of the secret items
	 * @throws ConfigException if a secret file is set but can't be read, is empty or too big
	 */
	public void checkSecrets(Iterable<String> keys) throws ConfigException {
		for (String key : keys) {
			try {
				this.getSecret(key);
			}
			catch (IllegalStateException e) {
				throw new ConfigException(e.getMessage(), e.getCause());
			}
		}
	}

	private String readSecret(String key) {
		String fileKey = key + ".file";
		String fileName = this.get(fileKey);
		if (fileName == null) {
			return this.get(key);
		}
		if (this.getFromEnvironment(key) != null) {
			// Likely a leftover, and the secret is still exposed (e.g. to user scripts) through the environment
			log.warn("Both '{}' and environment variable {} are set: the environment variable is ignored, "
				+ "it should be removed.", fileKey, environmentName(key));
		}
		else if (this.getFromFile(key) != null) {
			log.info("Both configuration items '{}' and '{}' are set, the inline value is ignored.", fileKey, key);
		}
		String value;
		try {
			Path path = Path.of(fileName);
			if (Files.size(path) > MAX_SECRET_FILE_SIZE) {
				throw new IllegalStateException("The secret file '%s' for configuration item '%s' is too big (more than %d bytes)"
					.formatted(fileName, key, MAX_SECRET_FILE_SIZE));
			}
			value = Files.readString(path, StandardCharsets.UTF_8);
		}
		catch (IOException | InvalidPathException e) {
			throw new IllegalStateException("Unable to read the secret file '%s' for configuration item '%s'"
				.formatted(fileName, key), e);
		}
		// Ignore the trailing line break(s), but keep any other whitespace
		value = value.replaceFirst("[\\r\\n]+\\z", "");
		if (value.isEmpty()) {
			throw new IllegalStateException("The secret file '%s' for configuration item '%s' is empty"
				.formatted(fileName, key));
		}
		log.info("Configuration item '{}' was read from secret file {}.", key, fileName);
		return value;
	}
}
