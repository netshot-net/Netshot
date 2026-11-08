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
package net.netshot.netshot.device.script.helper;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.polyglot.HostAccess.Export;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.hibernate.HibernateException;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.Session;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.Device.MissingDeviceDriverException;
import net.netshot.netshot.device.Device.NetworkClass;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.Module;
import net.netshot.netshot.device.Network4Address;
import net.netshot.netshot.device.Network6Address;
import net.netshot.netshot.device.NetworkAddress;
import net.netshot.netshot.device.NetworkAddress.AddressUsage;
import net.netshot.netshot.device.NetworkInterface;
import net.netshot.netshot.device.PhysicalAddress;
import net.netshot.netshot.device.access.Cli;
import net.netshot.netshot.device.access.Ssh;
import net.netshot.netshot.device.attribute.AttributeDefinition;
import net.netshot.netshot.device.attribute.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.device.collector.TransferProtocol;
import net.netshot.netshot.device.attribute.ConfigAttribute;
import net.netshot.netshot.device.attribute.DeviceAttribute;
import net.netshot.netshot.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.device.attribute.OptionDefinition;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.work.TaskContext;

/**
 * Class used to get  and set data on a device object from JavaScript.
 * @author sylvain.cadilhac
 */
@Slf4j
public final class JsDeviceHelper {

	private Device device;
	private JsCliHelper defaultCliHelper;
	private Session session;
	private TaskContext taskContext;
	private boolean readOnly;

	/** Common update date for modules and other items. */
	private Date updateDate;

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

	public JsDeviceHelper(Device device, JsCliHelper defaultCliHelper, Session session, TaskContext taskContext, boolean readOnly) throws MissingDeviceDriverException {
		this.device = device;
		this.defaultCliHelper = defaultCliHelper;
		this.session = session;
		this.taskContext = taskContext;
		this.readOnly = readOnly;
		this.updateDate = new Date();
	}

