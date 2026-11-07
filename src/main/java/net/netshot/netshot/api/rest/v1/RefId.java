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
package net.netshot.netshot.api.rest.v1;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A reference to another object in a request body: either its ID ({@code "mgmtDomain": 3}) or an object with its
 * ID ({@code "mgmtDomain": {"id": 3}}, which is what a read representation holds: the other properties of the
 * object are ignored). Absent or {@code null} is no reference.
 *
 * @param id the ID of the referenced object
 */
@Schema(description = "Reference to another object: its ID, or an object with its ID", oneOf = {Long.class, RefId.IdObject.class})
@JsonDeserialize(using = RefId.Deserializer.class)
public record RefId(@JsonValue long id) {

	/**
	 * The object form of a reference (the schema of the documentation).
	 *
	 * @param id the ID of the referenced object
	 */
	@Schema(name = "RefObject", description = "A reference to an object by its ID")
	public record IdObject(long id) {
	}

	/** Reads a reference. */
	static final class Deserializer extends JsonDeserializer<RefId> {
		@Override
		public RefId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			JsonNode node = p.readValueAsTree();
			JsonNode idNode = node.isObject() ? node.get("id") : node;
			if (idNode != null && (idNode.isIntegralNumber() || (idNode.isTextual() && idNode.asText().trim().matches("\\d{1,18}")))) {
				return new RefId(Long.parseLong(idNode.asText().trim()));
			}
			return ctxt.reportInputMismatch(RefId.class, "an ID, or an object with an ID, is expected");
		}
	}
}
