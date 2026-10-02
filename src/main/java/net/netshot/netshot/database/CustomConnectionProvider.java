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
package net.netshot.netshot.database;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.registry.internal.StandardServiceRegistryImpl;
import org.hibernate.c3p0.internal.C3P0ConnectionProvider;
import org.hibernate.cfg.C3p0Settings;
import org.hibernate.engine.jdbc.connections.spi.AbstractMultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

import net.netshot.netshot.Netshot;

/**
 * Custom connection provider, to handle read-write and read-only connections to the database.
 */
public class CustomConnectionProvider extends AbstractMultiTenantConnectionProvider<TenantIdentifier> {

	/** Map of ConnectionProvider objects (read-only and read-write). */
	private final Map<TenantIdentifier, ConnectionProvider> providerMap = new HashMap<>();

	private ConnectionProvider defaultProvider;

	public void registerConnectionProvider(TenantIdentifier identifier, Map<String, Object> props, boolean asDefaultProvider) {
		C3P0ConnectionProvider provider = new C3P0ConnectionProvider();
		provider.injectServices((StandardServiceRegistryImpl) new StandardServiceRegistryBuilder().build());
		Map<String, Object> configProps = new HashMap<>(props);
		configProps.put("hibernate.c3p0.dataSourceName", identifier.getSourceName());

		// Pool sizes can be set by the caller (e.g. for the small SYSTEM pool)
		configProps.putIfAbsent(C3p0Settings.C3P0_MIN_SIZE,
			Netshot.getConfig("netshot.db.pooler.minpoolsize", 5, 0, Integer.MAX_VALUE));
		configProps.putIfAbsent(C3p0Settings.C3P0_MAX_SIZE,
			Netshot.getConfig("netshot.db.pooler.maxpoolsize", 50, 0, Integer.MAX_VALUE));
		configProps.put("hibernate.c3p0.checkoutTimeout",
			Netshot.getConfig("netshot.db.pooler.checkouttimeout", 0, 0, Integer.MAX_VALUE));
		configProps.put(C3p0Settings.C3P0_TIMEOUT,
			Netshot.getConfig("netshot.db.pooler.maxidletimeout", 1800, 0, Integer.MAX_VALUE));
		configProps.put("hibernate.c3p0.maxConnectionAge",
			Netshot.getConfig("netshot.db.pooler.maxconnectionage", 0, 0, Integer.MAX_VALUE));
		configProps.put("hibernate.c3p0.testConnectionOnCheckout",
			Netshot.getConfig("netshot.db.pooler.testconnectiononcheckout", true));
		configProps.put("hibernate.c3p0.testConnectionOnCheckin",
			Netshot.getConfig("netshot.db.pooler.testconnectiononcheckin", false));
		configProps.put("hibernate.c3p0.idleConnectionTestPeriod",
			Netshot.getConfig("netshot.db.pooler.idleconnectiontestperiod", 0, 0, Integer.MAX_VALUE));
		configProps.put("hibernate.c3p0.connectionIsValidTimeout",
			Netshot.getConfig("netshot.db.pooler.connectionisvalidtimeout", 0, 0, Integer.MAX_VALUE));
		configProps.put(C3p0Settings.C3P0_MAX_STATEMENTS,
			Netshot.getConfig("netshot.db.pooler.maxstatements", 50, 0, Integer.MAX_VALUE));
		configProps.put("hibernate.c3p0.unreturnedConnectionTimeout",
			Netshot.getConfig("netshot.db.pooler.unreturnedconnectiontimeout", 1800, 0, Integer.MAX_VALUE));
		configProps.put("hibernate.c3p0.debugUnreturnedConnectionStackTraces", "true");
		provider.configure(configProps);
		this.providerMap.put(identifier, provider);
		if (asDefaultProvider) {
			this.defaultProvider = provider;
		}
	}

	@Override
	protected ConnectionProvider getAnyConnectionProvider() {
		return this.defaultProvider;
	}

	@Override
	protected ConnectionProvider selectConnectionProvider(TenantIdentifier tenantIdentifier) {
		ConnectionProvider cp = this.providerMap.get(tenantIdentifier);
		if (cp == null) {
			return this.defaultProvider;
		}
		return cp;
	}

}
