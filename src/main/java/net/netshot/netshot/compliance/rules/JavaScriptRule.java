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

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.Rule;

/**
 * A JavaScriptRule is a Javascript-coded script that will check the device
 * attributes, including config, and return whether the device is compliant
 * or not.
 */
@Entity
@DiscriminatorValue("JAVASCRIPT")
@Slf4j
public class JavaScriptRule extends Rule {

	/** The script. */
	@Getter(onMethod = @__({
		@Column(length = 10000000)
	}))
	@Setter
	private String script = ""
		+ "/*\n"
		+ " * Script template - to be customized.\n"
		+ " */\n"
		+ "function check(device) {\n"
		+ "    //const config = device.get(\"runningConfig\");\n"
		+ "    //const name = device.get(\"name\");\n"
		+ "    return CONFORMING;\n"
		+ "    //return NONCONFORMING;\n"
		+ "    //return NOTAPPLICABLE;\n"
		+ "}\n";

	/**
	 * Instantiates a new JavaScript rule.
	 */
	protected JavaScriptRule() {

	}

	/**
	 * Instantiates a new JavaScript rule.
	 *
	 * @param name the name
	 * @param policy the policy
	 */
	public JavaScriptRule(String name, Policy policy) {
		super(name, policy);
	}

	@Override
	public String toString() {
		return "Compliance JavaScript-based rule " + id + " (name '" + name + "')";
	}

}
