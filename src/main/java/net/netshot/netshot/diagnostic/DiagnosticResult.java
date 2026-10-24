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
package net.netshot.netshot.diagnostic;

import java.time.Instant;

/**
 * The result of a diagnostic on a device: the last value the diagnostic gave, kept with the
 * date it was first given ({@link #creationDate}) and the date it was last checked. There is one
 * result per device and diagnostic. Its type is stored as a one letter discriminator.
 */
public sealed interface DiagnosticResult
	permits DiagnosticTextResult, DiagnosticNumericResult, DiagnosticBinaryResult, DiagnosticLongTextResult {

	/**
	 * Gets the ID of the diagnostic.
	 *
	 * @return the diagnostic ID
	 */
	Long diagnosticId();

	/**
	 * Gets the name of the diagnostic (read from the diagnostic).
	 *
	 * @return the name
	 */
	String diagnosticName();

	/**
	 * Gets when the diagnostic first gave this value.
	 *
	 * @return the date
	 */
	Instant creationDate();

	/**
	 * Gets when the diagnostic last gave this value.
	 *
	 * @return the date
	 */
	Instant lastCheckDate();

	/**
	 * Gets the same result, with other dates.
	 *
	 * @param newCreationDate the date of creation
	 * @param newLastCheckDate the date of the last check
	 * @return the result
	 */
	DiagnosticResult withDates(Instant newCreationDate, Instant newLastCheckDate);

	/**
	 * Gets the value of the result, as text.
	 *
	 * @return the text (empty when there is no value)
	 */
	String getAsText();

	/**
	 * Gets the value of the result.
	 *
	 * @return the value
	 */
	Object getData();

	/**
	 * Tells whether another result has the same type and value (whatever its diagnostic and dates).
	 *
	 * @param other the other result (may be null)
	 * @return true if the value is the same
	 */
	boolean valueEquals(DiagnosticResult other);

	/**
	 * The time of a new result.
	 *
	 * @return now
	 */
	static Instant now() {
		return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
	}
}
