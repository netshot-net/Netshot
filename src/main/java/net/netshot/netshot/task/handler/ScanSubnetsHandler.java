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

import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.credentials.CredentialSetRepository;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.net.Network4Address;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.engine.ChildTaskOrchestrator;
import net.netshot.netshot.task.type.DiscoverDeviceTypeTask;
import net.netshot.netshot.task.type.ScanSubnetsTask;

/**
 * Schedules the discovery of every unknown address of some subnets.
 */
@Slf4j
public class ScanSubnetsHandler implements TaskHandler<ScanSubnetsTask> {

	private final ChildTaskOrchestrator childTaskOrchestrator;

	/** The transaction support. */
	private final Transactions transactions;

	/**
	 * Creates the handler.
	 * @param transactions the transaction support
	 * @param childTaskOrchestrator the child task orchestrator
	 */
	public ScanSubnetsHandler(Transactions transactions, ChildTaskOrchestrator childTaskOrchestrator) {
		this.transactions = transactions;
		this.childTaskOrchestrator = childTaskOrchestrator;
	}

	@Override
	public Class<ScanSubnetsTask> taskType() {
		return ScanSubnetsTask.class;
	}

	@Override
	public void run(Task task, ScanSubnetsTask spec, TaskRun run) {
		log.debug("Task {}. Starting scan subnet process.", task.id());

		List<Task> children = this.childTaskOrchestrator.reloadExistingChildren(run);
		if (children.isEmpty()) {
			Set<Integer> toScan = new HashSet<Integer>();
			List<DeviceCredentialSet> knownCommunities;
			try {
				for (Network4Address subnet : spec.subnets()) {
					int address1 = subnet.getSubnetMin();
					int address2 = subnet.getSubnetMax();
					int min = address1 > address2 ? address2 : address1;
					int max = address1 > address2 ? address1 : address2;
					if (min < max - 1) {
						min++; // Avoid subnet network address
						max--;
					}
					log.trace("Task {}. Will scan from {} to {}.", task.id(), min, max);
					run.logger.info("Will scan {} (from {} to {})", subnet.getPrefix(), min, max);
					List<InetAddress> existingAddresses = this.transactions.read(dsl -> dsl.select(DEVICE.CACHED_IP_ADDRESS)
						.from(DEVICE)
						.where(DSL.condition("{0} <<= cast({1} as inet)", DEVICE.CACHED_IP_ADDRESS, DSL.val(subnet.getPrefix())))
						.fetch(DEVICE.CACHED_IP_ADDRESS));
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
			catch (DataAccessException e) {
				log.error("Task {}. Error while retrieving the existing devices in the scope.",
					task.id(), e);
				run.logger.error("Error while checking the existing devices.");
				run.setStatus(Status.FAILURE);
				return;
			}

			try {
				knownCommunities = this.transactions.read(dsl -> new CredentialSetRepository().findAllSnmpCommunities(dsl, spec.domainId()));
			}
			catch (Exception e) {
				log.error("Task {}. Error while retrieving the communities.", task.id(), e);
				run.logger.error("Error while getting the communities.");
				run.setStatus(Status.FAILURE);
				return;
			}
			if (knownCommunities.size() == 0) {
				log.error("Task {}. No available SNMP community to scan devices.", task.id());
				run.logger.error("No available SNMP community to scan devices.");
				run.setStatus(Status.FAILURE);
				return;
			}
			log.trace("Task {}. Will try {} SNMP communities.", task.id(), knownCommunities.size());

			children = new ArrayList<>();
			for (int a : toScan) {
				try {
					Network4Address address = new Network4Address(a, 32);
					if (!address.isNormalUnicast()) {
						log.trace("Task {}. Bad address {} skipped.", task.id(), a);
						run.logger.info("Skipping {}.", address.getIp());
						continue;
					}
					run.logger.info("Adding a task to scan {}", address.getIp());
					log.trace("Task {}. Will add a discovery task for device with IP {} ({}).",
						task.id(), a, address.getIp());
					Set<Long> credentialSetIds = new HashSet<>();
					for (DeviceCredentialSet credentialSet : knownCommunities) {
						credentialSetIds.add(credentialSet.id());
					}
					children.add(DiscoverDeviceTypeTask.of(address.getIp(), spec.domainId(), credentialSetIds, task.comments(), task.author()));
				}
				catch (Exception e) {
					log.error("Task {}. Error while adding discovery task.", task.id(), e);
					run.logger.error("Error while adding discover device type: {}", e.getMessage());
				}
			}
			children = this.childTaskOrchestrator.preCreateChildren(run, children);
		}

		run.setStatus(this.childTaskOrchestrator.orchestrateChildren(run, children));
	}

}
