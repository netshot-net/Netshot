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
package net.netshot.netshot.devicescript.helper;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.access.Cli;
import net.netshot.netshot.access.Ssh;
import net.netshot.netshot.access.collector.TransferProtocol;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.driver.OptionDefinition;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Device.MissingDeviceDriverException;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.platform.net.Network6Address;
import net.netshot.netshot.platform.net.NetworkAddress;
import net.netshot.netshot.platform.net.NetworkAddress.AddressUsage;
import net.netshot.netshot.platform.net.PhysicalAddress;

/**
 * Class used to get  and set data on a device object from JavaScript.
 * @author sylvain.cadilhac
 */
@Slf4j
public final class JsDeviceHelper {

	private final DriverRegistry driverRegistry;
	private DeviceDraft device;
	private JsCliHelper defaultCliHelper;
	private final DeviceLookup lookup = new DeviceLookup();
	private TaskContext taskContext;
	private boolean readOnly;

	/** Common update date for modules and other items. */
	private Instant updateDate;

	public static String getStringMember(Value value, String key, String defaultResult) {
		Value result = value.getMember(key);
		if (result != null) {
			return result.asString();
		}
		return defaultResult;
	}

	public static boolean getBooleanMember(Value value, String key, boolean defaultResult) {
		Value result = value.getMember(key);
		if (result != null) {
			return result.asBoolean();
		}
		return defaultResult;
	}

	public JsDeviceHelper(DriverRegistry driverRegistry, DeviceDraft device, JsCliHelper defaultCliHelper,
		TaskContext taskContext, boolean readOnly) throws MissingDeviceDriverException {
		this.driverRegistry = driverRegistry;
		this.device = device;
		this.defaultCliHelper = defaultCliHelper;
		this.taskContext = taskContext;
		this.readOnly = readOnly;
		this.updateDate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
	}

	/**
	 * Adds a tag to the device. Allowed even in read-only mode (e.g. from
	 * compliance rules), since tags are user metadata, not snapshot data.
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
	 * Removes a tag from the device. Allowed even in read-only mode.
	 * @param tag the tag to remove
	 */
	private void removeTag(String tag) {
		if (device.removeTag(tag)) {
			taskContext.info("Removing tag '{}' from the device.", tag.trim());
		}
	}

	/**
	 * Sets the device comments. Allowed even in read-only mode.
	 * @param comments the new comments
	 */
	private void setComments(String comments) {
		device.setComments(comments);
		taskContext.trace("Setting device comments to '{}'.", comments);
	}

	@Export
	public void add(String key, Value data) {
		if ("tag".equals(key)) {
			if (data == null || !data.isString()) {
				taskContext.error("The tag to add should be a string.");
				return;
			}
			this.addTag(data.asString());
			return;
		}
		if (readOnly) {
			log.warn("Adding key '{}' is forbidden.", key);
			taskContext.error("Adding key {} is forbidden", key);
			return;
		}
		if (data == null) {
			return;
		}
		try {
			if ("module".equals(key)) {
				Module module = Module.builder()
					.slot(getStringMember(data, "slot", ""))
					.partNumber(getStringMember(data, "partNumber", ""))
					.serialNumber(getStringMember(data, "serialNumber", ""))
					.build();
				device.seeModule(module, this.updateDate);
			}
			else if ("networkInterface".equals(key)) {
				NetworkInterface.NetworkInterfaceBuilder networkInterface = NetworkInterface.builder()
					.interfaceName(data.getMember("name").asString())
					.virtualDevice(getStringMember(data, "virtualDevice", ""))
					.vrfInstance(getStringMember(data, "vrf", ""))
					.enabled(getBooleanMember(data, "enabled", true))
					.level3(getBooleanMember(data, "level3", true))
					.description(getStringMember(data, "description", ""))
					.physicalAddress(new PhysicalAddress(
						getStringMember(data, "mac", "0000.0000.0000")));
				Set<Network4Address> ip4Addresses = new LinkedHashSet<>();
				Set<Network6Address> ip6Addresses = new LinkedHashSet<>();
				Value ipAddresses = data.getMember("ip");
				if (ipAddresses != null) {
					for (long i = 0; i < ipAddresses.getArraySize(); i++) {
						Value ip = ipAddresses.getArrayElement(i);
						NetworkAddress address = null;
						if (ip.hasMember("ipv6")) {
							address = new Network6Address(ip.getMember("ipv6").asString(), ip.getMember("mask").asInt());
						}
						else if (ip.getMember("mask").isNumber()) {
							address = new Network4Address(ip.getMember("ip").asString(), ip.getMember("mask").asInt());
						}
						else {
							address = new Network4Address(ip.getMember("ip").asString(), ip.getMember("mask").asString());
						}
						Value usage = ip.getMember("usage");
						if (usage != null) {
							address.setAddressUsage(AddressUsage.valueOf(usage.asString()));
						}
						NetworkInterface.addIpAddress(ip4Addresses, ip6Addresses, address);
					}
				}

				device.addNetworkInterface(networkInterface.ip4Addresses(ip4Addresses).ip6Addresses(ip6Addresses).build());
			}
			else if ("vrf".equals(key)) {
				device.addVrfInstance(data.asString());
			}
			else if ("virtualDevice".equals(key)) {
				device.addVirtualDevice(data.asString());
			}
		}
		catch (Exception e) {
			log.warn("Error during snapshot while adding device attribute key '{}'.", key, e);
			taskContext.error("Can't add device attribute {}: {}", key, e.getMessage());
		}
	}

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

