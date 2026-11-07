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

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A write-only secret in a request body, which tells apart a property which is absent (a Java {@code null}:
 * the stored secret is unchanged) from one which is explicitly {@code null} ({@link #CLEARED}: the stored
 * secret is removed). Secrets are never written in a response.
 *
 * <p>A request record holds it in a component annotated
 * {@code @JsonInclude(JsonInclude.Include.NON_NULL)}, so that the request built from an existing object (the
 * base of a merge patch) leaves the secrets absent.</p>
 *
 * @param value the secret, or null if it is to be cleared
 */
@Schema(type = "string", format = "password", nullable = true,
	description = "Write-only secret: absent leaves the stored value unchanged, null clears it")
@JsonDeserialize(using = Secret.Deserializer.class)
public record Secret(@JsonValue String value) {

	/** The secret to remove (explicit {@code null}). */
	public static final Secret CLEARED = new Secret(null);

	/** Reads a secret. */
	static final class Deserializer extends JsonDeserializer<Secret> {
		@Override
		public Secret deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			if (p.currentToken() != JsonToken.VALUE_STRING) {
				return (Secret) ctxt.handleUnexpectedToken(Secret.class, p);
			}
			return new Secret(p.getText());
		}

		@Override
		public Object getAbsentValue(DeserializationContext ctxt) {
			return null;
		}

		@Override
		public Secret getNullValue(DeserializationContext ctxt) {
			return CLEARED;
		}
	}
}
