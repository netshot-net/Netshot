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
package net.netshot.netshot.inventory.device;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import lombok.Builder;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.driver.AttributeDefinition.EnumAttribute;
import net.netshot.netshot.driver.DrivenDevice;
import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.group.DeviceGroup;

/**
 * A device, as it is loaded with everything most of the use cases read: its domain, its
 * accesses (with their credential sets), its device level attributes, its (current) modules
 * and its interfaces. Its configurations and diagnostic results are loaded apart.
 *
 * @param id the ID (0 for a device which isn't stored yet)
 * @param version the version, incremented on each change
 * @param changeDate the time of the last change
 * @param name the name (as reported by the device)
 * @param comments the comments
 * @param contact the contact (as reported by the device)
 * @param location the location (as reported by the device)
 * @param family the family (model, as reported by the device)
 * @param createdDate when the device was created
 * @param creator who created the device
 * @param driver the name of the driver
 * @param mgmtAddress the management address (IPv4 literal, IPv6 literal, or FQDN, resolved lazily at connect time)
 * @param cachedIpAddress the last IP address that the management address resolved to, refreshed on every
 *        successful connection attempt. Used for fast IP-based search/dedup/passive matching, since the
 *        management address itself may be a hostname.
 * @param mgmtDomain the management domain
 * @param networkClass the network class
 * @param serialNumber the serial number
 * @param softwareLevel the software conformance level
 * @param softwareVersion the software version
 * @param status the status
 * @param eolDate the end of life date
 * @param eolModule the module giving the end of life date, if any
 * @param eosDate the end of sale date
 * @param eosModule the module giving the end of sale date, if any
 * @param lastConfigId the ID of the last configuration, or null if there is none
 * @param compliant whether the device has no non conforming compliance check result (computed)
 * @param options the user-configured, per-device values for the options declared by the
 *        device's driver (e.g. "force full backup"). Unlike the attributes, which the driver
 *        populates during a snapshot, these are set by the user and only read by the driver at
 *        runtime. Values are typed to match their option's declared type (a real {@code Boolean}
 *        for a BOOLEAN option, a {@code String} for TEXT), stored as-is in a JSON column.
 * @param tags the tags (sorted)
 * @param virtualDevices the virtual devices
 * @param vrfInstances the VRF instances
 * @param attributes the device level attributes
 * @param accesses the per-access configuration (address/port overrides and credential pins),
 *        one per access name (e.g. "ssh", "telnet", "https") - see {@link DeviceAccess}
 * @param modules the modules currently in the device (not the removed ones)
 * @param networkInterfaces the network interfaces
 * @param ownerGroups the groups the device is a member of, by ID (read only)
 */
