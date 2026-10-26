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
package net.netshot.netshot.boot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Catalog of the configuration settings")
public class NetshotSettingsTest {

	private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("net.netshot.netshot");

	/**
	 * Gets the top-level class of a class.
	 * @param javaClass the class
	 * @return the name of the top-level class
	 */
	private static String topLevelName(JavaClass javaClass) {
		JavaClass current = javaClass;
		while (current.getEnclosingClass().isPresent()) {
			current = current.getEnclosingClass().get();
		}
		return current.getName();
	}

	@Test
	@DisplayName("Settings are static final fields, of classes listed in the catalog")
	void allDeclaringClassesAreListed() {
		Set<String> listed = new TreeSet<>();
		for (Class<?> declaring : NetshotSettings.declaringClasses()) {
			listed.add(declaring.getName());
		}
		Set<String> declaring = new TreeSet<>();
		List<String> notStaticFinal = new ArrayList<>();
		for (JavaClass javaClass : MAIN_CLASSES) {
			for (JavaField field : javaClass.getFields()) {
				if (!field.getRawType().isEquivalentTo(Setting.class)) {
					continue;
				}
				if (!field.getModifiers().contains(JavaModifier.STATIC) || !field.getModifiers().contains(JavaModifier.FINAL)) {
					notStaticFinal.add(field.getFullName());
					continue;
				}
				declaring.add(topLevelName(javaClass));
			}
		}
		Assertions.assertEquals(List.of(), notStaticFinal, "Settings must be declared as static final fields");
		Assertions.assertEquals(declaring, listed, "The classes declaring settings must be listed in NetshotSettings");
	}

	@Test
	@DisplayName("Each setting key is declared once")
	void uniqueKeys() {
		Set<String> keys = new HashSet<>();
		Set<String> duplicates = new TreeSet<>();
		for (Setting<?> setting : NetshotSettings.catalog().settings()) {
			if (!keys.add(setting.key())) {
				duplicates.add(setting.key());
			}
			if (setting.alias() != null && !keys.add(setting.alias())) {
				duplicates.add(setting.alias());
			}
		}
		Assertions.assertEquals(Set.of(), duplicates);
	}
}
