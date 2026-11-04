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
package net.netshot.netshot.credentials;

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_CREDENTIAL_SET;
import static net.netshot.netshot.platform.database.schema.Tables.DOMAIN;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.impl.DSL;

import net.netshot.netshot.credentials.DeviceSnmpv3Community.AuthProtocol;
import net.netshot.netshot.credentials.DeviceSnmpv3Community.PrivProtocol;
import net.netshot.netshot.domain.DomainRepository;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.secret.LocalSecret;
import net.netshot.netshot.platform.secret.Secret;
import net.netshot.netshot.platform.database.schema.tables.Domain;

/**
 * The queries on the credential sets (global ones, and the ones specific to a device access).
 * The {@link DSLContext} is always the first parameter: the caller (the service) owns the transaction.
 */
public class CredentialSetRepository {

	/** The generated table of the credential sets (its name clashes with the {@link DeviceCredentialSet} record). */
	public static final net.netshot.netshot.platform.database.schema.tables.DeviceCredentialSet CS = DEVICE_CREDENTIAL_SET;

	/** The types of the SNMP credential sets. */
	private static final List<String> SNMP_TYPES = List.of(
		DeviceSnmpv1Community.class.getSimpleName(),
		DeviceSnmpv2cCommunity.class.getSimpleName(),
		DeviceSnmpv3Community.class.getSimpleName());

	/**
	 * The fields to select to build credential sets from (possibly aliased) tables.
	 *
	 * @param cs the credential set table
	 * @param d the domain table, joined on the domain of the credential set
	 * @return the fields
	 */
	public static List<SelectFieldOrAsterisk> fields(net.netshot.netshot.platform.database.schema.tables.DeviceCredentialSet cs, Domain d) {
		List<SelectFieldOrAsterisk> fields = new ArrayList<>(List.of(cs.fields()));
		fields.addAll(List.of(d.fields()));
		return fields;
	}

	/**
	 * Reads a field whose local value is stored in clear text (the username).
	 *
	 * @param r the record
	 * @param value the column of the local value
	 * @param vaultInstanceId the column of the Vault instance
	 * @param vaultPath the column of the Vault path
	 * @return the field, or null if it is not set
	 */
	private static Secret clear(Record r, Field<String> value, Field<Long> vaultInstanceId, Field<String> vaultPath) {
		return Secret.of(Secret.local(r.get(value)), r.get(vaultInstanceId), r.get(vaultPath));
	}

	/**
	 * Reads a field whose local value is stored encrypted (it is not decrypted).
	 *
	 * @param r the record
	 * @param value the column of the local value
	 * @param vaultInstanceId the column of the Vault instance
	 * @param vaultPath the column of the Vault path
	 * @return the field, or null if it is not set
	 */
	private static Secret encrypted(Record r, Field<LocalSecret> value, Field<Long> vaultInstanceId, Field<String> vaultPath) {
		return Secret.of(r.get(value), r.get(vaultInstanceId), r.get(vaultPath));
	}

