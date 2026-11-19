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
package net.netshot.netshot.boot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.platform.lifecycle.ServiceStartException;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Fatal error report")
class FatalErrorReporterTest {

	private static String console(Exception e) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		FatalErrorReporter.report(e, new PrintStream(out, true, StandardCharsets.UTF_8));
		return out.toString(StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("A configuration error is one line, without stack trace")
	void bootstrapError() {
		Assertions.assertEquals("NETSHOT FATAL ERROR: Invalid configuration: wrong" + System.lineSeparator(),
			console(new BootstrapException("Invalid configuration: wrong")));
	}

	@Test
	@DisplayName("A bootstrap error with a cause tells where")
	void bootstrapErrorWithCause() {
		String console = console(new BootstrapException("Unable to register the crypto libraries: failed", new IllegalStateException("failed")));
		Assertions.assertTrue(console.startsWith("NETSHOT FATAL ERROR: Unable to register the crypto libraries: failed"), console);
		Assertions.assertTrue(console.contains("Caused by: java.lang.IllegalStateException: failed"), console);
	}

	@Test
	@DisplayName("A service start failure gives the service and the cause, the stack trace being in the log")
	void serviceStartError() {
		Assertions.assertEquals("NETSHOT FATAL ERROR: Unable to start the service 'Task manager': no database (see the log)" + System.lineSeparator(),
			console(new ServiceStartException("Unable to start the service 'Task manager'", new IllegalStateException("no database"))));
	}

	@Test
	@DisplayName("Another error is printed with its stack trace")
	void otherError() {
		String console = console(new IllegalArgumentException("unexpected"));
		Assertions.assertTrue(console.startsWith("NETSHOT FATAL ERROR: unexpected" + System.lineSeparator() + "java.lang.IllegalArgumentException: unexpected"),
			console);
	}
}
