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
package net.netshot.netshot.device;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Hex;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.device.access.Http;
import net.netshot.netshot.device.access.Http.AuthScheme;
import net.netshot.netshot.device.access.Http.HttpConfig;
import net.netshot.netshot.device.access.Ssh.SshConfig;
import net.netshot.netshot.device.access.Ssh.SshInteractionInstruction;
import net.netshot.netshot.device.access.Telnet.TelnetConfig;
import net.netshot.netshot.device.attribute.AttributeDefinition;
import net.netshot.netshot.device.attribute.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.device.attribute.OptionDefinition;
import net.netshot.netshot.device.credentials.DeviceCredentialSet;
import net.netshot.netshot.device.credentials.DeviceHttpAccount;
import net.netshot.netshot.device.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.device.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.device.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.device.credentials.DeviceSshAccount;
import net.netshot.netshot.device.credentials.DeviceTelnetAccount;
import net.netshot.netshot.device.script.helper.JsUtils;
import net.netshot.netshot.device.collector.SnmpTrapReceiver;
import net.netshot.netshot.device.collector.SyslogServer;
import net.netshot.netshot.rest.RestViews.DefaultView;
import net.netshot.netshot.work.Task;
import net.netshot.netshot.work.TaskContext;

/**
 * This is a device driver.
 */
@XmlRootElement()
@XmlAccessorType(XmlAccessType.NONE)
@Slf4j
public class DeviceDriver implements Comparable<DeviceDriver> {

	public static final String PLACEHOLDER_USERNAME = "$$NetshotUsername$$";
	public static final String PLACEHOLDER_PASSWORD = "$$NetshotPassword$$";
	public static final String PLACEHOLDER_SUPERPASSWORD = "$$NetshotSuperPassword$$";

	/**
	 * Possible protocols for a device driver.
	 */
	public enum DriverProtocol {
		TELNET("telnet"),
		SSH("ssh"),
		SNMPV1("snmpv1"),
		SNMPV2C("snmpv2c"),
		SNMPV3("snmpv3"),
		HTTP("http"),
		HTTPS("https");

		private final String protocol;

		DriverProtocol(String protocol) {
			this.protocol = protocol;
		}

		public String value() {
			return protocol;
		}

		/** @return true for any of the SNMPv1/v2c/v3 family values */
		public boolean isSnmp() {
			return this == SNMPV1 || this == SNMPV2C || this == SNMPV3;
		}

		/** @return true for HTTP or HTTPS */
		public boolean isHttp() {
			return this == HTTP || this == HTTPS;
		}
	}

	/**
	 * Possible location types for driver files.
	 */
	public enum LocationType {
		EMBEDDED,
		FILE,
	}

	/**
	 * Driver location.
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static class Location {
		/** Location type. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private LocationType type;

		/** File name. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		@Setter
		private String fileName;

		public Location(LocationType type, String fileName) {
			this.type = type;
			this.fileName = fileName;
		}
	}

	public static class ReceiverTaskContext implements TaskContext {
		private final Logger logger;

		public ReceiverTaskContext(Class<?> clazz) {
			this.logger = LoggerFactory.getLogger(clazz);
		}

		@Override
		public void log(Level level, String message, Object... params) {
			this.logger.makeLoggingEventBuilder(level).log(message, params);
		}

		@Override
		public String getIdentifier() {
			return this.logger.getName();
		}
	}

	/** JS logger for SNMP-related messages. */
	private static final TaskContext JS_SNMP_LOGGER = new ReceiverTaskContext(SnmpTrapReceiver.class);

	/** JS logger for Syslog-related messages. */
	private static final TaskContext JS_SYSLOG_LOGGER = new ReceiverTaskContext(SyslogServer.class);

	/** The Javascript loader code. */
	private static final Source JSLOADER_SOURCE = readLoaderSource();

