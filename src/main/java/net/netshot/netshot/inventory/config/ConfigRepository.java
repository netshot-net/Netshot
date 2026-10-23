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
package net.netshot.netshot.inventory.config;

import static net.netshot.netshot.platform.database.schema.Tables.CONFIG;
import static net.netshot.netshot.platform.database.schema.Tables.CONFIG_ATTRIBUTE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP_CACHED_DEVICES;
import static net.netshot.netshot.platform.database.schema.Tables.LONG_TEXT_CONFIGURATION;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigNumericAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.platform.database.Page;

/**
 * The queries on configurations. The {@link DSLContext} is always the first parameter: the caller
 * (the service) owns the transaction.
 *
 * <p>A configuration is loaded with its attributes, with or without the long texts (a
 * {@link ConfigLongTextAttribute} loaded without its text has a null text).</p>
 */
public class ConfigRepository {

	/** The discriminators of the configuration attributes. */
	private static final String TEXT = "S";
	private static final String NUMERIC = "N";
	private static final String BINARY = "B";
	private static final String LONG_TEXT = "T";
	private static final String BINARY_FILE = "F";

	private static final Field<List<ConfigAttribute>> ATTRIBUTES = attributes(false);

	private static final Field<List<ConfigAttribute>> ATTRIBUTES_WITH_TEXTS = attributes(true);

	private static Field<List<ConfigAttribute>> attributes(boolean withTexts) {
		Field<String> longText = withTexts ? LONG_TEXT_CONFIGURATION.TEXT : DSL.inline(null, String.class).as("long_text_value");
		List<SelectField<?>> fields = List.of(CONFIG_ATTRIBUTE.TYPE, CONFIG_ATTRIBUTE.NAME, CONFIG_ATTRIBUTE.NUMBER,
			CONFIG_ATTRIBUTE.TEXT, CONFIG_ATTRIBUTE.ASSUMPTION, longText, CONFIG_ATTRIBUTE.UID, CONFIG_ATTRIBUTE.ORIGINAL_NAME,
			CONFIG_ATTRIBUTE.FILE_SIZE, CONFIG_ATTRIBUTE.CHECKSUM);
		SelectJoinStep<Record> select = DSL.select(fields).from(CONFIG_ATTRIBUTE);
		if (withTexts) {
			select = select.leftJoin(LONG_TEXT_CONFIGURATION).on(LONG_TEXT_CONFIGURATION.ID.eq(CONFIG_ATTRIBUTE.LONG_TEXT));
		}
		return DSL.multiset(select.where(CONFIG_ATTRIBUTE.CONFIG.eq(CONFIG.ID)).orderBy(CONFIG_ATTRIBUTE.ID))
			.convertFrom(r -> r.map(a -> toAttribute(a, longText)))
			.as(withTexts ? "attributes_with_texts" : "attributes");
	}

	private static ConfigAttribute toAttribute(Record r, Field<String> longText) {
		String type = r.get(CONFIG_ATTRIBUTE.TYPE);
		String name = r.get(CONFIG_ATTRIBUTE.NAME);
		return switch (type) {
			case TEXT -> new ConfigTextAttribute(name, r.get(CONFIG_ATTRIBUTE.TEXT));
			case NUMERIC -> new ConfigNumericAttribute(name, r.get(CONFIG_ATTRIBUTE.NUMBER));
			case BINARY -> new ConfigBinaryAttribute(name, r.get(CONFIG_ATTRIBUTE.ASSUMPTION));
			case LONG_TEXT -> new ConfigLongTextAttribute(name, r.get(longText));
			case BINARY_FILE -> new ConfigBinaryFileAttribute(name, r.get(CONFIG_ATTRIBUTE.UID), r.get(CONFIG_ATTRIBUTE.ORIGINAL_NAME),
				r.get(CONFIG_ATTRIBUTE.FILE_SIZE) == null ? 0 : r.get(CONFIG_ATTRIBUTE.FILE_SIZE), r.get(CONFIG_ATTRIBUTE.CHECKSUM));
			default -> throw new IllegalStateException("Unknown type of configuration attribute '%s'".formatted(type));
		};
	}