	/**
	 * Builds a credential set from the columns of (possibly aliased, possibly outer joined) tables.
	 *
	 * @param r the record
	 * @param cs the credential set table
	 * @param d the domain table, joined on the domain of the credential set
	 * @return the credential set, or null if the record has none (outer join)
	 * @throws IllegalStateException if the type of the credential set is unknown
	 */
	public static DeviceCredentialSet toCredentialSet(Record r,
			net.netshot.netshot.platform.database.schema.tables.DeviceCredentialSet cs, Domain d) {
		Long id = r.get(cs.ID);
		if (id == null) {
			return null;
		}
		String type = r.get(cs.DTYPE);
		int version = r.get(cs.VERSION);
		Instant changeDate = r.get(cs.CHANGE_DATE);
		String name = r.get(cs.NAME);
		net.netshot.netshot.domain.Domain domain = DomainRepository.toDomain(r, d);
		boolean deviceSpecific = Boolean.TRUE.equals(r.get(cs.DEVICE_SPECIFIC));
		return switch (type) {
			case "DeviceSshAccount" -> new DeviceSshAccount(id, version, changeDate, name, domain, deviceSpecific,
				clear(r, cs.USERNAME, cs.USERNAME_VAULT_INSTANCE_ID, cs.USERNAME_VAULT_PATH),
				encrypted(r, cs.PASSWORD, cs.PASSWORD_VAULT_INSTANCE_ID, cs.PASSWORD_VAULT_PATH),
				encrypted(r, cs.SUPER_PASSWORD, cs.SUPER_PASSWORD_VAULT_INSTANCE_ID, cs.SUPER_PASSWORD_VAULT_PATH));
			case "DeviceSshKeyAccount" -> new DeviceSshKeyAccount(id, version, changeDate, name, domain, deviceSpecific,
				clear(r, cs.USERNAME, cs.USERNAME_VAULT_INSTANCE_ID, cs.USERNAME_VAULT_PATH),
				encrypted(r, cs.PASSWORD, cs.PASSWORD_VAULT_INSTANCE_ID, cs.PASSWORD_VAULT_PATH),
				encrypted(r, cs.SUPER_PASSWORD, cs.SUPER_PASSWORD_VAULT_INSTANCE_ID, cs.SUPER_PASSWORD_VAULT_PATH),
				encrypted(r, cs.PRIVATE_KEY, cs.PRIVATE_KEY_VAULT_INSTANCE_ID, cs.PRIVATE_KEY_VAULT_PATH));
			case "DeviceTelnetAccount" -> new DeviceTelnetAccount(id, version, changeDate, name, domain, deviceSpecific,
				clear(r, cs.USERNAME, cs.USERNAME_VAULT_INSTANCE_ID, cs.USERNAME_VAULT_PATH),
				encrypted(r, cs.PASSWORD, cs.PASSWORD_VAULT_INSTANCE_ID, cs.PASSWORD_VAULT_PATH),
				encrypted(r, cs.SUPER_PASSWORD, cs.SUPER_PASSWORD_VAULT_INSTANCE_ID, cs.SUPER_PASSWORD_VAULT_PATH));
			case "DeviceHttpAccount" -> new DeviceHttpAccount(id, version, changeDate, name, domain, deviceSpecific,
				clear(r, cs.USERNAME, cs.USERNAME_VAULT_INSTANCE_ID, cs.USERNAME_VAULT_PATH),
				encrypted(r, cs.PASSWORD, cs.PASSWORD_VAULT_INSTANCE_ID, cs.PASSWORD_VAULT_PATH));
			case "DeviceSnmpv1Community" -> new DeviceSnmpv1Community(id, version, changeDate, name, domain, deviceSpecific,
				encrypted(r, cs.COMMUNITY, cs.COMMUNITY_VAULT_INSTANCE_ID, cs.COMMUNITY_VAULT_PATH));
			case "DeviceSnmpv2cCommunity" -> new DeviceSnmpv2cCommunity(id, version, changeDate, name, domain, deviceSpecific,
				encrypted(r, cs.COMMUNITY, cs.COMMUNITY_VAULT_INSTANCE_ID, cs.COMMUNITY_VAULT_PATH));
			case "DeviceSnmpv3Community" -> new DeviceSnmpv3Community(id, version, changeDate, name, domain, deviceSpecific,
				encrypted(r, cs.COMMUNITY, cs.COMMUNITY_VAULT_INSTANCE_ID, cs.COMMUNITY_VAULT_PATH),
				clear(r, cs.USERNAME, cs.USERNAME_VAULT_INSTANCE_ID, cs.USERNAME_VAULT_PATH),
				r.get(cs.AUTH_TYPE) == null ? null : AuthProtocol.valueOf(r.get(cs.AUTH_TYPE)),
				encrypted(r, cs.AUTH_KEY, cs.AUTH_KEY_VAULT_INSTANCE_ID, cs.AUTH_KEY_VAULT_PATH),
				r.get(cs.PRIV_TYPE) == null ? null : PrivProtocol.valueOf(r.get(cs.PRIV_TYPE)),
				encrypted(r, cs.PRIV_KEY, cs.PRIV_KEY_VAULT_INSTANCE_ID, cs.PRIV_KEY_VAULT_PATH));
			default -> throw new IllegalStateException("Unknown type of credential set '%s'".formatted(type));
		};
	}

	private static DeviceCredentialSet toCredentialSet(Record r) {
		return toCredentialSet(r, CS, DOMAIN);
	}

