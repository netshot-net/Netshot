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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.domain.Domain;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.device.Device.NetworkClass;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;

/**
 * The working copy of a device while a script runs on it (snapshot, script, diagnostics):
 * the driver helpers and the access manager change it in place, then
 * {@link DeviceRepository#save} writes what differs from the device as it was loaded
 * (see {@link #toDevice()}), and the new configuration and diagnostic results gathered by
 * the script are stored.
 */
public final class DeviceDraft {

	/** The device as it was loaded. */
	@Getter
	private final Device original;

	/** The name. */
	@Getter
	@Setter
	private String name;

	/** The comments. */
	@Getter
	@Setter
	private String comments;

	/** The contact. */
	@Getter
	@Setter
	private String contact;

	/** The location. */
	@Getter
	@Setter
	private String location;

	/** The family. */
	@Getter
	@Setter
	private String family;

	/** The serial number. */
	@Getter
	@Setter
	private String serialNumber;

	/** The software version. */
	@Getter
	@Setter
	private String softwareVersion;

	/** The network class. */
	@Getter
	@Setter
	private NetworkClass networkClass;

	/** The last IP address the management address resolved to. */
	@Getter
	@Setter
	private InetAddress cachedIpAddress;

	/** The end of life date. */
	@Getter
	@Setter
	private Instant eolDate;

	/** The end of life module. */
	@Getter
	@Setter
	private Module eolModule;

	/** The end of sale date. */
	@Getter
	@Setter
	private Instant eosDate;

	/** The end of sale module. */
	@Getter
	@Setter
	private Module eosModule;

	/** The tags. */
	private final Set<String> tags;

	/** The virtual devices. */
	private final Set<String> virtualDevices;

	/** The VRF instances. */
	private final Set<String> vrfInstances;

	/** The attributes, by name. */
	private final Map<String, DeviceAttribute> attributes = new LinkedHashMap<>();

	/** The modules (the new ones have the ID 0). */
	private final List<Module> modules;

	/** The network interfaces. */
	private final List<NetworkInterface> networkInterfaces;

	/** The accesses, by name. */
	private final Map<String, DeviceAccess> accesses = new LinkedHashMap<>();

	/** The new configuration gathered by a snapshot, to be stored, if any. */
	@Getter
	@Setter
	private Config newConfig;

	/**
	 * The last configuration of the device, with its long texts, when it has been loaded already
	 * (null otherwise: the helpers then read it when they need it).
	 */
	@Getter
	@Setter
	private Config lastConfig;

	/**
	 * The stored diagnostic results of the device, when they have been loaded already (null
	 * otherwise: the helpers then read them when they need them).
	 */
	@Getter
	@Setter
	private List<DiagnosticResult> storedDiagnosticResults;

	/** The diagnostic results given by the script, by diagnostic ID. */
	private final Map<Long, DiagnosticResult> diagnosticResults = new LinkedHashMap<>();

	/**
	 * Starts working on a device.
	 *
	 * @param device the device as it is stored
	 */
	public DeviceDraft(Device device) {
		this.original = device;
		this.name = device.name();
		this.comments = device.comments();
		this.contact = device.contact();
		this.location = device.location();
		this.family = device.family();
		this.serialNumber = device.serialNumber();
		this.softwareVersion = device.softwareVersion();
		this.networkClass = device.networkClass();
		this.cachedIpAddress = device.cachedIpAddress();
		this.eolDate = device.eolDate();
		this.eolModule = device.eolModule();
		this.eosDate = device.eosDate();
		this.eosModule = device.eosModule();
		this.tags = new TreeSet<>(device.tags());
		this.virtualDevices = new TreeSet<>(device.virtualDevices());
		this.vrfInstances = new TreeSet<>(device.vrfInstances());
		for (DeviceAttribute attribute : device.attributes()) {
			this.attributes.putIfAbsent(attribute.name(), attribute);
		}
		this.modules = new ArrayList<>(device.modules());
		this.networkInterfaces = new ArrayList<>(device.networkInterfaces());
		for (DeviceAccess access : device.accesses()) {
			this.accesses.put(access.accessName(), access);
		}
	}

	/**
	 * Gets the ID of the device.
	 *
	 * @return the ID
	 */
	public long getId() {
		return this.original.id();
	}

	/**
	 * Gets the name of the driver of the device.
	 *
	 * @return the name of the driver
	 */
	public String getDriver() {
		return this.original.driver();
	}

	/**
	 * Gets the management address of the device.
	 *
	 * @return the management address
	 */
	public String getMgmtAddress() {
		return this.original.mgmtAddress();
	}

