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
package net.netshot.netshot.api.rest.v0.device;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.xml.bind.annotation.XmlRootElement;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * The request to add a device. A property absent from the request has its default value (see the
 * canonical constructor): automatic discovery, empty address, name and device type, no domain.
 *
 * @param autoDiscover whether to discover the type of the device (the device is then
 *        created if the discovery succeeds) instead of creating it immediately
 * @param autoDiscoveryTask not used
 * @param ipAddress the management address (IP address or host name)
 * @param domainId the ID of the management domain
 * @param name not used (the name of the device is read from the device)
 * @param deviceType the name of the device driver (not used when auto discovering)
 * @param accesses the per-access settings
 * @param options the values of the driver options
 * @param comments the comments
 * @param tags the tags
 */
@JsonView(DefaultView.class)
// Transitional: with @XmlRootElement, Jersey reads a XML body through its JAXB provider, which fails
// (there is no JAXB runtime), as it always did for this request (see the API contract goldens).
// Without it, the XML body would get read by Jackson, which is a fix of the API to decide on.
@XmlRootElement(name = "device")
public record NewDeviceRequest(
	@JsonDeserialize(using = NewDeviceRequest.AutoDiscoverDeserializer.class) Boolean autoDiscover,
	long autoDiscoveryTask,
	String ipAddress,
	Long domainId,
	String name,
	String deviceType,
	List<DeviceAccessDto> accesses,
	Map<String, Object> options,
	String comments,
	List<String> tags) {

	/**
	 * Applies the default values.
	 */
	public NewDeviceRequest {
		autoDiscover = autoDiscover == null || autoDiscover;
		ipAddress = ipAddress == null ? "" : ipAddress;
		domainId = domainId == null ? -1L : domainId;
		name = name == null ? "" : name;
		deviceType = deviceType == null ? "" : deviceType;
	}

	/**
	 * Reads the {@code autoDiscover} flag: true if the property is absent, but false if it is
	 * explicitly null, as the request used to be read.
	 */
	public static final class AutoDiscoverDeserializer extends JsonDeserializer<Boolean> {

		@Override
		public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
			return parser.getValueAsBoolean();
		}

		@Override
		public Boolean getNullValue(DeserializationContext context) {
			return Boolean.FALSE;
		}

		@Override
		public Object getAbsentValue(DeserializationContext context) {
			return Boolean.TRUE;
		}
	}
}
