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
package net.netshot.netshot.api.rest.v1.export;

import java.util.Locale;

import jakarta.ws.rs.core.MediaType;

/**
 * The spreadsheet formats a list can be exported in, instead of JSON, XML or YAML (see {@link ListExport}).
 */
public enum ExportFormat {

	/** Comma separated values (RFC 4180). */
	CSV(ExportFormat.TEXT_CSV, "csv"),

	/** An Excel workbook. */
	XLSX(ExportFormat.APPLICATION_XLSX, "xlsx");

	/** The media type of the CSV format. */
	public static final String TEXT_CSV = "text/csv";

	/** The media type of the Excel format. */
	public static final String APPLICATION_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private final String mediaType;
	private final String extension;

	ExportFormat(String mediaType, String extension) {
		this.mediaType = mediaType;
		this.extension = extension;
	}

	/**
	 * Gets the media type of the format.
	 *
	 * @return the media type
	 */
	public String mediaType() {
		return this.mediaType;
	}

	/**
	 * Gets the extension of the files in this format.
	 *
	 * @return the extension, without dot
	 */
	public String extension() {
		return this.extension;
	}

	/**
	 * Finds the format of a media type (the exact type and subtype: a wildcard never selects an export).
	 *
	 * @param mediaType the media type
	 * @return the format, or null if the media type isn't one of the export formats
	 */
	public static ExportFormat of(MediaType mediaType) {
		for (ExportFormat format : values()) {
			MediaType candidate = MediaType.valueOf(format.mediaType);
			if (candidate.getType().equalsIgnoreCase(mediaType.getType()) && candidate.getSubtype().equalsIgnoreCase(mediaType.getSubtype())) {
				return format;
			}
		}
		return null;
	}

	/**
	 * Finds a format by its name.
	 *
	 * @param name the name ({@code csv} or {@code xlsx})
	 * @return the format, or null
	 */
	public static ExportFormat ofName(String name) {
		if (name == null) {
			return null;
		}
		for (ExportFormat format : values()) {
			if (format.extension.equals(name.toLowerCase(Locale.ROOT))) {
				return format;
			}
		}
		return null;
	}
}
