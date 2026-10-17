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

import java.util.regex.PatternSyntaxException;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.driver.AttributeDefinition.EnumAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.group.DeviceGroup;

/**
 * A software rule defines constraints that apply to the software versions.
 */
@Entity
public class SoftwareRule implements Comparable<SoftwareRule> {

	/**
	 * The Enum ConformanceLevel.
	 */
	public enum ConformanceLevel implements EnumAttribute {

		/** The gold. */
		GOLD,

		/** The silver. */
		SILVER,

		/** The bronze. */
		BRONZE,

		/** The unknown. */
		UNKNOWN
	}

	/** The id. */
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	protected long id;

	/** The priority, to determine which software rule should apply first. */
	@Getter
	@Setter
	protected double priority;

	/** The target group. */
	@Getter(onMethod = @__({
		@ManyToOne,
		@OnDelete(action = OnDeleteAction.SET_NULL)
	}))
	@Setter
	protected DeviceGroup targetGroup;

	@Getter
	@Setter
	private String driver;

	/** The family. */
	@Getter
	@Setter
	private String family = "";

	/** The family is a regular expression. */
	@Getter
	@Setter
	private boolean familyRegExp;

	/** The version. */
	@Getter
	@Setter
	private String version = "";

	/** The version is a regular expression. */
	@Getter
	@Setter
	private boolean versionRegExp;

	/** The part number. */
	@Getter
	@Setter
	private String partNumber;

	/** The part number reg exp. */
	@Getter
	@Setter
	private boolean partNumberRegExp;

	/** The level. */
	@Getter
	@Setter
	protected ConformanceLevel level = ConformanceLevel.GOLD;


	/**
	 * Instantiates a new software rule.
	 */
	protected SoftwareRule() {

	}

	public SoftwareRule(double priority, DeviceGroup targetGroup,
		String driver, String family, boolean familyRegExp,
		String version, boolean versionRegExp, String partNumber,
		boolean partNumberRegExp, ConformanceLevel level) {
		this.priority = priority;
		this.targetGroup = targetGroup;
		this.driver = driver;
		this.family = family;
		this.familyRegExp = familyRegExp;
		this.version = version;
		this.versionRegExp = versionRegExp;
		this.partNumber = partNumber;
		this.partNumberRegExp = partNumberRegExp;
		this.level = level;
	}

	/*(non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(SoftwareRule o) {
		return Double.compare(this.priority, o.priority);
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
		SoftwareRule other = (SoftwareRule) obj;
		return id == other.id;
	}

	/**
	 * Check.
	 *
	 * @param device the device
	 */
	public void check(Device device) {
		device.setSoftwareLevel(ConformanceLevel.UNKNOWN);
		if (targetGroup != null && !targetGroup.getCachedDevices().contains(device)) {
			return;
		}
		if (driver != null && !driver.equals(device.getDriver())) {
			return;
		}
		if (familyRegExp) {
			try {
				if (!device.getFamily().matches(family)) {
					return;
				}
			}
			catch (PatternSyntaxException e) {
				return;
			}
		}
		else {
			if (!"".equals(family) && !device.getFamily().equals(family)) {
				return;
			}
		}
		if (versionRegExp) {
			try {
				if (!device.getSoftwareVersion().matches(version)) {
					return;
				}
			}
			catch (PatternSyntaxException e) {
				return;
			}
		}
		else {
			if (!"".equals(version) && !device.getSoftwareVersion().equals(version)) {
				return;
			}
		}
		if (partNumber != null && !"".equals(partNumber)) {
			boolean moduleMatches = false;
			for (Module module : device.getModules()) {
				if (partNumberRegExp) {
					try {
						if (!module.getPartNumber().matches(partNumber)) {
							continue;
						}
					}
					catch (PatternSyntaxException e) {
						return;
					}
				}
				else {
					if (!"".equals(partNumber) && !module.getPartNumber().equals(partNumber)) {
						continue;
					}
				}
				moduleMatches = true;
				break;
			}
			if (!moduleMatches) {
				return;
			}
		}
		device.setSoftwareLevel(this.level);
	}

	@Override
	public String toString() {
		return "Software compliance rule " + id;
	}

}
