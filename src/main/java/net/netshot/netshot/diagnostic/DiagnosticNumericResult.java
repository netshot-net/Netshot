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
import java.util.Objects;

/**
 * A numeric diagnostic result.
 *
 * @param diagnosticId the ID of the diagnostic
 * @param diagnosticName the name of the diagnostic
 * @param creationDate when the diagnostic first gave this value
 * @param lastCheckDate when the diagnostic last gave this value
 * @param number the value
 */
public record DiagnosticNumericResult(Long diagnosticId, String diagnosticName, Instant creationDate, Instant lastCheckDate, Double number)
	implements DiagnosticResult {

	/**
	 * Builds a new result of a diagnostic (created and checked now).
	 *
	 * @param diagnostic the diagnostic
	 * @param value the value
	 * @return the result
	 */
	public static DiagnosticNumericResult of(Diagnostic diagnostic, Double value) {
		Instant now = DiagnosticResult.now();
		return new DiagnosticNumericResult(diagnostic.id(), diagnostic.name(), now, now, value);
	}

	@Override
	public DiagnosticNumericResult withDates(Instant newCreationDate, Instant newLastCheckDate) {
		return new DiagnosticNumericResult(this.diagnosticId, this.diagnosticName, newCreationDate, newLastCheckDate, this.number);
	}

	@Override
	public String getAsText() {
		return this.number == null ? "" : this.number.toString();
	}

	@Override
	public Object getData() {
		return this.number;
	}

	@Override
	public boolean valueEquals(DiagnosticResult other) {
		return other instanceof DiagnosticNumericResult o && Objects.equals(this.number, o.number);
	}
}
