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
package net.netshot.netshot.compliance;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.group.DeviceGroup;

/**
 * A policy is a set of rules, applied to a group of devices.
 */
@Entity
public class Policy {

	/** The id. */
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	private long id;

	/** The name. */
	@Getter(onMethod = @__({
		@NaturalId(mutable = true)
	}))
	@Setter
	private String name;

	/** The rules. */
	@Getter(onMethod = @__({
		@OneToMany(mappedBy = "policy", cascade = CascadeType.ALL)
	}))
	@Setter
	private Set<Rule> rules = new HashSet<>();

	/** The target group. */
	@Getter(onMethod = @__({
		@ManyToMany(),
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	private Set<DeviceGroup> targetGroups;

	/**
	 * Instantiates a new policy.
	 */
	protected Policy() {
	}

	/**
	 * Instantiates a new policy.
	 *
	 * @param name the name
	 * @param deviceGroups the device groups
	 */
	public Policy(String name, Set<DeviceGroup> deviceGroups) {
		this.name = name;
		this.targetGroups = deviceGroups;
	}

	/**
	 * Adds the rule.
	 *
	 * @param rule the rule
	 */
	public void addRule(Rule rule) {
		rules.add(rule);
		rule.setPolicy(this);
	}

	/*(non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (id ^ (id >>> 32));
		return result;
	}

	/*(non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
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
		Policy other = (Policy) obj;
		return id == other.id;
	}

	@Override
	public String toString() {
		return "Compliance policy " + id + " (name '" + name + "')";
	}

}
