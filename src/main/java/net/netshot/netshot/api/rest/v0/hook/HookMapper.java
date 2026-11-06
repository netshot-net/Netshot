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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import net.netshot.netshot.hook.Hook;
import net.netshot.netshot.hook.HookTrigger;
import net.netshot.netshot.hook.WebHook;

/**
 * Maps the hooks between the entities and their REST representation.
 * All the methods are pure functions.
 */
public final class HookMapper {

	private HookMapper() {
		// Static helpers only
	}

	/**
	 * Maps a hook to its representation, according to its kind.
	 *
	 * @param hook the hook, with its triggers loaded
	 * @return the DTO
	 * @throws IllegalArgumentException if the kind of hook is unknown
	 */
	public static HookDto toDto(Hook hook) {
		List<HookTriggerDto> triggers = null;
		if (hook.triggers() != null) {
			// The triggers are a set: they are listed by type, then item
			triggers = new ArrayList<>(hook.triggers().stream()
				.sorted(Comparator.comparing(HookTrigger::type, Comparator.nullsFirst(Comparator.naturalOrder()))
					.thenComparing(HookTrigger::item, Comparator.nullsFirst(Comparator.naturalOrder())))
				.map(trigger -> new HookTriggerDto(trigger.type(), trigger.item()))
				.toList());
		}
		return switch (hook) {
			case WebHook webHook -> new HookDto.Web(webHook.id(), webHook.name(), webHook.enabled(), triggers, webHook.action(),
				webHook.url(), webHook.httpsCaTrustMode(), webHook.httpsCustomCaCertificate());
		};
	}

	/**
	 * Maps hooks to their representation.
	 *
	 * @param hooks the hooks, with their triggers loaded
	 * @return the DTOs, in the same order
	 */
	public static List<HookDto> toDtos(List<Hook> hooks) {
		List<HookDto> dtos = new ArrayList<>(hooks.size());
		hooks.forEach(hook -> dtos.add(toDto(hook)));
		if (hooks.getClass() != ArrayList.class) {
			// Quirk kept: a paginated query with a collection fetch is paginated in memory and answers a
			// sub list, which XML publishes as <SubList> instead of <ArrayList>
			return dtos.subList(0, dtos.size());
		}
		return dtos;
	}

	/**
	 * Maps a request to a transient hook of the matching kind, holding what the client sent.
	 *
	 * @param request the request
	 * @return the hook, not persisted
	 */
	public static Hook toEntity(HookRequest request) {
		if (request instanceof HookRequest.Web web) {
			return new WebHook(web.getId(), 0, null, web.getName(), web.isEnabled(), toTriggers(web.getTriggers()), web.getAction(),
				web.getUrl(), web.getHttpsCaTrustMode(), web.getHttpsCustomCaCertificate());
		}
		throw new IllegalArgumentException("Unknown kind of hook request: " + request.getClass().getName());
	}

	private static Set<HookTrigger> toTriggers(List<HookTriggerDto> dtos) {
		if (dtos == null) {
			return null;
		}
		Set<HookTrigger> triggers = new HashSet<>();
		for (HookTriggerDto dto : dtos) {
			if (dto == null) {
				// A null trigger is accepted here, as it always was, and fails later
				triggers.add(null);
				continue;
			}
			triggers.add(new HookTrigger(dto.type(), dto.item()));
		}
		return triggers;
	}
}
