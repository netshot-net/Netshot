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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Value;

import net.netshot.netshot.driver.protocol.AuthScheme;
import net.netshot.netshot.driver.protocol.SshConfig;
import net.netshot.netshot.driver.protocol.SshInteractionInstruction;
import net.netshot.netshot.driver.protocol.TelnetConfig;
import net.netshot.netshot.platform.script.JsUtils;

/**
 * Parsers of the access configuration objects declared by the drivers (or overridden by scripts).
 */
public final class DriverConfigParser {

	private DriverConfigParser() {
	}

	/**
	 * Parses the "config" object of a CLI SSH access into an {@link SshConfig}.
	 * Also used to parse the {@code sshConfig} advanced option of
	 * {@code client.create(...)} (see {@code JsClientFactory}), in which case
	 * the result is a delta merged onto the driver-declared config via
	 * {@link SshConfig#apply}.
	 * @param cliSshConfig the JS "config" object (may be null)
	 * @return the populated SshConfig
	 */
	public static SshConfig parseSshConfig(Value cliSshConfig) {
		SshConfig sshConfig = new SshConfig();
		if (cliSshConfig != null && cliSshConfig.hasMembers()) {
			Value terminal = cliSshConfig.getMember("terminal");
			if (terminal != null && terminal.hasMembers()) {
				Value usePty = terminal.getMember("pty");
				if (usePty != null) {
					sshConfig.setUsePty(usePty.asBoolean());
				}
				Value vtType = terminal.getMember("type");
				if (vtType != null) {
					sshConfig.setTerminalType(vtType.asString());
				}
				Value vtHeight = terminal.getMember("height");
				if (vtHeight != null) {
					sshConfig.setTerminalHeight(vtHeight.asInt());
				}
				Value vtWidth = terminal.getMember("width");
				if (vtWidth != null) {
					sshConfig.setTerminalWidth(vtWidth.asInt());
				}
				Value vtRows = terminal.getMember("rows");
				if (vtRows != null) {
					sshConfig.setTerminalRows(vtRows.asInt());
					if (vtHeight == null) {
						sshConfig.setTerminalHeight(vtRows.asInt() * 8);
					}
				}
				Value vtCols = terminal.getMember("cols");
				if (vtCols != null) {
					sshConfig.setTerminalCols(vtCols.asInt());
					if (vtWidth == null) {
						sshConfig.setTerminalWidth(vtCols.asInt() * 20);
					}
				}
			}
			{
				Value kexAlgorithms = cliSshConfig.getMember("kexAlgorithms");
				if (kexAlgorithms != null && kexAlgorithms.hasArrayElements()) {
					Set<String> algos = new HashSet<>();
					for (long i = 0; i < kexAlgorithms.getArraySize(); i++) {
						Value algo = kexAlgorithms.getArrayElement(i);
						algos.add(algo.asString());
					}
					sshConfig.setKexAlgorithms(algos.toArray(new String[0]));
				}
			}
			{
				Value hostKeyAlgorithms = cliSshConfig.getMember("hostKeyAlgorithms");
				if (hostKeyAlgorithms != null && hostKeyAlgorithms.hasArrayElements()) {
					Set<String> algos = new HashSet<>();
					for (long i = 0; i < hostKeyAlgorithms.getArraySize(); i++) {
						Value algo = hostKeyAlgorithms.getArrayElement(i);
						algos.add(algo.asString());
					}
					sshConfig.setHostKeyAlgorithms(algos.toArray(new String[0]));
				}
			}
			{
				Value ciphers = cliSshConfig.getMember("ciphers");
				if (ciphers != null && ciphers.hasArrayElements()) {
					Set<String> algos = new HashSet<>();
					for (long i = 0; i < ciphers.getArraySize(); i++) {
						Value algo = ciphers.getArrayElement(i);
						algos.add(algo.asString());
					}
					sshConfig.setCiphers(algos.toArray(new String[0]));
				}
			}
			{
				Value macs = cliSshConfig.getMember("macs");
				if (macs != null && macs.hasArrayElements()) {
					Set<String> algos = new HashSet<>();
					for (long i = 0; i < macs.getArraySize(); i++) {
						Value algo = macs.getArrayElement(i);
						algos.add(algo.asString());
					}
					sshConfig.setMacs(algos.toArray(new String[0]));
				}
			}
			{
				Value compressionAlgorithms = cliSshConfig.getMember("compressionAlgorithms");
				if (compressionAlgorithms != null && compressionAlgorithms.hasArrayElements()) {
					Set<String> algos = new HashSet<>();
					for (long i = 0; i < compressionAlgorithms.getArraySize(); i++) {
						Value algo = compressionAlgorithms.getArrayElement(i);
						algos.add(algo.asString());
					}
					sshConfig.setCompressionAlgorithms(algos.toArray(new String[0]));
				}
			}
			{
				Value rekey = cliSshConfig.getMember("rekey");
				if (rekey != null && rekey.hasMembers()) {
					Value timeLimit = rekey.getMember("timeLimit");
					if (timeLimit != null) {
						sshConfig.setRekeyTimeLimit(timeLimit.asLong());
					}
					Value dataLimit = rekey.getMember("dataLimit");
					if (dataLimit != null) {
						sshConfig.setRekeyDataLimit(dataLimit.asLong());
					}
					Value blocksLimit = rekey.getMember("blocksLimit");
					if (blocksLimit != null) {
						sshConfig.setRekeyBlocksLimit(blocksLimit.asLong());
					}
					Value packetsLimit = rekey.getMember("packetsLimit");
					if (packetsLimit != null) {
						sshConfig.setRekeyPacketsLimit(packetsLimit.asLong());
					}
				}
			}
			Value auth = cliSshConfig.getMember("auth");
			if (auth != null && auth.hasMembers()) {
				Value interactive = auth.getMember("interactive");
				if (interactive != null && interactive.hasMembers()) {
					Value interactions = interactive.getMember("interactions");
					if (interactions != null && interactions.hasArrayElements()) {
						List<SshInteractionInstruction> instructions = new ArrayList<>();
						for (long i = 0; i < interactions.getArraySize(); i++) {
							Value interaction = interactions.getArrayElement(i);
							Value echo = interaction.getMember("echo");
							Pattern promptPattern = null;
							Value prompt = interaction.getMember("prompt");
							if (prompt != null) {
								try {
									promptPattern = JsUtils.jsRegExpToPattern(prompt);
								}
								catch (Exception e) {
									throw new IllegalArgumentException(
										"Invalid RegExp used as SSH interaction prompt (index %d)".formatted(i));
								}
							}
							Value response = interaction.getMember("response");
							instructions.add(new SshInteractionInstruction(
								promptPattern,
								(echo != null && echo.isBoolean()) ? echo.asBoolean() : null,
								(response != null && response.isString()) ? response.asString() : null
							));
						}
						sshConfig.setInteractionInstructions(instructions);
					}
				}
			}
		}
		return sshConfig;
	}

