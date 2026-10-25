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
package net.netshot.netshot.boot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.testing.UnitTest;

/**
 * The configuration reference (docs/content/configuration-reference.md) documents exactly the declared
 * settings, with the right value in its Reload column.
 */
@UnitTest
@DisplayName("Configuration reference documentation")
public class ConfigurationReferenceTest {

	private static final Path REFERENCE = Path.of("docs", "content", "configuration-reference.md");

	/** A backticked item in the first cell: a key, or a suffix ({@code .port}) of the first key. */
	private static final Pattern CODE = Pattern.compile("`([^`]+)`");

	/**
	 * Reads the documented settings, with the value of their Reload column.
	 * @return the value of the Reload column, by key
	 * @throws Exception if the file can't be read
	 */
	private static Map<String, String> documentedSettings() throws Exception {
		Map<String, String> documented = new TreeMap<>();
		for (String line : Files.readAllLines(REFERENCE, StandardCharsets.UTF_8)) {
			if (!line.startsWith("| `netshot.")) {
				continue;
			}
			String firstCell = line.substring(2, line.indexOf(" | "));
			String trimmed = line.trim();
			String reload = trimmed.substring(trimmed.lastIndexOf('|', trimmed.length() - 2) + 1, trimmed.length() - 1).trim();
			Matcher matcher = CODE.matcher(firstCell);
			String firstKey = null;
			while (matcher.find()) {
				String item = matcher.group(1);
				if (firstKey == null) {
					firstKey = item;
					documented.put(item, reload);
				}
				else if (item.startsWith(".")) {
					documented.put(firstKey.substring(0, firstKey.lastIndexOf('.')) + item, reload);
				}
			}
		}
		return documented;
	}

	@Test
	@DisplayName("The documented parameters are the declared settings, with the right Reload value")
	void documentedSettingsMatchCatalog() throws Exception {
		Map<String, String> expected = new TreeMap<>();
		for (Setting<?> setting : NetshotSettings.catalog().settings()) {
			expected.put(setting.key(), setting.reload() == Setting.Reload.LIVE ? "Yes" : "Restart");
		}
		Assertions.assertEquals(expected, documentedSettings());
	}
}
