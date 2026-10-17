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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.devicescript.UserDeviceScript;
import net.netshot.netshot.devicescript.UserDeviceScript.UserInputDefinition;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.task.engine.TaskContext;

@Entity
public class DeviceJsScript {

	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	private long id;

	@Getter(onMethod = @__({
		@NaturalId
	}))
	@Setter
	private String name;

	@Getter(onMethod = @__({
		@Column(length = 10000000)
	}))
	@Setter
	private String script;

	@Getter
	@Setter
	private String deviceDriver;

	@Getter
	@Setter
	private String author;

	@Getter(onMethod = @__({
		@NaturalId,
		@Column(length = 1000)
	}))
	@Setter
	private String folder = "";

	@Getter(onMethod = @__({
		@Transient
	}))
	@Setter
	private Map<String, UserInputDefinition> userInputDefinitions;

	protected DeviceJsScript() {

	}

	public DeviceJsScript(String name, String deviceDriver, String script, String author) {
		this.name = name;
		this.script = script;
		this.deviceDriver = deviceDriver;
		this.author = author;
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
	 * @param driverRegistry the loaded device drivers
	 * @throws IllegalArgumentException if the script is invalid
	 */
	public void extractUserInputDefinitions(DriverRegistry driverRegistry) throws IllegalArgumentException {
		if (this.userInputDefinitions == null) {
			final UserDeviceScript jsScript = new UserDeviceScript(driverRegistry, this.deviceDriver, this.script, this.makeVoidLogger());
			this.userInputDefinitions = jsScript.extractInputDefinitions();
		}
	}

	/**
	 * Validate passed user inputs against input definitions.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param userInputs the data to validate
	 * @throws IllegalArgumentException if validation fails
	 */
	public void validateUserInputs(DriverRegistry driverRegistry, Map<String, String> userInputs) throws IllegalArgumentException {
		final UserDeviceScript jsScript = new UserDeviceScript(driverRegistry, this.deviceDriver, this.script, this.makeVoidLogger());
		jsScript.setUserInputValues(userInputs);
		jsScript.validateUserInputs();
	}

	@Override
	public String toString() {
		return "Device JavaScript script " + id + " (name '" + name + "', author '" + author + "')";
	}
}
