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
package net.netshot.netshot.work.tasks;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.quartz.JobKey;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.TaskManager;
import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.Domain;
import net.netshot.netshot.device.DynamicDeviceGroup;
import net.netshot.netshot.device.NetworkAddress;
import net.netshot.netshot.device.access.DeviceAccess;
import net.netshot.netshot.device.access.Snmp;
import net.netshot.netshot.device.credentials.DeviceCredentialSet;
import net.netshot.netshot.device.credentials.DeviceSnmpCommunity;
import net.netshot.netshot.device.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.device.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.device.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.rest.RestViews.DefaultView;
import net.netshot.netshot.rest.RestViews.HookView;
import net.netshot.netshot.work.Task;

/**
 * This task discovers the type of the given device.
 */
@Entity
@DiscriminatorValue("DiscoverDeviceTypeTask")
@Slf4j
public final class DiscoverDeviceTypeTask extends Task implements DomainBasedTask {

	/** The credential sets. */
	@Getter(onMethod = @__({
		@ManyToMany,
		@Fetch(FetchMode.SELECT),
		@OnDelete(action = OnDeleteAction.CASCADE),
		@JoinTable(name = "discover_device_type_task_credential_sets",
			joinColumns = @JoinColumn(name = "discover_device_type_task"),
			inverseJoinColumns = @JoinColumn(name = "credential_sets"))
	}))
	@Setter
	private Set<DeviceCredentialSet> credentialSets = new HashSet<DeviceCredentialSet>();

	/** The device address (IPv4 literal, IPv6 literal, or FQDN, resolved lazily). */
	@Getter(onMethod = @__({
		@Column(name = "device_address"),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String deviceAddress;

	/** The resolved device address, computed once at the start of {@link #run()}. */
	@Getter(onMethod = @__({
		@Transient
	}))
	private transient NetworkAddress resolvedAddress;

	/** The success credential set. */
	@Getter(onMethod = @__({
		@Transient
	}))
	@Setter
	private DeviceCredentialSet successCredentialSet;

	/**
	 * The device found/created as a result of a successful discovery. Kept as its own
	 * column (not the shared "device" column hoisted onto Task) since its live
	 * ON DELETE CASCADE behavior is the opposite of the shared column's SET NULL.
	 */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(HookView.class),
		@ManyToOne(fetch = FetchType.LAZY),
		@OnDelete(action = OnDeleteAction.CASCADE)
	}))
	@Setter
	private Device discoverResultDevice;

	/**
	 * Instantiates a new discover device type task.
	 */
	protected DiscoverDeviceTypeTask() {
	}

	/**
	 * Instantiates a new discover device type task.
	 *
	 * @param deviceAddress = the device address
	 * @param domain = the domain
	 * @param comments = the comments
	 * @param author = the author
	 */
	public DiscoverDeviceTypeTask(String deviceAddress, Domain domain,
		String comments, String author) {
		super(comments, deviceAddress, author);
		this.deviceAddress = deviceAddress;
		this.setDomain(domain);
	}

	/**
	 * Gets the discovered device type name.
	 * @return the discovered device type name
	 */
	@Transient
	public String getDiscoveredDeviceType() {
		return this.getStringAttribute("discoveredDeviceType", null);
	}

	/**
	 * Sets the discovered device type name.
	 * @param discoveredDeviceType the discovered device type name
	 */
	public void setDiscoveredDeviceType(String discoveredDeviceType) {
		this.setAttribute("discoveredDeviceType", discoveredDeviceType);
	}

	/**
	 * Gets the comments to set on the device once discovered.
	 * @return the device comments, or null if none
	 */
	@Transient
	public String getDeviceComments() {
		return this.getStringAttribute("deviceComments", null);
	}

	/**
	 * Sets the comments to set on the device once discovered.
	 * @param deviceComments the device comments
	 */
	public void setDeviceComments(String deviceComments) {
		if (deviceComments == null) {
			this.attributes.remove("deviceComments");
		}
		else {
			this.setAttribute("deviceComments", deviceComments);
		}
	}

