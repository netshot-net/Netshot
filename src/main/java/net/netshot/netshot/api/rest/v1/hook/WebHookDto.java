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

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeName;

import io.swagger.v3.oas.annotations.media.Schema;
import net.netshot.netshot.api.rest.v1.Brief;
import net.netshot.netshot.hook.WebHook.Action;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * A hook which posts a description of the event to a URL.
 *
 * @param id the ID
 * @param url the path of the hook
 * @param display the name
 * @param version the version, incremented on each change (the ETag)
 * @param changeDate the date of the last change
 * @param type the kind of hook
 * @param name the name
 * @param enabled whether the hook is enabled
 * @param triggers the events which trigger the hook
 * @param action the way the payload is posted
 * @param targetUrl the URL called (HTTP or HTTPS); it is returned as it is stored, so it should not carry credentials
 * @param httpsCaTrustMode how the certificate of the target is trusted
 * @param httpsCustomCaCertificate the PEM trust anchors, for the {@code CUSTOM_CA} trust mode
 */
@Schema(name = "WebHook", description = "Hook calling a URL")
@JsonTypeName("WEB")
public record WebHookDto(
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long id,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String url,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) String display,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) long version,
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) Instant changeDate,
	@Brief String type,
	@Brief String name,
	@Brief boolean enabled,
	List<HookTriggerDto> triggers,
	Action action,
	@Brief String targetUrl,
	@Brief HttpsCaTrustMode httpsCaTrustMode,
	String httpsCustomCaCertificate) implements HookDto {
}
