/**
 * Copyright 2013-present Netshot
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
package net.netshot.netshot.devicescript.helper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyHashMap;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.access.DeviceReads;
import net.netshot.netshot.driver.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.driver.MissingDeviceDriverException;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.diagnostic.DiagnosticResult;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;

/**
 * Gives a Python compliance rule read access to the device (and to the other devices); the rule can only
 * change the comments and the tags of the device.
 * @author sylvain.cadilhac
 *
 */
@Slf4j
public final class PyDeviceHelper {

	private final DriverRegistry driverRegistry;
	private DeviceDraft device;
	private final DeviceLookup lookup;
	private TaskContext taskContext;

	/**
	 * Change a pythonic key into camel case, e.g. 'running_config' into 'runningConfig'.
	 * @param field = the field name to change
	 * @return the JS version if applicable, the original field otherwise
	 */
	private static String pythonicToJavascriptic(String field) {
		if (field.matches("^[a-z0-9_]+$")) {
			String[] parts = field.split("_");
			for (int i = 1; i < parts.length; i++) {
				parts[i] = StringUtils.capitalize(parts[i]);
			}
			return String.join("", parts);
		}
		return field;
	}

	public PyDeviceHelper(DriverRegistry driverRegistry, DeviceReads reads, DeviceDraft device, TaskContext taskContext)
		throws MissingDeviceDriverException {
		this.driverRegistry = driverRegistry;
		this.lookup = new DeviceLookup(reads);
		this.device = device;
		this.taskContext = taskContext;
	}

	/**
	 * Adds a tag to the device. Allowed since tags are user metadata, not snapshot data.
	 * @param tag the tag to add
	 */
	private void addTag(String tag) {
		if (Device.normalizeTag(tag) == null) {
			taskContext.warn("Ignoring invalid device tag '{}'.", tag);
			return;
		}
		if (device.addTag(tag)) {
			taskContext.info("Adding tag '{}' to the device.", tag.trim());
		}
	}

	/**
	 * Removes a tag from the device.
	 * @param tag the tag to remove
	 */
	private void removeTag(String tag) {
		if (device.removeTag(tag)) {
			taskContext.info("Removing tag '{}' from the device.", tag.trim());
		}
	}

	/**
	 * Sets the device comments.
	 * @param comments the new comments
	 * @throws IllegalArgumentException if the comments are too long (a script error)
	 */
	private void setComments(String comments) {
		if (comments == null) {
			return;
		}
		if (comments.length() > Device.MAX_COMMENTS_LENGTH) {
			throw new IllegalArgumentException("The device comments can't be longer than %d characters (%d given)."
				.formatted(Device.MAX_COMMENTS_LENGTH, comments.length()));
		}
		device.setComments(comments);
		taskContext.trace("Setting device comments to '{}'.", comments);
	}

	/**
	 * Adds an item to the device: only a tag (the device data come from the snapshots).
	 *
	 * @param key the collection (only "tag")
	 * @param data the tag
	 */
	@Export
	public void add(String key, Value data) {
		if (!"tag".equals(key)) {
			log.warn("Adding key '{}' is forbidden.", key);
			taskContext.error("Adding key {} is forbidden", key);
			return;
		}
		if (data == null || !data.isString()) {
			taskContext.error("The tag to add should be a string.");
			return;
		}
		this.addTag(data.asString());
	}

	/**
	 * Removes an item from the device: only a tag.
	 *
	 * @param key the collection (only "tag")
	 * @param data the tag
	 */
	@Export
	public void remove(String key, Value data) {
		if (data == null) {
			return;
		}
		if ("tag".equals(key)) {
			if (!data.isString()) {
				taskContext.error("The tag to remove should be a string.");
				return;
			}
			this.removeTag(data.asString());
		}
		else {
			log.warn("Removing key '{}' is not supported.", key);
			taskContext.error("Removing key {} is not supported", key);
		}
	}

	/**
	 * Refused: the device data come from the snapshots.
	 */
	@Export
	public void reset() {
		log.warn("Resetting device is forbidden.");
		taskContext.error("Resetting key is forbidden");
	}

	/**
	 * Sets a device field: only the comments (the device data come from the snapshots).
	 *
	 * @param key the field (only "comments")
	 * @param value the comments
	 */
	@Export
	public void set(String key, Value value) {
		if (!"comments".equals(key)) {
			log.warn("Setting key '{}' is forbidden.", key);
			taskContext.error("Setting key {} is forbidden", key);
			return;
		}
		if (value == null || value.isNull()) {
			return;
		}
		if (!value.isString()) {
			taskContext.error("The comments should be a string.");
			return;
		}
		this.setComments(value.asString());
	}

