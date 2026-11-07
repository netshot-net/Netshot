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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Kind of test: compares outputs of Netshot (REST responses, webhook payloads, exported files) to the
 * golden files stored under {@code src/test/resources}. Netshot and the REST server are started as for an
 * {@link ApiTest}, except that the tasks are stored but never run (the schedulers stay in standby), the
 * default time zone is UTC and no API token is managed (the contract seed creates its own data).
 *
 * <p>Tagged {@code contract}: needs Docker.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("contract")
@ExtendWith({ UtcTimeZoneExtension.class, DatabaseExtension.class, RestServerExtension.class })
public @interface ContractTest {
}
