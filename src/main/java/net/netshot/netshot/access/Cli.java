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
import java.io.InputStream;
import java.io.PrintStream;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.task.engine.TaskContext;

/**
 * A CLI object to access a device through command line.
 * Abstract - real implementations are Telnet, SSH.
 */
@Slf4j
public abstract class Cli implements Client {

	/** Possible clean up actions to enable on the Cli instance. */
	public enum CleanUpAction {
		/** Remove ANSI codes like colors. */
		STRIP_ANSI_CODES,
		/** Replace \r\n with \n. */
		NORMALIZE_LINE_ENDINGS,
		/** \r followed by characters rewrites the line. */
		PROCESS_CARRIAGE_RETURNS,
		/** Backspace \b erases previous character. */
		PROCESS_BACKSPACES,
	}

	/**
	 * An IOException, with an attached buffer.
	 */
	public class WithBufferIOException extends IOException {
		private static final long serialVersionUID = -1759143581862318498L;

		private String receivedBuffer;

		public WithBufferIOException(String message, CharSequence buffer) {
			super(message);
			this.receivedBuffer = buffer.toString();
		}

		public String getReceivedBuffer() {
			return receivedBuffer;
		}

	}

	/**
	 * Input for a CLI command.
	 */
	public static class CommandInput {
		/** The command to send (null to just wait for output). */
		@Getter
		@Setter
		private String command;

		/** The list of patterns to expect. */
		@Getter
		@Setter
		private String[] expects;

		/** The command timeout (null to use CLI default). */
		@Getter
		@Setter
		private Integer commandTimeout;

		/** The output cleanup actions (null to use CLI default). */
		@Getter
		@Setter
		private EnumSet<CleanUpAction> cleanUpActions;

		/** The minimum time to wait after expect match to discover additional output (null for no wait). */
		@Getter
		@Setter
		private Integer discoverWaitTime;

		/**
		 * Creates a new CommandInput with all parameters.
		 *
		 * @param command the command to send
		 * @param expects the patterns to expect
		 * @param commandTimeout the command timeout (null for default)
		 * @param cleanUpActions the cleanup actions (null for default)
		 * @param discoverWaitTime minimum time to wait after match to discover more output (null for no wait)
		 */
		public CommandInput(String command, String[] expects, Integer commandTimeout,
				EnumSet<CleanUpAction> cleanUpActions, Integer discoverWaitTime) {
			this.command = command;
			this.expects = expects;
			this.commandTimeout = commandTimeout;
			this.cleanUpActions = cleanUpActions;
			this.discoverWaitTime = discoverWaitTime;
		}

		/**
		 * Creates a new CommandInput with command and expects only (using defaults).
		 *
		 * @param command the command to send
		 * @param expects the patterns to expect
		 */
		public CommandInput(String command, String[] expects) {
			this(command, expects, null, null, null);
		}

		/**
		 * Creates a new CommandInput with command and single expect pattern.
		 *
		 * @param command the command to send
		 * @param expect the pattern to expect
		 */
		public CommandInput(String command, String expect) {
			this(command, new String[] { expect }, null, null, null);
		}
	}

	/**
	 * The output of a CLI command or read operation.
	 */
	public static class CommandOutput {
		/** The command that was sent. */
		@Getter
		private final String command;

		/** The cleaned output string (may have prompt stripped based on settings). */
		@Getter
		private final String output;

		/** The full cleaned output (always includes the prompt if it was matched). */
		@Getter
		private final String fullOutput;

		/** The raw uncleaned buffer as received from the device. */
		@Getter
		private final String rawBuffer;

		/** The matcher that found the expected pattern. */
		@Getter
		private final Matcher expectMatch;

		/** The index of the matched pattern in the expects array. */
		@Getter
		private final int expectMatchIndex;

		/** The pattern string that was matched. */
		@Getter
		private final String expectMatchPattern;

		/**
		 * Creates a new CommandOutput object.
		 *
		 * @param command the command that was sent
		 * @param output the cleaned output (possibly with prompt removed)
		 * @param fullOutput the full cleaned output (with prompt)
		 * @param rawBuffer the raw uncleaned buffer
		 * @param expectMatch the matcher that found the expected pattern
		 * @param expectMatchIndex the index of the matched pattern
		 * @param expectMatchPattern the pattern string that was matched
		 */
		public CommandOutput(String command, String output, String fullOutput, String rawBuffer, Matcher expectMatch,
				int expectMatchIndex, String expectMatchPattern) {
			this.command = command;
			this.output = output;
			this.fullOutput = fullOutput;
			this.rawBuffer = rawBuffer;
			this.expectMatch = expectMatch;
			this.expectMatchIndex = expectMatchIndex;
			this.expectMatchPattern = expectMatchPattern;
		}
	}