	/**
	 * Gets the management domain of the device.
	 *
	 * @return the domain
	 */
	public Domain getMgmtDomain() {
		return this.original.mgmtDomain();
	}

	/**
	 * Gets the driver options of the device.
	 *
	 * @return the options
	 */
	public Map<String, Object> getOptions() {
		return this.original.options();
	}

	/**
	 * Builds the device as it is now.
	 *
	 * @return the device
	 */
	public Device toDevice() {
		return this.original.toBuilder()
			.name(this.name)
			.comments(this.comments)
			.contact(this.contact)
			.location(this.location)
			.family(this.family)
			.serialNumber(this.serialNumber)
			.softwareVersion(this.softwareVersion)
			.networkClass(this.networkClass)
			.cachedIpAddress(this.cachedIpAddress)
			.eolDate(this.eolDate)
			.eolModule(this.eolModule)
			.eosDate(this.eosDate)
			.eosModule(this.eosModule)
			.tags(this.tags)
			.virtualDevices(this.virtualDevices)
			.vrfInstances(this.vrfInstances)
			.attributes(new ArrayList<>(this.attributes.values()))
			.modules(this.modules)
			.networkInterfaces(this.networkInterfaces)
			.accesses(new ArrayList<>(this.accesses.values()))
			.build();
	}

	/**
	 * Adds a tag.
	 *
	 * @param tag the tag (normalized first, see {@link Device#normalizeTag})
	 * @return true if the tag was actually added (valid and not already present)
	 */
	public boolean addTag(String tag) {
		String normalized = Device.normalizeTag(tag);
		return normalized != null && this.tags.add(normalized);
	}

	/**
	 * Removes a tag.
	 *
	 * @param tag the tag
	 * @return true if the tag was actually removed
	 */
	public boolean removeTag(String tag) {
		String normalized = Device.normalizeTag(tag);
		return normalized != null && this.tags.remove(normalized);
	}

	/**
	 * Gets the tags.
	 *
	 * @return the tags (sorted)
	 */
	public Set<String> getTags() {
		return Set.copyOf(this.tags);
	}

	/**
	 * Adds an attribute. Only the first attribute of a given name is kept (as long as the
	 * attributes are not cleared).
	 *
	 * @param attribute the attribute
	 */
	public void addAttribute(DeviceAttribute attribute) {
		this.attributes.putIfAbsent(attribute.name(), attribute);
	}

	/**
	 * Removes all the attributes.
	 */
	public void clearAttributes() {
		this.attributes.clear();
	}

	/**
	 * Gets the attributes.
	 *
	 * @return the attributes
	 */
	public Collection<DeviceAttribute> getAttributes() {
		return List.copyOf(this.attributes.values());
	}

	/**
	 * Gets an attribute by name.
	 *
	 * @param attributeName the name of the attribute
	 * @return the attribute, or null if there is none
	 */
	public DeviceAttribute getAttribute(String attributeName) {
		return this.attributes.get(attributeName);
	}

	/**
	 * Adds a virtual device.
	 *
	 * @param virtualDevice the virtual device
	 */
	public void addVirtualDevice(String virtualDevice) {
		this.virtualDevices.add(virtualDevice);
	}

	/**
	 * Removes all the virtual devices.
	 */
	public void clearVirtualDevices() {
		this.virtualDevices.clear();
	}

	/**
	 * Adds a VRF instance.
	 *
	 * @param vrfInstance the VRF instance
	 */
	public void addVrfInstance(String vrfInstance) {
		this.vrfInstances.add(vrfInstance);
	}

	/**
	 * Removes all the VRF instances.
	 */
	public void clearVrfInstances() {
		this.vrfInstances.clear();
	}

	/**
	 * Marks all the modules as removed (a module seen again is then revived, see {@link #seeModule}).
	 */
	public void setModulesRemoved() {
		this.modules.replaceAll(module -> module.toBuilder().removed(true).build());
	}

	/**
	 * Records a module seen in the device: a removed module of the same slot, part number and
	 * serial number is revived, otherwise the module is added.
	 *
	 * @param module the module (its dates are ignored)
	 * @param when when the module was seen
	 */
	public void seeModule(Module module, Instant when) {
		for (int m = 0; m < this.modules.size(); m++) {
			Module existing = this.modules.get(m);
			if (existing.removed() && existing.sameAs(module)) {
				this.modules.set(m, existing.toBuilder().removed(false).lastSeenDate(when).build());
				return;
			}
		}
		this.modules.add(module.toBuilder().id(0).removed(false).firstSeenDate(when).lastSeenDate(when).build());
	}

