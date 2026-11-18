/**
 * Copyright 2013-present Netshot
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

import java.util.Map;

import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.ApiTokenRepository;
import net.netshot.netshot.platform.database.schema.Tables;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.quartz.Scheduler;
import org.quartz.impl.SchedulerRepository;

/**
 * Extension of {@link ApiTest} and {@link ContractTest}, registered after {@link DatabaseExtension}: before
 * the annotated class, starts the task manager and the REST server of the application (it listens once started; the
 * {@link DatabaseExtension} closes the application after the class). For an {@link ApiTest}, also replaces the users by the API
 * tokens of {@link TestServer#API_TOKENS} before each test, and removes the tokens after it. Resolves the
 * {@link TestServer} parameters (each API version builds its clients from it).
 */
public final class RestServerExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback,
	ParameterResolver {

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		Class<?> testClass = context.getRequiredTestClass();
		if (!Kinds.usesRestServer(testClass)) {
			return;
		}
		TestWiring.application().start("task-manager", "rest-server");
		// Tasks must be stored but never run (they would reach no device, and could hold locks
		// the cleanup of the next test needs): keep the schedulers in standby, and drop any job
		// left by another test class sharing the JVM.
		for (Scheduler scheduler : SchedulerRepository.getInstance().lookupAll()) {
			scheduler.standby();
			scheduler.clear();
		}
	}

	@Override
	public void beforeEach(ExtensionContext context) {
		if (!managesTokens(context)) {
			return;
		}
		ApiTokenRepository tokens = new ApiTokenRepository();
		TestWiring.get().core().transactions().run(dsl -> {
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
		TestWiring.get().core().transactions().run(dsl -> dsl.deleteFrom(Tables.API_TOKEN).execute());
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
		return parameterContext.getParameter().getType() == TestServer.class;
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
		return TestServer.of(extensionContext);
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
}
