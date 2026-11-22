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
package net.netshot.netshot.platform.script;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.testing.ScriptEngineTest;

@ScriptEngineTest
@DisplayName("Conversions of the JavaScript values, regular expressions and errors")
class JsUtilsTest {

	private static Context context;

	@BeforeAll
	static void createContext() {
		context = Context.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();
	}

	@AfterAll
	static void closeContext() {
		context.close();
	}

	private static Object convert(String js) {
		return JsUtils.valueToJavaObject(context.eval("js", "(" + js + ")"));
	}

	@Test
	@DisplayName("A JavaScript value becomes plain Java objects: integers as longs, other numbers as doubles, arrays as lists, objects as maps")
	void values() {
		Assertions.assertNull(JsUtils.valueToJavaObject(null));
		Assertions.assertNull(convert("null"));
		Assertions.assertNull(convert("undefined"));
		Assertions.assertEquals("text", convert("'text'"));
		Assertions.assertEquals(Boolean.TRUE, convert("true"));
		Assertions.assertEquals(42L, convert("42"));
		Assertions.assertEquals(1.5, convert("1.5"));
		Assertions.assertEquals(List.of(1L, "two", List.of(true)), convert("[1, 'two', [true]]"));
		Object map = convert("{ b: 1, a: { list: [null] } }");
		Assertions.assertEquals(Map.of("b", 1L, "a", Map.of("list", java.util.Arrays.asList((Object) null))), map);
		Assertions.assertEquals(List.of("b", "a"), List.copyOf(((Map<?, ?>) map).keySet()), "The order of the keys is kept");
	}

	@Test
	@DisplayName("A JavaScript RegExp becomes a Pattern with the matching flags; JS-only flags are dropped")
	void regExps() {
		Pattern pattern = JsUtils.jsRegExpToPattern(context.eval("js", "/^a.b$/imsgu"));
		Assertions.assertEquals("^a.b$", pattern.pattern());
		Assertions.assertEquals(Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL | Pattern.UNICODE_CASE, pattern.flags());
		Assertions.assertTrue(pattern.matcher("x\nA\nB").find(), "Multiline, case insensitive, dot matches the new line");
		Assertions.assertEquals(0, JsUtils.jsRegExpToPattern(context.eval("js", "/plain/")).flags());

		Assertions.assertThrows(IllegalArgumentException.class, () -> JsUtils.jsRegExpToPattern(context.eval("js", "'not a RegExp'")));
		// Valid in JavaScript (a literal '{,3}'), not in Java
		Assertions.assertThrows(RuntimeException.class, () -> JsUtils.jsRegExpToPattern(context.eval("js", "/x{,3}/")));
	}

	@Test
	@DisplayName("A script error is reported with its source location (also for a thrown Error object), also when wrapped")
	void errors() {
		PolyglotException error = Assertions.assertThrows(PolyglotException.class,
			() -> context.eval(Source.newBuilder("js", "function fail() {\n  throw new Error('boom');\n}\nfail();", "lab.js").buildLiteral()));
		String message = JsUtils.jsErrorToMessage(error);
		Assertions.assertTrue(message.matches("Error: boom at lab\\.js:2:\\d+"), message);
		Assertions.assertEquals(message, JsUtils.jsErrorToMessage(new IllegalStateException("wrapper", error)));
		Assertions.assertEquals("plain", JsUtils.jsErrorToMessage(new IllegalStateException("plain")));

		PolyglotException thrownString = Assertions.assertThrows(PolyglotException.class,
			() -> context.eval(Source.newBuilder("js", "\n\nthrow 'text';", "other.js").buildLiteral()));
		Assertions.assertEquals("text at other.js:3:1", JsUtils.jsErrorToMessage(thrownString));
	}
}
