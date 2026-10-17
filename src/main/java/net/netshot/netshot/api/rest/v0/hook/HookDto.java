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
package net.netshot.netshot.api.rest.v0.hook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import net.netshot.netshot.hook.WebHook.Action;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * The representation of a hook, according to its kind (the {@code type} property). This is the
 * hook <em>definition</em> managed through the REST API, not the payload the hook sends.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = HookDto.Web.class, name = "Web"),
})
public sealed interface HookDto {

	/**
	 * Gets the hook ID.
	 *
	 * @return the ID
	 */
	long id();

	/**
	 * Gets the name of the hook.
	 *
	 * @return the name
	 */
	String name();

	/**
	 * Gets the triggers of the hook.
	 *
	 * @return the triggers (may be null)
	 */
	List<HookTriggerDto> triggers();

	/**
	 * A Web hook, which calls a URL.
	 *
	 * @param id the hook ID
	 * @param name the name
	 * @param enabled whether the hook is enabled
	 * @param triggers the events which trigger the hook
	 * @param action the way the payload is posted
	 * @param url the target URL
	 * @param httpsCaTrustMode how the certificate of the target is trusted
	 * @param httpsCustomCaCertificate the PEM trust anchors, for the custom CA mode
	 */
	@JacksonXmlRootElement(localName = "WebHook")
	record Web(
		long id,
		String name,
		boolean enabled,
		List<HookTriggerDto> triggers,
		Action action,
		String url,
		HttpsCaTrustMode httpsCaTrustMode,
		String httpsCustomCaCertificate) implements HookDto {
	}
}
