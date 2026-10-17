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
package net.netshot.netshot.compliance.rules;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.driver.DeviceDriver;

@Entity
@OnDelete(action = OnDeleteAction.CASCADE)
public class TextRule extends Rule {

	@Getter
	@Setter
	private String field;

	@Getter(onMethod = @__({
		@Column(length = 10000)
	}))
	@Setter
	private String context;

	@Getter(onMethod = @__({
		@Column(length = 10000000)
	}))
	private String text;

	@Getter
	@Setter
	private boolean regExp;

	@Getter
	@Setter
	private boolean invert;

	@Getter
	@Setter
	private boolean anyBlock;

	@Getter
	@Setter
	private boolean matchAll;

	@Getter
	@Setter
	private boolean normalize;

	@Getter
	@Setter
	private String deviceDriver;

	protected TextRule() {

	}

	public TextRule(String name, Policy policy) {
		super(name, policy);
	}

	public void setText(String text) {
		this.text = text;
		if (this.text != null) {
			this.text = this.text.replace("\r", "");
		}
	}

	@Transient
	public String getDeviceDriverDescription() {
		if ("".equals(deviceDriver)) {
			return "";
		}
		try {
			DeviceDriver d = DeviceDriver.getDriverByName(deviceDriver);
			return d.getDescription();
		}
		catch (Exception e) {
			return "Unknown driver";
		}
	}

	@Override
	public String toString() {
		return "Compliance text-based rule " + id + " (name '" + name + "')";
	}

}