	private static Config toConfig(Record r, Field<List<ConfigAttribute>> attributes) {
		return new Config(r.get(CONFIG.ID), r.get(CONFIG.VERSION), r.get(CONFIG.CHANGE_DATE), r.get(CONFIG.DEVICE),
			r.get(CONFIG.AUTHOR), r.get(CONFIG.CUSTOM_HASH), r.get(attributes));
	}

	private static List<Config> findWhere(DSLContext dsl, Condition condition, boolean withTexts, Page page, boolean latestFirst) {
		Field<List<ConfigAttribute>> attributes = withTexts ? ATTRIBUTES_WITH_TEXTS : ATTRIBUTES;
		var select = dsl.select(CONFIG.ID, CONFIG.VERSION, CONFIG.CHANGE_DATE, CONFIG.DEVICE, CONFIG.AUTHOR, CONFIG.CUSTOM_HASH, attributes)
			.from(CONFIG)
			.where(condition);
		if (latestFirst) {
			return page.apply(select.orderBy(CONFIG.CHANGE_DATE.desc(), CONFIG.ID.desc())).fetch(r -> toConfig(r, attributes));
		}
		return page.apply(select.orderBy(CONFIG.ID)).fetch(r -> toConfig(r, attributes));
	}

	/**
	 * Finds a configuration.
	 *
	 * @param dsl the jOOQ context
	 * @param id the configuration ID
	 * @param withTexts whether to load the long texts
	 * @return the configuration, or null if there is no such configuration
	 */
	public Config find(DSLContext dsl, long id, boolean withTexts) {
		List<Config> configs = findWhere(dsl, CONFIG.ID.eq(id), withTexts, Page.ALL, false);
		return configs.isEmpty() ? null : configs.get(0);
	}

	/**
	 * Finds the configurations of a device, the most recent first (without their long texts).
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @param page the window on the result
	 * @return the configurations
	 */
	public List<Config> findByDevice(DSLContext dsl, long deviceId, Page page) {
		List<Config> configs = findWhere(dsl, CONFIG.DEVICE.eq(deviceId), false, page, true);
		if (page.isRestricted()) {
			// Quirk kept: Hibernate paginated this query (with a collection fetch) in memory, answering
			// a sub list, which XML publishes as <SubList> instead of <ArrayList>
			return configs.subList(0, configs.size());
		}
		return configs;
	}

	/**
	 * Finds the configuration gathered just before another one on the same device.
	 *
	 * @param dsl the jOOQ context
	 * @param config the configuration
	 * @param withTexts whether to load the long texts
	 * @return the previous configuration, or null if there is none
	 */
	public Config findPrevious(DSLContext dsl, Config config, boolean withTexts) {
		List<Config> configs = findWhere(dsl, CONFIG.DEVICE.eq(config.deviceId()).and(CONFIG.CHANGE_DATE.lt(config.changeDate())),
			withTexts, new Page(null, 1), true);
		return configs.isEmpty() ? null : configs.get(0);
	}

