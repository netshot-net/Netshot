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
package net.netshot.netshot.driver;

import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.driver.protocol.AuthScheme;
import net.netshot.netshot.driver.protocol.SshConfig;
import net.netshot.netshot.driver.protocol.SshInteractionInstruction;
import net.netshot.netshot.driver.protocol.TelnetConfig;
import net.netshot.netshot.testing.ScriptEngineTest;

/**
 * Tests the parsing of the access settings a driver declares (SSH and Telnet settings, HTTP authentication schemes),
 * from JavaScript objects.
 */
@ScriptEngineTest
@DisplayName("Parsing of the driver access settings")
class DriverConfigParserTest {

	private Context context;

	@BeforeEach
	void createContext() {
		this.context = Context.create("js");
	}

	@AfterEach
	void closeContext() {
		this.context.close();
	}

	private Value js(String expression) {
		return this.context.eval("js", "(" + expression + ")");
	}

	@Test
	@DisplayName("SSH: terminal, algorithms in the declared order, rekey limits and interactive authentication")
	void ssh() {
		SshConfig config = DriverConfigParser.parseSshConfig(this.js("""
			{
				terminal: { pty: false, type: "xterm", height: 600, width: 1200, rows: 50, cols: 132 },
				kexAlgorithms: ["diffie-hellman-group14-sha256", "ecdh-sha2-nistp256", "diffie-hellman-group14-sha256"],
				hostKeyAlgorithms: ["ssh-ed25519", "rsa-sha2-512"],
				ciphers: ["aes256-ctr", "aes128-ctr"],
				macs: ["hmac-sha2-512", "hmac-sha2-256"],
				compressionAlgorithms: ["none"],
				rekey: { timeLimit: 3600, dataLimit: 1000000, blocksLimit: 2000, packetsLimit: 3000 },
				auth: { interactive: { interactions: [
					{ prompt: /Password: ?$/i, echo: false, response: "$$NetshotPassword$$" },
					{ prompt: /Token:/ },
				] } },
			}
			"""));

		Assertions.assertFalse(config.getUsePty());
		Assertions.assertEquals("xterm", config.getTerminalType());
		Assertions.assertEquals(600, config.getTerminalHeight());
		Assertions.assertEquals(1200, config.getTerminalWidth());
		Assertions.assertEquals(50, config.getTerminalRows());
		Assertions.assertEquals(132, config.getTerminalCols());
		// The order is the preference order of the negotiation; a duplicate is removed
		Assertions.assertEquals(List.of("diffie-hellman-group14-sha256", "ecdh-sha2-nistp256"), config.getKexAlgorithms());
		Assertions.assertEquals(List.of("ssh-ed25519", "rsa-sha2-512"), config.getHostKeyAlgorithms());
		Assertions.assertEquals(List.of("aes256-ctr", "aes128-ctr"), config.getCiphers());
		Assertions.assertEquals(List.of("hmac-sha2-512", "hmac-sha2-256"), config.getMacs());
		Assertions.assertEquals(List.of("none"), config.getCompressionAlgorithms());
		Assertions.assertEquals(3600L, config.getRekeyTimeLimit());
		Assertions.assertEquals(1000000L, config.getRekeyDataLimit());
		Assertions.assertEquals(2000L, config.getRekeyBlocksLimit());
		Assertions.assertEquals(3000L, config.getRekeyPacketsLimit());
		List<SshInteractionInstruction> interactions = config.getInteractionInstructions();
		Assertions.assertEquals(2, interactions.size());
		Assertions.assertTrue(interactions.get(0).getPromptPattern().matcher("PASSWORD: ").find());
		Assertions.assertEquals(Boolean.FALSE, interactions.get(0).getEcho());
		Assertions.assertEquals("$$NetshotPassword$$", interactions.get(0).getResponse());
		Assertions.assertNull(interactions.get(1).getEcho());
		Assertions.assertNull(interactions.get(1).getResponse());
	}

	@Test
	@DisplayName("SSH: the terminal size in pixels derives from the rows and columns when not given")
	void sshTerminalSize() {
		SshConfig config = DriverConfigParser.parseSshConfig(this.js("{ terminal: { rows: 40, cols: 100 } }"));
		Assertions.assertEquals(320, config.getTerminalHeight());
		Assertions.assertEquals(2000, config.getTerminalWidth());
		Assertions.assertNull(config.getUsePty());
	}

	@Test
	@DisplayName("SSH: nothing set without settings")
	void sshEmpty() {
		for (SshConfig config : List.of(DriverConfigParser.parseSshConfig(null), DriverConfigParser.parseSshConfig(this.js("{}")))) {
			Assertions.assertNull(config.getKexAlgorithms());
			Assertions.assertNull(config.getTerminalType());
			Assertions.assertNull(config.getInteractionInstructions());
		}
	}

