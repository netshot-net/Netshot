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

import org.hibernate.CacheMode;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.check.ComplianceChecker;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.type.CheckGroupComplianceTask;

/**
 * Runs a group compliance check task.
 */
@Slf4j
public class CheckGroupComplianceHandler implements TaskHandler<CheckGroupComplianceTask> {

	private final ComplianceChecker complianceChecker;

	/**
	 * Creates the handler.
	 * @param complianceChecker the compliance checker
	 */
	public CheckGroupComplianceHandler(ComplianceChecker complianceChecker) {
		this.complianceChecker = complianceChecker;
	}

	@Override
	public Class<CheckGroupComplianceTask> taskType() {
		return CheckGroupComplianceTask.class;
	}

	@Override
	public void run(CheckGroupComplianceTask task) {
		DeviceGroup group = task.getDeviceGroup();
		List<Device> devices = group == null ? task.getDeviceList() : null;
		if (group == null && (devices == null || devices.isEmpty())) {
			task.logger.info("Neither a device group nor a device list is set, the task will be cancelled.");
			task.setStatus(Status.CANCELLED);
			return;
		}
		log.debug("Task {}. Starting check compliance task for {}.", task.getId(),
			group == null ? devices.size() + " listed device(s)" : "group " + group.getId());

		List<Long> deviceIds = group == null ? devices.stream().map(Device::getId).toList() : null;

		Session session = Database.getSession();
		try {
			List<Policy> policies =
				session.createQuery("select p from Policy p", Policy.class).list();

			session.beginTransaction();
			if (group != null) {
				session
					.createMutationQuery(
						"delete from CheckResult c where c.key.device.id in "
						+ "(select dm1.key.device.id as id from DeviceGroup g1 join g1.cachedMemberships dm1 where dm1.key.group.id = :id)")
					.setParameter("id", group.getId())
					.executeUpdate();
			}
			else {
				session
					.createMutationQuery("delete from CheckResult c where c.key.device.id in :ids")
					.setParameter("ids", deviceIds)
					.executeUpdate();
			}
			for (Policy policy : policies) {
				// Get devices which are part of the target group/list and which are in a group which the policy is applied to
				ScrollableResults<Device> scrolledDevices;
				if (group != null) {
					scrolledDevices = session
						.createQuery(
							"select d from Device d join d.groupMemberships gm where gm.key.group.id = :groupId and d in "
							+ "(select dm1.key.device from Policy p join p.targetGroups g1 join g1.cachedMemberships dm1 where p.id = :policyId)",
							Device.class)
						.setParameter("groupId", group.getId())
						.setParameter("policyId", policy.getId())
						.setCacheMode(CacheMode.IGNORE)
						.scroll(ScrollMode.FORWARD_ONLY);
				}
				else {
					scrolledDevices = session
						.createQuery(
							"select d from Device d where d.id in :deviceIds and d in "
							+ "(select dm1.key.device from Policy p join p.targetGroups g1 join g1.cachedMemberships dm1 where p.id = :policyId)",
							Device.class)
						.setParameter("deviceIds", deviceIds)
						.setParameter("policyId", policy.getId())
						.setCacheMode(CacheMode.IGNORE)
						.scroll(ScrollMode.FORWARD_ONLY);
				}
				while (scrolledDevices.next()) {
					Device device = scrolledDevices.get();
					task.logger.info("Checking configuration compliance of device {} ({})", device.getName(), device.getId());
					this.complianceChecker.check(policy, device, session, task.logger);
					session.persist(device);
					session.flush();
					session.evict(device);
				}
			}
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

		log.debug("Task {}. Request to refresh all the groups after compliance check.", task.getId());
		DynamicDeviceGroup.refreshAllGroups();
		log.debug("Task {}. Group refreshing done.", task.getId());
	}

}
