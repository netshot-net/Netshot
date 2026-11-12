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
package net.netshot.netshot.testing.api.v0;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.netshot.netshot.testing.contract.Golden;
import net.netshot.netshot.testing.contract.Golden.Transform;

/**
 * The golden files of the v0 API contract ({@code src/test/resources/v0-contract/}): the canonical responses
 * (see {@link Golden}), the recorded statuses, and the projections of the v0 responses which depend on the
 * build.
 */
public final class V0ContractGolden {

	/** Directory holding the golden files. */
	public static final Path DIR = Path.of("src", "test", "resources", "v0-contract");

	/** File holding the HTTP status and content type of each case. */
	public static final String STATUS_FILE = "_status.properties";

	/** Drivers kept in the device type list (the others are not essential and change often). */
	private static final Set<String> KEPT_DRIVERS = Set.of("CiscoIOS12", "SkyhighSWG", "GenericSNMP");

	/**
	 * Device type (driver) list: keeps a few drivers only, and drops what depends on the build and the
	 * checkout location (driver version, source hash, file path).
	 */
	public static final Transform DEVICE_TYPES = new Transform() {
		@Override
		public JsonNode json(JsonNode node) {
			ArrayNode kept = JsonNodeFactory.instance.arrayNode();
			for (JsonNode driver : node) {
				if (KEPT_DRIVERS.contains(driver.path("name").asText())) {
					ObjectNode copy = ((ObjectNode) driver).deepCopy();
					copy.remove("version");
					JsonNode location = copy.get("location");
					if (location instanceof ObjectNode locationNode && locationNode.has("fileName")) {
						locationNode.put("fileName", "<path>/" + Path.of(locationNode.get("fileName").asText()).getFileName());
					}
					kept.add(copy);
				}
			}
			return kept;
		}

		@Override
		public void xml(Document document) {
			keepDrivers(document);
		}
	};

	/**
	 * OpenAPI document: drops the schemas (names and content depend on the Java classes which the contract
	 * must not depend on), keeps paths, operations and parameters.
	 */
	public static final Transform OPENAPI = new Transform() {
		@Override
		public JsonNode json(JsonNode node) {
			ObjectNode openApi = ((ObjectNode) node).deepCopy();
			((ObjectNode) openApi.get("components")).remove("schemas");
			dropSchemas(openApi);
			return openApi;
		}
	};

	private V0ContractGolden() {
		// Static helpers only
	}

	/**
	 * Removes the schema of every request/response body.
	 * @param node the OpenAPI node to clean up
	 */
	private static void dropSchemas(JsonNode node) {
		if (node instanceof ObjectNode object) {
			if (object.has("content") && object.get("content") instanceof ObjectNode content) {
				content.properties().forEach(entry -> {
					if (entry.getValue() instanceof ObjectNode mediaType) {
						mediaType.remove("schema");
					}
				});
			}
			object.properties().forEach(entry -> dropSchemas(entry.getValue()));
		}
		else if (node instanceof ArrayNode array) {
			array.forEach(V0ContractGolden::dropSchemas);
		}
	}

	/**
	 * Keeps the {@link #KEPT_DRIVERS} only in a XML device type list, without what depends on the build.
	 * @param document the XML document
	 */
	private static void keepDrivers(Document document) {
		Element root = document.getDocumentElement();
		for (Node child = root.getFirstChild(); child != null;) {
			Node next = child.getNextSibling();
			if (child instanceof Element item) {
				String name = "";
				for (Node field = item.getFirstChild(); field != null; field = field.getNextSibling()) {
					if (field instanceof Element fieldElement && "name".equals(fieldElement.getTagName())) {
						name = fieldElement.getTextContent();
					}
				}
				if (!KEPT_DRIVERS.contains(name)) {
					root.removeChild(item);
				}
				else {
					for (Node field = item.getFirstChild(); field != null;) {
						Node nextField = field.getNextSibling();
						if (field instanceof Element fieldElement) {
							if ("version".equals(fieldElement.getTagName())) {
								item.removeChild(fieldElement);
							}
							else if ("location".equals(fieldElement.getTagName())) {
								for (Node locationField = fieldElement.getFirstChild(); locationField != null;
									locationField = locationField.getNextSibling()) {
									if (locationField instanceof Element fileName && "fileName".equals(fileName.getTagName())) {
										fileName.setTextContent("<path>/" + Path.of(fileName.getTextContent()).getFileName());
									}
								}
							}
						}
						field = nextField;
					}
				}
			}
			child = next;
		}
	}

	/**
	 * Reads a golden file, if any.
	 * @param fileName the golden file name
	 * @return the content, or null if absent
	 */
	public static String read(String fileName) {
		return Golden.readGolden(DIR, fileName);
	}

	/**
	 * Writes a golden file.
	 * @param fileName the golden file name
	 * @param content the content
	 */
	public static void write(String fileName, String content) {
		Golden.writeGolden(DIR, fileName, content);
	}

	/**
	 * Loads the recorded statuses.
	 * @return the properties (key: golden file name, value: status;content-type)
	 */
	public static Properties readStatuses() {
		Properties properties = new Properties();
		Path path = DIR.resolve(STATUS_FILE);
		if (Files.exists(path)) {
			try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				properties.load(reader);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return properties;
	}

	/**
	 * Writes the recorded statuses, sorted and without any timestamp comment.
	 * @param statuses the statuses to write
	 */
	public static void writeStatuses(Map<String, String> statuses) {
		StringBuilder out = new StringBuilder();
		new TreeMap<>(statuses).forEach((name, value) -> out.append(name).append('=').append(value).append('\n'));
		write(STATUS_FILE, out.toString());
	}
}
