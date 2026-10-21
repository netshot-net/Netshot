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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.Objects;

import org.jooq.Binding;
import org.jooq.BindingGetResultSetContext;
import org.jooq.BindingGetSQLInputContext;
import org.jooq.BindingGetStatementContext;
import org.jooq.BindingRegisterContext;
import org.jooq.BindingSQLContext;
import org.jooq.BindingSetSQLOutputContext;
import org.jooq.BindingSetStatementContext;
import org.jooq.Converter;
import org.jooq.impl.DSL;

/**
 * jOOQ binding of the PostgreSQL {@code inet} columns to {@link InetAddress}
 * (the jOOQ counterpart of {@link InetAddressUserType}). Only the address is
 * kept: a prefix length stored in the value ({@code 10.0.0.0/8}) is dropped
 * when reading, as the prefix lengths live in their own columns.
 * Used by the generated classes, see the jOOQ code generation.
 */
public class InetBinding implements Binding<Object, InetAddress> {

	private static final Converter<Object, InetAddress> CONVERTER =
		Converter.ofNullable(Object.class, InetAddress.class, InetBinding::parse, InetAddress::getHostAddress);

	private static InetAddress parse(Object value) {
		String text = value.toString();
		int slash = text.indexOf('/');
		try {
			// A literal address: no name resolution
			return InetAddress.getByName(slash < 0 ? text : text.substring(0, slash));
		}
		catch (UnknownHostException e) {
			throw new IllegalArgumentException("Unable to parse IP address '%s'.".formatted(text), e);
		}
	}

	@Override
	public Converter<Object, InetAddress> converter() {
		return CONVERTER;
	}

	@Override
	public void sql(BindingSQLContext<InetAddress> ctx) throws SQLException {
		ctx.render().visit(DSL.val(ctx.convert(this.converter()).value())).sql("::inet");
	}

	@Override
	public void register(BindingRegisterContext<InetAddress> ctx) throws SQLException {
		ctx.statement().registerOutParameter(ctx.index(), Types.VARCHAR);
	}

	@Override
	public void set(BindingSetStatementContext<InetAddress> ctx) throws SQLException {
		ctx.statement().setString(ctx.index(), Objects.toString(ctx.convert(this.converter()).value(), null));
	}

	@Override
	public void set(BindingSetSQLOutputContext<InetAddress> ctx) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}

	@Override
	public void get(BindingGetResultSetContext<InetAddress> ctx) throws SQLException {
		ctx.convert(this.converter()).value(ctx.resultSet().getString(ctx.index()));
	}

	@Override
	public void get(BindingGetStatementContext<InetAddress> ctx) throws SQLException {
		ctx.convert(this.converter()).value(ctx.statement().getString(ctx.index()));
	}

	@Override
	public void get(BindingGetSQLInputContext<InetAddress> ctx) throws SQLException {
		throw new SQLFeatureNotSupportedException();
	}
}
