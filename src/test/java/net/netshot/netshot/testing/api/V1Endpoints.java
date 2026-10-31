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
package net.netshot.netshot.testing.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Declares the v1 API endpoints a test class covers, as {@code "METHOD /path/template"} under {@code /api/v1}
 * ({@code "GET /domains/{id}"}). Every endpoint of the v1 API must be declared by a test class
 * ({@code V1EndpointCoverageTest}), and the class must really call each endpoint it declares through a
 * {@link V1Client} (checked after the class by {@link V1EndpointsExtension}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(V1EndpointsExtension.class)
public @interface V1Endpoints {

	/**
	 * Gets the covered endpoints.
	 *
	 * @return the endpoints, {@code "METHOD /path/template"}
	 */
	String[] value();
}
