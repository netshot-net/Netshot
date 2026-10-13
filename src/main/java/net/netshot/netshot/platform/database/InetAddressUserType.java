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

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

/**
 * Custom Hibernate type mapping a {@link InetAddress} (IPv4 or IPv6) to a
 * native PostgreSQL "inet" column. No schema DDL is generated from this type
 * (Liquibase owns the schema); it only controls how Hibernate binds/extracts
 * the JDBC value.
 */
public class InetAddressUserType implements UserType<InetAddress> {

	@Override
	public int getSqlType() {
		return Types.OTHER;
	}

	@Override
	public Class<InetAddress> returnedClass() {
		return InetAddress.class;
	}

	@Override
	public boolean equals(InetAddress x, InetAddress y) {
		return Objects.equals(x, y);
	}

	@Override
	public int hashCode(InetAddress x) {
		return x == null ? 0 : x.hashCode();
	}

	@Override
	public InetAddress nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
			throws SQLException {
		String text = rs.getString(position);
		if (rs.wasNull() || text == null) {
			return null;
		}
		try {
			return InetAddress.getByName(text);
		}
		catch (UnknownHostException e) {
			throw new HibernateException("Unable to parse cached IP address '%s'.".formatted(text), e);
		}
	}

	@Override
	public void nullSafeSet(PreparedStatement st, InetAddress value, int index, SharedSessionContractImplementor session)
			throws SQLException {
		if (value == null) {
			st.setNull(index, Types.OTHER);
			return;
		}
		PGobject pgObject = new PGobject();
		pgObject.setType("inet");
		pgObject.setValue(value.getHostAddress());
		st.setObject(index, pgObject);
	}

	@Override
	public InetAddress deepCopy(InetAddress value) {
		return value;
	}

	@Override
	public boolean isMutable() {
		return false;
	}

	@Override
	public Serializable disassemble(InetAddress value) {
		return value == null ? null : value.getHostAddress();
	}

	@Override
	public InetAddress assemble(Serializable cached, Object owner) {
		if (cached == null) {
			return null;
		}
		try {
			return InetAddress.getByName((String) cached);
		}
		catch (UnknownHostException e) {
			throw new HibernateException("Unable to parse cached IP address '%s'.".formatted(cached), e);
		}
	}
}
