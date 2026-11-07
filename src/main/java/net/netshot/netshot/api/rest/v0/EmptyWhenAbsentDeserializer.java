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
package net.netshot.netshot.api.rest.v0;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;

/**
 * Reads a text component of a request record which is the empty string when the property is
 * absent from the request, but {@code null} when it is explicitly null: the two cases that a
 * bean field initialized to {@code ""} told apart, and a compact constructor cannot.
 */
public final class EmptyWhenAbsentDeserializer extends StdDeserializer<String> {

	private static final long serialVersionUID = 1L;

	/**
	 * Instantiates the deserializer.
	 */
	public EmptyWhenAbsentDeserializer() {
		super(String.class);
	}

	@Override
	public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
		return StringDeserializer.instance.deserialize(parser, context);
	}

	@Override
	public String getNullValue(DeserializationContext context) {
		return null;
	}

	@Override
	public Object getAbsentValue(DeserializationContext context) {
		return "";
	}
}
