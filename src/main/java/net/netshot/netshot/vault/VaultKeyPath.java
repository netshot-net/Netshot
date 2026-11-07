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
package net.netshot.netshot.vault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A {@link net.netshot.netshot.platform.secret.VaultSecret#vaultPath()} value,
 * split into the KV v2 secret path (everything up to the last {@code /}) and
 * the key within that secret's JSON data (everything after it).
 * <p>
 * The key part may itself use {@code .} to address a value nested inside a
 * JSON object, e.g. {@code secret/app/creds/parent.child}. A literal
 * {@code .} or {@code \} in a key segment is written as {@code \.} / {@code \\}.
 */
public final class VaultKeyPath {

	private final String kvPath;
	private final List<String> keySegments;

	private VaultKeyPath(String kvPath, List<String> keySegments) {
		this.kvPath = kvPath;
		this.keySegments = keySegments;
	}

	/**
	 * Parses a merged path+key value.
	 * @param value the value to parse, e.g. {@code "secret/app/creds/username"}
	 * @return the parsed path and key
	 * @throws VaultException if the value doesn't hold both a path and a key
	 */
	public static VaultKeyPath parse(String value) throws VaultException {
		int lastSlash = value.lastIndexOf('/');
		if (lastSlash < 0) {
			throw new VaultException(
				"Vault path '%s' must be of the form <secret path>/<key>".formatted(value));
		}
		String kvPath = value.substring(0, lastSlash);
		String rawKey = value.substring(lastSlash + 1);
		if (kvPath.isEmpty() || rawKey.isEmpty()) {
			throw new VaultException(
				"Vault path '%s' must be of the form <secret path>/<key>".formatted(value));
		}
		return new VaultKeyPath(kvPath, splitKey(rawKey));
	}

	/**
	 * @return the KV v2 secret path, relative to the mount
	 */
	public String getKvPath() {
		return this.kvPath;
	}

	/**
	 * Navigates the given secret data down the (possibly nested) key.
	 * @param data the secret's JSON data, as returned by Vault
	 * @return the resolved node, or a missing node if any segment of the key doesn't exist
	 */
	public JsonNode resolve(JsonNode data) {
		JsonNode node = data;
		for (String segment : this.keySegments) {
			node = node.path(segment);
		}
		return node;
	}

	@Override
	public String toString() {
		return String.join(".", this.keySegments);
	}

	private static List<String> splitKey(String rawKey) {
		List<String> segments = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < rawKey.length(); i++) {
			char c = rawKey.charAt(i);
			if (c == '\\' && i + 1 < rawKey.length() && (rawKey.charAt(i + 1) == '.' || rawKey.charAt(i + 1) == '\\')) {
				current.append(rawKey.charAt(i + 1));
				i++;
			}
			else if (c == '.') {
				segments.add(current.toString());
				current.setLength(0);
			}
			else {
				current.append(c);
			}
		}
		segments.add(current.toString());
		return Collections.unmodifiableList(segments);
	}
}