	/**
	 * Read the loader source code from resource file.
	 * @return the loader source
	 */
	private static Source readLoaderSource() {
		Source source = null;
		String path = "interfaces/driver-loader.js";
		try (
			InputStream in = DeviceDriver.class.getResourceAsStream("/" + path);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		) {
			log.info("Reading the JavaScript driver loader code from the resource JS file.");
			// Read the JavaScript loader code from the resource file.
			StringBuilder buffer = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				buffer.append(line);
				buffer.append("\n");
			}
			source = Source.newBuilder("js", buffer.toString(), "driver-loader.js").buildLiteral();
			log.debug("The JavaScript driver loader code has been read from the resource JS file.");
		}
		catch (Exception e) {
			log.error(MarkerFactory.getMarker("FATAL"), "Unable to read the Javascript driver loader.", e);
			System.err.println("NETSHOT FATAL ERROR");
			e.printStackTrace();
			System.exit(1);
		}
		return source;
	}

	/**
	 * The list of loaded newDrivers.
	 */
	private static Map<String, DeviceDriver> drivers = new ConcurrentHashMap<>();

	/**
	 * Hash for all drivers.
	 */
	private static String allDriverHash = "";

	/**
	 * Gets all loaded newDrivers.
	 * 
	 * @return the loaded newDrivers
	 */
	public static List<DeviceDriver> getAllDrivers() {
		List<DeviceDriver> allDrivers = new ArrayList<>(drivers.values());
		Collections.sort(allDrivers);
		return allDrivers;
	}

	/**
	 * Gets a driver from its name.
	 * 
	 * @param name
	 *                 the name of the device driver
	 * @return the device driver from that name, or null if not found
	 */
	public static DeviceDriver getDriverByName(String name) {
		if (name == null) {
			return null;
		}
		return drivers.get(name);
	}

	/**
	 * Gets a driver by description.
	 * 
	 * @param description The description to look for
	 * @return the device driver from that name, or null if not found
	 */
	public static DeviceDriver getDriverByDescription(String description) {
		if (description == null) {
			return null;
		}
		for (DeviceDriver driver : getAllDrivers()) {
			if (description.equalsIgnoreCase(driver.getDescription())) {
				return driver;
			}
		}
		return null;
	}

	/**
	 * Gets all loaded newDrivers as a hash.
	 * 
	 * @return the hash of loaded newDrivers
	 */
	public static Map<String, DeviceDriver> getDrivers() {
		return drivers;
	}

	/**
	 * Get a global hash for all drivers combined.
	 * @return the hash (hex-encoded string)
	 */
	public static String getAllDriverHash() {
		return allDriverHash;
	}

	/**
	 * Reloads all the newDrivers from disk.
	 * 
	 * @throws Exception
	 *                       something bad
	 */
	public static void refreshDrivers() throws Exception {
		Map<String, DeviceDriver> newDrivers = new HashMap<>();

		final String addPath = Netshot.getConfig("netshot.drivers.path");
		if (addPath != null) {
			final File folder = new File(addPath);
			if (folder.isDirectory()) {
				File[] files = folder.listFiles(new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						return pathname.isFile() && pathname.getName().endsWith(".js");
					}
				});
				for (File file : files) {
					log.info("Found user device driver file {}.", file);
					Reader reader = null;
					try {
						InputStream stream = new FileInputStream(file);
						reader = new InputStreamReader(stream);
						DeviceDriver driver = new DeviceDriver(reader, file.getName(),
							new Location(LocationType.FILE, file.getAbsolutePath()));
						if (newDrivers.containsKey(driver.getName())) {
							log.warn(
								"Skipping user device driver file {}, because a similar driver {} is already loaded.",
								file, driver.getName());
						}
						else {
							newDrivers.put(driver.getName(), driver);
						}
					}
					catch (Exception e) {
						log.error("Error while loading user device driver {}.", file, e);
					}
					finally {
						if (reader != null) {
							reader.close();
						}
					}
				}
			}
			else {
				log.error("Unable to open {} to find device drivers.", addPath);
			}
		}

		String driverPathName = "drivers/";
		final URL driverUrl = DeviceDriver.class.getResource("/" + driverPathName);
		if (driverUrl != null && "file".equals(driverUrl.getProtocol())) {
			log.debug("Scanning folder {} for device drivers (.js).", driverUrl);
			final File folder = new File(driverUrl.toURI());
			if (folder.isDirectory()) {
				File[] files = folder.listFiles(new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						return pathname.isFile() && pathname.getName().endsWith(".js");
					}
				});
				for (File file : files) {
					log.info("Found Netshot device driver file {}.", file);
					Reader reader = null;
					try {
						InputStream stream = DeviceDriver.class
							.getResourceAsStream("/" + driverPathName + file.getName());
						reader = new InputStreamReader(stream);
						DeviceDriver driver = new DeviceDriver(reader, file.getName(),
							new Location(LocationType.EMBEDDED, file.getAbsolutePath()));
						if (newDrivers.containsKey(driver.getName())) {
							log.warn(
								"Skipping user device driver file {}, because a similar driver {} is already loaded.",
								file, driver.getName());
						}
						else {
							newDrivers.put(driver.getName(), driver);
						}
					}
					catch (Exception e1) {
						log.error("Error while loading the device driver {}.", file, e1);
					}
					finally {
						if (reader != null) {
							reader.close();
						}
					}
				}
			}
		}
		if (driverUrl != null && "jar".equals(driverUrl.getProtocol())) {
			String path = driverUrl.getFile();
			path = path.substring(0, path.lastIndexOf('!'));
			File file = new File(new URI(path));
			try {
				JarFile jar = new JarFile(file);
				final Enumeration<JarEntry> e = jar.entries();
				while (e.hasMoreElements()) {
					final JarEntry je = e.nextElement();
					if (!je.isDirectory() && je.getName().startsWith(driverPathName)) {
						log.info("Found Netshot device driver file {} (in jar).", je.getName());
						Reader reader = null;
						try {
							InputStream stream = jar.getInputStream(je);
							reader = new InputStreamReader(stream);
							DeviceDriver driver = new DeviceDriver(reader,
								je.getName().replace(driverPathName, ""),
								new Location(LocationType.EMBEDDED, jar.getName()));
							if (newDrivers.containsKey(driver.getName())) {
								log.warn(
									"Skipping user device driver file {}, because a similar driver {} is already loaded.",
									file, driver.getName());
							}
							else {
								newDrivers.put(driver.getName(), driver);
							}
						}
						catch (Exception e1) {
							log.error("Error while loading the device driver {} from jar file.", file, e1);
						}
						finally {
							if (reader != null) {
								reader.close();
							}
						}
					}
				}
				jar.close();
			}
			catch (Exception e) {
				log.error("While looking for device drivers in {}.", path, e);
			}
		}

		StringBuffer hashBuffer = new StringBuffer();
		for (DeviceDriver driver : newDrivers.values()) {
			hashBuffer.append(driver.getSourceHash());
		}
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hash = digest.digest(hashBuffer.toString().getBytes());
		DeviceDriver.allDriverHash = Hex.encodeHexString(hash);
		DeviceDriver.drivers = newDrivers;
	}

	/** The name of the driver. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private String name;

	/** The author of the driver. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private String author;

	/** The description of the driver. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private String description;

	/** The version of the driver. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private String version;

	/**
	 * The priority of the driver for autodiscovery.
	 * 65536 by default, larger number gives higher priority.
	 */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private int priority;

	/** The device attributed provided by this driver. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private Set<AttributeDefinition> attributes = new HashSet<>();

	/** The device attributes as map for faster lookup. */
	private Map<AttributeDefinition.AttributeLevel, Map<String, AttributeDefinition>> attributesByName;

	/** The per-device, user-configurable options declared by this driver. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private Map<String, OptionDefinition> options = new HashMap<>();

	/** The protocols provided by this driver. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private Set<DriverProtocol> protocols = new HashSet<>();

	/**
	 * The main CLI modes supported by this driver (e.g. 'enable', 'configure',
	 * etc.)
	 */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private Set<String> cliMainModes = new HashSet<>();

	/** Set to true if the driver can analyze SNMP traps. */
	private boolean canAnalyzeTraps = true;

	/** Set to true if the driver can analyze syslog messages. */
	private boolean canAnalyzeSyslog = true;

	/**
	 * Set to true if the driver can identify a relevant device based on SNMP
	 * sysObjectId and name.
	 */
	private boolean canSnmpAutodiscover = true;

	/** The source code. */
	private Source source;

	/** Source hash. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private String sourceHash;

	/** Driver file location. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private Location location;

	/** The execution engine (for eval caching). */
	private Engine engine;

	/**
	 * Describes one named access (e.g. "ssh", "alternateSsh", "snmpv1", "https")
	 * declared by the driver, generalizing what used to be a single driver-wide
	 * SSH/Telnet config. A driver may declare several accesses of the same
	 * protocol (e.g. a secondary SSH access on another port).
	 */
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.NONE)
	public static final class AccessDefinition {
		/** The JS key this access was declared under (e.g. "alternateSsh"). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		private final String name;

		/** The protocol family (SSH, TELNET, SNMPV1/V2C/V3 or HTTP). */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		private final DriverProtocol protocol;

		/** Optional human-readable description. */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		private final String description;

		/** Populated only when protocol == SSH. */
		@Getter
		private final SshConfig sshConfig;

		/** Populated only when protocol == TELNET. */
		@Getter
		private final TelnetConfig telnetConfig;

		/** Populated only when protocol == HTTP. */
		@Getter
		private final HttpConfig httpConfig;

		/**
		 * The driver-declared default port for this access - TCP for SSH/Telnet/
		 * HTTP/HTTPS, UDP for SNMP. This is what the frontend should display/
		 * prefill for a per-access address/port override.
		 */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		private final int defaultPort;

		/**
		 * The group this access belongs to, used to pick alternatives among
		 * several accesses serving the same purpose (e.g. "cli" for SSH/Telnet,
		 * "snmp" for SNMPv1/v2c/v3, "http" for HTTP/HTTPS). Defaults to the
		 * access's protocol family (see {@link #defaultGroupFor}) but may be
		 * overridden by the driver, including to a fully custom value.
		 */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		private final String group;

		/**
		 * The priority used to order accesses within the same {@link #group}
		 * (higher = tried first). Defaults per protocol (see
		 * {@link #defaultPriorityFor}) but may be overridden by the driver.
		 */
		@Getter(onMethod = @__({
			@XmlElement, @JsonView(DefaultView.class)
		}))
		private final int priority;

		/**
		 * Convenience constructor defaulting {@link #group}/{@link #priority}
		 * from the access's protocol. Kept so existing call sites (mainly
		 * tests) that predate the group/priority feature keep compiling.
		 * @param name the access name
		 * @param protocol the access protocol
		 * @param description the access description
		 * @param sshConfig the SSH config, if applicable
		 * @param telnetConfig the Telnet config, if applicable
		 * @param httpConfig the HTTP config, if applicable
		 * @param defaultPort the default port to use
		 */
		public AccessDefinition(String name, DriverProtocol protocol, String description,
				SshConfig sshConfig, TelnetConfig telnetConfig, HttpConfig httpConfig, int defaultPort) {
			this(name, protocol, description, sshConfig, telnetConfig, httpConfig, defaultPort,
				defaultGroupFor(protocol), defaultPriorityFor(protocol));
		}

		public AccessDefinition(String name, DriverProtocol protocol, String description,
				SshConfig sshConfig, TelnetConfig telnetConfig, HttpConfig httpConfig, int defaultPort,
				String group, int priority) {
			this.name = name;
			this.protocol = protocol;
			this.description = description;
			this.sshConfig = sshConfig;
			this.telnetConfig = telnetConfig;
			this.httpConfig = httpConfig;
			this.defaultPort = defaultPort;
			this.group = group;
			this.priority = priority;
		}

		/**
		 * The credential subtype able to authenticate against this access.
		 * @return the credential class to filter candidate credential sets on
		 */
		@Transient
		public Class<? extends DeviceCredentialSet> getCredentialClass() {
			switch (this.protocol) {
				case SSH:
					return DeviceSshAccount.class;
				case TELNET:
					return DeviceTelnetAccount.class;
				case HTTP:
				case HTTPS:
					return DeviceHttpAccount.class;
				case SNMPV1:
					return DeviceSnmpv1Community.class;
				case SNMPV3:
					return DeviceSnmpv3Community.class;
				case SNMPV2C:
				default:
					return DeviceSnmpv2cCommunity.class;
			}
		}
	}

	/**
	 * Default {@link AccessDefinition#getGroup()} for a protocol: the
	 * access's protocol family ("cli" for SSH/Telnet, "snmp" for any SNMP
	 * version, "http" for HTTP/HTTPS).
	 * @param protocol the protocol
	 * @return the default group name
	 */
	private static String defaultGroupFor(DriverProtocol protocol) {
		if (protocol.isSnmp()) {
			return "snmp";
		}
		if (protocol.isHttp()) {
			return "http";
		}
		return "cli";
	}

	/**
	 * Default {@link AccessDefinition#getPriority()} for a protocol, used to
	 * order accesses within the same group (higher = tried first).
	 * @param protocol the protocol
	 * @return the default priority
	 */
	private static int defaultPriorityFor(DriverProtocol protocol) {
		switch (protocol) {
			case SSH:
				return 100;
			case HTTPS:
				return 90;
			case SNMPV3:
				return 80;
			case HTTP:
				return 30;
			case SNMPV2C:
				return 22;
			case SNMPV1:
				return 20;
			case TELNET:
			default:
				return 10;
		}
	}

	/** The named accesses (CLI/SNMP/HTTP) declared by this driver, in declaration order. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	private Map<String, AccessDefinition> accessDefinitions = new LinkedHashMap<>();

	/**
	 * Gets a declared access by name.
	 * @param accessName the access name (JS key)
	 * @return the access definition, or null if not found
	 */
	@Transient
	public AccessDefinition getAccessDefinition(String accessName) {
		return this.accessDefinitions.get(accessName);
	}

	/**
	 * Gets the default (legacy/backward-compatible) list of CLI accesses to use
	 * when a driver doesn't explicitly call {@code client.create(...)}: every
	 * SSH/Telnet access, sorted by priority descending (higher-priority
	 * accesses tried first) - by default this means SSH before Telnet,
	 * matching historical behavior, but a driver may reorder this via
	 * explicit per-access priorities.
	 * @return the ordered list of default CLI access definitions
	 */
	@Transient
	public List<AccessDefinition> getDefaultCliAccessDefinitions() {
		return this.getAccessDefinitionsByGroup("cli");
	}

	/**
	 * Gets the default list of SNMP accesses to use when a driver doesn't
	 * explicitly call {@code client.create(...)}, sorted by priority
	 * descending (by default: SNMPv3, then v2c, then v1).
	 * @return the ordered list of default SNMP access definitions
	 */
	@Transient
	public List<AccessDefinition> getDefaultSnmpAccessDefinitions() {
		return this.getAccessDefinitionsByGroup("snmp");
	}

	/**
	 * Gets every declared access whose {@link AccessDefinition#getGroup()}
	 * equals the given name, sorted by {@link AccessDefinition#getPriority()}
	 * descending (stable, so equal-priority accesses keep declaration order).
	 * @param group the group name
	 * @return the matching access definitions, priority-sorted
	 */
	@Transient
	public List<AccessDefinition> getAccessDefinitionsByGroup(String group) {
		List<AccessDefinition> result = new ArrayList<>();
		for (AccessDefinition def : this.accessDefinitions.values()) {
			if (def.getGroup().equals(group)) {
				result.add(def);
			}
		}
		result.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
		return result;
	}

	/**
	 * Resolves a list of access names into their definitions.
	 * @param names the access names (already resolved from JS, literal keys)
	 * @return the matching access definitions, in the given order
	 */
	@Transient
	public List<AccessDefinition> getAccessDefinitions(List<String> names) {
		List<AccessDefinition> result = new ArrayList<>();
		for (String accessName : names) {
			AccessDefinition def = this.accessDefinitions.get(accessName);
			if (def == null) {
				throw new IllegalArgumentException("Unknown access '" + accessName + "'.");
			}
			result.add(def);
		}
		return result;
	}

	/** Instantiates a new device driver (empty constructor). */
	protected DeviceDriver() {
	}

	/**
	 * Instantiates a new device driver.
	 * 
	 * @param reader = The reader to access the JavaScript driver code
	 * @param sourceName = The name of the driver source
	 * @param location = The location of the driver
	 * @throws Exception when something went wrong
	 */
	public DeviceDriver(Reader reader, String sourceName, Location location) throws Exception {
		this.attributesByName = new HashMap<>();
		for (AttributeDefinition.AttributeLevel level : AttributeDefinition.AttributeLevel.values()) {
			this.attributesByName.put(level, new HashMap<>());
		}
		this.location = location;
		this.source = Source.newBuilder("js", reader, sourceName).buildLiteral();
		{
			// Compute source code hash
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(this.source.getCharacters().toString().getBytes());
			this.sourceHash = Hex.encodeHexString(hash, true);
		}
		this.engine = Engine.create();

		try (Context context = this.getContext()) {
			this.loadCode(context);
			try {
				Value info = context.getBindings("js").getMember("Info");
				this.name = info.getMember("name").asString();
				this.author = info.getMember("author").asString();
				this.description = info.getMember("description").asString();
				this.version = info.getMember("version").asString();
				try {
					this.priority = info.getMember("priority").asInt();
				}
				catch (Exception e) {
					this.priority = 65536;
				}
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid Info object.", e);
			}

			try {
				Value config = context.getBindings("js").getMember("Config");
				for (String key : config.getMemberKeys()) {
					if (key == null || !key.matches("^[a-z][a-zA-Z0-9]+$")) {
						throw new IllegalArgumentException(String.format("Invalid config item %s.", key));
					}
					try {
						Value data = config.getMember(key);
						AttributeDefinition item = new AttributeDefinition(this, AttributeLevel.CONFIG, key, data);
						this.attributes.add(item);
						this.attributesByName.get(AttributeLevel.CONFIG).put(item.getName(), item);
					}
					catch (IllegalArgumentException e) {
						throw new IllegalArgumentException(String.format("Invalid item %s in Config.", key), e);
					}
				}
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid Config object.", e);
			}
			try {
				Value device = context.getBindings("js").getMember("Device");
				for (String key : device.getMemberKeys()) {
					if (key == null || !key.matches("^[a-z][a-zA-Z0-9]+$")) {
						throw new IllegalArgumentException(String.format("Invalid device item %s.", key));
					}
					try {
						Value data = device.getMember(key);
						AttributeDefinition item = new AttributeDefinition(this, AttributeLevel.DEVICE, key, data);
						this.attributes.add(item);
						this.attributesByName.get(AttributeLevel.DEVICE).put(item.getName(), item);
					}
					catch (IllegalArgumentException e) {
						throw new IllegalArgumentException(String.format("Invalid item %s in Device.", key), e);
					}
				}
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid Device object.", e);
			}

			try {
				Value optionsBlock = context.getBindings("js").getMember("Options");
				if (optionsBlock != null && optionsBlock.hasMembers()) {
					for (String key : optionsBlock.getMemberKeys()) {
						if (key == null || !key.matches("^[a-z][a-zA-Z0-9]+$")) {
							throw new IllegalArgumentException(String.format("Invalid option item %s.", key));
						}
						try {
							Value data = optionsBlock.getMember(key);
							OptionDefinition item = new OptionDefinition(this, key, data);
							this.options.put(item.getName(), item);
						}
						catch (IllegalArgumentException e) {
							throw new IllegalArgumentException(String.format("Invalid item %s in Options.", key), e);
						}
					}
				}
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid Options object.", e);
			}

			try {
				Value cli = context.getBindings("js").getMember("CLI");
				if (cli != null && cli.hasMembers()) {
					for (String key : cli.getMemberKeys()) {
						Value accessValue = cli.getMember(key);
						if (accessValue == null || !accessValue.hasMembers()) {
							continue;
						}
						DriverProtocol accessProtocol;
						Value protocolValue = accessValue.getMember("protocol");
						if (protocolValue != null && protocolValue.isString()) {
							String p = protocolValue.asString().toLowerCase();
							if ("ssh".equals(p)) {
								accessProtocol = DriverProtocol.SSH;
							}
							else if ("telnet".equals(p)) {
								accessProtocol = DriverProtocol.TELNET;
							}
							else {
								throw new IllegalArgumentException(
									String.format("Invalid protocol '%s' for CLI access '%s'.", p, key));
							}
						}
						else if ("ssh".equalsIgnoreCase(key)) {
							accessProtocol = DriverProtocol.SSH;
						}
						else if ("telnet".equalsIgnoreCase(key)) {
							accessProtocol = DriverProtocol.TELNET;
						}
						else {
							// Not an access definition (e.g. a CLI mode like 'enable', 'username', etc.)
							continue;
						}
						String accessDescription = null;
						Value descriptionValue = accessValue.getMember("description");
						if (descriptionValue != null && descriptionValue.isString()) {
							accessDescription = descriptionValue.asString();
						}
						this.protocols.add(accessProtocol);
						SshConfig accessSshConfig = null;
						TelnetConfig accessTelnetConfig = null;
						Value configValue = accessValue.getMember("config");
						int defaultPort;
						if (accessProtocol == DriverProtocol.SSH) {
							accessSshConfig = parseSshConfig(configValue);
							defaultPort = net.netshot.netshot.device.access.Ssh.DEFAULT_PORT;
						}
						else {
							accessTelnetConfig = parseTelnetConfig(configValue);
							defaultPort = net.netshot.netshot.device.access.Telnet.DEFAULT_PORT;
						}
						if (configValue != null && configValue.hasMembers()) {
							Value defaultPortValue = configValue.getMember("defaultPort");
							if (defaultPortValue != null) {
								defaultPort = defaultPortValue.asInt();
							}
						}
						Value macros = accessValue.getMember("macros");
						if (macros != null && macros.hasMembers()) {
							this.cliMainModes.addAll(macros.getMemberKeys());
						}
						this.accessDefinitions.put(key, new AccessDefinition(key, accessProtocol,
							accessDescription, accessSshConfig, accessTelnetConfig, null, defaultPort,
							this.parseGroup(accessValue, accessProtocol), this.parsePriority(accessValue, accessProtocol)));
					}
				}
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid CLI object.", e);
			}

			try {
				Value snmp = context.getBindings("js").getMember("SNMP");
				if (snmp != null && snmp.hasMembers()) {
					for (String key : snmp.getMemberKeys()) {
						Value accessValue = snmp.getMember(key);
						if (accessValue == null || !accessValue.hasMembers()) {
							continue;
						}
						DriverProtocol accessProtocol;
						Value protocolValue = accessValue.getMember("protocol");
						if (protocolValue != null && protocolValue.isString()) {
							String p = protocolValue.asString().toLowerCase();
							if ("snmpv1".equals(p)) {
								accessProtocol = DriverProtocol.SNMPV1;
							}
							else if ("snmpv3".equals(p)) {
								accessProtocol = DriverProtocol.SNMPV3;
							}
							else if ("snmpv2c".equals(p)) {
								accessProtocol = DriverProtocol.SNMPV2C;
							}
							else {
								throw new IllegalArgumentException(
									String.format("Invalid protocol '%s' for SNMP access '%s'.", p, key));
							}
						}
						else if ("snmpv1".equalsIgnoreCase(key)) {
							accessProtocol = DriverProtocol.SNMPV1;
						}
						else if ("snmpv3".equalsIgnoreCase(key)) {
							accessProtocol = DriverProtocol.SNMPV3;
						}
						else if ("snmpv2c".equalsIgnoreCase(key)) {
							accessProtocol = DriverProtocol.SNMPV2C;
						}
						else {
							throw new IllegalArgumentException(
								String.format("Invalid SNMP access key '%s': must be one of 'snmpv1', 'snmpv2c', 'snmpv3', "
									+ "or declare an explicit 'protocol'.", key));
						}
						String accessDescription = null;
						Value descriptionValue = accessValue.getMember("description");
						if (descriptionValue != null && descriptionValue.isString()) {
							accessDescription = descriptionValue.asString();
						}
						this.protocols.add(accessProtocol);
						this.accessDefinitions.put(key, new AccessDefinition(key, accessProtocol,
							accessDescription, null, null, null, net.netshot.netshot.device.access.Snmp.DEFAULT_PORT,
							this.parseGroup(accessValue, accessProtocol), this.parsePriority(accessValue, accessProtocol)));
					}
				}
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid SNMP object.", e);
			}

			try {
				Value http = context.getBindings("js").getMember("HTTP");
				if (http != null && http.hasMembers()) {
					for (String key : http.getMemberKeys()) {
						Value accessValue = http.getMember(key);
						if (accessValue == null || !accessValue.hasMembers()) {
							continue;
						}
						DriverProtocol accessProtocol;
						Value protocolValue = accessValue.getMember("protocol");
						if (protocolValue != null && protocolValue.isString()) {
							String p = protocolValue.asString().toLowerCase();
							if ("https".equals(p)) {
								accessProtocol = DriverProtocol.HTTPS;
							}
							else if ("http".equals(p)) {
								accessProtocol = DriverProtocol.HTTP;
							}
							else {
								throw new IllegalArgumentException(
									String.format("Invalid protocol '%s' for HTTP access '%s'.", p, key));
							}
						}
						else if ("http".equalsIgnoreCase(key)) {
							accessProtocol = DriverProtocol.HTTP;
						}
						else {
							accessProtocol = DriverProtocol.HTTPS;
						}
						String accessDescription = null;
						Value descriptionValue = accessValue.getMember("description");
						if (descriptionValue != null && descriptionValue.isString()) {
							accessDescription = descriptionValue.asString();
						}
						HttpConfig httpConfig = new HttpConfig();
						httpConfig.setTls(accessProtocol == DriverProtocol.HTTPS);
						Value configValue = accessValue.getMember("config");
						if (configValue != null && configValue.hasMembers()) {
							Value basePath = configValue.getMember("basePath");
							if (basePath != null && basePath.isString()) {
								httpConfig.setBasePath(basePath.asString());
							}
							Value defaultPort = configValue.getMember("defaultPort");
							if (defaultPort != null) {
								httpConfig.setDefaultPort(defaultPort.asInt());
							}
							else {
								httpConfig.setDefaultPort(httpConfig.isTls() ? Http.DEFAULT_PORT : 80);
							}
						}
						else {
							httpConfig.setDefaultPort(httpConfig.isTls() ? Http.DEFAULT_PORT : 80);
						}
						Value authValue = accessValue.getMember("auth");
						if (authValue != null && authValue.hasMembers()) {
							httpConfig.setAuth(parseHttpAuthScheme(authValue, key));
						}
						this.protocols.add(accessProtocol);
						this.accessDefinitions.put(key, new AccessDefinition(key, accessProtocol,
							accessDescription, null, null, httpConfig, httpConfig.getDefaultPort(),
							this.parseGroup(accessValue, accessProtocol), this.parsePriority(accessValue, accessProtocol)));
					}
				}
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid HTTP object.", e);
			}

			if (this.protocols.isEmpty()) {
				throw new IllegalArgumentException("Invalid driver, it supports neither Telnet, SSH, SNMP nor HTTP/HTTPS.");
			}

			try {
				if (!context.getBindings("js").getMember("snapshot").canExecute()) {
					throw new Exception();
				}
			}
			catch (Exception e) {
				throw new IllegalArgumentException("Invalid driver, the 'snapshot' function cannot be found.");
			}

			log.info("Loaded driver {} version {}.", this.name, this.version);
		}
	}

	/**
	 * Reads the optional "group" member off an access's JS object, defaulting
	 * to {@link #defaultGroupFor} when not set.
	 * @param accessValue the JS access object
	 * @param protocol the access's protocol (for the default)
	 * @return the effective group name
	 */
	private String parseGroup(Value accessValue, DriverProtocol protocol) {
		Value groupValue = accessValue.getMember("group");
		if (groupValue != null && groupValue.isString()) {
			return groupValue.asString();
		}
		return defaultGroupFor(protocol);
	}

	/**
	 * Reads the optional "priority" member off an access's JS object,
	 * defaulting to {@link #defaultPriorityFor} when not set.
	 * @param accessValue the JS access object
	 * @param protocol the access's protocol (for the default)
	 * @return the effective priority
	 */
	private int parsePriority(Value accessValue, DriverProtocol protocol) {
		Value priorityValue = accessValue.getMember("priority");
		if (priorityValue != null && priorityValue.fitsInInt()) {
			return priorityValue.asInt();
		}
		return defaultPriorityFor(protocol);
	}

	/**
	 * Parses the "config" object of a CLI SSH access into an {@link SshConfig}.
	 * Also used to parse the {@code sshConfig} advanced option of
	 * {@code client.create(...)} (see {@code JsClientFactory}), in which case
	 * the result is a delta merged onto the driver-declared config via
	 * {@link net.netshot.netshot.device.access.Ssh#applySshConfig}.
	 * @param cliSshConfig the JS "config" object (may be null)
	 * @return the populated SshConfig
	 */
	public static SshConfig parseSshConfig(Value cliSshConfig) {
		SshConfig sshConfig = new SshConfig(false);
		if (cliSshConfig != null && cliSshConfig.hasMembers()) {
			Value terminal = cliSshConfig.getMember("terminal");
			if (terminal != null && terminal.hasMembers()) {
				Value usePty = terminal.getMember("pty");
				if (usePty != null) {
					sshConfig.setUsePty(usePty.asBoolean());
				}
				Value vtType = terminal.getMember("type");
				if (vtType != null) {
					sshConfig.setTerminalType(vtType.asString());
				}
				Value vtHeight = terminal.getMember("height");
				if (vtHeight != null) {
					sshConfig.setTerminalHeight(vtHeight.asInt());
				}
				Value vtWidth = terminal.getMember("width");
				if (vtWidth != null) {
					sshConfig.setTerminalWidth(vtWidth.asInt());
				}
				Value vtRows = terminal.getMember("rows");
				if (vtRows != null) {
					sshConfig.setTerminalRows(vtRows.asInt());
					if (vtHeight == null) {
						sshConfig.setTerminalHeight(vtRows.asInt() * 8);
					}
				}
				Value vtCols = terminal.getMember("cols");
				if (vtCols != null) {
					sshConfig.setTerminalCols(vtCols.asInt());
					if (vtWidth == null) {
						sshConfig.setTerminalWidth(vtCols.asInt() * 20);
					}
				}
			}
			{
				Value kexAlgorithms = cliSshConfig.getMember("kexAlgorithms");
				if (kexAlgorithms != null && kexAlgorithms.hasArrayElements()) {
					Set<String> algos = new HashSet<>();
					for (long i = 0; i < kexAlgorithms.getArraySize(); i++) {
						Value algo = kexAlgorithms.getArrayElement(i);
						algos.add(algo.asString());
					}
					sshConfig.setKexAlgorithms(algos.toArray(new String[0]));
				}
			}
			{
				Value hostKeyAlgorithms = cliSshConfig.getMember("hostKeyAlgorithms");
				if (hostKeyAlgorithms != null && hostKeyAlgorithms.hasArrayElements()) {
					Set<String> algos = new HashSet<>();
					for (long i = 0; i < hostKeyAlgorithms.getArraySize(); i++) {
						Value algo = hostKeyAlgorithms.getArrayElement(i);
						algos.add(algo.asString());
					}
					sshConfig.setHostKeyAlgorithms(algos.toArray(new String[0]));
				}
			}
			{
				Value ciphers = cliSshConfig.getMember("ciphers");
				if (ciphers != null && ciphers.hasArrayElements()) {
					Set<String> algos = new HashSet<>();
					for (long i = 0; i < ciphers.getArraySize(); i++) {
						Value algo = ciphers.getArrayElement(i);
						algos.add(algo.asString());
					}
					sshConfig.setCiphers(algos.toArray(new String[0]));
				}
			}
			{
				Value macs = cliSshConfig.getMember("macs");
				if (macs != null && macs.hasArrayElements()) {
					Set<String> algos = new HashSet<>();
					for (long i = 0; i < macs.getArraySize(); i++) {
						Value algo = macs.getArrayElement(i);
						algos.add(algo.asString());
					}
					sshConfig.setMacs(algos.toArray(new String[0]));
				}
			}
			{
				Value compressionAlgorithms = cliSshConfig.getMember("compressionAlgorithms");
				if (compressionAlgorithms != null && compressionAlgorithms.hasArrayElements()) {
					Set<String> algos = new HashSet<>();
					for (long i = 0; i < compressionAlgorithms.getArraySize(); i++) {
						Value algo = compressionAlgorithms.getArrayElement(i);
						algos.add(algo.asString());
					}
					sshConfig.setCompressionAlgorithms(algos.toArray(new String[0]));
				}
			}
			{
				Value rekey = cliSshConfig.getMember("rekey");
				if (rekey != null && rekey.hasMembers()) {
					Value timeLimit = rekey.getMember("timeLimit");
					if (timeLimit != null) {
						sshConfig.setRekeyTimeLimit(timeLimit.asLong());
					}
					Value dataLimit = rekey.getMember("dataLimit");
					if (dataLimit != null) {
						sshConfig.setRekeyDataLimit(dataLimit.asLong());
					}
					Value blocksLimit = rekey.getMember("blocksLimit");
					if (blocksLimit != null) {
						sshConfig.setRekeyBlocksLimit(blocksLimit.asLong());
					}
					Value packetsLimit = rekey.getMember("packetsLimit");
					if (packetsLimit != null) {
						sshConfig.setRekeyPacketsLimit(packetsLimit.asLong());
					}
				}
			}
			Value auth = cliSshConfig.getMember("auth");
			if (auth != null && auth.hasMembers()) {
				Value interactive = auth.getMember("interactive");
				if (interactive != null && interactive.hasMembers()) {
					Value interactions = interactive.getMember("interactions");
					if (interactions != null && interactions.hasArrayElements()) {
						List<SshInteractionInstruction> instructions = new ArrayList<>();
						for (long i = 0; i < interactions.getArraySize(); i++) {
							Value interaction = interactions.getArrayElement(i);
							Value echo = interaction.getMember("echo");
							Pattern promptPattern = null;
							Value prompt = interaction.getMember("prompt");
							if (prompt != null) {
								try {
									promptPattern = JsUtils.jsRegExpToPattern(prompt);
								}
								catch (Exception e) {
									throw new IllegalArgumentException(
										"Invalid RegExp used as SSH interaction prompt (index %d)".formatted(i));
								}
							}
							Value response = interaction.getMember("response");
							instructions.add(new SshInteractionInstruction(
								promptPattern,
								(echo != null && echo.isBoolean()) ? echo.asBoolean() : null,
								(response != null && response.isString()) ? response.asString() : null
							));
						}
						sshConfig.setInteractionInstructions(instructions);
					}
				}
			}
		}
		return sshConfig;
	}

	/**
	 * Parses the "config" object of a CLI Telnet access into a {@link TelnetConfig}
	 * delta (only the fields actually present in {@code cliTelnetConfig} are set,
	 * the rest are left {@code null}). Also used to parse the {@code telnetConfig}
	 * advanced option of {@code client.create(...)} (see {@code JsClientFactory}),
	 * in which case the result is merged onto the driver-declared config via
	 * {@link net.netshot.netshot.device.access.Telnet#applyTelnetConfig}.
	 * @param cliTelnetConfig the JS "config" object (may be null)
	 * @return the populated TelnetConfig delta
	 */
	public static TelnetConfig parseTelnetConfig(Value cliTelnetConfig) {
		TelnetConfig telnetConfig = new TelnetConfig(false);
		if (cliTelnetConfig != null && cliTelnetConfig.hasMembers()) {
			Value terminal = cliTelnetConfig.getMember("terminal");
			if (terminal != null && terminal.hasMembers()) {
				Value vtType = terminal.getMember("type");
				if (vtType != null) {
					telnetConfig.setTerminalType(vtType.asString());
				}
				Value vtCols = terminal.getMember("cols");
				if (vtCols != null) {
					telnetConfig.setTerminalCols(vtCols.asInt());
				}
				Value vtRows = terminal.getMember("rows");
				if (vtRows != null) {
					telnetConfig.setTerminalRows(vtRows.asInt());
				}
			}
		}
		return telnetConfig;
	}

	/**
	 * Parses the "auth" object of an HTTP access into an {@link AuthScheme},
	 * OpenAPI-securitySchemes-inspired. "oauth2"/"openIdConnect" shapes are
	 * recognized but the actual IdP token fetch/cache/refresh flow is a
	 * deliberately deferred fast-follow, not implemented in this phase.
	 * <p>
	 * The "type"/"scheme"/"in" values are driver-authored constants (part of
	 * the documented schema, e.g. "apiKey"), not free-form user input, so they
	 * are matched with exact case - a typo like "apikey" is rejected rather
	 * than silently accepted.
	 * @param authValue the JS "auth" object
	 * @param accessName the name of the HTTP access (for error messages)
	 * @return the populated AuthScheme
	 */
	public static AuthScheme parseHttpAuthScheme(Value authValue, String accessName) {
		AuthScheme auth = new AuthScheme();
		Value typeValue = authValue.getMember("type");
		if (typeValue == null || !typeValue.isString()) {
			throw new IllegalArgumentException(
				String.format("Missing 'type' in auth scheme of HTTP access '%s'.", accessName));
		}
		String type = typeValue.asString();
		auth.setType(type);
		if ("http".equals(type)) {
			Value schemeValue = authValue.getMember("scheme");
			String scheme = (schemeValue != null && schemeValue.isString()) ? schemeValue.asString() : "basic";
			if (!"basic".equals(scheme) && !"bearer".equals(scheme)) {
				throw new IllegalArgumentException(
					String.format("Invalid 'scheme' '%s' in auth scheme of HTTP access '%s'.", scheme, accessName));
			}
			auth.setScheme(scheme);
		}
		else if ("apiKey".equals(type)) {
			Value inValue = authValue.getMember("in");
			String in = (inValue != null && inValue.isString()) ? inValue.asString() : "header";
			if (!"header".equals(in) && !"query".equals(in) && !"cookie".equals(in)) {
				throw new IllegalArgumentException(
					String.format("Invalid 'in' '%s' in auth scheme of HTTP access '%s'.", in, accessName));
			}
			auth.setIn(in);
			Value nameValue = authValue.getMember("name");
			if (nameValue == null || !nameValue.isString()) {
				throw new IllegalArgumentException(
					String.format("Missing 'name' in apiKey auth scheme of HTTP access '%s'.", accessName));
			}
			auth.setName(nameValue.asString());
		}
		else if ("cookie".equals(type)) {
			Value methodValue = authValue.getMember("method");
			String method = (methodValue != null && methodValue.isString()) ? methodValue.asString() : "post";
			if (!"post".equals(method) && !"put".equals(method)) {
				throw new IllegalArgumentException(
					String.format("Invalid 'method' '%s' in cookie auth scheme of HTTP access '%s'.", method, accessName));
			}
			auth.setMethod(method.toUpperCase());
			Value pathValue = authValue.getMember("path");
			if (pathValue == null || !pathValue.isString()) {
				throw new IllegalArgumentException(
					String.format("Missing 'path' in cookie auth scheme of HTTP access '%s'.", accessName));
			}
			auth.setPath(pathValue.asString());
			Value dataValue = authValue.getMember("data");
			if (dataValue != null && dataValue.hasMembers()) {
				Object data = JsUtils.valueToJavaObject(dataValue);
				if (data instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> dataMap = (Map<String, Object>) data;
					auth.setData(dataMap);
				}
			}
			Value contentTypeValue = authValue.getMember("contentType");
			String contentType = (contentTypeValue != null && contentTypeValue.isString())
				? contentTypeValue.asString() : "json";
			if (!"json".equals(contentType) && !"form".equals(contentType)) {
				throw new IllegalArgumentException(String.format(
					"Invalid 'contentType' '%s' in cookie auth scheme of HTTP access '%s'.", contentType, accessName));
			}
			auth.setContentType(contentType);
		}
		else if (!"oauth2".equals(type) && !"openIdConnect".equals(type)) {
			throw new IllegalArgumentException(
				String.format("Invalid auth 'type' '%s' in HTTP access '%s'.", type, accessName));
		}
		return auth;
	}

	/**
	 * Asks the driver to analyze a syslog message.
	 *
	 * @param message
	 *                    The syslog message
	 * @param ip
	 *                    The IP address the message is coming from
	 * @return true to trigger a snapshot of the device
	 */
	public boolean analyzeSyslog(String message, Network4Address ip) {
		if (!canAnalyzeSyslog) {
			return false;
		}
		try (Context context = this.getContext()) {
			this.loadCode(context);
			Value result = context.getBindings("js")
				.getMember("_analyzeSyslog")
				.execute(message, JS_SYSLOG_LOGGER);
			if (result != null && result.isBoolean()) {
				return result.asBoolean();
			}
		}
		catch (Exception e) {
			if (e instanceof PolyglotException && e.getMessage() != null
				&& e.getMessage().contains("No analyzeSyslog function.")) {
				log.info("The driver {} has no analyzeSyslog function. Won't be called again.", name);
				this.canAnalyzeSyslog = false;
			}
			else {
				log.error("Error while running _analyzeSyslog function on driver {}.", name, e);
			}
		}
		return false;
	}

	public boolean analyzeTrap(Map<String, Object> data, Network4Address ip) {
		if (!canAnalyzeTraps) {
			return false;
		}
		try (Context context = this.getContext()) {
			this.loadCode(context);
			Value result = context.getBindings("js")
				.getMember("_analyzeTrap")
				.execute(ProxyObject.fromMap(data), JS_SNMP_LOGGER);
			if (result != null && result.isBoolean()) {
				return result.asBoolean();
			}
		}
		catch (Exception e) {
			if (e instanceof PolyglotException && e.getMessage() != null
				&& e.getMessage().contains("No analyzeTrap function.")) {
				log.info("The driver {} has no analyzeTrap function. Won't be called again.", name);
				this.canAnalyzeTraps = false;
			}
			else {
				log.error("Error while running _analyzeTrap function on driver {}.", name, e);
			}
		}
		return false;
	}

	@Transient
	public final Context getContext() throws IOException {
		log.debug("Getting context");
		HostAccess hostAccess = HostAccess
			.newBuilder()
			.allowAccessAnnotatedBy(HostAccess.Export.class)
			.allowImplementationsAnnotatedBy(HostAccess.Implementable.class)
			.allowImplementationsAnnotatedBy(FunctionalInterface.class)
			.allowAccessInheritance(true)
			.build();
		Context.Builder builder = Context
			.newBuilder("js", "python")
			.allowHostAccess(hostAccess);
		Context context;
		synchronized (engine) {
			context = builder.engine(engine).build();
		}
		log.debug("Context is ready");
		return context;
	}

	/**
	 * Load driver JS code.
	 * @param context the context to load code into
	 */
	public final void loadCode(Context context) {
		context.eval(this.source);
		context.eval(JSLOADER_SOURCE);
	}

	/**
	 * Asks the driver to analyze SNMP information.
	 * @param task The auto discovery task
	 * @param sysObjectId The received sysObjectId
	 * @param sysDesc The received sysDesc
	 * @param taskContext The logger from the task
	 * @return true if the passed SNMP information matches a device of this driver
	 */
	public boolean snmpAutoDiscover(Task task, String sysObjectId, String sysDesc, TaskContext taskContext) {
		if (!canSnmpAutodiscover) {
			return false;
		}
		try (Context context = this.getContext()) {
			this.loadCode(context);
			Value result = context
				.getBindings("js")
				.getMember("_snmpAutoDiscover")
				.execute(sysObjectId, sysDesc, taskContext);
			if (result != null && result.isBoolean()) {
				return result.asBoolean();
			}
		}
		catch (Exception e) {
			if (e instanceof PolyglotException && e.getMessage() != null
				&& e.getMessage().contains("No snmpAutoDiscover function.")) {
				log.info("The driver {} has no snmpAutoDiscover function.", name);
				this.canSnmpAutodiscover = false;
			}
			else {
				log.error("Error while running _snmpAutoDiscover function on driver {}.", name, e);
			}
		}
		return false;
	}

	@Transient
	public AttributeDefinition getAttributeDefinition(AttributeDefinition.AttributeLevel attributeLevel, String attributeName) {
		return this.attributesByName.get(attributeLevel).get(attributeName);
	}

	@Transient
	public AttributeDefinition getAttributeDefinitionByTitle(String title) {
		for (AttributeDefinition attribute : this.getAttributes()) {
			if (attribute.getTitle().equalsIgnoreCase(title)) {
				return attribute;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("DeviceDriver [");
		if (name != null) {
			builder.append("name=");
			builder.append(name);
			builder.append(", ");
		}
		if (author != null) {
			builder.append("author=");
			builder.append(author);
			builder.append(", ");
		}
		if (description != null) {
			builder.append("description=");
			builder.append(description);
			builder.append(", ");
		}
		builder.append("version=");
		builder.append(version);
		builder.append(", ");
		builder.append("priority=");
		builder.append(Integer.toString(priority));
		builder.append("]");
		return builder.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof DeviceDriver)) {
			return false;
		}
		DeviceDriver other = (DeviceDriver) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		}
		else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (name == null ? 0 : name.hashCode());
		return result;
	}

	@Override
	public int compareTo(DeviceDriver o) {
		if (o == this) {
			return 0;
		}
		if (o == null) {
			return -1;
		}
		int r = Integer.compare(this.priority, o.priority);
		if (r != 0) {
			return -r;
		}
		return this.name.compareTo(o.name);
	}

}
