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
package net.netshot.netshot.vault;

import static net.netshot.netshot.platform.database.schema.Tables.VAULT_INSTANCE;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.QueryMapping;
import net.netshot.netshot.platform.database.QueryMapping.Option;
import net.netshot.netshot.platform.database.Versions;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance.AuthMethod;

/**
 * The queries on the Vault instances. The {@link DSLContext} is always the first parameter: the
 * caller (the service) owns the transaction.
 */
public class VaultInstanceRepository {

	/** The type (discriminator) of the HashiCorp Vault KV v2 instances. */
	static final String HASHICORP_KV2 = "HashicorpVaultKv2Instance";

	private static VaultInstance toVaultInstance(Record r) {
		String type = r.get(VAULT_INSTANCE.DTYPE);
		if (HASHICORP_KV2.equals(type)) {
			return new HashicorpVaultKv2Instance(r.get(VAULT_INSTANCE.ID), r.get(VAULT_INSTANCE.VERSION), r.get(VAULT_INSTANCE.CHANGE_DATE),
				r.get(VAULT_INSTANCE.NAME), r.get(VAULT_INSTANCE.BASE_URL), r.get(VAULT_INSTANCE.NAMESPACE),
				HttpsCaTrustMode.valueOf(r.get(VAULT_INSTANCE.HTTPS_CA_TRUST_MODE)), r.get(VAULT_INSTANCE.HTTPS_CUSTOM_CA_CERTIFICATE),
				r.get(VAULT_INSTANCE.KV_MOUNT_PATH),
				r.get(VAULT_INSTANCE.AUTH_METHOD) == null ? null : AuthMethod.valueOf(r.get(VAULT_INSTANCE.AUTH_METHOD)),
				r.get(VAULT_INSTANCE.APP_ROLE_MOUNT_PATH), r.get(VAULT_INSTANCE.APP_ROLE_ID), r.get(VAULT_INSTANCE.APP_ROLE_SECRET_ID),
				r.get(VAULT_INSTANCE.JWT_MOUNT_PATH), r.get(VAULT_INSTANCE.JWT_IDP_TOKEN_ENDPOINT), r.get(VAULT_INSTANCE.JWT_CLIENT_ID),
				r.get(VAULT_INSTANCE.JWT_CLIENT_SECRET), r.get(VAULT_INSTANCE.JWT_VAULT_ROLE), r.get(VAULT_INSTANCE.JWT_SCOPE));
		}
		throw new IllegalStateException("Unknown type of Vault instance '%s'".formatted(type));
	}

