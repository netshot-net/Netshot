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
package net.netshot.netshot;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Properties;

import org.hibernate.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.quartz.Scheduler;
import org.quartz.impl.SchedulerRepository;

import jakarta.ws.rs.core.UriBuilder;
import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.api.rest.RestServer;
import net.netshot.netshot.boot.Wiring;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.engine.TaskManager;

/**
 * Base of the tests which call the REST API over HTTP: starts Netshot (database, tasks, REST server on a
 * free local port) for the test class, stops the REST server after it, and gives each test an API client
 * authenticated with an admin API token.
 */
public abstract class WithRestServerTest extends WithDatabaseTest {

	/** An ID which matches no object. */
	protected static final long UNKNOWN_ID = 99999999;

	/** API tokens created before each test, by permission level. */
	protected static final Map<Integer, String> API_TOKENS = Map.of(
		UiUser.LEVEL_READONLY, "mwAPEe0mQlBvKuUYYS5MiFsmnVRWZpca",
		UiUser.LEVEL_OPERATOR, "vMLKSPWMq9hFRtKkhXbvHhveXEn3Y4BZ",
		UiUser.LEVEL_READWRITE, "rCSrr3S3PGsLmi54HFw6a3PbZClpCivk",
		UiUser.LEVEL_EXECUTEREADWRITE, "PXlJ4r8VvTObn6hyknI1lwOoxEPJjCDO",
		UiUser.LEVEL_ADMIN, "hLXg3ABu1qWJPle3Z3Z22X0aLkx5T354"
	);

	/** URL of the REST API of the current test class. */
	protected static String apiUrl;

	protected static void createApiTokens() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			// Remove default admin account
			session
				.createMutationQuery("delete from net.netshot.netshot.aaa.user.UiUser")
				.executeUpdate();
			for (Map.Entry<Integer, String> entry : API_TOKENS.entrySet()) {
				String description = "Test Token - level %d".formatted(entry.getKey());
				ApiToken token = new ApiToken(description, entry.getValue(), entry.getKey());
				session.persist(token);
			}
			session.getTransaction().commit();
		}
	}

	protected static Properties getNetshotConfig() {
		Properties config = getDatabaseConfig("restservicetest");
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "INFO");
		config.setProperty("netshot.http.ssl.enabled", "false");
		URI uri = UriBuilder.fromUri(apiUrl).replacePath("/").build();
		config.setProperty("netshot.http.baseurl", uri.toString());
		// Very low value for session expiration testing
		config.setProperty("netshot.aaa.maxidletime", "10");
		return config;
	}

	@BeforeAll
	protected static void startRestServer() throws Exception {
		apiUrl = TestNetwork.freeLocalApiUrl();
		Netshot.initConfig(getNetshotConfig());
		Netshot.loadModuleConfigs();
		Database.update();
		Wiring.init(TestDrivers.registry());
		TaskManager.init(Wiring.get().taskJobFactory());
		RestServer.init(Wiring.get().binder());
		Thread.sleep(1000);
	}

	@AfterAll
	protected static void stopRestServer() throws Exception {
		RestServer.shutdown();
		// Drop the tasks scheduled by this class, and let the running ones end, before the next class
		// initializes the database again (they would otherwise fail on a closed session).
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			scheduler.clear();
			long deadline = System.currentTimeMillis() + 30_000;
			while (!scheduler.getCurrentlyExecutingJobs().isEmpty() && System.currentTimeMillis() < deadline) {
				Thread.sleep(100);
			}
			// Other test classes may rely on a running scheduler.
			scheduler.start();
		}
	}

	/** API client of the current test, authenticated with the admin token. */
	protected NetshotApiClient apiClient;

	@BeforeEach
	void createToken() throws URISyntaxException {
		createApiTokens();
		this.apiClient = new NetshotApiClient(apiUrl, API_TOKENS.get(UiUser.LEVEL_ADMIN));
	}

	@AfterEach
	void flushTokens() {
		try (Session session = Database.getSession()) {
			session.beginTransaction();
			session
				.createMutationQuery("delete from ApiToken")
				.executeUpdate();
			session.getTransaction().commit();
		}
	}
}
