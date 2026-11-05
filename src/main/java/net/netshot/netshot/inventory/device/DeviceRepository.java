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
package net.netshot.netshot.inventory.device;

import static net.netshot.netshot.platform.database.schema.Tables.CHECK_RESULT;
import static net.netshot.netshot.platform.database.schema.Tables.CONFIG;
import static net.netshot.netshot.platform.database.schema.Tables.CONFIG_ATTRIBUTE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_ACCESS;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_ATTRIBUTE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP_CACHED_DEVICES;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_TAGS;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_VIRTUAL_DEVICES;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_VRF_INSTANCES;
import static net.netshot.netshot.platform.database.schema.Tables.DIAGNOSTIC_RESULT;
import static net.netshot.netshot.platform.database.schema.Tables.DOMAIN;
import static net.netshot.netshot.platform.database.schema.Tables.LONG_TEXT_CONFIGURATION;
import static net.netshot.netshot.platform.database.schema.Tables.MODULE;
import static net.netshot.netshot.platform.database.schema.Tables.NETWORK_INTERFACE;
import static net.netshot.netshot.platform.database.schema.Tables.NETWORK_INTERFACE_IP4ADDRESSES;
import static net.netshot.netshot.platform.database.schema.Tables.NETWORK_INTERFACE_IP6ADDRESSES;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.exception.DataChangedException;
import org.jooq.impl.DSL;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.domain.DomainRepository;
import net.netshot.netshot.inventory.compliance.CheckResult;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResultRepository;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.GroupRepository;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.database.schema.tables.DeviceCredentialSet;
import net.netshot.netshot.platform.database.schema.tables.Domain;
import net.netshot.netshot.platform.database.schema.tables.records.NetworkInterfaceIp4addressesRecord;
import net.netshot.netshot.platform.database.schema.tables.records.NetworkInterfaceIp6addressesRecord;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.platform.net.PhysicalAddress;
import net.netshot.netshot.platform.query.FieldType;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.platform.query.StatsQuery;
import net.netshot.netshot.platform.query.StatsRow;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * The queries on devices. The {@link DSLContext} is always the first parameter: the caller
 * (the service) owns the transaction. No query here changes how a transaction behaves, none
 * catches exceptions.
 *
 * <p>A {@link Device} is loaded in one query, with its domain, its end of life and end of sale
 * modules, its tags, virtual devices and VRFs, its attributes (with their long texts), its
 * accesses (with their credential sets and the domains of these), its current modules, its
 * interfaces (with their addresses), its groups and its compliance state.</p>
 */
public class DeviceRepository {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final TypeReference<Map<String, Object>> OPTIONS_TYPE = new TypeReference<>() {
	};

	/** The end of life module. */
	private static final net.netshot.netshot.platform.database.schema.tables.Module EOL = MODULE.as("eol_module");

	/** The end of sale module. */
	private static final net.netshot.netshot.platform.database.schema.tables.Module EOS = MODULE.as("eos_module");

	/** The global credential set of an access. */
	private static final DeviceCredentialSet GLOBAL_CS = CredentialSetRepository.CS.as("global_cs");

	/** The domain of the global credential set of an access. */
	private static final Domain GLOBAL_CS_DOMAIN = DOMAIN.as("global_cs_domain");

	/** The specific credential set of an access. */
	private static final DeviceCredentialSet SPECIFIC_CS = CredentialSetRepository.CS.as("specific_cs");

	/** The domain of the specific credential set of an access. */
	private static final Domain SPECIFIC_CS_DOMAIN = DOMAIN.as("specific_cs_domain");

	/** The discriminators of the device attributes. */
	private static final String TEXT = "S";
	private static final String NUMERIC = "N";
	private static final String BINARY = "B";
	private static final String LONG_TEXT = "T";

	private static final Field<Boolean> COMPLIANT = DSL.field(DSL.notExists(DSL.selectOne().from(CHECK_RESULT)
		.where(CHECK_RESULT.DEVICE.eq(DEVICE.ID), CHECK_RESULT.RESULT.eq(CheckResult.ResultOption.NONCONFORMING.name()))))
		.as("compliant");

	private static final Field<Set<String>> TAGS = DSL.multiset(
			DSL.select(DEVICE_TAGS.TAG).from(DEVICE_TAGS).where(DEVICE_TAGS.DEVICE.eq(DEVICE.ID)))
		.convertFrom(r -> (Set<String>) new TreeSet<>(r.getValues(DEVICE_TAGS.TAG)))
		.as("tags");

	private static final Field<Set<String>> VIRTUAL_DEVICES = DSL.multiset(
			DSL.select(DEVICE_VIRTUAL_DEVICES.VIRTUAL_DEVICES).from(DEVICE_VIRTUAL_DEVICES).where(DEVICE_VIRTUAL_DEVICES.DEVICE.eq(DEVICE.ID)))
		.convertFrom(r -> (Set<String>) new TreeSet<>(r.getValues(DEVICE_VIRTUAL_DEVICES.VIRTUAL_DEVICES)))
		.as("virtual_devices");

	private static final Field<Set<String>> VRF_INSTANCES = DSL.multiset(
			DSL.select(DEVICE_VRF_INSTANCES.VRF_INSTANCES).from(DEVICE_VRF_INSTANCES).where(DEVICE_VRF_INSTANCES.DEVICE.eq(DEVICE.ID)))
		.convertFrom(r -> (Set<String>) new TreeSet<>(r.getValues(DEVICE_VRF_INSTANCES.VRF_INSTANCES)))
		.as("vrf_instances");

	private static final Field<List<DeviceAttribute>> ATTRIBUTES = DSL.multiset(
			DSL.select(DEVICE_ATTRIBUTE.TYPE, DEVICE_ATTRIBUTE.NAME, DEVICE_ATTRIBUTE.NUMBER, DEVICE_ATTRIBUTE.TEXT,
					DEVICE_ATTRIBUTE.ASSUMPTION, LONG_TEXT_CONFIGURATION.TEXT)
				.from(DEVICE_ATTRIBUTE)
				.leftJoin(LONG_TEXT_CONFIGURATION).on(LONG_TEXT_CONFIGURATION.ID.eq(DEVICE_ATTRIBUTE.LONG_TEXT))
				.where(DEVICE_ATTRIBUTE.DEVICE.eq(DEVICE.ID))
				.orderBy(DEVICE_ATTRIBUTE.ID))
		.convertFrom(r -> r.map(a -> toAttribute(a.value1(), a.value2(), a.value3(), a.value4(), a.value5(), a.value6())))
		.as("attributes");

	private static final List<SelectField<?>> ACCESS_FIELDS;

	static {
		List<SelectField<?>> fields = new ArrayList<>(List.of(DEVICE_ACCESS.fields()));
		CredentialSetRepository.fields(GLOBAL_CS, GLOBAL_CS_DOMAIN).forEach(f -> fields.add((SelectField<?>) f));
		CredentialSetRepository.fields(SPECIFIC_CS, SPECIFIC_CS_DOMAIN).forEach(f -> fields.add((SelectField<?>) f));
		ACCESS_FIELDS = List.copyOf(fields);
	}