	/**
	 * The time of a change, to the second (as the former Hibernate interceptor did).
	 *
	 * @return the time
	 */
	static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.SECONDS);
	}

	/**
	 * Lists the Vault instances, by ID.
	 *
	 * @param dsl the jOOQ context
	 * @param page the window on the result
	 * @return the instances
	 */
	public List<VaultInstance> findAll(DSLContext dsl, Page page) {
		return page.apply(dsl.select(VAULT_INSTANCE.fields()).from(VAULT_INSTANCE).orderBy(VAULT_INSTANCE.ID))
			.fetch(VaultInstanceRepository::toVaultInstance);
	}

	/** The v1 name of the type of the HashiCorp Vault KV v2 instances. */
	public static final String TYPE_HASHICORP_KV2 = "HASHICORP_KV2";

	/** The fields the Vault instance lists can be filtered and sorted on. */
	static final QueryMapping LIST = QueryMapping.builder(VAULT_INSTANCE.ID)
		.string("name", VAULT_INSTANCE.NAME, Option.SORTABLE, Option.SEARCHABLE)
		.enumeration("type", DSL.case_(VAULT_INSTANCE.DTYPE).when(HASHICORP_KV2, TYPE_HASHICORP_KV2).else_(VAULT_INSTANCE.DTYPE),
			List.of(TYPE_HASHICORP_KV2), Option.SORTABLE)
		.string("baseUrl", VAULT_INSTANCE.BASE_URL, Option.SORTABLE, Option.SEARCHABLE)
		.string("namespace", VAULT_INSTANCE.NAMESPACE, Option.SEARCHABLE)
		.enumeration("authMethod", VAULT_INSTANCE.AUTH_METHOD, AuthMethod.class, Option.SORTABLE)
		.date("changeDate", VAULT_INSTANCE.CHANGE_DATE, Option.SORTABLE)
		.build();

	/**
	 * Lists the Vault instances matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query
	 * @return the instances of the window of the query
	 */
	public List<VaultInstance> findAll(DSLContext dsl, ListQuery query) {
		return query.page().apply(dsl.select(VAULT_INSTANCE.fields()).from(VAULT_INSTANCE)
			.where(LIST.condition(query)).orderBy(LIST.orderBy(query)))
			.fetch(VaultInstanceRepository::toVaultInstance);
	}

	/**
	 * Counts the Vault instances matching a query.
	 *
	 * @param dsl the jOOQ context
	 * @param query the query (its window is ignored)
	 * @return the number of instances
	 */
	public long count(DSLContext dsl, ListQuery query) {
		return dsl.fetchCount(VAULT_INSTANCE, LIST.condition(query));
	}

	/**
	 * Gets a Vault instance.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return the instance, or null if there is no such instance
	 */
	public VaultInstance find(DSLContext dsl, long id) {
		return dsl.select(VAULT_INSTANCE.fields()).from(VAULT_INSTANCE).where(VAULT_INSTANCE.ID.eq(id))
			.fetchOne(VaultInstanceRepository::toVaultInstance);
	}

	/**
	 * Stores a new Vault instance (version 0, changed now).
	 *
	 * @param dsl the jOOQ context
	 * @param vaultInstance the instance (its ID, version and change date are ignored)
	 * @return the stored instance
	 */
	public VaultInstance add(DSLContext dsl, VaultInstance vaultInstance) {
		long id = switch (vaultInstance) {
			case HashicorpVaultKv2Instance hc -> dsl.insertInto(VAULT_INSTANCE)
				.set(VAULT_INSTANCE.DTYPE, HASHICORP_KV2)
				.set(VAULT_INSTANCE.VERSION, 0)
				.set(VAULT_INSTANCE.CHANGE_DATE, now())
				.set(VAULT_INSTANCE.NAME, hc.name())
				.set(VAULT_INSTANCE.BASE_URL, hc.baseUrl())
				.set(VAULT_INSTANCE.NAMESPACE, hc.namespace())
				.set(VAULT_INSTANCE.HTTPS_CA_TRUST_MODE, hc.httpsCaTrustMode() == null ? null : hc.httpsCaTrustMode().name())
				.set(VAULT_INSTANCE.HTTPS_CUSTOM_CA_CERTIFICATE, hc.httpsCustomCaCertificate())
				.set(VAULT_INSTANCE.KV_MOUNT_PATH, hc.kvMountPath())
				.set(VAULT_INSTANCE.AUTH_METHOD, hc.authMethod() == null ? null : hc.authMethod().name())
				.set(VAULT_INSTANCE.APP_ROLE_MOUNT_PATH, hc.appRoleMountPath())
				.set(VAULT_INSTANCE.APP_ROLE_ID, hc.appRoleId())
				.set(VAULT_INSTANCE.APP_ROLE_SECRET_ID, hc.appRoleSecretId())
				.set(VAULT_INSTANCE.JWT_MOUNT_PATH, hc.jwtMountPath())
				.set(VAULT_INSTANCE.JWT_IDP_TOKEN_ENDPOINT, hc.jwtIdpTokenEndpoint())
				.set(VAULT_INSTANCE.JWT_CLIENT_ID, hc.jwtClientId())
				.set(VAULT_INSTANCE.JWT_CLIENT_SECRET, hc.jwtClientSecret())
				.set(VAULT_INSTANCE.JWT_VAULT_ROLE, hc.jwtVaultRole())
				.set(VAULT_INSTANCE.JWT_SCOPE, hc.jwtScope())
				.returning(VAULT_INSTANCE.ID)
				.fetchOne(VAULT_INSTANCE.ID);
		};
		return this.find(dsl, id);
	}

	/**
	 * Saves an existing Vault instance (its version is incremented, its change date set).
	 *
	 * @param dsl the jOOQ context
	 * @param vaultInstance the instance
	 * @return the saved instance, or null if no instance of this ID, type and version exists
	 */
	public VaultInstance save(DSLContext dsl, VaultInstance vaultInstance) {
		int updated = switch (vaultInstance) {
			case HashicorpVaultKv2Instance hc -> dsl.update(VAULT_INSTANCE)
				.set(VAULT_INSTANCE.VERSION, VAULT_INSTANCE.VERSION.plus(1))
				.set(VAULT_INSTANCE.CHANGE_DATE, now())
				.set(VAULT_INSTANCE.NAME, hc.name())
				.set(VAULT_INSTANCE.BASE_URL, hc.baseUrl())
				.set(VAULT_INSTANCE.NAMESPACE, hc.namespace())
				.set(VAULT_INSTANCE.HTTPS_CA_TRUST_MODE, hc.httpsCaTrustMode() == null ? null : hc.httpsCaTrustMode().name())
				.set(VAULT_INSTANCE.HTTPS_CUSTOM_CA_CERTIFICATE, hc.httpsCustomCaCertificate())
				.set(VAULT_INSTANCE.KV_MOUNT_PATH, hc.kvMountPath())
				.set(VAULT_INSTANCE.AUTH_METHOD, hc.authMethod() == null ? null : hc.authMethod().name())
				.set(VAULT_INSTANCE.APP_ROLE_MOUNT_PATH, hc.appRoleMountPath())
				.set(VAULT_INSTANCE.APP_ROLE_ID, hc.appRoleId())
				.set(VAULT_INSTANCE.APP_ROLE_SECRET_ID, hc.appRoleSecretId())
				.set(VAULT_INSTANCE.JWT_MOUNT_PATH, hc.jwtMountPath())
				.set(VAULT_INSTANCE.JWT_IDP_TOKEN_ENDPOINT, hc.jwtIdpTokenEndpoint())
				.set(VAULT_INSTANCE.JWT_CLIENT_ID, hc.jwtClientId())
				.set(VAULT_INSTANCE.JWT_CLIENT_SECRET, hc.jwtClientSecret())
				.set(VAULT_INSTANCE.JWT_VAULT_ROLE, hc.jwtVaultRole())
				.set(VAULT_INSTANCE.JWT_SCOPE, hc.jwtScope())
				.where(VAULT_INSTANCE.ID.eq(hc.id()), VAULT_INSTANCE.DTYPE.eq(HASHICORP_KV2), VAULT_INSTANCE.VERSION.eq(hc.version()))
				.execute();
		};
		return updated == 0 ? null : this.find(dsl, vaultInstance.id());
	}

	/**
	 * Locks the row of a Vault instance until the end of the transaction (see {@link Versions#lock}).
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return the version of the instance, or null if there is no such instance
	 */
	public Integer lock(DSLContext dsl, long id) {
		return Versions.lock(dsl, VAULT_INSTANCE, VAULT_INSTANCE.ID, VAULT_INSTANCE.VERSION, id);
	}

	/**
	 * Removes a Vault instance.
	 *
	 * @param dsl the jOOQ context
	 * @param id the ID
	 * @return true if the instance was found and removed
	 */
	public boolean remove(DSLContext dsl, long id) {
		return dsl.deleteFrom(VAULT_INSTANCE).where(VAULT_INSTANCE.ID.eq(id)).execute() > 0;
	}
}
