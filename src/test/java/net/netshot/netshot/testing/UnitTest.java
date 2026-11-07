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

import com.tngtech.archunit.junit.ArchTag;

import org.junit.jupiter.api.Tag;

/**
 * Kind of test: a unit test, running in the JVM only, without any database, network, container or script
 * engine. The architecture and layout tests, which only read the compiled classes, are unit tests too.
 *
 * <p>Tagged {@code unit}: run by the {@code fast} Maven profile.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("unit")
// The ArchUnit test engine (ArchitectureTest) ignores the JUnit Jupiter tags
@ArchTag("unit")
public @interface UnitTest {
}
