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
package net.netshot.netshot.inventory.search;

import static net.netshot.netshot.platform.database.schema.Tables.CHECK_RESULT;
import static net.netshot.netshot.platform.database.schema.Tables.CONFIG;
import static net.netshot.netshot.platform.database.schema.Tables.CONFIG_ATTRIBUTE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_ATTRIBUTE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_TAGS;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_VIRTUAL_DEVICES;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_VRF_INSTANCES;
import static net.netshot.netshot.platform.database.schema.Tables.DIAGNOSTIC_RESULT;
import static net.netshot.netshot.platform.database.schema.Tables.LONG_TEXT_CONFIGURATION;
import static net.netshot.netshot.platform.database.schema.Tables.MODULE;
import static net.netshot.netshot.platform.database.schema.Tables.NETWORK_INTERFACE;
import static net.netshot.netshot.platform.database.schema.Tables.NETWORK_INTERFACE_IP4ADDRESSES;
import static net.netshot.netshot.platform.database.schema.Tables.NETWORK_INTERFACE_IP6ADDRESSES;

import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.jooq.CaseValueStep;
import org.jooq.CaseWhenStep;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.driver.AttributeDefinition.EnumAttribute;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.Network6Address;
import net.netshot.netshot.inventory.device.PhysicalAddress;
import net.netshot.netshot.platform.database.Database;

/**
 * A Finder finds devices based on a text expression.
 */
@Slf4j
public class Finder {

	/**
	 * The loaded device drivers, which the expressions on the drivers and their attributes are parsed against.
	 * Transitional static state: the expressions are parsed by static methods, and finders are also created by entities.
	 */
	private static volatile DriverRegistry driverRegistry;

	/**
	 * Sets the loaded device drivers to parse the queries against.
	 *
	 * @param driverRegistry the registry
	 */
	public static void setDriverRegistry(DriverRegistry driverRegistry) {
		Finder.driverRegistry = driverRegistry;
	}

	/**
	 * The Enum TokenType.
	 */
	public enum TokenType {

		/** The and. */
		AND("(?i)^\\s*(and)\\b", "and"),

		/** The or. */
		OR("(?i)^\\s*(or)\\b", "or"),

		/** The not. */
		NOT("(?i)^\\s*(not)\\b", "not"),

		/** The is. */
		IS("(?i)^\\s*(is)\\b", "is"),

		/** The in. */
		IN("(?i)^\\s*(in)\\b", "in"),

		/** The bracketin. */
		BRACKETIN("^\\s*(\\()", "("),

		/** The bracketout. */
		BRACKETOUT("^\\s*(\\))", ")"),

		/** Contains text, case sensitive. */
		CONTAINS("(?i)^\\s*(contains)\\b", "contains"),

		/** Contains text, case insensitive. */
		CONTAINSNOCASE("(?i)^\\s*(containsnocase)\\b", "containsnocase"),

		/** REGEXP matches. */
		MATCHES("(?i)^\\s*(matches)\\b", "matches"),

		/** The startswith. */
		STARTSWITH("(?i)^\\s*(startswith)\\b", "startswith"),

		/** The endswith. */
		ENDSWITH("(?i)^\\s*(endswith)\\b", "endswith"),

		/** The before. */
		BEFORE("(?i)^\\s*(before)\\b", "before"),

		/** The after. */
		AFTER("(?i)^\\s*(after)\\b", "after"),

		/** The lessthan. */
		LESSTHAN("(?i)^\\s*(lessthan)\\b", "lessthan"),

		/** The greaterthan. */
		GREATERTHAN("(?i)^\\s*(greaterthan)\\b", "greaterthan"),

		/** True. */
		TRUE("(?i)^\\s*(true)\\b", "true"),

		/** False. */
		FALSE("(?i)^\\s*(false)\\b", "false"),

		/** The ip. */
		IP("(?i)^\\s*(\\[ip\\])", "IP"),

		/** The mac. */
		MAC("(?i)^\\s*(\\[mac\\])", "MAC"),

		/** The module. */
		MODULE("(?i)^\\s*(\\[module\\])", "Module"),

		/** Interface. */
		INTERFACE("(?i)^\\s*(\\[interface\\])", "Interface"),

		/** The VRF. */
		VRF("(?i)^\\s*(\\[vrf\\])", "VRF"),

		/** The virtual name. */
		VIRTUALNAME("(?i)^\\s*(\\[virtual name\\])", "Virtual Name"),

		/** A device tag. */
		TAG("(?i)^\\s*(\\[tag\\])", "Tag"),

		/** The device ID (old syntax is Device, new one is ID). */
		ID("(?i)^\\s*(\\[(id|device)\\])", "ID"),

		/** The domain token. */
		DOMAIN("(?i)^\\s*(\\[domain\\])", "Domain"),

		/** A type of device (= driver, by description i.e. nice name). */
		TYPE("(?i)^\\s*(\\[type\\])", "Type"),

		/** Legacy alias for type. */
		DRIVER("(?i)^\\s*(\\[driver\\])", "Driver"),

		/** A diagnostic result token, e.g. [DIAG > My diag] (with escaping) */
		DIAGNOSTIC("(?i)^\\s*\\[diagnostic\\s*>\\s*(?<k2>[^\\]\\\\]*(?:\\\\.[^\\]\\\\]*)*)\\]", "Diagnostic"),

		/** A compliance (policy rule) token, e.g. [RULE > My Policy > My rule] (with escaping) */
		RULE("(?i)^\\s*\\[rule\\s*>\\s*(?<k1>[^\\]\\\\]*?(?:\\\\.[^\\]\\\\]*)*)\\s*>\\s*(?<k2>[^\\]\\\\]*?(?:\\\\.[^\\]\\\\]*)*)\\]", "Rule"),

		/** A device/config item with driver name, e.g. [Cisco IOS and IOS-XE > IOS image file] */
		DRIVER_ATTR("(?i)^\\s*\\[(?<k1>[^\\]\\\\]*?(?:\\\\.[^\\]\\\\]*)*?)\\s*>\\s*(?<k2>[^\\]\\\\]*(?:\\\\.[^\\]\\\\]*)*?)\\]", ""),

		/** A simple device/config item, e.g. [Software version] */
		GENERIC_ATTR("(?i)^\\s*\\[(?<k2>[^\\]\\\\]*(?:\\\\.[^\\]\\\\]*)*?)\\]", ""),

		/** The SUBNET v4. */
		SUBNETV4("^\\s*(?<val>((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/(1[0-9]|2[0-9]|3[0-2]|[0-9]))", ""),

		/** The IP v4. */
		IPV4("^\\s*(?<val>((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?))", ""),

		/** The SUBNET v6. */
		SUBNETV6(
			"^\\s*(?<val>((([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4})|(([0-9A-Fa-f]{1,4}:){6}:[0-9A-Fa-f]{1,4})|"
			+ "(([0-9A-Fa-f]{1,4}:){5}:([0-9A-Fa-f]{1,4}:)?[0-9A-Fa-f]{1,4})|"
			+ "(([0-9A-Fa-f]{1,4}:){4}:([0-9A-Fa-f]{1,4}:){0,2}[0-9A-Fa-f]{1,4})|"
			+ "(([0-9A-Fa-f]{1,4}:){3}:([0-9A-Fa-f]{1,4}:){0,3}[0-9A-Fa-f]{1,4})|"
			+ "(([0-9A-Fa-f]{1,4}:){2}:([0-9A-Fa-f]{1,4}:){0,4}[0-9A-Fa-f]{1,4})|"
			+ "(([0-9A-Fa-f]{1,4}:){6}((b((25[0-5])|(1d{2})|(2[0-4]d)|(d{1,2}))b).){3}(b((25[0-5])|"
			+ "(1d{2})|(2[0-4]d)|(d{1,2}))b))|(([0-9A-Fa-f]{1,4}:){0,5}:((b((25[0-5])|(1d{2})|(2[0-4]d)|"
			+ "(d{1,2}))b).){3}(b((25[0-5])|(1d{2})|(2[0-4]d)|(d{1,2}))b))|(::([0-9A-Fa-f]{1,4}:){0,5}((b((25[0-5])|"
			+ "(1d{2})|(2[0-4]d)|(d{1,2}))b).){3}(b((25[0-5])|(1d{2})|(2[0-4]d)|(d{1,2}))b))|"
			+ "([0-9A-Fa-f]{1,4}::([0-9A-Fa-f]{1,4}:){0,5}[0-9A-Fa-f]{1,4})|"
			+ "(::([0-9A-Fa-f]{1,4}:){0,6}[0-9A-Fa-f]{1,4})|(([0-9A-Fa-f]{1,4}:){1,7}:))"
			+ "/(1[01][0-9]|12[0-8]|[0-9][0-9]|[0-9]))",
			""),

		/** The IP v6. */
		IPV6(
			"^\\s*(?<val>(([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4})|(([0-9A-Fa-f]{1,4}:){6}:[0-9A-Fa-f]{1,4})|"
			+ "(([0-9A-Fa-f]{1,4}:){5}:([0-9A-Fa-f]{1,4}:)?[0-9A-Fa-f]{1,4})|"
			+ "(([0-9A-Fa-f]{1,4}:){4}:([0-9A-Fa-f]{1,4}:){0,2}[0-9A-Fa-f]{1,4})|"
			+ "(([0-9A-Fa-f]{1,4}:){3}:([0-9A-Fa-f]{1,4}:){0,3}[0-9A-Fa-f]{1,4})|"
			+ "(([0-9A-Fa-f]{1,4}:){2}:([0-9A-Fa-f]{1,4}:){0,4}[0-9A-Fa-f]{1,4})|"
			+ "(([0-9A-Fa-f]{1,4}:){6}((b((25[0-5])|(1d{2})|(2[0-4]d)|(d{1,2}))b).){3}(b((25[0-5])|"
			+ "(1d{2})|(2[0-4]d)|(d{1,2}))b))|(([0-9A-Fa-f]{1,4}:){0,5}:((b((25[0-5])|(1d{2})|(2[0-4]d)|"
			+ "(d{1,2}))b).){3}(b((25[0-5])|(1d{2})|(2[0-4]d)|(d{1,2}))b))|(::([0-9A-Fa-f]{1,4}:){0,5}((b((25[0-5])|"
			+ "(1d{2})|(2[0-4]d)|(d{1,2}))b).){3}(b((25[0-5])|(1d{2})|(2[0-4]d)|(d{1,2}))b))|"
			+ "([0-9A-Fa-f]{1,4}::([0-9A-Fa-f]{1,4}:){0,5}[0-9A-Fa-f]{1,4})|"
			+ "(::([0-9A-Fa-f]{1,4}:){0,6}[0-9A-Fa-f]{1,4})|(([0-9A-Fa-f]{1,4}:){1,7}:))",
			""),

		/** The macsubnet. */
		MACSUBNET("^\\s*(?<val>[0-9a-fA-F]{4}\\.[0-9a-fA-F]{4}\\.[0-9a-fA-F]{4}/(4[0-8]|[1-3][0-9]|[0-9]))", ""),

