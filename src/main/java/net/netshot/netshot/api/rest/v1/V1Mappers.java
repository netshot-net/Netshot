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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import jakarta.ws.rs.core.MediaType;

/**
 * The Jackson mappers of the v1 REST API, one per format (JSON, XML, YAML), configured the same way:
 * <ul>
 * <li>dates ({@link Instant}) are ISO-8601 strings in UTC with milliseconds ({@code 2026-10-30T09:12:44.000Z}),
 * and are read from ISO-8601 with any offset;</li>
 * <li>null properties are written (a representation always has the same properties);</li>
 * <li>unknown properties of a request body are refused;</li>
 * <li>XML: no wrapper element around the lists (a list is the repetition of its element, in requests and in
 * responses), nulls are written and read as {@code xsi:nil="true"}, a single element is accepted for a list.</li>
 * </ul>
 */
public final class V1Mappers {

	/** The media type of YAML. */
	public static final String APPLICATION_YAML = "application/yaml";

	/** The media type of a JSON merge patch (RFC 7396). */
	public static final String APPLICATION_MERGE_PATCH_JSON = "application/merge-patch+json";

	/** The media type of the problem details in JSON (RFC 9457). */
	public static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

	/** The media type of the problem details in XML (RFC 9457). */
	public static final String APPLICATION_PROBLEM_XML = "application/problem+xml";

	/** The format of the dates. */
	private static final DateTimeFormatter DATE_FORMAT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

	private static final JsonMapper JSON = configure(JsonMapper.builder()).build();

	private static final XmlMapper XML = configure(XmlMapper.builder())
		.defaultUseWrapper(false)
		.enable(ToXmlGenerator.Feature.WRITE_NULLS_AS_XSI_NIL)
		.enable(FromXmlParser.Feature.PROCESS_XSI_NIL)
		.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
		.build();

	private static final YAMLMapper YAML = configure(YAMLMapper.builder())
		.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
		.build();

	private V1Mappers() {
	}

	private static <M extends ObjectMapper, B extends MapperBuilder<M, B>> B configure(B builder) {
		SimpleModule dates = new SimpleModule("NetshotV1Dates");
		dates.addSerializer(Instant.class, new InstantSerializer());
		dates.addDeserializer(Instant.class, new InstantDeserializer());
		return builder
			.addModule(dates)
			.addHandler(new ReadOnlyProperties())
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
	}

	/**
	 * Gets the JSON mapper.
	 *
	 * @return the mapper
	 */
	public static JsonMapper json() {
		return JSON;
	}

	/**
	 * Gets the XML mapper.
	 *
	 * @return the mapper
	 */
	public static XmlMapper xml() {
		return XML;
	}

	/**
	 * Gets the YAML mapper.
	 *
	 * @return the mapper
	 */
	public static YAMLMapper yaml() {
		return YAML;
	}

	/**
	 * Gets the mapper of a media type.
	 *
	 * @param mediaType the media type (null for JSON)
	 * @return the mapper of the format: XML for {@code xml} and {@code +xml}, YAML for {@code yaml}, JSON otherwise
	 */
	public static ObjectMapper forMediaType(MediaType mediaType) {
		if (isXml(mediaType)) {
			return XML;
		}
		if (isYaml(mediaType)) {
			return YAML;
		}
		return JSON;
	}

	/**
	 * Tells whether a media type is a XML one.
	 *
	 * @param mediaType the media type
	 * @return true for {@code application/xml}, {@code text/xml} and {@code +xml} types
	 */
	public static boolean isXml(MediaType mediaType) {
		return mediaType != null && ("xml".equals(mediaType.getSubtype()) || mediaType.getSubtype().endsWith("+xml"));
	}

	/**
	 * Tells whether a media type is a YAML one.
	 *
	 * @param mediaType the media type
	 * @return true for {@code yaml}, {@code x-yaml} and {@code +yaml} types
	 */
	public static boolean isYaml(MediaType mediaType) {
		return mediaType != null && mediaType.getSubtype().matches("(x-)?yaml|.*\\+yaml");
	}

	/**
	 * Tells whether a media type is a JSON one.
	 *
	 * @param mediaType the media type
	 * @return true for {@code json} and {@code +json} types
	 */
	public static boolean isJson(MediaType mediaType) {
		return mediaType != null && ("json".equals(mediaType.getSubtype()) || mediaType.getSubtype().endsWith("+json"));
	}

	/**
	 * Formats a date as the API writes it.
	 *
	 * @param instant the date
	 * @return the text
	 */
	public static String formatDate(Instant instant) {
		return DATE_FORMAT.format(instant);
	}

	/** Writes a date in ISO-8601, UTC, with milliseconds. */
	private static final class InstantSerializer extends JsonSerializer<Instant> {
		@Override
		public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
			gen.writeString(formatDate(value));
		}
	}

	/** Reads a date in ISO-8601, with an offset. */
	private static final class InstantDeserializer extends JsonDeserializer<Instant> {
		@Override
		public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			if (p.currentToken() != JsonToken.VALUE_STRING) {
				return (Instant) ctxt.handleUnexpectedToken(Instant.class, p);
			}
			String text = p.getText().trim();
			if (text.isEmpty()) {
				return null;
			}
			try {
				return OffsetDateTime.parse(text).toInstant();
			}
			catch (DateTimeParseException e) {
				return (Instant) ctxt.handleWeirdStringValue(Instant.class, text,
					"an ISO-8601 date and time with an offset (such as 2026-10-30T09:12:44Z) is expected");
			}
		}
	}
}
