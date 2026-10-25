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
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * The declaration of a configuration setting: its key, type, default value, acceptable range, whether it's
 * a secret (which can be read from a file) and when a change is applied.
 *
 * <p>Declared once, in a {@code static final} field of the class using it, e.g.
 * {@code Setting.integer("netshot.syslog.port").defaultValue(514).range(1, 65535).restart()},
 * then read with {@link Config#get(Setting)}. An invalid value is logged and replaced by the default value.</p>
 *
 * <p>A template setting has a placeholder in its key: {@code N} for a number ({@code netshot.aaa.radiusN.ip})
 * or {@code <Name>} for any text ({@code netshot.log.class.<ClassName>}); {@link #at(Object)} gives the
 * actual setting.</p>
 *
 * @param <T> the type of the value
 */
@Slf4j
public final class Setting<T> {

	/** When a change of the setting is applied. */
	public enum Reload {
		/** On a configuration reload (HUP signal). */
		LIVE,
		/** At the next start of Netshot only. */
		RESTART,
	}

	/** Placeholder in the key of a template setting. */
	private static final Pattern PLACEHOLDER = Pattern.compile("(?<=[a-z0-9])N(?=[.a-z]|$)|<[A-Za-z]+>");

	/** The key. */
	private final String key;

	/** The name of the type, for the error messages. */
	private final String typeName;

	/** Parses the text value (throws IllegalArgumentException if invalid). */
	private final Function<String, T> parser;

	/** The default value (null for none). */
	private final T defaultValue;

	/** The minimum value (null for none). */
	private final Comparable<T> min;

	/** The maximum value (null for none). */
	private final Comparable<T> max;

	/** Whether the setting is a secret. */
	private final boolean secret;

	/** The legacy key, read if the setting isn't set (null for none). */
	private final String alias;

	/** When a change of the setting is applied. */
	private final Reload reload;

	private Setting(Builder<T> builder, String key, Reload reload) {
		this.key = key;
		this.typeName = builder.typeName;
		this.parser = builder.parser;
		this.defaultValue = builder.defaultValue;
		this.min = builder.min;
		this.max = builder.max;
		this.secret = builder.secret;
		this.alias = builder.alias;
		this.reload = reload;
	}

	/**
	 * Starts the declaration of a text setting.
	 *
	 * @param key the key
	 * @return the builder
	 */
	public static Builder<String> text(String key) {
		return new Builder<>(key, "text", Function.identity());
	}

	/**
	 * Starts the declaration of a boolean setting ({@code true} or {@code false}).
	 *
	 * @param key the key
	 * @return the builder
	 */
	public static Builder<Boolean> bool(String key) {
		return new Builder<>(key, "boolean", value -> {
			if ("true".equals(value)) {
				return true;
			}
			if ("false".equals(value)) {
				return false;
			}
			throw new IllegalArgumentException("Not a boolean");
		});
	}

	/**
	 * Starts the declaration of an integer setting.
	 *
	 * @param key the key
	 * @return the builder
	 */
	public static Builder<Integer> integer(String key) {
		return new Builder<>(key, "integer", Integer::valueOf);
	}

	/**
	 * Starts the declaration of a long integer setting.
	 *
	 * @param key the key
	 * @return the builder
	 */
	public static Builder<Long> longInteger(String key) {
		return new Builder<>(key, "long", Long::valueOf);
	}

	/**
	 * Starts the declaration of a list setting: the items are separated by the given pattern,
	 * empty items are ignored.
	 *
	 * @param key the key
	 * @param separator the separator, as a regular expression, e.g. {@code ", *"}
	 * @return the builder
	 */
	public static Builder<List<String>> list(String key, String separator) {
		Pattern pattern = Pattern.compile(separator);
		return new Builder<>(key, "list", value -> {
			List<String> items = new ArrayList<>();
			for (String item : pattern.split(value)) {
				if (!item.isEmpty()) {
					items.add(item);
				}
			}
			return List.copyOf(items);
		});
	}

	/**
	 * Starts the declaration of a setting of another type.
	 *
	 * @param <T> the type of the value
	 * @param key the key
	 * @param typeName the name of the type, for the error messages
	 * @param parser parses the text value, throws an IllegalArgumentException if the value is invalid
	 * @return the builder
	 */
	public static <T> Builder<T> of(String key, String typeName, Function<String, T> parser) {
		return new Builder<>(key, typeName, parser);
	}

	/**
	 * Gets the key.
	 *
	 * @return the key, e.g. {@code netshot.syslog.port}
	 */
	public String key() {
		return this.key;
	}

	/**
	 * Gets the legacy key, read when the setting isn't set.
	 *
	 * @return the legacy key, null if none
	 */
	public String alias() {
		return this.alias;
	}

	/**
	 * Gets the default value.
	 *
	 * @return the default value, null if none
	 */
	public T defaultValue() {
		return this.defaultValue;
	}

	/**
	 * Whether the setting is a secret, which can be read from a file (see {@link Config#getSecret(String)}).
	 *
	 * @return true for a secret
	 */
	public boolean isSecret() {
		return this.secret;
	}

	/**
	 * Gets when a change of the setting is applied.
	 *
	 * @return the reload mode
	 */
	public Reload reload() {
		return this.reload;
	}

	/**
	 * Whether this setting is a template (its key has a placeholder, see {@link #at(Object)}).
	 *
	 * @return true for a template
	 */
	public boolean isTemplate() {
		return PLACEHOLDER.matcher(this.key).find();
	}

	/**
	 * Gets the actual setting of a template, replacing the (first) placeholder of the key.
	 *
	 * @param value the value of the placeholder, e.g. {@code 2} for {@code netshot.aaa.radiusN.ip}
	 * @return the actual setting, e.g. {@code netshot.aaa.radius2.ip}
	 */
	public Setting<T> at(Object value) {
		Matcher matcher = PLACEHOLDER.matcher(this.key);
		if (!matcher.find()) {
			throw new IllegalStateException("Setting %s is not a template".formatted(this.key));
		}
		String actualKey = this.key.substring(0, matcher.start()) + value + this.key.substring(matcher.end());
		return new Setting<>(this.toBuilder(), actualKey, this.reload);
	}

	/**
	 * Whether the given key is the key of this setting (or of an actual setting of this template).
	 *
	 * @param candidate the key to check
	 * @return true if it matches
	 */
	public boolean matches(String candidate) {
		if (this.key.equals(candidate) || candidate.equals(this.alias)) {
			return true;
		}
		if (!this.isTemplate()) {
			return false;
		}
		StringBuilder regex = new StringBuilder();
		Matcher matcher = PLACEHOLDER.matcher(this.key);
		int last = 0;
		while (matcher.find()) {
			regex.append(Pattern.quote(this.key.substring(last, matcher.start())));
			regex.append(matcher.group().equals("N") ? "[0-9]+" : ".+");
			last = matcher.end();
		}
		regex.append(Pattern.quote(this.key.substring(last)));
		return candidate.matches(regex.toString());
	}

	/**
	 * Reads the setting from a configuration.
	 *
	 * @param config the configuration
	 * @return the value, or the default value if not set or invalid
	 */
	T read(Config config) {
		String textValue = this.readText(config, this.key);
		String readKey = this.key;
		if (textValue == null && this.alias != null) {
			textValue = this.readText(config, this.alias);
			readKey = this.alias;
		}
		if (textValue == null) {
			return this.defaultValue;
		}
		T value;
		try {
			value = this.parser.apply(textValue);
		}
		catch (IllegalArgumentException e) {
			log.error("Unable to parse the {} value of configuration item '{}', using default value {}.",
				this.typeName, readKey, this.defaultValue);
			return this.defaultValue;
		}
		if ((this.min != null && this.min.compareTo(value) > 0) || (this.max != null && this.max.compareTo(value) < 0)) {
			log.error("Unacceptable {} value for configuration item '{}' (not in the range {} to {}), using default value {}",
				this.typeName, readKey, this.min, this.max, this.defaultValue);
			return this.defaultValue;
		}
		return value;
	}

	/**
	 * Reads the text value of the setting, without parsing it (secret files are read).
	 *
	 * @param config the configuration
	 * @return the text value, null if not set
	 */
	String readText(Config config) {
		String textValue = this.readText(config, this.key);
		if (textValue == null && this.alias != null) {
			textValue = this.readText(config, this.alias);
		}
		return textValue;
	}

	private String readText(Config config, String readKey) {
		return this.secret ? config.getSecret(readKey) : config.get(readKey);
	}

	private Builder<T> toBuilder() {
		Builder<T> builder = new Builder<>(this.key, this.typeName, this.parser);
		builder.defaultValue = this.defaultValue;
		builder.min = this.min;
		builder.max = this.max;
		builder.secret = this.secret;
		builder.alias = this.alias;
		return builder;
	}

	@Override
	public String toString() {
		return this.key;
	}

	/**
	 * Builder of a setting, ended by {@link #live()} or {@link #restart()}.
	 *
	 * @param <T> the type of the value
	 */
	public static final class Builder<T> {

		private final String key;
		private final String typeName;
		private final Function<String, T> parser;
		private T defaultValue;
		private Comparable<T> min;
		private Comparable<T> max;
		private boolean secret;
		private String alias;

		private Builder(String key, String typeName, Function<String, T> parser) {
			this.key = key;
			this.typeName = typeName;
			this.parser = parser;
		}

		/**
		 * Sets the default value.
		 *
		 * @param value the default value
		 * @return this builder
		 */
		public Builder<T> defaultValue(T value) {
			this.defaultValue = value;
			return this;
		}

		/**
		 * Sets the acceptable range.
		 *
		 * @param <C> the type of the bounds
		 * @param minValue the minimum value
		 * @param maxValue the maximum value
		 * @return this builder
		 */
		public <C extends Comparable<T>> Builder<T> range(C minValue, C maxValue) {
			this.min = minValue;
			this.max = maxValue;
			return this;
		}

		/**
		 * Marks the setting as a secret, which can be read from a file (see {@link Config#getSecret(String)}).
		 *
		 * @return this builder
		 */
		public Builder<T> secret() {
			this.secret = true;
			return this;
		}

		/**
		 * Sets a legacy key, read when the setting isn't set.
		 *
		 * @param legacyKey the legacy key
		 * @return this builder
		 */
		public Builder<T> alias(String legacyKey) {
			this.alias = legacyKey;
			return this;
		}

		/**
		 * Ends the declaration of a setting applied on a configuration reload.
		 *
		 * @return the setting
		 */
		public Setting<T> live() {
			return new Setting<>(this, this.key, Reload.LIVE);
		}

		/**
		 * Ends the declaration of a setting applied at the next start only.
		 *
		 * @return the setting
		 */
		public Setting<T> restart() {
			return new Setting<>(this, this.key, Reload.RESTART);
		}
	}
}