	private static final Field<List<DeviceAccess>> ACCESSES = DSL.multiset(
			DSL.select(ACCESS_FIELDS)
				.from(DEVICE_ACCESS)
				.leftJoin(GLOBAL_CS).on(GLOBAL_CS.ID.eq(DEVICE_ACCESS.GLOBAL_CREDENTIAL_SET))
				.leftJoin(GLOBAL_CS_DOMAIN).on(GLOBAL_CS_DOMAIN.ID.eq(GLOBAL_CS.MGMT_DOMAIN))
				.leftJoin(SPECIFIC_CS).on(SPECIFIC_CS.ID.eq(DEVICE_ACCESS.SPECIFIC_CREDENTIAL_SET))
				.leftJoin(SPECIFIC_CS_DOMAIN).on(SPECIFIC_CS_DOMAIN.ID.eq(SPECIFIC_CS.MGMT_DOMAIN))
				.where(DEVICE_ACCESS.DEVICE.eq(DEVICE.ID))
				.orderBy(DEVICE_ACCESS.ACCESS_NAME))
		.convertFrom(r -> r.map(DeviceRepository::toAccess))
		.as("accesses");

	private static final Field<List<Module>> MODULES = DSL.multiset(
			DSL.select(MODULE.fields()).from(MODULE)
				.where(MODULE.DEVICE.eq(DEVICE.ID), MODULE.REMOVED.isFalse())
				.orderBy(MODULE.ID))
		.convertFrom(r -> r.map(m -> toModule(m, MODULE)))
		.as("modules");

	private static final Field<Set<Network4Address>> IP4_ADDRESSES = DSL.multiset(
			DSL.select(NETWORK_INTERFACE_IP4ADDRESSES.ADDRESS, NETWORK_INTERFACE_IP4ADDRESSES.PREFIX_LENGTH,
					NETWORK_INTERFACE_IP4ADDRESSES.ADDRESS_USAGE)
				.from(NETWORK_INTERFACE_IP4ADDRESSES)
				.where(NETWORK_INTERFACE_IP4ADDRESSES.NETWORK_INTERFACE.eq(NETWORK_INTERFACE.ID)))
		.convertFrom(r -> (Set<Network4Address>) new LinkedHashSet<>(r.map(a -> Network4Address.fromColumns(a.value1(), a.value2(), a.value3()))))
		.as("ip4_addresses");

	private static final Field<Set<Network6Address>> IP6_ADDRESSES = DSL.multiset(
			DSL.select(NETWORK_INTERFACE_IP6ADDRESSES.ADDRESS, NETWORK_INTERFACE_IP6ADDRESSES.PREFIX_LENGTH,
					NETWORK_INTERFACE_IP6ADDRESSES.ADDRESS_USAGE)
				.from(NETWORK_INTERFACE_IP6ADDRESSES)
				.where(NETWORK_INTERFACE_IP6ADDRESSES.NETWORK_INTERFACE.eq(NETWORK_INTERFACE.ID)))
		.convertFrom(r -> (Set<Network6Address>) new LinkedHashSet<>(r.map(a -> Network6Address.fromColumns(a.value1(), a.value2(), a.value3()))))
		.as("ip6_addresses");

	/** The fields to select to build network interfaces (see {@link #toInterface}). */
	public static final List<SelectField<?>> INTERFACE_FIELDS;

	static {
		List<SelectField<?>> fields = new ArrayList<>(List.of(NETWORK_INTERFACE.fields()));
		fields.add(IP4_ADDRESSES);
		fields.add(IP6_ADDRESSES);
		INTERFACE_FIELDS = List.copyOf(fields);
	}

	private static final Field<List<NetworkInterface>> INTERFACES = DSL.multiset(
			DSL.select(INTERFACE_FIELDS).from(NETWORK_INTERFACE)
				.where(NETWORK_INTERFACE.DEVICE.eq(DEVICE.ID))
				.orderBy(NETWORK_INTERFACE.ID))
		.convertFrom(r -> r.map(DeviceRepository::toInterface))
		.as("interfaces");

	private static final Field<List<DeviceGroup>> GROUPS = DSL.multiset(
			DSL.select(DEVICE_GROUP.fields()).from(DEVICE_GROUP)
				.join(DEVICE_GROUP_CACHED_DEVICES).on(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(DEVICE_GROUP.ID))
				.where(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(DEVICE.ID))
				.orderBy(DEVICE_GROUP.ID))
		.convertFrom(r -> r.map(g -> GroupRepository.toGroup(g, DEVICE_GROUP)))
		.as("owner_groups");

	private static final List<SelectField<?>> FIELDS;

	/** The fields of a device without its components (modules and network interfaces), for the lists. */
	private static final List<SelectField<?>> LIST_FIELDS;

	static {
		List<SelectField<?>> fields = new ArrayList<>(List.of(DEVICE.fields()));
		fields.addAll(List.of(DOMAIN.fields()));
		fields.addAll(List.of(EOL.fields()));
		fields.addAll(List.of(EOS.fields()));
		fields.addAll(List.of(COMPLIANT, TAGS, VIRTUAL_DEVICES, VRF_INSTANCES, ATTRIBUTES, ACCESSES, GROUPS));
		LIST_FIELDS = List.copyOf(fields);
		fields.addAll(List.of(MODULES, INTERFACES));
		FIELDS = List.copyOf(fields);
	}

