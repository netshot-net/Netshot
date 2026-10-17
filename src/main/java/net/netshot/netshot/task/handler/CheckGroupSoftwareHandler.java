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
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.type.CheckGroupSoftwareTask;

/**
 * Runs a group software compliance and hardware support check task.
 */
@Slf4j
public class CheckGroupSoftwareHandler implements TaskHandler<CheckGroupSoftwareTask> {


	@Override
	public Class<CheckGroupSoftwareTask> taskType() {
		return CheckGroupSoftwareTask.class;
	}

	@Override
	public void run(CheckGroupSoftwareTask task) {
		DeviceGroup group = task.getDeviceGroup();
		List<Device> deviceList = group == null ? task.getDeviceList() : null;
		if (group == null && (deviceList == null || deviceList.isEmpty())) {
			task.logger.info("Neither a device group nor a device list is set, the task will be cancelled.");
			task.setStatus(Status.CANCELLED);
			return;
		}
		log.debug("Task {}. Starting check software compliance and hardware support status task for {}.",
			task.getId(), group == null ? deviceList.size() + " listed device(s)" : "group " + group.getId());

		List<Long> deviceIds = group == null ? deviceList.stream().map(Device::getId).toList() : null;

		Session session = Database.getSession();
		try {
			log.debug("Task {}. Retrieving the software rules", task.getId());
			List<SoftwareRule> softwareRules = session
				.createQuery("select sr from SoftwareRule sr order by sr.priority asc", SoftwareRule.class)
				.list();
			log.debug("Task {}. Retrieving the hardware rules", task.getId());
			List<HardwareRule> hardwareRules = session
				.createQuery("select hr from HardwareRule hr", HardwareRule.class)
				.list();

			session.beginTransaction();
			ScrollableResults<Device> devices;
			if (group != null) {
				devices = session
					.createQuery("select d from Device d join d.groupMemberships gm where gm.key.group.id = :id", Device.class)
					.setParameter("id", group.getId())
					.setCacheMode(CacheMode.IGNORE)
					.scroll(ScrollMode.FORWARD_ONLY);
			}
			else {
				devices = session
					.createQuery("select d from Device d where d.id in :ids", Device.class)
					.setParameter("ids", deviceIds)
					.setCacheMode(CacheMode.IGNORE)
					.scroll(ScrollMode.FORWARD_ONLY);
			}
			while (devices.next()) {
				Device device = devices.get();
				device.setSoftwareLevel(ConformanceLevel.UNKNOWN);
				for (SoftwareRule rule : softwareRules) {
					rule.check(device);
					if (device.getSoftwareLevel() != ConformanceLevel.UNKNOWN) {
						break;
					}
				}
				device.resetEoX();
				for (HardwareRule rule : hardwareRules) {
					rule.check(device);
				}
				session.persist(device);
				session.flush();
				session.evict(device);
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
	}

}
