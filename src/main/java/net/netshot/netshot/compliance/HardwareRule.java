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

import java.util.Date;
import java.util.regex.PatternSyntaxException;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.group.DeviceGroup;

/**
 * The Class HardwareRule.
 */
@Entity
public class HardwareRule {

	/** The id. */
	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	private long id;

	/** The device class. */
	@Getter
	@Setter
	private String driver;

	/** The target group. */
	@Getter(onMethod = @__({
		@ManyToOne,
		@OnDelete(action = OnDeleteAction.SET_NULL)
	}))
	@Setter
	private DeviceGroup targetGroup;

	/** The family. */
	@Getter
	@Setter
	private String family = "";

	/** The family reg exp. */
	@Getter
	@Setter
	private boolean familyRegExp;

	/** The part number. */
	@Getter
	@Setter
	private String partNumber;

	/** The part number reg exp. */
	@Getter
	@Setter
	private boolean partNumberRegExp;

	/** The end of sale. */
	@Getter
	@Setter
	private Date endOfSale;

	/** The end of life. */
	@Getter
	@Setter
	private Date endOfLife;

	/**
	 * Instantiates a new hardware rule.
	 */
	protected HardwareRule() {

	}

	public HardwareRule(String driver,
		DeviceGroup targetGroup, String family, boolean familyRegExp,
		String partNumber, boolean partNumberRegExp, Date endOfSale,
		Date endOfLife) {
		this.driver = driver;
		this.targetGroup = targetGroup;
		this.family = family;
		this.familyRegExp = familyRegExp;
		this.partNumber = partNumber;
		this.partNumberRegExp = partNumberRegExp;
		this.endOfSale = endOfSale;
		this.endOfLife = endOfLife;
	}

	/**
	 * Gets the device type.
	 *
	 * @return the device type
	 */
	@Transient
	public String getDeviceType() {
		DeviceDriver deviceDriver = DeviceDriver.getDriverByName(driver);
		if (deviceDriver == null) {
			return "";
		}
		return deviceDriver.getDescription();
	}

	/**
	 * Check.
	 *
	 * @param device the device
	 */
	public void check(Device device) {
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
			if (endOfLife != null && (device.getEolDate() == null || endOfLife.before(device.getEolDate()))) {
				device.setEolDate(endOfLife);
				device.setEolModule(module);
			}
			if (endOfSale != null && (device.getEosDate() == null || endOfSale.before(device.getEosDate()))) {
				device.setEosDate(endOfSale);
				device.setEosModule(module);
			}
		}
	}

	@Override
	public String toString() {
		return "Hardware compliance rule " + id;
	}
}