	/**
	 * Adds a module, as it is (no history is kept: see {@link #clearModules}).
	 *
	 * @param module the module (its ID is ignored)
	 */
	public void addModule(Module module) {
		this.modules.add(module.toBuilder().id(0).build());
	}

	/**
	 * Removes all the modules, without keeping them in the history (they are deleted).
	 */
	public void clearModules() {
		this.modules.clear();
	}

	/**
	 * Gets the modules (the removed ones included, while the script runs).
	 *
	 * @return the modules
	 */
	public List<Module> getModules() {
		return List.copyOf(this.modules);
	}

	/**
	 * Adds a network interface.
	 *
	 * @param networkInterface the interface
	 */
	public void addNetworkInterface(NetworkInterface networkInterface) {
		this.networkInterfaces.add(networkInterface);
	}

	/**
	 * Removes all the network interfaces.
	 */
	public void clearNetworkInterfaces() {
		this.networkInterfaces.clear();
	}

	/**
	 * Gets the network interfaces.
	 *
	 * @return the interfaces
	 */
	public List<NetworkInterface> getNetworkInterfaces() {
		return List.copyOf(this.networkInterfaces);
	}

	/**
	 * Gets a network interface by name.
	 *
	 * @param interfaceName the name of the interface
	 * @return the interface, or null if there is none
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
	 * Gets the virtual devices.
	 *
	 * @return the virtual devices
	 */
	public Set<String> getVirtualDevices() {
		return Set.copyOf(this.virtualDevices);
	}

	/**
	 * Gets the VRF instances.
	 *
	 * @return the VRF instances
	 */
	public Set<String> getVrfInstances() {
		return Set.copyOf(this.vrfInstances);
	}

	/**
	 * Gets the per-access configuration for the given access name, if any.
	 *
	 * @param accessName the access name (e.g. "ssh", "telnet", "https")
	 * @return the access, or null if none is configured
	 */
	public DeviceAccess getDeviceAccess(String accessName) {
		return this.accesses.get(accessName);
	}

	/**
	 * Sets the per-access configuration of an access (replacing the current one, if any).
	 *
	 * @param access the access
	 */
	public void putDeviceAccess(DeviceAccess access) {
		this.accesses.put(access.accessName(), access);
	}

	private DeviceAccess getOrCreateDeviceAccess(String accessName) {
		return this.accesses.computeIfAbsent(accessName, DeviceAccess::of);
	}

	/**
	 * Pins the given credential set as the global credential set for the given access,
	 * creating the per-access configuration if needed. Used by {@code AccessManager} to
	 * remember a credential set that successfully authenticated via the auto-try pool.
	 *
	 * @param accessName the access name
	 * @param credentialSet the credential set to pin
	 */
	public void pinGlobalCredentialSet(String accessName, DeviceCredentialSet credentialSet) {
		DeviceAccess access = this.getOrCreateDeviceAccess(accessName);
		this.accesses.put(accessName, access.toBuilder().globalCredentialSet(credentialSet).build());
	}

	/**
	 * Records a SSH host key learned via trust-on-first-use, creating the per-access
	 * configuration if needed. Used by {@code AccessManager} once a connection using a freshly
	 * learned host key genuinely succeeds.
	 *
	 * @param accessName the access name
	 * @param trustedHostKeys the new trusted keys block
	 * @param since when the keys were learned
	 */
	public void recordLearnedSshHostKeys(String accessName, String trustedHostKeys, Instant since) {
		DeviceAccess access = this.getOrCreateDeviceAccess(accessName);
		this.accesses.put(accessName, access.toBuilder().sshTrustedHostKeys(trustedHostKeys).sshTrustedHostKeysSince(since).build());
	}

	/**
	 * Removes the per-access configuration for the given access name, if any - an access with
	 * no configuration is never used at all (see {@code AccessManager}).
	 *
	 * @param accessName the access name
	 * @return true if an access was removed
	 */
	public boolean removeDeviceAccess(String accessName) {
		return this.accesses.remove(accessName) != null;
	}

	/**
	 * Records a diagnostic result given by the script (the last one given for a diagnostic wins).
	 *
	 * @param result the result
	 */
	public void addDiagnosticResult(DiagnosticResult result) {
		if (result != null) {
			this.diagnosticResults.put(result.diagnosticId(), result);
		}
	}

	/**
	 * Gets the diagnostic results given by the script.
	 *
	 * @return the results, by diagnostic ID
	 */
	public Map<Long, DiagnosticResult> getDiagnosticResults() {
		return new LinkedHashMap<>(this.diagnosticResults);
	}
}
