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
package net.netshot.netshot.diagnostic;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.group.DeviceGroup;

/**
 * A diagnostic describes how to get some data from a group of devices, and to store
 * an history of this data.
 * @author sylvain.cadilhac
 *
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", length = 16)
public abstract class Diagnostic {

	/** The set of real rule types. */
	private static final Set<Class<? extends Diagnostic>> DIAGNOSTIC_CLASSES;

	static {
		DIAGNOSTIC_CLASSES = new HashSet<>();
		DIAGNOSTIC_CLASSES.add(SimpleDiagnostic.class);
		DIAGNOSTIC_CLASSES.add(JavaScriptDiagnostic.class);
		DIAGNOSTIC_CLASSES.add(PythonDiagnostic.class);
	}

	/**
	 * Gets the concrete implementations of Diagnostic.
	 *
	 * @return the Diagnostic classes
	 */
	public static final Set<Class<? extends Diagnostic>> getDiagnosticClasses() {
		return DIAGNOSTIC_CLASSES;
	}

	/** The id. */
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	private long id;

	/** The name of the diagnostic. */
	@Getter(onMethod = @__({
		@NaturalId(mutable = true)
	}))
	@Setter
	private String name;

	/** The target group. */
	@Getter(onMethod = @__({
		@ManyToOne,
		@OnDelete(action = OnDeleteAction.SET_NULL)
	}))
	@Setter
	private DeviceGroup targetGroup;

	/** Is the diagnostic enabled? */
	@Getter
	@Setter
	protected boolean enabled;

	/** The type of data returned by the diagnostic. */
	@Getter(onMethod = @__({
		@Enumerated(EnumType.STRING)
	}))
	@Setter
	protected AttributeType resultType;

	/**
	 * Instantiate a new diagnostic.
	 */
	protected Diagnostic() {
	}

	/**
	 * Instantiates a new diagnostic.
	 * @param name The name
	 * @param enabled True to enable the diagnostic
	 * @param targetGroup The group of devices the diagnostic applies to
	 * @param resultType The type of result expected by this diagnostic
	 */
	public Diagnostic(String name, boolean enabled, DeviceGroup targetGroup, AttributeType resultType) {
		this.name = name;
		this.enabled = enabled;
		this.targetGroup = targetGroup;
		this.resultType = resultType;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (id ^ (id >>> 32));
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
		if (!(obj instanceof Diagnostic)) {
			return false;
		}
		Diagnostic other = (Diagnostic) obj;
		return id == other.id;
	}

	@Override
	public String toString() {
		return "Diagnostic " + id + " (name '" + name + "', type " + this.getClass().getSimpleName() + ")";
	}

}