	/** The connection timeout. */
	@Getter
	@Setter
	protected int connectionTimeout = 5000;

	/** The receive timeout. */
	@Getter
	@Setter
	protected int receiveTimeout = 60000;

	/** The command timeout. */
	@Getter
	@Setter
	protected int commandTimeout = 120000;

	/** Clean up actions on CLI output. */
	@Getter
	@Setter
	protected EnumSet<CleanUpAction> cleanUpActions = EnumSet.of(
		CleanUpAction.STRIP_ANSI_CODES,
		CleanUpAction.PROCESS_BACKSPACES,
		CleanUpAction.PROCESS_CARRIAGE_RETURNS
	);

	/** The current task context. */
	protected TaskContext taskContext;

	/** The in stream. */
	protected InputStream inStream;

	/** The out stream. */
	protected PrintStream outStream;

	/** The prompt. */
	protected String prompt;

	/** The host (IPv4/IPv6 literal or FQDN, resolved by the underlying transport at connect time). */
	protected String host;

	/**
	 * Instantiates a new cli.
	 *
	 * @param host the host (IPv4/IPv6 literal or FQDN)
	 * @param taskContext the current task context
	 */
	public Cli(String host, TaskContext taskContext) {
		this.host = host;
		this.taskContext = taskContext;
	}

	/**
	 * Connect.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public abstract void connect() throws IOException;

	/**
	 * Disconnect.
	 */
	public abstract void disconnect();

	/**
	 * Enable or disable the given clean up action.
	 *
	 * @param action the action
	 * @param enable true to enable, false to disable
	 */
	public void setOuputCleanUpAction(CleanUpAction action, boolean enable) {
		if (enable) {
			this.cleanUpActions.add(action);
		}
		else {
			this.cleanUpActions.remove(action);
		}
	}

	/**
	 * Send a command using CommandInput object.
	 *
	 * @param input the command input with all parameters
	 * @return the CommandOutput object
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public CommandOutput send(CommandInput input) throws IOException {
		// Send command if present
		if (input.getCommand() != null) {
			log.debug("Command to send: '{}'.", input.getCommand());
			this.write(input.getCommand());
		}

		// Wait for expected output
		// Extract parameters from input, using defaults where needed
		String[] expects = input.getExpects();
		int timeout = input.getCommandTimeout() == null ? this.commandTimeout : input.getCommandTimeout();
		int discoverWaitTime = input.getDiscoverWaitTime() == null ? 0 : input.getDiscoverWaitTime();

		Pattern[] patterns = new Pattern[expects.length];
		for (int i = 0; i < expects.length; i++) {
			patterns[i] = Pattern.compile(expects[i], Pattern.MULTILINE);
		}

		long lastActivityTime = System.currentTimeMillis();

		StringBuilder buffer = new StringBuilder();
		byte[] miniBuffer = new byte[4096];
		boolean bufferChanged = true;

		while (true) {
			while (this.inStream != null && this.inStream.available() > 0) {
				int length = this.inStream.read(miniBuffer);
				if (length > 0) {
					String s = new String(miniBuffer, 0, length);
					log.trace("Received data '{}'.", s);
					buffer.append(s);
					lastActivityTime = System.currentTimeMillis();
					bufferChanged = true;
				}
			}

			// Only process patterns if new data was received or this is the first iteration
			// AND we've waited long enough after last activity
			if (bufferChanged && System.currentTimeMillis() >= lastActivityTime + discoverWaitTime) {
				// Create a copy of the buffer for cleanup (don't modify the original buffer)
				StringBuilder cleanBuffer = new StringBuilder(buffer);
				this.cleanUpCommandOutput(cleanBuffer, input.cleanUpActions);

				if (patterns.length == 0) {
					// No pattern provided => returns what was captured
					return new CommandOutput(
						input.getCommand(),
						cleanBuffer.toString(),
						cleanBuffer.toString(),
						buffer.toString(),
						null,
						-1,
						""
					);
				}
				else {
					for (int i = 0; i < patterns.length; i++) {
						Matcher matcher = patterns[i].matcher(cleanBuffer);
						if (matcher.find()) {
							return new CommandOutput(
								input.getCommand(),
								matcher.replaceFirst(""),
								cleanBuffer.toString(),
								buffer.toString(),
								matcher,
								i,
								expects[i]
							);
						}
					}
					bufferChanged = false;
				}
			}

			if (System.currentTimeMillis() > lastActivityTime + timeout) {
				throw new WithBufferIOException("Timeout waiting for the command output.", buffer);
			}

			try {
				Thread.sleep(10);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("Thread interrupted while waiting for data", e);
			}
		}
	}

	/**
	 * Write.
	 *
	 * @param value the value
	 */
	protected void write(String value) {
		outStream.print(value);
		outStream.flush();
	}

