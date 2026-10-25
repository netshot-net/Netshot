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
package net.netshot.netshot.access;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.EnumSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.fake.FakeTaskContext;

@UnitTest
public class CliTest {

	/**
	 * CLI class to mock a connection to a device.
	 */
	private static class FakeCli extends Cli {

		FakeCli(TaskContext taskContext) {
			super(null, taskContext);
		}

		@Override
		public void connect() throws IOException {
			// Nothing
		}

		@Override
		public void disconnect() {
			// Nothing
		}
	}

	private TaskContext taskContext = new FakeTaskContext();

	private Cli cli = new FakeCli(taskContext);

	// Helper to access the protected cleanUpOutput method
	private String cleanUpOutput(String input) throws Exception {
		// cleanUpCommandOutput(StringBuilder buffer, EnumSet<CleanUpAction> cleanUpActions)
		Method method = Cli.class.getDeclaredMethod("cleanUpCommandOutput", StringBuilder.class, EnumSet.class);
		method.setAccessible(true);
		StringBuilder buffer = new StringBuilder(input);
		method.invoke(cli, buffer, null);
		return buffer.toString();
	}

	@Test
	@DisplayName("Remove basic ANSI color codes")
	void testBasicAnsiColorCodes() throws Exception {
		// Test simple color code: bold red text
		String input = "\u001b[1;31mHello World\u001b[0m";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Hello World", result, "Basic ANSI color codes should be removed");
	}

	@Test
	@DisplayName("Remove multiple ANSI color codes")
	void testMultipleAnsiColorCodes() throws Exception {
		String input = "\u001b[1;31mRed\u001b[0m \u001b[1;32mGreen\u001b[0m \u001b[1;34mBlue\u001b[0m";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Red Green Blue", result, "Multiple ANSI color codes should be removed");
	}

	@Test
	@DisplayName("Remove CSI cursor movement codes")
	void testCsiCursorMovement() throws Exception {
		String input = "\u001b[2JCleared\u001b[H";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Cleared", result, "CSI cursor movement codes should be removed");
	}

	@Test
	@DisplayName("Remove OSC sequences with BEL terminator")
	void testOscSequenceBel() throws Exception {
		String input = "\u001b]0;Window Title\u0007Content";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Content", result, "OSC sequences with BEL terminator should be removed");
	}

	@Test
	@DisplayName("Remove OSC sequences with ESC backslash terminator")
	void testOscSequenceEscBackslash() throws Exception {
		String input = "\u001b]0;Window Title\u001b\\Content";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Content", result, "OSC sequences with ESC backslash terminator should be removed");
	}

	@Test
	@DisplayName("Remove character set selection codes")
	void testCharacterSetSelection() throws Exception {
		String input = "\u001b(0Line\u001b)BText";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("LineText", result, "Character set selection codes should be removed");
	}

	@Test
	@DisplayName("Remove keypad mode codes")
	void testKeypadModes() throws Exception {
		String input = "\u001b=Numeric\u001b>Application";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("NumericApplication", result, "Keypad mode codes should be removed");
	}

	@Test
	@DisplayName("Handle plain text without ANSI codes")
	void testPlainText() throws Exception {
		String input = "Plain text without ANSI codes";
		String result = cleanUpOutput(input);
		Assertions.assertEquals(input, result, "Plain text should be returned unchanged");
	}

	@Test
	@DisplayName("Handle empty and null strings")
	void testEmptyAndNull() throws Exception {
		String emptyResult = cleanUpOutput("");
		Assertions.assertEquals("", emptyResult, "Empty string should return empty");
	}

	@Test
	@DisplayName("Remove control characters except newline and tab")
	void testControlCharacters() throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append('A');
		sb.append((char) 0x01);
		sb.append('B');
		String input1 = sb.toString();
		String result1 = cleanUpOutput(input1);

		Assertions.assertEquals(2, result1.length(), "Result should have 2 characters");
		Assertions.assertEquals('A', result1.charAt(0));
		Assertions.assertEquals('B', result1.charAt(1));

		String inputTabNewline = "Hello\tWorld\nNext";
		Assertions.assertEquals("Hello\tWorld\nNext", cleanUpOutput(inputTabNewline),
			"Tab and newline should be preserved");