	@Test
	@DisplayName("SSH: an interaction prompt must be a regular expression")
	void sshInvalidPrompt() {
		IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class, () -> DriverConfigParser.parseSshConfig(
			this.js("{ auth: { interactive: { interactions: [{ prompt: /ok/ }, { prompt: 42 }] } } }")));
		Assertions.assertEquals("Invalid RegExp used as SSH interaction prompt (index 1)", e.getMessage());
	}

	@Test
	@DisplayName("Telnet: only the given terminal settings are set")
	void telnet() {
		TelnetConfig config = DriverConfigParser.parseTelnetConfig(this.js("{ terminal: { type: \"vt100\", cols: 80, rows: 24 } }"));
		Assertions.assertEquals("vt100", config.getTerminalType());
		Assertions.assertEquals(80, config.getTerminalCols());
		Assertions.assertEquals(24, config.getTerminalRows());
		TelnetConfig partial = DriverConfigParser.parseTelnetConfig(this.js("{ terminal: { cols: 200 } }"));
		Assertions.assertNull(partial.getTerminalType());
		Assertions.assertNull(partial.getTerminalRows());
		Assertions.assertNull(DriverConfigParser.parseTelnetConfig(null).getTerminalCols());
	}

	private AuthScheme auth(String expression) {
		return DriverConfigParser.parseHttpAuthScheme(this.js(expression), "https");
	}

	@Test
	@DisplayName("HTTP authentication schemes and their defaults")
	void httpAuth() {
		AuthScheme basic = this.auth("{ type: \"http\" }");
		Assertions.assertEquals("http", basic.getType());
		Assertions.assertEquals("basic", basic.getScheme());
		Assertions.assertEquals("bearer", this.auth("{ type: \"http\", scheme: \"bearer\" }").getScheme());

		AuthScheme apiKey = this.auth("{ type: \"apiKey\", name: \"X-Auth\" }");
		Assertions.assertEquals("header", apiKey.getIn());
		Assertions.assertEquals("X-Auth", apiKey.getName());
		Assertions.assertEquals("query", this.auth("{ type: \"apiKey\", in: \"query\", name: \"key\" }").getIn());
		Assertions.assertEquals("cookie", this.auth("{ type: \"apiKey\", in: \"cookie\", name: \"key\" }").getIn());

		AuthScheme cookie = this.auth("{ type: \"cookie\", path: \"/login\", data: { user: \"$$NetshotUsername$$\", nested: { a: 1 } } }");
		Assertions.assertEquals("POST", cookie.getMethod());
		Assertions.assertEquals("/login", cookie.getPath());
		Assertions.assertEquals("json", cookie.getContentType());
		Assertions.assertEquals("$$NetshotUsername$$", cookie.getData().get("user"));
		Assertions.assertInstanceOf(Map.class, cookie.getData().get("nested"));
		AuthScheme form = this.auth("{ type: \"cookie\", method: \"put\", path: \"/session\", contentType: \"form\" }");
		Assertions.assertEquals("PUT", form.getMethod());
		Assertions.assertEquals("form", form.getContentType());
		Assertions.assertNull(form.getData());

		Assertions.assertEquals("oauth2", this.auth("{ type: \"oauth2\" }").getType());
		Assertions.assertEquals("openIdConnect", this.auth("{ type: \"openIdConnect\" }").getType());
	}

	@Test
	@DisplayName("HTTP authentication: the invalid schemes are refused, with the name of the access")
	void httpAuthInvalid() {
		for (String[] invalid : new String[][] {
			{ "{}", "Missing 'type' in auth scheme of HTTP access 'https'." },
			{ "{ type: \"digest\" }", "Invalid auth 'type' 'digest' in HTTP access 'https'." },
			{ "{ type: \"http\", scheme: \"ntlm\" }", "Invalid 'scheme' 'ntlm' in auth scheme of HTTP access 'https'." },
			{ "{ type: \"apiKey\", in: \"body\", name: \"k\" }", "Invalid 'in' 'body' in auth scheme of HTTP access 'https'." },
			{ "{ type: \"apiKey\" }", "Missing 'name' in apiKey auth scheme of HTTP access 'https'." },
			{ "{ type: \"cookie\", method: \"get\", path: \"/login\" }", "Invalid 'method' 'get' in cookie auth scheme of HTTP access 'https'." },
			{ "{ type: \"cookie\" }", "Missing 'path' in cookie auth scheme of HTTP access 'https'." },
			{ "{ type: \"cookie\", path: \"/login\", contentType: \"xml\" }",
				"Invalid 'contentType' 'xml' in cookie auth scheme of HTTP access 'https'." },
		}) {
			IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class, () -> this.auth(invalid[0]), invalid[0]);
			Assertions.assertEquals(invalid[1], e.getMessage());
		}
	}
}
