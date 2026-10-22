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
 * A PythonRule is a Python-coded script that will check the device attributes,
 * including config, and return whether the device is compliant or not.
 */
@Entity
@DiscriminatorValue("PYTHON")
@Slf4j
public class PythonRule extends Rule {

	/** The default example script. */
	@Getter(onMethod = @__({
		@Column(length = 10000000)
	}))
	@Setter
	private String script = ""
		+ "# Script template - to be customized\n"
		+ "def check(device):\n"
		+ "  ## Grab some data:\n"
		+ "  #  config = device.get('running_config')\n"
		+ "  #  name = device.get('name')\n"
		+ "  ## Some additional checks here...\n"
		+ "  ## debug('device name = %s' % name)\n"
		+ "  return result_option.CONFORMING\n"
		+ "  # return {'result': result_option.NONCONFORMING, 'comment': 'Why it is not fine'}\n"
		+ "  # return result_option.NOTAPPLICABLE\n";

	/**
	 * Instantiates a new Python rule.
	 */
	protected PythonRule() {

	}

	/**
	 * Instantiates a new Python rule.
	 *
	 * @param name
	 *                   the name
	 * @param policy
	 *                   the policy
	 */
	public PythonRule(String name, Policy policy) {
		super(name, policy);
	}

	@Override
	public String toString() {
		return "Compliance Python-based rule " + id + " (name '" + name + "')";
	}

}