		/** The macaddress. */
		MACADDRESS("^\\s*(?<val>[0-9a-fA-F]{4}\\.[0-9a-fA-F]{4}\\.[0-9a-fA-F]{4})", ""),

		/** The quoted text. */
		QUOTEDTEXT("^\\s*\"(?<val>.*?)(?<!\\\\)\"", ""),

		/** The numeric. */
		NUMERICVALUE("^\\s*(?<val>[0-9\\.,]+)\\b", ""),

		/** Enum keyword. */
		ENUMWORD("^\\s*(?<val>[A-Z0-9_]+)", "");

		/** The pattern. */
		private Pattern pattern;

		/** The command. */
		private String command;

		/**
		 * Instantiates a new token type.
		 *
		 * @param pattern the pattern
		 * @param command the command
		 */
		TokenType(String pattern, String command) {
			this.pattern = Pattern.compile(pattern);
			this.command = command;
		}

		/*(non-Javadoc)
		 * @see java.lang.Enum#toString()
		 */
		public String toString() {
			return command;
		}

		/**
		 * Escape a key.
		 *
		 * @param key the key to escape
		 * @return the result
		 */
		public static String escapeKey(String key) {
			// Escape special characters in reverse order to avoid double-escaping
			return key.replace("\\", "\\\\")
					.replace(">", "\\>")
					.replace("]", "\\]");
		}

		/**
		 * Unescape a key.
		 *
		 * @param key the key to unescape
		 * @return the result
		 */
		public static String unescapeKey(String key) {
			return key.replace("\\>", ">")
								.replace("\\]", "]")
								.replace("\\\\", "\\");
		}

		/**
		 * Escape a value.
		 *
		 * @param value the value to escape
		 * @return the result
		 */
		public static String escapeValue(String value) {
			return value.replace("\\", "\\\\")
						.replace("\"", "\\\"");
		}

		/**
		 * Unescape a value.
		 *
		 * @param value the value to unescape
		 * @return the result
		 */
		public static String unescapeValue(String value) {
			return value.replace("\\\"", "\"")
						.replace("\\\\", "\\");
		}
	}

	/**
	 * The Class Token.
	 */
	public static class Token {

		/** The position. */
		public int position;

		/** Primary key. */
		public String key1;

		/** Secondary key. */
		public String key2;

		/** The value. */
		public String value;

		/** The type. */
		public TokenType type;

		/** The expression. */
		public Expression expression;

		/**
		 * Instantiates a new token.
		 *
		 * @param key1 the first key
		 * @param key2 the second key
		 * @param value the value
		 * @param position the position
		 * @param type the type
		 */
		public Token(String key1, String key2, String value, int position, TokenType type) {
			this.position = position;
			this.key1 = key1;
			this.key2 = key2;
			this.value = value;
			this.type = type;
		}
	}

	/**
	 * Parsing Exception.
	 */
	public static class FinderParseException extends Exception {

		/** The Constant serialVersionUID. */
		private static final long serialVersionUID = -4102690686882816860L;

		/**
		 * Instantiates a new finder parse exception.
		 *
		 * @param message the message
		 */
		FinderParseException(String message) {
			super(message);
		}
	}

	/**
	 * The Class Expression.
	 */
	public abstract static class Expression {

		/**
		 * Instantiates a new expression.
		 */
		public Expression() {
		}

		/*(non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		public abstract String toString();

		/**
		 * Tokenize.
		 *
		 * @param text the text
		 * @return the list
		 * @throws FinderParseException the finder parse exception
		 */
		public static List<Token> tokenize(String text) throws FinderParseException {
			List<Token> tokens = new ArrayList<Token>();
			String buffer = text.trim();
			int position = 1;
			BufferLoop: while (buffer.length() > 0) {
				for (TokenType type : TokenType.values()) {
					Matcher matcher = type.pattern.matcher(buffer);
					if (matcher.find()) {
						String key1 = null;
						if (matcher.namedGroups().containsKey("k1")) {
							key1 = TokenType.unescapeKey(matcher.group("k1"));
						}
						String key2 = null;
						if (matcher.namedGroups().containsKey("k2")) {
							key2 = TokenType.unescapeKey(matcher.group("k2"));
						}
						String value = null;
						if (matcher.namedGroups().containsKey("val")) {
							value = TokenType.unescapeValue(matcher.group("val"));
						}
						Token token = new Token(key1, key2, value, position, type);
						tokens.add(token);
						buffer = buffer.substring(matcher.end());
						position += matcher.end();
						continue BufferLoop;
					}
				}
				throw new FinderParseException("Parsing error, unknown token at character %d.".formatted(position));
			}
			return tokens;
		}

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			ListIterator<Token> t = tokens.listIterator();
			int brackets = 0;
			List<Token> subTokens = new ArrayList<Token>();
			while (t.hasNext()) {
				Token token = t.next();
				if (token.type == TokenType.BRACKETIN) {
					brackets++;
					if (brackets > 1) {
						subTokens.add(token);
					}
					t.remove();
				}
				else if (token.type == TokenType.BRACKETOUT) {
					brackets--;
					if (brackets < 0) {
						throw new FinderParseException(
							"Parsing error, unexpected closing bracket.");
					}
					else if (brackets == 0) {
						token.type = null;
						token.expression = Expression.parse(subTokens);
						subTokens.clear();
					}
					else {
						subTokens.add(token);
						t.remove();
					}
				}
				else if (brackets > 0) {
					subTokens.add(token);
					t.remove();
				}
			}
			if (brackets > 0) {
				throw new FinderParseException(
					"Parsing error, missing closing bracket.");
			}

			if (tokens.size() == 0) {
				throw new FinderParseException("Parsing error, no more token to parse");
			}

			if (tokens.size() == 1 && tokens.get(0).expression != null) {
				return tokens.get(0).expression;
			}

			Expression expr;

			expr = OrOperator.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = AndOperator.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = NotOperator.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = ModuleExpression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = InterfaceExpression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = VrfExpression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = VirtualNameExpression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = TagExpression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = DeviceExpression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = DomainExpression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = DriverExpression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = Ipv4Expression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = Ipv6Expression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = MacExpression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = DiagnosticExpression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = ComplianceRuleExpression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			expr = AttributeExpression.parse(tokens);
			if (expr != null) {
				return expr;
			}

			throw new FinderParseException("Parsing error at character %d.".formatted(tokens.get(0).position));
		}

		/**
		 * Check the validity of a RegExp pattern token.
		 * @param token the token to check
		 * @throws FinderParseException
		 */
		protected static void checkRegExp(Token token) throws FinderParseException {
			try {
				Pattern.compile(token.value);
			}
			catch (PatternSyntaxException e1) {
				// Note: should check for POSIX regular expressions, to match with DB RegExp engine.
				throw new FinderParseException("Parsing error, invalid regular expression, at character %d.".formatted(token.position));
			}
		}

	
		/**
		 * Builds the jOOQ condition on the devices ({@code DEVICE}) matching the expression.
		 *
		 * @param itemPrefix the prefix of the aliases of the subqueries, unique for each expression
		 * @return the condition
		 */
		public abstract Condition toCondition(String itemPrefix);

		/**
		 * Gets the device table of a subquery (with its own alias, as the {@code d} of the HQL subqueries).
		 *
		 * @param itemPrefix the prefix of the expression
		 * @return the aliased device table
		 */
		protected static net.netshot.netshot.platform.database.schema.tables.Device subDevice(String itemPrefix) {
			return DEVICE.as(itemPrefix + "_d");
		}

		/**
		 * Gets the LIKE pattern for a text comparison.
		 *
		 * @param sign the comparison
		 * @param value the value to compare with
		 * @return the pattern (the value itself for an exact or regular expression match)
		 */
		protected static String likeTarget(TokenType sign, String value) {
			return switch (sign) {
				case CONTAINS -> "%" + value + "%";
				case CONTAINSNOCASE -> "%" + value.toLowerCase() + "%";
				case STARTSWITH -> value + "%";
				case ENDSWITH -> "%" + value;
				default -> value;
			};
		}

