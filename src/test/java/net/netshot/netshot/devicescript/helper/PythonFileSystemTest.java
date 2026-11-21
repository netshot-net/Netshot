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
package net.netshot.netshot.devicescript.helper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("File system of the Python scripts")
class PythonFileSystemTest {

	@TempDir
	Path dir;

	/** The virtualenv folder, the only one the scripts can read (with the Python library). */
	Path venv;

	/** A file outside the readable folders. */
	Path outside;

	PythonFileSystem fileSystem;

	@BeforeEach
	void setUp() throws IOException {
		this.venv = Files.createDirectories(this.dir.resolve("venv"));
		Files.writeString(this.venv.resolve("module.py"), "VALUE = 1\n");
		this.outside = Files.writeString(this.dir.resolve("outside.txt"), "secret");
		this.fileSystem = new PythonFileSystem(new PythonFileSystem.Settings(this.venv.toString()));
	}

	private static String read(PythonFileSystem fileSystem, Path path) throws IOException {
		try (var channel = fileSystem.newByteChannel(path, Set.of(StandardOpenOption.READ))) {
			ByteBuffer buffer = ByteBuffer.allocate(64);
			channel.read(buffer);
			return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
		}
	}

	@Test
	@DisplayName("The files of the virtualenv can be read and listed, not the others")
	void readAccess() throws IOException {
		Assertions.assertEquals("VALUE = 1\n", read(this.fileSystem, this.venv.resolve("module.py")));
		List<Path> listed = new ArrayList<>();
		try (DirectoryStream<Path> listing = this.fileSystem.newDirectoryStream(this.venv, _ -> true)) {
			listing.forEach(listed::add);
		}
		Assertions.assertEquals(List.of(this.venv.resolve("module.py")), listed);
		this.fileSystem.checkAccess(this.venv.resolve("module.py"), Set.of(AccessMode.READ));

		Assertions.assertThrows(SecurityException.class, () -> read(this.fileSystem, this.outside));
		Assertions.assertThrows(SecurityException.class, () -> this.fileSystem.newDirectoryStream(this.dir, _ -> true));
		Assertions.assertThrows(SecurityException.class, () -> this.fileSystem.checkAccess(this.outside, Set.of(AccessMode.READ)));
		// A '..' path leading out of the virtualenv
		Assertions.assertThrows(SecurityException.class, () -> read(this.fileSystem, this.venv.resolve("../outside.txt")));
	}

	@Test
	@DisplayName("The attributes and the real path of a file are given only in the virtualenv")
	void metadataAccess() throws IOException {
		Path module = this.venv.resolve("module.py");
		Assertions.assertEquals(10L, this.fileSystem.readAttributes(module, "size").get("size"));
		Assertions.assertEquals(module.toRealPath(), this.fileSystem.toRealPath(module));

		Assertions.assertThrows(SecurityException.class, () -> this.fileSystem.readAttributes(this.outside, "size"));
		Assertions.assertThrows(SecurityException.class, () -> this.fileSystem.toRealPath(this.outside));
		Path link = Files.createSymbolicLink(this.venv.resolve("link.txt"), this.outside);
		Assertions.assertThrows(SecurityException.class, () -> this.fileSystem.toRealPath(link), "The link target is revealed");
		Assertions.assertThrows(SecurityException.class, () -> this.fileSystem.readAttributes(link, "size"));
	}

	@Test
	@DisplayName("A missing file of the virtualenv is reported missing, a missing file elsewhere is refused")
	void missingFile() {
		Assertions.assertThrows(NoSuchFileException.class, () -> read(this.fileSystem, this.venv.resolve("missing/module.py")));
		Assertions.assertThrows(SecurityException.class, () -> read(this.fileSystem, this.dir.resolve("missing/module.py")));
	}

	@Test
	@DisplayName("A link of the virtualenv to a file outside is refused")
	void linkOutside() throws IOException {
		Path link = Files.createSymbolicLink(this.venv.resolve("link.txt"), this.outside);
		Assertions.assertThrows(SecurityException.class, () -> read(this.fileSystem, link));
	}

	@Test
	@DisplayName("Nothing can be written, created or deleted, even in the virtualenv")
	void writeAccess() {
		Path module = this.venv.resolve("module.py");
		for (StandardOpenOption option : new StandardOpenOption[] {
			StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING }) {
			Assertions.assertThrows(SecurityException.class,
				() -> this.fileSystem.newByteChannel(module, Set.of(StandardOpenOption.READ, option)), option.toString());
		}
		Assertions.assertThrows(SecurityException.class, () -> this.fileSystem.checkAccess(module, Set.of(AccessMode.WRITE)));
		Assertions.assertThrows(SecurityException.class, () -> this.fileSystem.checkAccess(module, Set.of(AccessMode.EXECUTE)));
		Assertions.assertThrows(SecurityException.class, () -> this.fileSystem.createDirectory(this.venv.resolve("new")));
		Assertions.assertThrows(SecurityException.class, () -> this.fileSystem.delete(module));
		Assertions.assertTrue(Files.exists(module));
		Assertions.assertFalse(Files.exists(this.venv.resolve("new")));
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	@DisplayName("The system-wide /proc entries can be read, not the ones of a process")
	void procEntries() throws IOException {
		PythonFileSystem noVenv = new PythonFileSystem(new PythonFileSystem.Settings(null));
		noVenv.checkAccess(Path.of("/proc/cpuinfo"), Set.of(AccessMode.READ));
		for (String path : new String[] { "/proc/self/environ", "/proc/thread-self/environ",
			"/proc/%d/environ".formatted(ProcessHandle.current().pid()), "/proc/1/cmdline" }) {
			Assertions.assertThrows(SecurityException.class, () -> noVenv.checkAccess(Path.of(path), Set.of(AccessMode.READ)), path);
		}
		Assertions.assertThrows(SecurityException.class, () -> read(noVenv, this.venv.resolve("module.py")), "No virtualenv");
	}

	@Test
	@DisplayName("The virtualenv setting is ignored when the folder doesn't exist or isn't a folder")
	void settings() {
		Properties properties = new Properties();
		Assertions.assertNull(PythonFileSystem.Settings.from(Config.of(properties)).venvFolder());
		properties.setProperty("netshot.python.virtualenv", this.dir.resolve("missing").toString());
		Assertions.assertNull(PythonFileSystem.Settings.from(Config.of(properties)).venvFolder());
		properties.setProperty("netshot.python.virtualenv", this.outside.toString());
		Assertions.assertNull(PythonFileSystem.Settings.from(Config.of(properties)).venvFolder(), "A file");
		properties.setProperty("netshot.python.virtualenv", this.venv.toString());
		Assertions.assertEquals(this.venv.toAbsolutePath().toString(),
			PythonFileSystem.Settings.from(Config.of(properties)).venvFolder());
	}
}
