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
package net.netshot.netshot.task.type;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.testing.UnitTest;

@UnitTest
@DisplayName("Task type descriptions")
public class TaskSpecsDescriptionTest {

	private static Object defaultValue(Class<?> type) {
		if (type == boolean.class) {
			return false;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		return null;
	}

	@Test
	@DisplayName("The description of a type name is the one of its specification")
	void descriptionsMatch() throws Exception {
		for (Class<?> specClass : TaskSpec.class.getPermittedSubclasses()) {
			RecordComponent[] components = specClass.getRecordComponents();
			Class<?>[] types = new Class<?>[components.length];
			Object[] values = new Object[components.length];
			for (int i = 0; i < components.length; i++) {
				types[i] = components[i].getType();
				values[i] = defaultValue(types[i]);
			}
			Constructor<?> constructor = specClass.getDeclaredConstructor(types);
			TaskSpec spec = (TaskSpec) constructor.newInstance(values);
			Assertions.assertEquals(spec.description(), TaskSpecs.description(spec.typeName()), specClass.getSimpleName());
		}
	}
}
