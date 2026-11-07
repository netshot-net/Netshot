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
package net.netshot.netshot.api.rest.v1.config;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.api.rest.v1.Ref;
import net.netshot.netshot.api.rest.v1.device.DeviceDto;

/**
 * A configuration of a device: what a snapshot gathered. Its large items (the long texts and the files) are not
 * part of the representation: the attributes only tell their name (and the size of a file) and the path to
 * download them ({@code /configs/{id}/items/{item}}).
 *
 * @param id the ID
 * @param url the path of the configuration
 * @param display the display name (the ID and the date)
 * @param version the version
 * @param device the device
 * @param changeDate when the configuration was gathered
 * @param author the author of the last change of the configuration, as reported by the device
 * @param customHash the hash set by the driver to tell whether the configuration has changed, if any
 * @param attributes the attributes (by name)
 */
@Schema(name = "Config", description = "Device configuration")
public record ConfigDto(
	long id,
	String url,
	String display,
	Long version,
	@Brief @Ref DeviceDto device,
	@Brief Instant changeDate,
	@Brief String author,
	String customHash,
	List<ConfigAttributeDto> attributes) {

	/** The properties a configuration without its attributes (the light list query) fills. */
	public static final Set<String> SUMMARY_PROPERTIES = Set.of("id", "url", "display", "version", "device", "changeDate",
		"author", "customHash");
}
