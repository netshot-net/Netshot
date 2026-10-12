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

import javax.script.ScriptException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DeviceGroup;
import net.netshot.netshot.device.attribute.AttributeDefinition.AttributeType;
import net.netshot.netshot.device.script.helper.ScriptToolbox;
import net.netshot.netshot.rest.RestViews.DefaultView;

/**
 * This is a Javascript-based diagnostic. Declare the diagnostic type along with
 * a script.
 * 
 * @author sylvain.cadilhac
 */
@Entity
@OnDelete(action = OnDeleteAction.CASCADE)
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class PythonDiagnostic extends Diagnostic {

	/**
	 * The JS script to execute on the device.
	 */
	@Getter(onMethod = @__({
		@Column(length = 10000000),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String script;

	/**
	 * Empty constructor. For Hibernate.
	 */
	protected PythonDiagnostic() {
	}

	/**
	 * Instantiates a new diagnostic.
	 * 
	 * @param name = The name
	 * @param enabled = True to enable the diagnostic
	 * @param targetGroup = The group of devices the diagnostic applies to
	 * @param resultType = The type of result expected by this diagnostic
	 * @param script = The Javascript script
	 */
	public PythonDiagnostic(String name, boolean enabled, DeviceGroup targetGroup, AttributeType resultType,
		String script) {
		super(name, enabled, targetGroup, resultType);
		this.script = script;
	}

	@Override
	public Value getJsObject(Device device, Context context) throws ScriptException {
		Source diagSource = Source.newBuilder("python", this.getScript(),
			"_diag%d.py".formatted(this.getId())).buildLiteral();
		ScriptToolbox.loadPython(context);
		context.eval(diagSource);
		Value diagnose = context.getBindings("python").getMember("diagnose");
		if (!diagnose.canExecute()) {
			throw new ScriptException(String.format("Unable to find 'diagnose' function in '%s' Python diagnostic", this.getName()));
		}
		return diagnose;
	}

}
