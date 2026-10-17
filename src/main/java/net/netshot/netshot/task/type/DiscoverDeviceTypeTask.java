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
package net.netshot.netshot.task.type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.quartz.JobKey;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.NetworkAddress;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.task.Task;

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
		@Column(name = "device_address")
	}))
	@Setter
	private String deviceAddress;

	/** The resolved device address, computed once at the start of the discovery. */
	@Getter(onMethod = @__({
		@Transient
	}))
	@Setter
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
	@Transient
	public long getSnapshotTaskId() {
		return this.getLongAttribute("snapshotTaskId", 0);
	}

	/**
	 * Sets the snapshot task id.
	 *
	 * @param snapshotTaskId the new snapshot task id
	 */
	public void setSnapshotTaskId(long snapshotTaskId) {
		this.setAttribute("snapshotTaskId", snapshotTaskId);
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


	/*
	 * (non-Javadoc)
	 * 
	 * @see net.netshot.netshot.task.Task#prepare()
	 */
	@Override
	public void prepare(Session session) {
		Hibernate.initialize(this.getCredentialSets());
		this.getDomain().getId();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.netshot.netshot.task.Task#hashCode()
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
	 * @see net.netshot.netshot.task.Task#equals(java.lang.Object)
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
	 * @see net.netshot.netshot.task.Task#getTaskDescription()
	 */
	@Override
	@Transient
	public String getTaskDescription() {
		return "Device autodiscovery";
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getIdentity()
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
	@Transient
	protected long getDeviceId() {
		if (this.getDiscoverResultDevice() == null) {
			return 0;
		}
		return this.getDiscoverResultDevice().getId();
	}

}
