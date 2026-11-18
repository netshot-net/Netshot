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

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Bootstrap")
class BootstrapTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("The failure to read the configuration tells which files were tried")
	void unreadableConfiguration() {
		String missing = this.tempDir.resolve("missing.conf").toString();
		BootstrapException e = Assertions.assertThrows(BootstrapException.class, () -> Bootstrap.initialize(ConfigStore.ofFiles(List.of(missing))));
		Assertions.assertEquals("Unable to read the configuration: No configuration file was found (tried %s)".formatted(missing), e.getMessage());
		Assertions.assertNull(e.getCause(), "No stack trace for a configuration error");
	}

	@Test
	@DisplayName("The JVM check tells GraalVM 25 from the other JVMs, also without the optional vendor version")
	void checkJvm() {
		Assertions.assertTrue(Bootstrap.checkJvm("Oracle GraalVM 25.0.4+7.1", "Oracle Corporation", "25.0.4+7-LTS-jvmci-b01"));
		Assertions.assertFalse(Bootstrap.checkJvm("Temurin-25.0.1+8", "Eclipse Adoptium", "25.0.1+8-LTS"));
		Assertions.assertFalse(Bootstrap.checkJvm("Oracle GraalVM 21.0.9+7.1", "Oracle Corporation", "21.0.9+7-LTS-jvmci-23.1-b79"));
		Assertions.assertFalse(Bootstrap.checkJvm(null, "Some vendor", "25.0.1"));
		Assertions.assertFalse(Bootstrap.checkJvm(null, null, null));
	}
}
