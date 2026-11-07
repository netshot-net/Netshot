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

import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.MediaType;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * JSON Merge Patch (RFC 7396): a {@code PATCH} body is a partial representation, whose properties replace the
 * ones of the target (recursively for the objects), {@code null} removing them (which the request record then
 * reads as {@code null}: cleared); the lists are replaced as a whole. The patch can also be given in XML
 * (an absent element is unchanged, {@code xsi:nil="true"} clears) or YAML.
 *
 * <p>A resource applies a patch to the request representation of the current object (the body a {@code PUT}
 * would take), then reads the result as that request: {@code PATCH} is {@code GET}, merge, {@code PUT}. The
 * service does it ({@link #of}) while the object is locked, so the patch applies to its latest version.</p>
 */
public final class MergePatch {

	/**
	 * A patch whose result isn't a valid request, thrown from {@link #of} (unchecked, to cross the service): the
	 * exception mapper reports its cause, as for an invalid body.
	 */
	public static final class InvalidResultException extends RuntimeException {

		private static final long serialVersionUID = 3268511801302264237L;

		InvalidResultException(JsonProcessingException cause) {
			super(cause);
		}
	}

	private MergePatch() {
	}

	/**
	 * Gives the change a patch makes to an object: the definition of the object once patched, computed from the
	 * object as it is (by the service, which locks it first).
	 *
	 * @param <T> the type of the object
	 * @param <R> the type of the request
	 * @param <D> the type of the definition the service takes
	 * @param patch the patch
	 * @param mediaType the media type of the patch (the format it was read from)
	 * @param requestType the class of the request
	 * @param toRequest gives the request representation of an object
	 * @param toDefinition gives the definition of a request
	 * @return the change, which throws a {@link NetshotException} if the patch isn't an object, or an
	 *         {@link InvalidResultException} if the patched representation isn't a valid request
	 */
	public static <T, R, D> Function<T, D> of(JsonNode patch, MediaType mediaType, Class<R> requestType,
		Function<T, R> toRequest, Function<R, D> toDefinition) {
		return current -> {
			try {
				return toDefinition.apply(apply(toRequest.apply(current), patch, mediaType, requestType));
			}
			catch (JsonProcessingException e) {
				throw new InvalidResultException(e);
			}
		};
	}

	/**
	 * Applies a patch to a request representation.
	 *
	 * @param <R> the type of the request
	 * @param current the request representation of the current object
	 * @param patch the patch
	 * @param mediaType the media type of the patch (the format it was read from)
	 * @param requestType the class of the request
	 * @return the patched request
	 * @throws NetshotException if the patch isn't an object
	 * @throws JsonProcessingException if the patched representation isn't a valid request (reported as a 400)
	 */
	public static <R> R apply(R current, JsonNode patch, MediaType mediaType, Class<R> requestType) throws JsonProcessingException {
		if (patch == null || !patch.isObject()) {
			throw NetshotException.invalidParameter(null, "A merge patch must be an object");
		}
		ObjectMapper mapper = V1Mappers.forMediaType(mediaType);
		JsonNode target = V1Mappers.json().valueToTree(current);
		JsonNode merged = merge(target, patch);
		return mapper.treeToValue(merged, requestType);
	}

	/**
	 * Merges a patch into a target (RFC 7396 algorithm).
	 *
	 * @param target the target (modified)
	 * @param patch the patch
	 * @return the result
	 */
	static JsonNode merge(JsonNode target, JsonNode patch) {
		if (!patch.isObject()) {
			return patch;
		}
		ObjectNode result = target != null && target.isObject() ? (ObjectNode) target : V1Mappers.json().createObjectNode();
		for (Map.Entry<String, JsonNode> entry : patch.properties()) {
			if (entry.getValue().isNull()) {
				result.putNull(entry.getKey());
			}
			else {
				result.set(entry.getKey(), merge(result.get(entry.getKey()), entry.getValue()));
			}
		}
		return result;
	}
}
