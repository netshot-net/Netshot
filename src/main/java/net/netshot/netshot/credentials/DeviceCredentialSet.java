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
package net.netshot.netshot.credentials;

import java.util.Date;
import java.util.UUID;

import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

/**
 * A credential set. Authentication data to access a device.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement()
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
	@Type(value = DeviceSnmpv1Community.class, name = "SNMP v1"),
	@Type(value = DeviceSnmpv2cCommunity.class, name = "SNMP v2"),
	@Type(value = DeviceSnmpv3Community.class, name = "SNMP v3"),
	@Type(value = DeviceSshAccount.class, name = "SSH"),
	@Type(value = DeviceSshKeyAccount.class, name = "SSH Key"),
	@Type(value = DeviceTelnetAccount.class, name = "Telnet"),
	@Type(value = DeviceHttpAccount.class, name = "HTTP")
})
public abstract class DeviceCredentialSet {

	public static String generateSpecificName() {
		return String.format("DEVICESPECIFIC-%s", UUID.randomUUID());
	}

	/** The change date. */
	@Getter
	@Setter
	protected Date changeDate;

	@Getter(onMethod = @__({
		@Version
	}))
	@Setter
	private int version;

	/** The id. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@XmlID, @Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	protected long id;

	/** The name. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class),
		@NaturalId(mutable = true)
	}))
	@Setter
	protected String name;

	/** The mgmtDomain. */
	@Getter(onMethod = @__({
		@ManyToOne,
		@OnDelete(action = OnDeleteAction.CASCADE),
		@XmlElement, @JsonView(DefaultView.class),
	}))
	@Setter
	protected Domain mgmtDomain;

	/** Device-specific credential set. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	protected boolean deviceSpecific;

	/**
	 * Instantiates a new device credential set.
	 */
	protected DeviceCredentialSet() {
		// Reserved for Hibernate
	}

	/**
	 * Instantiates a new device credential set.
	 *
	 * @param name the name
	 */
	public DeviceCredentialSet(String name) {
		this.name = name;
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
		if (!(obj instanceof DeviceCredentialSet)) {
			return false;
		}
		DeviceCredentialSet other = (DeviceCredentialSet) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		}
		else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

	/*(non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (name == null ? 0 : name.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "Device Credential Set " + id + " (name '" + name + "', type " + this.getClass().getSimpleName() + ")";
	}

}
