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
package net.netshot.netshot.platform.runtime;

import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

/**
 * The instrumentation handed by the JVM to the agent entry point of Netshot ({@code Launcher-Agent-Class}), used
 * to load the crypto libraries into the system class loader.
 */
public final class AgentInstrumentation {

	/**
	 * The instrumentation, set by the JVM through the agent entry point before {@code main}: the only mutable
	 * static field, see the STATIC_FIELDS_ARE_FINAL rule of ArchitectureTest.
	 */
	private static volatile Instrumentation instrumentation;

	private AgentInstrumentation() {
	}

	/**
	 * Sets the instrumentation (from the agent entry point).
	 *
	 * @param inst the instrumentation given by the JVM
	 */
	public static void set(Instrumentation inst) {
		instrumentation = inst;
	}

	/**
	 * Appends a JAR to the search of the system class loader.
	 *
	 * @param jar the JAR file to append
	 * @throws IllegalStateException if the agent entry point wasn't called
	 */
	public static void appendJar(JarFile jar) {
		Instrumentation current = instrumentation;
		if (current == null) {
			throw new IllegalStateException("Instrumentation not initialized");
		}
		current.appendToSystemClassLoaderSearch(jar);
	}
}
