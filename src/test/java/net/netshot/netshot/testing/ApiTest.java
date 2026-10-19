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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Kind of test: calls the REST API over HTTP. Netshot is booted as for a {@link DatabaseTest} (with
 * {@link DatabaseTest.Boot#WIRED}), then {@link RestServerExtension} starts the task manager and the REST
 * server on a free local port for the test class, and stops them after it.
 *
 * <p>Before each test, the API tokens of {@link TestServer#API_TOKENS} replace the users (they are removed
 * after the test). The test methods can receive the {@link TestServer} and an admin
 * {@link net.netshot.netshot.NetshotApiClient} as parameters.</p>
 *
 * <p>Tagged {@code api}: needs Docker.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("api")
@ExtendWith({ DatabaseExtension.class, RestServerExtension.class })
public @interface ApiTest {
}
