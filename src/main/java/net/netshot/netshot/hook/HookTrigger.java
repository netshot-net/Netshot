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
package net.netshot.netshot.hook;

import java.io.Serializable;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * A trigger for a hook.
 */
@Entity
public class HookTrigger implements Serializable {

	private static final long serialVersionUID = -1209506185538121214L;

	/**
	 * Types of trigger.
	 */
	public enum TriggerType {
		/** Trigger the hook right at the end of a task. */
		POST_TASK,
	}

	/** Type of trigger. */
	@Getter(onMethod = @__({
		@Id
	}))
	@Setter
	private TriggerType type;

	/** Item to be matched. */
	@Getter(onMethod = @__({
		@Id
	}))
	@Setter
	private String item;

	/** Associated hook. */
	@Getter(onMethod = @__({
		@Id,
		@ManyToOne,
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	private Hook hook;


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (item == null ? 0 : item.hashCode());
		result = prime * result + (type == null ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		HookTrigger other = (HookTrigger) obj;
		if (item == null) {
			if (other.item != null) {
				return false;
			}
		}
		else if (!item.equals(other.item)) {
			return false;
		}
		return type == other.type;
	}

}