	/**
	 * Lists the configuration changes in their summary form. A configuration is listed once per
	 * group (among the given ones) its device belongs to.
	 *
	 * @param dsl the jOOQ context
	 * @param filter the criteria
	 * @param page the window on the result
	 * @return the configuration summaries
	 */
	public List<ConfigSummary> findSummaries(DSLContext dsl, ConfigFilter filter, Page page) {
		Condition condition = DSL.trueCondition();
		if (!filter.domainIds().isEmpty()) {
			condition = condition.and(DEVICE.MGMT_DOMAIN.in(filter.domainIds()));
		}
		if (!filter.groupIds().isEmpty()) {
			condition = condition.and(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.in(filter.groupIds()));
		}
		if (filter.after() != null) {
			condition = condition.and(CONFIG.CHANGE_DATE.ge(filter.after().toInstant()));
		}
		if (filter.before() != null) {
			condition = condition.and(CONFIG.CHANGE_DATE.lt(filter.before().toInstant()));
		}
		var from = dsl.select(CONFIG.ID, DEVICE.NAME, DEVICE.ID, CONFIG.CHANGE_DATE, CONFIG.AUTHOR, DEVICE.NETWORK_CLASS)
			.from(CONFIG)
			.join(DEVICE).on(DEVICE.ID.eq(CONFIG.DEVICE));
		var query = filter.groupIds().isEmpty() ? from.where(condition)
			: from.join(DEVICE_GROUP_CACHED_DEVICES).on(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(DEVICE.ID)).where(condition);
		return page.apply(query).fetch(r -> new ConfigSummary(r.get(CONFIG.ID), r.get(DEVICE.NAME), r.get(DEVICE.ID),
			r.get(CONFIG.CHANGE_DATE) == null ? null : Date.from(r.get(CONFIG.CHANGE_DATE)), r.get(CONFIG.AUTHOR),
			r.get(DEVICE.NETWORK_CLASS) == null ? null : Device.NetworkClass.valueOf(r.get(DEVICE.NETWORK_CLASS))));
	}

	/**
	 * Stores a new configuration, with its attributes (and their long texts). Its change date is
	 * now (to the second) if it has none.
	 *
	 * @param dsl the jOOQ context
	 * @param config the configuration (its ID and version are ignored)
	 * @return the ID of the stored configuration
	 */
	public long add(DSLContext dsl, Config config) {
		Instant changeDate = config.changeDate() == null ? Instant.now().truncatedTo(ChronoUnit.SECONDS) : config.changeDate();
		long id = dsl.insertInto(CONFIG)
			.set(CONFIG.VERSION, 0)
			.set(CONFIG.DEVICE, config.deviceId())
			.set(CONFIG.CHANGE_DATE, changeDate)
			.set(CONFIG.AUTHOR, config.author())
			.set(CONFIG.CUSTOM_HASH, config.customHash())
			.returning(CONFIG.ID)
			.fetchOne(CONFIG.ID);
		for (ConfigAttribute attribute : config.attributes()) {
			var insert = dsl.insertInto(CONFIG_ATTRIBUTE)
				.set(CONFIG_ATTRIBUTE.CONFIG, id)
				.set(CONFIG_ATTRIBUTE.NAME, attribute.name());
			switch (attribute) {
				case ConfigTextAttribute a -> insert.set(CONFIG_ATTRIBUTE.TYPE, TEXT).set(CONFIG_ATTRIBUTE.TEXT, a.text()).execute();
				case ConfigNumericAttribute a -> insert.set(CONFIG_ATTRIBUTE.TYPE, NUMERIC).set(CONFIG_ATTRIBUTE.NUMBER, a.number()).execute();
				case ConfigBinaryAttribute a -> insert.set(CONFIG_ATTRIBUTE.TYPE, BINARY).set(CONFIG_ATTRIBUTE.ASSUMPTION, a.assumption()).execute();
				case ConfigLongTextAttribute a -> insert.set(CONFIG_ATTRIBUTE.TYPE, LONG_TEXT)
					.set(CONFIG_ATTRIBUTE.LONG_TEXT, DeviceRepository.insertLongText(dsl, a.text())).execute();
				case ConfigBinaryFileAttribute a -> insert.set(CONFIG_ATTRIBUTE.TYPE, BINARY_FILE)
					.set(CONFIG_ATTRIBUTE.UID, a.uid())
					.set(CONFIG_ATTRIBUTE.ORIGINAL_NAME, a.originalName())
					.set(CONFIG_ATTRIBUTE.FILE_SIZE, a.fileSize())
					.set(CONFIG_ATTRIBUTE.CHECKSUM, a.checksum())
					.execute();
			}
		}
		return id;
	}
}
