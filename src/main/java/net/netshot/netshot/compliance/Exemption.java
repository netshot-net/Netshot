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

import java.io.Serializable;
import java.util.Date;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.device.Device;


/**
 * The Exemption class means the given device is exempted from the given
 * rule, until the expiration date/time.
 */
@Entity
public class Exemption {

	/**
	 * The Class Key.
	 */
	@Embeddable
	public static class Key implements Serializable {

		/** The Constant serialVersionUID. */
		private static final long serialVersionUID = 1684160749155998836L;

		/** The rule. */
		@Getter(onMethod = @__({
			@ManyToOne,
			@OnDelete(action = OnDeleteAction.CASCADE)
		}))
		@Setter
		private Rule rule;

		/** The device. */
		@Getter(onMethod = @__({
			@ManyToOne,
			@OnDelete(action = OnDeleteAction.CASCADE)
		}))
		@Setter
		private Device device;

		/**
		 * Instantiates a new key.
		 */
		protected Key() {

		}

		/**
		 * Instantiates a new key.
		 *
		 * @param rule the rule
		 * @param device the device
		 */
		public Key(Rule rule, Device device) {
			this.rule = rule;
			this.device = device;
		}

		/*(non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (device == null ? 0 : device.hashCode());
			result = prime * result + (rule == null ? 0 : rule.hashCode());
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
			Key other = (Key) obj;
			if (device == null) {
				if (other.device != null) {
					return false;
				}
			}
			else if (!device.equals(other.device)) {
				return false;
			}
			if (rule == null) {
				if (other.rule != null) {
					return false;
				}
			}
			else if (!rule.equals(other.rule)) {
				return false;
			}
			return true;
		}

	}

	/** The key. */
	@Getter(onMethod = @__({
		@EmbeddedId
	}))
	@Setter
	private Key key = new Key();

	/** The expiration date. */
	@Getter
	@Setter
	private Date expirationDate;

	/**
	 * Instantiates a new exemption.
	 */
	protected Exemption() {
	}

	/**
	 * Instantiates a new exemption.
	 *
	 * @param rule the rule
	 * @param device the device
	 * @param expiration the expiration
	 */
	public Exemption(Rule rule, Device device, Date expiration) {
		this.key.setRule(rule);
		this.key.setDevice(device);
		this.expirationDate = expiration;
	}

	/**
	 * Gets the rule.
	 *
	 * @return the rule
	 */
	@Transient
	public Rule getRule() {
		return this.key.getRule();
	}

	/**
	 * Sets the rule.
	 *
	 * @param rule the new rule
	 */
	public void setRule(Rule rule) {
		this.key.setRule(rule);
	}

	/**
	 * Gets the device.
	 *
	 * @return the device
	 */
	@Transient
	public Device getDevice() {
		return this.key.getDevice();
	}

	/**
	 * Sets the device.
	 *
	 * @param device the new device
	 */
	public void setDevice(Device device) {
		this.key.setDevice(device);
	}

	/*(non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (key == null ? 0 : key.hashCode());
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
		Exemption other = (Exemption) obj;
		if (key == null) {
			if (other.key != null) {
				return false;
			}
		}
		else if (!key.equals(other.key)) {
			return false;
		}
		return true;
	}

}
