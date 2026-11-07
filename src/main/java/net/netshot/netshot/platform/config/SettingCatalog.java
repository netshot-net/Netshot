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
package net.netshot.netshot.platform.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import lombok.extern.slf4j.Slf4j;

/**
 * The known configuration settings, collected from the {@code static final} {@link Setting} fields
 * of the classes declaring them.
 *
 * <p>Used to check a configuration: the secret files to read, the items which don't match any setting
 * (likely typos), the changed settings which need a restart.</p>
 */
@Slf4j
public final class SettingCatalog {

	/** Empty catalog: nothing is checked. */
	public static final SettingCatalog EMPTY = new SettingCatalog(List.of());

	/** Prefix of the configuration items. */
	private static final String KEY_PREFIX = "netshot.";

	/** Suffix of the item giving the file of a secret. */
	private static final String FILE_SUFFIX = ".file";

	/** The settings. */
	private final List<Setting<?>> settings;

	private SettingCatalog(Collection<Setting<?>> settings) {
		this.settings = List.copyOf(settings);
	}

	/**
	 * Creates a catalog from settings.
	 *
	 * @param settings the settings
	 * @return the catalog
	 */
	public static SettingCatalog of(Collection<Setting<?>> settings) {
		return new SettingCatalog(settings);
	}

	/**
	 * Creates a catalog from the {@code static final} {@link Setting} fields of the given classes
	 * (and of their nested classes).
	 *
	 * @param classes the classes declaring the settings
	 * @return the catalog
	 */
	public static SettingCatalog fromClasses(Collection<Class<?>> classes) {
		List<Setting<?>> settings = new ArrayList<>();
		for (Class<?> declaring : classes) {
			settings.addAll(declaredSettings(declaring));
		}
		return new SettingCatalog(settings);
	}

	/**
	 * Gets the settings declared by the {@code static final} {@link Setting} fields of a class
	 * and of its nested classes.
	 *
	 * @param declaring the class
	 * @return the settings
	 */
	public static List<Setting<?>> declaredSettings(Class<?> declaring) {
		List<Setting<?>> settings = new ArrayList<>();
		for (Class<?> nested : declaring.getDeclaredClasses()) {
			settings.addAll(declaredSettings(nested));
		}
		for (Field field : declaring.getDeclaredFields()) {
			int modifiers = field.getModifiers();
			if (Setting.class.equals(field.getType()) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
				try {
					field.setAccessible(true);
					settings.add((Setting<?>) Objects.requireNonNull(field.get(null)));
				}
				catch (IllegalAccessException e) {
					throw new IllegalStateException("Unable to read setting field %s".formatted(field), e);
				}
			}
		}
		return settings;
	}

	/**
	 * Gets the settings.
	 *
	 * @return the settings
	 */
	public List<Setting<?>> settings() {
		return this.settings;
	}

	/**
	 * Finds the setting matching a key.
	 *
	 * @param key the key
	 * @return the setting, null if none
	 */
	public Setting<?> find(String key) {
		for (Setting<?> setting : this.settings) {
			if (setting.matches(key)) {
				return setting;
			}
		}
		return null;
	}

	/**
	 * Converts the name of an environment variable to the key of a configuration item.
	 *
	 * @param name the name of the variable, e.g. {@code NETSHOT_LOG_FILE}
	 * @return the key, e.g. {@code netshot.log.file}
	 */
	private static String keyOfEnvironmentName(String name) {
		return name.toLowerCase().replace('_', '.');
	}

	/**
	 * Gets the keys of the configuration items set in a configuration (file and environment), with the
	 * keys of the secret files ({@code <key>.file}) as is.
	 *
	 * @param config the configuration
	 * @return the keys
	 */
	private static Set<String> configuredKeys(Config config) {
		Set<String> keys = new TreeSet<>(config.fileKeys());
		for (String name : config.environmentNames(Config.environmentName(KEY_PREFIX))) {
			keys.add(keyOfEnvironmentName(name));
		}
		return keys;
	}

	/**
	 * Gets the keys of the secret items of a configuration whose value comes from a secret file.
	 *
	 * @param config the configuration
	 * @return the keys of the secrets
	 */
	Set<String> secretKeys(Config config) {
		Set<String> keys = new TreeSet<>();
		for (String key : configuredKeys(config)) {
			if (key.endsWith(FILE_SUFFIX)) {
				String secretKey = key.substring(0, key.length() - FILE_SUFFIX.length());
				Setting<?> setting = this.find(secretKey);
				if (setting != null && setting.isSecret()) {
					keys.add(secretKey);
				}
			}
		}
		return keys;
	}

	/**
	 * Gets the keys of the items of a configuration which don't match any known setting.
	 *
	 * @param config the configuration
	 * @return the unknown keys
	 */
	public Set<String> unknownKeys(Config config) {
		Set<String> unknown = new TreeSet<>();
		if (this.settings.isEmpty()) {
			return unknown;
		}
		for (String key : configuredKeys(config)) {
			if (!key.startsWith(KEY_PREFIX) || this.find(key) != null) {
				continue;
			}
			if (key.endsWith(FILE_SUFFIX)) {
				Setting<?> setting = this.find(key.substring(0, key.length() - FILE_SUFFIX.length()));
				if (setting != null && setting.isSecret()) {
					continue;
				}
			}
			unknown.add(key);
		}
		return unknown;
	}

	/**
	 * Logs a warning for each item of the configuration which doesn't match any known setting.
	 *
	 * @param config the configuration
	 */
	public void warnUnknownItems(Config config) {
		for (String key : this.unknownKeys(config)) {
			log.warn("Unknown configuration item '{}' (or environment variable {}): it is ignored, check its name.",
				key, Config.environmentName(key));
		}
	}

	/**
	 * Gets the keys of the settings which need a restart and are changed between two configurations.
	 *
	 * @param previous the previous configuration
	 * @param next the new configuration
	 * @return the keys of the changed settings
	 */
	public Set<String> changedRestartKeys(Config previous, Config next) {
		Set<String> keys = new TreeSet<>(configuredKeys(previous));
		keys.addAll(configuredKeys(next));
		Set<String> changed = new TreeSet<>();
		for (String key : keys) {
			String settingKey = key;
			Setting<?> setting = this.find(key);
			if (setting == null && key.endsWith(FILE_SUFFIX)) {
				settingKey = key.substring(0, key.length() - FILE_SUFFIX.length());
				setting = this.find(settingKey);
			}
			if (setting != null && setting.reload() == Setting.Reload.RESTART
					&& !Objects.equals(previous.get(key), next.get(key))) {
				changed.add(settingKey);
			}
		}
		return changed;
	}

	/**
	 * Logs a warning for each setting which needs a restart and is changed between two configurations.
	 *
	 * @param previous the previous configuration
	 * @param next the new configuration
	 */
	void warnRestartChanges(Config previous, Config next) {
		for (String key : this.changedRestartKeys(previous, next)) {
			log.warn("Configuration item '{}' was changed: the change will only be applied at the next start of Netshot.", key);
		}
	}
}