	private static <E extends Enum<E>> E toEnum(Class<E> type, String name) {
		return name == null ? null : Enum.valueOf(type, name);
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

	/**
	 * The time of a change, to the second (as the former Hibernate interceptor did).
	 *
	 * @return the time
	 */
	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.SECONDS);
	}

	private static Map<String, Object> readOptions(JSONB json) {
		if (json == null) {
			return Map.of();
		}
		try {
			Map<String, Object> options = JSON.readValue(json.data(), OPTIONS_TYPE);
			return options == null ? Map.of() : options;
		}
		catch (IOException e) {
			throw new IllegalStateException("Invalid stored device options", e);
		}
	}

	private static JSONB toJson(Map<String, Object> options) {
		try {
			return JSONB.valueOf(JSON.writeValueAsString(options == null ? Map.of() : options));
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Invalid device options", e);
		}
	}

	private static DeviceAttribute toAttribute(String type, String name, Double number, String text, Boolean assumption, String longText) {
		return switch (type) {
			case TEXT -> new DeviceTextAttribute(name, text);
			case NUMERIC -> new DeviceNumericAttribute(name, number);
			case BINARY -> new DeviceBinaryAttribute(name, assumption);
			case LONG_TEXT -> new DeviceLongTextAttribute(name, longText);
			default -> throw new IllegalStateException("Unknown type of device attribute '%s'".formatted(type));
		};
	}

	/**
	 * Builds a module from the columns of a (possibly aliased, possibly outer joined) module table.
	 *
	 * @param r the record
	 * @param m the module table
	 * @return the module, or null if the record has none (outer join)
	 */
	static Module toModule(Record r, net.netshot.netshot.platform.database.schema.tables.Module m) {
		Long id = r.get(m.ID);
		if (id == null) {
			return null;
		}
		return new Module(id, r.get(m.SLOT), r.get(m.PART_NUMBER), r.get(m.SERIAL_NUMBER), r.get(m.FIRST_SEEN_DATE),
			r.get(m.LAST_SEEN_DATE), Boolean.TRUE.equals(r.get(m.REMOVED)));
	}

	private static DeviceAccess toAccess(Record r) {
		return new DeviceAccess(
			r.get(DEVICE_ACCESS.ACCESS_NAME),
			r.get(DEVICE_ACCESS.ADDRESS),
			r.get(DEVICE_ACCESS.PORT),
			CredentialSetRepository.toCredentialSet(r, GLOBAL_CS, GLOBAL_CS_DOMAIN),
			CredentialSetRepository.toCredentialSet(r, SPECIFIC_CS, SPECIFIC_CS_DOMAIN),
			toEnum(DeviceAccess.SshHostKeyVerification.class, r.get(DEVICE_ACCESS.SSH_HOST_KEY_VERIFICATION)),
			r.get(DEVICE_ACCESS.SSH_TRUSTED_HOST_KEYS),
			r.get(DEVICE_ACCESS.SSH_TRUSTED_HOST_KEYS_SINCE),
			toEnum(HttpsCaTrustMode.class, r.get(DEVICE_ACCESS.HTTPS_CA_TRUST_MODE)),
			r.get(DEVICE_ACCESS.HTTPS_CUSTOM_CA_CERTIFICATE));
	}

	/**
	 * Builds a network interface from a record of the {@link #INTERFACE_FIELDS}.
	 *
	 * @param r the record
	 * @return the interface
	 */
	public static NetworkInterface toInterface(Record r) {
		Long physicalAddress = r.get(NETWORK_INTERFACE.PHYSICAL_ADDRESS);
		return new NetworkInterface(
			r.get(NETWORK_INTERFACE.ID),
			r.get(NETWORK_INTERFACE.INTERFACE_NAME),
			r.get(IP4_ADDRESSES),
			r.get(IP6_ADDRESSES),
			new PhysicalAddress(physicalAddress == null ? 0 : physicalAddress),
			r.get(NETWORK_INTERFACE.VRF_INSTANCE),
			r.get(NETWORK_INTERFACE.VIRTUAL_DEVICE),
			r.get(NETWORK_INTERFACE.DESCRIPTION),
			Boolean.TRUE.equals(r.get(NETWORK_INTERFACE.ENABLED)),
			Boolean.TRUE.equals(r.get(NETWORK_INTERFACE.LEVEL3)));
	}

	private static Device toDevice(Record r) {
		return new Device(
			r.get(DEVICE.ID),
			r.get(DEVICE.VERSION),
			r.get(DEVICE.CHANGE_DATE),
			r.get(DEVICE.NAME),
			r.get(DEVICE.COMMENTS),
			r.get(DEVICE.CONTACT),
			r.get(DEVICE.LOCATION),
			r.get(DEVICE.FAMILY),
			r.get(DEVICE.CREATED_DATE),
			r.get(DEVICE.CREATOR),
			r.get(DEVICE.DRIVER),
			r.get(DEVICE.MGMT_ADDRESS),
			r.get(DEVICE.CACHED_IP_ADDRESS),
			DomainRepository.toDomain(r, DOMAIN),
			toEnum(Device.NetworkClass.class, r.get(DEVICE.NETWORK_CLASS)),
			r.get(DEVICE.SERIAL_NUMBER),
			toEnum(ConformanceLevel.class, r.get(DEVICE.SOFTWARE_LEVEL)),
			r.get(DEVICE.SOFTWARE_VERSION),
			toEnum(Device.Status.class, r.get(DEVICE.STATUS)),
			r.get(DEVICE.EOL_DATE),
			toModule(r, EOL),
			r.get(DEVICE.EOS_DATE),
			toModule(r, EOS),
			r.get(DEVICE.LAST_CONFIG),
			r.get(DEVICE.LAST_SNAPSHOT_SUCCESS_DATE),
			r.get(DEVICE.LAST_SNAPSHOT_FAILURE_DATE),
			Boolean.TRUE.equals(r.get(COMPLIANT)),
			readOptions(r.get(DEVICE.OPTIONS)),
			r.get(TAGS),
			r.get(VIRTUAL_DEVICES),
			r.get(VRF_INSTANCES),
			r.get(ATTRIBUTES),
			r.get(ACCESSES),
			r.field(MODULES) == null ? null : r.get(MODULES),
			r.field(INTERFACES) == null ? null : r.get(INTERFACES),
			r.get(GROUPS));
	}

	private List<Device> findWhere(DSLContext dsl, Condition condition) {
		return dsl.select(FIELDS)
			.from(DEVICE)
			.leftJoin(DOMAIN).on(DOMAIN.ID.eq(DEVICE.MGMT_DOMAIN))
			.leftJoin(EOL).on(EOL.ID.eq(DEVICE.EOL_MODULE))
			.leftJoin(EOS).on(EOS.ID.eq(DEVICE.EOS_MODULE))
			.where(condition)
			.orderBy(DEVICE.ID)
			.fetch(DeviceRepository::toDevice);
	}

	/**
	 * Finds a device.
	 *
	 * @param dsl the jOOQ context
	 * @param id the device ID
	 * @return the device, or null if there is no such device
	 */
	public Device find(DSLContext dsl, long id) {
		List<Device> devices = this.findWhere(dsl, DEVICE.ID.eq(id));
		return devices.isEmpty() ? null : devices.get(0);
	}

	/**
	 * Finds a device by name (the first one, by ID, if several have this name).
	 *
	 * @param dsl the jOOQ context
	 * @param name the name of the device
	 * @return the device, or null if there is no such device
	 */
	public Device findByName(DSLContext dsl, String name) {
		List<Device> devices = this.findWhere(dsl, DEVICE.NAME.eq(name));
		return devices.isEmpty() ? null : devices.get(0);
	}

	/**
	 * Finds devices.
	 *
	 * @param dsl the jOOQ context
	 * @param ids the device IDs
	 * @return the devices which exist, by ID
	 */
	public List<Device> findAll(DSLContext dsl, Collection<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return this.findWhere(dsl, DEVICE.ID.in(ids));
	}

	/**
	 * Finds an active (not disabled) device having the given management address (case insensitive).
	 *
	 * @param dsl the jOOQ context
	 * @param address the management address
	 * @param excludedId the ID of a device to ignore (the one being edited), or null
	 * @return a matching device (its ID and name), or null if there is none
	 */
	public DeviceSummary findActiveByMgmtAddress(DSLContext dsl, String address, Long excludedId) {
		Condition condition = DSL.lower(DEVICE.MGMT_ADDRESS).eq(DSL.lower(DSL.val(address)))
			.and(DEVICE.STATUS.ne(Device.Status.DISABLED.name()));
		if (excludedId != null) {
			condition = condition.and(DEVICE.ID.ne(excludedId));
		}
		return dsl.select(DeviceSummary.FIELDS).from(DEVICE).where(condition).limit(1).fetchOne(DeviceSummary::fromRecord);
	}

	/**
	 * Finds a device in its summary form.
	 *
	 * @param dsl the jOOQ context
	 * @param id the device ID
	 * @return the device summary, or null if there is no such device
	 */
	public DeviceSummary findSummary(DSLContext dsl, long id) {
		return dsl.select(DeviceSummary.FIELDS).from(DEVICE).where(DEVICE.ID.eq(id)).fetchOne(DeviceSummary::fromRecord);
	}

	/**
	 * Lists the devices in their summary form.
	 *
	 * @param dsl the jOOQ context
	 * @param groupId the ID of a group whose members are listed (in the order of the group), or null for all the devices
	 * @param page the window on the result
	 * @return the device summaries
	 */
	public List<DeviceSummary> findSummaries(DSLContext dsl, Long groupId, Page page) {
		if (groupId == null) {
			return page.apply(dsl.select(DeviceSummary.FIELDS).from(DEVICE)).fetch(DeviceSummary::fromRecord);
		}
		return page.apply(dsl.select(DeviceSummary.FIELDS).from(DEVICE)
				.join(DEVICE_GROUP_CACHED_DEVICES).on(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(DEVICE.ID))
				.where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(groupId))
				.orderBy(DEVICE_GROUP_CACHED_DEVICES.LIST_POSITION.asc(), DEVICE.NAME.asc()))
			.fetch(DeviceSummary::fromRecord);
	}

	/** The fields the device lists can be filtered and sorted on. */
	public static final QueryMapping LIST = QueryMapping.builder(DEVICE.ID)
		.string("name", DEVICE.NAME, Option.SORTABLE, Option.SEARCHABLE)
		.string("mgmtAddress", DEVICE.MGMT_ADDRESS, Option.SORTABLE, Option.SEARCHABLE)
		.enumeration("status", DEVICE.STATUS, Device.Status.class, Option.SORTABLE, Option.GROUPABLE)
		.integer("mgmtDomain", DEVICE.MGMT_DOMAIN, Option.GROUPABLE)
		.string("family", DEVICE.FAMILY, Option.SORTABLE, Option.SEARCHABLE, Option.GROUPABLE)
		.string("driver", DEVICE.DRIVER, Option.SORTABLE, Option.GROUPABLE)
		.enumeration("networkClass", DEVICE.NETWORK_CLASS, Device.NetworkClass.class, Option.SORTABLE, Option.GROUPABLE)
		.string("softwareVersion", DEVICE.SOFTWARE_VERSION, Option.SORTABLE, Option.SEARCHABLE, Option.GROUPABLE)
		.enumeration("softwareLevel", DEVICE.SOFTWARE_LEVEL, ConformanceLevel.class, Option.SORTABLE, Option.GROUPABLE)
		.string("serialNumber", DEVICE.SERIAL_NUMBER, Option.SORTABLE, Option.SEARCHABLE)
		.string("location", DEVICE.LOCATION, Option.SORTABLE, Option.SEARCHABLE, Option.GROUPABLE)
		.string("contact", DEVICE.CONTACT, Option.SORTABLE, Option.SEARCHABLE)
		.string("comments", DEVICE.COMMENTS, Option.SEARCHABLE)
		.string("creator", DEVICE.CREATOR, Option.SORTABLE)
		.inet("cachedIpAddress", DEVICE.CACHED_IP_ADDRESS)
		.date("createdDate", DEVICE.CREATED_DATE, Option.SORTABLE)
		.date("changeDate", DEVICE.CHANGE_DATE, Option.SORTABLE)
		.date("eolDate", DEVICE.EOL_DATE, Option.SORTABLE, Option.GROUPABLE)
		.date("eosDate", DEVICE.EOS_DATE, Option.SORTABLE, Option.GROUPABLE)
		.date("lastSnapshotSuccessDate", DEVICE.LAST_SNAPSHOT_SUCCESS_DATE, Option.SORTABLE)
		.date("lastSnapshotFailureDate", DEVICE.LAST_SNAPSHOT_FAILURE_DATE, Option.SORTABLE)
		.custom("group", FieldType.INTEGER, EnumSet.of(Lookup.EXACT, Lookup.N), (_, values) ->
			DSL.exists(DSL.selectOne().from(DEVICE_GROUP_CACHED_DEVICES)
				.where(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(DEVICE.ID))
				.and(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.in(values.stream().map(Long.class::cast).toList()))))
		.custom("tag", FieldType.STRING, EnumSet.of(Lookup.EXACT, Lookup.N, Lookup.IE, Lookup.NIE, Lookup.IC, Lookup.NIC,
			Lookup.ISW, Lookup.NISW, Lookup.EMPTY), (lookup, values) -> {
				if (lookup == Lookup.EMPTY) {
					return DSL.notExists(DSL.selectOne().from(DEVICE_TAGS).where(DEVICE_TAGS.DEVICE.eq(DEVICE.ID)));
				}
				List<Condition> any = values.stream().map(String.class::cast).map(v -> switch (lookup) {
					case IE -> DEVICE_TAGS.TAG.equalIgnoreCase(v);
					case IC -> DEVICE_TAGS.TAG.containsIgnoreCase(v);
					case ISW -> DEVICE_TAGS.TAG.startsWithIgnoreCase(v);
					default -> DEVICE_TAGS.TAG.eq(v);
				}).toList();
				return DSL.exists(DSL.selectOne().from(DEVICE_TAGS).where(DEVICE_TAGS.DEVICE.eq(DEVICE.ID)).and(DSL.or(any)));
			})
		.custom("compliant", FieldType.BOOLEAN, EnumSet.of(Lookup.EXACT), (_, values) -> {
				Condition compliant = DSL.notExists(DSL.selectOne().from(CHECK_RESULT)
					.where(CHECK_RESULT.DEVICE.eq(DEVICE.ID), CHECK_RESULT.RESULT.eq(CheckResult.ResultOption.NONCONFORMING.name())));
				return DSL.or(values.stream().map(v -> Boolean.TRUE.equals(v) ? compliant : DSL.not(compliant)).toList());
			})
		// A device is counted once per group (no group: null), once per tag (no tag: null)
		.grouping("group", DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS, from -> from.leftJoin(DEVICE_GROUP_CACHED_DEVICES)
			.on(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES.eq(DEVICE.ID)))
		.grouping("tag", DEVICE_TAGS.TAG, from -> from.leftJoin(DEVICE_TAGS).on(DEVICE_TAGS.DEVICE.eq(DEVICE.ID)))
		.grouping("compliant", DSL.field(DSL.notExists(DSL.selectOne().from(CHECK_RESULT)
			.where(CHECK_RESULT.DEVICE.eq(DEVICE.ID), CHECK_RESULT.RESULT.eq(CheckResult.ResultOption.NONCONFORMING.name())))), null)
		.build();

	/**
	 * Lists the summaries of the devices matching a query and a condition.
	 *
	 * @param dsl the jOOQ context
	 * @param query the list query
	 * @param condition another condition (such as the one of a device search query)
	 * @return the summaries of the window of the query
	 */
	public List<DeviceSummary> findSummaries(DSLContext dsl, ListQuery query, Condition condition) {
		return query.page().apply(dsl.select(DeviceSummary.FIELDS).from(DEVICE)
				.where(LIST.condition(query)).and(condition)
				.orderBy(LIST.orderBy(query)))
			.fetch(DeviceSummary::fromRecord);
	}

	/**
	 * Lists the devices matching a query and a condition, with all their details but their components: their
	 * modules and network interfaces aren't loaded (empty), they have their own lists.
	 *
	 * @param dsl the jOOQ context
	 * @param query the list query
	 * @param condition another condition (such as the one of a device search query)
	 * @return the devices of the window of the query, without their modules and network interfaces
	 */
	public List<Device> findAll(DSLContext dsl, ListQuery query, Condition condition) {
		return query.page().apply(dsl.select(LIST_FIELDS)
				.from(DEVICE)
				.leftJoin(DOMAIN).on(DOMAIN.ID.eq(DEVICE.MGMT_DOMAIN))
				.leftJoin(EOL).on(EOL.ID.eq(DEVICE.EOL_MODULE))
				.leftJoin(EOS).on(EOS.ID.eq(DEVICE.EOS_MODULE))
				.where(LIST.condition(query)).and(condition)
				.orderBy(LIST.orderBy(query)))
			.fetch(DeviceRepository::toDevice);
	}

	/**
	 * Counts the devices matching a query and a condition.
	 *
	 * @param dsl the jOOQ context
	 * @param query the list query (its window is ignored)
	 * @param condition another condition
	 * @return the number of devices
	 */
	public long count(DSLContext dsl, ListQuery query, Condition condition) {
		return dsl.fetchCount(DEVICE, LIST.condition(query).and(condition));
	}

	/**
	 * Counts the devices matching a query and a condition, by field values and/or time buckets.
	 *
	 * @param dsl the jOOQ context
	 * @param query the filters
	 * @param stats what to count by
	 * @param condition the additional condition (search query), or null
	 * @return the rows
	 */
	public List<StatsRow> stats(DSLContext dsl, ListQuery query, StatsQuery stats, Condition condition) {
		return LIST.stats(dsl, DEVICE, query, stats, condition);
	}

	/**
	 * Finds the network interfaces of a device, with their IP addresses.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @param page the window on the result
	 * @return the interfaces, by ID (none if there is no such device)
	 */
	public List<NetworkInterface> findInterfaces(DSLContext dsl, long deviceId, Page page) {
		List<NetworkInterface> interfaces = page.apply(dsl.select(INTERFACE_FIELDS).from(NETWORK_INTERFACE)
				.where(NETWORK_INTERFACE.DEVICE.eq(deviceId))
				.orderBy(NETWORK_INTERFACE.ID))
			.fetch(DeviceRepository::toInterface);
		if (page.isRestricted()) {
			// Quirk kept: Hibernate paginated this query (with collection fetches) in memory, answering
			// a sub list, which XML publishes as <SubList> instead of <ArrayList>
			return interfaces.subList(0, interfaces.size());
		}
		return interfaces;
	}

	/**
	 * Finds the hardware modules of a device.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @param includeHistory whether to include the modules which have been removed
	 * @param page the window on the result
	 * @return the modules, by ID (none if there is no such device)
	 */
	public List<Module> findModules(DSLContext dsl, long deviceId, boolean includeHistory, Page page) {
		Condition condition = MODULE.DEVICE.eq(deviceId);
		if (!includeHistory) {
			condition = condition.and(MODULE.REMOVED.isFalse());
		}
		return page.apply(dsl.select(MODULE.fields()).from(MODULE).where(condition).orderBy(MODULE.ID))
			.fetch(m -> toModule(m, MODULE));
	}

	/**
	 * Lists the device families currently known: the driver and family of every device.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the families, one per device
	 */
	public List<DeviceFamily> findFamilies(DSLContext dsl, Page page) {
		return page.apply(dsl.select(DEVICE.DRIVER, DEVICE.FAMILY).from(DEVICE).orderBy(DEVICE.ID))
			.fetch(r -> new DeviceFamily(r.get(DEVICE.DRIVER), r.get(DEVICE.FAMILY)));
	}

	/**
	 * Lists the part numbers currently known: the part number of every module.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the part numbers, one per module
	 */
	public List<String> findPartNumbers(DSLContext dsl, Page page) {
		return page.apply(dsl.select(MODULE.PART_NUMBER).from(MODULE).orderBy(MODULE.ID)).fetch(MODULE.PART_NUMBER);
	}

	/**
	 * Lists the tags attached to at least one device, with the number of devices of each.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the tags, sorted
	 */
	public List<DeviceTagCount> findTagCounts(DSLContext dsl, Page page) {
		Field<Integer> count = DSL.count(DEVICE_TAGS.DEVICE);
		return page.apply(dsl.select(DEVICE_TAGS.TAG, count).from(DEVICE_TAGS).groupBy(DEVICE_TAGS.TAG).orderBy(DEVICE_TAGS.TAG))
			.fetch(r -> new DeviceTagCount(r.get(DEVICE_TAGS.TAG), r.get(count).longValue()));
	}

	/**
	 * Lists the software versions currently known: the (non empty) version of every device.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the software versions, one per device
	 */
	public List<String> findSoftwareVersions(DSLContext dsl, Page page) {
		return page.apply(dsl.select(DEVICE.SOFTWARE_VERSION).from(DEVICE)
				.where(DEVICE.SOFTWARE_VERSION.isNotNull(), DEVICE.SOFTWARE_VERSION.ne(""))
				.orderBy(DEVICE.ID))
			.fetch(DEVICE.SOFTWARE_VERSION);
	}

	/**
	 * Finds the paths of the binary files holding configuration attributes of a device.
	 *
	 * @param dsl the jOOQ context
	 * @param deviceId the device ID
	 * @return the file paths
	 */
	public List<Path> findConfigBinaryFilePaths(DSLContext dsl, long deviceId) {
		return dsl.select(CONFIG.ID, CONFIG_ATTRIBUTE.NAME, CONFIG_ATTRIBUTE.UID, CONFIG_ATTRIBUTE.ORIGINAL_NAME)
			.from(CONFIG_ATTRIBUTE)
			.join(CONFIG).on(CONFIG.ID.eq(CONFIG_ATTRIBUTE.CONFIG))
			.where(CONFIG.DEVICE.eq(deviceId), CONFIG_ATTRIBUTE.TYPE.eq("F"))
			.fetch(r -> new ConfigBinaryFileAttribute(r.get(CONFIG_ATTRIBUTE.NAME), r.get(CONFIG_ATTRIBUTE.UID),
				r.get(CONFIG_ATTRIBUTE.ORIGINAL_NAME), 0, null).getFilePath(deviceId, r.get(CONFIG.ID)));
	}

	/**
	 * Stores a new device, with its tags, virtual devices, VRFs, attributes, accesses, modules
	 * and interfaces (the specific credential sets of the accesses must be stored already).
	 * The device is version 0, changed now if it has no change date.
	 *
	 * @param dsl the jOOQ context
	 * @param device the device (its ID and version are ignored)
	 * @return the stored device
	 */
	public Device add(DSLContext dsl, Device device) {
		long id = dsl.insertInto(DEVICE)
			.set(rowValues(device))
			.set(DEVICE.VERSION, 0)
			.set(DEVICE.CHANGE_DATE, device.changeDate() == null ? now() : device.changeDate())
			.set(DEVICE.CREATED_DATE, device.createdDate())
			.set(DEVICE.CREATOR, device.creator())
			.set(DEVICE.DRIVER, device.driver())
			.returning(DEVICE.ID)
			.fetchOne(DEVICE.ID);
		insertTags(dsl, id, device.tags());
		insertVirtualDevices(dsl, id, device.virtualDevices());
		insertVrfInstances(dsl, id, device.vrfInstances());
		insertAttributes(dsl, id, device.attributes());
		for (DeviceAccess access : device.accesses()) {
			insertAccess(dsl, id, access);
		}
		for (Module module : device.modules()) {
			insertModule(dsl, id, module);
		}
		insertInterfaces(dsl, id, device.networkInterfaces());
		return this.find(dsl, id);
	}

	/**
	 * The values of the columns of a device which change (by an edit or a snapshot).
	 *
	 * @param device the device
	 * @return the values, by column
	 */
	private static Map<Field<?>, Object> rowValues(Device device) {
		Map<Field<?>, Object> values = new HashMap<>();
		values.put(DEVICE.NAME, device.name());
		values.put(DEVICE.COMMENTS, device.comments());
		values.put(DEVICE.CONTACT, device.contact());
		values.put(DEVICE.LOCATION, device.location());
		values.put(DEVICE.FAMILY, device.family());
		values.put(DEVICE.MGMT_ADDRESS, device.mgmtAddress());
		values.put(DEVICE.CACHED_IP_ADDRESS, device.cachedIpAddress());
		values.put(DEVICE.MGMT_DOMAIN, device.mgmtDomain() == null ? null : device.mgmtDomain().id());
		values.put(DEVICE.NETWORK_CLASS, name(device.networkClass()));
		values.put(DEVICE.SERIAL_NUMBER, device.serialNumber());
		values.put(DEVICE.SOFTWARE_LEVEL, name(device.softwareLevel()));
		values.put(DEVICE.SOFTWARE_VERSION, device.softwareVersion());
		values.put(DEVICE.STATUS, name(device.status()));
		values.put(DEVICE.EOL_DATE, device.eolDate());
		values.put(DEVICE.EOL_MODULE, device.eolModule() == null ? null : device.eolModule().id());
		values.put(DEVICE.EOS_DATE, device.eosDate());
		values.put(DEVICE.EOS_MODULE, device.eosModule() == null ? null : device.eosModule().id());
		values.put(DEVICE.LAST_CONFIG, device.lastConfigId());
		values.put(DEVICE.OPTIONS, toJson(device.options()));
		return values;
	}

	/**
	 * Tells whether the stored row (or one of the value collections) of a device changes.
	 *
	 * @param original the device as loaded
	 * @param edited the device to save
	 * @return true if the device row must be updated
	 */
	private static boolean rowChanged(Device original, Device edited) {
		return !Objects.equals(original.name(), edited.name())
			|| !Objects.equals(original.comments(), edited.comments())
			|| !Objects.equals(original.contact(), edited.contact())
			|| !Objects.equals(original.location(), edited.location())
			|| !Objects.equals(original.family(), edited.family())
			|| !Objects.equals(original.mgmtAddress(), edited.mgmtAddress())
			|| !Objects.equals(original.cachedIpAddress(), edited.cachedIpAddress())
			|| !Objects.equals(original.mgmtDomain() == null ? null : original.mgmtDomain().id(),
				edited.mgmtDomain() == null ? null : edited.mgmtDomain().id())
			|| original.networkClass() != edited.networkClass()
			|| !Objects.equals(original.serialNumber(), edited.serialNumber())
			|| original.softwareLevel() != edited.softwareLevel()
			|| !Objects.equals(original.softwareVersion(), edited.softwareVersion())
			|| original.status() != edited.status()
			|| !Objects.equals(original.eolDate(), edited.eolDate())
			|| !Objects.equals(original.eolModule() == null ? null : original.eolModule().id(),
				edited.eolModule() == null ? null : edited.eolModule().id())
			|| !Objects.equals(original.eosDate(), edited.eosDate())
			|| !Objects.equals(original.eosModule() == null ? null : original.eosModule().id(),
				edited.eosModule() == null ? null : edited.eosModule().id())
			|| !Objects.equals(original.lastConfigId(), edited.lastConfigId())
			|| !Objects.equals(original.options(), edited.options())
			|| !original.tags().equals(edited.tags())
			|| !original.virtualDevices().equals(edited.virtualDevices())
			|| !original.vrfInstances().equals(edited.vrfInstances());
	}

	/**
	 * Saves the changes of a device: what differs between the device as it was loaded and the
	 * edited one is written. When the row of the device (or its tags, virtual devices or VRFs)
	 * changes, it is checked not to have been changed meanwhile, its version is incremented
	 * and its change date set. The attributes, modules (by ID: the new ones have the ID 0, the
	 * missing ones are deleted), interfaces and accesses are written when they differ. A specific credential set which
	 * no access uses anymore is removed (the new ones must be stored already).
	 *
	 * @param dsl the jOOQ context
	 * @param original the device as it was loaded
	 * @param edited the edited device
	 * @throws DataChangedException if the device has been changed meanwhile
	 */
	public void save(DSLContext dsl, Device original, Device edited) {
		long id = original.id();
		if (rowChanged(original, edited)) {
			int updated = dsl.update(DEVICE)
				.set(rowValues(edited))
				.set(DEVICE.VERSION, DEVICE.VERSION.plus(1))
				.set(DEVICE.CHANGE_DATE, now())
				.where(DEVICE.ID.eq(id), DEVICE.VERSION.eq(original.version()))
				.execute();
			if (updated == 0) {
				throw new DataChangedException("The device %d has been changed or removed meanwhile".formatted(id));
			}
			if (!original.tags().equals(edited.tags())) {
				dsl.deleteFrom(DEVICE_TAGS).where(DEVICE_TAGS.DEVICE.eq(id)).execute();
				insertTags(dsl, id, edited.tags());
			}
			if (!original.virtualDevices().equals(edited.virtualDevices())) {
				dsl.deleteFrom(DEVICE_VIRTUAL_DEVICES).where(DEVICE_VIRTUAL_DEVICES.DEVICE.eq(id)).execute();
				insertVirtualDevices(dsl, id, edited.virtualDevices());
			}
			if (!original.vrfInstances().equals(edited.vrfInstances())) {
				dsl.deleteFrom(DEVICE_VRF_INSTANCES).where(DEVICE_VRF_INSTANCES.DEVICE.eq(id)).execute();
				insertVrfInstances(dsl, id, edited.vrfInstances());
			}
		}
		if (!original.attributes().equals(edited.attributes())) {
			deleteAttributes(dsl, id);
			insertAttributes(dsl, id, edited.attributes());
		}
		if (!original.networkInterfaces().equals(edited.networkInterfaces())) {
			dsl.deleteFrom(NETWORK_INTERFACE).where(NETWORK_INTERFACE.DEVICE.eq(id)).execute();
			insertInterfaces(dsl, id, edited.networkInterfaces());
		}
		this.saveModules(dsl, id, original.modules(), edited.modules());
		this.saveAccesses(dsl, id, original.accesses(), edited.accesses());
	}

	/**
	 * Records the outcome of a snapshot: sets the date of the last successful (or failed) snapshot. Unlike
	 * {@link #save}, only this column is written (by ID, without touching the version or the change date), so it
	 * can't conflict with a concurrent edit of the device nor with another runner.
	 *
	 * @param dsl the jOOQ context
	 * @param id the device ID
	 * @param success true to set the date of the last successful snapshot, false for the failed one
	 * @param date the date of the snapshot
	 * @return true if the device exists
	 */
	public boolean saveSnapshotDate(DSLContext dsl, long id, boolean success, Instant date) {
		Field<Instant> column = success ? DEVICE.LAST_SNAPSHOT_SUCCESS_DATE : DEVICE.LAST_SNAPSHOT_FAILURE_DATE;
		return dsl.update(DEVICE).set(column, date).where(DEVICE.ID.eq(id)).execute() > 0;
	}

	/**
	 * Stores what a script did on a device: the changes of the device (see {@link #save}), the
	 * new configuration it gathered (which becomes the last one of the device), and the
	 * diagnostic results it gave.
	 *
	 * @param dsl the jOOQ context
	 * @param draft the device the script worked on
	 * @return the new configuration, as stored, or null if there is none
	 * @throws DataChangedException if the device has been changed meanwhile
	 */
	public Config saveDraft(DSLContext dsl, DeviceDraft draft) {
		Device edited = draft.toDevice();
		Config newConfig = null;
		if (draft.getNewConfig() != null) {
			ConfigRepository configRepository = new ConfigRepository();
			long configId = configRepository.add(dsl, draft.getNewConfig());
			newConfig = configRepository.find(dsl, configId, false);
			edited = edited.toBuilder().lastConfigId(configId).build();
		}
		this.save(dsl, draft.getOriginal(), edited);
		new DiagnosticResultRepository().save(dsl, draft.getId(), draft.getDiagnosticResults().values());
		return newConfig;
	}

	private void saveModules(DSLContext dsl, long deviceId, List<Module> original, List<Module> edited) {
		Map<Long, Module> originalById = new HashMap<>();
		for (Module module : original) {
			originalById.put(module.id(), module);
		}
		Set<Long> keptIds = new HashSet<>();
		for (Module module : edited) {
			keptIds.add(module.id());
		}
		List<Long> deletedIds = new ArrayList<>(originalById.keySet());
		deletedIds.removeIf(keptIds::contains);
		if (!deletedIds.isEmpty()) {
			dsl.deleteFrom(MODULE).where(MODULE.ID.in(deletedIds), MODULE.DEVICE.eq(deviceId)).execute();
		}
		for (Module module : edited) {
			if (module.id() == 0) {
				insertModule(dsl, deviceId, module);
			}
			else if (!module.equals(originalById.get(module.id()))) {
				dsl.update(MODULE)
					.set(MODULE.SLOT, module.slot())
					.set(MODULE.PART_NUMBER, module.partNumber())
					.set(MODULE.SERIAL_NUMBER, module.serialNumber())
					.set(MODULE.FIRST_SEEN_DATE, module.firstSeenDate())
					.set(MODULE.LAST_SEEN_DATE, module.lastSeenDate())
					.set(MODULE.REMOVED, module.removed())
					.where(MODULE.ID.eq(module.id()), MODULE.DEVICE.eq(deviceId))
					.execute();
			}
		}
	}

	private void saveAccesses(DSLContext dsl, long deviceId, List<DeviceAccess> original, List<DeviceAccess> edited) {
		if (original.equals(edited)) {
			return;
		}
		Map<String, DeviceAccess> originalByName = new HashMap<>();
		for (DeviceAccess access : original) {
			originalByName.put(access.accessName(), access);
		}
		Set<String> editedNames = new HashSet<>();
		Set<Long> usedSpecificIds = new HashSet<>();
		for (DeviceAccess access : edited) {
			editedNames.add(access.accessName());
			if (access.specificCredentialSet() != null) {
				usedSpecificIds.add(access.specificCredentialSet().id());
			}
			DeviceAccess before = originalByName.get(access.accessName());
			if (before == null) {
				insertAccess(dsl, deviceId, access);
			}
			else if (!before.equals(access)) {
				dsl.update(DEVICE_ACCESS)
					.set(accessValues(access))
					.where(DEVICE_ACCESS.DEVICE.eq(deviceId), DEVICE_ACCESS.ACCESS_NAME.eq(access.accessName()))
					.execute();
			}
		}
		List<String> removedNames = new ArrayList<>();
		Set<Long> obsoleteSpecificIds = new HashSet<>();
		for (DeviceAccess access : original) {
			if (!editedNames.contains(access.accessName())) {
				removedNames.add(access.accessName());
			}
			if (access.specificCredentialSet() != null && !usedSpecificIds.contains(access.specificCredentialSet().id())) {
				obsoleteSpecificIds.add(access.specificCredentialSet().id());
			}
		}
		if (!removedNames.isEmpty()) {
			dsl.deleteFrom(DEVICE_ACCESS)
				.where(DEVICE_ACCESS.DEVICE.eq(deviceId), DEVICE_ACCESS.ACCESS_NAME.in(removedNames))
				.execute();
		}
		// Once no access references them anymore (their removal would remove the accesses)
		new CredentialSetRepository().removeAll(dsl, obsoleteSpecificIds);
	}

	/**
	 * Removes a device, with everything attached to it (configurations, attributes and their
	 * long texts, accesses and their specific credential sets, results...).
	 *
	 * @param dsl the jOOQ context
	 * @param id the device ID
	 * @return true if the device was found and removed
	 */
	public boolean remove(DSLContext dsl, long id) {
		return this.remove(dsl, id, null);
	}

	/**
	 * Removes a device if it is at a given version, with everything attached to it (see {@link #remove(DSLContext, long)}).
	 *
	 * @param dsl the jOOQ context
	 * @param id the device ID
	 * @param expectedVersion the version the device must be at, or null for any
	 * @return true if the device was found (at that version) and removed
	 */
	public boolean remove(DSLContext dsl, long id, Long expectedVersion) {
		List<Long> specificIds = dsl.select(DEVICE_ACCESS.SPECIFIC_CREDENTIAL_SET).from(DEVICE_ACCESS)
			.where(DEVICE_ACCESS.DEVICE.eq(id), DEVICE_ACCESS.SPECIFIC_CREDENTIAL_SET.isNotNull())
			.fetch(DEVICE_ACCESS.SPECIFIC_CREDENTIAL_SET);
		// The long texts are referenced by the rows of the device: they have to be removed explicitly
		List<Long> longTextIds = new ArrayList<>();
		longTextIds.addAll(dsl.select(DEVICE_ATTRIBUTE.LONG_TEXT).from(DEVICE_ATTRIBUTE)
			.where(DEVICE_ATTRIBUTE.DEVICE.eq(id), DEVICE_ATTRIBUTE.LONG_TEXT.isNotNull()).fetch(DEVICE_ATTRIBUTE.LONG_TEXT));
		longTextIds.addAll(dsl.select(CONFIG_ATTRIBUTE.LONG_TEXT).from(CONFIG_ATTRIBUTE)
			.join(CONFIG).on(CONFIG.ID.eq(CONFIG_ATTRIBUTE.CONFIG))
			.where(CONFIG.DEVICE.eq(id), CONFIG_ATTRIBUTE.LONG_TEXT.isNotNull()).fetch(CONFIG_ATTRIBUTE.LONG_TEXT));
		longTextIds.addAll(dsl.select(DIAGNOSTIC_RESULT.LONG_TEXT).from(DIAGNOSTIC_RESULT)
			.where(DIAGNOSTIC_RESULT.DEVICE.eq(id), DIAGNOSTIC_RESULT.LONG_TEXT.isNotNull()).fetch(DIAGNOSTIC_RESULT.LONG_TEXT));
		if (dsl.deleteFrom(DEVICE).where(DEVICE.ID.eq(id))
			.and(expectedVersion == null ? DSL.trueCondition() : DEVICE.VERSION.cast(Long.class).eq(expectedVersion)).execute() == 0) {
			return false;
		}
		deleteLongTexts(dsl, longTextIds);
		new CredentialSetRepository().removeAll(dsl, specificIds);
		return true;
	}

	/**
	 * Removes long texts.
	 *
	 * @param dsl the jOOQ context
	 * @param ids the IDs of the long texts
	 */
	public static void deleteLongTexts(DSLContext dsl, Collection<Long> ids) {
		if (!ids.isEmpty()) {
			dsl.deleteFrom(LONG_TEXT_CONFIGURATION).where(LONG_TEXT_CONFIGURATION.ID.in(ids)).execute();
		}
	}

	/**
	 * Stores a long text.
	 *
	 * @param dsl the jOOQ context
	 * @param text the text
	 * @return the ID of the long text
	 */
	public static long insertLongText(DSLContext dsl, String text) {
		return dsl.insertInto(LONG_TEXT_CONFIGURATION)
			.set(LONG_TEXT_CONFIGURATION.TEXT, text)
			.returning(LONG_TEXT_CONFIGURATION.ID)
			.fetchOne(LONG_TEXT_CONFIGURATION.ID);
	}

	private static void insertTags(DSLContext dsl, long deviceId, Set<String> tags) {
		for (String tag : tags) {
			dsl.insertInto(DEVICE_TAGS).set(DEVICE_TAGS.DEVICE, deviceId).set(DEVICE_TAGS.TAG, tag).execute();
		}
	}

	private static void insertVirtualDevices(DSLContext dsl, long deviceId, Set<String> virtualDevices) {
		for (String virtualDevice : virtualDevices) {
			dsl.insertInto(DEVICE_VIRTUAL_DEVICES)
				.set(DEVICE_VIRTUAL_DEVICES.DEVICE, deviceId)
				.set(DEVICE_VIRTUAL_DEVICES.VIRTUAL_DEVICES, virtualDevice)
				.execute();
		}
	}

	private static void insertVrfInstances(DSLContext dsl, long deviceId, Set<String> vrfInstances) {
		for (String vrfInstance : vrfInstances) {
			dsl.insertInto(DEVICE_VRF_INSTANCES)
				.set(DEVICE_VRF_INSTANCES.DEVICE, deviceId)
				.set(DEVICE_VRF_INSTANCES.VRF_INSTANCES, vrfInstance)
				.execute();
		}
	}

	private static void deleteAttributes(DSLContext dsl, long deviceId) {
		List<Long> longTextIds = new ArrayList<>(dsl.deleteFrom(DEVICE_ATTRIBUTE).where(DEVICE_ATTRIBUTE.DEVICE.eq(deviceId))
			.returning(DEVICE_ATTRIBUTE.LONG_TEXT)
			.fetch(DEVICE_ATTRIBUTE.LONG_TEXT));
		longTextIds.removeIf(Objects::isNull);
		deleteLongTexts(dsl, longTextIds);
	}

	private static void insertAttributes(DSLContext dsl, long deviceId, List<DeviceAttribute> attributes) {
		for (DeviceAttribute attribute : attributes) {
			var insert = dsl.insertInto(DEVICE_ATTRIBUTE)
				.set(DEVICE_ATTRIBUTE.DEVICE, deviceId)
				.set(DEVICE_ATTRIBUTE.NAME, attribute.name());
			switch (attribute) {
				case DeviceTextAttribute a -> insert.set(DEVICE_ATTRIBUTE.TYPE, TEXT).set(DEVICE_ATTRIBUTE.TEXT, a.text()).execute();
				case DeviceNumericAttribute a -> insert.set(DEVICE_ATTRIBUTE.TYPE, NUMERIC).set(DEVICE_ATTRIBUTE.NUMBER, a.number()).execute();
				case DeviceBinaryAttribute a -> insert.set(DEVICE_ATTRIBUTE.TYPE, BINARY).set(DEVICE_ATTRIBUTE.ASSUMPTION, a.assumption()).execute();
				case DeviceLongTextAttribute a -> insert.set(DEVICE_ATTRIBUTE.TYPE, LONG_TEXT)
					.set(DEVICE_ATTRIBUTE.LONG_TEXT, insertLongText(dsl, a.text())).execute();
			}
		}
	}

	private static Map<Field<?>, Object> accessValues(DeviceAccess access) {
		Map<Field<?>, Object> values = new HashMap<>();
		values.put(DEVICE_ACCESS.ADDRESS, access.address());
		values.put(DEVICE_ACCESS.PORT, access.port());
		values.put(DEVICE_ACCESS.GLOBAL_CREDENTIAL_SET, access.globalCredentialSet() == null ? null : access.globalCredentialSet().id());
		values.put(DEVICE_ACCESS.SPECIFIC_CREDENTIAL_SET, access.specificCredentialSet() == null ? null : access.specificCredentialSet().id());
		values.put(DEVICE_ACCESS.SSH_HOST_KEY_VERIFICATION, name(access.sshHostKeyVerification()));
		values.put(DEVICE_ACCESS.SSH_TRUSTED_HOST_KEYS, access.sshTrustedHostKeys());
		values.put(DEVICE_ACCESS.SSH_TRUSTED_HOST_KEYS_SINCE, access.sshTrustedHostKeysSince());
		values.put(DEVICE_ACCESS.HTTPS_CA_TRUST_MODE, name(access.httpsCaTrustMode()));
		values.put(DEVICE_ACCESS.HTTPS_CUSTOM_CA_CERTIFICATE, access.httpsCustomCaCertificate());
		return values;
	}

	private static void insertAccess(DSLContext dsl, long deviceId, DeviceAccess access) {
		dsl.insertInto(DEVICE_ACCESS)
			.set(accessValues(access))
			.set(DEVICE_ACCESS.DEVICE, deviceId)
			.set(DEVICE_ACCESS.ACCESS_NAME, access.accessName())
			.execute();
	}

	private static void insertModule(DSLContext dsl, long deviceId, Module module) {
		dsl.insertInto(MODULE)
			.set(MODULE.DEVICE, deviceId)
			.set(MODULE.SLOT, module.slot())
			.set(MODULE.PART_NUMBER, module.partNumber())
			.set(MODULE.SERIAL_NUMBER, module.serialNumber())
			.set(MODULE.FIRST_SEEN_DATE, module.firstSeenDate())
			.set(MODULE.LAST_SEEN_DATE, module.lastSeenDate())
			.set(MODULE.REMOVED, module.removed())
			.execute();
	}

	private static void insertInterfaces(DSLContext dsl, long deviceId, List<NetworkInterface> interfaces) {
		for (NetworkInterface networkInterface : interfaces) {
			long interfaceId = dsl.insertInto(NETWORK_INTERFACE)
				.set(NETWORK_INTERFACE.DEVICE, deviceId)
				.set(NETWORK_INTERFACE.INTERFACE_NAME, networkInterface.interfaceName())
				.set(NETWORK_INTERFACE.DESCRIPTION, networkInterface.description())
				.set(NETWORK_INTERFACE.ENABLED, networkInterface.enabled())
				.set(NETWORK_INTERFACE.LEVEL3, networkInterface.level3())
				.set(NETWORK_INTERFACE.PHYSICAL_ADDRESS, networkInterface.physicalAddress().getLongAddress())
				.set(NETWORK_INTERFACE.VIRTUAL_DEVICE, networkInterface.virtualDevice())
				.set(NETWORK_INTERFACE.VRF_INSTANCE, networkInterface.vrfInstance())
				.returning(NETWORK_INTERFACE.ID)
				.fetchOne(NETWORK_INTERFACE.ID);
			for (Network4Address address : networkInterface.ip4Addresses()) {
				NetworkInterfaceIp4addressesRecord r = dsl.newRecord(NETWORK_INTERFACE_IP4ADDRESSES);
				r.setNetworkInterface(interfaceId);
				r.setAddress(address.getAddress());
				r.setPrefixLength(address.getPrefixLength());
				r.setAddressUsage(name(address.getAddressUsage()));
				r.insert();
			}
			for (Network6Address address : networkInterface.ip6Addresses()) {
				NetworkInterfaceIp6addressesRecord r = dsl.newRecord(NETWORK_INTERFACE_IP6ADDRESSES);
				r.setNetworkInterface(interfaceId);
				r.setAddress(address.getAddress());
				r.setPrefixLength(address.getPrefixLength());
				r.setAddressUsage(name(address.getAddressUsage()));
				r.insert();
			}
		}
	}
}
