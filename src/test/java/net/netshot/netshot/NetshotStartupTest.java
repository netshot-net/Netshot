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
package net.netshot.netshot;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.platform.runtime.BuildInfo;
import net.netshot.netshot.testing.DatabaseTest;
import net.netshot.netshot.testing.DatabaseTest.Boot;
import net.netshot.netshot.testing.TestDatabase;

/**
 * Runs the server ({@link Netshot#main}) in its own JVM, as it's started in production: the bootstrap (configuration,
 * logging), the report of the fatal errors and the exit code, the start of the services, the reload on HUP and the
 * stop on SIGTERM.
 */
@DatabaseTest(boot = Boot.MANUAL)
@DisplayName("Server startup (own JVM)")
class NetshotStartupTest {

	/** How long the server may take to start or stop. */
	private static final Duration TIMEOUT = Duration.ofMinutes(3);

	/** The number of the HUP signal (the same on Linux and macOS). */
	private static final int SIGHUP = 1;

	@TempDir
	Path tempDir;

	/**
	 * The output of a server process.
	 *
	 * @param exitCode the exit code
	 * @param stdout the standard output
	 * @param stderr the error output
	 * @param log the log file
	 */
	private record Run(int exitCode, String stdout, String stderr, String log) {
	}

	/**
	 * Builds the configuration of a server: logging to a file, at INFO, without the listeners (HTTP, SSH, Syslog,
	 * SNMP traps).
	 *
	 * @return the configuration
	 */
	private Properties baseConfig() {
		Properties config = new Properties();
		config.setProperty("netshot.log.file", this.tempDir.resolve("netshot.log").toString());
		config.setProperty("netshot.log.level", "INFO");
		config.setProperty("netshot.http.enabled", "false");
		config.setProperty("netshot.sshserver.enabled", "false");
		config.setProperty("netshot.syslog.enabled", "false");
		config.setProperty("netshot.snmptrap.enabled", "false");
		config.setProperty("netshot.snapshots.binary.path", this.tempDir.toString());
		return config;
	}

	private Path writeConfig(Properties config) throws IOException {
		Path file = this.tempDir.resolve("netshot.conf");
		StringBuilder text = new StringBuilder();
		config.forEach((key, value) -> text.append(key).append(" = ").append(value).append('\n'));
		Files.writeString(file, text);
		return file;
	}

	/**
	 * Starts the server in its own JVM, with the classpath of the tests, out of the NETSHOT_* environment (as with
	 * the jar, but without its launcher agent).
	 *
	 * @param configFile the configuration file
	 * @return the process
	 * @throws IOException if the process can't be started
	 */
	private Process launch(Path configFile) throws IOException {
		// The jar has no Logback configuration: before its setup, the logging is the default one of Logback (DEBUG, on
		// the console), not the one of the tests, which are on the classpath
		Path logback = this.tempDir.resolve("logback-default.xml");
		Files.writeString(logback, """
			<configuration>
				<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
					<encoder><pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} -- %msg%n</pattern></encoder>
				</appender>
				<root level="DEBUG"><appender-ref ref="CONSOLE"/></root>
			</configuration>
			""");
		List<String> command = new ArrayList<>();
		command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
		command.add("-Dlogback.configurationFile=" + logback);
		// As the manifest of the jar
		command.add("--enable-native-access=ALL-UNNAMED");
		command.add("-cp");
		command.add(System.getProperty("java.class.path"));
		command.add(Netshot.class.getName());
		command.add("-c");
		command.add(configFile.toString());
		ProcessBuilder builder = new ProcessBuilder(command)
			.directory(this.tempDir.toFile())
			.redirectOutput(this.tempDir.resolve("stdout.txt").toFile())
			.redirectError(this.tempDir.resolve("stderr.txt").toFile());
		Map<String, String> environment = builder.environment();
		environment.keySet().removeIf(name -> name.startsWith("NETSHOT_"));
		return builder.start();
	}

