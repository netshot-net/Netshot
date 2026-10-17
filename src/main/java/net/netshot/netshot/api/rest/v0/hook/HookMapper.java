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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;

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
		Hook unproxied = (Hook) Hibernate.unproxy(hook);
		if (unproxied instanceof WebHook webHook) {
			List<HookTriggerDto> triggers = null;
			if (webHook.getTriggers() != null) {
				triggers = new ArrayList<>(webHook.getTriggers().size());
				for (HookTrigger trigger : webHook.getTriggers()) {
					triggers.add(new HookTriggerDto(trigger.getType(), trigger.getItem()));
				}
			}
			return new HookDto.Web(webHook.getId(), webHook.getName(), webHook.isEnabled(), triggers, webHook.getAction(),
				webHook.getUrl(), webHook.getHttpsCaTrustMode(), webHook.getHttpsCustomCaCertificate());
		}
		throw new IllegalArgumentException("Unknown kind of hook: " + unproxied.getClass().getName());
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
			WebHook hook = new WebHook();
			hook.setId(web.getId());
			hook.setName(web.getName());
			hook.setEnabled(web.isEnabled());
			hook.setTriggers(toTriggers(web.getTriggers()));
			hook.setAction(web.getAction());
			hook.setUrl(web.getUrl());
			hook.setHttpsCaTrustMode(web.getHttpsCaTrustMode());
			hook.setHttpsCustomCaCertificate(web.getHttpsCustomCaCertificate());
			return hook;
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
			HookTrigger trigger = new HookTrigger();
			trigger.setType(dto.type());
			trigger.setItem(dto.item());
			triggers.add(trigger);
		}
		return triggers;
	}
}
