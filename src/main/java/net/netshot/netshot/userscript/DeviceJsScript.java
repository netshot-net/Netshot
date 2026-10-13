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
package net.netshot.netshot.userscript;

import java.util.Map;

import org.hibernate.annotations.NaturalId;
import org.slf4j.event.Level;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.devicescript.UserDeviceScript;
import net.netshot.netshot.devicescript.UserDeviceScript.UserInputDefinition;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.task.engine.TaskContext;

@Entity
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class DeviceJsScript {

	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY),
		@XmlAttribute, @JsonView(DefaultView.class)
	}))
	@Setter
	private long id;

	@Getter(onMethod = @__({
		@NaturalId,
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String name;

	@Getter(onMethod = @__({
		@Column(length = 10000000),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String script;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String deviceDriver;

	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String author;

	@Getter(onMethod = @__({
		@NaturalId,
		@Column(length = 1000),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String folder = "";

	@Getter(onMethod = @__({
		@Transient,
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private Map<String, UserInputDefinition> userInputDefinitions;

	protected DeviceJsScript() {

	}

	public DeviceJsScript(String name, String deviceDriver, String script, String author) {
		this.name = name;
		this.script = script;
		this.deviceDriver = deviceDriver;
		this.author = author;
	}

	@Transient
	@XmlElement
	@JsonView(DefaultView.class)
	public String getRealDeviceType() {
		DeviceDriver driver = DeviceDriver.getDriverByName(this.deviceDriver);
		if (driver == null) {
			return "Unknwon driver";
		}
		return driver.getDescription();
	}

	/**
	 * Create a task context which does nothing.
	 *
	 * @return the void task context
	 */
	private TaskContext makeVoidLogger() {
		return new TaskContext() {
			@Override
			public boolean isTracing() {
				return false;
			}

			@Override
			public void log(Level level, String message, Object... params) {
				// Ignore
			}

			@Override
			public String getIdentifier() {
				return "Void";
			}
		};
	}

	/**
	 * Actually populate user input definitions by evaluating the script and extract Input variable.
	 *
	 * @throws IllegalArgumentException if the script is invalid
	 */
	public void extractUserInputDefinitions() throws IllegalArgumentException {
		if (this.userInputDefinitions == null) {
			final UserDeviceScript jsScript = new UserDeviceScript(this.deviceDriver, this.script, this.makeVoidLogger());
			this.userInputDefinitions = jsScript.extractInputDefinitions();
		}
	}

	/**
	 * Validate passed user inputs against input definitions.
	 *
	 * @param userInputs the data to validate
	 * @throws IllegalArgumentException if validation fails
	 */
	public void validateUserInputs(Map<String, String> userInputs) throws IllegalArgumentException {
		final UserDeviceScript jsScript = new UserDeviceScript(this.deviceDriver, this.script, this.makeVoidLogger());
		jsScript.setUserInputValues(userInputs);
		jsScript.validateUserInputs();
	}

	@Override
	public String toString() {
		return "Device JavaScript script " + id + " (name '" + name + "', author '" + author + "')";
	}
}