	/**
	 * Send.
	 *
	 * @param command the command
	 * @param expects the expects
	 * @return the CommandOutput object
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public CommandOutput send(String command, String[] expects) throws IOException {
		return send(new CommandInput(command, expects));
	}

	/**
	 * Send.
	 *
	 * @param command the command
	 * @param expect the expect
	 * @return the CommandOutput object
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public CommandOutput send(String command, String expect) throws IOException {
		return send(new CommandInput(command, expect));
	}

	/**
	 * Cleans up CLI output.
	 *
	 * @param buffer the input buffer coming from device's terminal
	 * @param actions the cleanup actions to apply (use default class actions if null)
	 */
	protected void cleanUpCommandOutput(StringBuilder buffer, EnumSet<CleanUpAction> actions) {
		EnumSet<CleanUpAction> cleanupActions = actions == null ? this.cleanUpActions : actions;

		// Apply cleanup steps in order, modifying buffer in-place
		if (cleanupActions.contains(CleanUpAction.STRIP_ANSI_CODES)) {
			this.removeAnsiCodes(buffer);
		}
		if (cleanupActions.contains(CleanUpAction.PROCESS_BACKSPACES)) {
			this.processBackspaces(buffer);
		}
		if (cleanupActions.contains(CleanUpAction.PROCESS_CARRIAGE_RETURNS)) {
			this.processCarriageReturns(buffer);
		}
		if (cleanupActions.contains(CleanUpAction.NORMALIZE_LINE_ENDINGS)) {
			this.normalizeLineEndings(buffer);
		}
	}

	/**
	 * Removes ANSI escape codes and control characters from the buffer.
	 *
	 * @param buffer the string buffer (modified in-place)
	 */
	private void removeAnsiCodes(StringBuilder buffer) {
		// Process buffer by directly deleting unwanted characters
		int i = 0;
		while (i < buffer.length()) {
			char ch = buffer.charAt(i);

			// Handle ESC sequences
			if (ch == '\u001B') {
				// ESC at end of string - delete it
				if (i + 1 >= buffer.length()) {
					buffer.deleteCharAt(i);
					continue;
				}

				char next = buffer.charAt(i + 1);
				int deleteStart = i;
				int deleteEnd = i + 1; // exclusive

				// CSI sequences: ESC [ ... letter
				if (next == '[') {
					deleteEnd = i + 2;
					while (deleteEnd < buffer.length()) {
						char c = buffer.charAt(deleteEnd);
						// CSI sequences end with a letter (@-Z or a-z per ECMA-48)
						if ((c >= '@' && c <= 'Z')
								|| (c >= 'a' && c <= 'z')) {
							deleteEnd++;
							break;
						}
						deleteEnd++;
					}
					buffer.delete(deleteStart, deleteEnd);
					continue;
				}
				// OSC sequences: ESC ] ... BEL or ESC ] ... ESC \
				else if (next == ']') {
					deleteEnd = i + 2;
					while (deleteEnd < buffer.length()) {
						char c = buffer.charAt(deleteEnd);
						if (c == '\u0007') { // BEL
							deleteEnd++;
							break;
						}
						if (c == '\u001B' && deleteEnd + 1 < buffer.length()
								&& buffer.charAt(deleteEnd + 1) == '\\') {
							deleteEnd += 2;
							break;
						}
						deleteEnd++;
					}
					buffer.delete(deleteStart, deleteEnd);
					continue;
				}
				// Character set selection: ESC ( or ESC )
				else if ((next == '(' || next == ')')
						&& i + 2 < buffer.length()) {
					buffer.delete(i, i + 3);
					continue;
				}
				// Keypad modes: ESC = or ESC >
				else if (next == '=' || next == '>') {
					buffer.delete(i, i + 2);
					continue;
				}
				// Other single-character ESC sequences (ESC followed by any char)
				else {
					buffer.delete(i, i + 2);
					continue;
				}
			}
			// Filter out control characters (except newline, tab, carriage return, backspace)
			else if ((ch >= '\u0000' && ch <= '\u0007')  // \b is \u0008, keep it for later processing
					|| (ch >= '\u000B' && ch <= '\u000C')
					|| (ch >= '\u000E' && ch <= '\u001F')
					|| ch == '\u007F') {
				buffer.deleteCharAt(i);
				continue;
			}

			// Regular character - keep it, move to next
			i++;
		}
	}

