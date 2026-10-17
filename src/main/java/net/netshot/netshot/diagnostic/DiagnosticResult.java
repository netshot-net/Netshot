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

import java.util.Date;
import java.util.Objects;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.device.Device;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.CHAR)
@DiscriminatorValue("A")
@FilterDef(name = "lightDiagAttributesOnly", defaultCondition = "type <> 'T'")
public abstract class DiagnosticResult {

	/** Unique ID of the diagnostic result. **/
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	protected long id;

	/** The date this result was created on. **/
	@Getter
	@Setter
	private Date creationDate = new Date();

	/** The last date this result was returned. **/
	@Getter
	@Setter
	private Date lastCheckDate = new Date();

	/** The diagnostic which created that result. **/
	@Getter(onMethod = @__({
		@ManyToOne(fetch = FetchType.LAZY),
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	private Diagnostic diagnostic;

	/** The device which created that result. **/
	@Getter(onMethod = @__({
		@ManyToOne,
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	private Device device;

	/**
	 * Instantiates a new diagnostic result (for Hibernate).
	 */
	protected DiagnosticResult() {
	}

	/**
	 * Instantiates a new diagnostic result.
	 * @param device The device
	 * @param diagnostic The diagnostic
	 */
	public DiagnosticResult(Device device, Diagnostic diagnostic) {
		this.device = device;
		this.diagnostic = diagnostic;
	}

	/**
	 * Gets the result value as text.
	 * @return the result value as text
	 */
	@Transient
	public abstract String getAsText();


	/**
	 * Gets the result value as an object.
	 * @return the result value as object
	 */
	@Transient
	public abstract Object getData();

	/**
	 * Gets the name of the associated diagnostic. To be sent along the result via REST.
	 * @return the name of the diagnostic.
	 */
	@Transient
	public String getDiagnosticName() {
		return this.getDiagnostic().getName();
	}

	/**
	 * Gets the ID of the associated diagnostic. To be sent along the result via REST.
	 * @return the id of the diagnostic.
	 */
	@Transient
	public Long getDiagnosticId() {
		return this.getDiagnostic().getId();
	}

	public abstract boolean valueEquals(DiagnosticResult obj);

	@Override
	public int hashCode() {
		return Objects.hash(diagnostic, device);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof DiagnosticResult)) {
			return false;
		}
		DiagnosticResult other = (DiagnosticResult) obj;
		return Objects.equals(diagnostic, other.diagnostic)
			&& Objects.equals(device, other.device);
	}

}