@Builder(toBuilder = true)
public record Device(
	long id,
	int version,
	Instant changeDate,
	String name,
	String comments,
	String contact,
	String location,
	String family,
	Instant createdDate,
	String creator,
	String driver,
	String mgmtAddress,
	InetAddress cachedIpAddress,
	Domain mgmtDomain,
	NetworkClass networkClass,
	String serialNumber,
	ConformanceLevel softwareLevel,
	String softwareVersion,
	Status status,
	Instant eolDate,
	Module eolModule,
	Instant eosDate,
	Module eosModule,
	Long lastConfigId,
	boolean compliant,
	Map<String, Object> options,
	Set<String> tags,
	Set<String> virtualDevices,
	Set<String> vrfInstances,
	List<DeviceAttribute> attributes,
	List<DeviceAccess> accesses,
	List<Module> modules,
	List<NetworkInterface> networkInterfaces,
	List<DeviceGroup> ownerGroups) implements DrivenDevice {

	@Override
	public long deviceId() {
		return this.id;
	}

	@Override
	public String driverName() {
		return this.driver;
	}

	/**
	 * The network class of a device.
	 */
	public enum NetworkClass implements EnumAttribute {

		/** The firewall. */
		FIREWALL,

		/** The loadbalancer. */
		LOADBALANCER,

		/** The router. */
		ROUTER,

		/** The server. */
		SERVER,

		/** The switch. */
		SWITCH,

		/** The switchrouter. */
		SWITCHROUTER,

		/** The accesspoint. */
		ACCESSPOINT,

		/** The wirelesscontroller. */
		WIRELESSCONTROLLER,

		/** The consoleserver. */
		CONSOLESERVER,

		/** Unknown network class. */
		UNKNOWN,

		/** Voice Gateway. */
		VOICEGATEWAY,
	}

	/**
	 * The status of a device.
	 */
	public enum Status implements EnumAttribute {

		/** The disabled. */
		DISABLED,

		/** The inproduction. */
		INPRODUCTION,

		/** The preproduction. */
		PREPRODUCTION
	}

	/** The name of a device which hasn't been snapshot yet. */
	public static final String DEFAULTNAME = "[NONAME]";

	/** The maximum length of a tag. */
	public static final int MAX_TAG_LENGTH = 255;

	/**
	 * Instantiates a device, the missing collections being empty.
	 */
	public Device {
		options = options == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(options));
		tags = tags == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(tags));
		virtualDevices = virtualDevices == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(virtualDevices));
		vrfInstances = vrfInstances == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(vrfInstances));
		attributes = attributes == null ? List.of() : List.copyOf(attributes);
		accesses = accesses == null ? List.of() : List.copyOf(accesses);
		modules = modules == null ? List.of() : List.copyOf(modules);
		networkInterfaces = networkInterfaces == null ? List.of() : List.copyOf(networkInterfaces);
		ownerGroups = ownerGroups == null ? List.of() : List.copyOf(ownerGroups);
	}

	/**
	 * Builds a new device (not stored yet), with the default values.
	 *
	 * @param driver the name of the driver
	 * @param address the management address
	 * @param domain the management domain
	 * @param creator who creates the device
	 * @return the device
	 */
	public static Device create(String driver, String address, Domain domain, String creator) {
		return Device.builder()
			.name(DEFAULTNAME)
			.comments("")
			.contact("")
			.location("")
			.family("")
			.serialNumber("")
			.softwareVersion("")
			.createdDate(Instant.now().truncatedTo(ChronoUnit.MILLIS))
			.creator(creator)
			.driver(driver)
			.mgmtAddress(address)
			.cachedIpAddress(resolveIpAddress(address))
			.mgmtDomain(domain)
			.networkClass(NetworkClass.UNKNOWN)
			.softwareLevel(ConformanceLevel.UNKNOWN)
			.status(Status.INPRODUCTION)
			.compliant(true)
			.build();
	}

	/**
	 * Resolves a management address (cached as the IP address of the device). Called when the
	 * device is created or its management address is changed; also refreshed at the start of
	 * every connection.
	 *
	 * @param address the management address
	 * @return the IP address, or null if the address can't currently be resolved
	 */
	public static InetAddress resolveIpAddress(String address) {
		if (address == null) {
			return null;
		}
		try {
			return InetAddress.getByName(address);
		}
		catch (UnknownHostException e) {
			return null;
		}
	}

	/**
	 * Normalizes a tag value: trims surrounding whitespace.
	 *
	 * @param tag the raw tag
	 * @return the normalized tag, or null if the tag is null, blank or too long
	 */
	public static String normalizeTag(String tag) {
		if (tag == null) {
			return null;
		}
		String normalized = tag.trim();
		if (normalized.isEmpty() || normalized.length() > MAX_TAG_LENGTH) {
			return null;
		}
		return normalized;
	}

	/**
	 * Normalizes tags (the invalid ones are ignored).
	 *
	 * @param tags the raw tags (null means no tag)
	 * @return the normalized tags
	 */
	public static Set<String> normalizeTags(Collection<String> tags) {
		Set<String> normalizedTags = new TreeSet<>();
		if (tags != null) {
			for (String tag : tags) {
				String normalized = Device.normalizeTag(tag);
				if (normalized != null) {
					normalizedTags.add(normalized);
				}
			}
		}
		return normalizedTags;
	}

	/**
	 * Merges a whole new set of per-access configurations (e.g. from a device edit form or a
	 * device-creation request submitting the full list of accesses to use) into the current
	 * ones. An access not present in the incoming list is removed; the others take the incoming
	 * values (a blank address meaning no override). The SSH trusted host keys keep their
	 * "since" date unless they change.
	 *
	 * @param current the current accesses
	 * @param incoming the incoming accesses (null means none)
	 * @return the new accesses
	 */
	public static List<DeviceAccess> mergeAccesses(List<DeviceAccess> current, Collection<DeviceAccess> incoming) {
		if (incoming == null) {
			return List.of();
		}
		Map<String, DeviceAccess> currentByName = new HashMap<>();
		for (DeviceAccess access : current) {
			currentByName.put(access.accessName(), access);
		}
		Map<String, DeviceAccess> merged = new LinkedHashMap<>();
		for (DeviceAccess input : incoming) {
			DeviceAccess existing = currentByName.get(input.accessName());
			Instant keysSince = existing == null ? null : existing.sshTrustedHostKeysSince();
			if (existing == null || !Objects.equals(existing.sshTrustedHostKeys(), input.sshTrustedHostKeys())) {
				keysSince = input.sshTrustedHostKeys() == null ? null : Instant.now().truncatedTo(ChronoUnit.MILLIS);
			}
			merged.put(input.accessName(), input.toBuilder()
				.address(input.address() != null && !input.address().isEmpty() ? input.address() : null)
				.sshTrustedHostKeysSince(keysSince)
				.build());
		}
		return new ArrayList<>(merged.values());
	}

	/**
	 * String view of the cached IP address, exposed on the full device REST representation only
	 * (not on the lightweight device list DTO).
	 *
	 * @return the IP address, or null if it is unknown
	 */
	public String getCachedIpAddressAsString() {
		return this.cachedIpAddress == null ? null : this.cachedIpAddress.getHostAddress();
	}

	/**
	 * Gets the per-access configuration for the given access name, if any.
	 *
	 * @param accessName the access name (e.g. "ssh", "telnet", "https")
	 * @return the access, or null if none is configured
	 */
	public DeviceAccess getDeviceAccess(String accessName) {
		for (DeviceAccess access : this.accesses) {
			if (accessName.equals(access.accessName())) {
				return access;
			}
		}
		return null;
	}

	/**
	 * Gets the device level attributes, by name.
	 *
	 * @return the attributes
	 */
	public Map<String, DeviceAttribute> getAttributeMap() {
		Map<String, DeviceAttribute> map = new HashMap<>();
		for (DeviceAttribute a : this.attributes) {
			map.put(a.name(), a);
		}
		return map;
	}

	/**
	 * Returns a device attribute based on name.
	 *
	 * @param attributeName the name of the attribute to find
	 * @return the found attribute or null if none
	 */
	public DeviceAttribute getAttribute(String attributeName) {
		for (DeviceAttribute attribute : this.attributes) {
			if (attribute.name().equals(attributeName)) {
				return attribute;
			}
		}
		return null;
	}

	/**
	 * Gets a network interface based on its name.
	 *
	 * @param interfaceName the name of the interface to look for
	 * @return the found interface or null if none was found
	 */
	public NetworkInterface getNetworkInterface(String interfaceName) {
		for (NetworkInterface networkInterface : this.networkInterfaces) {
			if (networkInterface.interfaceName().equals(interfaceName)) {
				return networkInterface;
			}
		}
		return null;
	}

	/**
	 * Checks whether the device has the given tag.
	 *
	 * @param tag the tag to look for
	 * @return true if the device has this tag
	 */
	public boolean hasTag(String tag) {
		String normalized = Device.normalizeTag(tag);
		return normalized != null && this.tags.contains(normalized);
	}

	/**
	 * Tells whether the device is past its end of life date.
	 *
	 * @return true if the device is end of life
	 */
	public boolean isEndOfLife() {
		return this.eolDate != null && this.eolDate.isBefore(Instant.now());
	}

	/**
	 * Tells whether the device is past its end of sale date.
	 *
	 * @return true if the device is end of sale
	 */
	public boolean isEndOfSale() {
		return this.eosDate != null && this.eosDate.isBefore(Instant.now());
	}

	@Override
	public String toString() {
		return "Device " + id + " (name '" + name + "', driver '" + driver + "', IP address " + mgmtAddress + ")";
	}
}