	/**
	 * Gets the device item.
	 *
	 * @param targetDevice the device
	 * @param item the item
	 * @return the device item
	 */
	private Object getDeviceItem(Device targetDevice, String item) {
		DeviceDriver driver;
		try {
			driver = this.driverRegistry.forDevice(targetDevice);
		}
		catch (MissingDeviceDriverException e) {
			return null;
		}
		if ("type".equals(item)) {
			return driver.getDescription();
		}
		else if ("name".equals(item)) {
			return targetDevice.name();
		}
		else if ("family".equals(item)) {
			return targetDevice.family();
		}
		else if ("management_ip_address".equals(item)) {
			return targetDevice.mgmtAddress();
		}
		else if ("management_domain".equals(item)) {
			return targetDevice.mgmtDomain().name();
		}
		else if ("location".equals(item)) {
			return targetDevice.location();
		}
		else if ("contact".equals(item)) {
			return targetDevice.contact();
		}
		else if ("software_version".equals(item)) {
			return targetDevice.softwareVersion();
		}
		else if ("serial_number".equals(item)) {
			return targetDevice.serialNumber();
		}
		else if ("comments".equals(item)) {
			return targetDevice.comments();
		}
		else if ("network_class".equals(item)) {
			return targetDevice.networkClass() == null ? null : targetDevice.networkClass().toString();
		}
		else if ("virtual_devices".equals(item)) {
			return ProxyArray.fromList(List.copyOf(targetDevice.virtualDevices()));
		}
		else if ("vrfs".equals(item)) {
			return ProxyArray.fromList(List.copyOf(targetDevice.vrfInstances()));
		}
		else if ("tags".equals(item)) {
			List<String> tags = new ArrayList<>(targetDevice.tags());
			Collections.sort(tags);
			return ProxyArray.fromList(new ArrayList<Object>(tags));
		}
		else if ("modules".equals(item)) {
			List<Object> modules = new ArrayList<>();
			for (Module m : targetDevice.modules()) {
				Map<Object, Object> module = new HashMap<>();
				module.put("slot", Value.asValue(m.slot()));
				module.put("part_number", Value.asValue(m.partNumber()));
				module.put("serial_number", Value.asValue(m.serialNumber()));
				modules.add(ProxyHashMap.from(module));
			}
			return ProxyArray.fromList(modules);
		}
		else if ("interfaces".equals(item)) {
			List<Object> networkInterfaces = new ArrayList<>();
			for (NetworkInterface ni : targetDevice.networkInterfaces()) {
				Map<Object, Object> networkInterface = new HashMap<>();
				networkInterface.put("name", ni.interfaceName());
				networkInterface.put("description", ni.description());
				networkInterface.put("mac", ni.getMacAddress());
				networkInterface.put("virtual_device", ni.virtualDevice());
				networkInterface.put("vrf", ni.vrfInstance());
				networkInterface.put("enabled", ni.enabled());
				networkInterface.put("level3", ni.level3());
				List<Object> ips = new ArrayList<>();
				for (Network4Address address : ni.ip4Addresses()) {
					Map<Object, Object> ip = new HashMap<>();
					ip.put("ip", address.getIp());
					ip.put("mask", Integer.toString(address.getPrefixLength()));
					ip.put("usage", address.getAddressUsage().toString());
					ips.add(ProxyHashMap.from(ip));
				}
				for (Network6Address address : ni.ip6Addresses()) {
					Map<Object, Object> ip = new HashMap<>();
					ip.put("ipv6", address.getIp());
					ip.put("mask", Integer.toString(address.getPrefixLength()));
					ip.put("usage", address.getAddressUsage().toString());
					ips.add(ProxyHashMap.from(ip));
				}
				networkInterface.put("ip", ProxyArray.fromList(ips));
				networkInterfaces.add(ProxyHashMap.from(networkInterface));
			}
			return ProxyArray.fromList(networkInterfaces);
		}
		else {
			String jsItem = pythonicToJavascriptic(item);
			for (AttributeDefinition definition : driver.getAttributes()) {
				if ((definition.getName().equals(jsItem) || definition.getTitle().equals(jsItem)) && definition.isCheckable()) {
					Config lastConfig = definition.getLevel() == AttributeLevel.CONFIG ? this.lastConfigOf(targetDevice) : null;
					if (definition.getLevel() == AttributeLevel.CONFIG && lastConfig != null) {
						for (ConfigAttribute attribute : lastConfig.attributes()) {
							if (attribute.name().equals(jsItem)) {
								return attribute.getData();
							}
						}
					}
					else if (definition.getLevel() == AttributeLevel.DEVICE) {
						for (DeviceAttribute attribute : targetDevice.attributes()) {
							if (attribute.name().equals(jsItem)) {
								return attribute.getData();
							}
						}
					}
				}
			}
			for (DiagnosticResult diagnosticResult : this.diagnosticResultsOf(targetDevice)) {
				String diagnosticName = diagnosticResult.diagnosticName();
				if (diagnosticName != null && diagnosticName.equals(item)) {
					return diagnosticResult.getData();
				}
			}
		}
		return null;
	}

