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
package net.netshot.netshot.api.rest.v1;

import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An error, as problem details (RFC 9457), with the Netshot error code.
 *
 * @param type the URI of the kind of problem ({@code urn:netshot:error:duplicate-domain}..., or {@code about:blank}
 *        for a plain HTTP error)
 * @param title the short description of the kind of problem
 * @param status the HTTP status
 * @param detail the description of this occurrence of the problem
 * @param instance the path of the request
 * @param code the name of the Netshot error code (null for a plain HTTP error)
 * @param errorCode the numeric Netshot error code (null for a plain HTTP error)
 * @param errors the details by parameter or property, if any
 */
@JacksonXmlRootElement(localName = "problem")
@Schema(name = "Problem", description = "Error (RFC 9457 problem details)")
public record ProblemDto(
	String type,
	String title,
	int status,
	String detail,
	String instance,
	String code,
	Integer errorCode,
	List<FieldErrorDto> errors) {

	/**
	 * The error about a parameter or a property of the request.
	 *
	 * @param field the name of the query parameter, or the path of the property in the request body
	 * @param message the description of the error
	 */
	@Schema(name = "FieldError")
	public record FieldErrorDto(String field, String message) {
	}
}
