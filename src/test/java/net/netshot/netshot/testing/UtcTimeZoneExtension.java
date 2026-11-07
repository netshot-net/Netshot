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
package net.netshot.netshot.testing;

import java.util.TimeZone;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Sets the default time zone of the JVM to UTC for the test class, and restores the previous one after it: for
 * the {@link ContractTest}s (some outputs, such as download file names, format dates in it), and the tests whose
 * results depend on calendar days in the default time zone (daylight saving time changes).
 */
public final class UtcTimeZoneExtension implements BeforeAllCallback, AfterAllCallback {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(UtcTimeZoneExtension.class);

	@Override
	public void beforeAll(ExtensionContext context) {
		context.getStore(NAMESPACE).put(TimeZone.class, TimeZone.getDefault());
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	@Override
	public void afterAll(ExtensionContext context) {
		TimeZone previous = context.getStore(NAMESPACE).remove(TimeZone.class, TimeZone.class);
		if (previous != null) {
			TimeZone.setDefault(previous);
		}
	}
}