	/**
	 * The time of a change, to the second (as the former Hibernate interceptor did).
	 *
	 * @return the time
	 */
	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.SECONDS);
	}

	/**
	 * Sets the columns of a field whose local value is stored in clear text (the username).
	 *
	 * @param values the values of the columns
	 * @param value the column of the local value
	 * @param vaultInstanceId the column of the Vault instance
	 * @param vaultPath the column of the Vault path
	 * @param secret the field
	 */
	private static void putClear(Map<Field<?>, Object> values, Field<String> value, Field<Long> vaultInstanceId, Field<String> vaultPath,
			Secret secret) {
		LocalSecret local = Secret.localValue(secret);
		values.put(value, local == null ? null : local.reveal());
		values.put(vaultInstanceId, Secret.vaultInstanceId(secret));
		values.put(vaultPath, Secret.vaultPath(secret));
	}

	/**
	 * Sets the columns of a field whose local value is stored encrypted.
	 *
	 * @param values the values of the columns
	 * @param value the column of the local value
	 * @param vaultInstanceId the column of the Vault instance
	 * @param vaultPath the column of the Vault path
	 * @param secret the field
	 */
	private static void putEncrypted(Map<Field<?>, Object> values, Field<LocalSecret> value, Field<Long> vaultInstanceId,
			Field<String> vaultPath, Secret secret) {
		values.put(value, Secret.localValue(secret));
		values.put(vaultInstanceId, Secret.vaultInstanceId(secret));
		values.put(vaultPath, Secret.vaultPath(secret));
	}

	/**
	 * The values of the columns of a credential set, but its ID, version and change date.
	 * The columns which don't apply to its type are null.
	 *
	 * @param credentialSet the credential set
	 * @return the values, by column
	 */
	private static Map<Field<?>, Object> values(DeviceCredentialSet credentialSet) {
		Map<Field<?>, Object> values = new LinkedHashMap<>();
		for (Field<?> field : CS.fields()) {
			values.put(field, null);
		}
		values.remove(CS.ID);
		values.remove(CS.VERSION);
		values.remove(CS.CHANGE_DATE);
		values.put(CS.DTYPE, credentialSet.typeName());
		values.put(CS.NAME, credentialSet.name());
		values.put(CS.MGMT_DOMAIN, credentialSet.mgmtDomain() == null ? null : credentialSet.mgmtDomain().id());
		values.put(CS.DEVICE_SPECIFIC, credentialSet.deviceSpecific());
		if (credentialSet instanceof DeviceCliAccount cli) {
			putClear(values, CS.USERNAME, CS.USERNAME_VAULT_INSTANCE_ID, CS.USERNAME_VAULT_PATH, cli.username());
			putEncrypted(values, CS.PASSWORD, CS.PASSWORD_VAULT_INSTANCE_ID, CS.PASSWORD_VAULT_PATH, cli.password());
			putEncrypted(values, CS.SUPER_PASSWORD, CS.SUPER_PASSWORD_VAULT_INSTANCE_ID, CS.SUPER_PASSWORD_VAULT_PATH, cli.superPassword());
		}
		if (credentialSet instanceof DeviceSshKeyAccount key) {
			putEncrypted(values, CS.PRIVATE_KEY, CS.PRIVATE_KEY_VAULT_INSTANCE_ID, CS.PRIVATE_KEY_VAULT_PATH, key.privateKey());
		}
		if (credentialSet instanceof DeviceHttpAccount http) {
			putClear(values, CS.USERNAME, CS.USERNAME_VAULT_INSTANCE_ID, CS.USERNAME_VAULT_PATH, http.username());
			putEncrypted(values, CS.PASSWORD, CS.PASSWORD_VAULT_INSTANCE_ID, CS.PASSWORD_VAULT_PATH, http.password());
		}
		if (credentialSet instanceof DeviceSnmpCommunity snmp) {
			putEncrypted(values, CS.COMMUNITY, CS.COMMUNITY_VAULT_INSTANCE_ID, CS.COMMUNITY_VAULT_PATH, snmp.community());
		}
		if (credentialSet instanceof DeviceSnmpv3Community v3) {
			putClear(values, CS.USERNAME, CS.USERNAME_VAULT_INSTANCE_ID, CS.USERNAME_VAULT_PATH, v3.username());
			values.put(CS.AUTH_TYPE, v3.authType() == null ? null : v3.authType().name());
			putEncrypted(values, CS.AUTH_KEY, CS.AUTH_KEY_VAULT_INSTANCE_ID, CS.AUTH_KEY_VAULT_PATH, v3.authKey());
			values.put(CS.PRIV_TYPE, v3.privType() == null ? null : v3.privType().name());
			putEncrypted(values, CS.PRIV_KEY, CS.PRIV_KEY_VAULT_INSTANCE_ID, CS.PRIV_KEY_VAULT_PATH, v3.privKey());
		}
		return values;
	}

	private List<DeviceCredentialSet> findWhere(DSLContext dsl, Condition condition, Page page) {
		return page.apply(dsl.select(fields(CS, DOMAIN)).from(CS).leftJoin(DOMAIN).on(DOMAIN.ID.eq(CS.MGMT_DOMAIN))
				.where(condition).orderBy(CS.ID.asc()))
			.fetch(CredentialSetRepository::toCredentialSet);
	}

	/** The fields the credential set lists can be filtered and sorted on (the v1 name of the type is exposed). */
	static final QueryMapping LIST = QueryMapping.builder(CS.ID)
		.string("name", CS.NAME, Option.SORTABLE, Option.SEARCHABLE)
		.enumeration("type", DSL.case_(CS.DTYPE)
			.when("DeviceSshAccount", "SSH").when("DeviceSshKeyAccount", "SSH_KEY").when("DeviceTelnetAccount", "TELNET")
			.when("DeviceHttpAccount", "HTTP").when("DeviceSnmpv1Community", "SNMP_V1").when("DeviceSnmpv2cCommunity", "SNMP_V2C")
			.when("DeviceSnmpv3Community", "SNMP_V3").else_(CS.DTYPE),
			List.of("SSH", "SSH_KEY", "TELNET", "HTTP", "SNMP_V1", "SNMP_V2C", "SNMP_V3"), Option.SORTABLE)
		.integer("mgmtDomain", CS.MGMT_DOMAIN)
		.bool("deviceSpecific", CS.DEVICE_SPECIFIC)
		.string("username", CS.USERNAME, Option.SORTABLE, Option.SEARCHABLE)
		.date("changeDate", CS.CHANGE_DATE, Option.SORTABLE)
		.build();

	/**
	 * Builds the condition of a list query: its filters and search, on the global credential sets unless the
	 * query filters on {@code deviceSpecific}.
	 *
	 * @param query the query
	 * @return the condition
	 */
	private static Condition listCondition(ListQuery query) {
		boolean scoped = query.criteria().stream().anyMatch(c -> "deviceSpecific".equals(c.field()));
		return LIST.condition(query).and(scoped ? DSL.trueCondition() : CS.DEVICE_SPECIFIC.isFalse());
	}

	/**
	 * Lists the credential sets matching a query (the global ones, unless the query filters on
	 * {@code deviceSpecific}).
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the credential sets of the window of the query
	 */
	public List<DeviceCredentialSet> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(fields(CS, DOMAIN)).from(CS).leftJoin(DOMAIN).on(DOMAIN.ID.eq(CS.MGMT_DOMAIN))
				.where(listCondition(query)).orderBy(LIST.orderBy(query)))
			.fetch(CredentialSetRepository::toCredentialSet);
	}

	/**
	 * Counts the credential sets matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of credential sets
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(CS, listCondition(query));
	}

	/**
	 * Lists the global credential sets (not the ones specific to a device access), by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the credential sets
	 */
	public List<DeviceCredentialSet> findGlobal(DSLContext dsl, Page page) {
		return this.findWhere(dsl, CS.DEVICE_SPECIFIC.isFalse(), page);
	}

	/**
	 * Finds a credential set of any kind, global or specific to a device access.
	 *
	 * @param dsl the jOOQ context
	 * @param id the credential set ID
	 * @return the credential set, or null if there is no such credential set
	 */
	public DeviceCredentialSet find(DSLContext dsl, long id) {
		List<DeviceCredentialSet> found = this.findWhere(dsl, CS.ID.eq(id), Page.ALL);
		return found.isEmpty() ? null : found.get(0);
	}

	/**
	 * Finds credential sets by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param ids the credential set IDs
	 * @return the credential sets which exist, by ID
	 */
	public List<DeviceCredentialSet> findAll(DSLContext dsl, Collection<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return this.findWhere(dsl, CS.ID.in(ids), Page.ALL);
	}

	/**
	 * Finds the global credential sets which can be tried on the devices of a domain (those
	 * of the domain and those of no particular domain), by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param domainId the domain ID, or null for a device without domain
	 * @return the credential sets
	 */
	public List<DeviceCredentialSet> findUsableInDomain(DSLContext dsl, Long domainId) {
		Condition inDomain = domainId == null ? CS.MGMT_DOMAIN.isNull() : CS.MGMT_DOMAIN.isNull().or(CS.MGMT_DOMAIN.eq(domainId));
		return this.findWhere(dsl, CS.DEVICE_SPECIFIC.isFalse().and(inDomain), Page.ALL);
	}

	/**
	 * Finds the global SNMP communities usable to poll the devices of a domain (those of the
	 * domain and those of no particular domain), by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param domainId the domain ID, or null for a device without domain
	 * @return the SNMP communities
	 */
	public List<DeviceCredentialSet> findSnmpCommunities(DSLContext dsl, Long domainId) {
		Condition inDomain = domainId == null ? CS.MGMT_DOMAIN.isNull() : CS.MGMT_DOMAIN.isNull().or(CS.MGMT_DOMAIN.eq(domainId));
		return this.findWhere(dsl, CS.DEVICE_SPECIFIC.isFalse().and(CS.DTYPE.in(SNMP_TYPES)).and(inDomain), Page.ALL);
	}

	/**
	 * Finds the SNMP communities of a domain, and those of no particular domain, including the
	 * ones specific to device accesses (as the subnet scan always used them), by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param domainId the domain ID
	 * @return the SNMP communities
	 */
	public List<DeviceCredentialSet> findAllSnmpCommunities(DSLContext dsl, Long domainId) {
		Condition inDomain = domainId == null ? CS.MGMT_DOMAIN.isNull() : CS.MGMT_DOMAIN.isNull().or(CS.MGMT_DOMAIN.eq(domainId));
		return this.findWhere(dsl, CS.DTYPE.in(SNMP_TYPES).and(inDomain), Page.ALL);
	}

	/**
	 * Stores a new credential set (version 0, changed now).
	 *
	 * @param dsl the jOOQ context
	 * @param credentialSet the credential set (its ID, version and change date are ignored)
	 * @return the stored credential set
	 */
	public DeviceCredentialSet add(DSLContext dsl, DeviceCredentialSet credentialSet) {
		long id = dsl.insertInto(CS)
			.set(values(credentialSet))
			.set(CS.VERSION, 0)
			.set(CS.CHANGE_DATE, now())
			.returning(CS.ID)
			.fetchOne(CS.ID);
		return this.find(dsl, id);
	}

	/**
	 * Saves an existing credential set (its version is incremented, its change date set).
	 *
	 * @param dsl the jOOQ context
	 * @param credentialSet the credential set
	 * @return the saved credential set, or null if there is no credential set of this ID and type
	 */
	public DeviceCredentialSet save(DSLContext dsl, DeviceCredentialSet credentialSet) {
		return this.save(dsl, credentialSet, null);
	}

	/**
	 * Saves an existing credential set if it is still at a given version (its version is incremented, its
	 * change date set).
	 *
	 * @param dsl the jOOQ context
	 * @param credentialSet the credential set
	 * @param expectedVersion the version the credential set must be at, or null for any
	 * @return the saved credential set, or null if there is no credential set of this ID and type (at that version)
	 */
	public DeviceCredentialSet save(DSLContext dsl, DeviceCredentialSet credentialSet, Long expectedVersion) {
		int updated = dsl.update(CS)
			.set(values(credentialSet))
			.set(CS.VERSION, CS.VERSION.plus(1))
			.set(CS.CHANGE_DATE, now())
			.where(CS.ID.eq(credentialSet.id()), CS.DTYPE.eq(credentialSet.typeName()))
			.and(expectedVersion == null ? DSL.trueCondition() : CS.VERSION.cast(Long.class).eq(expectedVersion))
			.execute();
		return updated == 0 ? null : this.find(dsl, credentialSet.id());
	}

	/**
	 * Removes a credential set.
	 *
	 * @param dsl the jOOQ context
	 * @param id the credential set ID
	 * @return true if the credential set was found and removed
	 */
	public boolean remove(DSLContext dsl, long id) {
		return dsl.deleteFrom(CS).where(CS.ID.eq(id)).execute() > 0;
	}

	/**
	 * Removes a credential set if it is at a given version.
	 *
	 * @param dsl the jOOQ context
	 * @param id the credential set ID
	 * @param expectedVersion the version the credential set must be at, or null for any
	 * @return true if the credential set was found (at that version) and removed
	 */
	public boolean remove(DSLContext dsl, long id, Long expectedVersion) {
		return dsl.deleteFrom(CS).where(CS.ID.eq(id))
			.and(expectedVersion == null ? DSL.trueCondition() : CS.VERSION.cast(Long.class).eq(expectedVersion))
			.execute() > 0;
	}

	/**
	 * Removes credential sets.
	 *
	 * @param dsl the jOOQ context
	 * @param ids the credential set IDs
	 */
	public void removeAll(DSLContext dsl, Collection<Long> ids) {
		if (ids != null && !ids.isEmpty()) {
			dsl.deleteFrom(CS).where(CS.ID.in(ids)).execute();
		}
	}

}