	/**
	 * Parses the "config" object of a CLI Telnet access into a {@link TelnetConfig}
	 * delta (only the fields actually present in {@code cliTelnetConfig} are set,
	 * the rest are left {@code null}). Also used to parse the {@code telnetConfig}
	 * advanced option of {@code client.create(...)} (see {@code JsClientFactory}),
	 * in which case the result is merged onto the driver-declared config via
	 * {@link TelnetConfig#apply}.
	 * @param cliTelnetConfig the JS "config" object (may be null)
	 * @return the populated TelnetConfig delta
	 */
	public static TelnetConfig parseTelnetConfig(Value cliTelnetConfig) {
		TelnetConfig telnetConfig = new TelnetConfig(false);
		if (cliTelnetConfig != null && cliTelnetConfig.hasMembers()) {
			Value terminal = cliTelnetConfig.getMember("terminal");
			if (terminal != null && terminal.hasMembers()) {
				Value vtType = terminal.getMember("type");
				if (vtType != null) {
					telnetConfig.setTerminalType(vtType.asString());
				}
				Value vtCols = terminal.getMember("cols");
				if (vtCols != null) {
					telnetConfig.setTerminalCols(vtCols.asInt());
				}
				Value vtRows = terminal.getMember("rows");
				if (vtRows != null) {
					telnetConfig.setTerminalRows(vtRows.asInt());
				}
			}
		}
		return telnetConfig;
	}

