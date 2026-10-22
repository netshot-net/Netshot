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
package net.netshot.netshot.platform.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;

import javax.sql.DataSource;

import org.hibernate.engine.jdbc.connections.spi.AbstractMultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;

/**
 * Hibernate connection provider, to handle the read-write, read-only and system connections to the
 * database: it gives Hibernate the connections of the {@link ConnectionPools}.
 */
public class CustomConnectionProvider extends AbstractMultiTenantConnectionProvider<TenantIdentifier> {

	private static final long serialVersionUID = 1L;

	/**
	 * Gives the connections of one pool, without auto-commit (as the c3p0 provider of Hibernate did).
	 */
	private static final class PoolConnectionProvider implements ConnectionProvider {

		private static final long serialVersionUID = 1L;

		private final transient DataSource dataSource;

		PoolConnectionProvider(DataSource dataSource) {
			this.dataSource = dataSource;
		}

		@Override
		public Connection getConnection() throws SQLException {
			Connection connection = this.dataSource.getConnection();
			if (connection.getAutoCommit()) {
				connection.setAutoCommit(false);
			}
			return connection;
		}

		@Override
		public void closeConnection(Connection connection) throws SQLException {
			connection.close();
		}

		@Override
		public boolean supportsAggressiveRelease() {
			return false;
		}

		@Override
		public boolean isUnwrappableAs(Class<?> unwrapType) {
			return unwrapType.isInstance(this) || unwrapType.isInstance(this.dataSource);
		}

		@Override
		public <T> T unwrap(Class<T> unwrapType) {
			if (unwrapType.isInstance(this)) {
				return unwrapType.cast(this);
			}
			if (unwrapType.isInstance(this.dataSource)) {
				return unwrapType.cast(this.dataSource);
			}
			throw new IllegalArgumentException("Cannot unwrap to " + unwrapType);
		}
	}

	/** The providers, by kind of connection. */
	private final Map<TenantIdentifier, ConnectionProvider> providerMap = new EnumMap<>(TenantIdentifier.class);

	/**
	 * Instantiates the provider.
	 *
	 * @param pools the connection pools
	 */
	public CustomConnectionProvider(ConnectionPools pools) {
		for (TenantIdentifier identifier : TenantIdentifier.values()) {
			this.providerMap.put(identifier, new PoolConnectionProvider(pools.get(identifier)));
		}
	}

	@Override
	protected ConnectionProvider getAnyConnectionProvider() {
		return this.providerMap.get(TenantIdentifier.READ_ONLY);
	}

	@Override
	protected ConnectionProvider selectConnectionProvider(TenantIdentifier tenantIdentifier) {
		return this.providerMap.get(tenantIdentifier);
	}
}
