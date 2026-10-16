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
package net.netshot.netshot.api.rest.v0.report;

import java.io.IOException;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;

import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * The number of devices reaching a hardware support milestone (end of sale or end of life) on a
 * given day.
 *
 * <p>The {@code type} tells which milestone. Its values are the type ids the former polymorphic
 * classes ({@code RestService$RsHardwareSupportEoSStat}...) were published with, which clients
 * rely on: in JSON a property, in XML an attribute, and in XML the {@code $} is {@code ..}
 * (what Jackson's {@code MINIMAL_CLASS} type ids became in XML). Jackson's own type ids can't
 * produce both forms (a {@code NAME} id is written the same way in the two formats), hence an
 * explicit property.</p>
 *
 * @param type the milestone: {@link #END_OF_SALE} or {@link #END_OF_LIFE}
 * @param eoxDate the date of the milestone (may be null)
 * @param deviceCount the number of devices
 */
@JsonView(DefaultView.class)
public record HardwareSupportStatDto(
	@JacksonXmlProperty(isAttribute = true) @JsonSerialize(using = HardwareSupportStatDto.TypeIdSerializer.class) String type,
	Date eoxDate,
	long deviceCount) {

	/** The type of the end-of-sale counts. */
	public static final String END_OF_SALE = ".RestService$RsHardwareSupportEoSStat";

	/** The type of the end-of-life counts. */
	public static final String END_OF_LIFE = ".RestService$RsHardwareSupportEoLStat";

	/** Writes the type id as is in JSON, and with two dots instead of the dollar in XML. */
	public static class TypeIdSerializer extends StdSerializer<String> {

		private static final long serialVersionUID = 1L;

		/** Instantiates the serializer. */
		public TypeIdSerializer() {
			super(String.class);
		}

		@Override
		public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeString(gen instanceof ToXmlGenerator ? value.replace("$", "..") : value);
		}
	}
}
