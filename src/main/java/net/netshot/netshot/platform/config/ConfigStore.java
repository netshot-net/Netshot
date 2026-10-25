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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.MarkerFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Holds the current configuration snapshot, and replaces it as a whole on a reload.
 *
 * <p>A reload is all or nothing: the new configuration is loaded and checked (configuration file,
 * secret files of the known secret settings) first; if anything fails, the current configuration is kept.
 * Then the snapshot is swapped, the {@link #bind bindings} are refreshed and the listeners are called.</p>
 *
 * <p>Components read their settings through a binding ({@link #bind(Function)}), which maps the current
 * snapshot to an immutable settings object, recomputed when the snapshot changes.</p>
 */
@Slf4j
public final class ConfigStore {

	/** Loads a configuration. */
	@FunctionalInterface
	public interface Loader {
		/**
		 * Loads the configuration.
		 *
		 * @return the configuration
		 * @throws ConfigException if the configuration can't be loaded
		 */
		Config load() throws ConfigException;
	}

	/**
	 * A setting object mapped from the current configuration snapshot.
	 *
	 * @param <T> the type of the setting object
	 */
	public static final class Binding<T> implements Supplier<T> {

		private record Value<T>(Config source, T value) {
		}

		private final ConfigStore store;
		private final Function<Config, T> mapper;
		private volatile Value<T> value;

		private Binding(ConfigStore store, Function<Config, T> mapper) {
			this.store = store;
			this.mapper = mapper;
		}

		@Override
		public T get() {
			Config current = this.store.current();
			Value<T> cached = this.value;
			if (cached == null || cached.source() != current) {
				cached = new Value<>(current, this.mapper.apply(current));
				this.value = cached;
			}
			return cached.value();
		}
	}

	/** The current configuration. */
	private volatile Config current;

	/** The known settings, to check a new configuration against. */
	private volatile SettingCatalog catalog = SettingCatalog.EMPTY;

	/** The bindings, refreshed when the configuration changes (weak: they belong to their owners). */
	private final Set<Binding<?>> bindings = Collections.newSetFromMap(new WeakHashMap<>());

	/** The listeners, called when the configuration changes. */
	private final List<Consumer<Config>> listeners = new CopyOnWriteArrayList<>();

	/**
	 * Creates a store.
	 *
	 * @param initial the initial configuration
	 */
	public ConfigStore(Config initial) {
		this.current = Objects.requireNonNull(initial);
	}

	/**
	 * Gets the current configuration snapshot.
	 *
	 * @return the current configuration
	 */
	public Config current() {
		return this.current;
	}

	/**
	 * Sets the known settings: a new configuration is checked against them (secret files,
	 * changes of the settings which need a restart, unknown items).
	 *
	 * @param newCatalog the known settings
	 */
	public void setCatalog(SettingCatalog newCatalog) {
		this.catalog = Objects.requireNonNull(newCatalog);
	}

	/**
	 * Gets the known settings.
	 *
	 * @return the known settings
	 */
	public SettingCatalog catalog() {
		return this.catalog;
	}

	/**
	 * Loads the initial configuration (at startup): it replaces the current one if it can be loaded.
	 *
	 * @param loader the loader
	 * @return true if the configuration was loaded
	 */
	public boolean load(Loader loader) {
		Config config;
		try {
			config = this.check(loader.load());
		}
		catch (ConfigException e) {
			log.error(MarkerFactory.getMarker("FATAL"), "Unable to load the configuration: {}", e.getMessage(), e);
			return false;
		}
		this.catalog.warnUnknownItems(config);
		this.replace(config);
		return true;
	}

	/**
	 * Reloads the configuration (HUP signal): on success, the new configuration replaces the current one,
	 * otherwise the current one is kept.
	 *
	 * @param loader the loader
	 * @return true if the configuration was reloaded
	 */
	public boolean reload(Loader loader) {
		Config config;
		try {
			config = this.check(loader.load());
		}
		catch (ConfigException e) {
			log.error("Unable to reload the configuration, the current configuration is kept: {}", e.getMessage(), e);
			return false;
		}
		this.catalog.warnUnknownItems(config);
		this.catalog.warnRestartChanges(this.current, config);
		this.replace(config);
		log.warn("The configuration was reloaded.");
		return true;
	}

	private Config check(Config config) throws ConfigException {
		config.checkSecrets(this.catalog.secretKeys(config));
		return config;
	}

	/**
	 * Replaces the current configuration, without any check (tests), then refreshes the bindings
	 * and calls the listeners.
	 *
	 * @param config the new configuration
	 */
	public void replace(Config config) {
		this.current = Objects.requireNonNull(config);
		List<Binding<?>> toRefresh;
		synchronized (this.bindings) {
			toRefresh = new ArrayList<>(this.bindings);
		}
		for (Binding<?> binding : toRefresh) {
			try {
				binding.get();
			}
			catch (RuntimeException e) {
				log.error("Error while applying the new configuration", e);
			}
		}
		for (Consumer<Config> listener : this.listeners) {
			try {
				listener.accept(config);
			}
			catch (RuntimeException e) {
				log.error("Error while applying the new configuration", e);
			}
		}
	}

	/**
	 * Binds a settings object to the configuration: the returned supplier gives the object mapped from the current
	 * snapshot, mapped again (once) after each change of the configuration.
	 *
	 * @param <T> the type of the settings object
	 * @param mapper maps a configuration snapshot to the settings object (logs the invalid values)
	 * @return the binding
	 */
	public <T> Binding<T> bind(Function<Config, T> mapper) {
		Binding<T> binding = new Binding<>(this, mapper);
		synchronized (this.bindings) {
			this.bindings.add(binding);
		}
		return binding;
	}

	/**
	 * Adds a listener, called with the new configuration after each change (after the bindings are refreshed).
	 * For the components which must act on a change (restart a listener, rebuild a client...).
	 *
	 * @param listener the listener
	 */
	public void addListener(Consumer<Config> listener) {
		this.listeners.add(listener);
	}

	/**
	 * Removes a listener.
	 *
	 * @param listener the listener
	 */
	public void removeListener(Consumer<Config> listener) {
		this.listeners.remove(listener);
	}
}
