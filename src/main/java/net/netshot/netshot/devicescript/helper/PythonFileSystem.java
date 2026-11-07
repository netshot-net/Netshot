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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.io.FileSystem;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;

/**
 * Implementation of the FileSystem GraalVM interface to restrict
 * Python code I/O access to the shipping GraalPython libraries,
 * in read-only mode.
 * Use {@link #newRestrictedFileSystem()} to get the file system to pass to the
 * Python context, which also gives read access to the GraalPy standard library.
 */
@Slf4j
public final class PythonFileSystem implements FileSystem {

	/**
	 * The settings of the Python file system.
	 *
	 * @param venvFolder the Python virtualenv folder (null if none)
	 */
	public record Settings(String venvFolder) {

		/** The Python virtualenv folder. */
		static final Setting<String> VIRTUALENV = Setting.text("netshot.python.virtualenv").live();

		/**
		 * Reads the settings.
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			String venv = config.get(VIRTUALENV);
			if (venv != null) {
				File venvFile = new File(venv);
				if (venvFile.exists() && venvFile.isDirectory()) {
					return new Settings(venvFile.getAbsolutePath());
				}
				log.warn("Python virtualenv folder {} doesn't exist or is not a directory", venv);
			}
			return new Settings(null);
		}
	}

	/** The settings, from the configuration. */
	private static final ConfigStore.Binding<Settings> SETTINGS = Netshot.config().bind(Settings::from);

	/**
	 * Gets the settings of the Python file system.
	 * @return the settings
	 */
	public static Settings settings() {
		return SETTINGS.get();
	}

	private final FileSystem delegate;
	private final Path libFolder;
	private final Path venvFolder;

	/**
	 * Create the file system to be used by Python contexts: this filter, plus access
	 * to the internal resources of the languages (such as the GraalPy standard library,
	 * extracted to the polyglot resource cache folder), everything in read-only mode.
	 * The read-only wrapper matters: the internal resources are shared by all Python
	 * contexts, so a script able to write there could inject code in the other scripts.
	 * @return the file system
	 * @throws IOException if the file system can't be initialized
	 */
	public static FileSystem newRestrictedFileSystem() throws IOException {
		return FileSystem.newReadOnlyFileSystem(
			FileSystem.allowInternalResourceAccess(new PythonFileSystem()));
	}

	public PythonFileSystem() throws IOException {
		this.delegate = FileSystem.newDefaultFileSystem();
		// Language folder of former GraalVM JDKs (when languages were installed within the JDK)
		Path languagesFolder = null;
		try {
			languagesFolder = delegate.toRealPath(
				delegate.parsePath(System.getProperty("java.home") + "/languages"));
		}
		catch (IOException e) {
			log.debug("No languages folder in Java home, ignoring");
		}
		this.libFolder = languagesFolder;
		String venv = PythonFileSystem.settings().venvFolder();
		if (venv == null) {
			this.venvFolder = null;
		}
		else {
			this.venvFolder = delegate.toRealPath(delegate.parsePath(venv));
		}
	}

	private void verifyAccess(Path path, String reason) {
		log.debug("Checking permission ({}) for path {}", reason, path);
		Path realPath = null;
		for (Path c = path; c != null; c = c.getParent()) {
			try {
				realPath = delegate.toRealPath(c);
				break;
			}
			catch (IOException ioe) {
			}
		}
		if (realPath == null || !((libFolder != null && realPath.startsWith(libFolder)) || (venvFolder != null && realPath.startsWith(venvFolder))
			|| isSystemWideProcEntry(realPath))) {
			log.info("Access ({}) to {} [{}] is denied", reason, path, realPath);
			throw new SecurityException("Access to " + path + " is denied.");
		}
	}

	/**
	 * Checks whether the given (real) path is a system-wide /proc entry (e.g. /proc/cpuinfo).
	 * Per-process entries (/proc/self, /proc/thread-self, /proc/[pid]) are excluded, as they
	 * would expose the Netshot process environment (secrets passed as env variables),
	 * command line or memory.
	 * @param realPath the real path to check
	 * @return true if the path is a system-wide /proc entry
	 */
	private static boolean isSystemWideProcEntry(Path realPath) {
		if (!realPath.isAbsolute() || realPath.getNameCount() < 2 || !"proc".equals(realPath.getName(0).toString())) {
			return false;
		}
		String entry = realPath.getName(1).toString();
		return !"self".equals(entry) && !"thread-self".equals(entry) && !entry.chars().allMatch(Character::isDigit);
	}

	@Override
	public Path parsePath(String path) {
		return delegate.parsePath(path);
	}

	@Override
	public Path parsePath(URI uri) {
		return delegate.parsePath(uri);
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
		throws IOException {
		for (OpenOption option : options) {
			if (!option.equals(StandardOpenOption.READ)) {
				log.warn("Denying new Byte channel for {} with option {}", path, option);
				throw new SecurityException("Opening " + path + " with option " + option + " is denied.");
			}
		}
		verifyAccess(path, "newByteChannel");
		return delegate.newByteChannel(path, options, attrs);
	}

	@Override
	public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
		for (AccessMode mode : modes) {
			if (!mode.equals(AccessMode.READ)) {
				log.warn("Denying access to file {} in {} mode", path, mode);
				throw new SecurityException("Opening " + path + " in mode " + mode + " is denied.");
			}
		}
		verifyAccess(path, "checkAccess");
		delegate.checkAccess(path, modes, linkOptions);
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		log.warn("Denying creation of directory {}", dir);
		throw new SecurityException("Creation of directory " + dir + " is denied.");
	}

	@Override
	public void delete(Path path) throws IOException {
		log.warn("Denying deletion of {}", path);
		throw new SecurityException("Deletion of file " + path + " is denied.");
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		verifyAccess(dir, "newDirectoryStream");
		return delegate.newDirectoryStream(dir, filter);
	}

	@Override
	public Path toAbsolutePath(Path path) {
		return delegate.toAbsolutePath(path);
	}

	@Override
	public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
		return delegate.toRealPath(path, linkOptions);
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		return delegate.readAttributes(path, attributes, options);
	}
}
