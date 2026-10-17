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

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.driver.AttributeDefinition.AttributeType;
import net.netshot.netshot.inventory.group.DeviceGroup;

/**
 * This is a Javascript-based diagnostic. Declare the diagnostic type along with
 * a script.
 * 
 * @author sylvain.cadilhac
 */
@Entity
@OnDelete(action = OnDeleteAction.CASCADE)
public class JavaScriptDiagnostic extends Diagnostic {

	/**
	 * The JS script to execute on the device.
	 */
	@Getter(onMethod = @__({
		@Column(length = 10000000)
	}))
	@Setter
	private String script;

	/**
	 * Empty constructor. For Hibernate.
	 */
	protected JavaScriptDiagnostic() {
	}

	/**
	 * Instantiates a new diagnostic.
	 * 
	 * @param name
	 *                      The name
	 * @param enabled
	 *                      True to enable the diagnostic
	 * @param targetGroup
	 *                      The group of devices the diagnostic applies to
	 * @param resultType
	 *                      The type of result expected by this diagnostic
	 * @param script
	 *                      The Javascript script
	 */
	public JavaScriptDiagnostic(String name, boolean enabled, DeviceGroup targetGroup, AttributeType resultType,
		String script) {
		super(name, enabled, targetGroup, resultType);
		this.script = script;
	}

}