	/**
	 * Parses the "auth" object of an HTTP access into an {@link AuthScheme},
	 * OpenAPI-securitySchemes-inspired. "oauth2"/"openIdConnect" shapes are
	 * recognized but the actual IdP token fetch/cache/refresh flow is a
	 * deliberately deferred fast-follow, not implemented in this phase.
	 * <p>
	 * The "type"/"scheme"/"in" values are driver-authored constants (part of
	 * the documented schema, e.g. "apiKey"), not free-form user input, so they
	 * are matched with exact case - a typo like "apikey" is rejected rather
	 * than silently accepted.
	 * @param authValue the JS "auth" object
	 * @param accessName the name of the HTTP access (for error messages)
	 * @return the populated AuthScheme
	 */
	public static AuthScheme parseHttpAuthScheme(Value authValue, String accessName) {
		AuthScheme auth = new AuthScheme();
		Value typeValue = authValue.getMember("type");
		if (typeValue == null || !typeValue.isString()) {
			throw new IllegalArgumentException(
				String.format("Missing 'type' in auth scheme of HTTP access '%s'.", accessName));
		}
		String type = typeValue.asString();
		auth.setType(type);
		if ("http".equals(type)) {
			Value schemeValue = authValue.getMember("scheme");
			String scheme = (schemeValue != null && schemeValue.isString()) ? schemeValue.asString() : "basic";
			if (!"basic".equals(scheme) && !"bearer".equals(scheme)) {
				throw new IllegalArgumentException(
					String.format("Invalid 'scheme' '%s' in auth scheme of HTTP access '%s'.", scheme, accessName));
			}
			auth.setScheme(scheme);
		}
		else if ("apiKey".equals(type)) {
			Value inValue = authValue.getMember("in");
			String in = (inValue != null && inValue.isString()) ? inValue.asString() : "header";
			if (!"header".equals(in) && !"query".equals(in) && !"cookie".equals(in)) {
				throw new IllegalArgumentException(
					String.format("Invalid 'in' '%s' in auth scheme of HTTP access '%s'.", in, accessName));
			}
			auth.setIn(in);
			Value nameValue = authValue.getMember("name");
			if (nameValue == null || !nameValue.isString()) {
				throw new IllegalArgumentException(
					String.format("Missing 'name' in apiKey auth scheme of HTTP access '%s'.", accessName));
			}
			auth.setName(nameValue.asString());
		}
		else if ("cookie".equals(type)) {
			Value methodValue = authValue.getMember("method");
			String method = (methodValue != null && methodValue.isString()) ? methodValue.asString() : "post";
			if (!"post".equals(method) && !"put".equals(method)) {
				throw new IllegalArgumentException(
					String.format("Invalid 'method' '%s' in cookie auth scheme of HTTP access '%s'.", method, accessName));
			}
			auth.setMethod(method.toUpperCase());
			Value pathValue = authValue.getMember("path");
			if (pathValue == null || !pathValue.isString()) {
				throw new IllegalArgumentException(
					String.format("Missing 'path' in cookie auth scheme of HTTP access '%s'.", accessName));
			}
			auth.setPath(pathValue.asString());
			Value dataValue = authValue.getMember("data");
			if (dataValue != null && dataValue.hasMembers()) {
				Object data = JsUtils.valueToJavaObject(dataValue);
				if (data instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> dataMap = (Map<String, Object>) data;
					auth.setData(dataMap);
				}
			}
			Value contentTypeValue = authValue.getMember("contentType");
			String contentType = (contentTypeValue != null && contentTypeValue.isString())
				? contentTypeValue.asString() : "json";
			if (!"json".equals(contentType) && !"form".equals(contentType)) {
				throw new IllegalArgumentException(String.format(
					"Invalid 'contentType' '%s' in cookie auth scheme of HTTP access '%s'.", contentType, accessName));
			}
			auth.setContentType(contentType);
		}
		else if (!"oauth2".equals(type) && !"openIdConnect".equals(type)) {
			throw new IllegalArgumentException(
				String.format("Invalid auth 'type' '%s' in HTTP access '%s'.", type, accessName));
		}
		return auth;
	}
}
