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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

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
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * Hook base class.
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@Type(value = WebHook.class, name = "Web")
})
public abstract class Hook {

	/** Unique ID of the webhook. **/
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private long id;

	/** Name of the webhook. */
	@Getter(onMethod = @__({
		@Column(unique = true),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String name;

	/** Whether the hook is enabled. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private boolean enabled = true;

	/** List of associated triggers. */
	@Getter(onMethod = @__({
		@OneToMany(mappedBy = "hook", orphanRemoval = true, fetch = FetchType.EAGER, cascade = CascadeType.ALL),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private Set<HookTrigger> triggers = new HashSet<>();

	/**
	 * Execute the hook.
	 * @param data Associated data (such a task)
	 * @return the result of the execution
	 */
	public abstract String execute(Object data) throws Exception;

	@Override
	public String toString() {
		return "Hook [id=" + id + ", name=" + name + "]";
	}
}
