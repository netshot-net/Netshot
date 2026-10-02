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
package net.netshot.netshot.work;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.WithDatabaseTest;

/**
 * Tests for {@link TaskLockManager}, in local (in-memory) and distributed (PostgreSQL advisory
 * locks) modes. Two distributed managers with their own DB connections simulate two cluster members.
 */
@DisplayName("Task and device locks (TaskLockManager)")
public class TaskLockManagerTest extends WithDatabaseTest {

	private String url;
	private String username;
	private String password;

	/** All the connections opened by the managers under test, most recent last. */
	private final List<Connection> openedConnections = new ArrayList<>();

	private final List<TaskLockManager> managers = new ArrayList<>();

	@BeforeEach
	void initDb() {
		Properties config = getDatabaseConfig("tasklocktest");
		this.url = config.getProperty("netshot.db.url");
		this.username = config.getProperty("netshot.db.username");
		this.password = config.getProperty("netshot.db.password");
	}

	@AfterEach
	void closeManagers() {
		for (TaskLockManager manager : this.managers) {
			manager.close();
		}
		this.managers.clear();
		this.openedConnections.clear();
	}

	private Connection openConnection() throws SQLException {
		return DriverManager.getConnection(this.url, this.username, this.password);
	}

	private TaskLockManager newDistributedManager() {
		Supplier<Connection> supplier = () -> {
			try {
				Connection connection = this.openConnection();
				this.openedConnections.add(connection);
				return connection;
			}
			catch (SQLException e) {
				throw new IllegalStateException(e);
			}
		};
		TaskLockManager manager = new TaskLockManager(true, supplier);
		this.managers.add(manager);
		return manager;
	}

	@Test
	@DisplayName("Local mode: in-memory exclusion, no DB connection")
	void localMode() throws SQLException {
		TaskLockManager manager = new TaskLockManager(false, () -> {
			throw new AssertionError("No DB connection should be opened in local mode");
		});
		Assertions.assertTrue(manager.tryLock(TaskLockManager.NS_DEVICE, 10, 1));
		Assertions.assertFalse(manager.tryLock(TaskLockManager.NS_DEVICE, 10, 2),
			"Lock held by task 1 must not be granted to task 2");
		Assertions.assertTrue(manager.tryLock(TaskLockManager.NS_SNAPSHOT, 10, 2),
			"Same ID in another namespace is another lock");
		Assertions.assertEquals(1L, manager.getLocalOwner(TaskLockManager.NS_DEVICE, 10));
		Assertions.assertTrue(manager.isHeld(TaskLockManager.NS_DEVICE, 10));

		manager.unlock(TaskLockManager.NS_DEVICE, 10, 2);
		Assertions.assertTrue(manager.isHeld(TaskLockManager.NS_DEVICE, 10),
			"Release by a non-owner must be ignored");

		manager.unlock(TaskLockManager.NS_DEVICE, 10, 1);
		Assertions.assertFalse(manager.isHeld(TaskLockManager.NS_DEVICE, 10));
		Assertions.assertTrue(manager.tryLock(TaskLockManager.NS_DEVICE, 10, 2));
	}

	@Test
	@DisplayName("Distributed mode: exclusion across members")
	void crossMemberExclusion() throws SQLException {
		TaskLockManager member1 = this.newDistributedManager();
		TaskLockManager member2 = this.newDistributedManager();

		Assertions.assertTrue(member1.tryLock(TaskLockManager.NS_TASK, 100, 100));
		Assertions.assertFalse(member2.tryLock(TaskLockManager.NS_TASK, 100, 100),
			"A task locked by member 1 must not be lockable by member 2");
		Assertions.assertTrue(member2.isHeld(TaskLockManager.NS_TASK, 100),
			"Member 2 must see the lock held by member 1");
		Assertions.assertNull(member2.getLocalOwner(TaskLockManager.NS_TASK, 100));
		Assertions.assertFalse(member2.isHeld(TaskLockManager.NS_TASK, 101));

		member1.unlock(TaskLockManager.NS_TASK, 100, 100);
		Assertions.assertFalse(member2.isHeld(TaskLockManager.NS_TASK, 100));
		Assertions.assertTrue(member2.tryLock(TaskLockManager.NS_TASK, 100, 100));
	}

	@Test
	@DisplayName("Distributed mode: exclusion within a member despite re-entrant DB locks")
	void sameMemberExclusion() throws SQLException {
		TaskLockManager member1 = this.newDistributedManager();
		TaskLockManager member2 = this.newDistributedManager();

		Assertions.assertTrue(member1.tryLock(TaskLockManager.NS_DEVICE, 20, 1));
		Assertions.assertFalse(member1.tryLock(TaskLockManager.NS_DEVICE, 20, 2),
			"Two local tasks must not share a lock (DB advisory locks are re-entrant per session)");
		member1.unlock(TaskLockManager.NS_DEVICE, 20, 1);
		Assertions.assertFalse(member2.isHeld(TaskLockManager.NS_DEVICE, 20),
			"The DB lock must be fully released");
	}

	@Test
	@DisplayName("Distributed mode: locks are released when the member's connection goes away")
	void releasedOnConnectionClose() throws SQLException {
		TaskLockManager member1 = this.newDistributedManager();
		TaskLockManager member2 = this.newDistributedManager();

		Assertions.assertTrue(member1.tryLock(TaskLockManager.NS_TASK, 200, 200));
		member1.close();
		Assertions.assertTrue(member2.tryLock(TaskLockManager.NS_TASK, 200, 200),
			"The lock must be released by the database when the holder's connection is closed");
	}

	@Test
	@DisplayName("Distributed mode: held locks are re-acquired after a reconnection")
	void reacquiredAfterReconnection() throws Exception {
		TaskLockManager member1 = this.newDistributedManager();
		TaskLockManager member2 = this.newDistributedManager();

		Assertions.assertTrue(member1.tryLock(TaskLockManager.NS_TASK, 300, 300));
		Connection member1Connection = this.openedConnections.get(0);
		int member1Pid;
		try (Statement statement = member1Connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT pg_backend_pid()")) {
			result.next();
			member1Pid = result.getInt(1);
		}

		// Kill member 1's DB session (as the server would after missed TCP keepalives)
		try (Connection admin = this.openConnection(); Statement statement = admin.createStatement()) {
			statement.execute("SELECT pg_terminate_backend(%d)".formatted(member1Pid));
		}
		Thread.sleep(500);
		Assertions.assertFalse(member2.isHeld(TaskLockManager.NS_TASK, 300),
			"The lock must be gone with member 1's session");

		// Periodic check detects the broken connection, reconnects and re-acquires the lock
		member1.check();
		Assertions.assertTrue(member2.isHeld(TaskLockManager.NS_TASK, 300),
			"Member 1 must have re-acquired its lock after reconnection");
		Assertions.assertFalse(member2.tryLock(TaskLockManager.NS_TASK, 300, 300));
	}
}
