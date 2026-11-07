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
package net.netshot.netshot.api.rest.v1;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a v1 request record as the writable part of a representation: the properties of the representation
 * which the request doesn't have (the read-only ones: {@code id}, {@code url}, {@code version}, dates...) are
 * ignored in a request body, so that an object read with {@code GET} can be modified and sent back. Any other
 * unknown property is still refused. Applied by {@link V1Mappers} (see {@link ReadOnlyProperties}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ReadOnlyFrom {

	/**
	 * The representation the request is the writable part of.
	 *
	 * @return the class of the representation
	 */
	Class<?> value();
}
