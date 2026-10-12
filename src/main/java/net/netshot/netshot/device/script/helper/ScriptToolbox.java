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
package net.netshot.netshot.device.script.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

/**
 * The script toolbox: helpers (such as the ConfigTree configuration parser)
 * available to user scripts (drivers, compliance rules, diagnostics).
 * Implemented natively in both JavaScript (global Toolbox object, toolbox.js)
 * and Python (netshot.toolbox module, toolbox.py), so the toolbox code must be
 * evaluated in the context before the user code.
 */
public final class ScriptToolbox {

	/** Toolbox JavaScript source. */
	public static final Source JS_SOURCE = readSource("js", "toolbox.js");

	/** Toolbox Python source. */
	public static final Source PYTHON_SOURCE = readSource("python", "toolbox.py");

	private ScriptToolbox() {
	}

	/**
	 * Read a toolbox source from the resource files.
	 * @param language the polyglot language
	 * @param fileName the file name (in the interfaces resource folder)
	 * @return the source
	 */
	private static Source readSource(String language, String fileName) {
		String path = "/interfaces/" + fileName;
		try (InputStream in = ScriptToolbox.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Unable to find the toolbox resource " + path);
			}
			String code = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			return Source.newBuilder(language, code, fileName).buildLiteral();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Unable to read the toolbox resource " + path, e);
		}
	}

	/**
	 * Load the JavaScript toolbox into the context.
	 * @param context the context (with JavaScript support)
	 */
	public static void loadJs(Context context) {
		context.eval(JS_SOURCE);
	}

	/**
	 * Load the Python toolbox into the context (registers the netshot.toolbox module).
	 * @param context the context (with Python support)
	 */
	public static void loadPython(Context context) {
		context.eval(PYTHON_SOURCE);
	}
}