	/**
	 * Gets the tags to set on the device once discovered.
	 * @return the device tags (never null)
	 */
	@Transient
	public List<String> getDeviceTags() {
		List<String> tags = new ArrayList<>();
		if (this.attributes.get("deviceTags") instanceof List<?> list) {
			for (Object item : list) {
				if (item instanceof String tag) {
					tags.add(tag);
				}
			}
		}
		return tags;
	}

	/**
	 * Sets the tags to set on the device once discovered.
	 * @param deviceTags the device tags
	 */
	public void setDeviceTags(List<String> deviceTags) {
		if (deviceTags == null || deviceTags.isEmpty()) {
			this.attributes.remove("deviceTags");
		}
		else {
			this.setAttribute("deviceTags", new ArrayList<>(deviceTags));
		}
	}

	/**
	 * Gets the snapshot task id.
	 *
	 * @return the snapshot task id
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public long getSnapshotTaskId() {
		return this.getLongAttribute("snapshotTaskId", 0);
	}

	/**
	 * Sets the snapshot task id.
	 *
	 * @param snapshotTaskId the new snapshot task id
	 */
	protected void setSnapshotTaskId(long snapshotTaskId) {
		this.setAttribute("snapshotTaskId", snapshotTaskId);
	}

	/**
	 * Gets the discovered device type description.
	 * 
	 * @return the discovered device type description
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public String getDiscoveredDeviceTypeDescription() {
		String description = "Unknown";
		DeviceDriver driver = DeviceDriver.getDriverByName(this.getDiscoveredDeviceType());
		if (driver == null) {
			log.debug("No driver named {}.", this.getDiscoveredDeviceType());
		}
		else {
			description = driver.getDescription();
		}
		return description;
	}

	/**
	 * Adds the credential set.
	 * 
	 * @param credentialSet
	 *          the credential set
	 */
	public void addCredentialSet(DeviceCredentialSet credentialSet) {
		credentialSets.add(credentialSet);
	}


	private boolean snmpDiscover(Snmp poller) {
		log.debug("Task {}. Trying SNMP discovery.", this.getId());
		String sysObjectId;
		String sysDesc;
		try {
			sysDesc = poller.getAsString("1.3.6.1.2.1.1.1.0");
			sysObjectId = poller.getAsString("1.3.6.1.2.1.1.2.0");
			this.logger.debug("Got sysDesc = {}", sysDesc);
			this.logger.debug("Got sysObjectID = {}", sysObjectId);
			log.trace("Got sysDesc '{}' and sysObjectID '{}'.", sysDesc,
				sysObjectId);

			// Iterates over possible device classes
			for (DeviceDriver driver : DeviceDriver.getAllDrivers()) {
				if (driver.snmpAutoDiscover(this, sysObjectId, sysDesc, this.logger)) {
					log.trace("The driver {} did accept the OID.", driver.getName());
					this.setDiscoveredDeviceType(driver.getName());
					return true;
				}
			}
		}
		catch (IOException e) {
			log.warn("Error while polling the device via SNMP.", e);
			this.logger.error("Error while polling the device via SNMP.");
		}
		finally {
			try {
				poller.stop();
			}
			catch (IOException e) {
			}
		}
		log.debug("No driver has accepted the OID.");
		return false;
	}

	private boolean snmpv1Discover(DeviceSnmpCommunity community) {
		log.trace("Task {}. SNMPv1 discovery with credential set {}.",
			this.getId(), community.getName());
		this.logger.trace("Trying SNMPv1 discovery (credentials {})", community.getName());
		try {
			Snmp poller = new Snmp(this.resolvedAddress, community);
			return snmpDiscover(poller);
		}
		catch (UnknownHostException e) {
			log.warn("Task {}. SNMPv1 unknown host error.", this.getId(), e);
			this.logger.warn("SNMPv1 unknown host error: {}", e.getMessage());
		}
		catch (Exception e) {
			log.error("Task {}. SNMPv1 error while polling the device.", this.getId(), e);
			this.logger.warn("Error while SNMPv1 polling the device: {}", e.getMessage());
		}
		return false;
	}

