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

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.hook.Hook;
import net.netshot.netshot.hook.HookTrigger;
import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.platform.tls.HttpsCaTrustMode;

/**
 * Maps the hooks to their v1 representation and back.
 */
public final class HookMapper {

	/** The path of the hooks. */
	public static final String PATH = "hooks";

	private HookMapper() {
	}

	/**
	 * Maps a hook, according to its kind.
	 *
	 * @param hook the hook, with its triggers loaded (null gives null)
	 * @return the representation
	 */
	public static HookDto toDto(Hook hook) {
		if (hook == null) {
			return null;
		}
		return switch (hook) {
			case WebHook web -> new WebHookDto(web.id(), V1Links.of(PATH, web.id()), web.name(), web.version(), web.changeDate(), "WEB",
				web.name(), web.enabled(), triggers(web), web.action(), web.url(), web.httpsCaTrustMode(), web.httpsCustomCaCertificate());
		};
	}

	/**
	 * Maps a hook to the request which would define it as it is (the base of a merge patch).
	 *
	 * @param hook the hook
	 * @return the request
	 */
	public static HookRequest toRequest(Hook hook) {
		return switch (hook) {
			case WebHook web -> new WebHookRequest(web.name(), web.enabled(), triggers(web), web.action(), web.url(),
				web.httpsCaTrustMode(), web.httpsCustomCaCertificate());
		};
	}

	/**
	 * Maps a request to a hook (not stored yet) of the matching kind.
	 *
	 * @param request the request
	 * @return the hook
	 * @throws NetshotException if a trigger is invalid
	 */
	public static Hook toHook(HookRequest request) {
		return switch (request) {
			case WebHookRequest web -> new WebHook(0, 0, null, web.name(), web.enabled() == null || web.enabled(), triggers(web.triggers()),
				web.action(), web.targetUrl(),
				web.httpsCaTrustMode() == null ? HttpsCaTrustMode.SYSTEM_TRUSTSTORE : web.httpsCaTrustMode(),
				web.httpsCustomCaCertificate());
		};
	}

	/**
	 * Lists the triggers of a hook by type, then item.
	 *
	 * @param hook the hook
	 * @return the representation of the triggers
	 */
	private static List<HookTriggerDto> triggers(Hook hook) {
		if (hook.triggers() == null) {
			return List.of();
		}
		return hook.triggers().stream()
			.sorted(Comparator.comparing(HookTrigger::type, Comparator.nullsFirst(Comparator.naturalOrder()))
				.thenComparing(HookTrigger::item, Comparator.nullsFirst(Comparator.naturalOrder())))
			.map(trigger -> new HookTriggerDto(trigger.type(), trigger.item()))
			.toList();
	}

	private static Set<HookTrigger> triggers(List<HookTriggerDto> dtos) {
		Set<HookTrigger> triggers = new LinkedHashSet<>();
		if (dtos != null) {
			for (HookTriggerDto dto : dtos) {
				if (dto == null || dto.type() == null) {
					throw NetshotException.invalidParameter("triggers", "A trigger needs a type");
				}
				triggers.add(new HookTrigger(dto.type(), dto.item()));
			}
		}
		return triggers;
	}
}
