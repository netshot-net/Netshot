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
package net.netshot.netshot.api.rest.v1.hook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.ReadOnlyFrom;
import net.netshot.netshot.hook.WebHook.Action;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * The definition of a Web hook.
 *
 * @param name the name (unique)
 * @param enabled whether the hook is enabled (true if absent)
 * @param triggers the events which trigger the hook (none if absent)
 * @param action the way the payload is posted
 * @param targetUrl the URL called (HTTP or HTTPS)
 * @param httpsCaTrustMode how the certificate of the target is trusted ({@code SYSTEM_TRUSTSTORE} if absent)
 * @param httpsCustomCaCertificate the PEM trust anchors, required for the {@code CUSTOM_CA} trust mode
 */
@Schema(name = "WebHookRequest", description = "Definition of a hook calling a URL")
@ReadOnlyFrom(WebHookDto.class)
@JsonTypeName("WEB")
public record WebHookRequest(
	@JsonProperty(required = true) String name,
	Boolean enabled,
	List<HookTriggerDto> triggers,
	@JsonProperty(required = true) Action action,
	@JsonProperty(required = true) String targetUrl,
	HttpsCaTrustMode httpsCaTrustMode,
	String httpsCustomCaCertificate) implements HookRequest {
}
