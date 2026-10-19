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

import java.util.Properties;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * What the kind annotations of a test class ask the extensions to do, and the configuration they share.
 */
final class Kinds {

	/** The namespace of the objects which the extensions keep in the stores of the test classes. */
	static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(Kinds.class);

	private Kinds() {
		// Static helpers only
	}

	/**
	 * Tells whether a test class boots Netshot on a database ({@link DatabaseTest}, {@link ApiTest},
	 * {@link ContractTest}).
	 *
	 * @param testClass the test class
	 * @return true if the class itself carries one of these annotations
	 */
	static boolean usesDatabase(Class<?> testClass) {
		return AnnotationSupport.isAnnotated(testClass, DatabaseTest.class) || usesRestServer(testClass);
	}

	/**
	 * Tells whether a test class starts the REST server ({@link ApiTest}, {@link ContractTest}).
	 *
	 * @param testClass the test class
	 * @return true if the class itself carries one of these annotations
	 */
	static boolean usesRestServer(Class<?> testClass) {
		return AnnotationSupport.isAnnotated(testClass, ApiTest.class) || isContract(testClass);
	}

	/**
	 * Tells whether a test class is a {@link ContractTest}.
	 *
	 * @param testClass the test class
	 * @return true if the class itself carries the annotation
	 */
	static boolean isContract(Class<?> testClass) {
		return AnnotationSupport.isAnnotated(testClass, ContractTest.class);
	}

	/**
	 * Builds the Netshot configuration of a test class: the given properties, the logging to the console
	 * and, over them, the {@link TestProperty} values of the class.
	 *
	 * @param testClass the test class
	 * @param base the properties set by the extension (database, HTTP...)
	 * @return the configuration
	 */
	static Properties config(Class<?> testClass, Properties base) {
		Properties config = new Properties();
		config.setProperty("netshot.log.file", "CONSOLE");
		config.setProperty("netshot.log.level", "WARN");
		config.putAll(base);
		for (TestProperty property : AnnotationSupport.findRepeatableAnnotations(testClass, TestProperty.class)) {
			config.setProperty(property.name(), property.value());
		}
		return config;
	}
}
