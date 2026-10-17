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

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

/**
 * Hook base class.
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Hook {

	/** Unique ID of the webhook. **/
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	private long id;

	/** Name of the webhook. */
	@Getter(onMethod = @__({
		@Column(unique = true)
	}))
	@Setter
	private String name;

	/** Whether the hook is enabled. */
	@Getter
	@Setter
	private boolean enabled = true;

	/** List of associated triggers. */
	@Getter(onMethod = @__({
		@OneToMany(mappedBy = "hook", orphanRemoval = true, fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	}))
	@Setter
	private Set<HookTrigger> triggers = new HashSet<>();

	@Override
	public String toString() {
		return "Hook [id=" + id + ", name=" + name + "]";
	}
}
