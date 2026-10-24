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

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;

/**
 * Access to the database with jOOQ: the {@link DSLContext} works on a connection
 * given by {@link Transactions}, in its transaction (jOOQ never commits nor rolls back).
 */
public final class Jooq {

	static {
		// No banner nor tip of the day in the logs
		System.setProperty("org.jooq.no-logo", "true");
		System.setProperty("org.jooq.no-tips", "true");
	}

	/** The settings of all the contexts. */
	private static final Settings SETTINGS = new Settings()
		// The generated classes are not bound to a schema: the tables are found through the search path
		.withRenderSchema(false)
		.withExecuteLogging(false);

	private Jooq() {
		// Static helpers only
	}

	/**
	 * Gets a jOOQ context working on the given connection. The context must not be
	 * used after the connection is closed.
	 *
	 * @param connection the JDBC connection
	 * @return the jOOQ context
	 */
	public static DSLContext dsl(Connection connection) {
		DefaultConfiguration configuration = new DefaultConfiguration();
		configuration.set(connection);
		configuration.set(SQLDialect.POSTGRES);
		configuration.set(SETTINGS);
		return DSL.using(configuration);
	}
}