	/**
	 * Processes backspace character sequences
	 * (removes the backspace along with previous char).
	 *
	 * @param buffer the string buffer (modified in-place)
	 */
	private void processBackspaces(StringBuilder buffer) {
		int i = 0;
		while (i < buffer.length()) {
			// Check for pattern: non-backspace char followed by backspace
			if (i + 1 < buffer.length()
					&& buffer.charAt(i) != '\b'
					&& buffer.charAt(i + 1) == '\b') {
				// Delete both characters (simulates backspace deleting previous char)
				buffer.delete(i, i + 2);
				// Step back to check for cascading backspaces, but don't go below 0
				if (i > 0) {
					i--;
				}
			}
			else {
				i++;
			}
		}
	}

	/**
	 * Processes carriage returns to simulate terminal line overwrite behavior.
	 * When a \r is encountered, the cursor moves to the beginning of the current line,
	 * and subsequent characters overwrite existing characters position by position.
	 *
	 * @param buffer the string buffer (modified in-place)
	 */
	private void processCarriageReturns(StringBuilder buffer) {
		int i = 0;
		int lineStart = 0; // Track start of current line

		while (i < buffer.length()) {
			char ch = buffer.charAt(i);

			if (ch == '\n') {
				// Move to next line
				lineStart = i + 1;
				i++;
			}
			else if (ch == '\r') {
				// Count consecutive \r characters
				int crStart = i;
				int crCount = 0;
				while (i < buffer.length() && buffer.charAt(i) == '\r') {
					crCount++;
					i++;
				}

				// Find content after the \r sequence
				int contentStart = i;
				int contentEnd = contentStart;

				// Find the end of content (next \r or \n or end of buffer)
				while (contentEnd < buffer.length()
						&& buffer.charAt(contentEnd) != '\r'
						&& buffer.charAt(contentEnd) != '\n') {
					contentEnd++;
				}

				int contentLength = contentEnd - contentStart;

				if (contentLength > 0) {
					// Extract the content that will overwrite
					String content = buffer.substring(contentStart, contentEnd);
					int lineLength = crStart - lineStart;

					// Save the tail if the line is longer than the new content
					String tail = "";
					if (lineLength > contentLength) {
						tail = buffer.substring(lineStart + contentLength, crStart);
					}

					// Delete from lineStart to contentEnd (old line + \r's + new content)
					buffer.delete(lineStart, contentEnd);

					// Insert new content + tail at lineStart
					buffer.insert(lineStart, content + tail);

					// Continue from after the inserted content + tail
					i = lineStart + content.length() + tail.length();
				}
				else {
					// No content after \r sequence
					if (crCount > 1) {
						// Multiple consecutive \r's with no content - delete all
						buffer.delete(crStart, crStart + crCount);
						i = crStart; // Reset to start of deleted region
					}
					else {
						// Single \r with no content after (either before \n or at end) - keep it
						// i is already advanced past the \r
					}
				}
			}
			else {
				i++;
			}
		}
	}

	/**
	 * Normalizes line endings by converting CRLF to LF and removing trailing \r.
	 * Only active when normalizeCarriageReturns is enabled.
	 *
	 * @param buffer the string buffer (modified in-place)
	 */
	private void normalizeLineEndings(StringBuilder buffer) {
		int i = 0;

		while (i < buffer.length()) {
			char ch = buffer.charAt(i);

			if (ch == '\r') {
				if (i + 1 < buffer.length()
						&& buffer.charAt(i + 1) == '\n') {
					// CRLF -> LF: delete the \r, keep the \n
					buffer.deleteCharAt(i);
					continue;
				}
				else if (i + 1 >= buffer.length()) {
					// Trailing \r at end of buffer: delete it if there are newlines in the buffer
					// (indicating multi-line content)
					if (buffer.indexOf("\n") >= 0) {
						buffer.deleteCharAt(i);
						continue;
					}
				}
			}
			i++;
		}
	}

}