	private Run await(Process process) throws Exception {
		if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
			process.destroyForcibly();
			Assertions.fail("The server didn't end: " + this.read("stderr.txt"));
		}
		return new Run(process.exitValue(), this.read("stdout.txt"), this.read("stderr.txt"), this.read("netshot.log"));
	}

	private String read(String name) throws IOException {
		Path file = this.tempDir.resolve(name);
		return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
	}

	/**
	 * Waits for a line in the log of a running server.
	 *
	 * @param process the server process
	 * @param text the text to wait for
	 * @throws Exception if interrupted, or if the text doesn't come
	 */
	private void awaitLog(Process process, String text) throws Exception {
		Instant deadline = Instant.now().plus(TIMEOUT);
		while (!this.read("netshot.log").contains(text)) {
			if (!process.isAlive()) {
				Assertions.fail("The server ended (%d) before logging '%s': %s".formatted(process.exitValue(), text, this.read("stderr.txt")));
			}
			if (Instant.now().isAfter(deadline)) {
				process.destroyForcibly();
				Assertions.fail("The server didn't log '%s' in time".formatted(text));
			}
			Thread.sleep(200);
		}
	}

	private static int count(String text, String part) {
		Matcher matcher = Pattern.compile(Pattern.quote(part)).matcher(text);
		int count = 0;
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	/**
	 * Sends a signal to a process, with the kill function of the C library (Java can only send TERM and KILL, and
	 * the build image, a Debian without procps, has no kill command).
	 *
	 * @param pid the process ID
	 * @param signal the signal number
	 * @return the result of kill: 0 on success
	 */
	private static int sendSignal(long pid, int signal) {
		Linker linker = Linker.nativeLinker();
		MethodHandle kill = linker.downcallHandle(linker.defaultLookup().find("kill").orElseThrow(),
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
		try {
			return (int) kill.invokeExact((int) pid, signal);
		}
		catch (Throwable e) {
			throw new IllegalStateException("Unable to call kill", e);
		}
	}

	@Test
	@DisplayName("Without configuration file: exits with 1, telling which file was tried, nothing else on the console")
	void missingConfiguration() throws Exception {
		Path missing = this.tempDir.resolve("missing.conf");
		Run run = this.await(this.launch(missing));
		Assertions.assertEquals(1, run.exitCode());
		Assertions.assertEquals("NETSHOT FATAL ERROR: Unable to read the configuration: No configuration file was found (tried %s)%n".formatted(missing),
			run.stderr());
		// Nothing else on the console (before the logging is set up, e.g. the stack trace of the file tried)
		Assertions.assertEquals("Starting Netshot version %s.%n".formatted(BuildInfo.VERSION), run.stdout());
	}

	@Test
	@DisplayName("A configuration error found once the logging is set up is also logged, without stack trace")
	void configurationError() throws Exception {
		Properties config = this.baseConfig();
		config.setProperty("netshot.db.encryptionpassword.required", "true");
		Run run = this.await(this.launch(this.writeConfig(config)));
		Assertions.assertEquals(1, run.exitCode());
		Assertions.assertTrue(run.stderr().startsWith("NETSHOT FATAL ERROR: Invalid configuration: The DB encryption password"), run.stderr());
		Assertions.assertEquals(1, count(run.stderr(), System.lineSeparator()), "One line: " + run.stderr());
		Assertions.assertEquals(1, count(run.log(), "Invalid configuration: The DB encryption password"), run.log());
	}

	@Test
	@DisplayName("A database which can't be reached: exits with 1, the error logged once")
	void databaseUnreachable() throws Exception {
		Properties config = this.baseConfig();
		config.setProperty("netshot.db.url", "jdbc:postgresql://127.0.0.1:1/netshot");
		Run run = this.await(this.launch(this.writeConfig(config)));
		Assertions.assertEquals(1, run.exitCode());
		Assertions.assertTrue(run.stderr().contains("NETSHOT FATAL ERROR: Unable to update the database schema"), run.stderr());
		Assertions.assertEquals(1, count(run.log(), "Unable to update the database schema"), run.log());
	}

	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	@DisplayName("Starts, reloads the configuration on HUP, stops on SIGTERM")
	void startReloadStop(TestDatabase database) throws Exception {
		Properties config = this.baseConfig();
		config.putAll(database.freshConfig("public"));
		Path configFile = this.writeConfig(config);
		Process process = this.launch(configFile);
		try {
			this.awaitLog(process, "Netshot is started");
			String log = this.read("netshot.log");
			Assertions.assertTrue(log.contains("Configuration file %s read.".formatted(configFile)), log);
			Assertions.assertTrue(this.read("stdout.txt").contains("Configuration file %s read.".formatted(configFile)));

			Assertions.assertEquals(0, sendSignal(process.pid(), SIGHUP));
			this.awaitLog(process, "The configuration was reloaded from %s.".formatted(configFile));
			Assertions.assertTrue(process.isAlive(), "The server stopped on HUP");
		}
		finally {
			process.destroy();
		}
		Run run = this.await(process);
		// 128 + SIGTERM
		Assertions.assertEquals(143, run.exitCode());
		Assertions.assertTrue(run.log().contains("Netshot is stopped."), run.log());
		// The JVM may warn on the console (e.g. the deprecated memory access of Truffle), but nothing fails
		Assertions.assertFalse(run.stderr().contains("NETSHOT FATAL ERROR") || run.stderr().contains("Exception"), run.stderr());
	}
}