		String inputWithDel = "Text\u007FEnd";
		Assertions.assertEquals("TextEnd", cleanUpOutput(inputWithDel),
			"DEL character (0x7F) should be removed");
	}

	@Test
	@DisplayName("Normalize Windows line endings (CRLF to LF)")
	void testWindowsLineEndings() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.NORMALIZE_LINE_ENDINGS, true);
		String input = "Line1\r\nLine2\r\nLine3";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Line1\nLine2\nLine3", result,
			"Windows line endings (CRLF) should be normalized to LF");
	}

	@Test
	@DisplayName("Don't remove standalone carriage return")
	void testStandaloneCarriageReturn() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_CARRIAGE_RETURNS, false);
		cli.setOuputCleanUpAction(Cli.CleanUpAction.NORMALIZE_LINE_ENDINGS, true);
		String input = "Before\rAfter";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Before\rAfter", result, "Standalone carriage return should not be removed");
	}

	@Test
	@DisplayName("Handle ESC at end of string")
	void testEscAtEnd() throws Exception {
		String input = "Text\u001b";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Text", result, "ESC at end of string should be removed");
	}

	@Test
	@DisplayName("Handle incomplete ANSI sequences")
	void testIncompleteAnsiSequence() throws Exception {
		String input = "\u001b[1;31Text";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("ext", result, "Incomplete ANSI sequence consumes chars until letter found");
	}

	@Test
	@DisplayName("Complex real-world example with mixed codes")
	void testComplexRealWorld() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.NORMALIZE_LINE_ENDINGS, true);
		String input = "\u001b[1;31mError:\u001b[0m Connection failed\r\n"
			+ "\u001b[2JScreen cleared\u001b[H\u001b[1;32mSuccess\u001b[0m";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Error: Connection failed\nScreen clearedSuccess", result,
			"Complex real-world ANSI codes should be handled correctly");
	}

	@Test
	@DisplayName("CSI sequences with @ terminator (ECMA-48)")
	void testCsiWithAtTerminator() throws Exception {
		String input = "\u001b[@Text";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Text", result, "CSI sequences with @ terminator should be removed");
	}

	@Test
	@DisplayName("Preserve newlines and tabs in output")
	void testPreserveNewlinesAndTabs() throws Exception {
		String input = "\u001b[31mRed\u001b[0m\n\tIndented\u001b[32mGreen\u001b[0m";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Red\n\tIndentedGreen", result,
			"Newlines and tabs should be preserved in output");
	}

	@Test
	@DisplayName("Test ANSI codes from Cisco devices")
	void testCiscoAnsiCodes() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.NORMALIZE_LINE_ENDINGS, true);
		String input = "router1#\u001b[Kshow version\r\nCisco IOS Software";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("router1#show version\nCisco IOS Software", result,
			"Cisco ANSI codes should be properly removed");
	}

	@Test
	@DisplayName("Test ANSI codes with SGR parameters")
	void testSgrParameters() throws Exception {
		String input = "\u001b[0mNormal \u001b[1mBold \u001b[4mUnderline \u001b[7mReverse \u001b[0mReset";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Normal Bold Underline Reverse Reset", result,
			"SGR parameters should be properly removed");
	}

	@Test
	@DisplayName("Test 256-color ANSI codes")
	void test256ColorCodes() throws Exception {
		String input = "\u001b[38;5;196mRed256\u001b[0m \u001b[48;5;21mBlue256BG\u001b[0m";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Red256 Blue256BG", result,
			"256-color ANSI codes should be properly removed");
	}

	@Test
	@DisplayName("Test RGB ANSI codes")
	void testRgbColorCodes() throws Exception {
		String input = "\u001b[38;2;255;0;0mRGB Red\u001b[0m";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("RGB Red", result,
			"RGB ANSI codes should be properly removed");
	}

	@Test
	@DisplayName("Performance test with large string")
	void testPerformanceWithLargeString() throws Exception {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 1000; i++) {
			builder.append("\u001b[1;3").append(i % 8).append("mLine ").append(i).append("\u001b[0m\n");
		}
		String input = builder.toString();

		long startTime = System.currentTimeMillis();
		String result = cleanUpOutput(input);
		long endTime = System.currentTimeMillis();

		Assertions.assertFalse(result.contains("\u001b"),
			"Result should not contain any ESC characters");

		long duration = endTime - startTime;
		Assertions.assertTrue(duration < 100,
			"Processing should complete in less than 100ms, took " + duration + "ms");
	}

	@Test
	@DisplayName("Test stripAnsiCodes configuration - disabled")
	void testStripAnsiCodesDisabled() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.STRIP_ANSI_CODES, false);

		String input = "\u001b[1;31mRed Text\u001b[0m";
		String result = cleanUpOutput(input);
		Assertions.assertEquals(input, result, "ANSI codes should be preserved when stripAnsiCodes is false");
	}

	@Test
	@DisplayName("Test normalizeCarriageReturns configuration - disabled")
	void testNormalizeCarriageReturnsDisabled() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_CARRIAGE_RETURNS, false);

		String input = "Line1\r\nLine2\rLine3";
		String result = cleanUpOutput(input);
		Assertions.assertEquals(input, result, "Carriage returns should be preserved when normalizeCarriageReturns is false");
	}

	@Test
	@DisplayName("Test combined configuration - both disabled")
	void testBothConfigurationsDisabled() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.STRIP_ANSI_CODES, false);
		cli.setOuputCleanUpAction(Cli.CleanUpAction.NORMALIZE_LINE_ENDINGS, false);

		String input = "\u001b[31mRed\u001b[0m\r\nText";
		String result = cleanUpOutput(input);
		Assertions.assertEquals(input, result, "Output should be unchanged when both options are disabled");
	}

	@Test
	@DisplayName("Test stripAnsiCodes enabled, normalizeCarriageReturns disabled")
	void testStripAnsiOnly() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.STRIP_ANSI_CODES, true);
		cli.setOuputCleanUpAction(Cli.CleanUpAction.NORMALIZE_LINE_ENDINGS, false);
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_CARRIAGE_RETURNS, false);

		String input = "\u001b[31mRed\u001b[0m\r\nText\rMore";
		String result = cleanUpOutput(input);
		Assertions.assertEquals("Red\r\nText\rMore", result,
			"ANSI codes should be removed but carriage returns preserved");
	}

	@Test
	@DisplayName("Test processBackspaces with single backspace")
	void testProcessBackspacesSingle() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_BACKSPACES, true);

		String input = "hello\b";
		String result = cleanUpOutput(input);

		Assertions.assertEquals("hell", result,
			"Single backspace should delete the previous character");
	}

	@Test
	@DisplayName("Test processBackspaces with consecutive backspaces")
	void testProcessBackspacesConsecutive() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_BACKSPACES, true);

		// This should delete 'c' then 'b' then 'a'
		String input = "abc\b\b\b";
		String result = cleanUpOutput(input);

		Assertions.assertEquals("", result,
			"Consecutive backspaces should delete multiple previous characters");
	}

	@Test
	@DisplayName("Test processBackspaces with multiple sequences")
	void testProcessBackspacesMultipleSequences() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_BACKSPACES, true);

		String input = "test\bing message\b";
		String result = cleanUpOutput(input);

		Assertions.assertEquals("tesing messag", result,
			"Multiple backspace sequences should each delete their previous character");
	}

	@Test
	@DisplayName("Test processBackspaces with typo correction simulation")
	void testProcessBackspacesTypoCorrection() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_BACKSPACES, true);

		// Simulate typing "teh" then backspacing to correct to "the"
		String input = "teh\b\bhe";
		String result = cleanUpOutput(input);

		Assertions.assertEquals("the", result,
			"Backspaces should simulate correcting a typo");
	}

	@Test
	@DisplayName("Test processBackspaces disabled")
	void testProcessBackspacesDisabled() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_BACKSPACES, false);
		cli.setOuputCleanUpAction(Cli.CleanUpAction.NORMALIZE_LINE_ENDINGS, false);

		String input = "hello\b";
		String result = cleanUpOutput(input);

		Assertions.assertEquals(input, result,
			"Backspaces should be preserved when processBackspaces is disabled");
	}

	@Test
	@DisplayName("Test processBackspaces with no backspaces")
	void testProcessBackspacesNone() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_BACKSPACES, true);

		String input = "normal text";
		String result = cleanUpOutput(input);

		Assertions.assertEquals(input, result,
			"Text without backspaces should be unchanged");
	}

	@Test
	@DisplayName("Test processBackspaces at beginning of string")
	void testProcessBackspacesAtBeginning() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_BACKSPACES, true);

		// Backspace at the beginning has nothing to delete, should be removed
		String input = "\bhello";
		String result = cleanUpOutput(input);

		// The \b pattern is [^\b][\b], so lone \b at start won't match
		// It should remain as-is (or be removed by ANSI cleanup if that's enabled)
		// Actually, \b is not removed by our pattern, so it stays
		Assertions.assertEquals("\bhello", result,
			"Backspace at beginning should remain (no previous char to delete)");
	}

	@Test
	@DisplayName("Test removeBeforeCarriageReturn for pager sequences")
	void testRemoveBeforeCarriageReturn() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_CARRIAGE_RETURNS, true);

		// Simulate pager: text + pager prompt + \r + spaces (same length as prompt) + \r + continuation
		// Prompt is 33 chars, spaces should be 33 to fully overwrite it
		String input = "some text-Press Any Key For More-\r                                 \rmore text";

		// First verify the input has \r characters
		long crCount = input.chars().filter(ch -> ch == '\r').count();
		Assertions.assertEquals(2, crCount, "Input should contain exactly 2 \\r characters");

		String result = cleanUpOutput(input);

		// Check that \r characters are removed from result
		long resultCrCount = result.chars().filter(ch -> ch == '\r').count();
		Assertions.assertEquals(0, resultCrCount, "Result should contain no \\r characters");

		// The \r before spaces overwrites the prompt with spaces (33 chars)
		// The \r after spaces overwrites the spaces with "more text" (9 chars), leaving 24 trailing spaces
		// Terminal behavior: "more text" + 24 spaces
		Assertions.assertEquals("more text                        ", result,
			"Pager sequence should overwrite prompt with spaces, then text");
	}

	@Test
	@DisplayName("Test removeBeforeCarriageReturn with newlines")
	void testRemoveBeforeCarriageReturnWithNewlines() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_CARRIAGE_RETURNS, true);

		// Each line should be processed independently
		// "line1 old" (9 chars) → \r → "new" (3 chars) overwrites first 3 → "newe1 old"
		String input = "line1 old\rnew\nline2 old\rnew";
		String result = cleanUpOutput(input);

		Assertions.assertEquals("newe1 old\nnewe2 old", result,
			"Each line should have content overwritten with tail preserved");
	}

	@Test
	@DisplayName("Test removeBeforeCarriageReturn disabled")
	void testRemoveBeforeCarriageReturnDisabled() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_CARRIAGE_RETURNS, false);
		cli.setOuputCleanUpAction(Cli.CleanUpAction.NORMALIZE_LINE_ENDINGS, false);

		String input = "old\rnew";
		String result = cleanUpOutput(input);

		Assertions.assertEquals(input, result,
			"Content should be preserved when removeBeforeCarriageReturn is disabled");
	}

	@Test
	@DisplayName("Test processCarriageReturns with multiple \\r on same line")
	void testProcessCarriageReturnsMultiple() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_CARRIAGE_RETURNS, true);
		cli.setOuputCleanUpAction(Cli.CleanUpAction.NORMALIZE_LINE_ENDINGS, true);

		// Multiple \r with overwrites: "first" → "second" → "thirdd" (last 'd' from "second" remains)
		String input = "first\rsecond\rthird";
		String result = cleanUpOutput(input);

		Assertions.assertEquals("thirdd", result,
			"Characters should overwrite position by position, preserving tail");
	}

	@Test
	@DisplayName("Test processCarriageReturns with \\r at end of line")
	void testProcessCarriageReturnsAtEnd() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_CARRIAGE_RETURNS, true);
		cli.setOuputCleanUpAction(Cli.CleanUpAction.NORMALIZE_LINE_ENDINGS, true);

		// \r at end of line (no content after) - should not remove anything
		String input = "some text\r";
		String result = cleanUpOutput(input);

		Assertions.assertEquals("some text\r", result,
			"\\r at end without content after should not trigger removal");
	}

	@Test
	@DisplayName("Test processCarriageReturns with consecutive \\r characters")
	void testProcessCarriageReturnsConsecutive() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_CARRIAGE_RETURNS, true);
		cli.setOuputCleanUpAction(Cli.CleanUpAction.NORMALIZE_LINE_ENDINGS, true);

		// Multiple consecutive \r: "before" → empty → empty → "aftere" (last 'e' from "before" remains)
		String input = "before\r\r\rafter";
		String result = cleanUpOutput(input);

		Assertions.assertEquals("aftere", result,
			"Consecutive \\r with content should preserve tail from previous content");
	}

	@Test
	@DisplayName("Test processCarriageReturns with empty result")
	void testProcessCarriageReturnsEmpty() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_CARRIAGE_RETURNS, true);

		// Only \r characters, no content after
		String input = "text\r\r\r";
		String result = cleanUpOutput(input);

		Assertions.assertEquals("text", result,
			"Only \\r at end should leave the text before first \\r");
	}

	@Test
	@DisplayName("Test processCarriageReturns with mixed content")
	void testProcessCarriageReturnsMixed() throws Exception {
		cli.setOuputCleanUpAction(Cli.CleanUpAction.PROCESS_CARRIAGE_RETURNS, true);
		cli.setOuputCleanUpAction(Cli.CleanUpAction.NORMALIZE_LINE_ENDINGS, true);

		// Complex case: multiple lines with various \r patterns
		// Line 1: "line1" (5 chars) → \r → "new1" (4 chars) = "new11"
		// Line 2: "line2 old" (9 chars) → \r\r\r → "new2" (4 chars) = "new22 old"
		// Line 3: "line3" with \r at end (no content after) → "line3"
		String input = "line1\rnew1\nline2 old\r\r\rnew2\nline3\r";
		String result = cleanUpOutput(input);

		Assertions.assertEquals("new11\nnew22 old\nline3", result,
			"Mixed patterns should be handled correctly across lines");
	}
}
