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
package net.netshot.netshot.testing;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Map;

import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.ApiTokenRepository;
import net.netshot.netshot.api.rest.RestServer;
import net.netshot.netshot.platform.database.schema.Tables;
import net.netshot.netshot.testing.api.NetshotApiClient;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.quartz.Scheduler;
import org.quartz.impl.SchedulerRepository;


/**
 * Extension of {@link ApiTest} and {@link ContractTest}, registered after {@link DatabaseExtension}: before
 * the annotated class, starts the task manager and the REST server (waiting for it to listen); after it,
 * stops the server and clears the schedulers. For an {@link ApiTest}, also replaces the users by the API
 * tokens of {@link TestServer#API_TOKENS} before each test, and removes the tokens after it. Resolves the
 * {@link TestServer} and (admin) {@link NetshotApiClient} parameters.
 */
public final class RestServerExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback,
	ParameterResolver {

	/** How long to wait for the REST server to listen, in milliseconds. */
	private static final long START_TIMEOUT = 30_000;

	/** How long to wait for the running tasks to end after an {@link ApiTest} class, in milliseconds. */
	private static final long TASK_END_TIMEOUT = 30_000;

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		Class<?> testClass = context.getRequiredTestClass();
		if (!Kinds.usesRestServer(testClass)) {
			return;
		}
		TestWiring.get().taskManager().start();
		if (Kinds.isContract(testClass)) {
			// Tasks must be stored but never run: keep the schedulers in standby, and drop
			// any job left by another test class sharing the JVM.
			for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
				scheduler.standby();
				scheduler.clear();
			}
		}
		RestServer restServer = new RestServer(TestWiring.get().binder());
		restServer.start();
		context.getStore(Kinds.NAMESPACE).put(RestServer.class, restServer);
		waitUntilListening(server(context).apiUri());
	}

	@Override
	public void afterAll(ExtensionContext context) throws Exception {
		Class<?> testClass = context.getRequiredTestClass();
		if (!Kinds.usesRestServer(testClass)) {
			return;
		}
		RestServer restServer = context.getStore(Kinds.NAMESPACE).remove(RestServer.class, RestServer.class);
		if (restServer != null) {
			restServer.stop();
		}
		// Drop the tasks scheduled by this class, and let the running ones end, before the next class
		// initializes the database again (they would otherwise fail on a closed session).
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			scheduler.clear();
			long deadline = System.currentTimeMillis() + TASK_END_TIMEOUT;
			while (!scheduler.getCurrentlyExecutingJobs().isEmpty() && System.currentTimeMillis() < deadline) {
				Thread.sleep(100);
			}
			// Other test classes may rely on a running scheduler.
			scheduler.start();
		}
	}

	@Override
	public void beforeEach(ExtensionContext context) {
		if (!managesTokens(context)) {
			return;
		}
		ApiTokenRepository tokens = new ApiTokenRepository();
		TestWiring.get().transactions().run(dsl -> {
			// Remove the default admin account
			dsl.deleteFrom(Tables.USER).execute();
			for (Map.Entry<Integer, String> entry : TestServer.API_TOKENS.entrySet()) {
				String description = "Test Token - level %d".formatted(entry.getKey());
				tokens.add(dsl, ApiToken.of(description, entry.getValue(), entry.getKey()));
			}
		});
	}

	@Override
	public void afterEach(ExtensionContext context) {
		if (!managesTokens(context)) {
			return;
		}
		TestWiring.get().transactions().run(dsl -> dsl.deleteFrom(Tables.API_TOKEN).execute());
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
		Class<?> type = parameterContext.getParameter().getType();
		return type == TestServer.class || type == NetshotApiClient.class;
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
		TestServer server = server(extensionContext);
		if (parameterContext.getParameter().getType() == TestServer.class) {
			return server;
		}
		try {
			return server.client();
		}
		catch (Exception e) {
			throw new ParameterResolutionException("Unable to create the API client", e);
		}
	}

	/**
	 * Gets the server of the test class, from the store of the class or of an enclosing class.
	 *
	 * @param context the extension context
	 * @return the server
	 */
	private static TestServer server(ExtensionContext context) {
		TestServer server = context.getStore(Kinds.NAMESPACE).get(TestServer.class, TestServer.class);
		if (server == null) {
			throw new IllegalStateException("No test server: annotate the test class with @ApiTest or @ContractTest");
		}
		return server;
	}

	/**
	 * Tells whether the API tokens are managed around the current test: it belongs to an {@link ApiTest}
	 * class (possibly through enclosing classes).
	 *
	 * @param context the extension context of the test
	 * @return true to manage the tokens
	 */
	private static boolean managesTokens(ExtensionContext context) {
		for (ExtensionContext current = context; current != null; current = current.getParent().orElse(null)) {
			Class<?> testClass = current.getTestClass().orElse(null);
			if (testClass != null && Kinds.usesRestServer(testClass)) {
				return !Kinds.isContract(testClass);
			}
		}
		return false;
	}

	/**
	 * Waits for the REST server, started in its own thread, to accept connections.
	 *
	 * @param uri the URI of the API
	 * @throws InterruptedException if interrupted while waiting
	 */
	private static void waitUntilListening(URI uri) throws InterruptedException {
		long deadline = System.currentTimeMillis() + START_TIMEOUT;
		while (true) {
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), 200);
				return;
			}
			catch (IOException e) {
				if (System.currentTimeMillis() > deadline) {
					throw new IllegalStateException("The REST server doesn't listen on %s".formatted(uri), e);
				}
				Thread.sleep(50);
			}
		}
	}
}
