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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;

import net.netshot.netshot.hook.HookTrigger.TriggerType;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * An event which triggers a hook. The same shape is written and read (a {@code hook} property,
 * which the entity has, is ignored when read).
 *
 * @param type the type of trigger
 * @param item the item to be matched (e.g. the name of a task type)
 */
@JsonView(DefaultView.class)
@JsonIgnoreProperties({"hook"})
public record HookTriggerDto(TriggerType type, String item) {
}