	@Export
	public void reset() {
		if (readOnly) {
			log.warn("Resetting device is forbidden.");
			taskContext.error("Resetting key is forbidden");
			return;
		}
		device.setFamily("");
		device.setLocation("");
		device.setContact("");
		device.setSoftwareVersion("");
		device.setNetworkClass(NetworkClass.UNKNOWN);
		device.clearAttributes();
		device.clearVrfInstances();
		device.clearVirtualDevices();
		device.setModulesRemoved();
		device.clearNetworkInterfaces();
		device.setEolModule(null);
		device.setEosModule(null);
		device.setEolDate(null);
		device.setEosDate(null);
	}

	@Export
	public void set(String key, Boolean value) {
		if (readOnly) {
			log.warn("Setting key '{}' is forbidden.", key);
			taskContext.error("Setting key {} is forbidden", key);
			return;
		}
		if (value == null) {
			return;
		}
		try {
			DeviceDriver driver = this.driverRegistry.forDevice(device);
			for (AttributeDefinition attribute : driver.getAttributes()) {
				if (attribute.getLevel().equals(AttributeLevel.DEVICE) && attribute.getName().equals(key)) {
					switch (attribute.getType()) {
						case BINARY:
							device.addAttribute(new DeviceBinaryAttribute(key, value));
							taskContext.trace("Setting device attribute '{}' to {}.", key, value);
							break;
						default:
					}
					break;
				}
			}
		}
		catch (Exception e) {
			log.warn("Error during snapshot while setting device attribute key '{}'.", key);
			taskContext.error("Can't add device attribute {}: {}", key, e.getMessage());
		}
	}


	@Export
	public void set(String key, Double value) {
		if (readOnly) {
			log.warn("Setting key '{}' is forbidden.", key);
			taskContext.error("Setting key {} is forbidden", key);
			return;
		}
		if (value == null) {
			return;
		}
		try {
			DeviceDriver driver = this.driverRegistry.forDevice(device);
			for (AttributeDefinition attribute : driver.getAttributes()) {
				if (attribute.getLevel().equals(AttributeLevel.DEVICE) && attribute.getName().equals(key)) {
					switch (attribute.getType()) {
						case NUMERIC:
							device.addAttribute(new DeviceNumericAttribute(key, value));
							taskContext.trace("Setting device attribute '{}' to {}.", key, value);
							break;
						default:
					}
					break;
				}
			}
		}
		catch (Exception e) {
			log.warn("Error during snapshot while setting device attribute key '{}'.", key);
			taskContext.error("Can't add device attribute {}: {}", key, e.getMessage());
		}
	}

