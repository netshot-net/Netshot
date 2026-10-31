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

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;

/**
 * Skips, in a request body, the read-only properties of the representation a request record is the writable
 * part of ({@link ReadOnlyFrom}); the other unknown properties are left to the mapper, which refuses them.
 */
final class ReadOnlyProperties extends DeserializationProblemHandler {

	@Override
	public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p, JsonDeserializer<?> deserializer,
		Object beanOrClass, String propertyName) throws IOException {
		Class<?> type = beanOrClass instanceof Class<?> c ? c : beanOrClass != null ? beanOrClass.getClass() : deserializer.handledType();
		ReadOnlyFrom readOnlyFrom = type == null ? null : type.getAnnotation(ReadOnlyFrom.class);
		if (readOnlyFrom == null || !DtoModel.of(readOnlyFrom.value()).names().contains(propertyName)) {
			return false;
		}
		p.skipChildren();
		return true;
	}
}
