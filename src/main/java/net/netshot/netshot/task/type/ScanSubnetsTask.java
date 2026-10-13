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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.quartz.JobKey;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.task.Task;

/**
 * This task scans a subnet to discover devices.
 */
@Entity
@DiscriminatorValue("ScanSubnetsTask")
@Slf4j
public final class ScanSubnetsTask extends Task implements DomainBasedTask, ChildOrchestratingTask {

	/** The subnets. */
	@Getter(onMethod = @__({
		@Fetch(FetchMode.SELECT),
		@ElementCollection(fetch = FetchType.EAGER),
		// Can't make it accepted by Hibernate 6.5... nevermind
		// @OnDelete(action = OnDeleteAction.CASCADE),
		@CollectionTable(name = "scan_subnets_task_subnets",
			joinColumns = @JoinColumn(name = "scan_subnets_task")),
		@AttributeOverrides({
			@AttributeOverride(name = "address", column = @Column(name = "ipv4address")),
			@AttributeOverride(name = "addressUsage", column = @Column(name = "ipv4usage")),
			@AttributeOverride(name = "prefixLength", column = @Column(name = "ipv4mask")),
		})
	}))
	@Setter
	private Set<Network4Address> subnets;

	/**
	 * Instantiates a new scan subnet task.
	 */
	protected ScanSubnetsTask() {
	}

	/**
	 * Instantiates a new scan subnets task.
	 *
	 * @param subnets the subnets
	 * @param domain the domain
	 * @param comments the comments
	 * @param target the target
	 * @param author the author
	 */
	public ScanSubnetsTask(Set<Network4Address> subnets, Domain domain, String comments,
		String target, String author) {
		super(comments, target, author);
		this.setDomain(domain);
		this.subnets = subnets;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getTaskDescription()
	 */
	@Override
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public String getTaskDescription() {
		return "Subnet scan";
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#run()
	 */
	@Override
	public void run() {
		log.debug("Task {}. Starting scan subnet process.", this.getId());

		List<Task> children = this.reloadExistingChildren();
		if (children.isEmpty()) {
			Session session = Database.getSession();
			Set<Integer> toScan = new HashSet<Integer>();
			List<DeviceCredentialSet> knownCommunities;
			try {
				try {
					for (Network4Address subnet : subnets) {
						int address1 = subnet.getSubnetMin();
						int address2 = subnet.getSubnetMax();
						int min = address1 > address2 ? address2 : address1;
						int max = address1 > address2 ? address1 : address2;
						if (min < max - 1) {
							min++; // Avoid subnet network address
							max--;
						}
						log.trace("Task {}. Will scan from {} to {}.", this.getId(), min, max);
						this.logger.info("Will scan {} (from {} to {})", subnet.getPrefix(), min, max);
						List<InetAddress> existingAddresses = session
							.createQuery(
								"select d.cachedIpAddress from Device d where net_contains(d.cachedIpAddress, :cidr)",
								InetAddress.class)
							.setParameter("cidr", subnet.getPrefix())
							.list();
						Set<Integer> existing = new HashSet<>();
						for (InetAddress ip : existingAddresses) {
							if (ip instanceof Inet4Address ip4) {
								existing.add(Network4Address.inetAddressToInt(ip4));
							}
						}
						for (int a = min; a <= max; a++) {
							if (!existing.contains(a)) {
								toScan.add(a);
							}
						}
					}
				}
				catch (HibernateException e) {
					log.error("Task {}. Error while retrieving the existing devices in the scope.",
						this.getId(), e);
					this.logger.error("Error while checking the existing devices.");
					this.status = Status.FAILURE;
					return;
				}

				try {
					knownCommunities = session
						.createQuery("from DeviceSnmpCommunity c where c.mgmtDomain is null or c.mgmtDomain = :domain",
							DeviceCredentialSet.class)
						.setParameter("domain", this.getDomain())
						.list();
				}
				catch (Exception e) {
					log.error("Task {}. Error while retrieving the communities.", this.getId(), e);
					this.logger.error("Error while getting the communities.");
					this.status = Status.FAILURE;
					return;
				}
			}
			finally {
				session.close();
			}
			if (knownCommunities.size() == 0) {
				log.error("Task {}. No available SNMP community to scan devices.", this.getId());
				this.logger.error("No available SNMP community to scan devices.");
				this.status = Status.FAILURE;
				return;
			}
			log.trace("Task {}. Will try {} SNMP communities.", this.getId(), knownCommunities.size());

			children = new ArrayList<>();
			for (int a : toScan) {
				try {
					Network4Address address = new Network4Address(a, 32);
					if (!address.isNormalUnicast()) {
						log.trace("Task {}. Bad address {} skipped.", this.getId(), a);
						this.logger.info("Skipping {}.", address.getIp());
						continue;
					}
					this.logger.info("Adding a task to scan {}", address.getIp());
					log.trace("Task {}. Will add a discovery task for device with IP {} ({}).",
						this.getId(), a, address.getIp());
					DiscoverDeviceTypeTask discoverTask = new DiscoverDeviceTypeTask(address.getIp(), this.getDomain(), comments, author);
					for (DeviceCredentialSet credentialSet : knownCommunities) {
						discoverTask.addCredentialSet(credentialSet);
					}
					children.add(discoverTask);
				}
				catch (Exception e) {
					log.error("Task {}. Error while adding discovery task.", this.getId(), e);
					this.logger.error("Error while adding discover device type: {}", e.getMessage());
				}
			}
			this.preCreateChildren(children);
		}

		this.status = this.orchestrateChildren(children);
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		ScanSubnetsTask task = (ScanSubnetsTask) super.clone();
		task.setSubnets(this.subnets);
		return task;
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getIdentity()
	 */
	@Override
	@Transient
	public JobKey getIdentity() {
		return new JobKey(String.format("Task_%d", this.getId()), "ScanSubnets");
	}
}
