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
package net.netshot.netshot.api.rest.v1.export;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * A list as a table: a header and rows of scalar cells, made from the JSON representation of the items
 * (after the selection, so the columns are what the client selected).
 *
 * <p>The columns are the properties of the items, in the order of their first appearance. A nested object is
 * written as dotted columns ({@code mgmtDomain.name}), except a reference to another object (which
 * has an {@code id}, a {@code url} and a {@code display}, and perhaps some brief properties): it is written as
 * one column holding its {@code display}. To get the properties of a reference, select them with {@code fields}. A list
 * of values is written as one cell, its elements joined with {@value #SEPARATOR} (a reference by its
 * {@code display}, any other object or list as compact JSON). A missing or null value is an empty cell.</p>
 *
 * @param columns the names of the columns
 * @param rows the rows, each with a cell (null if empty) per column
 */
public record ExportTable(List<String> columns, List<List<JsonNode>> rows) {

	/** The separator of the elements of a list in a cell. */
	public static final String SEPARATOR = ";";

	/** The maximum length of a text in a cell (the limit of Excel). */
	public static final int MAX_TEXT_LENGTH = 32767;

	private static final Pattern INSTANT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");

	/**
	 * Builds the table of the items of a list.
	 *
	 * @param items the JSON representation of the items (an array)
	 * @return the table
	 */
	public static ExportTable of(JsonNode items) {
		Set<String> columns = new LinkedHashSet<>();
		List<Map<String, JsonNode>> flat = new ArrayList<>();
		if (items != null) {
			for (JsonNode item : items) {
				Map<String, JsonNode> row = new LinkedHashMap<>();
				if (item.isObject()) {
					flatten("", item, row);
				}
				else {
					row.put("value", item);
				}
				columns.addAll(row.keySet());
				flat.add(row);
			}
		}
		List<String> header = List.copyOf(columns);
		List<List<JsonNode>> rows = new ArrayList<>(flat.size());
		for (Map<String, JsonNode> row : flat) {
			List<JsonNode> cells = new ArrayList<>(header.size());
			for (String column : header) {
				cells.add(row.get(column));
			}
			rows.add(cells);
		}
		return new ExportTable(header, rows);
	}

	private static void flatten(String prefix, JsonNode node, Map<String, JsonNode> row) {
		if (node.isObject()) {
			if (!prefix.isEmpty() && isReference(node)) {
				row.put(prefix, node.get("display"));
				return;
			}
			if (node.isEmpty() && !prefix.isEmpty()) {
				row.put(prefix, null);
				return;
			}
			node.properties().forEach(entry -> flatten(prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey(), entry.getValue(), row));
		}
		else if (node.isArray()) {
			List<String> texts = new ArrayList<>();
			node.forEach(element -> texts.add(elementText(element)));
			row.put(prefix, texts.isEmpty() ? null : JsonNodeFactory.instance.textNode(String.join(SEPARATOR, texts)));
		}
		else {
			row.put(prefix, node);
		}
	}

	/**
	 * Tells whether an object is a reference to another object (or an item of the API in general): it has the
	 * {@code id}, {@code url} and {@code display} every object of the API has. A nested object selected with
	 * {@code fields} (such as {@code mgmtDomain.name}) has neither {@code url} nor {@code display}, so its
	 * properties are written as columns.
	 *
	 * @param node the object
	 * @return true for a reference
	 */
	static boolean isReference(JsonNode node) {
		return node.path("display").isTextual() && node.path("url").isTextual() && node.has("id");
	}

	private static String elementText(JsonNode element) {
		if (element.isObject() && isReference(element)) {
			return element.get("display").asText();
		}
		if (element.isContainerNode()) {
			return element.toString();
		}
		return element.isNull() ? "" : element.asText();
	}

	/**
	 * Tells whether a text is an ISO-8601 instant (the way the API writes the dates).
	 *
	 * @param text the text
	 * @return true if it is an instant
	 */
	public static boolean isInstant(String text) {
		if (!INSTANT.matcher(text).matches()) {
			return false;
		}
		try {
			Instant.parse(text);
			return true;
		}
		catch (java.time.DateTimeException e) {
			return false;
		}
	}

	/**
	 * Writes a cell as a field of a CSV record (RFC 4180): the field is quoted, with the quotes doubled, if it
	 * contains a comma, a quote or a line break. A text starting with {@code =}, {@code +}, {@code -}, {@code @},
	 * a tab or a carriage return gets a leading quote, so that a spreadsheet application doesn't run it as a
	 * formula (CSV injection).
	 *
	 * @param cell the cell (null for an empty one)
	 * @return the field
	 */
	public static String csvField(JsonNode cell) {
		if (cell == null || cell.isNull() || cell.isMissingNode()) {
			return "";
		}
		String text = cell.asText();
		if (cell.isTextual() && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
			text = "'" + text;
		}
		if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
			return '"' + text.replace("\"", "\"\"") + '"';
		}
		return text;
	}
}
