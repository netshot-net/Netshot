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
 * Kind of test: uses a PostgreSQL database. A single PostgreSQL container is shared by the whole test run;
 * before the annotated class, {@link DatabaseExtension} creates a database named after the class (lower
 * case simple name, with the enclosing class names for a nested class) as a copy of a template database
 * migrated once per run (so the class starts with the full, empty schema), sets a Netshot configuration
 * pointing at it (plus a temporary folder for the binary snapshot files and the
 * {@link TestProperty} values), loads the module configurations and the drivers, then boots Netshot as far
 * as {@link #boot()} says.
 *
 * <p>Only the class carrying the annotation is booted: a {@code @Nested} class sharing the fixture of its
 * enclosing class needs nothing, one needing its own database carries the annotation too. The test methods
 * can receive the {@link TestDatabase} as a parameter.</p>
 *
 * <p>Tagged {@code db}: needs Docker.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("db")
@ExtendWith(DatabaseExtension.class)
public @interface DatabaseTest {

	/**
	 * How far Netshot is booted before the test class.
	 */
	enum Boot {
		/**
		 * Nothing is set up: the test gets configurations of schemas of the main database from the
		 * {@link TestDatabase} itself, and migrates them as it needs (as {@code DatabaseMigrationTest} does).
		 */
		MANUAL,
		/** The configuration points at the database of the class, already migrated (copied from the template). */
		MIGRATED,
		/** As {@link #MIGRATED}, plus the wiring ({@code Wiring.init}) with the test drivers. */
		WIRED
	}

	/**
	 * Gets how far Netshot is booted before the test class.
	 *
	 * @return the boot level, {@link Boot#WIRED} by default
	 */
	Boot boot() default Boot.WIRED;
}