		/**
		 * Compares a text column with a value.
		 *
		 * @param sign the comparison (regular expression, case insensitive or plain LIKE)
		 * @param field the column
		 * @param value the value to compare with
		 * @return the condition
		 */
		protected static Condition textCondition(TokenType sign, Field<String> field, String value) {
			if (TokenType.MATCHES.equals(sign)) {
				return DSL.condition("{0} ~ {1}", field, DSL.val(value));
			}
			if (TokenType.CONTAINSNOCASE.equals(sign)) {
				return DSL.lower(field).like(likeTarget(sign, value));
			}
			return field.like(likeTarget(sign, value));
		}
}

	/**
	 * The Class NotOperator.
	 */
	public static class NotOperator extends Expression {

		/** The child. */
		public Expression child;

		/**
		 * Instantiates a new not operator.
		 */
		public NotOperator() {
			super();
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			StringBuffer buffer = new StringBuffer();
			buffer.append(TokenType.NOT).append(" (").append(child.toString()).append(")");
			return buffer.toString();
		}

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			ListIterator<Token> t = tokens.listIterator();
			while (t.hasNext()) {
				boolean first = !t.hasPrevious();
				Token token = t.next();
				if (token.type == TokenType.NOT) {
					if (!first) {
						throw new FinderParseException(
							"Parsing error, misplaced NOT at character %d.".formatted(token.position));
					}
					else {
						NotOperator notExpr = new NotOperator();
						t.previous();
						t.remove();
						notExpr.child = Expression.parse(tokens);
						return notExpr;
					}
				}
			}
			return null;
		}

	
		@Override
		public Condition toCondition(String itemPrefix) {
			return DSL.not(this.child.toCondition(itemPrefix + "_0"));
		}
}

	/**
	 * The Class AndOperator.
	 */
	public static class AndOperator extends Expression {

		/** The children. */
		public List<Expression> children = new ArrayList<Expression>();

		/**
		 * Instantiates a new and operator.
		 */
		public AndOperator() {
			super();
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		public String toString() {
			StringBuffer buffer = new StringBuffer();
			int i = 0;
			buffer.append("(");
			for (Expression child : children) {
				if (i++ > 0) {
					buffer.append(") ").append(TokenType.AND).append(" (");
				}
				buffer.append(child.toString());
			}
			buffer.append(")");
			return buffer.toString();
		}

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			ListIterator<Token> t = tokens.listIterator();
			AndOperator andExpr = new AndOperator();
			List<Token> tokenBuffer = new ArrayList<Token>();
			while (t.hasNext()) {
				Token token = t.next();
				if (token.type == TokenType.AND) {
					if (tokenBuffer.size() == 0) {
						throw new FinderParseException("Parsing error, nothing before AND at character %d.".formatted(token.position));
					}
					andExpr.children.add(Expression.parse(tokenBuffer));
					tokenBuffer.clear();
				}
				else {
					tokenBuffer.add(token);
				}
			}
			if (andExpr.children.size() > 0) {
				if (tokenBuffer.size() == 0) {
					throw new FinderParseException(
						"Parsing error, nothing after last AND.");
				}
				andExpr.children.add(Expression.parse(tokenBuffer));
				return andExpr;
			}
			return null;
		}

	
		@Override
		public Condition toCondition(String itemPrefix) {
			List<Condition> parts = new ArrayList<>();
			for (Expression child : children) {
				parts.add(child.toCondition(itemPrefix + "_" + parts.size()));
			}
			return DSL.and(parts);
		}
}

	/**
	 * The Class OrOperator.
	 */
	public static class OrOperator extends Expression {

		/** The children. */
		public List<Expression> children = new ArrayList<Expression>();

		/**
		 * Instantiates a new or operator.
		 *
		 */
		public OrOperator() {
			super();
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		public String toString() {
			StringBuffer buffer = new StringBuffer();
			int i = 0;
			buffer.append("(");
			for (Expression child : children) {
				if (i++ > 0) {
					buffer.append(") ").append(TokenType.OR).append(" (");
				}
				buffer.append(child.toString());
			}
			buffer.append(")");
			return buffer.toString();
		}

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			ListIterator<Token> t = tokens.listIterator();
			OrOperator orExpr = new OrOperator();
			List<Token> tokenBuffer = new ArrayList<Token>();
			while (t.hasNext()) {
				Token token = t.next();
				if (token.type == TokenType.OR) {
					if (tokenBuffer.size() == 0) {
						throw new FinderParseException("Parsing error, nothing before OR at character %d.".formatted(token.position));
					}
					orExpr.children.add(Expression.parse(tokenBuffer));
					tokenBuffer.clear();
				}
				else {
					tokenBuffer.add(token);
				}
			}
			if (orExpr.children.size() > 0) {
				if (tokenBuffer.size() == 0) {
					throw new FinderParseException(
						"Parsing error, nothing after last OR.");
				}
				orExpr.children.add(Expression.parse(tokenBuffer));
				return orExpr;
			}
			return null;
		}
	
		@Override
		public Condition toCondition(String itemPrefix) {
			List<Condition> parts = new ArrayList<>();
			for (Expression child : children) {
				parts.add(child.toCondition(itemPrefix + "_" + parts.size()));
			}
			return DSL.or(parts);
		}
}

	/**
	 * The Class ModuleExpression.
	 */
	public static class ModuleExpression extends Expression {

		/**
		 * Instantiates a new module expression.
		 */
		public ModuleExpression() {
			super();
		}

		/** The sign. */
		public TokenType sign;

		/** The value. */
		private String value;

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.size() == 3 && tokens.get(0).type == TokenType.MODULE) {
				Token comparator = tokens.get(1);
				Token value = tokens.get(2);
				switch (comparator.type) {
					case IS:
					case CONTAINS:
					case CONTAINSNOCASE:
					case STARTSWITH:
					case ENDSWITH:
					case MATCHES:
						if (value.type == TokenType.QUOTEDTEXT) {
							if (comparator.type == TokenType.MATCHES) {
								checkRegExp(value);
							}
							ModuleExpression modExpr = new ModuleExpression();
							modExpr.sign = comparator.type;
							modExpr.value = value.value;
							return modExpr;
						}
						else {
							throw new FinderParseException("Expecting a quoted string for MODULE at character %d.".formatted(value.position));
						}
					default:
						throw new FinderParseException("Invalid operator after MODULE at character %d.".formatted(comparator.position));
				}
			}
			return null;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			return "[%s] %s \"%s\"".formatted(TokenType.MODULE, sign,
				TokenType.escapeValue(value));
		}

	
		@Override
		public Condition toCondition(String itemPrefix) {
			var d = subDevice(itemPrefix);
			var m = MODULE.as(itemPrefix + "_m");
			return DEVICE.ID.in(DSL.select(d.ID).from(d).leftJoin(m).on(m.DEVICE.eq(d.ID))
				.where(DSL.condition("{0} is not true", m.REMOVED)
					.and(textCondition(sign, m.SERIAL_NUMBER, value).or(textCondition(sign, m.PART_NUMBER, value)))));
		}
}

	/**
	 * The Class InterfaceExpression.
	 */
	public static class InterfaceExpression extends Expression {

		/**
		 * Instantiates a new interface expression.
		 */
		public InterfaceExpression() {
			super();
		}

		/** The sign. */
		public TokenType sign;

		/** The value. */
		private String value;

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.size() == 3 && tokens.get(0).type == TokenType.INTERFACE) {
				Token comparator = tokens.get(1);
				Token value = tokens.get(2);
				switch (comparator.type) {
					case IS:
					case CONTAINS:
					case CONTAINSNOCASE:
					case STARTSWITH:
					case ENDSWITH:
					case MATCHES:
						if (value.type == TokenType.QUOTEDTEXT) {
							if (comparator.type == TokenType.MATCHES) {
								checkRegExp(value);
							}
							InterfaceExpression modExpr = new InterfaceExpression();
							modExpr.sign = comparator.type;
							modExpr.value = value.value;
							return modExpr;
						}
						else {
							throw new FinderParseException("Expecting a quoted string for INTERFACE at character %d.".formatted(value.position));
						}
					default:
						throw new FinderParseException("Invalid operator after INTERFACE at character %d.".formatted(comparator.position));
				}
			}
			return null;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			return "[%s] %s \"%s\"".formatted(TokenType.INTERFACE, sign,
				TokenType.escapeValue(value));
		}

	
		@Override
		public Condition toCondition(String itemPrefix) {
			var d = subDevice(itemPrefix);
			var ni = NETWORK_INTERFACE.as(itemPrefix + "_ni");
			return DEVICE.ID.in(DSL.select(d.ID).from(d).leftJoin(ni).on(ni.DEVICE.eq(d.ID))
				.where(textCondition(sign, ni.INTERFACE_NAME, value).or(textCondition(sign, ni.DESCRIPTION, value))));
		}
}

	/**
	 * The Class DeviceExpression.
	 */
	public static class DeviceExpression extends Expression {

		/**
		 * Instantiates a new device expression.
		 *
		 */
		public DeviceExpression() {
			super();
		}

		/** The value. */
		private Long value;

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.size() == 3 && tokens.get(0).type == TokenType.ID) {
				Token comparator = tokens.get(1);
				Token value = tokens.get(2);
				if (comparator.type != TokenType.IS) {
					throw new FinderParseException("Invalid operator after ID at character %d.".formatted(comparator.position));
				}
				if (value.type != TokenType.NUMERICVALUE) {
					throw new FinderParseException("Expecting a numeric value for ID at character %d.".formatted(value.position));
				}
				DeviceExpression devExpr = new DeviceExpression();
				devExpr.value = Long.parseLong(value.value);
				return devExpr;
			}
			return null;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			return "[%s] %s %d".formatted(TokenType.ID, TokenType.IS, this.value);
		}

	
		@Override
		public Condition toCondition(String itemPrefix) {
			return DEVICE.ID.eq(value);
		}
}

	/**
	 * The Class DomainExpression. Matches a device domain.
	 */
	public static class DomainExpression extends Expression {

		/** The value. */
		private Long value;

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.size() == 3 && tokens.get(0).type == TokenType.DOMAIN) {
				Token comparator = tokens.get(1);
				Token value = tokens.get(2);
				if (comparator.type != TokenType.IS) {
					throw new FinderParseException("Invalid operator after DOMAIN at character %d.".formatted(comparator.position));
				}
				if (value.type != TokenType.NUMERICVALUE) {
					throw new FinderParseException("Expecting a numeric value for DOMAIN at character %d.".formatted(value.position));
				}
				DomainExpression domExpr = new DomainExpression();
				domExpr.value = Long.parseLong(value.value);
				return domExpr;
			}
			return null;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			return "[%s] %s %d".formatted(TokenType.DOMAIN, TokenType.IS, this.value);
		}
	
		@Override
		public Condition toCondition(String itemPrefix) {
			return DEVICE.MGMT_DOMAIN.eq(value);
		}
}

	/**
	 * The Class DriverExpression. Matches a device driver.
	 */
	public static class DriverExpression extends Expression {

		/** The sign. */
		public TokenType sign;

		/** The value. */
		private String value;

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.size() == 3
					&& (tokens.get(0).type == TokenType.TYPE || tokens.get(0).type == TokenType.DRIVER)) {
				Token comparator = tokens.get(1);
				Token value = tokens.get(2);
				switch (comparator.type) {
					case IS:
					case CONTAINS:
					case CONTAINSNOCASE:
					case STARTSWITH:
					case ENDSWITH:
					case MATCHES:
						if (value.type == TokenType.QUOTEDTEXT) {
							if (comparator.type == TokenType.MATCHES) {
								checkRegExp(value);
							}
							DriverExpression driverExpr = new DriverExpression();
							driverExpr.sign = comparator.type;
							driverExpr.value = value.value;
							return driverExpr;
						}
						else {
							throw new FinderParseException("Expecting a quoted string for DRIVER at character %d.".formatted(value.position));
						}
					default:
						throw new FinderParseException("Invalid operator after DRIVER at character %d.".formatted(comparator.position));
				}
			}
			return null;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			return "[%s] %s \"%s\"".formatted(TokenType.TYPE, sign,
				TokenType.escapeValue(value));
		}
	
		@Override
		public Condition toCondition(String itemPrefix) {
			CaseValueStep<String> choose = DSL.choose(DEVICE.DRIVER);
			CaseWhenStep<String, String> cases = null;
			for (DeviceDriver driver : Finder.driverRegistry.getAllDrivers()) {
				cases = cases == null
					? choose.when(DSL.inline(driver.getName()), DSL.inline(driver.getDescription()))
					: cases.when(DSL.inline(driver.getName()), DSL.inline(driver.getDescription()));
			}
			Field<String> description = cases == null ? DSL.inline((String) null, String.class) : cases;
			return textCondition(sign, description, value);
		}
}

	/**
	 * The Class NullExpression.
	 */
	public static class NullExpression extends Expression {

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			return "";
		}

	
		@Override
		public Condition toCondition(String itemPrefix) {
			return DSL.trueCondition();
		}
}

	/**
	 * The Class MacExpression.
	 */
	public static class MacExpression extends Expression {

		/** The sign. */
		public TokenType sign;

		/** The target. */
		public PhysicalAddress target;

		/** The prefix length. */
		public int prefixLength;

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.get(0).type == TokenType.MAC) {
				if (tokens.size() != 3) {
					throw new FinderParseException("Incomplete or incorrect expression after MAC at character %d.".formatted(tokens.get(0).position));
				}
				Token comparator = tokens.get(1);
				Token value = tokens.get(2);

				String mac = "";

				MacExpression macExpr = new MacExpression();
				if (value.type == TokenType.MACADDRESS) {
					if (comparator.type != TokenType.IS) {
						throw new FinderParseException("Invalid operator for simple MAC at character %d.".formatted(comparator.position));
					}
					mac = value.value;
				}
				else if (value.type == TokenType.MACSUBNET) {
					if (comparator.type != TokenType.IN) {
						throw new FinderParseException("Invalid operator for MAC with mask at character %d.".formatted(comparator.position));
					}
					String[] values = value.value.split("/");
					mac = values[0];
					macExpr.prefixLength = Integer.parseInt(values[1]);
				}
				else {
					return null;
				}

				macExpr.sign = comparator.type;
				try {
					macExpr.target = new PhysicalAddress(mac);
				}
				catch (ParseException e) {
					throw new FinderParseException("Error while parsing MAC address at character %d.".formatted(value.position));
				}
				return macExpr;
			}
			return null;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			String mac = this.target.toString();
			if (this.sign == TokenType.IN) {
				mac += "/" + this.prefixLength;
			}
			return "[MAC] %s %s".formatted(this.sign, mac);
		}

	
		@Override
		public Condition toCondition(String itemPrefix) {
			var d = subDevice(itemPrefix);
			var ni = NETWORK_INTERFACE.as(itemPrefix + "_ni");
			Condition where;
			if (this.sign == TokenType.IN) {
				long mask = 0xFFFFFFFFFFFFFFFFL << (48 - this.prefixLength);
				long min = this.target.getLongAddress() & mask;
				long max = this.target.getLongAddress() | ~mask;
				where = ni.PHYSICAL_ADDRESS.ge(min).and(ni.PHYSICAL_ADDRESS.le(max));
			}
			else {
				where = ni.PHYSICAL_ADDRESS.eq(this.target.getLongAddress());
			}
			return DEVICE.ID.in(DSL.select(d.ID).from(d).leftJoin(ni).on(ni.DEVICE.eq(d.ID)).where(where));
		}
}

	/**
	 * The Class Ipv4Expression.
	 */
	public static class Ipv4Expression extends Expression {

		/** The sign. */
		public TokenType sign;

		/** The with mask. */
		public boolean withMask;

		/** The target. */
		public Network4Address target;

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.get(0).type == TokenType.IP) {
				if (tokens.size() != 3) {
					throw new FinderParseException("Incomplete or incorrect expression after IP at character %d.".formatted(tokens.get(0).position));
				}
				Token comparator = tokens.get(1);
				Token value = tokens.get(2);

				int prefixLength = 32;
				String ip = "";

				Ipv4Expression ipExpr = new Ipv4Expression();
				if (value.type == TokenType.IPV4) {
					if (comparator.type != TokenType.IS) {
						throw new FinderParseException("Invalid operator for simple IP at character %d.".formatted(comparator.position));
					}
					ip = value.value;
					ipExpr.withMask = false;
				}
				else if (value.type == TokenType.SUBNETV4) {
					if (comparator.type != TokenType.IS
						&& comparator.type != TokenType.IN
						&& comparator.type != TokenType.CONTAINS) {
						throw new FinderParseException("Invalid operator for IP subnet at character %d.".formatted(comparator.position));
					}
					String[] values = value.value.split("/");
					ip = values[0];
					prefixLength = Integer.parseInt(values[1]);
					ipExpr.withMask = true;
					if (comparator.type == TokenType.CONTAINS && ipExpr.withMask) {
						throw new FinderParseException(
							"'IP contains' must be followed by a simple IP address (at character %d).".formatted(comparator.position));
					}
				}
				else {
					return null;
				}

				ipExpr.sign = comparator.type;
				try {
					ipExpr.target = new Network4Address(ip, prefixLength);
				}
				catch (UnknownHostException e) {
					throw new FinderParseException(
						"Error while parsing IP address at character %d.".formatted(value.position));
				}
				return ipExpr;
			}
			return null;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			return "[IP] %s %s".formatted(this.sign,
				this.withMask ? this.target.getPrefix() : this.target.getIp());
		}

	
		@Override
		public Condition toCondition(String itemPrefix) {
			var d = subDevice(itemPrefix);
			var ni = NETWORK_INTERFACE.as(itemPrefix + "_ni");
			var ip4 = NETWORK_INTERFACE_IP4ADDRESSES.as(itemPrefix + "_ip4");
			Condition where;
			if (this.sign == TokenType.IN) {
				String cidr = this.target.getPrefix();
				where = DSL.condition("{0} <<= cast({1} as inet)", d.CACHED_IP_ADDRESS, DSL.val(cidr))
					.or(DSL.condition("{0} <<= cast({1} as inet)", ip4.ADDRESS, DSL.val(cidr)));
			}
			else if (this.withMask) {
				where = ip4.ADDRESS.eq(DSL.val(this.target.getInetAddress(), ip4.ADDRESS.getDataType()))
					.and(ip4.PREFIX_LENGTH.eq(this.target.getPrefixLength()));
			}
			else if (this.sign == TokenType.CONTAINS) {
				where = DSL.condition("{0} <<= cast((host({1}) || '/' || {2}) as inet)",
					DSL.val(this.target.getInetAddress(), ip4.ADDRESS.getDataType()), ip4.ADDRESS, ip4.PREFIX_LENGTH);
			}
			else {
				where = d.CACHED_IP_ADDRESS.eq(DSL.val(this.target.getInetAddress(), d.CACHED_IP_ADDRESS.getDataType()))
					.or(ip4.ADDRESS.eq(DSL.val(this.target.getInetAddress(), ip4.ADDRESS.getDataType())));
			}
			return DEVICE.ID.in(DSL.select(d.ID).from(d).leftJoin(ni).on(ni.DEVICE.eq(d.ID))
				.leftJoin(ip4).on(ip4.NETWORK_INTERFACE.eq(ni.ID)).where(where));
		}
}

	/**
	 * The Class Ipv6Expression.
	 */
	public static class Ipv6Expression extends Expression {

		/** The sign. */
		public TokenType sign;

		/** The with mask. */
		public boolean withMask;

		/** The target. */
		public Network6Address target;

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.get(0).type == TokenType.IP) {
				if (tokens.size() != 3) {
					throw new FinderParseException("Incomplete expression after IP at character %d.".formatted(tokens.get(0).position));
				}
				Token comparator = tokens.get(1);
				Token value = tokens.get(2);

				int prefixLength = 128;
				String ip = "";

				Ipv6Expression ipExpr = new Ipv6Expression();
				if (value.type == TokenType.IPV6) {
					if (comparator.type != TokenType.IS) {
						throw new FinderParseException("Invalid operator for simple IP at character %d.".formatted(comparator.position));
					}
					ip = value.value;
					ipExpr.withMask = false;
				}
				else if (value.type == TokenType.SUBNETV6) {
					if (comparator.type != TokenType.IS
						&& comparator.type != TokenType.IN) {
						throw new FinderParseException("Invalid operator for IP subnet at character %d.".formatted(comparator.position));
					}
					String[] values = value.value.split("/");
					ip = values[0];
					prefixLength = Integer.parseInt(values[1]);
					ipExpr.withMask = true;
				}

				ipExpr.sign = comparator.type;
				try {
					ipExpr.target = new Network6Address(ip, prefixLength);
				}
				catch (UnknownHostException e) {
					throw new FinderParseException(
						"Error while parsing IP address at character %d.".formatted(value.position));
				}
				return ipExpr;
			}
			return null;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			return "[IP] %s %s".formatted(this.sign,
				this.withMask ? this.target.getPrefix() : this.target.getIp());
		}

	
		@Override
		public Condition toCondition(String itemPrefix) {
			var d = subDevice(itemPrefix);
			var ni = NETWORK_INTERFACE.as(itemPrefix + "_ni");
			var ip6 = NETWORK_INTERFACE_IP6ADDRESSES.as(itemPrefix + "_ip6");
			Condition where;
			if (this.sign == TokenType.IN) {
				String cidr = this.target.getPrefix();
				where = DSL.condition("{0} <<= cast({1} as inet)", d.CACHED_IP_ADDRESS, DSL.val(cidr))
					.or(DSL.condition("{0} <<= cast({1} as inet)", ip6.ADDRESS, DSL.val(cidr)));
			}
			else if (this.withMask) {
				where = ip6.ADDRESS.eq(DSL.val(this.target.getInetAddress(), ip6.ADDRESS.getDataType()))
					.and(ip6.PREFIX_LENGTH.eq(this.target.getPrefixLength()));
			}
			else {
				where = d.CACHED_IP_ADDRESS.eq(DSL.val(this.target.getInetAddress(), d.CACHED_IP_ADDRESS.getDataType()))
					.or(ip6.ADDRESS.eq(DSL.val(this.target.getInetAddress(), ip6.ADDRESS.getDataType())));
			}
			return DEVICE.ID.in(DSL.select(d.ID).from(d).leftJoin(ni).on(ni.DEVICE.eq(d.ID))
				.leftJoin(ip6).on(ip6.NETWORK_INTERFACE.eq(ni.ID)).where(where));
		}
}

	/**
	 * The Class DiagnosticExpression - base class for diagnostic result expressions.
	 */
	public abstract static class DiagnosticExpression extends Expression {

		/** The diagnostic. */
		protected Diagnostic diagnostic;

		/** The sign/operator. */
		protected TokenType sign;

		/**
		 * Instantiates a new diagnostic expression.
		 *
		 * @param diagnostic the diagnostic
		 */
		public DiagnosticExpression(Diagnostic diagnostic) {
			super();
			this.diagnostic = diagnostic;
		}

		/**
		 * Gets the text value representation.
		 *
		 * @return the text value
		 */
		protected abstract String getTextValue();

		@Override
		public String toString() {
			return "[%s > %s] %s %s".formatted(
				TokenType.DIAGNOSTIC,
				TokenType.escapeKey(this.diagnostic.getName()),
				sign,
				this.getTextValue());
		}

		/**
		 * Parses the tokens to create a diagnostic expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.size() == 0) {
				return null;
			}
			if (tokens.get(0).type != TokenType.DIAGNOSTIC) {
				return null;
			}

			// Fetch diagnostic once and cache it
			Token token = tokens.get(0);
			Diagnostic diagnostic = getDiagnosticByName(token);
			if (diagnostic == null) {
				throw new FinderParseException("Unknown diagnostic '%s' at character %d."
					.formatted(token.key2, token.position));
			}

			switch (diagnostic.getResultType()) {
			case BINARY:
				Expression binaryExpression = BinaryDiagnosticExpression.parse(tokens, diagnostic);
				if (binaryExpression != null) {
					return binaryExpression;
				}
				break;
			case NUMERIC:
				Expression numExpression = NumericDiagnosticExpression.parse(tokens, diagnostic);
				if (numExpression != null) {
					return numExpression;
				}
				break;
			case TEXT:
				Expression textExpression = TextDiagnosticExpression.parse(tokens, diagnostic);
				if (textExpression != null) {
					return textExpression;
				}
				break;
			default:
				throw new FinderParseException(
					"Unsupported diagnostic result type %s (%s).".formatted(
						diagnostic.getResultType(), diagnostic.getName()));
			}

			// Diagnostic found but syntax doesn't match any type
			throw new FinderParseException("Invalid expression for diagnostic '%s' at character %d.".formatted(
				diagnostic.getName(), token.position));
		}

		/**
		 * Gets the diagnostic by name.
		 *
		 * @param token the token containing the diagnostic name
		 * @return the diagnostic
		 * @throws FinderParseException the finder parse exception
		 */
		private static Diagnostic getDiagnosticByName(Token token) throws FinderParseException {
			String diagnosticName = token.key2;
			Session session = Database.getSession();
			try {
				Diagnostic diagnostic = session
					.createQuery("select d from Diagnostic d where d.name = :name", Diagnostic.class)
					.setParameter("name", diagnosticName)
					.uniqueResult();
				return diagnostic;
			}
			catch (HibernateException e) {
				log.error("Error while retrieving diagnostic '{}'.", diagnosticName, e);
				throw new FinderParseException("Database error while retrieving diagnostic '%s'.".formatted(diagnosticName));
			}
			finally {
				session.close();
			}
		}
	
		/**
		 * Builds the condition on the diagnostic result.
		 *
		 * @param result the diagnostic result table (aliased)
		 * @return the condition
		 */
		protected abstract Condition buildCondition(net.netshot.netshot.platform.database.schema.tables.DiagnosticResult result);

		@Override
		public Condition toCondition(String itemPrefix) {
			var d = subDevice(itemPrefix);
			var dr = DIAGNOSTIC_RESULT.as(itemPrefix + "_dr");
			return DEVICE.ID.in(DSL.select(d.ID).from(d).join(dr).on(dr.DEVICE.eq(d.ID))
				.where(dr.DIAGNOSTIC.eq(this.diagnostic.getId()).and(this.buildCondition(dr))));
		}
}

	/**
	 * The Class TextDiagnosticExpression.
	 */
	public static class TextDiagnosticExpression extends DiagnosticExpression {

		/** The value. */
		private String value;

		/**
		 * Instantiates a new text diagnostic expression.
		 *
		 * @param diagnostic the diagnostic
		 */
		public TextDiagnosticExpression(Diagnostic diagnostic) {
			super(diagnostic);
		}

		@Override
		protected String getTextValue() {
			return "\"%s\"".formatted(TokenType.escapeValue(value));
		}

		/**
		 * Parses the tokens to create a text diagnostic expression.
		 *
		 * @param tokens the tokens
		 * @param diagnostic the cached diagnostic
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens, Diagnostic diagnostic) throws FinderParseException {
			if (tokens.size() != 3) {
				return null;
			}
			Token sign = tokens.get(1);
			Token value = tokens.get(2);

			// Check syntax first
			if (sign.type != TokenType.IS
				&& sign.type != TokenType.CONTAINS
				&& sign.type != TokenType.CONTAINSNOCASE
				&& sign.type != TokenType.MATCHES
				&& sign.type != TokenType.STARTSWITH
				&& sign.type != TokenType.ENDSWITH) {
				throw new FinderParseException(
					"Invalid operator for a text diagnostic '%s' at character %d."
					.formatted(diagnostic.getName(), sign.position));
			}
			if (value.type != TokenType.QUOTEDTEXT) {
				throw new FinderParseException(
					"Parsing error, invalid value for text diagnostic '%s' at position %d, should be a quoted text."
					.formatted(diagnostic.getName(), value.position, TokenType.TRUE, TokenType.FALSE));
			}

			// Build expression
			if (sign.type == TokenType.MATCHES) {
				checkRegExp(value);
			}
			TextDiagnosticExpression textExpr = new TextDiagnosticExpression(diagnostic);
			textExpr.value = value.value;
			textExpr.sign = sign.type;
			return textExpr;
		}
	
		@Override
		protected Condition buildCondition(net.netshot.netshot.platform.database.schema.tables.DiagnosticResult result) {
			return textCondition(sign, result.TEXT, value);
		}
}

	/**
	 * The Class NumericDiagnosticExpression.
	 */
	public static class NumericDiagnosticExpression extends DiagnosticExpression {

		/** The value. */
		private double value;

		/**
		 * Instantiates a new numeric diagnostic expression.
		 *
		 * @param diagnostic the diagnostic
		 */
		public NumericDiagnosticExpression(Diagnostic diagnostic) {
			super(diagnostic);
		}

		@Override
		protected String getTextValue() {
		if (value == Math.floor(value)) {
			return String.format("%.0f", value);
		}
		return Double.toString(value);
		}

		/**
		 * Parses the tokens to create a numeric diagnostic expression.
		 *
		 * @param tokens the tokens
		 * @param diagnostic the cached diagnostic
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens, Diagnostic diagnostic) throws FinderParseException {
			if (tokens.size() != 3) {
				return null;
			}
			Token sign = tokens.get(1);
			Token value = tokens.get(2);

			if (sign.type != TokenType.IS
				&& sign.type != TokenType.LESSTHAN
						&& sign.type != TokenType.GREATERTHAN) {
				throw new FinderParseException(
					"Invalid operator for a numeric diagnostic '%s' at character %d."
					.formatted(diagnostic.getName(), sign.position));
			}
			if (value.type != TokenType.NUMERICVALUE) {
				throw new FinderParseException(
					"Parsing error, invalid value for numeric diagnostic '%s' at position %d."
					.formatted(diagnostic.getName(), value.position));
			}

			NumericDiagnosticExpression numExpr = new NumericDiagnosticExpression(diagnostic);
			try {
				numExpr.value = NumberFormat.getInstance(Locale.ENGLISH).parse(value.value).doubleValue();
			}
			catch (ParseException e) {
				throw new FinderParseException("Can't parse the number '%s' at character %d."
					.formatted(value.value, value.position));
			}
			numExpr.sign = sign.type;
			return numExpr;
		}
	
		@Override
		protected Condition buildCondition(net.netshot.netshot.platform.database.schema.tables.DiagnosticResult result) {
			if (TokenType.GREATERTHAN.equals(sign)) {
				return result.NUMBER.gt(value);
			}
			else if (TokenType.LESSTHAN.equals(sign)) {
				return result.NUMBER.lt(value);
			}
			return result.NUMBER.eq(value);
		}
}

	/**
	 * The Class BinaryDiagnosticExpression.
	 */
	public static class BinaryDiagnosticExpression extends DiagnosticExpression {

		/** The value. */
		private boolean value;

		/**
		 * Instantiates a new binary diagnostic expression.
		 *
		 * @param diagnostic the diagnostic
		 */
		public BinaryDiagnosticExpression(Diagnostic diagnostic) {
			super(diagnostic);
		}

		@Override
		protected String getTextValue() {
			return value ? "true" : "false";
		}

		/**
		 * Parses the tokens to create a binary diagnostic expression.
		 *
		 * @param tokens the tokens
		 * @param diagnostic the cached diagnostic
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens, Diagnostic diagnostic) throws FinderParseException {
			if (tokens.size() != 3) {
				return null;
			}
			Token sign = tokens.get(1);
			Token value = tokens.get(2);

			// Check syntax first
			if (sign.type != TokenType.IS) {
				throw new FinderParseException(
					"Invalid operator for a binary diagnostic '%s' at character %d."
					.formatted(diagnostic.getName(), sign.position));
			}

			BinaryDiagnosticExpression binExpr = new BinaryDiagnosticExpression(diagnostic);
			binExpr.sign = sign.type;
			if (value.type == TokenType.TRUE) {
				binExpr.value = true;
			}
			else if (value.type == TokenType.FALSE) {
				binExpr.value = false;
			}
			else {
				throw new FinderParseException(
					"Parsing error, invalid value for binary diagnostic '%s' at position %d, should be '%s' or '%s'."
					.formatted(diagnostic.getName(), value.position, TokenType.TRUE, TokenType.FALSE));
			}
			return binExpr;
		}
	
		@Override
		protected Condition buildCondition(net.netshot.netshot.platform.database.schema.tables.DiagnosticResult result) {
			return result.ASSUMPTION.eq(value);
		}
}

	/**
	 * The Class ComplianceRuleExpression.
	 */
	public static class ComplianceRuleExpression extends Expression {

		/** The policy. */
		protected Policy policy;

		/** The rule. */
		protected Rule rule;

		/** The result value. */
		private CheckResult.ResultOption value;

		/**
		 * Instantiates a new compliance rule expression.
		 *
		 * @param policy the policy
		 * @param rule the rule
		 */
		public ComplianceRuleExpression(Policy policy, Rule rule) {
			super();
			this.policy = policy;
			this.rule = rule;
		}

		@Override
		public String toString() {
			return "[%s > %s > %s] %s %s".formatted(
				TokenType.RULE,
				TokenType.escapeKey(this.policy.getName()),
				TokenType.escapeKey(this.rule.getName()),
				TokenType.IS,
				this.value);
		}

		/**
		 * Parses the tokens to create a compliance rule expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.size() == 0) {
				return null;
			}
			if (tokens.get(0).type != TokenType.RULE) {
				return null;
			}

			// Fetch policy and rule once and cache them
			Token token = tokens.get(0);
			Policy policy = getPolicyByName(token.key1);
			if (policy == null) {
				throw new FinderParseException("Unknown policy '%s' at character %d.".formatted(
					token.key1, token.position));
			}

			Rule rule = getRuleByPolicyAndName(policy, token.key2);
			if (rule == null) {
				throw new FinderParseException("Unknown rule '%s' for policy '%s' at character %d.".formatted(
					token.key2, policy.getName(), token.position));
			}

			// Validate syntax
			if (tokens.size() != 3) {
				throw new FinderParseException("Incomplete or incorrect expression after rule item at character %d.".formatted(tokens.get(0).position));
			}
			Token sign = tokens.get(1);
			Token value = tokens.get(2);

			if (sign.type != TokenType.IS) {
				throw new FinderParseException("Invalid operator for a rule item at character %d.".formatted(sign.position));
			}
			if (value.type != TokenType.QUOTEDTEXT && value.type != TokenType.ENUMWORD) {
				throw new FinderParseException("Parsing error, should be a quoted text or enum keyword at character %d.".formatted(value.position));
			}

			ComplianceRuleExpression ruleExpr = new ComplianceRuleExpression(policy, rule);
			try {
				ruleExpr.value = CheckResult.ResultOption.valueOf(value.value);
			}
			catch (Exception e) {
				throw new FinderParseException(
					"Invalid value for rule result at character %d.".formatted(value.position));
			}
			return ruleExpr;
		}

		/**
		 * Gets the policy by name.
		 *
		 * @param policyName the policy name
		 * @return the policy
		 * @throws FinderParseException the finder parse exception
		 */
		private static Policy getPolicyByName(String policyName) throws FinderParseException {
			Session session = Database.getSession();
			try {
				Policy policy = session
					.createQuery("select p from Policy p where p.name = :name", Policy.class)
					.setParameter("name", policyName)
					.uniqueResult();
				return policy;
			}
			catch (HibernateException e) {
				log.error("Error while retrieving policy '{}'.", policyName, e);
				throw new FinderParseException("Database error while retrieving policy '%s'.".formatted(policyName));
			}
			finally {
				session.close();
			}
		}

		/**
		 * Gets the rule by policy and name.
		 *
		 * @param policy the policy
		 * @param ruleName the rule name
		 * @return the rule
		 * @throws FinderParseException the finder parse exception
		 */
		private static Rule getRuleByPolicyAndName(Policy policy, String ruleName) throws FinderParseException {
			Session session = Database.getSession();
			try {
				Rule rule = session
					.createQuery("select r from Rule r where r.policy = :policy and r.name = :name", Rule.class)
					.setParameter("policy", policy)
					.setParameter("name", ruleName)
					.uniqueResult();
				return rule;
			}
			catch (HibernateException e) {
				log.error("Error while retrieving rule '{}' for policy '{}'.", ruleName, policy.getName(), e);
				throw new FinderParseException("Database error while retrieving rule '%s' for policy '%s'.".formatted(ruleName, policy.getName()));
			}
			finally {
				session.close();
			}
		}
	
		@Override
		public Condition toCondition(String itemPrefix) {
			var d = subDevice(itemPrefix);
			var cr = CHECK_RESULT.as(itemPrefix + "_cr");
			return DEVICE.ID.in(DSL.select(d.ID).from(d).join(cr).on(cr.DEVICE.eq(d.ID))
				.where(cr.RULE.eq(this.rule.getId()).and(cr.RESULT.eq(this.value.name()))));
		}
}

	/**
	 * Base class for (device/config) attribute-based expressions.
	 */
	public abstract static class AttributeExpression extends Expression {

		/** The attribute. */
		protected AttributeDefinition attribute;

		/** The sign. */
		public TokenType sign;

		/**
		 * Instantiates a new attribute expression.
		 *
		 * @param attribute the attribute
		 */
		public AttributeExpression(AttributeDefinition attribute) {
			super();
			this.attribute = attribute;
		}

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.size() == 0) {
				return null;
			}
			if (tokens.get(0).type != TokenType.DRIVER_ATTR && tokens.get(0).type != TokenType.GENERIC_ATTR) {
				return null;
			}
			Expression textExpression = TextAttributeExpression.parse(tokens);
			if (textExpression != null) {
				return textExpression;
			}
			Expression dateExpression = DateAttributeExpression.parse(tokens);
			if (dateExpression != null) {
				return dateExpression;
			}
			Expression enumExpression = EnumAttributeExpression.parse(tokens);
			if (enumExpression != null) {
				return enumExpression;
			}
			Expression numExpression = NumericAttributeExpression.parse(tokens);
			if (numExpression != null) {
				return numExpression;
			}
			Expression binaryExpression = BinaryAttributeExpression.parse(tokens);
			if (binaryExpression != null) {
				return binaryExpression;
			}

			throw new FinderParseException("Unknown configuration field [%s] at character %d.".formatted(tokens.get(0).value, tokens.get(0).position));
		}

		protected static AttributeDefinition getDriverAttributeDefinition(
				Token itemToken, AttributeType type) throws FinderParseException {
			return getDriverAttributeDefinition(itemToken, List.of(type));
		}

		protected static AttributeDefinition getDriverAttributeDefinition(
				Token itemToken, List<AttributeType> possibleTypes) throws FinderParseException {
			DeviceDriver driver = Finder.driverRegistry.getDriverByDescription(itemToken.key1);
			if (driver == null) {
				throw new FinderParseException(
					"Parsing error at character %d, unknown driver '%s'."
					.formatted(itemToken.position, itemToken.key1));
			}
			AttributeDefinition attribute = driver.getAttributeDefinitionByTitle(itemToken.key2);
			if (attribute == null) {
				throw new FinderParseException(
					"Parsing error at character %d, attribute '%s' of driver '%s' does not exist."
					.formatted(itemToken.position, itemToken.key2, driver.getDescription()));
			}
			if (!attribute.isSearchable()) {
				throw new FinderParseException(
					"Parsing error at character %d, attribute '%s' of driver '%s' is not searchable."
					.formatted(itemToken.position, attribute.getTitle(), driver.getDescription()));
			}
			if (!possibleTypes.contains(attribute.getType())) {
				return null;
			}
			return attribute;
		}

		/**
		 * Gets the text value.
		 *
		 * @return the text value
		 */
		protected abstract String getTextValue();

		private String getItemLabel() {
			DeviceDriver driver = this.attribute.getDriver();
			if (driver == null) {
				return "%s".formatted(
					TokenType.escapeKey(this.attribute.getTitle())
				);
			}
			return "%s > %s".formatted(
				TokenType.escapeKey(driver.getDescription()),
				TokenType.escapeKey(this.attribute.getTitle())
			);
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		public String toString() {
			return "[%s] %s %s".formatted(this.getItemLabel(), sign, this.getTextValue());
		}

	
		/**
		 * Gets a column of the table holding the attribute value: the native column (named after the
		 * attribute) of the device or config, or the given column of the driver attribute table.
		 *
		 * @param <T> the type of the column
		 * @param table the table
		 * @param driverColumn the column holding the value of a driver attribute
		 * @param type the type of the column
		 * @return the column
		 */
		protected <T> Field<T> valueField(Table<?> table, String driverColumn, Class<T> type) {
			String column = this.attribute.getDriver() == null
				? this.attribute.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase()
				: driverColumn;
			Field<?> field = table.field(column);
			if (field == null) {
				// Unknown column: the query fails, as the former HQL one did
				return DSL.field(DSL.name(table.getName(), column), type);
			}
			if (!type.equals(field.getType())) {
				throw new IllegalStateException("Column %s of %s is a %s, not a %s".formatted(column, table.getName(),
					field.getType().getSimpleName(), type.getSimpleName()));
			}
			// The generated field, with its converter (the coerced one would bind the value with the default type)
			@SuppressWarnings("unchecked")
			Field<T> typed = (Field<T>) field;
			return typed;
		}

		/**
		 * Builds the condition on the table holding the attribute value.
		 *
		 * @param table the device, config, device attribute or config attribute table (aliased)
		 * @return the condition
		 */
		protected abstract Condition buildCondition(Table<?> table);

		@Override
		public Condition toCondition(String itemPrefix) {
			if (this.attribute.getDriver() == null) {
				if (AttributeLevel.CONFIG.equals(this.attribute.getLevel())) {
					// Native config property
					var d = subDevice(itemPrefix);
					var c = CONFIG.as(itemPrefix + "_c");
					return DEVICE.ID.in(DSL.select(d.ID).from(d, c)
						.where(d.LAST_CONFIG.eq(c.ID).and(this.buildCondition(c))));
				}
				// Native device property
				return this.buildCondition(DEVICE);
			}
			if (AttributeLevel.CONFIG.equals(this.attribute.getLevel())) {
				// Driver-specific config attribute
				var d = subDevice(itemPrefix);
				var c = CONFIG.as(itemPrefix + "_c");
				var ca = CONFIG_ATTRIBUTE.as(itemPrefix + "_ca");
				return DEVICE.ID.in(DSL.select(d.ID).from(d, c.join(ca).on(ca.CONFIG.eq(c.ID).and(ca.NAME.eq(this.attribute.getName()))))
					.where(d.DRIVER.eq(this.attribute.getDriver().getName()).and(d.LAST_CONFIG.eq(c.ID))
						.and(this.buildCondition(ca))));
			}
			// Driver-specific device attribute
			var d = subDevice(itemPrefix);
			var da = DEVICE_ATTRIBUTE.as(itemPrefix + "_da");
			return DEVICE.ID.in(DSL.select(d.ID).from(d.join(da).on(da.DEVICE.eq(d.ID).and(da.NAME.eq(this.attribute.getName()))))
				.where(d.DRIVER.eq(this.attribute.getDriver().getName()).and(this.buildCondition(da))));
		}
}

	/**
	 * The Class NumericAttributeExpression.
	 */
	public static class NumericAttributeExpression extends AttributeExpression {

		/** The value. */
		private Double value;

		/**
		 * Instantiates a new numeric config item expression.
		 *
		 * @param attribute the attribute
		 */
		public NumericAttributeExpression(AttributeDefinition attribute) {
			super(attribute);
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.AttributeExpression#getTextValue()
		 */
		protected String getTextValue() {
			NumberFormat format = NumberFormat.getInstance(Locale.ENGLISH);
			format.setGroupingUsed(false);
			return format.format(value);
		}

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			AttributeDefinition attribute = null;

			if (tokens.get(0).key1 != null) {
				attribute = AttributeExpression.getDriverAttributeDefinition(
					tokens.get(0), AttributeType.NUMERIC);
			}
			if (attribute == null) {
				return null;
			}
			if (tokens.size() != 3) {
				throw new FinderParseException("Incomplete or incorrect expression after numeric item at character %d.".formatted(tokens.get(0).position));
			}
			Token sign = tokens.get(1);
			Token value = tokens.get(2);

			if (sign.type != TokenType.IS
				&& sign.type != TokenType.LESSTHAN
						&& sign.type != TokenType.GREATERTHAN) {
				throw new FinderParseException("Parsing error, invalid numeric operator at position %d.".formatted(sign.position));
			}
			if (value.type != TokenType.NUMERICVALUE) {
				throw new FinderParseException("Parsing error, should be a numeric value at character %d.".formatted(value.position));
			}
			NumericAttributeExpression numExpr =
				new NumericAttributeExpression(attribute);

			try {
				numExpr.value = Double.parseDouble(value.value);
			}
			catch (NumberFormatException e) {
				throw new FinderParseException("Parsing error, uname to parse the numeric value at character %d.".formatted(value.position));
			}
			numExpr.sign = sign.type;
			return numExpr;
		}

	
		@Override
		protected Condition buildCondition(Table<?> table) {
			Field<Double> property = this.valueField(table, "number", Double.class);
			if (TokenType.GREATERTHAN.equals(sign)) {
				return property.gt(value);
			}
			else if (TokenType.LESSTHAN.equals(sign)) {
				return property.lt(value);
			}
			return property.eq(value);
		}
}

	/**
	 * The Class EnumAttributeExpression.
	 */
	public static class EnumAttributeExpression extends AttributeExpression {

		/** The value. */
		private Object value;

		/**
		 * Instantiates a new enum config item expression.
		 *
		 * @param attribute the attribute
		 */
		public EnumAttributeExpression(AttributeDefinition attribute) {
			super(attribute);
			this.sign = TokenType.IS;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.AttributeExpression#getTextValue()
		 */
		protected String getTextValue() {
			return "%s".formatted(TokenType.escapeValue(value.toString()));
		}

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			AttributeDefinition attribute = null;
			Class<? extends EnumAttribute> theEnum = null;

			if (tokens.get(0).key1 == null) {
				if ("Network Class".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.ENUM, 
						AttributeLevel.DEVICE,
						"networkClass",
						"Network Class");
					theEnum = NetworkClass.class;
				}
				else if ("Software Level".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.ENUM, 
						AttributeLevel.DEVICE,
						"softwareLevel",
					"Software Level");
					theEnum = SoftwareRule.ConformanceLevel.class;
				}
				else if ("Status".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.ENUM, 
						AttributeLevel.DEVICE,
						"status",
						"Status");
					theEnum = Device.Status.class;
				}
			}
			else {
				attribute = AttributeExpression.getDriverAttributeDefinition(
					tokens.get(0), AttributeType.ENUM);
			}
			
			if (attribute == null) {
				return null;
			}
			if (tokens.size() != 3) {
				throw new FinderParseException("Incomplete or incorrect expression after enum item at character %d.".formatted(tokens.get(0).position));
			}
			Token sign = tokens.get(1);
			Token value = tokens.get(2);
			if (sign.type != TokenType.IS) {
				throw new FinderParseException("Parsing error, invalid operator at position %d, should be 'is'.".formatted(sign.position));
			}
			if (value.type != TokenType.QUOTEDTEXT && value.type != TokenType.ENUMWORD) {
				throw new FinderParseException(
					"Parsing error, should be a quoted string or enum keyword at character %d."
						.formatted(value.position));
			}
			EnumAttributeExpression enumExpr =
				new EnumAttributeExpression(attribute);
			try {
				@SuppressWarnings({ "unchecked", "rawtypes" })
				Object choice = Enum.valueOf((Class) theEnum, value.value);
				enumExpr.value = choice;
			}
			catch (Exception e) {
				throw new FinderParseException(
					"Invalid value for item %s at character %d."
						.formatted(attribute.getTitle(), value.position));
			}
			return enumExpr;
		}
	
		@Override
		protected Condition buildCondition(Table<?> table) {
			// The enumerations are stored by name
			return this.valueField(table, "choice", String.class).eq(((Enum<?>) value).name());
		}
}

	/**
	 * The Class TextAttributeExpression.
	 */
	public static class TextAttributeExpression extends AttributeExpression {

		/** The value. */
		private String value;

		/**
		 * Instantiates a new text config item expression.
		 *
		 * @param attribute the attribute
		 */
		public TextAttributeExpression(AttributeDefinition attribute) {
			super(attribute);
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.AttributeExpression#getTextValue()
		 */
		protected String getTextValue() {
			return "\"%s\"".formatted(TokenType.escapeValue(value));
		}

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			AttributeDefinition attribute = null;

			if (tokens.get(0).key1 == null) {
				if ("Name".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.TEXT, 
						AttributeLevel.DEVICE,
						"name",
						"Name");
				}
				else if ("Comments".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.TEXT, 
						AttributeLevel.DEVICE,
						"comments",
						"Comments");
				}
				else if ("Family".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.TEXT, 
						AttributeLevel.DEVICE,
						"family",
						"Family");
				}
				else if ("Contact".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.TEXT, 
						AttributeLevel.DEVICE,
						"contact",
						"Contact");
				}
				else if ("Location".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.TEXT, 
						AttributeLevel.DEVICE,
						"location",
						"Location");
				}
				else if ("Software Version".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.TEXT, 
						AttributeLevel.DEVICE,
						"softwareVersion",
						"Software Version");
				}
				else if ("Serial Number".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.TEXT, 
						AttributeLevel.DEVICE,
						"serialNumber",
						"Serial Number");
				}
			}
			else {
				attribute = AttributeExpression.getDriverAttributeDefinition(
					tokens.get(0), List.of(AttributeType.TEXT, AttributeType.LONGTEXT));
			}

			if (attribute == null) {
				return null;
			}
			if (tokens.size() != 3) {
				throw new FinderParseException("Incomplete or incorrect expression after text item at character %d.".formatted(tokens.get(0).position));
			}
			Token sign = tokens.get(1);
			Token value = tokens.get(2);
			if (sign.type != TokenType.IS
				&& sign.type != TokenType.CONTAINS
				&& sign.type != TokenType.CONTAINSNOCASE
				&& sign.type != TokenType.MATCHES
				&& sign.type != TokenType.STARTSWITH
				&& sign.type != TokenType.ENDSWITH) {
				throw new FinderParseException("Invalid operator for a text item at character %d.".formatted(sign.position));
			}
			if (value.type != TokenType.QUOTEDTEXT) {
				throw new FinderParseException("Parsing error, should be a quoted text, at character %d.".formatted(value.position));
			}
			if (sign.type == TokenType.MATCHES) {
				checkRegExp(value);
			}
			TextAttributeExpression textExpr =
				new TextAttributeExpression(attribute);
			textExpr.value = value.value;
			textExpr.sign = sign.type;
			return textExpr;
		}
	
		@Override
		protected Condition buildCondition(Table<?> table) {
			Field<String> property = this.valueField(table, "text", String.class);
			if (this.attribute.getDriver() != null && AttributeType.LONGTEXT.equals(this.attribute.getType())) {
				// The long text is in its own table: an inner join, as the implicit one of the HQL path
				var lt = LONG_TEXT_CONFIGURATION.as(table.getName() + "_lt");
				@SuppressWarnings("unchecked")
				Field<Long> longText = (Field<Long>) table.field("long_text");
				return DSL.exists(DSL.selectOne().from(lt).where(lt.ID.eq(longText).and(textCondition(sign, lt.TEXT, value))));
			}
			return textCondition(sign, property, value);
		}
}

	/**
	 * The Class DateAttributeExpression.
	 */
	public static class DateAttributeExpression extends AttributeExpression {

		public abstract static class TypedDate {
			public static TypedDate parse(String text) throws ParseException {
				try {
					return new AbsoluteIso8601DateTime(text);
				}
				catch (DateTimeParseException e) {
					// Try next
				}
				try {
					return new AbsoluteDateTime(text);
				}
				catch (DateTimeParseException e) {
					// Try next
				}
				try {
					return new AbsoluteDateNoTime(text);
				}
				catch (DateTimeParseException e) {
					// Try next
				}
				try {
					return new RelativeDateNow(text);
				}
				catch (ParseException e) {
					// Try next
				}
				try {
					return new RelativeDateToday(text);
				}
				catch (ParseException e) {
					// Try next
				}
				throw new ParseException("Unable to parse date", 0);
			}

			public abstract Instant getStart();

			public abstract Instant getEnd();

			public abstract String getText();
		}

		public abstract static class AbsoluteDate extends TypedDate {
		}

		public static class AbsoluteDateNoTime extends AbsoluteDate {
			private LocalDate date;

			public AbsoluteDateNoTime(String text) {
				this.date = LocalDate.parse(text);
			}

			public String getText() {
				return this.date.toString();
			}

			public Instant getStart() {
				return this.date.atStartOfDay(ZoneId.systemDefault()).toInstant();
			}

			public Instant getEnd() {
				return this.date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
			}
		}

		public static class AbsoluteDateTime extends AbsoluteDate {
			private LocalDateTime date;

			public AbsoluteDateTime(String text) {
				this.date = LocalDateTime.parse(text);
			}

			public String getText() {
				return this.date.toString();
			}

			public Instant getStart() {
				return this.date.atZone(ZoneId.systemDefault()).toInstant();
			}

			public Instant getEnd() {
				return this.date.atZone(ZoneId.systemDefault()).toInstant();
			}
		}

		public static class AbsoluteIso8601DateTime extends AbsoluteDate {
			private ZonedDateTime date;

			public AbsoluteIso8601DateTime(String text) throws DateTimeParseException {
				this.date = ZonedDateTime.parse(text);
			}

			public String getText() {
				return this.date.toString();
			}

			public Instant getStart() {
				return this.date.toInstant();
			}

			public Instant getEnd() {
				return this.date.toInstant();
			}
		}

		public abstract static class RelativeDate extends TypedDate {

		}

		public static class RelativeDateNow extends RelativeDate {
			private static final Pattern NOW_PATTERN = Pattern.compile(
				"(?i)^Now(\\s*((?<days>\\+|\\-\\s*[0-9]+)\\s*D))?(\\s*((?<hours>\\+|\\-\\s*[0-9]+)\\s*H))?$");

			private long dayShift;
			private long hourShift;

			public RelativeDateNow(String text) throws ParseException {
				Matcher matcher = NOW_PATTERN.matcher(text);
				if (!matcher.matches()) {
					throw new ParseException("Cannot parse Now-based relative time", 0);
				}
				if (matcher.group("days") != null) {
					this.dayShift = Long.parseLong(matcher.group("days").replace(" ", ""));
				}
				if (matcher.group("hours") != null) {
					this.hourShift = Long.parseLong(matcher.group("hours").replace(" ", ""));
				}
			}

			public String getText() {
				String text = "Now";
				if (this.dayShift != 0) {
					text += " %s%dd".formatted(this.dayShift > 0 ? "+" : "-", Math.abs(this.dayShift));
				}
				if (this.hourShift != 0) {
					text += " %s%dh".formatted(this.hourShift > 0 ? "+" : "-", Math.abs(this.hourShift));
				}
				return text;
			}

			public Instant getStart() {
				return LocalDateTime.now().plusDays(this.dayShift).plusHours(this.hourShift).atZone(ZoneId.systemDefault()).toInstant();
			}

			public Instant getEnd() {
				return LocalDateTime.now().plusDays(this.dayShift).plusHours(this.hourShift).atZone(ZoneId.systemDefault()).toInstant();
			}
		}

		public static class RelativeDateToday extends RelativeDate {
			private static final Pattern TODAY_PATTERN = Pattern.compile("(?i)^Today(\\s*((?<days>\\+|\\-\\s*[0-9]+)\\s*D))?(\\s*((?<hours>\\+|\\-\\s*[0-9]+)\\s*H))?$");

			private long dayShift;
			private long hourShift;

			public RelativeDateToday(String text) throws ParseException {
				Matcher matcher = TODAY_PATTERN.matcher(text);
				if (!matcher.matches()) {
					throw new ParseException("Cannot parse Today-based relative time", 0);
				}
				if (matcher.group("days") != null) {
					this.dayShift = Long.parseLong(matcher.group("days").replace(" ", ""));
				}
				if (matcher.group("hours") != null) {
					this.hourShift = Long.parseLong(matcher.group("hours").replace(" ", ""));
				}
			}

			public String getText() {
				String text = "Today";
				if (this.dayShift != 0) {
					text += " %s%dd".formatted(this.dayShift > 0 ? "+" : "-", Math.abs(this.dayShift));
				}
				if (this.hourShift != 0) {
					text += " %s%dh".formatted(this.hourShift > 0 ? "+" : "-", Math.abs(this.hourShift));
				}
				return text;
			}

			public Instant getStart() {
				return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).plusDays(this.dayShift).plusHours(this.hourShift).toInstant();
			}

			public Instant getEnd() {
				return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).plusDays(1 + this.dayShift).plusHours(this.hourShift).toInstant();
			}
		}

		/** The value. */
		private TypedDate value;

		/**
		 * Instantiates a new date config item expression.
		 *
		 * @param attribute the attribute
		 */
		public DateAttributeExpression(AttributeDefinition attribute) {
			super(attribute);
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.AttributeExpression#getTextValue()
		 */
		protected String getTextValue() {
			return "\"%s\"".formatted(this.value.getText());
		}

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			AttributeDefinition attribute = null;

			if (tokens.get(0).key1 == null) {
				if ("Creation Date".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.DATE, 
						AttributeLevel.DEVICE,
						"createdDate",
						"Creation Date");
				}
				else if ("Last Change Date".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.DATE, 
						AttributeLevel.DEVICE,
						"changeDate",
						"Last Change Date");
				}
				else if ("End of Sale Date".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.DATE, 
						AttributeLevel.DEVICE,
						"eosDate",
						"End of Sale Date");
				}
				else if ("End of Life Date".equalsIgnoreCase(tokens.get(0).key2)) {
					attribute = new AttributeDefinition(
						null,
						AttributeType.DATE, 
						AttributeLevel.DEVICE,
						"eolDate",
						"End of Life Date");
				}
			}
			else {
				attribute = AttributeExpression.getDriverAttributeDefinition(
					tokens.get(0), AttributeType.DATE);
			}

			if (attribute == null) {
				return null;
			}
			if (tokens.size() != 3) {
				throw new FinderParseException("Incomplete or incorrect expression after date item at character %d.".formatted(tokens.get(0).position));
			}
			Token sign = tokens.get(1);
			Token value = tokens.get(2);

			if (sign.type != TokenType.BEFORE && sign.type != TokenType.AFTER
				&& sign.type != TokenType.IS) {

				throw new FinderParseException("Parsing error, invalid date operator at position %d.".formatted(sign.position));

			}
			if (value.type != TokenType.QUOTEDTEXT) {
				throw new FinderParseException("Parsing error, should be a quoted date at character %d.".formatted(value.position));
			}
			DateAttributeExpression dateExpr = new DateAttributeExpression(attribute);
			dateExpr.sign = sign.type;
			try {
				dateExpr.value = TypedDate.parse(value.value);
			}
			catch (ParseException e2) {
				throw new FinderParseException("Invalid date/time at position %d.".formatted(value.position));
			}
			return dateExpr;
		}
	
		@Override
		protected Condition buildCondition(Table<?> table) {
			Field<Instant> property = this.valueField(table, "when", Instant.class);
			switch (sign) {
				case AFTER:
					return property.ge(this.value.getStart());
				case BEFORE:
					return property.le(this.value.getEnd());
				default:
					return property.ge(this.value.getStart()).and(property.le(this.value.getEnd()));
			}
		}
}

	/**
	 * The Class BinaryAttributeExpression.
	 */
	public static class BinaryAttributeExpression extends AttributeExpression {

		private boolean value;

		/**
		 * Instantiates a new binary config item expression.
		 *
		 * @param attribute the attribute
		 */
		public BinaryAttributeExpression(AttributeDefinition attribute) {
			super(attribute);
			this.sign = TokenType.IS;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.AttributeExpression#getTextValue()
		 */
		protected String getTextValue() {
			return value ? "true" : "false";
		}

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			AttributeDefinition attribute = null;

			if (tokens.get(0).key1 != null) {
				attribute = AttributeExpression.getDriverAttributeDefinition(
					tokens.get(0), AttributeType.BINARY);
			}
			if (attribute == null) {
				return null;
			}
			if (tokens.size() != 3) {
				throw new FinderParseException("Incomplete or incorrect expression after enum item at character %d.".formatted(tokens.get(0).position));
			}
			Token sign = tokens.get(1);
			Token value = tokens.get(2);
			if (sign.type != TokenType.IS) {
				throw new FinderParseException("Parsing error, invalid operator at position %d, should be 'is'.".formatted(sign.position));
			}
			BinaryAttributeExpression binExpr =
				new BinaryAttributeExpression(attribute);
			if (value.type == TokenType.TRUE) {
				binExpr.value = true;
			}
			else if (value.type == TokenType.FALSE) {
				binExpr.value = false;
			}
			else {
				throw new FinderParseException("Parsing error, invalid value at position %d, should be '%s' or '%s'."
					.formatted(value.position, TokenType.TRUE, TokenType.FALSE));
			}
			return binExpr;
		}
	
		@Override
		protected Condition buildCondition(Table<?> table) {
			return this.valueField(table, "assumption", Boolean.class).eq(this.value);
		}
}

	/**
	 * The Class VrfExpression.
	 */
	public static class VrfExpression extends Expression {

		/** The sign. */
		public TokenType sign;

		/** The value. */
		private String value;

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.size() == 3 && tokens.get(0).type == TokenType.VRF) {
				Token comparator = tokens.get(1);
				Token value = tokens.get(2);
				switch (comparator.type) {
					case IS:
					case CONTAINS:
					case CONTAINSNOCASE:
					case STARTSWITH:
					case ENDSWITH:
					case MATCHES:
						if (value.type == TokenType.QUOTEDTEXT) {
							if (comparator.type == TokenType.MATCHES) {
								checkRegExp(value);
							}
							VrfExpression modExpr = new VrfExpression();
							modExpr.sign = comparator.type;
							modExpr.value = value.value;
							return modExpr;
						}
						else {
							throw new FinderParseException("Expecting a quoted string for VRF at character %d.".formatted(value.position));
						}
					default:
						throw new FinderParseException("Invalid operator after VRF at character %d.".formatted(comparator.position));
				}
			}
			return null;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			return "[%s] %s \"%s\"".formatted(TokenType.VRF, sign,
				TokenType.escapeValue(value));
		}

	
		@Override
		public Condition toCondition(String itemPrefix) {
			var d = subDevice(itemPrefix);
			var v = DEVICE_VRF_INSTANCES.as(itemPrefix + "_v");
			return DEVICE.ID.in(DSL.select(d.ID).from(d).leftJoin(v).on(v.DEVICE.eq(d.ID))
				.where(textCondition(sign, v.VRF_INSTANCES, value)));
		}
}

	/**
	 * The Class VirtualNameExpression.
	 */
	public static class VirtualNameExpression extends Expression {

		/** The sign. */
		public TokenType sign;

		/** The value. */
		private String value;

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.size() == 3 && tokens.get(0).type == TokenType.VIRTUALNAME) {
				Token comparator = tokens.get(1);
				Token value = tokens.get(2);
				switch (comparator.type) {
					case IS:
					case CONTAINS:
					case CONTAINSNOCASE:
					case STARTSWITH:
					case ENDSWITH:
					case MATCHES:
						if (value.type == TokenType.QUOTEDTEXT) {
							if (comparator.type == TokenType.MATCHES) {
								checkRegExp(value);
							}
							VirtualNameExpression vnameExpr = new VirtualNameExpression();
							vnameExpr.sign = comparator.type;
							vnameExpr.value = value.value;
							return vnameExpr;
						}
						else {
							throw new FinderParseException("Expecting a quoted string for VirtualName at character %d.".formatted(value.position));
						}
					default:
						throw new FinderParseException("Invalid operator after VirtualName at character %d.".formatted(comparator.position));
				}
			}
			return null;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			return "[%s] %s \"%s\"".formatted(TokenType.VIRTUALNAME, sign,
				TokenType.escapeValue(value));
		}

	
		@Override
		public Condition toCondition(String itemPrefix) {
			var d = subDevice(itemPrefix);
			var v = DEVICE_VIRTUAL_DEVICES.as(itemPrefix + "_v");
			return DEVICE.ID.in(DSL.select(d.ID).from(d).leftJoin(v).on(v.DEVICE.eq(d.ID))
				.where(textCondition(sign, v.VIRTUAL_DEVICES, value).or(textCondition(sign, d.NAME, value))));
		}
}

	/**
	 * The Class TagExpression, to find devices by (user-defined) tag.
	 */
	public static class TagExpression extends Expression {

		/** The sign. */
		public TokenType sign;

		/** The value. */
		private String value;

		/**
		 * Parses the tokens to create an expression.
		 *
		 * @param tokens the tokens
		 * @return the expression
		 * @throws FinderParseException the finder parse exception
		 */
		public static Expression parse(List<Token> tokens) throws FinderParseException {
			if (tokens.size() == 3 && tokens.get(0).type == TokenType.TAG) {
				Token comparator = tokens.get(1);
				Token value = tokens.get(2);
				switch (comparator.type) {
					case IS:
					case CONTAINS:
					case CONTAINSNOCASE:
					case STARTSWITH:
					case ENDSWITH:
					case MATCHES:
						if (value.type == TokenType.QUOTEDTEXT) {
							if (comparator.type == TokenType.MATCHES) {
								checkRegExp(value);
							}
							TagExpression tagExpr = new TagExpression();
							tagExpr.sign = comparator.type;
							tagExpr.value = value.value;
							return tagExpr;
						}
						else {
							throw new FinderParseException("Expecting a quoted string for Tag at character %d.".formatted(value.position));
						}
					default:
						throw new FinderParseException("Invalid operator after Tag at character %d.".formatted(comparator.position));
				}
			}
			return null;
		}

		/*(non-Javadoc)
		 * @see net.netshot.netshot.inventory.search.Finder.Expression#toString()
		 */
		@Override
		public String toString() {
			return "[%s] %s \"%s\"".formatted(TokenType.TAG, sign,
				TokenType.escapeValue(value));
		}

	
		@Override
		public Condition toCondition(String itemPrefix) {
			var d = subDevice(itemPrefix);
			var t = DEVICE_TAGS.as(itemPrefix + "_t");
			return DEVICE.ID.in(DSL.select(d.ID).from(d).join(t).on(t.DEVICE.eq(d.ID))
				.where(textCondition(sign, t.TAG, value)));
		}
}

	/** The prefix of the aliases of the subqueries. */
	private static final String ALIAS_PREFIX = "var";

	/** The tokens. */
	private List<Token> tokens;

	/** The expression. */
	private Expression expression;

	/**
	 * Instantiates a new finder.
	 *
	 * @param query the query
	 * @throws FinderParseException the finder parse exception
	 */
	public Finder(String query)
		throws FinderParseException {
		this.tokens = Expression.tokenize(query);
		if (this.tokens.size() == 0) {
			this.expression = new NullExpression();
		}
		else {
			this.expression = Expression.parse(tokens);
		}
	}

	/**
	 * Gets the formatted query.
	 *
	 * @return the formatted query
	 */
	public String getFormattedQuery() {
		return expression.toString();
	}

	/**
	 * Gets the condition on the devices ({@code DEVICE} table) matching the query.
	 *
	 * @return the condition
	 */
	public Condition getCondition() {
		return this.expression.toCondition(ALIAS_PREFIX);
	}

}
