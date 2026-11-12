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
package net.netshot.netshot.vault;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Vault key paths")
class VaultKeyPathTest {

	private final ObjectMapper json = new ObjectMapper();

	@Test
	@DisplayName("A path is split into the KV path and the key")
	void parsesSimplePathAndKey() throws VaultException {
		VaultKeyPath keyPath = VaultKeyPath.parse("secret/app/creds/username");
		Assertions.assertEquals("secret/app/creds", keyPath.getKvPath());
		Assertions.assertEquals("username", keyPath.toString());
	}

	@Test
	@DisplayName("Unescaped dots go down into nested keys")
	void resolvesNestedKeyViaUnescapedDots() throws VaultException, JsonProcessingException {
		VaultKeyPath keyPath = VaultKeyPath.parse("secret/app/creds/parent.child");
		JsonNode data = this.json.readTree("{\"parent\": {\"child\": \"value\"}}");
		Assertions.assertEquals("value", keyPath.resolve(data).asText());
	}

	@Test
	@DisplayName("An escaped dot is part of the key")
	void treatsEscapedDotAsLiteralKeyCharacter() throws VaultException, JsonProcessingException {
		VaultKeyPath keyPath = VaultKeyPath.parse("secret/app/creds/a\\.b");
		JsonNode data = this.json.readTree("{\"a.b\": \"value\"}");
		Assertions.assertEquals("value", keyPath.resolve(data).asText());
	}

	@Test
	@DisplayName("An escaped backslash is part of the key")
	void treatsEscapedBackslashAsLiteralCharacter() throws VaultException, JsonProcessingException {
		VaultKeyPath keyPath = VaultKeyPath.parse("secret/app/creds/a\\\\b");
		JsonNode data = this.json.readTree("{\"a\\\\b\": \"value\"}");
		Assertions.assertEquals("value", keyPath.resolve(data).asText());
	}

	@Test
	@DisplayName("A missing nested key resolves to a missing node")
	void missingNestedKeyResolvesToMissingNode() throws VaultException, JsonProcessingException {
		VaultKeyPath keyPath = VaultKeyPath.parse("secret/app/creds/parent.missing");
		JsonNode data = this.json.readTree("{\"parent\": {\"child\": \"value\"}}");
		Assertions.assertTrue(keyPath.resolve(data).isMissingNode());
	}

	@Test
	@DisplayName("A value without a slash is refused")
	void rejectsValueWithoutSlash() {
		Assertions.assertThrows(VaultException.class, () -> VaultKeyPath.parse("username"));
	}

	@Test
	@DisplayName("A value ending with a slash is refused")
	void rejectsValueEndingWithSlash() {
		Assertions.assertThrows(VaultException.class, () -> VaultKeyPath.parse("secret/app/creds/"));
	}
}
