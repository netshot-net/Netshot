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
package net.netshot.netshot.api.rest.v0.diagnostic;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * The representation of the result of a diagnostic on a device. The {@code type} property (an
 * attribute in XML) is the type of the value. A long text is not part of the representation.
 */
@JsonView(DefaultView.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = DiagnosticResultDto.Numeric.class, name = "NUMERIC"),
	@JsonSubTypes.Type(value = DiagnosticResultDto.Text.class, name = "TEXT"),
	@JsonSubTypes.Type(value = DiagnosticResultDto.LongText.class, name = "LONGTEXT"),
	@JsonSubTypes.Type(value = DiagnosticResultDto.Binary.class, name = "BINARY"),
})
public sealed interface DiagnosticResultDto {

	/**
	 * Gets the ID of the diagnostic which created the result.
	 *
	 * @return the diagnostic ID
	 */
	long diagnosticId();

	/**
	 * A numeric result.
	 *
	 * @param creationDate when the result was first returned
	 * @param diagnosticId the ID of the diagnostic
	 * @param diagnosticName the name of the diagnostic
	 * @param lastCheckDate when the result was last returned
	 * @param number the value
	 */
	@JsonView(DefaultView.class)
	@JacksonXmlRootElement(localName = "DiagnosticNumericResult")
	record Numeric(Date creationDate, long diagnosticId, String diagnosticName, Date lastCheckDate, Double number)
		implements DiagnosticResultDto {
	}

	/**
	 * A text result.
	 *
	 * @param creationDate when the result was first returned
	 * @param diagnosticId the ID of the diagnostic
	 * @param diagnosticName the name of the diagnostic
	 * @param lastCheckDate when the result was last returned
	 * @param text the value
	 */
	@JsonView(DefaultView.class)
	@JacksonXmlRootElement(localName = "DiagnosticTextResult")
	record Text(Date creationDate, long diagnosticId, String diagnosticName, Date lastCheckDate, String text)
		implements DiagnosticResultDto {
	}

	/**
	 * A long text result: the value is not part of the representation.
	 *
	 * @param creationDate when the result was first returned
	 * @param diagnosticId the ID of the diagnostic
	 * @param diagnosticName the name of the diagnostic
	 * @param lastCheckDate when the result was last returned
	 */
	@JsonView(DefaultView.class)
	@JacksonXmlRootElement(localName = "DiagnosticLongTextResult")
	record LongText(Date creationDate, long diagnosticId, String diagnosticName, Date lastCheckDate)
		implements DiagnosticResultDto {
	}

	/**
	 * A boolean result.
	 *
	 * @param assumption the value
	 * @param creationDate when the result was first returned
	 * @param diagnosticId the ID of the diagnostic
	 * @param diagnosticName the name of the diagnostic
	 * @param lastCheckDate when the result was last returned
	 */
	@JsonView(DefaultView.class)
	@JacksonXmlRootElement(localName = "DiagnosticBinaryResult")
	record Binary(Boolean assumption, Date creationDate, long diagnosticId, String diagnosticName, Date lastCheckDate)
		implements DiagnosticResultDto {
	}
}
