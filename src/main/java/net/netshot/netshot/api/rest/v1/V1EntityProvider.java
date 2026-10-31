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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

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
 * <p>A response is first turned into its JSON tree, the {@link Selection} attached to the request (if any) is
 * applied to it ({@link V1Response#select}), and the tree is written in the negotiated format: the
 * representation is the same whatever the format. In XML, the root element is {@code list} for a page
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
		JsonNode tree = entity instanceof JsonNode node ? node : V1Mappers.json().valueToTree(entity);
		Selection selection = V1Response.selectionOf(this.request, entity);
		if (selection != null) {
			if (entity instanceof ListResponse<?>) {
				selection.apply(tree.get("results"));
			}
			else {
				selection.apply(tree);
			}
		}
		ObjectMapper mapper = V1Mappers.forMediaType(mediaType);
		if (V1Mappers.isXml(mediaType)) {
			mapper.writer().withRootName(rootName(entity)).writeValue(entityStream, tree);
		}
		else {
			mapper.writeValue(entityStream, tree);
		}
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
