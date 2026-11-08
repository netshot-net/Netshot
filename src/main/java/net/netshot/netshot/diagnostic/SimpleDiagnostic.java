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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.Value;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.DeviceGroup;
import net.netshot.netshot.device.attribute.AttributeDefinition.AttributeType;
import net.netshot.netshot.rest.RestViews.DefaultView;

/**
 * This is a simple diagnostic: runs a CLI command in a CLI mode, and optionally
 * transforms the result using a regular expression.
 * 
 * @author sylvain.cadilhac
 */
@Entity
@OnDelete(action = OnDeleteAction.CASCADE)
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class SimpleDiagnostic extends Diagnostic {

	/**
	 * A simple object to store command and CLI mode, to be passed to Javascript for execution.
	 */
	public static class JsSimpleDiagnostic {
		@Getter(onMethod = @__({
			@Export
		}))
		@Setter
		private String mode;

		@Getter(onMethod = @__({
			@Export
		}))
		@Setter
		private String command;

		public JsSimpleDiagnostic(String mode, String command) {
			this.mode = mode;
			this.command = command;
		}
	}

	/**
	 * The type of device this diagnostic applies to.
	 */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String deviceDriver;

	/**
	 * The CLI mode to run the command in.
	 */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String cliMode;

	/**
	 * The CLI command to run.
	 */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String command;

	/**
	 * The pattern to search for in the result string.
	 */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String modifierPattern;

	/**
	 * The replacement string (to replace the modifierPattern with). Also supports
	 * regular expression backreferences.
	 */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String modifierReplacement;

	/**
	 * Instantiates a new SimpleDiagnostic. For Hibernate.
	 */
	protected SimpleDiagnostic() {
	}

	/**
	 * Instantiates a new diagnostic.
	 * 
	 * @param name = The name
	 * @param enabled = True to enable the diagnostic
	 * @param targetGroup = The group of devices the diagnostic applies to
	 * @param resultType = The type of result expected by this diagnostic
	 * @param deviceDriver = The device driver
	 * @param cliMode = The CLI mode
	 * @param command = The CLI command to issue
	 * @param modifierPattern = The pattern to match
	 * @param modifierReplacement = The replacement script if the pattern is matched
	 */
	public SimpleDiagnostic(String name, boolean enabled, DeviceGroup targetGroup, AttributeType resultType,
		String deviceDriver, String cliMode, String command, String modifierPattern, String modifierReplacement) {
		super(name, enabled, targetGroup, resultType);
		this.deviceDriver = deviceDriver;
		this.cliMode = cliMode;
		this.command = command;
		this.modifierPattern = modifierPattern;
		this.modifierReplacement = modifierReplacement;
	}

	/**
	 * Gets the description of the device driver. To be sent along with the
	 * diagnostic.
	 * 
	 * @return the description of the device driver
	 */
	@Transient
	@XmlElement
	@JsonView(DefaultView.class)
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


	@Transient
	@Override
	public DiagnosticResult makeResult(Device device, Value value) {
		String newValue = value.asString();
		if (this.getModifierPattern() != null) {
			newValue = newValue.replaceAll(this.getModifierPattern(), this.getModifierReplacement());
		}
		switch (this.resultType) {
			case LONGTEXT:
				return new DiagnosticLongTextResult(device, this, newValue);
			case TEXT:
				return new DiagnosticTextResult(device, this, newValue);
			case NUMERIC:
				return new DiagnosticNumericResult(device, this, Double.parseDouble(newValue));
			case BINARY:
				boolean booleanValue = !"".equals(newValue) && !"false".equals(newValue.toLowerCase()) && !"no".equals(newValue.toLowerCase());
				return new DiagnosticBinaryResult(device, this, booleanValue);
			default:
				return null;
		}
	}

	@Override
	public Value getJsObject(Device device, Context context) {
		if (!device.getDriver().equals(this.getDeviceDriver())) {
			return null;
		}
		return Value.asValue(new JsSimpleDiagnostic(this.cliMode, this.command));
	}

}