	private boolean snmpv2cDiscover(DeviceSnmpCommunity community) {
		log.trace("Task {}. SNMPv2c discovery with credential set {}.",
			this.getId(), community.getName());
		this.logger.trace("Trying SNMPv2c discovery (credential set {})", community.getName());
		try {
			Snmp poller = new Snmp(this.resolvedAddress, community);
			return snmpDiscover(poller);
		}
		catch (UnknownHostException e) {
			log.warn("Task {}. SNMPv2 unknown host error.", this.getId(), e);
			this.logger.warn("SNMPv2 unknown host error: {}", e.getMessage());
		}
		catch (Exception e) {
			log.error("Task {}. SNMPv2 error while polling the device.", this.getId(), e);
			this.logger.warn("Error while SNMPv2 polling the device: {}", e.getMessage());
		}
		return false;
	}

	private boolean snmpv3Discover(DeviceSnmpv3Community cred) {
		log.trace("Task {}. SNMPv3 discovery with credential set {}.",
			this.getId(), cred.getName());
		this.logger.trace("Trying SNMPv3 discovery (credentials {})", cred.getName());
		try {
			Snmp poller = new Snmp(this.resolvedAddress, cred);
			return snmpDiscover(poller);
		}
		catch (UnknownHostException e) {
			log.warn("Task {}. SNMPv3 unknown host error.", this.getId(), e);
			this.logger.warn("SNMPv3 unknown host error: {}", e.getMessage());
		}
		catch (Exception e) {
			log.error("Task {}. SNMPv3 error while polling the device.", this.getId(), e);
			this.logger.warn("Error while SNMPv3 polling the device: {}", e.getMessage());
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.netshot.netshot.work.Task#prepare()
	 */
	@Override
	public void prepare(Session session) {
		Hibernate.initialize(this.getCredentialSets());
		this.getDomain().getId();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.netshot.netshot.work.Task#run()
	 */
	@Override
	public void run() {
		log.debug("Task {}. Starting autodiscovery process.", this.getId());
		boolean didTrySnmp = false;

		try {
			this.resolvedAddress = NetworkAddress.getNetworkAddress(InetAddress.getByName(this.deviceAddress));
		}
		catch (UnknownHostException e) {
			log.warn("Task {}. Unable to resolve device address '{}'.", this.getId(), this.deviceAddress, e);
			this.logger.error("Unable to resolve device address '{}'.", this.deviceAddress);
			this.status = Status.FAILURE;
			return;
		}

		log.trace("Task {}. {} credential sets in the list.", this.getId(), credentialSets.size());
		for (DeviceCredentialSet credentialSet : credentialSets) {
			if (credentialSet instanceof DeviceSnmpv1Community snmpCommunity) {
				log.trace("Task {}. SNMPv1 credential set.", this.getId());
				didTrySnmp = true;
				if (snmpv1Discover(snmpCommunity)) {
					this.status = Status.SUCCESS;
					this.successCredentialSet = credentialSet;
					break;
				}
			}
			else if (credentialSet instanceof DeviceSnmpv2cCommunity snmpCommunity) {
				log.trace("Task {}. SNMPv2c credential set.", this.getId());
				didTrySnmp = true;
				if (snmpv2cDiscover(snmpCommunity)) {
					this.status = Status.SUCCESS;
					this.successCredentialSet = credentialSet;
					break;
				}
			}
			else if (credentialSet instanceof DeviceSnmpv3Community snmpCreds) {
				log.trace("Task {}. SNMPv3 credential set.", this.getId());
				didTrySnmp = true;
				if (snmpv3Discover(snmpCreds)) {
					this.status = Status.SUCCESS;
					this.successCredentialSet = credentialSet;
					break;
				}
			}
		}
		if (this.status == Status.SUCCESS) {
			Task snapshotTask = null;
			Session session = Database.getSession();
			this.setDiscoverResultDevice(null);
			Device newDevice = null;
			try {
				session.beginTransaction();
				newDevice = new Device(this.getDiscoveredDeviceType(), deviceAddress, this.getDomain(), this.author);
				// Create a bare (enabled, no pin) DeviceAccess row for every access the
				// driver declares, so each is usable via the domain's auto-try pool -
				// an access with no row at all is never used (see AccessManager).
				DeviceDriver newDeviceDriver = DeviceDriver.getDriverByName(this.getDiscoveredDeviceType());
				if (newDeviceDriver != null) {
					for (String accessName : newDeviceDriver.getAccessDefinitions().keySet()) {
						newDevice.getAccesses().add(new DeviceAccess(newDevice, accessName));
					}
				}
				if (this.getDeviceComments() != null) {
					newDevice.setComments(this.getDeviceComments());
				}
				newDevice.replaceTags(this.getDeviceTags());
				this.setDiscoverResultDevice(newDevice);
				session.persist(newDevice);
				snapshotTask = new TakeSnapshotTask(newDevice,
					"Automatic snapshot after discovery", author, true, false, false);
				snapshotTask.setPriority(this.getPriority());
				snapshotTask.setParentTaskId(this.getId());
				session.persist(snapshotTask);
				session.getTransaction().commit();
				this.setSnapshotTaskId(snapshotTask.getId());
			}
			catch (HibernateException e) {
				Database.rollbackSilently(session);
				log.error("Task {}. Couldn't save the new device.", this.getId(), e);
				this.logger.error("Database error while adding the device");
			}
			catch (Exception e) {
				log.error("Task {}. Error while saving the new device or the new task.", this.getId(), e);
				this.logger.error("Couldn't add the device after discovery");
			}
			finally {
				session.close();
			}

			if (newDevice != null) {
				DynamicDeviceGroup.refreshAllGroupsOfOneDevice(newDevice);
			}

			try {
				if (snapshotTask != null) {
					TaskManager.addTask(snapshotTask);
				}
			}
			catch (Exception e) {
				log.error("Task {}. Error while registering the new snapshot task.", this.getId(), e);
			}

			return;
		}
		if (!didTrySnmp) {
			log.warn("Task {}. No available SNMP credential set.", this.getId());
			this.logger.error(
				"No available SNMP credential set... cannot start autodiscovery.");
		}
		this.status = Status.FAILURE;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.netshot.netshot.work.Task#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
			+ (creationDate == null ? 0 : creationDate.hashCode());
		result = prime * result
			+ (deviceAddress == null ? 0 : deviceAddress.hashCode());
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.netshot.netshot.work.Task#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof DiscoverDeviceTypeTask)) {
			return false;
		}
		DiscoverDeviceTypeTask other = (DiscoverDeviceTypeTask) obj;
		if (creationDate == null) {
			if (other.creationDate != null) {
				return false;
			}
		}
		else if (!creationDate.equals(other.creationDate)) {
			return false;
		}
		if (deviceAddress == null) {
			if (other.deviceAddress != null) {
				return false;
			}
		}
		else if (!deviceAddress.equals(other.deviceAddress)) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.netshot.netshot.work.Task#getTaskDescription()
	 */
	@Override
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public String getTaskDescription() {
		return "Device autodiscovery";
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.work.Task#getIdentity()
	 */
	@Override
	@Transient
	public JobKey getIdentity() {
		return new JobKey(String.format("Task_%d", this.getId()),
			String.format("DiscoverDeviceType_%s", this.getDeviceAddress()));
	}

	@Override
	public void copyResultsTo(Task target) {
		super.copyResultsTo(target);
		DiscoverDeviceTypeTask t = (DiscoverDeviceTypeTask) target;
		t.setDiscoverResultDevice(this.getDiscoverResultDevice());
	}

	/**
	 * Get the ID of the discovered device.
	 *
	 * @return the ID of the device
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	protected long getDeviceId() {
		if (this.getDiscoverResultDevice() == null) {
			return 0;
		}
		return this.getDiscoverResultDevice().getId();
	}

}
