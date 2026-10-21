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

import org.hibernate.Session;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;

/**
 * Access to the database with jOOQ, while Hibernate is still used by the
 * features which are not migrated yet: the {@link DSLContext} works on the
 * connection of a Hibernate session, in its transaction (jOOQ never commits
 * nor rolls back), so that both can be used in the same unit of work.
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
	 * Gets a jOOQ context working on the connection of the given session. Before
	 * running each statement, the pending changes of the session are flushed, so
	 * that the statement sees them. The context must not be used after the session
	 * is closed.
	 *
	 * @param session the Hibernate session
	 * @return the jOOQ context
	 */
	public static DSLContext dsl(Session session) {
		Connection connection = session.doReturningWork(c -> c);
		DefaultConfiguration configuration = new DefaultConfiguration();
		configuration.set(connection);
		configuration.set(SQLDialect.POSTGRES);
		configuration.set(SETTINGS);
		configuration.set(new SessionFlushListener(session));
		return DSL.using(configuration);
	}

	/**
	 * Flushes the pending changes of the Hibernate session before a jOOQ statement.
	 */
	private static final class SessionFlushListener implements ExecuteListener {

		private static final long serialVersionUID = 1L;

		private final transient Session session;

		SessionFlushListener(Session session) {
			this.session = session;
		}

		@Override
		public void executeStart(ExecuteContext ctx) {
			if (this.session.isOpen() && this.session.getTransaction().isActive()) {
				this.session.flush();
			}
		}
	}
}
