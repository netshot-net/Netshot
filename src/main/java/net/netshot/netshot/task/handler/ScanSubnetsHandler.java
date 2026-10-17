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
package net.netshot.netshot.task.handler;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.engine.ChildTaskOrchestrator;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;

/**
 * Schedules the discovery of every unknown address of some subnets.
 */
@Slf4j
public class ScanSubnetsHandler implements TaskHandler<ScanSubnetsTask> {

	private final ChildTaskOrchestrator childTaskOrchestrator;

	/**
	 * Creates the handler.
	 * @param childTaskOrchestrator the child task orchestrator
	 */
	public ScanSubnetsHandler(ChildTaskOrchestrator childTaskOrchestrator) {
		this.childTaskOrchestrator = childTaskOrchestrator;
	}

	@Override
	public Class<ScanSubnetsTask> taskType() {
		return ScanSubnetsTask.class;
	}

	@Override
	public void run(ScanSubnetsTask task) {
		log.debug("Task {}. Starting scan subnet process.", task.getId());

		List<Task> children = this.childTaskOrchestrator.reloadExistingChildren(task);
		if (children.isEmpty()) {
			Session session = Database.getSession();
			Set<Integer> toScan = new HashSet<Integer>();
			List<DeviceCredentialSet> knownCommunities;
			try {
				try {
					for (Network4Address subnet : task.getSubnets()) {
						int address1 = subnet.getSubnetMin();
						int address2 = subnet.getSubnetMax();
						int min = address1 > address2 ? address2 : address1;
						int max = address1 > address2 ? address1 : address2;
						if (min < max - 1) {
							min++; // Avoid subnet network address
							max--;
						}
						log.trace("Task {}. Will scan from {} to {}.", task.getId(), min, max);
						task.logger.info("Will scan {} (from {} to {})", subnet.getPrefix(), min, max);
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
						task.getId(), e);
					task.logger.error("Error while checking the existing devices.");
					task.setStatus(Status.FAILURE);
					return;
				}

				try {
					knownCommunities = session
						.createQuery("from DeviceSnmpCommunity c where c.mgmtDomain is null or c.mgmtDomain = :domain",
							DeviceCredentialSet.class)
						.setParameter("domain", task.getDomain())
						.list();
				}
				catch (Exception e) {
					log.error("Task {}. Error while retrieving the communities.", task.getId(), e);
					task.logger.error("Error while getting the communities.");
					task.setStatus(Status.FAILURE);
					return;
				}
			}
			finally {
				session.close();
			}
			if (knownCommunities.size() == 0) {
				log.error("Task {}. No available SNMP community to scan devices.", task.getId());
				task.logger.error("No available SNMP community to scan devices.");
				task.setStatus(Status.FAILURE);
				return;
			}
			log.trace("Task {}. Will try {} SNMP communities.", task.getId(), knownCommunities.size());

			children = new ArrayList<>();
			for (int a : toScan) {
				try {
					Network4Address address = new Network4Address(a, 32);
					if (!address.isNormalUnicast()) {
						log.trace("Task {}. Bad address {} skipped.", task.getId(), a);
						task.logger.info("Skipping {}.", address.getIp());
						continue;
					}
					task.logger.info("Adding a task to scan {}", address.getIp());
					log.trace("Task {}. Will add a discovery task for device with IP {} ({}).",
						task.getId(), a, address.getIp());
					DiscoverDeviceTypeTask discoverTask = new DiscoverDeviceTypeTask(address.getIp(), task.getDomain(), task.getComments(), task.getAuthor());
					for (DeviceCredentialSet credentialSet : knownCommunities) {
						discoverTask.addCredentialSet(credentialSet);
					}
					children.add(discoverTask);
				}
				catch (Exception e) {
					log.error("Task {}. Error while adding discovery task.", task.getId(), e);
					task.logger.error("Error while adding discover device type: {}", e.getMessage());
				}
			}
			this.childTaskOrchestrator.preCreateChildren(task, children);
		}

		task.setStatus(this.childTaskOrchestrator.orchestrateChildren(task, children));
	}

}
