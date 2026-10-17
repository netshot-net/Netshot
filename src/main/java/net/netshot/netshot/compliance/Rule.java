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

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.rules.JavaScriptRule;
import net.netshot.netshot.compliance.rules.PythonRule;
import net.netshot.netshot.compliance.rules.TextRule;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;


/**
 * A rule defines a number of constraints that a device should comply with.
 * A concrete implementation is the Javascript-based script rule.
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Table(uniqueConstraints = {@UniqueConstraint(columnNames = {"policy", "name"})})
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@Slf4j
public abstract class Rule {

	/** The set of real rule types. */
	private static final Set<Class<? extends Rule>> RULE_CLASSES;

	static {
		RULE_CLASSES = new HashSet<>();
		RULE_CLASSES.add(JavaScriptRule.class);
		RULE_CLASSES.add(PythonRule.class);
		RULE_CLASSES.add(TextRule.class);
	}

	/**
	 * Gets the rule classes.
	 *
	 * @return the rule classes
	 */
	public static final Set<Class<? extends Rule>> getRuleClasses() {
		return RULE_CLASSES;
	}

	/** The enabled. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected boolean enabled;

	/** The id. */
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY),
		@XmlAttribute, @JsonView(DefaultView.class)
	}))
	@Setter
	protected long id;

	/** The policy. */
	@Getter(onMethod = @__({
		@ManyToOne,
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	protected Policy policy;

	/** The name. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected String name = "";

	/** The exemptions. */
	@Getter(onMethod = @__({
		@OneToMany(mappedBy = "key.rule", orphanRemoval = true, cascade = CascadeType.ALL)
	}))
	@Setter
	private Set<Exemption> exemptions = new HashSet<>();

	/**
	 * Instantiates a new rule.
	 */
	protected Rule() {

	}

	/**
	 * Instantiates a new rule.
	 *
	 * @param name the name
	 * @param policy the policy
	 */
	public Rule(String name, Policy policy) {
		this.name = name;
		this.policy = policy;
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
		Rule other = (Rule) obj;
		return id == other.id;
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

	/**
	 * Delete exemption.
	 *
	 * @param exemption the exemption
	 */
	public void deleteExemption(Exemption exemption) {
		this.exemptions.remove(exemption);
	}

	/**
	 * Adds the exemption.
	 *
	 * @param exemption the exemption
	 */
	public void addExemption(Exemption exemption) {
		this.exemptions.add(exemption);
	}

	/**
	 * Clear exemptions.
	 */
	public void clearExemptions() {
		this.exemptions.clear();
	}

	@Override
	public String toString() {
		return "Compliance rule " + id + " (name '" + name + "')";
	}

}
