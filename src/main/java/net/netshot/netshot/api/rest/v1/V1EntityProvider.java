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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Locale;

import javax.xml.namespace.QName;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;

/**
 * Reads and writes the bodies of the v1 API, in JSON, XML and YAML, with the {@link V1Mappers}.
 *
 * <p>A response is written in the negotiated format with the {@link Selection} attached to the request (if any,
 * {@link V1Response#select}): only the selected properties are written, without building the whole
 * representation first; the representation is the same whatever the format. In XML, the root element is {@code list} for a page
 * ({@link ListResponse}), otherwise the name given by {@link JacksonXmlRootElement} or the class name without
 * its {@code Dto} suffix ({@code domain}).</p>
 */
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML,
	V1Mappers.APPLICATION_PROBLEM_JSON, V1Mappers.APPLICATION_PROBLEM_XML})
@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, V1Mappers.APPLICATION_YAML,
	V1Mappers.APPLICATION_MERGE_PATCH_JSON})
public class V1EntityProvider implements MessageBodyReader<Object>, MessageBodyWriter<Object> {

	@Context
	private HttpServletRequest request;

	private static boolean isSupported(MediaType mediaType) {
		return V1Mappers.isJson(mediaType) || V1Mappers.isXml(mediaType) || V1Mappers.isYaml(mediaType);
	}

	private static boolean isBody(Class<?> type) {
		return type.isRecord() || JsonNode.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)
			|| DtoModel.isModel(type);
	}

	@Override
	public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return isSupported(mediaType) && isBody(type);
	}

	@Override
	public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType,
		MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException {
		ObjectMapper mapper = V1Mappers.forMediaType(mediaType);
		if (JsonNode.class.isAssignableFrom(type)) {
			return mapper.readTree(entityStream);
		}
		return mapper.readerFor(mapper.constructType(genericType)).readValue(entityStream);
	}

	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return isSupported(mediaType) && isBody(type);
	}

	@Override
	public void writeTo(Object entity, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
		MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException {
		ObjectMapper mapper = V1Mappers.forMediaType(mediaType);
		Selection selection = entity instanceof JsonNode ? null : V1Response.selectionOf(this.request, entity);
		if (selection == null) {
			ObjectWriter writer = V1Mappers.isXml(mediaType) ? mapper.writer().withRootName(rootName(entity)) : mapper.writer();
			writer.writeValue(entityStream, entity);
			return;
		}
		// The selection is applied while writing (no intermediate tree): the selected properties only
		try (JsonGenerator generator = mapper.createGenerator(entityStream)) {
			if (generator instanceof ToXmlGenerator xml) {
				xml.setNextName(new QName(rootName(entity)));
				xml.initGenerator();
			}
			SerializerProvider provider = mapper.getSerializerProviderInstance();
			if (entity instanceof ListResponse<?> list) {
				writeList(generator, provider, list, selection);
			}
			else {
				selection.write(generator, provider, entity);
			}
		}
	}

	/**
	 * Writes a list: the envelope as it is, the items with the selection.
	 *
	 * @param generator the generator
	 * @param provider the serializer provider, for the values
	 * @param list the list
	 * @param selection the selection of the items
	 * @throws IOException if the list can't be written
	 */
	private static void writeList(JsonGenerator generator, SerializerProvider provider, ListResponse<?> list, Selection selection)
		throws IOException {
		generator.writeStartObject(list);
		for (DtoModel.Prop prop : DtoModel.of(ListResponse.class).props()) {
			generator.writeFieldName(prop.name());
			if ("results".equals(prop.name())) {
				selection.write(generator, provider, list.results());
			}
			else {
				provider.defaultSerializeValue(prop.get(list), generator);
			}
		}
		generator.writeEndObject();
	}

	/**
	 * Gets the name of the XML root element of a response.
	 *
	 * @param entity the response
	 * @return the name
	 */
	static String rootName(Object entity) {
		if (entity instanceof ListResponse<?> || entity instanceof Collection<?>) {
			return "list";
		}
		Class<?> type = entity.getClass();
		JacksonXmlRootElement root = type.getAnnotation(JacksonXmlRootElement.class);
		if (root == null) {
			for (Class<?> parent : type.getInterfaces()) {
				root = parent.getAnnotation(JacksonXmlRootElement.class);
				if (root != null) {
					break;
				}
			}
		}
		if (root != null && !root.localName().isEmpty()) {
			return root.localName();
		}
		String name = type.getSimpleName().replaceFirst("Dto$", "");
		return name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
	}
}