	/**
	 * Gets the.
	 *
	 * @param item the item
	 * @return the object
	 */
	@Export
	public Object get(String item) {
		log.debug("Python request for item {} on current device.", item);
		return this.getDeviceItem(this.device.toDevice(), item);
	}

	/**
	 * Loads another device (it has to have a configuration).
	 *
	 * @param id the device ID
	 * @return the device
	 * @throws IllegalStateException if there is no such device, or it has no configuration
	 */
	private Device loadDevice(long id) {
		Device foundDevice = this.lookup.findDevice(id);
		if (foundDevice == null || foundDevice.lastConfigId() == null) {
			throw new IllegalStateException("No device %d with a configuration".formatted(id));
		}
		return foundDevice;
	}

	/**
	 * Loads another device by name (it has to have a configuration).
	 *
	 * @param name the device name
	 * @return the device
	 * @throws IllegalStateException if there is no such device, or it has no configuration
	 */
	private Device loadDevice(String name) {
		Device foundDevice = this.lookup.findDevice(name);
		if (foundDevice == null || foundDevice.lastConfigId() == null) {
			throw new IllegalStateException("No device named '%s' with a configuration".formatted(name));
		}
		return foundDevice;
	}

	/**
	 * Gets the last configuration of a device (the one of the device the script runs on, if it
	 * was loaded already).
	 *
	 * @param targetDevice the device
	 * @return the configuration, or null if the device has none
	 */
	private Config lastConfigOf(Device targetDevice) {
		if (targetDevice.id() == this.device.getId() && this.device.getLastConfig() != null) {
			return this.device.getLastConfig();
		}
		return this.lookup.lastConfig(targetDevice);
	}

	/**
	 * Gets the diagnostic results of a device: for the device the script runs on, the results
	 * given by the script so far replace the stored ones.
	 *
	 * @param targetDevice the device
	 * @return the diagnostic results
	 */
	private List<DiagnosticResult> diagnosticResultsOf(Device targetDevice) {
		boolean current = targetDevice.id() == this.device.getId();
		List<DiagnosticResult> results = new ArrayList<>(current && this.device.getStoredDiagnosticResults() != null
			? this.device.getStoredDiagnosticResults() : this.lookup.diagnosticResults(targetDevice.id()));
		if (current) {
			Map<Long, DiagnosticResult> given = this.device.getDiagnosticResults();
			results.removeIf(result -> given.containsKey(result.diagnosticId()));
			results.addAll(given.values());
		}
		return results;
	}

	/**
	 * Destroy.
	 */
	public void destroy() {
	}

	/**
	 * Gets the.
	 *
	 * @param item the item
	 * @param deviceId the device id
	 * @return the object
	 */
	@Export
	public Object get(String item, long deviceId) {
		log.debug("Python request for item {} on device {}.", item,
			deviceId);
		if (deviceId == this.device.getId()) {
			return this.get(item);
		}
		try {
			Device targetDevice = loadDevice(deviceId);
			return this.getDeviceItem(targetDevice, item);
		}
		catch (Exception e) {
			log.error("Error on Python get, item {}, device {}.", item,
				deviceId, e);
			this.taskContext.warn("Unable to get data %s for device {}.", item, deviceId);
		}
		return null;
	}

	@Export
	public Object get(String item, String deviceName) {
		log.debug("Python request for item {} on device named {}.", item,
			deviceName);
		try {
			if (device.getName().equals(deviceName)) {
				return this.get(item);
			}
			Device targetDevice = loadDevice(deviceName);
			return this.getDeviceItem(targetDevice, item);
		}
		catch (Exception e) {
			log.error("Error on Python get, item {}, device named {}.", item,
				deviceName, e);
			this.taskContext.warn("Unable to get data {} for device named {}.", item, deviceName);
		}
		return null;
	}

	@Export
	public void debug(String message) {
		taskContext.debug(message);
	}

	/**
	 * Resolve an hostname or an IP (reverse).
	 *
	 * @param host the host
	 * @return the object
	 */
	@Export
	public Object nslookup(String host) {
		String name = "";
		String address = "";
		try {
			InetAddress ip = InetAddress.getByName(host);
			name = ip.getHostName();
			address = ip.getHostAddress();
		}
		catch (UnknownHostException e) {
		}
		Map<Object, Object> result = new HashMap<>();
		result.put("name", name);
		result.put("address", address);
		return ProxyHashMap.from(result);
	}
}
