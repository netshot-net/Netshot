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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A Netshot configuration property set by the extensions of {@link ScriptEngineTest}, {@link DatabaseTest},
 * {@link ApiTest} and {@link ContractTest} before the test class, on top of their defaults (which it
 * overrides). It can also be set on an annotation, which then sets the property on the classes it annotates.
 */
@Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(TestProperties.class)
public @interface TestProperty {

	/**
	 * Gets the name of the property.
	 *
	 * @return the name, e.g. {@code netshot.aaa.maxidletime}
	 */
	String name();

	/**
	 * Gets the value of the property.
	 *
	 * @return the value
	 */
	String value();
}
