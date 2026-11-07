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

import java.util.Properties;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import net.netshot.netshot.TestConfig;

/**
 * Extension of {@link ScriptEngineTest} and {@link SimulatorTest}: before the annotated class, sets an
 * empty Netshot configuration (plus the {@link TestProperty} values), loads the module configurations and
 * reloads the drivers. Does nothing for a class which also boots a database (its extension does the same
 * on the database configuration).
 */
public final class ScriptEngineExtension implements BeforeAllCallback {

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		Class<?> testClass = context.getRequiredTestClass();
		boolean annotated = AnnotationSupport.isAnnotated(testClass, ScriptEngineTest.class)
			|| AnnotationSupport.isAnnotated(testClass, SimulatorTest.class);
		if (!annotated || Kinds.usesDatabase(testClass)) {
			return;
		}
		TestConfig.init(Kinds.config(testClass, new Properties()));
		TestDrivers.reload();
	}
}