	@Export
	public void add(String key, Value data) {
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
				boolean found = false;
				Module module = new Module(
					getStringMember(data, "slot", ""),
					getStringMember(data, "partNumber", ""),
					getStringMember(data, "serialNumber", ""),
					device
				);
				for (Module existingModule : device.getModules()) {
					if (existingModule.isRemoved()
						&& Objects.equals(existingModule.getSlot(), module.getSlot())
						&& Objects.equals(existingModule.getPartNumber(), module.getPartNumber())
						&& Objects.equals(existingModule.getSerialNumber(), module.getSerialNumber())) {
						existingModule.setRemoved(false);
						existingModule.setLastSeenDate(this.updateDate);
						found = true;
						break;
					}
				}
				if (!found) {
					module.setFirstSeenDate(this.updateDate);
					module.setLastSeenDate(this.updateDate);
					device.getModules().add(module);
				}
			}
			else if ("networkInterface".equals(key)) {
				NetworkInterface networkInterface = new NetworkInterface(
					device,
					data.getMember("name").asString(),
					getStringMember(data, "virtualDevice", ""),
					getStringMember(data, "vrf", ""),
					getBooleanMember(data, "enabled", true),
					getBooleanMember(data, "level3", true),
					getStringMember(data, "description", "")
				);
				networkInterface.setPhysicalAddress(new PhysicalAddress(
					getStringMember(data, "mac", "0000.0000.0000")));
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
						networkInterface.addIpAddress(address);
					}
				}

				device.getNetworkInterfaces().add(networkInterface);
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
		device.clearVrfInstance();
		device.clearVirtualDevices();
		device.setModulesRemoved();
		device.getNetworkInterfaces().clear();
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
			DeviceDriver driver = device.getDeviceDriver();
			for (AttributeDefinition attribute : driver.getAttributes()) {
				if (attribute.getLevel().equals(AttributeLevel.DEVICE) && attribute.getName().equals(key)) {
					switch (attribute.getType()) {
						case BINARY:
							device.addAttribute(new DeviceBinaryAttribute(device, key, value));
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
			DeviceDriver driver = device.getDeviceDriver();
			for (AttributeDefinition attribute : driver.getAttributes()) {
				if (attribute.getLevel().equals(AttributeLevel.DEVICE) && attribute.getName().equals(key)) {
					switch (attribute.getType()) {
						case NUMERIC:
							device.addAttribute(new DeviceNumericAttribute(device, key, value));
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
			else if ("comments".equals(key)) {
				device.setComments(value);
				taskContext.trace("Setting device comments to '{}'.", value);
			}
			else if ("networkClass".equals(key)) {
				NetworkClass nc = NetworkClass.valueOf(value);
				if (nc != null) {
					device.setNetworkClass(nc);
					taskContext.trace("Setting device network class to '{}'.", value);
				}
			}
			else {
				DeviceDriver driver = device.getDeviceDriver();
				for (AttributeDefinition attribute : driver.getAttributes()) {
					if (attribute.getLevel().equals(AttributeLevel.DEVICE) && attribute.getName().equals(key)) {
						switch (attribute.getType()) {
							case LONGTEXT:
								device.addAttribute(new DeviceLongTextAttribute(device, key, value));
								taskContext.trace("Setting device attribute '{}' to '{}'.", key, value);
								break;
							case TEXT:
								device.addAttribute(new DeviceTextAttribute(device, key, value));
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
			driver = targetDevice.getDeviceDriver();
		}
		catch (MissingDeviceDriverException e) {
			return null;
		}
		if ("type".equals(item)) {
			return driver.getDescription();
		}
		else if ("name".equals(item)) {
			return targetDevice.getName();
		}
		else if ("family".equals(item)) {
			return targetDevice.getFamily();
		}
		else if ("managementIpAddress".equals(item)) {
			return targetDevice.getMgmtAddress();
		}
		else if ("managementDomain".equals(item)) {
			return targetDevice.getMgmtDomain().getName();
		}
		else if ("location".equals(item)) {
			return targetDevice.getLocation();
		}
		else if ("contact".equals(item)) {
			return targetDevice.getContact();
		}
		else if ("softwareVersion".equals(item)) {
			return targetDevice.getSoftwareVersion();
		}
		else if ("serialNumber".equals(item)) {
			return targetDevice.getSerialNumber();
		}
		else if ("comments".equals(item)) {
			return targetDevice.getComments();
		}
		else if ("networkClass".equals(item)) {
			return targetDevice.getNetworkClass() == null ? null : targetDevice.getNetworkClass().toString();
		}
		else if ("virtualDevices".equals(item)) {
			return ProxyArray.fromList(List.copyOf(targetDevice.getVirtualDevices()));
		}
		else if ("vrfs".equals(item)) {
			return ProxyArray.fromList(List.copyOf(targetDevice.getVrfInstances()));
		}
		else if ("modules".equals(item)) {
			List<Object> modules = new ArrayList<>();
			for (Module m : targetDevice.getModules()) {
				Map<String, Object> module = new HashMap<>();
				module.put("slot", Value.asValue(m.getSlot()));
				module.put("partNumber", Value.asValue(m.getPartNumber()));
				module.put("serialNumber", Value.asValue(m.getSerialNumber()));
				modules.add(ProxyObject.fromMap(module));
			}
			return ProxyArray.fromList(modules);
		}
		else if ("interfaces".equals(item)) {
			List<Object> networkInterfaces = new ArrayList<>();
			for (NetworkInterface ni : targetDevice.getNetworkInterfaces()) {
				Map<String, Object> networkInterface = new HashMap<>();
				networkInterface.put("name", ni.getInterfaceName());
				networkInterface.put("description", ni.getDescription());
				networkInterface.put("mac", ni.getMacAddress());
				networkInterface.put("virtualDevice", ni.getVirtualDevice());
				networkInterface.put("vrf", ni.getVrfInstance());
				networkInterface.put("enabled", ni.isEnabled());
				networkInterface.put("level3", ni.isLevel3());
				List<Object> ips = new ArrayList<>();
				for (Network4Address address : ni.getIp4Addresses()) {
					Map<String, Object> ip = new HashMap<>();
					ip.put("ip", address.getIp());
					ip.put("mask", Integer.toString(address.getPrefixLength()));
					ip.put("usage", address.getAddressUsage().toString());
					ips.add(ProxyObject.fromMap(ip));
				}
				for (Network6Address address : ni.getIp6Addresses()) {
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
					if (definition.getLevel() == AttributeLevel.CONFIG && targetDevice.getLastConfig() != null) {
						for (ConfigAttribute attribute : targetDevice.getLastConfig().getAttributes()) {
							if (attribute.getName().equals(item)) {
								return attribute.getData();
							}
						}
					}
					else if (definition.getLevel() == AttributeLevel.DEVICE) {
						for (DeviceAttribute attribute : targetDevice.getAttributes()) {
							if (attribute.getName().equals(item)) {
								return attribute.getData();
							}
						}
					}
				}
			}
			for (DiagnosticResult diagnosticResult : targetDevice.getDiagnosticResults()) {
				String diagnosticName = diagnosticResult.getDiagnosticName();
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
		return this.getDeviceItem(this.device, item);
	}

	/**
	 * Resolves the per-device values of the driver-declared Options, falling
	 * back to each option's default value when the device has no stored
	 * value for it. Read-only: options are user-set, not driver-written.
	 *
	 * <p>Values are already correctly typed in {@link Device#getOptions()}
	 * (a real {@code Boolean} for a BOOLEAN option, a {@code String} for
	 * TEXT) and passed through as-is.</p>
	 *
	 * @return the effective option values, keyed by option name
	 */
	public Map<String, Object> resolveOptionValues() {
		Map<String, Object> values = new HashMap<>();
		DeviceDriver driver;
		try {
			driver = this.device.getDeviceDriver();
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
	 * Load device.
	 *
	 * @param id the id
	 * @return the device
	 * @throws HibernateException the hibernate exception
	 */
	private Device loadDevice(long id) throws HibernateException {
		Device foundDevice = session
			.createQuery("from Device d join fetch d.lastConfig where d.id = :id", Device.class)
			.setParameter("id", id)
			.uniqueResult();
		return foundDevice;
	}

	private Device loadDevice(String name) throws HibernateException {
		Device foundDevice = session
			.createQuery("from Device d join fetch d.lastConfig where d.name = :name", Device.class)
			.setParameter("name", name)
			.uniqueResult();
		return foundDevice;
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
			Object result = this.getDeviceItem(targetDevice, item);
			session.evict(targetDevice);
			return result;
		}
		catch (ObjectNotFoundException e) {
			log.error("Device not found on JavaScript get, item {}, device {}.",
				item, deviceId, e);
			this.taskContext.warn("Unable to find the device {}.", deviceId);
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
			Object result = this.getDeviceItem(targetDevice, item);
			session.evict(targetDevice);
			return result;
		}
		catch (ObjectNotFoundException e) {
			log.error("Device not found on JavaScript get, item {}, device named {}.",
				item, deviceName, e);
			this.taskContext.warn("Unable to find the device named {}.", deviceName);
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
