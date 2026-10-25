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
package net.netshot.netshot.inventory.diagnostic;

import java.time.Instant;
import java.util.Objects;


/**
 * A text diagnostic result.
 *
 * @param diagnosticId the ID of the diagnostic
 * @param diagnosticName the name of the diagnostic
 * @param creationDate when the diagnostic first gave this value
 * @param lastCheckDate when the diagnostic last gave this value
 * @param text the value
 */
public record DiagnosticTextResult(Long diagnosticId, String diagnosticName, Instant creationDate, Instant lastCheckDate, String text)
	implements DiagnosticResult {

	/**
	 * Builds a new result of a diagnostic (created and checked now).
	 *
	 * @param diagnosticId the ID of the diagnostic
	 * @param diagnosticName the name of the diagnostic
	 * @param value the value
	 * @return the result
	 */
	public static DiagnosticTextResult of(long diagnosticId, String diagnosticName, String value) {
		Instant now = DiagnosticResult.now();
		return new DiagnosticTextResult(diagnosticId, diagnosticName, now, now, value);
	}

	@Override
	public DiagnosticTextResult withDates(Instant newCreationDate, Instant newLastCheckDate) {
		return new DiagnosticTextResult(this.diagnosticId, this.diagnosticName, newCreationDate, newLastCheckDate, this.text);
	}

	@Override
	public String getAsText() {
		return this.text == null ? "" : this.text;
	}

	@Override
	public Object getData() {
		return this.text;
	}

	@Override
	public boolean valueEquals(DiagnosticResult other) {
		return other instanceof DiagnosticTextResult o && Objects.equals(this.text, o.text);
	}
}
