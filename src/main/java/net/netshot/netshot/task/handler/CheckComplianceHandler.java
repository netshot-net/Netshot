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

import java.util.List;

import org.hibernate.Session;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.compliance.check.ComplianceChecker;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.type.CheckComplianceTask;

/**
 * Runs a device compliance check task.
 */
@Slf4j
public class CheckComplianceHandler implements TaskHandler<CheckComplianceTask> {

	private final ComplianceChecker complianceChecker;

	/**
	 * Creates the handler.
	 * @param complianceChecker the compliance checker
	 */
	public CheckComplianceHandler(ComplianceChecker complianceChecker) {
		this.complianceChecker = complianceChecker;
	}

	@Override
	public Class<CheckComplianceTask> taskType() {
		return CheckComplianceTask.class;
	}

	@Override
	public void run(CheckComplianceTask task) {
		Device device = task.getDevice();
		log.debug("Task {}. Starting check compliance task for device {}.", task.getId(),
			device == null ? "null" : device.getId());
		if (device == null) {
			task.logger.info("The device doesn't exist, the task will be cancelled.");
			task.setStatus(Status.CANCELLED);
			return;
		}

		Session session = Database.getSession();
		try {
			session.beginTransaction();
			// Delete previous results
			session
				.createMutationQuery("delete from CheckResult c where c.key.device.id = :id")
				.setParameter("id", device.getId())
				.executeUpdate();
			// Start over from a fresh device from DB
			device = session.get(Device.class, device.getId());
			task.setDevice(device);
			task.logger.info("Check compliance task for device {} ({}).",
				device.getName(), device.getMgmtAddress());
			if (device.getLastConfig() == null) {
				log.info("Task {}. Unable to fetch the device with its last config... has it been captured at least once?",
					task.getId());
				throw new Exception("No last config for this device. Has it been captured at least once?");
			}
			List<Policy> policies = session
				.createQuery("select distinct p from Policy p join p.targetGroups g "
					+ "join g.cachedMemberships dm with dm.key.device.id = :id", Policy.class)
				.setParameter("id", device.getId())
				.list();

			task.logger.info("Checking configuration compliance of device {} ({})...",
				device.getName(), device.getId());
			for (Policy policy : policies) {
				this.complianceChecker.check(policy, device, session, task.logger);
			}
			session.persist(device);
			session.flush();
			List<SoftwareRule> softwareRules = session
				.createQuery("select sr from SoftwareRule sr where (sr.targetGroup is null) or "
					+ "sr.targetGroup in (select g from DeviceGroup g join g.cachedMemberships dm "
					+ "with dm.key.device.id = :id) order by sr.priority asc", SoftwareRule.class)
				.setParameter("id", device.getId())
				.list();
			device.setSoftwareLevel(ConformanceLevel.UNKNOWN);
			for (SoftwareRule rule : softwareRules) {
				rule.check(device);
				if (device.getSoftwareLevel() != ConformanceLevel.UNKNOWN) {
					break;
				}
			}
			session.persist(device);
			List<HardwareRule> hardwareRules = session
				.createQuery("select hr from HardwareRule hr", HardwareRule.class)
				.list();
			device.resetEoX();
			for (HardwareRule rule : hardwareRules) {
				rule.check(device);
			}
			session.persist(device);
			session.getTransaction().commit();
			task.setStatus(Status.SUCCESS);
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Task {}. Error while checking compliance.", task.getId(), e);
			task.logger.error("Error while checking compliance: {}", e.getMessage());
			task.setStatus(Status.FAILURE);
			return;
		}
		finally {
			session.close();
		}

		log.debug("Task {}. Request to refresh all the groups for the device after compliance check.", task.getId());
		DynamicDeviceGroup.refreshAllGroupsOfOneDevice(device);
	}

}