	@Export
	public void set(String key, String value) {
		if ("comments".equals(key) && value != null) {
			this.setComments(value);
			return;
		}
		if (readOnly) {
			log.warn("Setting key '{}' is forbidden.", key);
			taskContext.error("Setting key {} is forbidden", key);
			return;
		}
		if (value == null) {
			return;
		}
		try {
			if ("name".equals(key)) {
				device.setName(value);
				taskContext.trace("Setting device name to '{}'.", value);
			}
			else if ("family".equals(key)) {
				device.setFamily(value);
				taskContext.trace("Setting device family to '{}'.", value);
			}
			else if ("location".equals(key)) {
				device.setLocation(value);
				taskContext.trace("Setting device location to '{}'.", value);
			}
			else if ("contact".equals(key)) {
				device.setContact(value);
				taskContext.trace("Setting device contact to '{}'.", value);
			}
			else if ("softwareVersion".equals(key)) {
				device.setSoftwareVersion(value);
				taskContext.trace("Setting device software version to '{}'.", value);
			}
			else if ("serialNumber".equals(key)) {
				device.setSerialNumber(value);
				taskContext.trace("Setting device serial number to '{}'.", value);
			}
			else if ("networkClass".equals(key)) {
				NetworkClass nc = NetworkClass.valueOf(value);
				if (nc != null) {
					device.setNetworkClass(nc);
					taskContext.trace("Setting device network class to '{}'.", value);
				}
			}
			else {
				DeviceDriver driver = this.driverRegistry.forDevice(device);
				for (AttributeDefinition attribute : driver.getAttributes()) {
					if (attribute.getLevel().equals(AttributeLevel.DEVICE) && attribute.getName().equals(key)) {
						switch (attribute.getType()) {
							case LONGTEXT:
								device.addAttribute(new DeviceLongTextAttribute(key, value));
								taskContext.trace("Setting device attribute '{}' to '{}'.", key, value);
								break;
							case TEXT:
								device.addAttribute(new DeviceTextAttribute(key, value));
								taskContext.trace("Setting device attribute '{}' to '{}'.", key, value);
								break;
							default:
						}
						break;
					}
				}
			}
		}
		catch (Exception e) {
			log.warn("Error during snapshot while setting device attribute key '{}'.", key);
			taskContext.error("Can't add device attribute {}: {}", key, e.getMessage());
		}
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
		else if ("managementIpAddress".equals(item)) {
			return targetDevice.mgmtAddress();
		}
		else if ("managementDomain".equals(item)) {
			return targetDevice.mgmtDomain().name();
		}
		else if ("location".equals(item)) {
			return targetDevice.location();
		}
		else if ("contact".equals(item)) {
			return targetDevice.contact();
		}
		else if ("softwareVersion".equals(item)) {
			return targetDevice.softwareVersion();
		}
		else if ("serialNumber".equals(item)) {
			return targetDevice.serialNumber();
		}
		else if ("comments".equals(item)) {
			return targetDevice.comments();
		}
		else if ("networkClass".equals(item)) {
			return targetDevice.networkClass() == null ? null : targetDevice.networkClass().toString();
		}
		else if ("virtualDevices".equals(item)) {
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
				Map<String, Object> module = new HashMap<>();
				module.put("slot", Value.asValue(m.slot()));
				module.put("partNumber", Value.asValue(m.partNumber()));
				module.put("serialNumber", Value.asValue(m.serialNumber()));
				modules.add(ProxyObject.fromMap(module));
			}
			return ProxyArray.fromList(modules);
		}
		else if ("interfaces".equals(item)) {
			List<Object> networkInterfaces = new ArrayList<>();
			for (NetworkInterface ni : targetDevice.networkInterfaces()) {
				Map<String, Object> networkInterface = new HashMap<>();
				networkInterface.put("name", ni.interfaceName());
				networkInterface.put("description", ni.description());
				networkInterface.put("mac", ni.getMacAddress());
				networkInterface.put("virtualDevice", ni.virtualDevice());
				networkInterface.put("vrf", ni.vrfInstance());
				networkInterface.put("enabled", ni.enabled());
				networkInterface.put("level3", ni.level3());
				List<Object> ips = new ArrayList<>();
				for (Network4Address address : ni.ip4Addresses()) {
					Map<String, Object> ip = new HashMap<>();
					ip.put("ip", address.getIp());
					ip.put("mask", Integer.toString(address.getPrefixLength()));
					ip.put("usage", address.getAddressUsage().toString());
					ips.add(ProxyObject.fromMap(ip));
				}
				for (Network6Address address : ni.ip6Addresses()) {
					Map<String, Object> ip = new HashMap<>();
					ip.put("ipv6", address.getIp());
					ip.put("mask", Integer.toString(address.getPrefixLength()));
					ip.put("usage", address.getAddressUsage().toString());
					ips.add(ProxyObject.fromMap(ip));
				}
				networkInterface.put("ip", ProxyArray.fromList(ips));
				networkInterfaces.add(ProxyObject.fromMap(networkInterface));
			}
			return ProxyArray.fromList(networkInterfaces);
		}
		else {
			for (AttributeDefinition definition : driver.getAttributes()) {
				if ((definition.getName().equals(item) || definition.getTitle().equals(item)) && definition.isCheckable()) {
					Config lastConfig = definition.getLevel() == AttributeLevel.CONFIG ? this.lastConfigOf(targetDevice) : null;
					if (definition.getLevel() == AttributeLevel.CONFIG && lastConfig != null) {
						for (ConfigAttribute attribute : lastConfig.attributes()) {
							if (attribute.name().equals(item)) {
								return attribute.getData();
							}
						}
					}
					else if (definition.getLevel() == AttributeLevel.DEVICE) {
						for (DeviceAttribute attribute : targetDevice.attributes()) {
							if (attribute.name().equals(item)) {
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
		log.debug("JavaScript request for item {} on current device.", item);
		return this.getDeviceItem(this.device.toDevice(), item);
	}

	/**
	 * Resolves the per-device values of the driver-declared Options, falling
	 * back to each option's default value when the device has no stored
	 * value for it. Read-only: options are user-set, not driver-written.
	 *
	 * <p>Values are already correctly typed in {@link Device#options()}
	 * (a real {@code Boolean} for a BOOLEAN option, a {@code String} for
	 * TEXT) and passed through as-is.</p>
	 *
	 * @return the effective option values, keyed by option name
	 */
	public Map<String, Object> resolveOptionValues() {
		Map<String, Object> values = new HashMap<>();
		DeviceDriver driver;
		try {
			driver = this.driverRegistry.forDevice(this.device);
		}
		catch (MissingDeviceDriverException e) {
			driver = null;
		}
		if (driver != null) {
			Map<String, Object> storedOptions = this.device.getOptions();
			if (storedOptions == null) {
				storedOptions = Collections.emptyMap();
			}
			for (OptionDefinition definition : driver.getOptions().values()) {
				Object value = storedOptions.get(definition.getName());
				if (value == null) {
					value = definition.getDefaultValue();
				}
				if (value != null) {
					values.put(definition.getName(), value);
				}
			}
		}
		return values;
	}

	/**
	 * Gets the per-device values of the driver-declared Options, for use
	 * from JS as {@code device.options.<name>} - a driver's natural
	 * {@code if (device.options.x)} check works as expected since values
	 * are properly typed (see {@link #resolveOptionValues()}).
	 *
	 * @return a JS object mapping option name to its current value
	 */
	@Export
	public Object getOptions() {
		return ProxyObject.fromMap(this.resolveOptionValues());
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
		log.debug("JavaScript request for item {} on device {}.", item,
			deviceId);
		if (deviceId == this.device.getId()) {
			return this.get(item);
		}
		try {
			Device targetDevice = loadDevice(deviceId);
			return this.getDeviceItem(targetDevice, item);
		}
		catch (Exception e) {
			log.error("Error on JavaScript get, item {}, device {}.", item,
				deviceId, e);
			this.taskContext.warn("Unable to get data %s for device {}.", item, deviceId);
		}
		return null;
	}

	@Export
	public Object get(String item, String deviceName) {
		log.debug("JavaScript request for item {} on device named {}.", item,
			deviceName);
		try {
			if (device.getName().equals(deviceName)) {
				return this.get(item);
			}
			Device targetDevice = loadDevice(deviceName);
			return this.getDeviceItem(targetDevice, item);
		}
		catch (Exception e) {
			log.error("Error on JavaScript get, item {}, device named {}.", item,
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
		Map<String, Object> result = new HashMap<>();
		result.put("name", name);
		result.put("address", address);
		return ProxyObject.fromMap(result);
	}

	/**
	 * Download a text file from the device, using SCP or SFTP, and return this text.
	 * @param protocol the transfer protocol to use
	 * @param remoteFileName the file (including full path) to download from the device
	 * @param charset the text charset
	 * @param newSession start the SCP/SFTP download in a new SSH session
	 * @return the downloaded text
	 * @throws Exception if an error occurs during the download
	 */
	public String textDownload(TransferProtocol protocol, String remoteFileName,
			Charset charset, boolean newSession) throws Exception {

		if (remoteFileName == null) {
			log.warn("Remote file name is missing in textDownload request.");
			taskContext.error("Remote file name is missing in textDownload request.");
			throw new IllegalArgumentException("Remote file name to download is missing in textDownload request");
		}
		if (TransferProtocol.SCP.equals(protocol) || TransferProtocol.SFTP.equals(protocol)) {
			if (defaultCliHelper == null) {
				log.warn("Error during textDownload: can't use SCP/SFTP textDownload as no CLI access exists in the context.");
				taskContext.error("Can't use SCP/SFTP method in textDownload request as no CLI access exists in the context.");
				throw new IllegalArgumentException("Can't use SCP/SFTP textDownload method as no CLI access exists in this context.");
			}

			Cli cli = defaultCliHelper.getUnderlyingCli();
			if (cli instanceof Ssh sshCli) {
				ByteArrayOutputStream targetStream = new ByteArrayOutputStream();
				if (TransferProtocol.SCP.equals(protocol)) {
					sshCli.scpDownload(remoteFileName, targetStream, newSession);
				}
				else {
					sshCli.sftpDownload(remoteFileName, targetStream, newSession);
				}
				return targetStream.toString(charset);
			}

			// Not SSH-base CLI session
			log.warn("Error during textDownload: can't use SCP/SFTP method with non-SSH CLI access.");
			taskContext.error("Can't use SCP/SFTP method with non-SSH CLI access in textDownload request.");
			throw new IllegalArgumentException("Can't use SCP/SFTP method with non-SSH CLI access in textDownload request.");
		}
		

		log.warn("Error during textDownload: can't use SCP/SFTP method with non-SSH CLI access.");
		taskContext.error("Can't use SCP/SFTP method with non-SSH CLI access in textDownload request.");
		throw new IllegalArgumentException("Unsupported .");

	}

	/**
	 * Download a text file from the device, using SCP or SFTP, and return this text - to be called from JS.
	 * @param method "scp" or "sftp"
	 * @param remoteFileName the file (including full path) to download from the device
	 * @param charsetName the text charset, to decode the received data
	 * @param newSession start the SCP/SFTP download in a new SSH session
	 * @return the downloaded text
	 */
	@Export
	public String textDownload(String method, String remoteFileName, String charsetName, boolean newSession) throws Exception {

		TransferProtocol protocol = null;
		if (method != null) {
			protocol = TransferProtocol.valueOf(method.toUpperCase());
		}

		if (protocol == null) {
			log.warn("Error during textDownload, invalid download method '{}'.", method);
			taskContext.error("Invalid download method '{}' in textDownload.", method);
			throw new IllegalArgumentException("Invalid download method");
		}

		final Charset charset;
		try {
			charset = Charset.forName(charsetName);
		}
		catch (Exception e) {
			log.warn("Error during textDownload, invalid charset name '{}'.", charsetName);
			taskContext.error("Invalid charset name '{}' in textDownload.", method);
			throw new IllegalArgumentException("Invalid charset");
		}

		return this.textDownload(protocol, remoteFileName, charset, newSession);
	}
}
